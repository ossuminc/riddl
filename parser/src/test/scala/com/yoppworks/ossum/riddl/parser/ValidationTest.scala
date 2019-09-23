package com.yoppworks.ossum.riddl.parser

import AST.DomainDef
import AST._
import Validation._
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ValidatorTest */
class ValidationTest extends WordSpec with MustMatchers {

  "ValidatorTest" should {
    "identify duplicate domain definitions" in {
      val errors = validate(
        Seq(
          DomainDef(0, Seq.empty[String], "foo"),
          DomainDef(0, Seq.empty[String], "foo")
        )
      )
      errors.isEmpty mustNot be(true)
      errors.head.message.contains("foo") must be(true)
    }
  }
}
