/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** `on other` is `case _` (Reid, 2026-09-11), and an inlet nothing can dequeue is reported for
  * EVERY processor kind.
  *
  * riddl-models' task `2026-09-11-external-context-unhandled-command.md` measured two shapes at
  * zero findings and asked for a rule. Reid ruled on the first and it is NOT a defect: an inlet
  * admitting {A, B, C} with clauses for A and B and `on other { error }` RECEIVES C -- `on other`
  * fires for exactly the message types no `on <message>` clause handles, and erroring on them is
  * business logic. (An `on other` that would never fire, because every admitted type has its own
  * clause, is simply dead code.) The sender does not care how the far end handles what it sends,
  * so the task's weaker "warn on an actual send" option has no rationale either.
  *
  * The second shape IS a defect and was an oversight: a CONTEXT or PROJECTOR with an inlet and no
  * handler at all has nothing to dequeue, and `checkInletsAreReceived` skipped every handler-less
  * processor on the assumption that a "should have a handler" rule reports it -- Entity, Adaptor,
  * Repository and Streamlet have one; those two kinds do not.
  */
class OnOtherIsCaseUnderscoreTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def inletNotReceived(msgs: Messages): Seq[Message] =
    msgs.filter(_.ruleId.contains(RuleId.InletNotReceived))

  /** A context whose inlet admits three commands; `handler` is its body (or empty). */
  private def billing(handler: String): String =
    s"""domain D is {
       |  context Sender is {
       |    event Go is { id: String } with { briefly "e" }
       |    inlet In is event Go with { briefly "i" }
       |    outlet Out is command D.Billing.RecordPayment with { briefly "o" }
       |    handler SH is {
       |      on event Go is { send command D.Billing.RecordPayment(id = "x") to outlet Out }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |  } with { briefly "s" }
       |  context Billing is {
       |    command GenerateInvoice is { id: String } with { briefly "c" }
       |    command SendInvoice is { id: String } with { briefly "c" }
       |    command RecordPayment is { id: String } with { briefly "c" }
       |    type BillingCommand is one of { GenerateInvoice or SendInvoice or RecordPayment } with { briefly "t" }
       |    inlet Requests is type BillingCommand with { briefly "i" }
       |$handler
       |  } with { briefly "b" }
       |  persistent connector C is from outlet D.Sender.Out to inlet D.Billing.Requests with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "an admitted message that only `on other { error }` handles" should {

    "NOT be reported: it is received, and refusing it is business logic (ruled 2026-09-11)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          billing(
            """    handler BH is {
              |      on command GenerateInvoice is { do "x" }
              |      on command SendInvoice is { do "x" }
              |      on other is { error "unexpected" }
              |    } with { briefly "h" }""".stripMargin
          ),
          td.name
        )
        inletNotReceived(msgs) mustBe empty
        msgs.filter(_.ruleId.contains(RuleId.TellNotDeliverable)) mustBe empty
    }

    "still be reported when there is NO `on other` to receive it (negative control)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          billing(
            """    handler BH is {
              |      on command GenerateInvoice is { do "x" }
              |      on command SendInvoice is { do "x" }
              |    } with { briefly "h" }""".stripMargin
          ),
          td.name
        )
        val found = inletNotReceived(msgs)
        found.size mustBe 1
        found.head.message must include("RecordPayment")
    }
  }

  "an inlet on a processor with NO handler at all" should {

    "be reported for a CONTEXT, which has no 'should have a handler' rule of its own" in {
      (td: TestData) =>
        val found = inletNotReceived(diagnostics(billing(""), td.name))
        found.size mustBe 1
        found.head.message must include("declares no handler at all")
    }

    "be reported for a PROJECTOR, likewise" in { (td: TestData) =>
      val msgs = diagnostics(
        billing(
          """    projector Ledger is {
            |      record View is { id: String } with { briefly "v" }
            |      inlet PIn is command D.Billing.RecordPayment with { briefly "i" }
            |    } with { briefly "p" }
            |    handler BH is { on other is { do "route" } } with { briefly "h" }""".stripMargin
        ),
        td.name
      )
      inletNotReceived(msgs).filter(_.message.contains("'Ledger'")).size mustBe 1
    }

    "stay SILENT for an ENTITY, whose own 'no handlers' rule already reports it" in {
      (td: TestData) =>
        val msgs = diagnostics(
          billing(
            """    entity Account is {
              |      inlet AIn is command D.Billing.RecordPayment with { briefly "i" }
              |      record F is { id: String } with { briefly "f" }
              |      state S of record Account.F is { ??? } with { briefly "s" }
              |    } with { briefly "e" }
              |    handler BH is { on other is { do "route" } } with { briefly "h" }""".stripMargin
          ),
          td.name
        )
        inletNotReceived(msgs).filter(_.message.contains("'Account'")) mustBe empty
        msgs.filter(_.ruleId.contains(RuleId.EntityNoHandlers)) must not be empty
    }
  }
}
