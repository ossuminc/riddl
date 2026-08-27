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

/** A clause that answers should be handling a message that DECLARES what it answers with.
  *
  * Reported by riddl-generator 2026-08-19: a `reply` in a clause whose query declares no `replies`
  * was silent, and the same hole existed symmetrically for `yield`. A19 makes the declaration the
  * CONTRACT — a generator derives the handler's return type from it and never from the body, since
  * inferring from the body would let a body silently redefine the interface. With no declaration
  * the method is `void` and the reply becomes `return x;` inside it, which does not compile.
  *
  * **StyleWarning, by the author's ruling (2026-08-19):** *"a reply should be symmetric with the
  * replies clause, but not having that symmetry doesn't rise to the level of an error"*. The model
  * is untidy rather than self-contradictory — it answers, it just never said so.
  *
  * The asymmetry that made this findable: `forward` ALREADY requires the declaration and Errors
  * without it, so the strictest of the three response statements checked what the two ordinary
  * ones did not.
  *
  * The CONVERSE — declaring `yields`/`replies` and then not producing it, or producing the wrong
  * type — was verified to be an Error already in all four combinations, so nothing was added for
  * it.
  */
class UndeclaredResponseTest extends AbstractValidatingTest {

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

  // Filter on the KEYWORD, not on "declares no" -- the domain-level "declares no 'error-sink'
  // inlet" message matches that loosely and made both negative cases fail against a check that did
  // not yet exist, which is a false red that would have looked like a real one.
  private def undeclared(msgs: Messages): Messages =
    msgs.filter(m =>
      m.message.contains("declares no 'replies'") || m.message.contains("declares no 'yields'")
    )

  "a clause that replies to a query declaring no `replies`" should {
    "draw a style warning" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    result Ans is { note: String }
          |    query Ask is { key: String }
          |    entity Ent is {
          |      handler han is {
          |        on query Ctx.Ask is { reply result Ctx.Ans(note = "x") }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      val found = undeclared(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.isStyle mustBe true
        found.head.message must include("replies")
      }
    }
  }

  "a clause that yields from a command declaring no `yields`" should {
    "draw a style warning too — the halves are symmetric" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    event Done is { note: String }
          |    command Do is { note: String }
          |    entity Ent is {
          |      handler han is {
          |        on command Ctx.Do is { yield event Ctx.Done(note = "x") }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      val found = undeclared(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.isStyle mustBe true
        found.head.message must include("yields")
      }
    }
  }

  "a clause whose message DOES declare a response" should {
    "draw nothing — this is the ordinary, correct shape" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    result Ans is { note: String }
          |    query Ask replies result Ctx.Ans is { key: String }
          |    entity Ent is {
          |      handler han is {
          |        on query Ctx.Ask is { reply result Ctx.Ans(note = "x") }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { undeclared(msgs) mustBe empty }
    }
  }

  "a clause that answers nothing" should {
    "draw nothing — declaring no response and producing none is consistent" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    command Do is { note: String }
          |    entity Ent is {
          |      handler han is {
          |        on command Ctx.Do is { do "handle it quietly" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { undeclared(msgs) mustBe empty }
    }
  }
}
