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

/** `error` refuses, and a refusal ends its block — so a statement after it is unreachable.
  *
  * Reported by riddl-generator 2026-08-19: it lowers `error` to a terminal `return`, so every
  * statement a model wrote after one became unreachable Java, which does not compile. 268 sites in
  * reactive-bbq. Author's ruling, rejecting the alternative reading outright: treating the
  * following statements as "record the refusal and carry on" *"suggests 'throw out control flow,
  * it's not important!' which is ridiculous"*.
  *
  * **`require` is NOT terminal and must not be caught by this.** `require X` refuses only when X
  * fails; execution continues when it holds, so statements after it are ordinary. Only `error`
  * refuses unconditionally.
  *
  * Per statement LIST, like A23: a `when` branch is its own list, so an `error` inside one says
  * nothing about statements after the `when` itself.
  */
class ErrorTerminalTest extends AbstractValidatingTest {

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

  private def model(body: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    event Rejected is { why: String }
       |    command Browse is { who: String }
       |    entity Ent is {
       |      outlet Outs is type Ctx.Rejected
       |      handler han is {
       |        on command Ctx.Browse is {
       |$body
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "a statement after `error`" should {
    "be an Error, naming both" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""          error "not accepted in this state"
                |          send event Ctx.Rejected(why = "nope") to outlet Ctx.Ent.Outs""".stripMargin),
        td
      )
      val found = terminalErrors(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.isError mustBe true
      }
    }
  }

  "`error` as the LAST statement" should {
    "draw nothing" in { (td: TestData) =>
      val msgs = messagesFor(model("""          error "not accepted in this state""""), td)
      withClue(msgs.map(_.message).mkString("\n")) { terminalErrors(msgs) mustBe empty }
    }
  }

  "the migration the ruling enables — transmit, THEN refuse" should {
    "be legal, because A23 no longer counts a transmission as an effect" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""          send event Ctx.Rejected(why = "nope") to outlet Ctx.Ent.Outs
                |          error "not accepted in this state"""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        terminalErrors(msgs) mustBe empty
        msgs.filter(_.message.contains("must come before any effect")) mustBe empty
      }
    }
  }

  "`require` followed by statements" should {
    "draw nothing — require is conditional, not terminal" in { (td: TestData) =>
      val msgs = messagesFor(
        model("""          require "the caller is known"
                |          send event Ctx.Rejected(why = "nope") to outlet Ctx.Ent.Outs""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { terminalErrors(msgs) mustBe empty }
    }
  }

  "a state mutation before a refusal" should {
    "STILL be an Error — A23 keeps the ban on set/morph" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    command Browse is { who: String }
          |    entity Ent is {
          |      record Data is { note: String }
          |      state Main of record Ent.Data is {
          |        handler han is {
          |          on command Ctx.Browse is {
          |            set field Main.note to "changed"
          |            error "too late, already mutated"
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        msgs.filter(_.message.contains("must come before any effect")) must not be empty
      }
    }
  }
}
