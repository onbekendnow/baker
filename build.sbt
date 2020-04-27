import Dependencies._
import sbt.Keys._

def testScope(project: ProjectReference): ClasspathDep[ProjectReference] = project % "test->test;test->compile"

lazy val buildExampleDockerCommand: Command = Command.command("buildExampleDocker")({
  state =>
    val extracted = Project.extract(state)

      "bakery-state-node/docker:publishLocal" ::
      "bakery-client-example/docker:publishLocal" ::
      "kafka-listener-example/docker:publishLocal" ::
      "bakery-controller/docker:publishLocal" ::
      "project bakery-interaction-make-payment-and-ship-items" ::
      "buildInteractionDockerImage --image-name=interaction-make-payment-and-ship-items --publish=local --interaction=webshop.webservice.MakePaymentInstance --interaction=webshop.webservice.ShipItemsInstance" ::
      "project bakery-interaction-reserve-items" ::
      "buildInteractionDockerImage --image-name=baas-interaction-example-reserve-items --publish=local --interaction=webshop.webservice.ReserveItemsInstance" ::
      "project bakery-smoke-tests" ::
      state
})

val commonSettings = Defaults.coreDefaultSettings ++ Seq(
  organization := "com.ing.baker",
  scalaVersion := "2.12.11",
  crossScalaVersions := Seq("2.12.11"),
  fork := true,
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-v"),
  javacOptions := Seq("-source", jvmV, "-target", jvmV),
  sources in doc := Seq(),
  publishArtifact in packageDoc := false,
  publishArtifact in packageSrc := false,
  scalacOptions := Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-Ywarn-dead-code",
    "-language:higherKinds",
    "-language:existentials",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-encoding", "utf8",
    s"-target:jvm-$jvmV",
    "-Xfatal-warnings"
  ),
  coverageExcludedPackages := "<empty>;.*.javadsl;.*.scaladsl;.*.common;.*.protobuf",
  packageOptions in(Compile, packageBin) +=
    Package.ManifestAttributes(
      "Build-Time" -> new java.util.Date().toString,
      "Build-Commit" -> git.gitHeadCommit.value.getOrElse("No Git Revision Found")
    ),
  resolvers += Resolver.bintrayRepo("cakesolutions", "maven"),
  maintainer in Docker := "The Bakery Team",
  dockerRepository in Docker := sys.env.get("BAAS_DOCKER_REPO"),
  version in Docker := "local" // used by smoke tests for locally built images
)

val dependencyOverrideSettings = Seq(
  dependencyOverrides ++= Seq(
    catsCore,
    akkaActor,
    jnrConstants
  )
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val defaultModuleSettings = commonSettings ++ dependencyOverrideSettings ++ Publish.settings

lazy val scalaPBSettings = Seq(PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value))

lazy val `baker-types` = project.in(file("core/baker-types"))
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "baker-types",
    libraryDependencies ++= compileDeps(
      slf4jApi,
      objenisis,
      scalapbRuntime,
      jodaTime,
      typeSafeConfig,
      scalaReflect(scalaVersion.value),
      scalaLogging
    ) ++ testDeps(scalaTest, scalaCheck, scalaCheckPlus)
  )

lazy val `baker-intermediate-language` = project.in(file("core/baker-intermediate-language"))
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "baker-intermediate-language",
    libraryDependencies ++= compileDeps(
      scalaGraph,
      slf4jApi,
      scalaGraphDot,
      typeSafeConfig,
      scalaLogging
    ) ++ testDeps(scalaTest, scalaCheck, scalaCheckPlus)
  ).dependsOn(`baker-types`)

lazy val `baker-interface` = project.in(file("core/baker-interface"))
  .settings(defaultModuleSettings)
  .settings(scalaPBSettings)
  .settings(
    moduleName := "baker-interface",
    libraryDependencies ++= Seq(
      circe,
      circeParser,
      circeGeneric,
      circeGenericExtras,
      catsEffect,
      scalaJava8Compat
    ) ++ providedDeps(findbugs) ++ testDeps(
      scalaTest
    )
  )
  .dependsOn(`baker-intermediate-language`)

