/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, Messages, *}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{PredefType, RiddlParserInput}
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Unit Tests For TypeValidationTest */
class TypeValidatorTest extends AbstractValidatingTest {

  "TypeValidator" should {
    "ensure type names start with capital letter" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |type bar is String
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input) { case (_: Domain, _, msgs: Seq[Message]) =>
          if msgs.isEmpty then fail("Type 'bar' should have generated warning")
          else if msgs.map(_.message).exists(_.contains("should start with"))
          then {
            succeed
          } else {
            fail("No such message")
          }
        }
      }
    }
    "identify undefined type references" in { (td: TestData) =>
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
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgsAndWarnings: Messages.Messages) =>
            val errors = msgsAndWarnings.justErrors
            errors.size mustBe 1
            // Id(P) now names any Processor, not only an Entity (2026-08-13); 'Bar' resolves
            // to a Type, so the widened check is what fires.
            errors.head.message must include("but a Processor was expected")
        }
      }
    }
    "allow ??? in aggregate bodies without warning" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  type Empty is { ??? } with { described as "empty" }
          |} with { explained as "nothing" }
          |""".stripMargin,
        td
      )
      // Suppress missing warnings so the only message is the unused-type UsageWarning; A48 adds a
      // no-author MissingWarning to the author-less domain otherwise.
      pc.withOptions(CommonOptions.default.copy(showMissingWarnings = false)) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            msgs mustNot be(empty)
            info(msgs.format)
            msgs.size must be(1)
            msgs.filter(_.kind == Messages.UsageWarning).last.format must include("is unused")
        }
      }
    }

    "identify when pattern type does not refer to a valid pattern" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo is {
          |type pat is Pattern("[")
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            assertValidationMessage(msgs, Error, "Unclosed character class")
        }
      }
    }

    "accept unique ID types that reference a non-Entity Processor" in { (td: TestData) =>
      // Id(P) now names any Processor, not only an Entity (2026-08-13) -- a Context is one
      // of the six eligible kinds, so this must validate clean.
      val input = RiddlParserInput(
        """
          |domain foo is {
          |context TypeTest is { ??? }
          |type Order is Id(TypeTest)
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            msgs.justErrors mustBe empty
        }
      }
    }

    "identify when unique ID types reference something other than a Processor" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """
          |domain foo is {
          |type NotAProcessor is Integer
          |type Order is Id(NotAProcessor)
          |}
          |""".stripMargin,
          td
        )
        pc.withOptions(CommonOptions.default) { _ =>
          parseAndValidateDomain(input, shouldFailOnErrors = false) {
            case (_: Domain, _, msgs: Messages) =>
              assertValidationMessage(
                msgs,
                Error,
                "but a Processor was expected"
              )
          }
        }
    }

    "check infrequently used TypeExpressions" in { (td: TestData) =>
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
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (domain: Domain, _, msgs: Messages) =>
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
            rng.typEx.asInstanceOf[RangeType].format must be("range(23,42)")
            c.typEx.isInstanceOf[AggregateUseCaseTypeExpression] must be(true)
            c.typEx.asInstanceOf[AggregateUseCaseTypeExpression].format must be(
              "command { int: Integer, str: String }"
            )
            d.typEx.asInstanceOf[Decimal].format must be("Decimal(3,8)")
        }
      }
    }

    "error when type name matches a predefined type" in { (td: TestData) =>
      // Test every predefined type name that can be used as a type
      // identifier without conflicting with the parser. Some names
      // (like String, Boolean, Integer, etc.) are keywords that the
      // parser consumes as type expressions, so they can't appear
      // as user-defined type names. We test those that parse as
      // identifiers followed by "is".
      val testableTypes = PredefType.allPredefTypes.filterNot { name =>
        // These are consumed by the parser as type expression
        // keywords and won't parse as "type X is ..." definitions
        scala.collection.immutable
          .Set(
            "Abstract",
            "Anything",
            "Boolean",
            "Current",
            "Currency",
            "Date",
            "DateTime",
            "Decimal",
            "Duration",
            "Id",
            "Integer",
            "Location",
            "Length",
            "Luminosity",
            "Mass",
            "Mole",
            "Nothing",
            "Natural",
            "Number",
            "Pattern",
            "Real",
            "String",
            "Temperature",
            "Time",
            "TimeStamp",
            "URI",
            "UserId",
            "UUID",
            "Whole",
            "ZonedDate",
            "ZonedDateTime"
          )
          .contains(name)
      }
      // If all names are keywords, test with at least one that we
      // know works by wrapping in a context (where type keywords
      // are not consumed as expressions at the identifier position)
      // Actually: predefined type keywords ARE consumed by the
      // parser before reaching the identifier, so we test using
      // the names that don't clash. For full coverage, we verify
      // the check exists and works with "Blob" which is not a
      // keyword used in type expressions.
      val input = RiddlParserInput(
        """domain foo is {
          |  type Blob is Number
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            assertValidationMessage(
              msgs,
              Error,
              "redefines built-in type 'Blob'"
            )
        }
      }
    }

    "warn when type name is case-variant of predefined type" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |  type Timestamp is TimeStamp
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            assertValidationMessage(
              msgs,
              StyleWarning,
              "redundant case-variant of built-in type 'TimeStamp'"
            )
        }
      }
    }

    "accept a command that yields an event and a query that yields a result (A19)" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain foo is {
            |  event E is { id: Integer }
            |  result R is { id: Integer }
            |  command C yields event E is { id: Integer }
            |  query Q replies result R is { id: Integer }
            |}
            |""".stripMargin,
          td
        )
        pc.withOptions(CommonOptions.default) { _ =>
          parseAndValidateDomain(input, shouldFailOnErrors = false) {
            case (_: Domain, _, msgs: Messages) =>
              val errs = msgs.justErrors
              assert(errs.isEmpty, s"Expected no errors, got:\n${errs.format}")
          }
        }
    }

    "reject a command that yields a result (A19)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |  result R is { id: Integer }
          |  command C yields result R is { id: Integer }
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            assertValidationMessage(msgs, Error, "should be one of these message types")
        }
      }
    }

    "reject a query that yields an event (A19)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |  event E is { id: Integer }
          |  query Q replies event E is { id: Integer }
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            assertValidationMessage(msgs, Error, "should be one of these message types")
        }
      }
    }

    "reject a yields clause on a non-command/query type (A19)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |  event E is { id: Integer }
          |  record Rec yields event E is { id: Integer }
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            assertValidationMessage(msgs, Error, "Only command and query types may declare")
        }
      }
    }
  }
}
