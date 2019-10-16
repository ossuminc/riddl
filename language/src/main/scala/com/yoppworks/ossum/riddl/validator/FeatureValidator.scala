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

  def open(): Unit = {}

  def visitBackground(background: Background): Unit = {}
  def visitExample(example: ExampleDef): Unit = {}
}