lazy val `baker-runtime` = project.in(file("core/baker-runtime"))
  .settings(defaultModuleSettings)
  .settings(scalaPBSettings)
  .settings(
    moduleName := "baker-runtime",
    // we have to exclude the sources because of a compiler bug: https://issues.scala-lang.org/browse/SI-10134
    sources in(Compile, doc) := Seq.empty,
    libraryDependencies ++=
      compileDeps(
        akkaActor,
        akkaPersistence,
        akkaPersistenceQuery,
        akkaCluster,
        akkaClusterTools,
        akkaClusterSharding,
        akkaBoostrap,
        akkaSlf4j,
        akkaInmemoryJournal,
        ficusConfig,
        catsCore,
        catsEffect,
        scalapbRuntime,
        protobufJava,
        slf4jApi,
        scalaLogging
      ) ++ testDeps(
        akkaStream,
        akkaTestKit,
        akkaMultiNodeTestkit,
        akkaStreamTestKit,
        akkaPersistenceCassandra,
        levelDB,
        levelDBJni,
        betterFiles,
        graphvizJava,
        junitInterface,
        scalaTest,
        scalaCheck,
        scalaCheckPlus,
        scalaCheckPlusMockito,
        mockito)
        ++ providedDeps(findbugs)
  )
  .dependsOn(
    `baker-intermediate-language`,
    `baker-interface`,
    testScope(`baker-recipe-dsl`),
    testScope(`baker-recipe-compiler`),
    testScope(`baker-types`))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)

lazy val `baker-split-brain-resolver` = project.in(file("core/baker-split-brain-resolver"))
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "baker-split-brain-resolver",
    // we have to exclude the sources because of a compiler bug: https://issues.scala-lang.org/browse/SI-10134
    sources in(Compile, doc) := Seq.empty,
    libraryDependencies ++=
      compileDeps(
        akkaActor,
        akkaCluster,
        ficusConfig,
        slf4jApi,
        scalaLogging
      ) ++ testDeps(
        akkaTestKit,
        akkaMultiNodeTestkit,
        scalaTest
      )
  )
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)

lazy val `baker-recipe-dsl` = project.in(file("core/baker-recipe-dsl"))
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "baker-recipe-dsl",
    // we have to exclude the sources because of a compiler bug: https://issues.scala-lang.org/browse/SI-10134
    sources in(Compile, doc) := Seq.empty,
    libraryDependencies ++=
      compileDeps(
        javaxInject,
        paranamer,
        scalaReflect(scalaVersion.value),
      ) ++
        testDeps(
          scalaTest,
          scalaCheck,
          scalaCheckPlus,
          junitInterface,
          slf4jApi
        )
  ).dependsOn(`baker-types`)

lazy val `baker-recipe-compiler` = project.in(file("core/baker-recipe-compiler"))
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "baker-recipe-compiler",
    libraryDependencies ++=
      testDeps(scalaTest, scalaCheck, junitJupiter)
  )
  .dependsOn(`baker-recipe-dsl`, `baker-intermediate-language`, testScope(`baker-recipe-dsl`))

lazy val `bakery-protocol-baker` = project.in(file("bakery/bakery-protocol-baker"))
  .settings(defaultModuleSettings)
  .settings(scalaPBSettings)
  .settings(
    moduleName := "bakery-protocol-baker",
    libraryDependencies ++= Seq(
      http4s,
      http4sDsl
    )
  )
  .dependsOn(`baker-interface`)

lazy val `bakery-protocol-interaction-scheduling` = project.in(file("bakery/bakery-protocol-interaction-scheduling"))
  .settings(defaultModuleSettings)
  .settings(scalaPBSettings)
  .settings(
    moduleName := "bakery-protocol-interaction-scheduling",
    libraryDependencies ++= Seq(
      http4s,
      http4sDsl,
      http4sClient
    )
  )
  .dependsOn(`baker-interface`)

lazy val `bakery-client-template` = project.in(file("bakery/bakery-client-template"))
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "bakery-client-template",
    libraryDependencies ++= Seq(
      http4s,
      http4sDsl,
      http4sClient
    )
  )
  .dependsOn(`baker-interface`, `bakery-protocol-baker`)

