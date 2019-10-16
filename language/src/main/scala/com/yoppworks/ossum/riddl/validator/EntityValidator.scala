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

  def open(): Unit = {}

  def visitProducer(c: ChannelRef): Unit = {}

  def visitConsumer(c: ChannelRef): Unit = {}

  def visitInvariant(i: InvariantDef): Unit = {}

  def visitFeature(f: FeatureDef): FeatureTraveler[ValidationState] = {
    FeatureValidator(f, payload)
  }
}
