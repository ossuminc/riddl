package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal
import Validation._

/** Unit Tests For ContextValidator */
case class ContextValidator(
  context: ContextDef,
  payload: ValidationState
) extends ValidatorBase[ContextDef](context)
    with Traversal.ContextTraveler[ValidationState] {

  def open(): Unit = {}

  def visitType(t: TypeDef): Traversal.TypeTraveler[ValidationState] = {
    TypeValidator(t, payload)
  }

  def visitCommand(command: CommandDef): Unit = {
    if (command.events.isEmpty) {
      payload.add(
        ValidationMessage(
          command.loc,
          "Commands must always yield at least one event"
        )
      )
    }
  }

  def visitEvent(event: EventDef): Unit = {}

  def visitQuery(query: QueryDef): Unit = {}

  def visitResult(result: ResultDef): Unit = {}

  def visitAdaptor(
    a: AdaptorDef
  ): Traversal.AdaptorTraveler[ValidationState] =
    AdaptorValidator(a, payload)

  def visitInteraction(
    i: InteractionDef
  ): Traversal.InteractionTraveler[ValidationState] =
    InteractionValidator(i, payload)

  def visitEntity(
    e: EntityDef
  ): Traversal.EntityTraveler[ValidationState] = {
    EntityValidator(e, payload)
  }

  override def close(): Unit = {
    if (context.entities.isEmpty) {
      payload.add(
        ValidationMessage(
          context.loc,
          "Contexts that define no entities are not valid",
          Error
        )
      )
    }
  }
}
