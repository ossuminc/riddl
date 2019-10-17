package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal
import Validation._

/** Unit Tests For InteractionValidator */
case class InteractionValidator(
  interaction: InteractionDef,
  payload: ValidationState
) extends ValidatorBase[InteractionDef](interaction)
    with Traversal.InteractionTraveler[ValidationState] {

  def visitRole(role: RoleDef): Unit = {}

  def visitAction(action: ActionDef): Unit = {}
}
