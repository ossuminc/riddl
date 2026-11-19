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

/** riddl-models, 2026-11-19 (usage-metering): a periodic timer -- "close this month, and arrange to
  * close the next one" -- is an `on X` clause that schedules the next X for later. `stream-graph-cycle`
  * reported it as an infinite message loop and never looked at the `at`, so a recurrence had no legal
  * spelling.
  *
  * Reid's ruling (2026-11-19): a scheduled re-emission whose instant ADVANCES is a recurrence, not a
  * loop. "Advances" is read from the text: the `at` value is `<instant> + <duration>` (B4 types the
  * sum, so a sum that is an instant has a Duration on one side). Any other instant keeps the error,
  * because CM §20.7 delivers a PAST instant immediately: `at closeMonth.periodEnd` has already passed
  * when it fires, so it would re-fire at once with the same value, forever -- exactly the loop the
  * rule forbids.
  */
class RecurringScheduledSendTest extends AbstractValidatingTest {

  private def model(sends: String): String =
    s"""domain Billing is {
       |  context Metering as flow is {
       |    command CloseMonth is { periodEnd: TimeStamp }
       |    inlet MonthsIn is command CloseMonth
       |    outlet MonthsOut is command CloseMonth
       |    connector 'Month Clock' is from outlet Metering.MonthsOut to inlet Metering.MonthsIn
       |    handler MeteringBoundary is {
       |      on closeMonth: command CloseMonth is {
       |        do "roll this month's usage up"
       |$sends
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private val next = "send command CloseMonth(periodEnd = closeMonth.periodEnd + 30 days) to outlet Metering.MonthsOut"

  private def cycles(source: String, origin: String): Seq[Message] =
    var captured: Messages = Messages.empty
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    withClue(captured.filter(_.kind >= Messages.Error).map(_.format).mkString("\n")) {
      captured.filter(m => m.kind >= Messages.Error && !m.ruleId.contains(RuleId.GraphCycle)) mustBe empty
    }
    captured.filter(m => m.kind == Messages.Error && m.ruleId.contains(RuleId.GraphCycle))

  "a scheduled re-emission" should {
    "validate when its instant advances by a duration (the task's repro)" in { (td: TestData) =>
      cycles(model(s"        $next at closeMonth.periodEnd + 30 days"), "advancing") mustBe empty
    }
    "validate when the duration is added on the left" in { (td: TestData) =>
      cycles(model(s"        $next at 30 days + closeMonth.periodEnd"), "advancing-left") mustBe empty
    }
    "validate when it advances from system.now" in { (td: TestData) =>
      cycles(model(s"        $next at system.now + 30 days"), "advancing-now") mustBe empty
    }
  }

  "a re-emission that does not advance" should {
    "still be a loop without `at`" in { (td: TestData) =>
      cycles(model(s"        $next"), "unscheduled").size mustBe 1
    }
    "still be a loop at an instant that is already past when it fires" in { (td: TestData) =>
      cycles(model(s"        $next at closeMonth.periodEnd"), "past").size mustBe 1
    }
    "still be a loop at system.now" in { (td: TestData) =>
      cycles(model(s"        $next at system.now"), "now").size mustBe 1
    }
    "still be a loop when the instant moves BACKWARD" in { (td: TestData) =>
      cycles(model(s"        $next at closeMonth.periodEnd - 30 days"), "backward").size mustBe 1
    }
    "still be a loop when the same clause also re-emits immediately" in { (td: TestData) =>
      val both = s"        $next at closeMonth.periodEnd + 30 days\n        $next"
      cycles(model(both), "both").size mustBe 1
    }
    "say how to make it a recurrence" in { (td: TestData) =>
      pc.withOptions(CommonOptions(provideTips = true)) { _ =>
        val msg = cycles(model(s"        $next at closeMonth.periodEnd"), "suggest").head
        msg.suggestion must include("+ <duration>")
      }
    }
  }
}
