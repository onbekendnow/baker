package com.ing.baker.baas.state

import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.stream.{ActorMaterializer, Materializer}
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.ing.baker.runtime.akka.AkkaBakerConfig.KafkaEventSinkSettings
import com.ing.baker.runtime.akka.{AkkaBaker, AkkaBakerConfig}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.http4s.client.blaze.BlazeClientBuilder
import skuber.api.client.KubernetesClient

import scala.concurrent.ExecutionContext
import scala.util.{Success, Try, Failure}

object Main extends IOApp with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    Kamon.init()

    // Config
    val config = mergeConfig(executeLoad(getExtraSecrets) ++ executeLoad(getExtraConfig))

    val httpServerPort = config.getInt("baas-component.http-api-port")
    val recipeDirectory = config.getString("baas-component.recipe-directory")

    val eventSinkSettings = config.getConfig("baker.kafka-event-sink").as[KafkaEventSinkSettings]

    // Core dependencies
    implicit val system: ActorSystem =
      ActorSystem("BaaSStateNodeSystem", config)
    implicit val materializer: Materializer =
      ActorMaterializer()
    implicit val connectionPool: ExecutionContext =
      ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
    val hostname: InetSocketAddress =
      InetSocketAddress.createUnresolved("0.0.0.0", httpServerPort)
    val k8s: KubernetesClient = skuber.k8sInit

    val mainResource = for {
      httpClient <- BlazeClientBuilder[IO](connectionPool).resource
      serviceDiscovery <- ServiceDiscovery.resource(httpClient, k8s)
      eventSink <- KafkaEventSink.resource(eventSinkSettings)
      baker = AkkaBaker
        .withConfig(AkkaBakerConfig(
          interactionManager = serviceDiscovery.buildInteractionManager,
          bakerActorProvider = AkkaBakerConfig.bakerProviderFrom(config),
          readJournal = AkkaBakerConfig.persistenceQueryFrom(config, system),
          timeouts = AkkaBakerConfig.Timeouts.from(config),
          bakerValidationSettings = AkkaBakerConfig.BakerValidationSettings.from(config),
        )(system))
      _ <- Resource.liftF(eventSink.attach(baker))
      _ <- Resource.liftF(RecipeLoader.loadRecipesIntoBaker(recipeDirectory, baker))
      _ <- Resource.liftF(IO.async[Unit] { callback =>
        Cluster(system).registerOnMemberUp {
          callback(Right(()))
        }
      })
      _ <- StateNodeService.resource(baker, hostname, serviceDiscovery)
    } yield ()

    mainResource.use(_ => IO.never).as(ExitCode.Success)
  }

  /** Merges all configuration giving priority to values in front of the list, and as final fallback the configuration from
    * ConfigFactory.load */
  def mergeConfig(cs: List[Config]): Config =
    (cs match {
      case c :: tail => c.withFallback(mergeConfig(tail))
      case Nil => ConfigFactory.load()
    }).resolve()

  def executeLoad(from: IO[List[Config]]): List[Config] =
    from.attempt.unsafeRunSync() match {
      case Left(e) => logger.error(s"Error while loading config: ${e.getMessage}"); List.empty
      case Right(a) => a
    }

  def getExtraConfig: IO[List[Config]] =
    loadExtraConfig(IO(new File("/bakery-config")))

  def getExtraSecrets: IO[List[Config]] =
    loadExtraConfig(IO(new File("/bakery-secrets")))

  def loadExtraConfig(from: IO[File]): IO[List[Config]] =
    from.map {
      _.listFiles
        .filter(f => """.*\.conf$""".r.findFirstIn(f.getName).isDefined)
        .map(f => Try(ConfigFactory.parseFile(f) -> f.getName))
        .map {
          case Failure(e) => logger.error("Failed to parse extra config: " + e.getMessage); None
          case Success((v, name)) => logger.info(s"Loaded extra configuration from '$name'"); Some(v)
        }
        .toList
        .flatten
    }
}
