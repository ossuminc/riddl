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

/** BACKLOG [1.32] (Reid, 2026-11-20): what validation says about a calendar or cron schedule.
  *
  * A schedule is a legal recurrence period in `on every` and in `send … every`. Errors are for
  * what can NEVER fire or cannot be read: a day outside 1..31, a yearly date no year has
  * (`february 30`), a non-positive interval, a weekday named twice, a time of day past 23:59:59, a
  * malformed cron expression or time zone. A day SOME months lack (`on day 31`, `february 29`) is
  * legal and SKIPS those months -- Reid's ruling -- and draws an ADVISORY recommending `on last
  * day` for month-end.
  *
  * Every case asserts the whole model's error count, so a fixture that failed to parse cannot pass
  * vacuously; the controls assert the fixture itself is clean.
  */
class CalendarScheduleValidationTest extends AbstractValidatingTest {

  /** `tips`: suggestions are stripped at the accumulator unless `provideTips` is on. */
  private def messages(src: String, td: TestData, tips: Boolean = false): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default.copy(provideTips = tips)) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    withClue(clue(out)) { out.exists(_.message.startsWith("Expected one of")) mustBe false }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))
  private def errors(msgs: Seq[Message]): Seq[Message] = msgs.filter(_.kind == Messages.Error)
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** An `on every <schedule>` clause and a recurring send in `on init`, each with the schedule. */
  private def model(clauseSchedule: String, sendSchedule: String = "month on day 1"): String =
    s"""domain D is {
       |  context C is {
       |    command Tick is { n: Integer } with { briefly "t" }
       |    inlet TicksIn is command Tick with { briefly "i" }
       |    handler Boundary is {
       |      on command Tick is { do "tick" }
       |      on every $clauseSchedule is { do "fire" }
       |    } with { briefly "h" }
       |    entity Clock is {
       |      outlet Ticks is command Tick with { briefly "o" }
       |      handler Lifecycle is {
       |        on init is {
       |          send command Tick(n = 1) to outlet Clock.Ticks every $sendSchedule
       |        }
       |        on other is { error "no" }
       |      } with { briefly "h" }
       |    } with { briefly "e" }
       |    connector K is from outlet C.Clock.Ticks to inlet C.TicksIn with { briefly "k" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def mustBeClean(schedule: String, td: TestData, tips: Boolean = false): Seq[Message] =
    val msgs = messages(model(schedule, schedule), td, tips)
    withClue(clue(msgs)) { errors(msgs) mustBe empty }
    msgs

  /** The rule fires once in the `on every` clause and once in the send -- two errors in all. */
  private def mustRefuseBoth(schedule: String, rule: RuleId, td: TestData): Unit =
    val msgs = messages(model(schedule, schedule), td)
    withClue(clue(msgs)) {
      errors(msgs).size mustBe 2
      of(msgs, rule).size mustBe 2
    }

  "a well-formed schedule (controls)" should {
    "be a legal period for `on every` and `send … every`" in { (td: TestData) =>
      val schedules = Seq(
        "month on day 1 at 09:00 in \"America/Toronto\"",
        "month on last day",
        "month on the third wednesday at 10:00",
        "3 months on day 28",
        "year on march 31 at 17:00",
        "year on the first monday of september",
        "day at 23:59:59",
        "weekday at 08:30 in \"UTC\"",
        "monday, wednesday and friday at 07:00 in \"+05:30\"",
        "cron \"0 9 1 * *\" in \"Europe/London\"",
        "cron \"*/15 8-17 * * 1-5\"",
        "cron \"0 0 L * *\"",
        "cron \"30 10 * * 3#3\""
      )
      schedules.foreach { s =>
        val msgs = mustBeClean(s, td)
        withClue(s"$s\n${clue(msgs)}") {
          of(msgs, RuleId.SendEveryNotDuration) mustBe empty
          of(msgs, RuleId.EveryPeriodNotDuration) mustBe empty
          of(msgs, RuleId.ScheduleDaySkipsMonths) mustBe empty
        }
      }
    }
  }

  "a schedule that can never fire" should {
    "refuse a day outside 1..31" in { (td: TestData) =>
      mustRefuseBoth("month on day 32", RuleId.ScheduleDayOutOfRange, td)
    }
    "refuse day 0" in { (td: TestData) =>
      mustRefuseBoth("month on day 0", RuleId.ScheduleDayOutOfRange, td)
    }
    "refuse a yearly date no year has" in { (td: TestData) =>
      mustRefuseBoth("year on february 30", RuleId.ScheduleDateNeverExists, td)
    }
    "refuse april 31" in { (td: TestData) =>
      mustRefuseBoth("year on april 31", RuleId.ScheduleDateNeverExists, td)
    }
    "refuse a zero interval" in { (td: TestData) =>
      mustRefuseBoth("0 months on day 1", RuleId.ScheduleIntervalNotPositive, td)
    }
    "refuse a weekday named twice" in { (td: TestData) =>
      mustRefuseBoth("monday and monday", RuleId.ScheduleDuplicateWeekday, td)
    }
    "refuse a time of day past 23:59" in { (td: TestData) =>
      mustRefuseBoth("day at 24:30", RuleId.ScheduleTimeOutOfRange, td)
    }
  }

  "a cron expression" should {
    "refuse the wrong number of fields" in { (td: TestData) =>
      mustRefuseBoth("cron \"0 9 1 *\"", RuleId.CronMalformed, td)
    }
    "refuse a field out of range" in { (td: TestData) =>
      mustRefuseBoth("cron \"0 25 1 * *\"", RuleId.CronMalformed, td)
    }
    "refuse a field that is not cron syntax" in { (td: TestData) =>
      mustRefuseBoth("cron \"0 9 first * *\"", RuleId.CronMalformed, td)
    }
  }

  "a time zone" should {
    "refuse one that is not a zone name or offset" in { (td: TestData) =>
      mustRefuseBoth("day in \"Toronto time\"", RuleId.TimeZoneMalformed, td)
    }
    "refuse a malformed zone on a cron schedule" in { (td: TestData) =>
      mustRefuseBoth("cron \"0 9 * * *\" in \"+25:00\"", RuleId.TimeZoneMalformed, td)
    }
  }

  "a day some months lack" should {
    "skip those months, advising `on last day`, not refusing" in { (td: TestData) =>
      Seq("month on day 31", "month on day 29", "year on february 29").foreach { s =>
        val msgs = mustBeClean(s, td, tips = true)
        withClue(s"$s\n${clue(msgs)}") {
          val found = of(msgs, RuleId.ScheduleDaySkipsMonths)
          found.size mustBe 2
          found.forall(_.kind == Messages.Advisory) mustBe true
          found.head.suggestion must include("on last day")
        }
      }
    }
    "not advise day 28" in { (td: TestData) =>
      val msgs = mustBeClean("month on day 28", td)
      of(msgs, RuleId.ScheduleDaySkipsMonths) mustBe empty
    }
  }
}
