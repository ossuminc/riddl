/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `system` — values supplied by the running system (Reid, 2026-08-25).
  *
  * The exact parallel of `self`: `self` is the currently executing processor instance, `system` is
  * the currently executing system. It closes a real gap — RIDDL had `TimeStamp`/`DateTime`/`Date`/
  * `Time` as types and no expression yielding the current one, so a field recording when something
  * happened could never be populated. riddl-generator measured 155 of its 1,180 AI-FILL holes on
  * reactive-bbq naming a `java.time` type: 13% of every hole was a clock read.
  *
  * Members are a CLOSED set: `now` yields `TimeStamp`, `random` yields `Real`. `random` is in by
  * Reid's ruling, accepting the nondeterminism as useful to model writers.
  */
class SystemValueTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = false, showWarnings = false)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) => captured = msgs; succeed
      }
    }
    captured

  private def errs(msgs: Messages): Messages = msgs.filter(_.isError)

  /** A field of the given type, set from the given expression. */
  private def setInto(fieldType: String, expr: String): String =
    s"""domain D is {
       |  context C is {
       |    event Ticked is { at: TimeStamp }
       |    record Reading is { f: $fieldType }
       |    entity Meter is {
       |      state Running of record C.Reading is { ??? }
       |      state Stopped of record C.Reading is { ??? }
       |      handler H is { on event C.Ticked is { set field Reading.f to $expr } }
       |    }
       |  }
       |}
       |""".stripMargin

  "system.now" should {
    "be accepted wherever a value is" in { (td: TestData) =>
      // The four positions the design names: a set, a constructor argument, a comparison operand,
      // and (via the same value parser) anywhere else a value goes.
      val src =
        """domain D is {
          |  context C is {
          |    event Ticked is { at: TimeStamp  score: Real }
          |    record Reading is { at: TimeStamp  startedAt: TimeStamp  score: Real }
          |    entity Meter is {
          |      state Running of record C.Reading is { ??? }
          |      state Stopped of record C.Reading is { ??? }
          |      handler H is {
          |        on event C.Ticked is {
          |          set field Reading.at to system.now
          |          set field Reading.score to system.random
          |          when Reading.startedAt < system.now then
          |            do "started before now"
          |          end
          |          yield event C.Ticked(at = system.now, score = system.random)
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      withClue(messagesFor(src, td).map(_.format).mkString("\n")) {
        errs(messagesFor(src, td)) mustBe empty
      }
    }

    "type as TimeStamp, and be assignable to the other date/time types" in { (td: TestData) =>
      for t <- Seq("TimeStamp", "DateTime", "Date", "Time") do
        withClue(s"$t: ${messagesFor(setInto(t, "system.now"), td).map(_.format).mkString("\n")}") {
          errs(messagesFor(setInto(t, "system.now"), td)) mustBe empty
        }
    }

    "be an Error assigned to Duration -- an instant is not an interval" in { (td: TestData) =>
      val msgs = messagesFor(setInto("Duration", "system.now"), td)
      withClue(msgs.map(_.format).mkString("\n")) {
        val hit = errs(msgs)
        hit must not be empty
        hit.head.message must (include("Duration").and(include("TimeStamp")))
      }
    }
  }

  "system.random" should {
    "type as Real" in { (td: TestData) =>
      withClue(messagesFor(setInto("Real", "system.random"), td).map(_.format).mkString("\n")) {
        errs(messagesFor(setInto("Real", "system.random"), td)) mustBe empty
      }
    }
  }

  "a bad `system` reference" should {
    /** Acceptance criterion 4: a clean error naming what `system` provides, NOT a confusing
      * path-resolution failure. Because `system` is a keyword the parser takes before `valueRef`, a
      * typo cannot fall back to being read as an ordinary path — so without this it would arrive at
      * validation as a value with no type and be reported, if at all, as something unrelated.
      */
    "name what system provides when the member is unknown" in { (td: TestData) =>
      val msgs = messagesFor(setInto("TimeStamp", "system.bogus"), td)
      withClue(msgs.map(_.format).mkString("\n")) {
        val hit = errs(msgs)
        hit must not be empty
        hit.head.message must (include("system.now").and(include("system.random")))
      }
    }

    "say `system` is not a value on its own" in { (td: TestData) =>
      val msgs = messagesFor(setInto("TimeStamp", "system"), td)
      withClue(msgs.map(_.format).mkString("\n")) {
        errs(msgs).map(_.message).mkString must include("not a value on its own")
      }
    }
  }

  "the parser" should {
    /** `system` must NOT cut, unlike `self`, and this is the case that proves why.
      *
      * `Keywords.keyword` ends in `./`. `comparison` tries `comparand ~ operator` and relies on
      * backtracking when no operator follows — so a cutting keyword in a comparand arm turned
      * `set x to system.now` into "Expected one of (!= | < | <= | == | > | >=)" at the end of the
      * statement. `self` never faced it because SelfValue is not a Comparand; SystemValue is.
      */
    "parse `system.now` as a plain value, not only as a comparison operand" in { (td: TestData) =>
      errs(messagesFor(setInto("TimeStamp", "system.now"), td)) mustBe empty
    }
  }
}
