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

  def visitAction(action: ActionDef): Unit = {
    action match {
      case ma: MessageActionDef =>
        checkRef[EntityDef](ma.receiver.id)
        checkRef[EntityDef](ma.sender.id)
        checkRef[MessageDefinition](ma.message.id)
        ma.reactions.foreach(x => checkRef(x.entity.id))

      case da: DirectiveActionDef =>
        checkRef[EntityDef](da.entity.id)
        checkRef[MessageDefinition](da.message.id)
        checkRef[RoleDef](da.role.id)
        da.reactions.foreach(x => checkRef(x.entity.id))

      case da: DeletionActionDef =>
        checkRef[EntityDef](da.entity.id)
        da.reactions.foreach(x => checkRef(x.entity.id))

      case ca: CreationActionDef =>
        checkRef[EntityDef](ca.entity.id)
        ca.reactions.foreach(x => checkRef(x.entity.id))
    }
  }
}
