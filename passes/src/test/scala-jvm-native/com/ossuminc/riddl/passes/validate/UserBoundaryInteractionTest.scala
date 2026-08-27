/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.TestData

/** A39: a User (actor) may interact only at the application boundary. Only the two untyped
  * interaction steps ([[ArbitraryInteraction]], [[SendMessageInteraction]]) can pair a user with an
  * arbitrary referent, so those are the only ones policed. The five dedicated user steps hard-type
  * their non-user side to a UI element or URL and are compliant by construction.
  */
class UserBoundaryInteractionTest extends AbstractValidatingTest {

  private val a39 = "a user may interact only at the application boundary"

  private def hasA39(msgs: Messages.Messages): Boolean =
    msgs.exists(_.message.contains(a39))

  "A39 user-boundary interaction" should {

    "not flag a typed show-output step (user side is compliant by construction)" in {
      (td: TestData) =>
        val rpi = RiddlParserInput(
          """domain D is {
            |  user Shopper is "a customer"
            |  application context Store is {
            |    result Info is { msg: String }
            |    group main is {
            |      output greeting presents result D.Store.Info
            |    }
            |  }
            |  epic E is {
            |    user D.Shopper wants to "browse" so that "buy"
            |    case C is {
            |      user D.Shopper wants to "browse" so that "buy"
            |      step show output D.Store.main.greeting to user D.Shopper
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseAndValidateDomain(rpi, shouldFailOnErrors = false) {
          case (_: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
            hasA39(msgs) mustBe false
        }
    }

    "not flag an arbitrary/send step whose non-user side is on the application boundary" in {
      (td: TestData) =>
        val rpi = RiddlParserInput(
          """domain D is {
            |  user Shopper is "a customer"
            |  application context Store is {
            |    command Order is { item: String }
            |    result Info is { msg: String }
            |    group main is {
            |      input pick acquires command D.Store.Order
            |      output greeting presents result D.Store.Info
            |    }
            |  }
            |  epic E is {
            |    user D.Shopper wants to "browse" so that "buy"
            |    case C is {
            |      user D.Shopper wants to "browse" so that "buy"
            |      step from user D.Shopper "reads" to output D.Store.main.greeting
            |      step send command D.Store.Order from user D.Shopper to context D.Store
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseAndValidateDomain(rpi, shouldFailOnErrors = false) {
          case (_: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
            hasA39(msgs) mustBe false
        }
    }

    "flag an arbitrary step from a user to an entity in a plain (non-application) context" in {
      (td: TestData) =>
        val rpi = RiddlParserInput(
          """domain D is {
            |  user Shopper is "a customer"
            |  context Warehouse is {
            |    entity Stock is { ??? }
            |  }
            |  epic E is {
            |    user D.Shopper wants to "browse" so that "buy"
            |    case C is {
            |      user D.Shopper wants to "browse" so that "buy"
            |      step from user D.Shopper "inspects" to entity D.Warehouse.Stock
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseAndValidateDomain(rpi, shouldFailOnErrors = false) {
          case (_: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
            assertValidationMessage(msgs, Messages.Error, a39)
        }
    }

    "flag a send-message step from a user directly to an internal entity" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain D is {
          |  user Shopper is "a customer"
          |  context Warehouse is {
          |    command Reserve is { item: String }
          |    entity Stock is { ??? }
          |  }
          |  epic E is {
          |    user D.Shopper wants to "browse" so that "buy"
          |    case C is {
          |      user D.Shopper wants to "browse" so that "buy"
          |      step send command D.Warehouse.Reserve from user D.Shopper to entity D.Warehouse.Stock
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(rpi, shouldFailOnErrors = false) {
        case (_: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          assertValidationMessage(msgs, Messages.Error, a39)
      }
    }

    "not apply to a system-to-system step with no user on either side" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain D is {
          |  context App is { ??? }
          |  context Gateway is { ??? }
          |  command DoSomething is { ??? }
          |  user U is "an example user"
          |  epic E is {
          |    user D.U wants to "hmm" so that "haw"
          |    case C is {
          |      user D.U wants to "hmm" so that "haw"
          |      step send command D.DoSomething from context D.App to context D.Gateway
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(rpi, shouldFailOnErrors = false) {
        case (_: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          hasA39(msgs) mustBe false
      }
    }

    "compose with A41 without double-reporting when a user touches a misplaced UI element" in {
      (td: TestData) =>
        // The group lives in a non-application context, so A41 flags the group placement. The
        // referent is still an Input (a UI element), so A39 treats it as on the boundary and does
        // NOT pile on — A41 owns this issue, A39 stays silent.
        val rpi = RiddlParserInput(
          """domain D is {
            |  user Shopper is "a customer"
            |  context Plain is {
            |    command Order is { item: String }
            |    group main is {
            |      input pick acquires command D.Plain.Order
            |    }
            |  }
            |  epic E is {
            |    user D.Shopper wants to "browse" so that "buy"
            |    case C is {
            |      user D.Shopper wants to "browse" so that "buy"
            |      step from user D.Shopper "uses" to input D.Plain.main.pick
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseAndValidateDomain(rpi, shouldFailOnErrors = false) {
          case (_: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
            // A41 reports the misplaced UI group ...
            assertValidationMessage(
              msgs,
              Messages.Error,
              "Only application-intended contexts may contain UI groups"
            )
            // ... and A39 does not double-report the same issue.
            hasA39(msgs) mustBe false
        }
    }
  }
}
