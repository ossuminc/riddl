/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.Assertion
import org.scalatest.TestData

/** A saga may not `ask`, not even as a value.
  *
  * Reid's ruling, 2026-08-10: a saga must not depend on dynamic state, or the same inputs could
  * yield different transaction results at different times. That wobbliness is the thing being
  * prevented; the remedy is to acquire the value in a handler and pass it into the saga through its
  * `requires`, so the saga is closed over its inputs and compensation sees the same data the
  * forward action saw.
  *
  * The rule was already true BY ACCIDENT and unfollowably so, which is what prompted it (reported
  * from riddl-models): a saga step must contain a `tell command`, that `tell` already spends the
  * A12 budget of at most one failure point, and `Ask` counts as a failure point. So an `ask` beside
  * the mandatory `tell` was over budget, and an ask-only step failed the `tell` requirement — while
  * the over-budget message advised "split into multiple steps", a remedy that produces the OTHER
  * error. Saying the rule plainly replaces advice that could not be taken.
  */
class SagaAskProhibitedTest extends AbstractValidatingTest {

  /** `stepBody` is dropped into a saga step's do-block; everything else is held constant. */
  private def sagaModel(stepBody: String): String =
    s"""domain D is {
       |  context C is {
       |    command Pay is { amount: Integer } with { briefly "p" }
       |    command Ship is { what: String(1,20) } with { briefly "s" }
       |    query GetQuote is { id: Integer } with { briefly "q" }
       |    result Quote is { price: Integer } with { briefly "r" }
       |    entity Pricing is {
       |      handler H is { on query GetQuote { reply result Quote } } with { briefly "h" }
       |    } with { briefly "e" }
       |    entity Orders is {
       |      handler H is { on command Pay { ??? } on command Ship { ??? } } with { briefly "h" }
       |    } with { briefly "e" }
       |    saga S is {
       |      step One is {
       |        $stepBody
       |      } reverted by {
       |        tell command Ship to entity C.Orders
       |      } with { briefly "one" }
       |      step Two is {
       |        tell command Ship to entity C.Orders
       |      } reverted by {
       |        tell command Pay to entity C.Orders
       |      } with { briefly "two" }
       |    } with { briefly "saga" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def messagesOf(src: String, td: TestData)(f: Messages => Assertion): Assertion =
    parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
      f(msgs)
    }

  private def asksBanned(msgs: Messages): Boolean =
    msgs.filter(_.kind == Error).exists(_.message.contains("may not 'ask'"))

  private def failurePointCount(msgs: Messages): Boolean =
    msgs.exists(_.message.contains("potential failure points"))

  "A saga step" should {

    "reject an 'ask' beside the mandatory 'tell' (criterion 1)" in { (td: TestData) =>
      val body =
        """let quote = ask query GetQuote of entity C.Pricing
          |        tell command Pay to entity C.Orders""".stripMargin
      messagesOf(sagaModel(body), td) { msgs =>
        withClue(msgs.format) { asksBanned(msgs) mustBe true }
      }
    }

    "not advise splitting into multiple steps when the cause is an 'ask' (criterion 2)" in {
      (td: TestData) =>
        val body =
          """let quote = ask query GetQuote of entity C.Pricing
            |        tell command Pay to entity C.Orders""".stripMargin
        messagesOf(sagaModel(body), td) { msgs =>
          withClue(msgs.format) { failurePointCount(msgs) mustBe false }
        }
    }

    "reject an 'ask' in the revert block too — compensation must not read dynamic state" in {
      (td: TestData) =>
        val src = sagaModel("tell command Pay to entity C.Orders")
          .replace(
            """      } reverted by {
              |        tell command Ship to entity C.Orders
              |      } with { briefly "one" }""".stripMargin,
            """      } reverted by {
              |        let q = ask query GetQuote of entity C.Pricing
              |        tell command Ship to entity C.Orders
              |      } with { briefly "one" }""".stripMargin
          )
        messagesOf(src, td) { msgs =>
          withClue(msgs.format) { asksBanned(msgs) mustBe true }
        }
    }

    "accept a step with no 'ask' at all" in { (td: TestData) =>
      messagesOf(sagaModel("tell command Pay to entity C.Orders"), td) { msgs =>
        withClue(msgs.format) { asksBanned(msgs) mustBe false }
      }
    }

    "still count genuine multiple failure points when no 'ask' is involved (criterion 4)" in {
      (td: TestData) =>
        val body =
          """tell command Pay to entity C.Orders
            |        tell command Ship to entity C.Orders""".stripMargin
        messagesOf(sagaModel(body), td) { msgs =>
          withClue(msgs.format) { failurePointCount(msgs) mustBe true }
        }
    }
  }

  "An 'ask' outside a saga" should {
    "be unaffected (criterion 3)" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    command Go is { id: Integer } with { briefly "g" }
          |    query GetQuote is { id: Integer } with { briefly "q" }
          |    result Quote is { price: Integer } with { briefly "r" }
          |    entity Pricing is {
          |      handler H is { on query GetQuote { reply result Quote } } with { briefly "h" }
          |    } with { briefly "e" }
          |    entity Orders is {
          |      handler H is {
          |        on command Go {
          |          let quote = ask query GetQuote of entity C.Pricing
          |          do "use the quote"
          |        }
          |      } with { briefly "h" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      messagesOf(src, td) { msgs =>
        withClue(msgs.format) { asksBanned(msgs) mustBe false }
      }
    }
  }
}
