package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal
import Validation._

/** Unit Tests For FeatureValidator */
case class FeatureValidator(
  feature: FeatureDef,
  payload: ValidationState
) extends ValidatorBase[FeatureDef](feature)
    with Traversal.FeatureTraveler[ValidationState] {

  def visitBackground(background: Background): Unit = {
    check(
      background.givens.isEmpty,
      s"${feature.identify} has a background specification that is empty",
      MissingWarning
    )
  }

  def visitExample(example: ExampleDef): Unit = {
    check(
      example.description.s.isEmpty,
      s"${example.identify} should have a description",
      MissingWarning
    )
    check(
      example.givens.isEmpty,
      s"${example.identify} should have at least one given ",
      MissingWarning
    )
    check(
      example.whens.isEmpty,
      s"${example.identify} should have at least one when",
      MissingWarning
    )
    check(
      example.thens.isEmpty,
      s"${example.identify} should have at least one then",
      MissingWarning
    )
  }
}
