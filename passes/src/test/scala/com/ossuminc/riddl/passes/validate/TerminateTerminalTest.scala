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

/** `terminate` destroys the instance, so a statement after it in the same block is unreachable.
  *
  * Author's ruling, 2026-08-20: *"having a set state, or any statement after a terminate is
  * something riddlc should error about (because the statements must be ignored)"*. Reported by
  * riddl-models, which found it BY EYE while adding `on term` — not because anything reported it.
  *
  * **This is the asymmetry rc.19 left behind.** That release made `error` terminal and reordered
  * 268 corpus statements for exactly this reason, yet a `set state` after a `terminate` in
  * reactive-bbq's `TableOrder` survived it and every validation since, because the rule matched
  * `ErrorStatement` alone. Same statement, same position, same unreachability, one of them caught.
  *
  * The cases below pin the two halves that a smaller fix would have got wrong: that `terminate`
  * reports its OWN reason rather than inheriting `error`'s "refuses", and that `on term` needs no
  * exemption because it is a different statement list.
  */
class TerminateTerminalTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    captured
  end messagesFor

  private def terminalErrors(msgs: Messages): Messages =
    msgs.filter(_.message.contains("unreachable"))

  /** `body` goes in the `on endIt` clause; `extra` adds sibling clauses to the same handler. */
  private def model(body: String, extra: String = ""): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    type ThingId is Id(Ctx.Thing)
       |    aggregate entity Thing as void is {
       |      command EndIt is { thingId: ThingId }
       |      record ThingData is {
       |        thingId: ThingId
       |        label: String(1,20)
       |      }
       |      initial state Live of record Thing.ThingData is {
       |        initial handler H is {
       |          on endIt: command EndIt is {
       |$body
       |          }
       |$extra
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "a statement after `terminate`" should {
    "be an Error — the instance is gone, so it cannot run" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""            terminate self.id
                |            set field ThingData.label to "this runs after the entity is gone"""".stripMargin),
        td
      )
      val found = terminalErrors(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.isError mustBe true
      }
    }

    "state TERMINATE's reason, never inheriting `error`'s wording" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""            terminate self.id
                |            set field ThingData.label to "gone"""".stripMargin),
        td
      )
      val found = terminalErrors(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        val text = found.head.message
        // The whole point of not reusing `error`'s message: a `terminate` does not "refuse", and
        // `require` is not a conditional `terminate`, so neither word may appear here.
        text must include("terminate")
        text must include("destroys the instance")
        text must not include "refuses"
        found.head.suggestion must not include "require"
      }
    }
  }

  "`terminate` as the LAST statement" should {
    "draw nothing — this is what all three corpus sites look like" in { (td: TestData) =>
      val msgs = messagesFor(model("""            terminate self.id"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { terminalErrors(msgs) mustBe empty }
    }
  }

  "an `on term` clause beside the terminating one" should {
    "need no exemption — a sibling clause is its own statement list" in { (td: TestData) =>
      val msgs = messagesFor(
        model(
          """            terminate self.id""",
          """          on term is {
            |            do "release what this held"
            |          }""".stripMargin
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { terminalErrors(msgs) mustBe empty }
    }
  }

  "a `terminate` inside a `when` branch" should {
    "say nothing about statements after the `when` — the branch may not be taken" in {
      (td: TestData) =>
        val msgs = messagesFor(
          model("""            when "the order is closed" then {
                  |              terminate self.id
                  |            }
                  |            set field ThingData.label to "reached when the branch is not taken"""".stripMargin),
          td
        )
        withClue(msgs.map(_.message).mkString("\n")) { terminalErrors(msgs) mustBe empty }
    }
  }
}
