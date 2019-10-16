package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal
import Validation._

/** Unit Tests For DomainValidator */
case class DomainValidator(
  domain: DomainDef,
  payload: ValidationState = ValidationState()
) extends ValidatorBase[DomainDef](domain)
    with Traversal.DomainTraveler[ValidationState] {

  def open(): Unit = {}

  def visitType(
    typ: TypeDef
  ): Traversal.TypeTraveler[ValidationState] = {
    TypeValidator(typ, payload)
  }

  def visitChannel(
    channel: ChannelDef
  ): Traversal.ChannelTraveler[ValidationState] = {
    ChannelValidator(channel, payload)
  }

  def visitInteraction(
    i: InteractionDef
  ): Traversal.InteractionTraveler[ValidationState] = {
    InteractionValidator(i, payload)
  }

  def visitContext(
    context: ContextDef
  ): Traversal.ContextTraveler[ValidationState] = {
    ContextValidator(context, payload)
  }
}
