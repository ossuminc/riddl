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

/** Two things riddlc knew enough to say and did not (riddl-generator, 2026-08-25).
  *
  *   1. A `morph`/`become` with nowhere to go. Not under-specification — **wrong in every possible
  *      lowering**, so a compiler should refuse it. Silent in the worst way: the model reads as
  *      though it has a state machine and what was built is one state with a dead statement in it.
  *   2. A saga with no `timeout`, so a generator invents the bound that decides when compensation
  *      fires.
  *
  * **`morph` and `become` are checked against DIFFERENT counts, because of what each one MOVES**
  * (Reid, 2026-08-25). `morph` moves the state AND the handler with it, since a state may carry its
  * own default handler — so at one state there is neither another state to occupy nor another
  * default behaviour to adopt. `become` moves only the handler, so two handlers make it meaningful
  * however many states exist. Counting states for `become` would reject that legal model, pinned
  * below.
  */
class TransitionAndTimeoutTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    // provideTips is REQUIRED to assert on a suggestion: Messages.Accumulator.add strips it
    // otherwise, so `suggestion` reads as an empty string and an include() assertion fails
    // against output that is actually correct.
    pc.withOptions(CommonOptions(showStyleWarnings = false, showWarnings = true, provideTips = true)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) => captured = msgs; succeed
      }
    }
    captured

  private def errs(msgs: Messages, frag: String): Messages =
    msgs.filter(m => m.isError && m.message.contains(frag))

  private def entity(states: String, handlers: String, stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    command Go is { g: String(1,9) }
       |    record R is { a: String(1,9) }
       |    entity Subject is {
       |$states
       |$handlers
       |    }
       |  }
       |}
       |""".stripMargin

  private val oneState = "      state Only of record C.R is { ??? }"
  private val twoStates =
    """      state Only of record C.R is { ??? }
      |      state Other of record C.R is { ??? }""".stripMargin

  private def morphHandler(target: String) =
    s"""      handler H is {
       |        on command C.Go is { morph entity C.Subject to state Subject.$target with record C.R(a = "x") }
       |      }""".stripMargin

  "a `morph` with nowhere to go" should {
    "be an Error when the entity has ONE state" in { (td: TestData) =>
      val msgs = messagesFor(entity(oneState, morphHandler("Only"), ""), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        val hit = errs(msgs, "cannot move")
        hit must not be empty
        hit.head.message must include("Subject")
        // Criterion 2: the message must say which of the two fixes applies.
        hit.head.suggestion must (include("Declare the other states").and(include("drop the")))
      }
    }

    "draw nothing when the entity has TWO states" in { (td: TestData) =>
      val msgs = messagesFor(entity(twoStates, morphHandler("Only"), ""), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, "cannot move") mustBe empty }
    }
  }

  "a `become` with nowhere to go" should {
    "be an Error when the entity has ONE handler" in { (td: TestData) =>
      val h =
        """      handler H is {
          |        on command C.Go is { become entity C.Subject to handler Subject.H }
          |      }""".stripMargin
      val msgs = messagesFor(entity(oneState, h, ""), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, "cannot change the behaviour of") must not be empty
      }
    }

    "draw nothing for a SINGLE-STATE entity with TWO handlers" in { (td: TestData) =>
      // The case a states-based rule would wrongly reject. `become` names a HANDLER, so two
      // handlers is all it needs — the number of states is irrelevant to it.
      val h =
        """      handler H is {
          |        on command C.Go is { become entity C.Subject to handler Subject.H2 }
          |      }
          |      handler H2 is { on other is { do "x" } }""".stripMargin
      val msgs = messagesFor(entity(oneState, h, ""), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, "cannot change the behaviour of") mustBe empty
      }
    }
  }

  "a saga with no timeout" should {
    "draw a completeness warning naming the saga" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    saga Checkout is {
          |      step One is { do "first" } reverted by { do "undo first" }
          |      step Two is { do "second" } reverted by { do "undo second" }
          |    }
          |  }
          |}
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) {
        val hit = msgs.filter(_.message.contains("states no 'timeout'"))
        hit must not be empty
        hit.head.message must include("Checkout")
        // It is a WARNING, not an Error: an absent bound is the model declining to say, not the
        // model contradicting itself. `correlation` mandates its timeout because that one carries a
        // statement block which must fire; a saga's is an option with no block.
        hit.head.isError mustBe false
      }
    }

    "draw nothing when the saga states one" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    saga Checkout is {
          |      step One is { do "first" } reverted by { do "undo first" }
          |      step Two is { do "second" } reverted by { do "undo second" }
          |    } with { option timeout("PT5M") }
          |  }
          |}
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) {
        msgs.filter(_.message.contains("states no 'timeout'")) mustBe empty
      }
    }
  }
}
