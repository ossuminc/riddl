/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A70 — correlations in projectors.
  *
  * A projection frequently must join facts arriving from different entities at different times, and
  * a Projector otherwise has nowhere to hold the partial join while it waits. The semantics are
  * specified in `RIDDL-Computational-Model.md` §6.2 and §6.5–§6.8, which is the authority for any
  * lowering decision; these cases pin only what riddlc must REPORT.
  *
  * The check that earns the feature is the first one below: every required non-key field of the
  * yielded record is set by some fold. It turns "this correlation can never complete" from a
  * production mystery into a compile-time fact, exactly as the event-sourcing rules did for
  * entities.
  */
class CorrelationTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsFor(src: String, origin: String): String =
    diagnostics(src, origin).justErrors.map(_.message).mkString("\n")

  /** One model, parameterised on the record's fields and the folds that fill them.
    *
    * The events carry `customerId`/`orderId` so the key-reachability rule is satisfied by default;
    * the case that tests that rule overrides the event declarations.
    */
  private def model(
    fields: String = "customerId: String, orderId: String, paidAmount: Number",
    folds: String = "on e: event PaymentTaken is { set field paidAmount to e.amount }",
    keys: String = "customerId, orderId",
    timeout: String = "30 days",
    eventFields: String = "customerId: String, orderId: String, amount: Number, confirmed: Boolean"
  ): String =
    s"""domain D is {
       |  context C is {
       |    record Fulfillment is { $fields } with { briefly "the joined record" }
       |    event PaymentTaken is { $eventFields } with { briefly "payment" }
       |    command ReportStalled is { why: String } with { briefly "alert" }
       |    entity Monitor is {
       |      handler M is { on command ReportStalled is { do "record it" } }
       |    } with { briefly "monitor" }
       |    repository Store is { ??? } with { briefly "store" }
       |    projector FulfillmentView is {
       |      updates repository Store
       |      correlation FulfillmentJoin by $keys yields record Fulfillment is {
       |        handler Collect is {
       |          $folds
       |        } with { briefly "folds" }
       |      } times out after "$timeout" {
       |        do "escalate to operations"
       |      } with { briefly "the correlation" }
       |    } with { briefly "the projector" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "Correlation completeness" should {

    "accept a correlation whose folds set every required non-key field" in { (td: TestData) =>
      errorsFor(model(), "correlation-complete") must be("")
    }

    "reject a correlation that can never complete" in { (td: TestData) =>
      // `shippedAt` is required, is not a key, and no fold sets it -- so no arrival of events
      // could ever populate the record. That is the whole point of the check.
      val errors = errorsFor(
        model(fields = "customerId: String, orderId: String, paidAmount: Number, shippedAt: String"),
        "correlation-incomplete"
      )
      errors must include("can never complete")
      errors must include("shippedAt")
    }

    "exempt key fields from the must-be-set rule" in { (td: TestData) =>
      // §6.5 populates key fields implicitly from the correlation key. Demanding a fold set them
      // would reject every correct correlation, so this case would fail if the exemption were lost.
      errorsFor(model(), "correlation-keys-exempt") must not(include("customerId"))
    }

    "not require an optional field to be set by a fold" in { (td: TestData) =>
      // `?` and `*` both admit "nothing there", so neither blocks completion.
      errorsFor(
        model(fields =
          "customerId: String, orderId: String, paidAmount: Number, note: String?, tags: String*"
        ),
        "correlation-optional"
      ) must be("")
    }

    "resolve a bare `set field` against the correlation's yielded record" in { (td: TestData) =>
      // A70 chose the bare form deliberately: the enclosing correlation says which record the name
      // belongs to. If that scoping were missing this would fail to resolve rather than validate.
      errorsFor(
        model(folds = "on e: event PaymentTaken is { set field paidAmount to e.amount }"),
        "correlation-bare-set"
      ) must be("")
    }

    "count a `set` nested inside a when/then block" in { (td: TestData) =>
      // Reachability is all the check asks; it does not try to prove the branch is taken.
      errorsFor(
        model(folds = """on e: event PaymentTaken is {
                        |            when e.confirmed then
                        |              set field paidAmount to e.amount
                        |            end
                        |          }""".stripMargin),
        "correlation-nested-set"
      ) must not(include("can never complete"))
    }
  }

  /** The rules that make a correlation's RESULT well-defined, rather than its completion possible. */
  "Correlation soundness" should {

    "reject a vague timeout duration" in { (td: TestData) =>
      // The bound left metadata for the grammar (A70); the duration check must not have been left
      // behind with it, or `times out after "banana"` would compile.
      errorsFor(model(timeout = "banana"), "correlation-vague-timeout") must include(
        "vague duration"
      )
    }

    "reject a non-positive timeout" in { (td: TestData) =>
      errorsFor(model(timeout = "0s"), "correlation-zero-timeout") must include(
        "non-positive duration"
      )
    }

    "reject two clauses writing the same field" in {  (td: TestData) =>
      // A race: arrival order across sources is not guaranteed, so the completed record would
      // differ between runs over identical events. §6.6 rejects it rather than describing it.
      val errors = errorsFor(
        model(folds = """on e: event PaymentTaken is { set field paidAmount to e.amount }
                        |          on f: event PaymentTaken is { set field paidAmount to f.amount }
                        |""".stripMargin),
        "correlation-race"
      )
      errors must include("more than one clause")
      errors must include("arrival order")
    }

    "reject a fold that sets nothing" in { (td: TestData) =>
      errorsFor(
        model(folds = """on e: event PaymentTaken is { set field paidAmount to e.amount }
                        |          on f: event PaymentTaken is { do "look busy" }
                        |""".stripMargin),
        "correlation-no-set"
      ) must include("must terminate in a 'set'")
    }

    "reject an effect inside a fold" in { (td: TestData) =>
      // Purity is what makes re-running a fold safe (§6.5), so this is an Error and not a style
      // warning. The same statement is legal in the timeout block, which the next case pins.
      errorsFor(
        model(folds = """on e: event PaymentTaken is {
                        |            set field paidAmount to e.amount
                        |            tell command ReportStalled to entity Monitor
                        |          }""".stripMargin),
        "correlation-effect-in-fold"
      ) must include("may not 'tell'")
    }

    "allow the same effect in the timeout block" in { (td: TestData) =>
      // §6.7: the timeout block EXISTS to have an effect. Banning effects there would leave it
      // unable to do anything, so the ban must bind folds only -- this is the case that proves the
      // previous one is not simply banning `tell` everywhere in a projector.
      val src = model().replace(
        """do "escalate to operations"""",
        "tell command ReportStalled to entity Monitor"
      )
      errorsFor(src, "correlation-effect-in-timeout") must be("")
    }

    "reject a key component missing from a handled event" in { (td: TestData) =>
      // §6.6 makes the key the distribution key, so an event without it could not be routed to the
      // instance holding that tuple's partial in the first place.
      errorsFor(
        model(eventFields = "customerId: String, amount: Number, confirmed: Boolean"),
        "correlation-key-missing"
      ) must include("every key component must be present on every handled event")
    }
  }
}
