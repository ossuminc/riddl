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

/** A `send`'s message must be ADMITTED by the portlet's declared type.
  *
  * Reported by riddl-generator 2026-08-19, measured: **299 of 386 remaining javac errors in
  * reactive-bbq — 77%** — across 55 message types sent to outlets that do not admit them, in a
  * model validating 100% clean. A conforming generator must preserve the outlet's declared type: it
  * is the contract the connector and every downstream consumer are built on, and riddlg lowers an
  * alternation to a sealed interface, so an outlet becomes `Emitter<BarEvent>` and a non-member
  * `send` cannot be lowered at all.
  *
  * ERROR by the author's ruling (2026-08-19): there is no reading under which this is a legitimate
  * optimization the deployment knowingly accepts — the consumer on the far end is typed by the
  * portlet's type and simply cannot receive the value.
  *
  * The check covers BOTH portlet kinds because `send` names either, and both DECLARE a type. It is
  * NOT extended to a symmetric inlet-side check on what handlers claim to receive: the author ruled
  * that delivery is matched to an on-clause and an unmatched message is a no-op, and there is no
  * `receive X from inlet Foo` to hang such a check on — that implicitness is deliberate, to keep
  * generator implementations flexible.
  */
class SendPortletTypeTest extends AbstractValidatingTest {

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

  private def admits(msgs: Messages): Messages =
    msgs.filter(_.message.contains("does not admit"))

  "an outlet typed by an alternation" should {
    "reject a message that is not one of its members" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Shop is {
          |  context Bar is {
          |    event Poured is { ident: String }
          |    event Filled is { ident: String }
          |    event Spilled is { ident: String }
          |    type BarEvent is one of { Bar.Poured or Bar.Filled }
          |    processor Src as source is {
          |      outlet Events is type Bar.BarEvent
          |      handler h is {
          |        on other is { send event Bar.Spilled(ident = "x") to outlet Events }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        admits(msgs) must not be empty
        admits(msgs).head.isError mustBe true
      }
    }

    "accept a member" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Shop is {
          |  context Bar is {
          |    event Poured is { ident: String }
          |    event Filled is { ident: String }
          |    type BarEvent is one of { Bar.Poured or Bar.Filled }
          |    processor Src as source is {
          |      outlet Events is type Bar.BarEvent
          |      handler h is {
          |        on other is { send event Bar.Poured(ident = "x") to outlet Events }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { admits(msgs) mustBe empty }
    }

    "accept a member reached THROUGH AN ALIAS" in { (td: TestData) =>
      // reactive-bbq's house style: `type SupplierEvent is SupplierSystem.ShipmentDelivered`.
      // Without alias resolution this check would fire on the corpus's most common shape.
      val msgs = messagesFor(
        """domain Shop is {
          |  context Bar is {
          |    event Poured is { ident: String }
          |    type PouredAlias is Bar.Poured
          |    type BarEvent is one of { Bar.PouredAlias or Bar.Poured }
          |    processor Src as source is {
          |      outlet Events is type Bar.BarEvent
          |      handler h is {
          |        on other is { send event Bar.Poured(ident = "x") to outlet Events }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { admits(msgs) mustBe empty }
    }
  }

  "an outlet typed by a single message type" should {
    "reject a different message" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Shop is {
          |  context Bar is {
          |    event Poured is { ident: String }
          |    event Spilled is { ident: String }
          |    processor Src as source is {
          |      outlet Events is type Bar.Poured
          |      handler h is {
          |        on other is { send event Bar.Spilled(ident = "x") to outlet Events }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        admits(msgs) must not be empty
        admits(msgs).head.isError mustBe true
      }
    }

    "accept the same message reached through an alias on the PORTLET side" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Shop is {
          |  context Bar is {
          |    event Poured is { ident: String }
          |    type PouredAlias is Bar.Poured
          |    processor Src as source is {
          |      outlet Events is type Bar.PouredAlias
          |      handler h is {
          |        on other is { send event Bar.Poured(ident = "x") to outlet Events }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { admits(msgs) mustBe empty }
    }
  }

  "an INLET target" should {
    "be checked too — `send` names either kind, and both declare a type" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Shop is {
          |  context Bar is {
          |    event Poured is { ident: String }
          |    event Spilled is { ident: String }
          |    processor Snk as sink is {
          |      inlet Ins is type Bar.Poured
          |      handler k is { on event Bar.Poured is { do "consume" } }
          |    }
          |    processor Src as source is {
          |      outlet Outs is type Bar.Spilled
          |      handler h is {
          |        on other is { send event Bar.Spilled(ident = "y") to inlet Bar.Snk.Ins }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) {
        admits(msgs) must not be empty
        admits(msgs).head.isError mustBe true
      }
    }
  }
}
