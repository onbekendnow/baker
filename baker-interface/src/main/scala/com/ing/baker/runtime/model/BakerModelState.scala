package com.ing.baker.runtime.model

import com.ing.baker.il.CompiledRecipe

case class BakerModelState(recipes: Map[String, (Long, CompiledRecipe)],
                           recipeInstances: Map[String, RecipeInstanceCore]
                          ) { self =>

  def addRecipe(recipe: CompiledRecipe, timestamp: Long): BakerModelState =
    copy(recipes = recipes + (recipe.recipeId -> (timestamp, recipe)))

  def bake(recipe: CompiledRecipe, recipeInstanceId: String): BakerModelState =
    copy(recipeInstances = recipeInstances + (recipeInstanceId -> RecipeInstanceCore.empty(recipe, recipeInstanceId)))

  def setRecipeInstance(recipeInstanceId: String, recipeInstanceState: RecipeInstanceCore): BakerModelState =
    copy(recipeInstances = recipeInstances + (recipeInstanceId -> recipeInstanceState))
}
