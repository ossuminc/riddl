package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal
import Validation._

/** Validator for Adaptors */
case class AdaptorValidator(
  adaptor: AdaptorDef,
  payload: ValidationState
) extends ValidatorBase[AdaptorDef](adaptor)
    with Traversal.AdaptorTraveler[ValidationState] {

  override def open(): Unit = {
    super.open()
    adaptor.targetDomain.foreach(x => checkRef[DomainDef](x.id))
    checkRef[ContextDef](adaptor.targetContext.id)
  }
}
