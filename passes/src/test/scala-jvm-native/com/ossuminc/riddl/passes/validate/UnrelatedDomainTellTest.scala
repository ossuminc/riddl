/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** A `tell` whose target lies in an UNRELATED domain is a modelling error, and must say so (Reid,
  * 2026-09-08).
  *
  * **This resolves a three-sided vise, not a two-sided one.** riddl-generator reported that an
  * adaptor in `Shop` telling a processor in `Corp` -- top-level siblings -- had no legal spelling
  * at all:
  *
  *   - declare the far inlet and omit the connector => `msg-tell-target-unreachable`, "add a
  *     connector";
  *   - add the connector => `stream-crosses-domains`, unrelated domains, not allowed;
  *   - drop the far inlet instead => `adaptor-target-no-admitting-inlet` (AR5).
  *
  * Root scope admits no connector, so there was no fourth placement. Worse, `stream-crosses-domains`
  * *prescribed* the shape that A6 then refused: its suggestion says "model the communication with an
  * adaptor and messaging rather than a direct stream connector".
  *
  * **The ruling is that the statement is wrong, and the DIAGNOSTIC was wrong about why.** Reid: two
  * unrelated top-level domains cannot be connected, so "likely the Shop and Corp need to share a
  * meta-domain and hence this is a modelling error" -- but the message must not say "add a
  * connector", it must say *put both domains into a common parent domain and add a connector
  * between them*. So A6 keeps erroring here and gains a distinct rule that names both domains and
  * the restructuring remedy.
  *
  * The negative controls are what make this a NARROWING rather than a deletion: a related
  * cross-domain tell still validates, and a same-domain tell with no Connector still draws the
  * original `msg-tell-target-unreachable`.
  */
class UnrelatedDomainTellTest extends AbstractValidatingTest {

