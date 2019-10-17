package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal
import com.yoppworks.ossum.riddl.parser.Traversal.FeatureTraveler
import Validation._

/** Unit Tests For EntityValidator */
case class EntityValidator(
  entity: EntityDef,
  payload: ValidationState
) extends ValidatorBase[EntityDef](entity)
    with Traversal.EntityTraveler[ValidationState] {

  override def open(): Unit = {
    super.open()
    checkTypeExpression(entity.typ)
  }

  def visitProducer(c: ChannelRef): Unit = {
    checkRef[ChannelDef](c.id)
  }

  def visitConsumer(c: ChannelRef): Unit = {
    checkRef[ChannelDef](c.id)
  }

  def visitInvariant(i: InvariantDef): Unit = {}

  def visitFeature(f: FeatureDef): FeatureTraveler[ValidationState] = {
    FeatureValidator(f, payload)
  }

  override def close(): Unit = {
    if (entity.consumes.isEmpty) {
      payload.add(
        ValidationMessage(entity.loc, "An entity must consume a channel")
      )
    }
    if (entity.produces.isEmpty &&
        entity.options.exists { case EntityPersistent(_) => true }) {
      payload.add(
        ValidationMessage(
          entity.loc,
          "An entity that produces no events on a channel cannot be persistent"
        )
      )
    }
  }
}
