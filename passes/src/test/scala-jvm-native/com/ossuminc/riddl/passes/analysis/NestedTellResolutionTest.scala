/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc

import org.scalatest.{Assertion, TestData}

/** A `tell` nested inside `when … then … end` resolves like one written directly.
  *
  * MessageFlowPass reported "could not resolve tell target" and "could not resolve message type"
  * for eight references in riddl-models that resolve everywhere else — the model reported zero
  * errors, and fully qualifying the paths did not help. Every occurrence sat inside a conditional.
  *
  * The cause is not, as first supposed, a refMap keyed on the wrong parent. `ResolutionPass` walks
  * nested `when`/`foreach`/`match` bodies through `resolveForeachFieldRefs`, but for a nested
  * TellStatement or SendStatement it resolved ONLY a `Constructor` operand — never the processor
  * reference or the message. So no entry was ever added and MessageFlowPass's lookup correctly
  * found nothing.
  */
class NestedTellResolutionTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  context Bar is {
      |    command ReceiveDrinkOrder is { id: String }
      |    entity DrinkOrder is {
      |      handler DH is {
      |        on command D.Bar.ReceiveDrinkOrder is { ??? }
      |      }
      |    }
      |  }
      |  context FrontOfHouse is {
      |    event OrderSubmitted is { id: String }
      |    adaptor ToBar to context D.Bar is {
      |      handler AH is {
      |        on event D.FrontOfHouse.OrderSubmitted is {
      |          when "the order has drink items" then
      |            tell command D.Bar.ReceiveDrinkOrder(id = "the id") to entity D.Bar.DrinkOrder
      |          end
      |        }
      |        on other is { ??? }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "a tell nested in a conditional" should {

    "not draw a MessageFlowPass resolution warning" in { (td: TestData) =>
      parseAndValidateInput(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs: Messages.Messages) =>
          val unresolved = msgs.filter(_.message.contains("MessageFlowPass: could not resolve"))
          withClue(s"messages were:\n${msgs.format}\n") { unresolved mustBe empty }
      }
    }

    "leave the model free of errors" in { (td: TestData) =>
      parseAndValidateInput(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs: Messages.Messages) =>
          withClue(s"messages were:\n${msgs.format}\n") { msgs.justErrors mustBe empty }
      }
    }
  }
}
