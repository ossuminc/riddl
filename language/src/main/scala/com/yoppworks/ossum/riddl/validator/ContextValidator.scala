package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal._
import Validation._

/** Unit Tests For ContextValidator */
case class ContextValidator(
  context: ContextDef,
  payload: ValidationState
) extends ValidatorBase[ContextDef](context)
    with ContextTraveler[ValidationState] {

  def visitType(t: TypeDef): TypeTraveler[ValidationState] = {
    TypeValidator(t, payload)
  }

  def visitCommand(command: CommandDef): Unit = {
    checkTypeExpression(command.typ)
    if (command.events.isEmpty) {
      payload.add(
        ValidationMessage(
          command.loc,
          "Commands must always yield at least one event"
        )
      )
    } else {
      command.events.foreach { eventRef =>
        checkRef[EventDef](eventRef.id)
      }
    }
  }

  def visitEvent(event: EventDef): Unit = {
    checkTypeExpression(event.typ)
  }

  def visitQuery(query: QueryDef): Unit = {
    checkTypeExpression(query.typ)
    checkRef[ResultDef](query.result.id)
  }

  def visitResult(result: ResultDef): Unit = {
    checkTypeExpression(result.typ)
  }

  override def visitChannel(
    chan: ChannelDef
  ): ChannelTraveler[ValidationState] = {
    ChannelValidator(chan, payload)
  }

  def visitAdaptor(
    a: AdaptorDef
  ): AdaptorTraveler[ValidationState] =
    AdaptorValidator(a, payload)

  def visitInteraction(
    i: InteractionDef
  ): InteractionTraveler[ValidationState] =
    InteractionValidator(i, payload)

  def visitEntity(
    e: EntityDef
  ): EntityTraveler[ValidationState] = {
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
