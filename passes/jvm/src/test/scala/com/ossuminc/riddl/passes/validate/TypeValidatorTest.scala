/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, ec, pc}
import org.scalatest.TestData

/** Unit Tests For TypeValidationTest */
class TypeValidatorTest extends AbstractValidatingTest {

  pc.setOptions(CommonOptions.default)
  "TypeValidator" should {
    "ensure type names start with capital letter" in { (td:TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |type bar is String
          |}
          |""".stripMargin,td
      )
      parseAndValidateDomain(input) { case (_: Domain, _, msgs: Seq[Message]) =>
        if msgs.isEmpty then fail("Type 'bar' should have generated warning")
        else if msgs.map(_.message).exists(_.contains("should start with"))
        then { succeed }
        else { fail("No such message") }
      }
    }
    "identify undefined type references" in { (td:TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo is {
          |  type Foo is Number
          |  type Bar is Integer
          |  type Rename is Bar
          |  type OneOrMore is many Bar
          |  type ZeroOrMore is many optional Bar
          |  type Optional is optional Bar
          |  type Aggregate is {a: Bar, b: Foo}
          |  result AggregateResult is Aggregate
          |  type Alternation is one of { Bar or Foo }
          |  type Order is Id(Bar)
          |}
          |""".stripMargin,td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) {
        case (_: Domain, _, msgsAndWarnings: Messages.Messages) =>
          val errors = msgsAndWarnings.justErrors
          errors.size mustBe (1)
          errors.head.message must include("but an Entity was expected")
      }
    }
    "allow ??? in aggregate bodies without warning" in { (td:TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  type Empty is { ??? } with { described as "empty" }
          |} with { explained as "nothing" }
          |""".stripMargin,td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        msgs mustNot be(empty)
        info(msgs.format)
        msgs.size must be (4)
        msgs.filter(_.kind == Messages.UsageWarning).last.format must include("is unused")
      }
    }

    "identify when pattern type does not refer to a valid pattern" in { (td:TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo is {
          |type pat is Pattern("[")
          |}
          |""".stripMargin,td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "Unclosed character class")
      }
    }

    "identify when unique ID types reference something other than an entity" in { (td:TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo is {
          |context TypeTest is { ??? }
          |type Order is Id(TypeTest)
          |}
          |""".stripMargin,td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Path 'TypeTest' resolved to Context 'TypeTest' at empty(3:1), in Type 'Order', but an Entity"
        )
      }
    }

    "check infrequently used TypeExpressions" in { (td:TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo is {
          |  type SI = set of Integer
          |  type SN = sequence of Number
          |  type r = replica of Integer
          |  type rng = range(23,42)
          |  type d = Decimal(3,8)
          |  command c(int: Integer, str: String)
          |}
          |""".stripMargin,td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain: Domain, _, msgs: Messages) =>
        msgs.justErrors must be(empty)
        val si = domain.types.find("SI").get
        val sn = domain.types.find("SN").get
        val r = domain.types.find("r").get
        val rng = domain.types.find("rng").get
        val d = domain.types.find("d").get
        val c = domain.types.find("c").get
        si.typEx.isInstanceOf[Set] must be(true)
        si.typEx.asInstanceOf[Set].format must be("set of Integer")
        sn.typEx.isInstanceOf[Sequence] must be(true)
        sn.typEx.asInstanceOf[Sequence].format must be("sequence of Number")
        r.typEx.isInstanceOf[Replica] must be(true)
        r.typEx.asInstanceOf[Replica].format must be("replica of Integer")
        rng.typEx.isInstanceOf[RangeType] must be(true)
        rng.typEx.asInstanceOf[RangeType].format must be("Range(23,42)")
        c.typEx.isInstanceOf[AggregateUseCaseTypeExpression] must be(true)
        c.typEx.asInstanceOf[AggregateUseCaseTypeExpression].format must be("command { int: Integer, str: String }")
      }

    }
  }
}
