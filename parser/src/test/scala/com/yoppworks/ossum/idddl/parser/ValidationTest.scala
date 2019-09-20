package com.yoppworks.ossum.idddl.parser

import com.yoppworks.ossum.idddl.parser.AST.{DomainDef, _}
import com.yoppworks.ossum.idddl.parser.Validation._
import org.scalatest.{MustMatchers, WordSpec}

/** Unit Tests For ValidatorTest */
class ValidationTest extends WordSpec with MustMatchers {

  "ValidatorTest" should {
    "identify duplicate domain definitions" in {
      val errors = validate(Seq(
        DomainDef(0,DomainPath(Seq("foo"))),
        DomainDef(0,DomainPath(Seq("foo")))
      ))
      errors.isEmpty mustNot be(true)
      errors.head.message.contains("foo") must be(true)
    }
  }
}