  /** Two top-level domains -- no shared ancestor. The far context declares the admitting inlet AR5
    * requires, which is exactly what removed A6's `target.inlets.isEmpty` exemption and made this
    * shape unspellable.
    */
  private val unrelated: String =
    """domain Shop is {
      |  context Sales is {
      |    event OrderPlaced is { id: String } with { briefly "e" }
      |    inlet SalesIn is event OrderPlaced with { briefly "i" }
      |    handler SalesIntake is {
      |      on event OrderPlaced is { do "note it" }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }
      |    adaptor ToBilling to context Corp.Billing is {
      |      handler billing is {
      |        on placed: event OrderPlaced is {
      |          let raiseInvoice: type Corp.Billing.RaiseInvoice =
      |            prompt("the invoice to raise for this order")
      |          tell raiseInvoice to context Corp.Billing
      |        }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "a" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |domain Corp is {
      |  context Billing is {
      |    command RaiseInvoice is { id: String } with { briefly "c" }
      |    inlet InvoicesIn is command Billing.RaiseInvoice with { briefly "i" }
      |    handler BillingIntake is {
      |      on command Billing.RaiseInvoice is { do "raise it" }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  /** The SAME model with the remedy applied: both domains under a common parent, and the connector
    * the remedy tells the author to add. This is the shape the message must lead them to.
    */
  private val related: String =
    """domain Enterprise is {
      |  domain Shop is {
      |    context Sales is {
      |      event OrderPlaced is { id: String } with { briefly "e" }
      |      inlet SalesIn is event OrderPlaced with { briefly "i" }
      |      handler SalesIntake is {
      |        on event OrderPlaced is { do "note it" }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |      adaptor ToBilling to context Enterprise.Corp.Billing is {
      |        handler billing is {
      |          on placed: event OrderPlaced is {
      |            let raiseInvoice: type Enterprise.Corp.Billing.RaiseInvoice =
      |              prompt("the invoice to raise for this order")
      |            tell raiseInvoice to context Enterprise.Corp.Billing
      |          }
      |          on other is { error "unexpected" }
      |        } with { briefly "h" }
      |      } with { briefly "a" }
      |    } with { briefly "c" }
      |  } with { briefly "d" }
      |  domain Corp is {
      |    context Billing is {
      |      command RaiseInvoice is { id: String } with { briefly "c" }
      |      inlet InvoicesIn is command Billing.RaiseInvoice with { briefly "i" }
      |      handler BillingIntake is {
      |        on command Billing.RaiseInvoice is { do "raise it" }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "c" }
      |  } with { briefly "d" }
      |  connector Cross is from outlet Shop.Sales.ToBilling
      |    to inlet Corp.Billing.InvoicesIn with { briefly "x" }
      |} with { briefly "e" }
      |""".stripMargin

  /** A tell inside ONE domain with no Connector at all -- the case A6 was written for. */
  private val sameDomainNoConnector: String =
    """domain D is {
      |  context Sales is {
      |    event OrderPlaced is { id: String } with { briefly "e" }
      |    inlet SalesIn is event OrderPlaced with { briefly "i" }
      |    outlet SalesOut is command Billing.RaiseInvoice with { briefly "o" }
      |    handler SalesIntake is {
      |      on placed: event OrderPlaced is {
      |        let raiseInvoice: type D.Billing.RaiseInvoice =
      |          prompt("the invoice to raise")
      |        tell raiseInvoice to context D.Billing
      |      }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }
      |  } with { briefly "c" }
      |  context Billing is {
      |    command RaiseInvoice is { id: String } with { briefly "c" }
      |    inlet InvoicesIn is command Billing.RaiseInvoice with { briefly "i" }
      |    handler BillingIntake is {
      |      on command Billing.RaiseInvoice is { do "raise it" }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  /** `provideTips` is REQUIRED to see a suggestion at all: `Messages.Accumulator.add` is a single
    * chokepoint that STRIPS `suggestion` unless it is set, so a test asserting one against default
    * options compares against the empty string and fails for a reason unrelated to its subject.
    */
  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, provideTips = true)
    ) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  "a `tell` across unrelated domains" should {

    "be an Error naming BOTH domains" in { (td: TestData) =>
      val msgs = diagnostics(unrelated, td.name)
      val found = errorsOf(msgs, RuleId.TellCrossesUnrelatedDomains)
      found must not be empty
      val text = found.head.message
      withClue(s"message was: $text\n") {
        text must include("Shop")
        text must include("Corp")
      }
    }

    "offer the RESTRUCTURING remedy, never 'add a connector'" in { (td: TestData) =>
      // The whole defect: the author was told to add a connector, and no connector is legal.
      val found = errorsOf(diagnostics(unrelated, td.name), RuleId.TellCrossesUnrelatedDomains)
      val suggestion = found.head.suggestion
      withClue(s"suggestion was: $suggestion\n") {
        suggestion must include("common parent domain")
        suggestion must include("Connector")
      }
    }

    "REPLACE the misleading unreachable message rather than adding to it" in { (td: TestData) =>
      // Two errors for one fact, one of them unfollowable, is what was reported.
      errorsOf(diagnostics(unrelated, td.name), RuleId.TellTargetUnreachable) mustBe empty
    }
  }

  "the narrowing" should {

    // Negative control 1: the remedy the message prescribes must actually work.
    "leave a RELATED cross-domain tell alone once the remedy is applied" in { (td: TestData) =>
      val msgs = diagnostics(related, td.name)
      errorsOf(msgs, RuleId.TellCrossesUnrelatedDomains) mustBe empty
      errorsOf(msgs, RuleId.TellTargetUnreachable) mustBe empty
    }

    // Negative control 2: without this, deleting A6 outright would look identical to scoping it.
    "leave a same-domain tell with no Connector drawing the ORIGINAL message" in { (td: TestData) =>
      val msgs = diagnostics(sameDomainNoConnector, td.name)
      errorsOf(msgs, RuleId.TellCrossesUnrelatedDomains) mustBe empty
      errorsOf(msgs, RuleId.TellTargetUnreachable) must not be empty
    }
  }
}
