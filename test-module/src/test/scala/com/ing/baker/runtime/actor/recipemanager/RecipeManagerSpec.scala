package com.ing.baker.runtime.actor.recipemanager

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.ing.baker.TestRecipeHelper
import com.ing.baker.compiler.RecipeCompiler
import com.ing.baker.runtime.actor.recipemanager.RecipeManager.{AddRecipe, GetRecipe, NoRecipeFound, RecipeFound}
import com.ing.baker.runtime.core.BakerExecutionSpec
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.concurrent.Await

object RecipeManagerSpec {
  val config: Config = ConfigFactory.parseString(
    """
      |akka.persistence.journal.plugin = "inmemory-journal"
      |akka.persistence.snapshot-store.plugin = "inmemory-snapshot-store"
      |akka.test.timefactor = 3.0
    """.stripMargin)
}

class RecipeManagerSpec  extends TestRecipeHelper {

  override def actorSystemName = "RecipeManagerSpec"

  val log = LoggerFactory.getLogger(classOf[BakerExecutionSpec])

  "The RecipeManagerSpec" should {
    "Add a recipe to the list when a AddRecipe message is received" in {
      val compiledRecipe = RecipeCompiler.compileRecipe(getComplexRecipe("AddRecipeRecipe"))
      val recipeManager: ActorRef = defaultActorSystem.actorOf(RecipeManager.props(),  s"recipeManager-${UUID.randomUUID().toString}")

      val futureAddResult = recipeManager.ask(AddRecipe(compiledRecipe))(timeout)
      val recipeId = Await.result(futureAddResult, timeout) match {
        case RecipeManager.AddRecipeResponse(x) => x
        case _ => fail("Adding recipe failed")
      }

      val futureGetResult = recipeManager.ask(GetRecipe(recipeId))(timeout)
      Await.result(futureGetResult, timeout) match {
        case RecipeFound(recipe) => recipe
        case NoRecipeFound => fail("Recipe not found")
        case _ => fail("Unknown response received")
      }
    }
  }

}