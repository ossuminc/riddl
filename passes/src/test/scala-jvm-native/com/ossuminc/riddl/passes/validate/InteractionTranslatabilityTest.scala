/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.{Assertion, TestData}

/** A40: at validation time the validator predicts, cheaply, whether a free-text interaction step (a
  * [[com.ossuminc.riddl.language.AST.VagueInteraction]] or a
  * [[com.ossuminc.riddl.language.AST.ArbitraryInteraction]]) will be AI-translatable into a
  * generated test, and emits a CompletenessWarning when the prediction is negative. The heuristic
  * is vocabulary grounding: prose containing at least one content word drawn from the model's
  * in-scope vocabulary (definition names, term names, brief/description text) is predicted
  * translatable. Richer in-scope terminology therefore raises the prediction.
  *
  * A prediction is only made when the prose has at least two content words to predict FROM. One
  * bare word — typically all an ArbitraryInteraction's relationship is — is no evidence either way,
  * so the check declines to judge rather than emitting a certain false positive.
  */
class InteractionTranslatabilityTest extends AbstractValidatingTest {

  private val a40 = "uses no terms defined in scope"

  private def a40Messages(msgs: Messages): Messages =
    msgs.filter(m => m.kind == Messages.CompletenessWarning && m.message.contains(a40))

  private def validating(input: String, td: TestData)(check: Messages => Assertion): Assertion =
    parseAndValidateInput(RiddlParserInput(input, td), shouldFailOnErrors = false) {
      case (_, _, msgs: Messages) => check(msgs)
    }

  /** A vague step with several content words, none of them defined vocabulary. */
  private val ungroundedVagueModel: String =
    """domain Ordering is {
      |  user Shopper is "a customer"
      |  epic Checkout is {
      |    user Ordering.Shopper wants to "check out" so that "buy"
      |    case Simple is {
      |      user Ordering.Shopper wants to "check out" so that "buy"
      |      step is "an auditor" "reconciles" "the ledger"
      |    }
      |  }
      |}
      |""".stripMargin