lazy val `bakery-state-node` = project.in(file("bakery/bakery-state-node"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(commonSettings ++ Publish.settings)
  .settings(
    moduleName := "state-node",
    scalacOptions ++= Seq(
      "-Ypartial-unification"
    ),
    libraryDependencies ++= Seq(
      slf4jApi,
      logback,
      akkaPersistenceCassandra,
      akkaManagementHttp,
      akkaClusterBoostrap,
      akkaDiscoveryKube,
      skuber,
      http4s,
      http4sDsl,
      http4sServer,
      scalaKafkaClient
    ) ++ testDeps(
      slf4jApi,
      logback,
      scalaTest,
      mockServer,
      circe,
      circeGeneric
    )
  )
  .settings(
    packageSummary in Docker := "The core node",
    packageName in Docker := "bakery-node-state"
  )
  .dependsOn(
    `baker-runtime`,
    `baker-recipe-compiler`,
    `baker-recipe-dsl`,
    `baker-intermediate-language`,
    `bakery-client-template`,
    `bakery-protocol-baker`,
    `bakery-protocol-interaction-scheduling`
  )

lazy val `bakery-interaction-wrapper` = project.in(file("bakery/bakery-interaction-wrapper"))
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "bakery-interaction-wrapper",
    libraryDependencies ++= Seq(
      slf4jApi,
      http4s,
      http4sDsl,
      http4sServer
    ) ++ testDeps(
      scalaTest,
      logback
    )
  )
  .dependsOn(
    `bakery-protocol-interaction-scheduling`,
    `baker-interface`
  )

lazy val `bakery-controller` = project.in(file("bakery/bakery-controller"))
  .settings(defaultModuleSettings)
  .enablePlugins(JavaAppPackaging)
  .settings(
    packageSummary in Docker := "The bakery controller",
    packageName in Docker := "bakery-controller"
  )
  .settings(
    moduleName := "bakery-controller",
    libraryDependencies ++= Seq(
      slf4jApi,
      akkaSlf4j,
      logback,
      scalaLogging,
      skuber,
      http4s,
      http4sDsl,
      http4sServer
    ) ++ testDeps(
      scalaTest,
      logback
    )
  )
  .dependsOn(
    `baker-types`,
    `baker-recipe-compiler`,
    `baker-recipe-dsl`,
    `baker-intermediate-language`,
    `bakery-client-template`,
    `bakery-protocol-interaction-scheduling`)

lazy val baker = project.in(file("."))
  .settings(defaultModuleSettings)
  .aggregate(
    `baker-types`,
    `baker-runtime`,
    `baker-recipe-compiler`,
    `baker-recipe-dsl`,
    `baker-intermediate-language`,
    `baker-split-brain-resolver`,
    `baker-interface`,
    `bakery-controller`,
    `bakery-client-template`,
    `bakery-state-node`,
    `bakery-interaction-wrapper`,
    `bakery-protocol-interaction-scheduling`,
    `bakery-protocol-baker`,
    `bakery-sbt-interaction-docker-build-plugin`)

lazy val `baker-example` = project
  .in(file("examples/baker-example"))
  .enablePlugins(JavaAppPackaging)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    moduleName := "baker-example",
    scalacOptions ++= Seq(
      "-Ypartial-unification"
    ),
    libraryDependencies ++=
      compileDeps(
        slf4jApi,
        http4s,
        http4sDsl,
        http4sServer,
        http4sCirce,
        circe,
        circeGeneric,
        kamon,
        kamonPrometheus,
        akkaPersistenceCassandra,
        akkaPersistenceQuery
      ) ++ testDeps(
        scalaTest,
        scalaCheck,
        junitInterface,
        slf4jApi,
        mockito
      )
  )
  .settings(
    packageSummary in Docker := "A web-shop checkout service example running baker",
    packageName in Docker := "baker-example-app",
    dockerExposedPorts := Seq(8080)
  )
  .dependsOn(`baker-types`, `baker-runtime`, `baker-recipe-compiler`, `baker-recipe-dsl`, `baker-intermediate-language`)

