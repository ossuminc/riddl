package com.yoppworks.ossum.idddl.parser

import com.yoppworks.ossum.idddl.parser.AST.{DomainDef, _}
import com.yoppworks.ossum.idddl.parser.Validator._
import org.scalatest.{MustMatchers, WordSpec}

/** Unit Tests For ValidatorTest */
class ValidatorTest extends WordSpec with MustMatchers {

  "ValidatorTest" should {
    "handle unknown definition types" in {
      validate(Seq(ResultDef(0,"foo",String))) match {
        case Seq(valError) =>
          valError.message mustBe "Unknown Definition"
        case Seq() =>
          fail("Unknown Definition was not detected")
      }
    }
    "identify duplicate domain definitions" in {
      val errors = validate(Seq(
        DomainDef(0,DomainPath(Seq("foo")),Seq.empty[ContextDef]),
        DomainDef(0,DomainPath(Seq("foo")),Seq.empty[ContextDef])
      ))
      errors.isEmpty mustNot be(true)
      println(errors.head)
    }
  }
}
