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

/** `forward` — pass the handled message on and discharge the response obligation.
  *
  * Requested by riddl-generator 2026-08-19 and designed by the author the same day. `yields` is
  * declared on the MESSAGE, so every handler of a command declaring `yields event E` owes an `E`.
  * A boundary handler that merely passes the command along has nothing to produce and had no way
  * to say so, which forced riddlg to emit a method typed to return `E` with an `AI FILL` where the
  * return belonged — 82 times in reactive-bbq.
  *
  * **Legal only where there is something to delegate**: a command declaring `yields`, or a query
  * declaring `replies`. You cannot delegate an event or a result — those record what happened,
  * they do not owe an answer.
  */
class ForwardStatementTest extends AbstractValidatingTest {

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

  private def having(msgs: Messages, text: String): Messages =
    msgs.filter(_.message.contains(text))

  /** A context with a command declaring `yields`, a query declaring `replies`, an event, and a
    * downstream outlet — with `body` spliced into the boundary entity's handler.
    */
  private def model(handled: String, body: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    event Happened is { note: String }
       |    result Answer is { note: String }
       |    command DoIt yields event Ctx.Happened is { note: String }
       |    query AskIt replies result Ctx.Answer is { note: String }
       |    processor Downstream as sink is {
       |      inlet Ins is type Ctx.DoIt
       |      handler d is { on command Ctx.DoIt is { do "handle it" } }
       |    }
       |    entity Boundary is {
       |      outlet Outs is type Ctx.DoIt
       |      handler h is {
       |$body
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "forward in a command clause declaring yields" should {
    "be accepted, and discharge the obligation" in { (td: TestData) =>
      val msgs = messagesFor(
        model("", """        on doIt: command Ctx.DoIt is {
                    |          forward doIt to outlet Ctx.Boundary.Outs
                    |        }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        having(msgs, "does not yield") mustBe empty
        having(msgs, "forward") mustBe empty
      }
    }
  }

  "forward in an EVENT clause" should {
    "be an Error — an event owes no response, so there is nothing to delegate" in {
      (td: TestData) =>
        val msgs = messagesFor(
          model("", """        on event Ctx.Happened is {
                      |          forward event Ctx.Happened to outlet Ctx.Boundary.Outs
                      |        }""".stripMargin),
          td
        )
        val found = having(msgs, "'forward' is only allowed")
        withClue(msgs.map(_.message).mkString("\n")) {
          found must not be empty
          found.head.isError mustBe true
        }
    }
  }

  "forward in a command clause with NO yields declaration" should {
    "be an Error — there is no obligation to discharge" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    command Plain is { note: String }
          |    entity Boundary is {
          |      outlet Outs is type Ctx.Plain
          |      handler h is {
          |        on plain: command Ctx.Plain is {
          |          forward plain to outlet Ctx.Boundary.Outs
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      val found = having(msgs, "'forward' is only allowed")
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.isError mustBe true
      }
    }
  }

  "a yield after a forward" should {
    "be an Error — the response was delegated, so this clause cannot also produce it" in {
      (td: TestData) =>
        val msgs = messagesFor(
          model("", """        on doIt: command Ctx.DoIt is {
                      |          forward doIt to outlet Ctx.Boundary.Outs
                      |          yield event Ctx.Happened
                      |        }""".stripMargin),
          td
        )
        val found = having(msgs, "after a 'forward'")
        withClue(msgs.map(_.message).mkString("\n")) {
          found must not be empty
          found.head.isError mustBe true
        }
    }
  }

  "a send after a forward" should {
    "be legal but draw a style warning — forward generally goes last" in { (td: TestData) =>
      val msgs = messagesFor(
        model("", """        on doIt: command Ctx.DoIt is {
                    |          forward doIt to outlet Ctx.Boundary.Outs
                    |          send doIt to outlet Ctx.Boundary.Outs
                    |        }""".stripMargin),
        td
      )
      val found = having(msgs, "generally be the last")
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.isStyle mustBe true
        having(msgs, "after a 'forward'").filter(_.isError) mustBe empty
      }
    }
  }

  "a handler that only SENDS the handled message" should {
    "no longer discharge the obligation — that is delegation and must say forward" in {
      (td: TestData) =>
        val msgs = messagesFor(
          model("", """        on doIt: command Ctx.DoIt is {
                      |          send doIt to outlet Ctx.Boundary.Outs
                      |        }""".stripMargin),
          td
        )
        val found = having(msgs, "does not yield")
        withClue(msgs.map(_.message).mkString("\n")) {
          found must not be empty
          found.head.isError mustBe true
        }
    }
  }
}
