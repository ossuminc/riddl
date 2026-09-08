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

/** `stream-graph-cycle`, re-ruled 2026-09-07 (Reid): the loop to forbid is an INFINITE MESSAGE LOOP —
  * an `on X` clause transmits X (send/tell/forward), the message travels the portlet/connector network,
  * arrives at an inlet admitting X on a processor whose `on X` clause transmits X again, and so on back
  * to where it started. Length is irrelevant, one node or many. Everything else is not a loop:
  *
  *   - a clause that is not `on X` — `on quiescence`, `on init`, `on command Book` emitting `event
  *     ReminderDue` — cannot be re-entered by the message it sends, so its self-loop is legal (the
  *     schedule-to-yourself idiom for `send … at` depends on this);
  *   - a processor with no handlers does not validate and does not pass anything through;
  *   - `on other`, `on init`, `on term` do not count as handling X.
  *
  * Type awareness: X may be a member of an alternation. Portlets are typically typed with the union
  * (`Y is one of { W or X or Z }`), so a chain of Y-typed ports carries X; an `on Y` clause handles X;
  * an `on Z` clause emitting X is not a loop of anything. `tell` and `forward` are semantically
  * identical to `send` at the model level and ride the same channel.
  */
class MessageLoopTest extends AbstractValidatingTest {

