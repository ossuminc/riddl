/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** `send <msg> to <portlet> at <instant>` — a scheduled send (Reid's rulings, 2026-09-07; CM §20).
  *
  *   - `send` only: `tell`'s target may be a value, and `tell m to x at t` already parses as a lookup.
  *   - The instant must type as TimeStamp, DateTime or ZonedDateTime; a Date (no time) or a String
  *     is an Error (`stmt-send-at-not-instant`); an undeterminable type stays silent.
  *   - A past instant is delivered immediately; there is no cancellation construct — the idiom is to
  *     schedule to yourself and decide at fire time.
  *   - Deliberately UNCHANGED: A23's effect set (a scheduled send is still a transmission), the
  *     discharge rules (a `send` has not discharged `yields` since rc.19, and scheduling one does
  *     not change that), A6 reachability (the channel must exist now; delivery merely happens
  *     later), outlet ownership, portlet typing.
  */
class SendAtTest extends AbstractValidatingTest {

  private def model(clauses: String, fields: String = "", yields: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    command Book$yields is {
       |      id: String, dueAt: TimeStamp, dueDate: Date, dueDateTime: DateTime, dueZoned: ZonedDateTime(UTC),
       |      note: String$fields
       |    } with { briefly "b" }
       |    event Reminded is { id: String } with { briefly "r" }
       |    constant Opening: TimeStamp = "2026-01-01T09:00:00Z" with { briefly "k" }
       |    inlet In is command Book with { briefly "i" }
       |    outlet Out is event Reminded with { briefly "o" }
       |    handler H is {
       |$clauses
       |    } with { briefly "h" }
       |    streamlet Drain as sink is {
       |      inlet DIn is event Reminded with { briefly "i" }
       |      handler DH is { on event Reminded is { do "remind" } } with { briefly "h" }
       |    } with { briefly "d" }
       |    connector W is from outlet C.Out to inlet C.Drain.DIn with { briefly "w" }
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

  "the `at` instant" should {

    "accept a TimeStamp field of the handled message" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""      on b: command Book is { send event Reminded(id = b.id) to outlet Out at b.dueAt }"""),
        "at-timestamp-field"
      )
      errorsOf(msgs, RuleId.SendAtNotInstant) mustBe empty
      msgs.justErrors.map(_.format) mustBe empty
    }

    "accept `system.now`, a DateTime field and a ZonedDateTime field" in { (td: TestData) =>
      Seq("system.now", "b.dueDateTime", "b.dueZoned").foreach { instant =>
        val msgs = diagnostics(
          model(s"""      on b: command Book is { send event Reminded(id = b.id) to outlet Out at $instant }"""),
          s"at-ok-$instant"
        )
        withClue(instant) { errorsOf(msgs, RuleId.SendAtNotInstant) mustBe empty }
      }
    }

    "accept a TimeStamp CONSTANT" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""      on b: command Book is { send event Reminded(id = b.id) to outlet Out at Opening }"""),
        "at-constant"
      )
      errorsOf(msgs, RuleId.SendAtNotInstant) mustBe empty
    }

    "reject a Date, which has no time" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""      on b: command Book is { send event Reminded(id = b.id) to outlet Out at b.dueDate }"""),
        "at-date"
      )
      val found = errorsOf(msgs, RuleId.SendAtNotInstant)
      found must not be empty
      found.head.message must include("Date")
    }

    "reject a String" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""      on b: command Book is { send event Reminded(id = b.id) to outlet Out at b.note }"""),
        "at-string"
      )
      errorsOf(msgs, RuleId.SendAtNotInstant) must not be empty
    }

    "stay silent when the type is undeterminable (an unascribed prompt)" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """      on b: command Book is {
            |        let due = prompt("the moment the reminder is due")
            |        send event Reminded(id = b.id) to outlet Out at due
            |      }""".stripMargin
        ),
        "at-undeterminable"
      )
      errorsOf(msgs, RuleId.SendAtNotInstant) mustBe empty
    }
  }

  "a scheduled send" should {

    "leave the discharge rules alone: a send settles no `yields`, scheduled or not" in { (td: TestData) =>
      // Since rc.19 only yield/reply, error/require and forward discharge a declared response. A
      // scheduled send is still a send, so the same Error fires with and without `at` -- and a
      // clause that yields AND schedules is clean.
      val declares = " yields event Reminded"
      val scheduled = diagnostics(
        model("""      on b: command Book is { send event Reminded(id = b.id) to outlet Out at b.dueAt }""", yields = declares),
        "at-no-discharge-scheduled"
      )
      errorsOf(scheduled, RuleId.YieldUndeclared) must not be empty
      val plain = diagnostics(
        model("""      on b: command Book is { send event Reminded(id = b.id) to outlet Out }""", yields = declares),
        "at-no-discharge-plain"
      )
      errorsOf(plain, RuleId.YieldUndeclared) must not be empty
      val both = diagnostics(
        model(
          """      on b: command Book is {
            |        yield event Reminded(id = b.id)
            |        send event Reminded(id = b.id) to outlet Out at b.dueAt
            |      }""".stripMargin,
          yields = declares
        ),
        "at-yield-and-schedule"
      )
      errorsOf(both, RuleId.YieldUndeclared) mustBe empty
      errorsOf(both, RuleId.SendAtNotInstant) mustBe empty
    }

    "leave a plain send exactly as before (negative control)" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""      on b: command Book is { send event Reminded(id = b.id) to outlet Out }"""),
        "at-absent"
      )
      errorsOf(msgs, RuleId.SendAtNotInstant) mustBe empty
      msgs.justErrors.map(_.format) mustBe empty
    }

    "count the instant among the statement's values: an `initiate` hidden in it is a fold effect" in {
      (td: TestData) =>
        // `statementValues` must yield the `at` operand, or the value walks (`initiatesIn` here)
        // cannot see into it. A correlation FOLD is the scope used because it admits `send` at the
        // parser level (a function body does not), so the ONLY offender visible through the walk
        // is the `initiate` inside the instant.
        val src =
          """domain D is {
            |  context C is {
            |    event A is { id: String, at: TimeStamp } with { briefly "a" }
            |    event B is { id: String } with { briefly "b" }
            |    event Ping is { n: Integer } with { briefly "p" }
            |    command Done is { id: String } with { briefly "d" }
            |    outlet Out is event Ping with { briefly "o" }
            |    entity Worker is {
            |      handler WH is { on init is { do "start" } } with { briefly "h" }
            |    } with { briefly "w" }
            |    projector P is {
            |      record Row is { id: String } with { briefly "r" }
            |      correlation Join by id yields command Done is {
            |        handler Folds is {
            |          on event A is { send event Ping(n = 1) to outlet Out at initiate entity Worker }
            |          on event B is { set field Done.id to "b" }
            |        }
            |      } times out after "30 days" { do "give up" }
            |    } with { briefly "p" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = diagnostics(src, "at-value-walk")
        val foldEffects = errorsOf(msgs, RuleId.FoldEffect)
        foldEffects.exists(_.message.contains("'initiate'")) mustBe true
    }
  }
}
