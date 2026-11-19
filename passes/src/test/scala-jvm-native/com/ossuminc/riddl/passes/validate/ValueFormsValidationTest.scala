/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** BACKLOG [1.27] value forms: typing and the new rules.
  *
  * Each case yields `Done(f = <arg>)` with `f` declared `<fieldType>`, so a form's TYPE is tested
  * by what it may fill: the existing assignability check and the optional-for-required Advisory
  * see it through `valueTypeExpr`. Every silent case asserts the WHOLE model is error-free, so a
  * fixture that failed to parse could not pass vacuously.
  */
class ValueFormsValidationTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))
  private def errors(msgs: Seq[Message]): Seq[Message] = msgs.filter(_.kind == Messages.Error)
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** In scope: `g` (the `Go` command) and the state `E.S`, both described in the record below. */
  private def model(fieldType: String, arg: String, letLine: String = ""): String =
    s"""domain D is { context C is {
       |  record Money is { amount: Integer, currency: String } with { briefly "m" }
       |  record Line is { sku: String, qty: Integer, price: Decimal(10,2), cost: C.Money,
       |    wait: Duration } with { briefly "l" }
       |  command Go yields event Done is { sku: String, n: Integer } with { briefly "c" }
       |  event Done is { f: $fieldType } with { briefly "e" }
       |  entity E is {
       |    record F is { lines: C.Line*, wait: Duration, maybeWait: Duration?, t1: TimeStamp,
       |      t2: TimeStamp, label: String, n: Integer, maybeN: Integer?, waits: Duration*,
       |      counts: Integer*, flag: Boolean } with { briefly "f" }
       |    state S of record E.F
       |    inlet I is command Go
       |    outlet O is event Done
       |    handler H is {
       |      on g: command Go is {
       |        $letLine
       |        yield event Done(f = $arg)
       |      }
       |      on other is { error "x" }
       |    }
       |  }
       |} }
       |""".stripMargin

  private def mustBeClean(fieldType: String, arg: String, td: TestData, letLine: String = ""): Unit =
    val msgs = messages(model(fieldType, arg, letLine), td)
    withClue(clue(msgs)) {
      errors(msgs) mustBe empty
      of(msgs, RuleId.OptionalForRequired) mustBe empty
    }

  private def mustAdvise(fieldType: String, arg: String, td: TestData, letLine: String = ""): Unit =
    val msgs = messages(model(fieldType, arg, letLine), td)
    withClue(clue(msgs)) {
      errors(msgs) mustBe empty
      of(msgs, RuleId.OptionalForRequired).size mustBe 1
    }

  private def mustRefuse(rule: RuleId, fieldType: String, arg: String, td: TestData): Seq[Message] =
    val msgs = messages(model(fieldType, arg), td)
    val found = of(msgs, rule)
    withClue(clue(msgs)) { found must not be empty }
    found

  "`<unit> of d`" should {
    "be an Integer" in { (td: TestData) =>
      mustBeClean("Integer", "minutes of E.S.wait", td)
      mustBeClean("Integer", "days of (E.S.t2 - E.S.t1)", td)
      mustBeClean("Integer", "hours of E.S.wait * 2", td)
      mustBeClean("Integer", "minutes of 90 seconds", td)
    }
    "not fill a String" in { (td: TestData) =>
      val msgs = messages(model("String", "minutes of E.S.wait"), td)
      withClue(clue(msgs)) { errors(msgs) must not be empty }
    }
    "be optional of an optional duration" in { (td: TestData) =>
      mustBeClean("Integer?", "minutes of E.S.maybeWait", td)
      mustAdvise("Integer", "minutes of E.S.maybeWait", td)
    }
    "refuse an operand that is not a Duration" in { (td: TestData) =>
      val found = mustRefuse(RuleId.DurationUnitsNotDuration, "Integer", "minutes of E.S.label", td)
      found.head.message must include("'E.S.label'")
      mustRefuse(RuleId.DurationUnitsNotDuration, "Integer", "minutes of E.S.waits", td)
    }
  }

  "`sum of`" should {
    "sum numbers, Durations and Currency to their own type" in { (td: TestData) =>
      mustBeClean("Integer", "sum of E.S.counts", td)
      mustBeClean("Integer", "sum of E.S.lines as l by l.qty", td)
      mustBeClean("Decimal(10,2)", "sum of E.S.lines as l by l.price * l.qty", td)
      mustBeClean("Duration", "sum of E.S.waits", td)
      mustBeClean("Duration", "sum of E.S.lines as l by l.wait", td)
      mustBeClean("Integer", "sum of E.S.lines as l by l.cost.amount", td)
    }
    "refuse what does not add" in { (td: TestData) =>
      mustRefuse(RuleId.SumNotSummable, "Integer", "sum of E.S.lines", td)
        .head.message must include("each element")
      mustRefuse(RuleId.SumNotSummable, "Integer", "sum of E.S.lines as l by l.sku", td)
        .head.message must include("'l.sku'")
      mustRefuse(RuleId.SumNotSummable, "Integer", "sum of E.S.lines as l by l.cost", td)
    }
    "refuse an operand that is not a collection" in { (td: TestData) =>
      mustRefuse(RuleId.NotACollection, "Integer", "sum of E.S.n", td)
    }
    "bind the element in the projection only" in { (td: TestData) =>
      val msgs = messages(model("Integer", "sum of E.S.lines as l by l.nosuch"), td)
      withClue(clue(msgs)) { errors(msgs) must not be empty }
      val outside = messages(model("Integer", "l.qty", "let s = sum of E.S.lines as l by l.qty"), td)
      withClue(clue(outside)) { errors(outside) must not be empty }
    }
    "not fill a String" in { (td: TestData) =>
      val msgs = messages(model("String", "sum of E.S.counts"), td)
      withClue(clue(msgs)) { errors(msgs) must not be empty }
    }
  }

  "`one of`" should {
    "be the element type, made optional" in { (td: TestData) =>
      mustBeClean("C.Line?", "one of E.S.lines as l where l.sku == g.sku", td)
      mustAdvise("C.Line", "one of E.S.lines as l where l.sku == g.sku", td)
    }
    "chain into a field of the result, which is optional" in { (td: TestData) =>
      val line = "let line = one of E.S.lines as l where l.sku == g.sku"
      mustBeClean("Integer?", "line.qty", td, line)
      mustAdvise("Integer", "line.qty", td, line)
      val bad = messages(model("Integer?", "line.nosuch", line), td)
      withClue(clue(bad)) { errors(bad) must not be empty }
    }
    "require a boolean predicate and a collection" in { (td: TestData) =>
      val notBool = messages(model("C.Line?", "one of E.S.lines as l where l.sku"), td)
      withClue(clue(notBool)) { errors(notBool) must not be empty }
      mustRefuse(RuleId.NotACollection, "C.Line?", "one of E.S.n as l where l.qty > 0", td)
    }
    "bind the element in the predicate only" in { (td: TestData) =>
      val msgs = messages(model("C.Line?", "one of E.S.lines as l where l.nosuch > 0"), td)
      withClue(clue(msgs)) { errors(msgs) must not be empty }
    }
  }
}
