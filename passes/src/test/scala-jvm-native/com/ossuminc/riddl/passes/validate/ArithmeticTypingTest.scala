/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** B4 (Reid, 2026-09-18/21): the typing table for `+ - * /`, string concatenation, time
  * arithmetic, comparisons on expressions, and constant value expressions.
  *
  * Every value expression has a REAL type (Reid, 2026-09-21): a boolean expression is `Boolean`,
  * a numeric expression the SMALLEST constrained numeric type containing its operands and result.
  * The lattice is observed through the mismatch message, which names both operand types: `(3 -
  * 5) + "s"` is refused as an `Integer` and a `String`, so the message is the probe.
  */
class ArithmeticTypingTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def of(msgs: Messages, rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))

  /** An entity whose state has one field of every type the table names; `body` is the fold. */
  private def model(body: String, constants: String = ""): String =
    s"""domain D is {
       |  context C is {
       |$constants
       |    command Go yields event Went is { id: String, n: Natural, w: Whole, i: Integer, r: Real,
       |      dec: Decimal(10,2), money: Current, dur: Duration, t: TimeStamp, s: String, b: Boolean }
       |      with { briefly "c" }
       |    event Went is { id: String } with { briefly "e" }
       |    entity E is {
       |      record Fields is { id: String, count: Integer, opened: TimeStamp, note: String,
       |        flag: Boolean, span: Duration } with { briefly "f" }
       |      state Main of record E.Fields
       |      inlet In is command Go with { briefly "i" }
       |      outlet Out is event Went with { briefly "o" }
       |      handler H is {
       |        on init is { yield event Went(id = "x") }
       |        on g: command Go is {
       |          $body
       |          yield event Went(id = g.id)
       |        }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  /** The type the expression `expr` (over the command's fields) gets, read off the mismatch
    * message of `expr + "probe"` -- a String cannot be added to anything but a String.
    */
  private def typeOf(expr: String, td: TestData): String =
    val found = of(diagnostics(model(s"""let probe = ($expr) + "probe""""), td.name),
      RuleId.ArithmeticOperandMismatch)
    withClue(s"probe for '$expr':") { found.size mustBe 1 }
    val m = found.head.message
    val quoted = "'([^']+)' value".r.findAllMatchIn(m).map(_.group(1)).toSeq
    quoted.headOption.getOrElse(fail(s"no operand type in: $m"))

  "numeric arithmetic" should {

    "type as the smallest constrained numeric type containing both operands and the result" in {
      (td: TestData) =>
        typeOf("g.n + g.n", td) mustBe "Natural"
        typeOf("g.n * 5", td) mustBe "Natural"
        typeOf("g.n - g.n", td) mustBe "Integer" // Natural minus Natural may go negative
        typeOf("g.n / g.n", td) mustBe "Whole" // 1 / 2 is 0
        typeOf("g.w * 5", td) mustBe "Whole"
        typeOf("g.i / g.i", td) mustBe "Integer" // integer division stays Integer
        typeOf("g.i + g.r", td) mustBe "Real"
        typeOf("g.dec + g.dec", td) mustBe "Decimal(10,2)"
        typeOf("g.r + g.dec", td) mustBe "Number"
        typeOf("g.money + g.money", td) mustBe "Current"
        typeOf("g.money + 1", td) mustBe "Number"
        typeOf("0 + 0", td) mustBe "Whole"
        typeOf("-3 * 2", td) mustBe "Integer"
        typeOf("1.5 * 2", td) mustBe "Real"
    }

    "refuse a boolean operand and a string minus a string" in { (td: TestData) =>
      of(diagnostics(model("let x = g.b + 1"), td.name), RuleId.ArithmeticOperandMismatch).size mustBe 1
      of(diagnostics(model("""let x = g.s - "a""""), td.name), RuleId.ArithmeticOperandMismatch).size mustBe 1
    }

    "validate the LoyaltyAccount-shaped fold clean" in { (td: TestData) =>
      val msgs = diagnostics(model("set field E.Fields.count to Fields.count + g.i"), td.name)
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }
  }

  "string concatenation" should {
    "type as String under + and nothing else" in { (td: TestData) =>
      val msgs = diagnostics(model("""set field E.Fields.note to "hello " + g.s"""), td.name)
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
      of(diagnostics(model("""let x = "a" * 2"""), td.name), RuleId.ArithmeticOperandMismatch).size mustBe 1
      of(diagnostics(model("""let x = "a" + 2"""), td.name), RuleId.ArithmeticOperandMismatch).size mustBe 1
    }
  }

  "time arithmetic" should {

    "schedule a send at an instant EXPRESSION, and refuse an offset with no instant" in {
      (td: TestData) =>
        val ok = diagnostics(
          model("send event Went(id = g.id) to outlet E.Out at system.now + 30 days"),
          td.name
        )
        withClue(ok.justErrors.format) { ok.justErrors mustBe empty }
        val bad = diagnostics(
          model("send event Went(id = g.id) to outlet E.Out at system.now + 30"),
          td.name
        )
        of(bad, RuleId.ArithmeticOperandMismatch).size mustBe 1
    }

    "type timestamp minus timestamp as Duration, duration times number as Duration" in {
      (td: TestData) =>
        typeOf("system.now - g.t", td) mustBe "Duration"
        typeOf("g.dur * 2", td) mustBe "Duration"
        typeOf("g.dur + 1 hour", td) mustBe "Duration"
        typeOf("g.t - 2 weeks", td) mustBe "TimeStamp"
        typeOf("30 days", td) mustBe "Duration"
        of(diagnostics(model("let x = g.t * g.dur"), td.name), RuleId.ArithmeticOperandMismatch).size mustBe 1
    }
  }

  "comparisons" should {

    "order timestamps and durations, which used to be silently unchecked" in { (td: TestData) =>
      val ok = diagnostics(model("when Fields.opened + g.dur < system.now then { do \"late\" } end"), td.name)
      withClue(ok.justErrors.format) { ok.justErrors mustBe empty }
      val bad = diagnostics(model("""when g.t < "x" then { do "no" } end"""), td.name)
      (of(bad, RuleId.OrderingNeedsNumeric) ++ of(bad, RuleId.IncomparableKinds)) must not be empty
    }

    "type a comparison as Boolean, so it can be assigned to a boolean field" in { (td: TestData) =>
      val ok = diagnostics(model("set field E.Fields.flag to g.i > 3"), td.name)
      withClue(ok.justErrors.format) { ok.justErrors mustBe empty }
      // and it is a boolean OPERAND: `(a > 3) + 1` is refused as Boolean and Natural
      typeOf("g.i > 3", td) mustBe "Boolean"
    }

    "still draw the literal style warning on a bare literal operand" in { (td: TestData) =>
      of(diagnostics(model("when g.i > 3 then { do \"x\" } end"), td.name),
        RuleId.LiteralComparisonStyle).size mustBe 1
    }
  }

  "constant value expressions" should {

    "accept literals, durations and other constants, holding the result to the declared type" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """do "nothing"""",
            constants =
              """    constant A: Natural = 10 with { briefly "a" }
                |    constant B: Natural = A + 1 with { briefly "b" }
                |    constant W: Duration = 30 days with { briefly "w" }
                |    constant G: Duration = W + 1 day with { briefly "g" }
                |    constant L: String = "a" + "b" with { briefly "l" }
                |    constant R: Natural = constant A * 2 with { briefly "r" }""".stripMargin
          ),
          td.name
        )
        withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "refuse an operand that is not a constant" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """do "nothing"""",
          constants = """    constant K: Natural = Fields.count + 1 with { briefly "k" }"""
        ),
        td.name
      )
      of(msgs, RuleId.ConstantOperandNotConstant).size mustBe 1
    }

    "refuse an expression whose type is not the declared one" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """do "nothing"""",
          constants =
            """    constant S: String = 1 + 2 with { briefly "s" }
              |    constant T: Natural = 30 days with { briefly "t" }""".stripMargin
        ),
        td.name
      )
      of(msgs, RuleId.ConstantExpressionTypeMismatch).size mustBe 2
    }
  }
}
