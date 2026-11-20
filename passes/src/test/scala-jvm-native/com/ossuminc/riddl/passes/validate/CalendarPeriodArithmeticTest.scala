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

/** BACKLOG [1.32] (Reid, 2026-11-20): calendar-period arithmetic, `date + 1 month`.
  *
  * A month or a year is not a Duration -- its length depends on where it starts -- so `1 month`
  * is a CALENDAR PERIOD, meaningful only added to or subtracted from a date or time: `Date`,
  * `DateTime`, `ZonedDateTime` or `TimeStamp`, giving the same type. With a `Time` (no calendar),
  * a Duration, another period, or on its own -- a `let`, a field, a Duration position -- it is
  * `value-arithmetic-operand-mismatch`.
  *
  * Every case asserts the whole model's error count, so a fixture that failed to parse cannot pass
  * vacuously; the control asserts the fixture itself is clean.
  */
class CalendarPeriodArithmeticTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    withClue(clue(out)) { out.exists(_.message.startsWith("Expected one of")) mustBe false }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))
  private def errors(msgs: Seq[Message]): Seq[Message] = msgs.filter(_.kind == Messages.Error)
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** `g` is the handled `Go`; each body line is a `let`. */
  private def model(body: String): String =
    s"""domain D is { context C is {
       |  type Zoned is ZonedDateTime(UTC) with { briefly "z" }
       |  command Go is { d: Date, dt: DateTime, z: Zoned, ts: TimeStamp, t: Time,
       |    dur: Duration, month: Integer } with { briefly "c" }
       |  entity E is {
       |    inlet I is command Go
       |    handler H is {
       |      on g: command Go is {
       |        $body
       |        do "used"
       |      }
       |      on other is { error "x" }
       |    } with { briefly "h" }
       |  } with { briefly "e" }
       |} }
       |""".stripMargin

  private def mustBeClean(body: String, td: TestData): Unit =
    val msgs = messages(model(body), td)
    withClue(clue(msgs)) { errors(msgs) mustBe empty }

  private def mustRefuseOnce(body: String, td: TestData): Unit =
    val msgs = messages(model(body), td)
    withClue(clue(msgs)) {
      errors(msgs).size mustBe 1
      of(msgs, RuleId.ArithmeticOperandMismatch).size mustBe 1
    }

  "a calendar period added to a date or time" should {
    "type as that date or time, for every calendar-bearing type" in { (td: TestData) =>
      mustBeClean(
        """let a: Date = g.d + 1 month
          |        let b: DateTime = g.dt - 2 years
          |        let c: Zoned = g.z + 3 months
          |        let e: TimeStamp = g.ts + 1 year
          |        let f: Date = 1 month + g.d
          |        let h: Date = g.d + 1 month + 30 days""".stripMargin,
        td
      )
    }
    "refuse a Time, which has no calendar" in { (td: TestData) =>
      mustRefuseOnce("let a = g.t + 1 month", td)
    }
    "refuse a Duration" in { (td: TestData) =>
      mustRefuseOnce("let a = g.dur + 1 month", td)
    }
    "refuse a period minus a date" in { (td: TestData) =>
      mustRefuseOnce("let a = 1 month - g.d", td)
    }
  }

  "a calendar period on its own" should {
    "refuse a bare period in a let" in { (td: TestData) =>
      mustRefuseOnce("let p = 1 month", td)
    }
    "refuse a bare period where a Duration is declared" in { (td: TestData) =>
      mustRefuseOnce("let p: Duration = 2 years", td)
    }
  }

  "the words month and year" should {
    "stay references when no number precedes them" in { (td: TestData) =>
      mustBeClean(
        """let n: Integer = g.month + 1
          |        let month = 3
          |        let k: Integer = month + 1""".stripMargin,
        td
      )
    }
  }
}
