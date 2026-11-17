/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A saga step reads the saga's own `requires` input (synapify, 2026-11-17).
  *
  * CM §9.4 calls a saga an atomic FUNCTION with `requires` and `returns` "just like a function",
  * and a function's `requires` fields are in scope in its body. A step's were not: every bare
  * `amount` in a step drew `value-ref-unresolved`, so no step could say which account or amount it
  * acts on, and the corpus fell back to `prompt(...)` for the input that started the run. The
  * resolver's value-scope lookup knew entity state, the handled message and a Function's input,
  * and had no arm for a Saga.
  */
class SagaRequiresInStepsTest extends AbstractValidatingTest {

  private def model(stepArg: String, debitAmountType: String = "Integer"): String =
    s"""domain D is {
       |  context C is {
       |    command StartTransfer is { source: String, amount: Integer }
       |    command Debit is { account: String, amount: $debitAmountType }
       |    command Credit is { account: String, amount: $debitAmountType }
       |    entity Account is { handler H is { on command Debit { ??? } on command Credit { ??? } } }
       |    saga Transfer is {
       |      requires command C.StartTransfer
       |      step Withdraw is {
       |        tell command Debit(account = source, amount = $stepArg) to entity C.Account
       |      } reverted by {
       |        tell command Credit(account = source, amount = $stepArg) to entity C.Account
       |      }
       |      step Notify is {
       |        tell command Credit(account = source, amount = $stepArg) to entity C.Account
       |      } reverted by {
       |        tell command Debit(account = source, amount = $stepArg) to entity C.Account
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def errors(src: String, td: TestData): Seq[Messages.Message] =
    Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
      case Right(result) => result.messages.justErrors
      case Left(msgs)    => msgs.justErrors

  "A saga step" should {
    "resolve the saga's `requires` fields, in the step and in its `reverted by` block" in {
      (td: TestData) =>
        val errs = errors(model("amount"), td)
        withClue(errs.map(_.format).mkString("\n")) {
          errs.filter(_.ruleId.contains(RuleId.ValueRefUnresolved)) mustBe empty
        }
    }

    "still report a name the `requires` input does not have" in { (td: TestData) =>
      val errs = errors(model("nope"), td)
      errs.count(_.ruleId.contains(RuleId.ValueRefUnresolved)) mustBe 4
      errs.find(_.ruleId.contains(RuleId.ValueRefUnresolved)).get.message must include("saga")
    }

    "type-check a `requires` field against the field it supplies" in { (td: TestData) =>
      // The field resolves to the saga's `amount: Integer`, so supplying it where a String is
      // expected is a type error -- proof the reference landed on a REAL field, not merely that
      // the unresolved-reference message went away.
      val errs = errors(model("amount", debitAmountType = "String"), td)
      withClue(errs.map(_.format).mkString("\n")) {
        errs.filter(_.ruleId.contains(RuleId.ValueRefUnresolved)) mustBe empty
        errs.exists(_.message.contains("amount")) mustBe true
      }
    }
  }
}
