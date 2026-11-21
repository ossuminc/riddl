/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** Recurrence (Reid, 2026-11-19; riddl-models' usage-metering), in two spellings:
  *
  *   - `send <msg> to <outlet> every <period> [starting [at] <instant>]` -- legal ONLY in `on init`,
  *     because running it registers a timer and anywhere else every handled message would add
  *     another (`stmt-send-every-not-in-init`);
  *   - `on every <period> is { … }` -- a timer clause with no message, port or connector; several
  *     may share a handler.
  *
  * The period is a POSITIVE Duration (a zero period fires continuously); the start is an instant,
  * typed as `send ... at`'s is. A recurring send is otherwise an ordinary send: A6 reachability,
  * outlet ownership and portlet typing apply unchanged.
  */
class RecurrenceValidationTest extends AbstractValidatingTest {

  private def model(initBody: String, clauses: String = "", messageBody: String = """do "close""""): String =
    s"""domain Billing is {
       |  context Metering is {
       |    command CloseMonth is { periodEnd: TimeStamp } with { briefly "c" }
       |    constant Monthly: Duration = 30 days with { briefly "m" }
       |    constant First: TimeStamp = "2026-12-01T00:00:00Z" with { briefly "f" }
       |    constant Label: String = "monthly" with { briefly "l" }
       |    record MeterData is { count: Integer, day: Date } with { briefly "r" }
       |    inlet MonthsIn is command CloseMonth with { briefly "i" }
       |    handler MeteringBoundary is {
       |      on command CloseMonth is { do "close the month" }
       |    } with { briefly "h" }
       |    entity Meter is {
       |      outlet Closes is command CloseMonth with { briefly "o" }
       |      handler Lifecycle is {
       |        on init is {
       |$initBody
       |          morph entity Billing.Metering.Meter to state Billing.Metering.Meter.Counting
       |            with record Billing.Metering.MeterData(count = 0, day = "2026-12-01")
       |        }
       |      } with { briefly "h" }
       |      state Counting of record Billing.Metering.MeterData is {
       |        handler CountingHandler is {
       |$clauses
       |          on closeMonth: command CloseMonth is {
       |            $messageBody
       |          }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |    connector Clock is from outlet Metering.Meter.Closes to inlet Metering.MonthsIn with { briefly "k" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    captured

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  private def clean(msgs: Messages): Unit =
    withClue(msgs.justErrors.map(_.format).mkString("\n")) { msgs.justErrors mustBe empty }

  private val send = "          send command CloseMonth(periodEnd = system.now + 30 days) to outlet Meter.Closes"

  "a recurring send" should {
    "validate in `on init`, with each period spelling and each start" in { (td: TestData) =>
      Seq(
        "every 30 days",
        "every Monthly",
        "every \"P30D\"",
        "every 30 days starting at First",
        "every 30 days starting First",
        "every 1 hour starting at system.now + 1 hour"
      ).zipWithIndex.foreach { (tail, i) =>
        clean(diagnostics(model(s"$send $tail"), s"init-ok-$i"))
      }
    }
    "be an Error outside `on init`" in { (td: TestData) =>
      val msgs = diagnostics(
        model("", messageBody = "send command CloseMonth(periodEnd = system.now) to outlet Meter.Closes every 30 days"),
        "outside-init"
      )
      errorsOf(msgs, RuleId.SendEveryNotInInit).size mustBe 1
    }
    "be an Error in an `on every` clause too" in { (td: TestData) =>
      val clause =
        """          on every 1 hour is {
          |            send command CloseMonth(periodEnd = system.now) to outlet Meter.Closes every 30 days
          |          }""".stripMargin
      errorsOf(diagnostics(model("", clause), "in-every"), RuleId.SendEveryNotInInit).size mustBe 1
    }
    "reject a period that is not a Duration" in { (td: TestData) =>
      Seq("every First", "every Label").zipWithIndex.foreach { (tail, i) =>
        val msgs = diagnostics(model(s"$send $tail"), s"not-duration-$i")
        errorsOf(msgs, RuleId.SendEveryNotDuration).size mustBe 1
      }
    }
    // synapify, 2026-11-20: `valueTypeExpr` has no arm for a bare number, so `every 5` typed as
    // nothing and the period check said nothing.
    "reject a bare number as the period" in { (td: TestData) =>
      val errs = errorsOf(diagnostics(model(s"$send every 5"), "bare-number"), RuleId.SendEveryNotDuration)
      errs.size mustBe 1
      errs.head.message must include("not Duration")
    }
    "reject a zero, negative or vague period" in { (td: TestData) =>
      errorsOf(diagnostics(model(s"$send every 0 days"), "zero"), RuleId.NonPositiveDuration).size mustBe 1
      errorsOf(diagnostics(model(s"$send every -1 days"), "negative"), RuleId.NonPositiveDuration)
        .size mustBe 1
      errorsOf(diagnostics(model(s"""$send every "30""""), "vague"), RuleId.VagueDuration).size mustBe 1
    }
    "reject a start that is not an instant" in { (td: TestData) =>
      val msgs = diagnostics(model(s"$send every 30 days starting at Monthly"), "start-duration")
      val errs = errorsOf(msgs, RuleId.SendAtNotInstant)
      errs.size mustBe 1
      errs.head.message must include("'starting at' instant")
    }
  }

  "an `on every` clause" should {
    "validate with each period spelling, several to a handler" in { (td: TestData) =>
      val clauses =
        """          on every 30 days is { do "roll up" }
          |          on every Monthly is { do "archive" }
          |          on every "PT15M" is { do "sample" }""".stripMargin
      clean(diagnostics(model("", clauses), "clauses-ok"))
    }
    "reject a period that is not a Duration" in { (td: TestData) =>
      val msgs = diagnostics(model("", """          on every First is { do "x" }"""), "clause-not-duration")
      errorsOf(msgs, RuleId.EveryPeriodNotDuration).size mustBe 1
    }
    // synapify, 2026-11-20: both validated with no message at all.
    "reject a bare number as the period" in { (td: TestData) =>
      val msgs = diagnostics(model("", """          on every 5 is { do "x" }"""), "clause-bare-number")
      errorsOf(msgs, RuleId.EveryPeriodNotDuration).size mustBe 1
    }
    "report a period naming nothing" in { (td: TestData) =>
      val msgs = diagnostics(model("", """          on every fortnightly is { do "x" }"""), "clause-unresolved")
      withClue(msgs.map(_.format).mkString("\n")) {
        msgs.justErrors.exists(_.message.contains("fortnightly")) mustBe true
      }
    }
    "accept a calendar or cron schedule" in { (td: TestData) =>
      val clauses =
        """          on every month on day 1 at 09:00 is { do "bill" }
          |          on every weekday at 08:30 is { do "open" }
          |          on every cron "0 9 1 * *" is { do "report" }""".stripMargin
      clean(diagnostics(model("", clauses), "clause-schedules"))
    }
    "reject a zero period" in { (td: TestData) =>
      val msgs = diagnostics(model("", """          on every 0 minutes is { do "x" }"""), "clause-zero")
      errorsOf(msgs, RuleId.NonPositiveDuration).size mustBe 1
    }
    "want statements, like any effect block" in { (td: TestData) =>
      // A Missing warning, which the accumulator drops unless asked for.
      pc.withOptions(CommonOptions(showMissingWarnings = true)) { _ =>
        val msgs = diagnostics(model("", "          on every 1 hour is { ??? }"), "clause-empty")
        withClue(msgs.map(_.format).mkString("\n")) {
          msgs.exists(_.ruleId.contains(RuleId.ClauseNoStatements)) mustBe true
        }
      }
    }
  }
}
