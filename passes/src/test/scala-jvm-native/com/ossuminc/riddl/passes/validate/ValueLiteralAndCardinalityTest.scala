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

/** riddl-models, 2026-11-18, replacing `prompt(...)` yield arguments with real values, found three
  * silences being read as rules (BACKLOG [1.28], [1.29]; Reid's rulings the same day):
  *
  *   - a written NUMBER was never typed at a constructor argument, `put`, `return` or `require …
  *     with`, so `5` filled a `DateTime`, `Boolean`, `String` or record field;
  *   - a written STRING filled a time field whatever it said (`"tomorrow"`); it must now be
  *     ISO-8601 for that type, while a String-typed REFERENCE stays accepted;
  *   - a `T?` value filled a required `T` field in silence. It is an ADVISORY, not an Error:
  *     emptiness is a run-time fact, and the generator decides how the empty case fails (a plain
  *     Warning would block generation, CM §0.3 ruling 5).
  *
  * Every silent case asserts the WHOLE model is error-free, so a fixture that failed to parse could
  * not pass vacuously.
  */
class ValueLiteralAndCardinalityTest extends AbstractValidatingTest {

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

  /** `Order` yields `Done(f = <arg>)` where `Done`'s one field `f` is declared `<fieldType>`. In scope:
    * `g` (the `Go` command: `n: Integer`, `s: String`, `maybeN: Integer?`, `many: Integer*`) and the
    * state `E.S` (`opt: C.Price?`, `ts: TimeStamp`, `label: String`, `price: C.Price`).
    */
  private def yieldModel(fieldType: String, arg: String, letLine: String = ""): String =
    s"""domain D is { context C is {
       |  type Price is Decimal(10,2)
       |  type When is DateTime
       |  record R is { x: Integer } with { briefly "r" }
       |  command Go yields event Done is { n: Integer, s: String, maybeN: Integer?, many: Integer* }
       |    with { briefly "c" }
       |  event Done is { f: $fieldType } with { briefly "e" }
       |  entity E is {
       |    record F is { opt: C.Price?, ts: TimeStamp, label: String, price: C.Price }
       |      with { briefly "f" }
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

  private def mustBeClean(fieldType: String, arg: String, td: TestData): Unit =
    val msgs = messages(yieldModel(fieldType, arg), td)
    withClue(clue(msgs)) {
      errors(msgs) mustBe empty
      of(msgs, RuleId.OptionalForRequired) mustBe empty
    }

  private def mustRefuse(fieldType: String, arg: String, rule: RuleId, td: TestData): Message =
    val msgs = messages(yieldModel(fieldType, arg), td)
    withClue(clue(msgs)) {
      errors(msgs).size mustBe 1
      of(msgs, rule).size mustBe 1
    }
    of(msgs, rule).head

  "A written number as a constructor argument [1.28]" should {
    "be refused for a field that is not numeric" in { (td: TestData) =>
      Seq("DateTime", "Boolean", "String", "C.R", "Currency(USD)", "C.When").foreach { ft =>
        withClue(s"field type $ft: ") { mustRefuse(ft, "5", RuleId.ValueTypeMismatch, td) }
      }
    }
    "be accepted for a numeric field, including through an alias" in { (td: TestData) =>
      Seq("Integer", "Real", "Natural", "C.Price", "Integer?").foreach { ft =>
        withClue(s"field type $ft: ") { mustBeClean(ft, "5", td) }
      }
    }
    "be held to the numeric field's range, which it never was at this position" in {
      (td: TestData) =>
        mustRefuse("Natural", "-3", RuleId.NotNatural, td)
        mustRefuse("Integer", "1.5", RuleId.NotWholeNumber, td)
    }
  }

  "A written number at the other typed positions [1.28]" should {
    "be refused by a let with a predefined type" in { (td: TestData) =>
      val msgs = messages(yieldModel("Integer", "g.n", letLine = "let d: DateTime = 5"), td)
      withClue(clue(msgs)) { of(msgs, RuleId.ValueTypeMismatch).size mustBe 1 }
    }
    "be refused by a let with an aliased type" in { (td: TestData) =>
      val msgs = messages(yieldModel("Integer", "g.n", letLine = "let d: type C.When = 5"), td)
      withClue(clue(msgs)) { of(msgs, RuleId.ValueTypeMismatch).size mustBe 1 }
    }
    "be refused by return" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  type When is DateTime
          |  function F is { requires { a: Integer } returns type C.When return 5 }
          |} }
          |""".stripMargin
      val msgs = messages(src, td)
      withClue(clue(msgs)) { of(msgs, RuleId.ValueTypeMismatch).size mustBe 1 }
    }
    "be refused by require … with" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  entity E is {
          |    type Wanted is DateTime
          |    invariant Inv requires type C.E.Wanted is "something must hold"
          |    record Fields is { count: Integer }
          |    state S of record C.E.Fields
          |    handler H is { on other is { require invariant C.E.Inv with 42 } }
          |  }
          |} }
          |""".stripMargin
      val msgs = messages(src, td)
      withClue(clue(msgs)) { of(msgs, RuleId.ValueTypeMismatch).size mustBe 1 }
    }
    "be refused by put" in { (td: TestData) =>
      val src =
        """domain d is {
          |  application context UI is {
          |    type When is DateTime
          |    command Refresh is { ??? }
          |    group Main is { output Panel presents type When }
          |    handler Screen is { on command Refresh { put 5 to output Panel } }
          |  }
          |}
          |""".stripMargin
      val msgs = messages(src, td)
      withClue(clue(msgs)) { of(msgs, RuleId.ValueTypeMismatch).size mustBe 1 }
    }
  }

  "A written string for a time field [1.29](b)" should {
    "be refused unless it is ISO-8601 for that type" in { (td: TestData) =>
      Seq(
        "Date" -> "\"tomorrow\"",
        "DateTime" -> "\"tomorrow\"",
        "TimeStamp" -> "\"tomorrow\"",
        "Date" -> "\"2026-13-01\"",
        "Date" -> "\"2026-11-18T10:30\"",
        "DateTime" -> "\"2026-11-18T25:00\"",
        "DateTime" -> "\"2026-11-18\"",
        "C.When" -> "\"soon\""
      ).foreach { (ft, arg) =>
        withClue(s"$ft = $arg: ") {
          val m = mustRefuse(ft, arg, RuleId.TimeLiteralNotIso8601, td)
          m.message must include("ISO-8601")
        }
      }
    }
    "be accepted when it is" in { (td: TestData) =>
      Seq(
        "Date" -> "\"2026-11-18\"",
        "DateTime" -> "\"2026-11-18T10:30\"",
        "DateTime" -> "\"2026-11-18T10:30:15.250\"",
        "TimeStamp" -> "\"2026-11-18T10:30:00Z\"",
        "TimeStamp" -> "\"2026-11-18T10:30:00-05:00\"",
        "C.When" -> "\"2026-11-18T10:30\""
      ).foreach { (ft, arg) => withClue(s"$ft = $arg: ") { mustBeClean(ft, arg, td) } }
    }
    "leave a String-typed REFERENCE alone, per the ruling" in { (td: TestData) =>
      mustBeClean("DateTime", "g.s", td)
    }
    "leave TimeStamp into Date alone: an intended conversion" in { (td: TestData) =>
      mustBeClean("Date", "system.now", td)
      mustBeClean("Date", "E.S.ts", td)
    }
  }

  "An optional value for a required field [1.29](a)" should {
    "be an Advisory, not an Error or a generation-blocking Warning" in { (td: TestData) =>
      val msgs = messages(yieldModel("C.Price", "E.S.opt"), td)
      withClue(clue(msgs)) {
        errors(msgs) mustBe empty
        val w = of(msgs, RuleId.OptionalForRequired)
        w.size mustBe 1
        w.head.kind mustBe Messages.Advisory
        w.head.message must include("E.S.opt")
      }
    }
    "be warned for a message field and for a zero-or-more value" in { (td: TestData) =>
      Seq("Integer" -> "g.maybeN", "Integer" -> "g.many").foreach { (ft, arg) =>
        withClue(s"$ft = $arg: ") {
          val msgs = messages(yieldModel(ft, arg), td)
          withClue(clue(msgs)) {
            errors(msgs) mustBe empty
            of(msgs, RuleId.OptionalForRequired).size mustBe 1
          }
        }
      }
    }
    "be silent when the field admits empty too, or the value is required" in { (td: TestData) =>
      mustBeClean("C.Price?", "E.S.opt", td)
      mustBeClean("C.Price?", "E.S.price", td)
      mustBeClean("C.Price", "E.S.price", td)
      mustBeClean("Integer?", "g.maybeN", td)
    }
  }
}
