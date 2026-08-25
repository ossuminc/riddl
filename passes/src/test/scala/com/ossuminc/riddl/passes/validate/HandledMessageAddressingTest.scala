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

/** A message an entity handles must be able to say WHICH instance it is for (riddl-generator, ruled
  * by Reid 2026-08-25).
  *
  * To send `M` to an instance of `E`, `M` must carry a field typed `Id(E)` — that field IS the id of
  * the entity it is sent to. A message without one is deficient by design: it is built to be told,
  * and it cannot say where.
  *
  * **Reported at the `on` clause, which is what distinguishes it from `checkTellAddressing`.** That
  * one fires AT a `tell`, taking the target from the statement — so when nothing tells `M` it has
  * nothing to attach to and stays silent, which is exactly when the deficiency most needs
  * reporting, because the sends have not been written yet.
  *
  * **`self` is not an answer.** Inside the clause `self` names the instance, but it exists only
  * BECAUSE routing already chose one; it cannot inform that choice. The information must be in the
  * message.
  */
class HandledMessageAddressingTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = false, showWarnings = true)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) => captured = msgs; succeed
      }
    }
    captured

  private def hits(msgs: Messages): Messages =
    msgs.filter(_.message.contains("but carries no field typed"))

  private def model(commands: String, clauses: String): String =
    s"""domain D is {
       |  context C is {
       |$commands
       |    type ItemId is Id(C.Item)
       |    record Data is { a: String(1,9) }
       |    entity Item is {
       |      state S of record C.Data is { ??? }
       |      state S2 of record C.Data is { ??? }
       |      handler H is {
       |$clauses
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "a message an entity handles" should {

    "draw a completeness warning when it carries no Id field" in { (td: TestData) =>
      val msgs = messagesFor(
        model(
          "    command NoId is { what: String(1,9) }",
          """        on command C.NoId is { do "x" }"""
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        val h = hits(msgs)
        h must not be empty
        h.head.message must include("NoId")
        h.head.message must include("Item")
        // A warning, not an Error: the message is under-specified, not self-contradictory.
        h.head.isError mustBe false
      }
    }

    "draw nothing when it carries an inline Id(E)" in { (td: TestData) =>
      val msgs = messagesFor(
        model(
          "    command WithId is { who: Id(C.Item)  what: String(1,9) }",
          """        on command C.WithId is { do "y" }"""
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { hits(msgs) mustBe empty }
    }

    "draw nothing when the Id comes through a named ALIAS" in { (td: TestData) =>
      // `type ItemId is Id(C.Item)` is riddl-models' documented house style. Matching `UniqueId`
      // alone would catch only the rare inline spelling and misfire on the common one — the rc.14
      // defect that cost 72 of 86 findings.
      val msgs = messagesFor(
        model(
          "    command ViaAlias is { who: C.ItemId  what: String(1,9) }",
          """        on command C.ViaAlias is { do "z" }"""
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { hits(msgs) mustBe empty }
    }

    "not fire for `on other`, which names no message" in { (td: TestData) =>
      val msgs = messagesFor(
        model(
          "    command Any is { what: String(1,9) }",
          """        on other is { do "w" }"""
        ),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { hits(msgs) mustBe empty }
    }

    "report a message ONCE even when two states handle it" in { (td: TestData) =>
      // An entity handling the same message in two states has one deficient message, not two.
      val src =
        """domain D is {
          |  context C is {
          |    command NoId is { what: String(1,9) }
          |    record Data is { a: String(1,9) }
          |    entity Item is {
          |      state S of record C.Data is {
          |        handler H1 is { on command C.NoId is { do "x" } }
          |      }
          |      state S2 of record C.Data is {
          |        handler H2 is { on command C.NoId is { do "y" } }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) { hits(msgs).size mustBe 1 }
    }
  }
}