  "A40 interaction translatability prediction" should {

    "stay silent for a vague step grounded in an in-scope definition name" in { (td: TestData) =>
      validating(
        """domain Ordering is {
          |  user Shopper is "a customer"
          |  epic Checkout is {
          |    user Ordering.Shopper wants to "check out" so that "buy"
          |    case Simple is {
          |      user Ordering.Shopper wants to "check out" so that "buy"
          |      step is "the Shopper" "completes" "an order"
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )(msgs => a40Messages(msgs) mustBe empty)
    }

    "warn exactly once for a vague step whose prose is entirely undefined vocabulary" in {
      (td: TestData) =>
        validating(ungroundedVagueModel, td) { msgs =>
          val warnings = a40Messages(msgs)
          warnings.size mustBe 1
          warnings.head.message must include("an auditor reconciles the ledger")
        }
    }

    "decline to judge prose that has no content words at all to predict from" in { (td: TestData) =>
      // Every word here is either under three characters or a stop word, so there is no evidence
      // either way and the check must stay silent rather than guess.
      validating(
        """domain Ordering is {
          |  user Shopper is "a customer"
          |  epic Checkout is {
          |    user Ordering.Shopper wants to "check out" so that "buy"
          |    case Simple is {
          |      user Ordering.Shopper wants to "check out" so that "buy"
          |      step is "someone" "does" "the thing somehow"
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )(msgs => a40Messages(msgs) mustBe empty)
    }

    "fall silent once a term the prose uses is defined (richer terminology raises the prediction)" in {
      (td: TestData) =>
        val ungrounded =
          """domain Ordering is {
            |  user Shopper is "a customer"
            |  epic Checkout is {
            |    user Ordering.Shopper wants to "check out" so that "buy"
            |    case Simple is {
            |      user Ordering.Shopper wants to "check out" so that "buy"
            |      step is "clerk" "reconciles" "ledger"
            |    }
            |  }
            |}
            |""".stripMargin
        val grounded =
          """domain Ordering is {
            |  user Shopper is "a customer"
            |  epic Checkout is {
            |    user Ordering.Shopper wants to "check out" so that "buy"
            |    case Simple is {
            |      user Ordering.Shopper wants to "check out" so that "buy"
            |      step is "clerk" "reconciles" "ledger"
            |    }
            |  } with {
            |    term Ledger is "the authoritative accounting record"
            |  }
            |}
            |""".stripMargin
        validating(ungrounded, td)(msgs => a40Messages(msgs).size mustBe 1)
        validating(grounded, td)(msgs => a40Messages(msgs) mustBe empty)
    }

    "ground a step on its own 'briefly' when that brief uses in-scope vocabulary" in {
      (td: TestData) =>
        validating(
          """domain Ordering is {
            |  user Shopper is "a customer"
            |  epic Checkout is {
            |    user Ordering.Shopper wants to "check out" so that "buy"
            |    case Simple is {
            |      user Ordering.Shopper wants to "check out" so that "buy"
            |      step is "an auditor" "reconciles" "the ledger" with {
            |        briefly "the Shopper completes Checkout"
            |      }
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )(msgs => a40Messages(msgs) mustBe empty)
    }

    "warn for an arbitrary step whose multi-word relationship text is undefined vocabulary" in {
      (td: TestData) =>
        validating(
          """domain Ordering is {
            |  user Shopper is "a customer"
            |  application context Store is {
            |    result Info is { msg: String }
            |    group main is {
            |      output greeting presents result Ordering.Store.Info
            |    }
            |  }
            |  epic Checkout is {
            |    user Ordering.Shopper wants to "check out" so that "buy"
            |    case Simple is {
            |      user Ordering.Shopper wants to "check out" so that "buy"
            |      step from user Ordering.Shopper "frobnicates the widget" to output Ordering.Store.main.greeting
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        ) { msgs =>
          val warnings = a40Messages(msgs)
          warnings.size mustBe 1
          warnings.head.message must include("frobnicates the widget")
        }
    }

    "decline to judge an arbitrary step whose relationship is a single bare verb" in {
      (td: TestData) =>
        // The overwhelmingly common shape in practice ("presses", "sends", "select"). A bare verb
        // is no evidence either way, and never appears in a noun-dominated vocabulary, so warning
        // on it would be a guaranteed false positive.
        validating(
          """domain Ordering is {
            |  user Shopper is "a customer"
            |  application context Store is {
            |    result Info is { msg: String }
            |    group main is {
            |      output greeting presents result Ordering.Store.Info
            |    }
            |  }
            |  epic Checkout is {
            |    user Ordering.Shopper wants to "check out" so that "buy"
            |    case Simple is {
            |      user Ordering.Shopper wants to "check out" so that "buy"
            |      step from user Ordering.Shopper "frobnicates" to output Ordering.Store.main.greeting
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )(msgs => a40Messages(msgs) mustBe empty)
    }

    "stay silent for an arbitrary step whose relationship text is grounded" in { (td: TestData) =>
      validating(
        """domain Ordering is {
          |  user Shopper is "a customer"
          |  application context Store is {
          |    result Info is { msg: String }
          |    group main is {
          |      output greeting presents result Ordering.Store.Info
          |    }
          |  }
          |  epic Checkout is {
          |    user Ordering.Shopper wants to "check out" so that "buy"
          |    case Simple is {
          |      user Ordering.Shopper wants to "check out" so that "buy"
          |      step from user Ordering.Shopper "reads the Store greeting" to output Ordering.Store.main.greeting
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )(msgs => a40Messages(msgs) mustBe empty)
    }

    "never apply to structurally typed interaction steps" in { (td: TestData) =>
      validating(
        """domain Ordering is {
          |  user Shopper is "a customer"
          |  application context Store is {
          |    command Order is { item: String }
          |    result Info is { msg: String }
          |    group main is {
          |      input pick acquires command Ordering.Store.Order
          |      output greeting presents result Ordering.Store.Info
          |    }
          |  }
          |  epic Checkout is {
          |    user Ordering.Shopper wants to "check out" so that "buy"
          |    case Simple is {
          |      user Ordering.Shopper wants to "check out" so that "buy"
          |      step focus user Ordering.Shopper on group Ordering.Store.main
          |      step take input Ordering.Store.main.pick from user Ordering.Shopper
          |      step show output Ordering.Store.main.greeting to user Ordering.Shopper
          |      step send command Ordering.Store.Order from user Ordering.Shopper to context Ordering.Store
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )(msgs => a40Messages(msgs) mustBe empty)
    }

    "be suppressed when completeness warnings are turned off" in { (td: TestData) =>
      pc.withOptions[Assertion](CommonOptions.default.copy(showCompletenessWarnings = false)) { _ =>
        validating(ungroundedVagueModel, td)(msgs => a40Messages(msgs) mustBe empty)
      }
    }
  }
}
