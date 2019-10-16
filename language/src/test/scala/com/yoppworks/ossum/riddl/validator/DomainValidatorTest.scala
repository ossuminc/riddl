package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST.DomainDef
import com.yoppworks.ossum.riddl.parser.AST.Identifier
import com.yoppworks.ossum.riddl.validator.Validation._
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ValidatorTest */
class DomainValidatorTest extends WordSpec with MustMatchers {

  "DomainValidator" should {
    "identify duplicate domain definitions" in {
      val errors = validate(
        Seq(
          DomainDef((0, 0), Identifier((0, 0), "foo")),
          DomainDef((0, 0), Identifier((0, 0), "foo"))
        )
      )
      errors.isEmpty mustNot be(true)
      errors.head.message.contains("foo") must be(true)
    }
  }
}
