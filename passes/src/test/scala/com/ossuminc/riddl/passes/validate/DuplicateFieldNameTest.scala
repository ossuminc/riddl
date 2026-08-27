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

/** A field name may appear only once in one aggregate, and an argument name only once in one
  * constructor.
  *
  * Reported by riddl-examples 2026-08-18, from their rc.17 zero-messages migration. A duplicate
  * survived validation AND an idempotent prettify round trip: the whole corpus read as zero
  * messages of every kind while 13 ShopifyCart definitions carried the same name twice. They found
  * it by parsing their own output and counting names — nothing riddlc said pointed at it.
  *
  * It is an Error rather than a style warning because the aggregate's SHAPE is ambiguous: every
  * downstream consumer — a generator, a BAST reader, riddlg — has to pick one silently. That is
  * contradiction, not untidiness, which is the line this repo draws for Error.
  *
  * The rc.15 completeness rule requiring an `Id(<Entity>)` field makes the collision MORE likely,
  * since a model already naming its identifier `cartId` collides the moment anyone follows the
  * short-identifier guidance.
  */
class DuplicateFieldNameTest extends AbstractValidatingTest {

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

  private def dupes(msgs: Messages): Messages =
    msgs.filter(m => m.message.contains("is declared more than once"))

  private def dupeArgs(msgs: Messages): Messages =
    msgs.filter(m => m.message.contains("is supplied more than once"))

  "a repeated field name in a message type" should {
    "be an Error naming both declarations" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    command Cmd is { cartId is String, cartId is Integer }
          |  }
          |}
          |""".stripMargin,
        td
      )
      val found = dupes(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.isError mustBe true
        // Both locations must be named, or the author has to hunt for the other one.
        found.head.message must include("cartId")
      }
    }
  }

  "a repeated field name in a plain aggregation" should {
    "be an Error too — the same defect, a different container" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    type Rec is { alpha is String, alpha is Integer }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        dupes(msgs) must not be empty
        dupes(msgs).head.isError mustBe true
      }
    }
  }

  "a repeated constructor argument" should {
    "be an Error — the call-site half of the same mistake" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    command Cmd is { alpha is String, beta is String }
          |    entity Ent is {
          |      inlet In is type Ctx.Cmd
          |      handler han is {
          |        on command Ctx.Cmd is { do "handle" }
          |        on other is {
          |          send command Ctx.Cmd(alpha = "a", alpha = "b") to inlet Ctx.Ent.In
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        dupeArgs(msgs) must not be empty
        dupeArgs(msgs).head.isError mustBe true
      }
    }
  }

  "distinct names" should {
    "draw nothing — the negative control that keeps this from firing on every model" in {
      (td: TestData) =>
        val msgs = messagesFor(
          """domain Dom is {
            |  context Ctx is {
            |    command Cmd is { cartId is String, itemId is Integer }
            |    type Rec is { alpha is String, beta is Integer }
            |  }
            |}
            |""".stripMargin,
          td
        )
        withClue(msgs.map(_.message).mkString("\n")) {
          dupes(msgs) mustBe empty
          dupeArgs(msgs) mustBe empty
        }
    }
  }
}
