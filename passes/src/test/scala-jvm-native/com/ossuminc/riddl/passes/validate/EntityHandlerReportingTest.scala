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

/** BACKLOG [2.20] and [2.21] (Reid, 2026-11-20).
  *
  * [2.20]: an entity with no handlers anywhere was reported twice, as an Error and as Completeness
  * 4f. Reid ruled the Completeness kind right; the Error is gone. The per-state rule survives only
  * where 4f cannot speak -- some states have handlers and this one does not -- as Completeness.
  *
  * [2.21]: "an FSM never transitions" carried the code `entity-fsm-single-state`, a condition it
  * never reported. It is `entity-fsm-no-transitions` now, and it reads the STATES' handlers, where
  * a transition is usually written.
  */
class EntityHandlerReportingTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    // A fixture that fails to PARSE would pass every "mustBe empty" vacuously.
    withClue(clue(out)) { out.exists(_.message.startsWith("Expected one of")) mustBe false }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  private def model(entityBody: String, meta: String = ""): String =
    s"""domain D is { context C is {
       |  command Go is { n: Integer } with { briefly "c" }
       |  record R is { n: Integer } with { briefly "r" }
       |  entity E is {
       |$entityBody
       |  } with { briefly "e" $meta }
       |} }
       |""".stripMargin

  "an entity with no handlers anywhere [2.20]" should {
    "be reported ONCE, as Completeness, never as an Error" in { (td: TestData) =>
      val msgs = messages(model("    record F is { n: Integer }\n    state S of record E.F"), td)
      withClue(clue(msgs)) {
        of(msgs, RuleId.EntityNoHandlers).map(_.kind) mustBe Seq(Messages.CompletenessWarning)
        of(msgs, RuleId.EntityNoHandlersOrStates) mustBe empty
        of(msgs, RuleId.EntityStateNoHandlers) mustBe empty
      }
    }
    "be reported once with no states at all" in { (td: TestData) =>
      val msgs = messages(model("    record F is { n: Integer }"), td)
      withClue(clue(msgs)) {
        of(msgs, RuleId.EntityNoHandlers).size mustBe 1
        of(msgs, RuleId.EntityNoHandlersOrStates) mustBe empty
      }
    }
  }

  "a state with no handlers beside one that has them [2.20]" should {
    "be reported for that state, as Completeness" in { (td: TestData) =>
      val msgs = messages(
        model(
          """    record F is { n: Integer }
            |    state A of record E.F is {
            |      handler HA is { on command Go is { morph entity E to state E.B with record E.F(n = 1) } }
            |    }
            |    state B of record E.F""".stripMargin
        ),
        td
      )
      withClue(clue(msgs)) {
        val found = of(msgs, RuleId.EntityStateNoHandlers)
        found.size mustBe 1
        found.head.kind mustBe Messages.CompletenessWarning
        found.head.message must include("State 'B'")
        of(msgs, RuleId.EntityNoHandlers) mustBe empty
      }
    }
  }

  "a finite-state-machine that never transitions [2.21]" should {
    val fsm = "option finite-state-machine"
    val twoStates =
      """    record F is { n: Integer }
        |    state A of record E.F is { handler HA is { on command Go is { BODY } } }
        |    state B of record E.F is { handler HB is { on command Go is { do "b" } } }""".stripMargin

    "report entity-fsm-no-transitions, not entity-fsm-single-state" in { (td: TestData) =>
      val msgs = messages(model(twoStates.replace("BODY", "do \"a\""), fsm), td)
      withClue(clue(msgs)) {
        of(msgs, RuleId.EntityFsmNoTransitions).size mustBe 1
        of(msgs, RuleId.FsmSingleState) mustBe empty
      }
    }
    "see a morph written inside a STATE's handler" in { (td: TestData) =>
      val msgs = messages(
        model(twoStates.replace("BODY", "morph entity E to state E.B with record E.F(n = 1)"), fsm),
        td
      )
      withClue(clue(msgs)) {
        of(msgs, RuleId.EntityFsmNoTransitions) mustBe empty
      }
    }
  }
}