lazy val `bakery-client-example` = project
  .in(file("examples/bakery-client-example"))
  .enablePlugins(JavaAppPackaging)
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "bakery-client-example",
    scalacOptions ++= Seq(
      "-Ypartial-unification"
    ),
    libraryDependencies ++=
      compileDeps(
        slf4jApi,
        http4s,
        http4sDsl,
        http4sServer,
        http4sCirce,
        circe,
        circeGeneric
      ) ++ testDeps(
        scalaTest,
        scalaCheck
      )
  )
  .settings(
    packageSummary in Docker := "A web-shop checkout service example running on Bakery",
    packageName in Docker := "bakery-client-example"
  )
  .dependsOn(`baker-types`, `bakery-client-template`, `baker-recipe-compiler`, `baker-recipe-dsl`)

lazy val `kafka-listener-example` = project
  .in(file("examples/kafka-listener-example"))
  .enablePlugins(JavaAppPackaging)
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "kafka-listener-example",
    scalacOptions ++= Seq(
      "-Ypartial-unification"
    ),
    libraryDependencies ++=
      compileDeps(
        slf4jApi,
        circe,
        circeGeneric,
        circeGenericExtras,
        fs2kafka,
        ficusConfig
      ) ++ testDeps(
        scalaTest,
        scalaCheck
      )
  )
  .settings(
    packageSummary in Docker := "A web-shop checkout service example running on Bakery",
    packageName in Docker := "kafka-listener-example"
  )
  .dependsOn(`baker-types`, `baker-interface`, `baker-recipe-compiler`, `baker-recipe-dsl`)

lazy val `bakery-interaction-reserve-items` = project.in(file("examples/bakery-interaction-reserve-items"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(baas.sbt.BuildInteractionDockerImageSBTPlugin)
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "bakery-interaction-reserve-items",
    scalacOptions ++= Seq(
      "-Ypartial-unification"
    ),
    libraryDependencies ++=
      compileDeps(
        slf4jApi,
        catsEffect
      ) ++ testDeps(
        scalaTest,
        scalaCheck
      )
  )
  .dependsOn(`bakery-interaction-wrapper`)

lazy val `bakery-interaction-make-payment-and-ship-items` = project.in(file("examples/bakery-interaction-make-payment-and-ship-items"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(baas.sbt.BuildInteractionDockerImageSBTPlugin)
  .settings(defaultModuleSettings)
  .settings(
    moduleName := "bakery-interaction-make-payment-and-ship-items",
    scalacOptions ++= Seq(
      "-Ypartial-unification"
    ),
    libraryDependencies ++=
      compileDeps(
        slf4jApi,
        catsEffect
      ) ++ testDeps(
        scalaTest,
        scalaCheck,
        scalaCheckPlus
      )
  )
  .dependsOn(`bakery-interaction-wrapper`)

lazy val `bakery-smoke-tests` = project.in(file("bakery/bakery-smoke-tests"))
  .settings(defaultModuleSettings)
  .settings(noPublishSettings)
  .settings(
    moduleName := "bakery-smoke-tests",
    commands += buildExampleDockerCommand,
    libraryDependencies ++= Seq() ++
      testDeps(
        http4sDsl,
        http4sClient,
        circe,
        slf4jApi,
        scalaTest,
        scalaCheck
      )
  )
  .dependsOn(
    `bakery-client-template`,
    `bakery-client-example`,
    `bakery-interaction-make-payment-and-ship-items`,
    `bakery-interaction-reserve-items`)

lazy val `bakery-sbt-interaction-docker-build-plugin` = project.in(file("sbt-interaction-docker-build-plugin"))
  .settings(defaultModuleSettings)
  .settings(noPublishSettings) // docker plugin can't be published, at least not to azure feed
  .settings(
    // workaround to let plugin be used in the same project without publishing it
    sourceGenerators in Compile += Def.task {
      val file = (sourceManaged in Compile).value / "baas" / "sbt" / "BuildInteractionDockerImageSBTPlugin.scala"
      val sourceFile = IO.readBytes(baseDirectory.value.getParentFile / "project" / "BuildInteractionDockerImageSBTPlugin.scala")
      IO.write(file, sourceFile)
      Seq(file)
    }.taskValue,
    addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.6.0"),
    addSbtPlugin("org.vaslabs.kube" % "sbt-kubeyml" % "0.3.4")
  )
  .enablePlugins(SbtPlugin)
  .enablePlugins(baas.sbt.BuildInteractionDockerImageSBTPlugin)
  .dependsOn(`bakery-interaction-wrapper`)