  private def model(body: String): String =
    s"""domain D is {
       |  event X is { n: Integer } with { briefly "x" }
       |  event W is { n: Integer } with { briefly "w" }
       |  event Z is { n: Integer } with { briefly "z" }
       |  command Book is { id: String, startsAt: TimeStamp } with { briefly "b" }
       |  type Y is one of { D.W or D.X or D.Z } with { briefly "y" }
       |  context C is {
       |    streamlet Src as source is {
       |      outlet o is event D.X with { briefly "o" }
       |      handler sh is { on init { send event D.X(n = 1) to outlet C.Src.o } } with { briefly "h" }
       |    } with { briefly "src" }
       |$body
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

  private def loops(msgs: Messages): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(RuleId.GraphCycle))

  // A flow that re-emits what it handles: the one shape that can loop.
  private def relay(name: String, portType: String = "event D.X", handled: String = "event D.X"): String =
    s"""    streamlet $name as flow is {
       |      inlet i is $portType with { briefly "i" }
       |      outlet o is $portType with { briefly "o" }
       |      handler h is { on $handled { send event D.X(n = 1) to outlet C.$name.o } } with { briefly "h" }
       |    } with { briefly "$name" }""".stripMargin

  "a message loop" should {

    "be an Error when an `on X` clause sends X back to its own inlet (length one)" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          relay("Loop") + "\n" +
            """    connector c1 is { from outlet C.Src.o to inlet C.Loop.i } with { briefly "c" }
              |    connector c2 is { from outlet C.Loop.o to inlet C.Loop.i } with { briefly "c" }""".stripMargin
        ),
        "loop-one"
      )
      val found = loops(msgs)
      found must not be empty
      found.head.message must include("'X'")
      found.head.message must include("'Loop'")
    }

    "be an Error across two processors that each re-emit X (length two)" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          relay("A") + "\n" + relay("B") + "\n" +
            """    connector c1 is { from outlet C.Src.o to inlet C.A.i } with { briefly "c" }
              |    connector c2 is { from outlet C.A.o to inlet C.B.i } with { briefly "c" }
              |    connector c3 is { from outlet C.B.o to inlet C.A.i } with { briefly "c" }""".stripMargin
        ),
        "loop-two"
      )
      val found = loops(msgs)
      found must not be empty
      found.head.message must include("'A'")
      found.head.message must include("'B'")
    }

    "be an Error when the ports carry the UNION Y and the clause handles X" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          relay("Loop", portType = "type D.Y") + "\n" +
            """    connector c2 is { from outlet C.Loop.o to inlet C.Loop.i } with { briefly "c" }""".stripMargin
        ),
        "loop-union-ports"
      )
      loops(msgs) must not be empty
    }

    "be an Error when the clause handles the UNION Y and re-emits X through Y-typed ports" in {
      (td: TestData) =>
        // An on-clause names a message KIND, so the union is handled as `event D.Y`; Y is an
        // alternation of events.
        val msgs = diagnostics(
          model(
            relay("Loop", portType = "type D.Y", handled = "event D.Y") + "\n" +
              """    connector c2 is { from outlet C.Loop.o to inlet C.Loop.i } with { briefly "c" }""".stripMargin
          ),
          "loop-union-clause"
        )
        loops(msgs) must not be empty
    }

    "be an Error when the message is `tell`-ed rather than sent (same channel at the model level)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """    entity E is {
              |      inlet i is event D.X with { briefly "i" }
              |      handler h is { on event D.X { tell event D.X(n = 1) to entity E } } with { briefly "h" }
              |    } with { briefly "e" }""".stripMargin
          ),
          "loop-tell"
        )
        loops(msgs) must not be empty
    }
  }

  "NOT a message loop" should {

    "an `on Z` clause emitting X around a self-loop — the message cannot re-enter that clause" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            relay("Loop", portType = "type D.Y", handled = "event D.Z") + "\n" +
              """    connector c2 is { from outlet C.Loop.o to inlet C.Loop.i } with { briefly "c" }""".stripMargin
          ),
          "not-loop-other-type"
        )
        loops(msgs) mustBe empty
    }

    "an `on quiescence` clause sending X to its own inlet" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    streamlet Idle as flow is {
            |      inlet i is event D.X with { briefly "i" }
            |      outlet o is event D.X with { briefly "o" }
            |      handler h is {
            |        on event D.X { do "consume it" }
            |        on quiescence "30 minutes" { send event D.X(n = 1) to outlet C.Idle.o }
            |      } with { briefly "h" }
            |    } with { briefly "idle" }
            |    connector c2 is { from outlet C.Idle.o to inlet C.Idle.i } with { briefly "c" }""".stripMargin
        ),
        "not-loop-quiescence"
      )
      loops(msgs) mustBe empty
    }

    "the schedule-to-yourself idiom: `on command Book` scheduling an event to its own inlet" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """    streamlet Bookings as flow is {
              |      inlet bi is command D.Book with { briefly "i" }
              |      inlet ri is event D.X with { briefly "i" }
              |      outlet o is event D.X with { briefly "o" }
              |      handler h is {
              |        on b: command D.Book { send event D.X(n = 1) to outlet C.Bookings.o at b.startsAt }
              |        on event D.X { do "decide at fire time" }
              |      } with { briefly "h" }
              |    } with { briefly "bookings" }
              |    connector c2 is { from outlet C.Bookings.o to inlet C.Bookings.ri } with { briefly "c" }""".stripMargin
          ),
          "not-loop-schedule-to-self"
        )
        loops(msgs) mustBe empty
    }

    "a ring whose second hop re-emits a DIFFERENT type" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          relay("A") + "\n" +
            """    streamlet B as flow is {
              |      inlet i is event D.X with { briefly "i" }
              |      outlet o is event D.W with { briefly "o" }
              |      handler h is { on event D.X { send event D.W(n = 1) to outlet C.B.o } } with { briefly "h" }
              |    } with { briefly "b" }
              |    streamlet Back as flow is {
              |      inlet i is event D.W with { briefly "i" }
              |      outlet o is event D.X with { briefly "o" }
              |      handler h is { on event D.W { send event D.X(n = 1) to outlet C.Back.o } } with { briefly "h" }
              |    } with { briefly "back" }
              |    connector c1 is { from outlet C.Src.o to inlet C.A.i } with { briefly "c" }
              |    connector c2 is { from outlet C.A.o to inlet C.B.i } with { briefly "c" }
              |    connector c3 is { from outlet C.B.o to inlet C.Back.i } with { briefly "c" }
              |    connector c4 is { from outlet C.Back.o to inlet C.A.i } with { briefly "c" }""".stripMargin
        ),
        "not-loop-type-changes"
      )
      loops(msgs) mustBe empty
    }

    "a ring through a HANDLER-LESS processor, which passes nothing through" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          relay("A") + "\n" +
            """    streamlet Pipe as flow is {
              |      inlet i is event D.X with { briefly "i" }
              |      outlet o is event D.X with { briefly "o" }
              |    } with { briefly "pipe" }
              |    connector c1 is { from outlet C.Src.o to inlet C.A.i } with { briefly "c" }
              |    connector c2 is { from outlet C.A.o to inlet C.Pipe.i } with { briefly "c" }
              |    connector c3 is { from outlet C.Pipe.o to inlet C.A.i } with { briefly "c" }""".stripMargin
        ),
        "not-loop-handlerless"
      )
      loops(msgs) mustBe empty
    }

    "a request/response pair — the two directions carry different types" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    streamlet A as flow is {
            |      inlet i is event D.W with { briefly "i" }
            |      outlet o is command D.Book with { briefly "o" }
            |      handler h is { on event D.W { do "note it" } } with { briefly "h" }
            |    } with { briefly "a" }
            |    streamlet B as flow is {
            |      inlet i is command D.Book with { briefly "i" }
            |      outlet o is event D.W with { briefly "o" }
            |      handler h is { on command D.Book { do "act" } } with { briefly "h" }
            |    } with { briefly "b" }
            |    connector c2 is { from outlet C.A.o to inlet C.B.i } with { briefly "c" }
            |    connector c3 is { from outlet C.B.o to inlet C.A.i } with { briefly "c" }""".stripMargin
        ),
        "not-loop-request-response"
      )
      loops(msgs) mustBe empty
    }
  }
}
