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
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A103, the PERMISSIVE half: the adaptor IS the boundary for the pair it names (Reid, 2026-09-05/06;
  * CM §§7.2, 7.7, 8.1).
  *
  * An Adaptor declared in context A `to context B` or `from context B` is boundary surface of A for
  * that ordered pair and direction, so a cross-context connector between A and B may terminate on it
  * (AR1). Its two ports are IMPLIED by its direction, so it is always a flow and need write neither
  * (AR3); a connector endpoint path that resolves to an Adaptor names its implied port (AR4, no
  * grammar change -- Reid's choice). A `tell ... to context X` from inside an adaptor is validated
  * against X's DECLARED portlets and nothing is synthesised (AR5). And A6 accepts the implied outlet
  * as owned, so the one-hop shape needs no context-level plumbing.
  *
  * Permissive means both the old two-hop shape and the new one-hop shape validate. The Errors that
  * make the adaptor EXCLUSIVE (AR2, AR6, AR8) and that reject a mismatched shape ascription belong to
  * the adamant half and are not asserted here.
  *
  * Every positive case has a negative control in the same family, so a rule cannot pass by firing on
  * nothing.
  */
class AdaptorIsTheBoundaryTest extends AbstractValidatingTest {

  /** Sales (near) and Ful (far). `sales` and `ful` are the bodies; `wiring` is domain-scope. */
  private def model(sales: String, ful: String, wiring: String): String =
    s"""domain Shop is {
       |  context Sales is {
       |    command Ship is { sku: String } with { briefly "s" }
       |$sales
       |  } with { briefly "sales" }
       |  context Ful is {
       |    command Receive is { sku: String } with { briefly "r" }
       |    command Other is { n: Integer } with { briefly "o" }
       |$ful
       |  } with { briefly "ful" }
       |$wiring
       |} with { briefly "shop" }
       |""".stripMargin

  private val fulHandler: String =
    """    handler FulHandler is {
      |      on command Receive is { do "record the receipt" }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }""".stripMargin

  /** An OUTBOUND adaptor with no ports, telling the far context (probe A's shape). */
  private val impliedOutbound: String =
    """    adaptor ToFul to context Shop.Ful is {
      |      handler H is {
      |        on ship: command Ship is { tell command Shop.Ful.Receive(sku = ship.sku) to context Shop.Ful }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "a" }""".stripMargin

  private def validate(source: String, origin: String): (Root, Messages) =
    var captured: (Root, Messages) = (Root.empty, Messages.empty)
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (root, _, messages) =>
      captured = (root, messages)
      succeed
    }
    captured

  private def diagnostics(source: String, origin: String): Messages = validate(source, origin)._2

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  "AR1: a cross-context connector" should {

    "be allowed to LEAVE from a declared outlet of an OUTBOUND adaptor toward its referent" in {
      (td: TestData) =>
        // Probe D. Only `stream-boundary-outlet` objected before this change.
        val msgs = diagnostics(
          model(
            """    adaptor ToFul to context Shop.Ful is {
              |      outlet Out is command Shop.Ful.Receive with { briefly "o" }
              |      handler H is {
              |        on ship: command Ship is { tell command Shop.Ful.Receive(sku = ship.sku) to context Shop.Ful }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin,
            "    inlet In is command Receive with { briefly \"i\" }\n" + fulHandler,
            """  connector Cross is from outlet Shop.Sales.ToFul.Out to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar1-outbound-declared"
        )
        errorsOf(msgs, RuleId.BoundaryOutlet) mustBe empty
        msgs.justErrors mustBe empty
    }

    "still be REJECTED when it leaves from a NON-adaptor's outlet inside the context" in {
      (td: TestData) =>
        // Negative control: the exemption is for adaptors, not for anything with an outlet.
        val msgs = diagnostics(
          model(
            """    streamlet Shipper as source is {
              |      outlet Out is command Shop.Ful.Receive with { briefly "o" }
              |    } with { briefly "s" }""".stripMargin,
            "    inlet In is command Receive with { briefly \"i\" }\n" + fulHandler,
            """  connector Cross is from outlet Shop.Sales.Shipper.Out to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar1-non-adaptor"
        )
        errorsOf(msgs, RuleId.BoundaryOutlet) must not be empty
    }

    "still be REJECTED when it leaves from an INBOUND adaptor toward the context it is FROM" in {
      (td: TestData) =>
        // Direction must match the crossing: `from context Ful` faces Ful on its INLET side.
        val msgs = diagnostics(
          model(
            """    adaptor FromFul from context Shop.Ful is {
              |      outlet Out is command Shop.Ful.Receive with { briefly "o" }
              |      handler H is { on other is { do "translate" } } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin,
            "    inlet In is command Receive with { briefly \"i\" }\n" + fulHandler,
            """  connector Cross is from outlet Shop.Sales.FromFul.Out to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar1-wrong-direction"
        )
        errorsOf(msgs, RuleId.BoundaryOutlet) must not be empty
    }
  }

  "AR4: a connector endpoint naming an adaptor" should {

    "resolve to its implied outlet, and the one-hop model validates with no errors" in {
      (td: TestData) =>
        // Probe A: `ref-wrong-kind` and A6's unreachable-target Error both disappear.
        val msgs = diagnostics(
          model(
            impliedOutbound,
            "    inlet Incoming is command Receive with { briefly \"i\" }\n" + fulHandler,
            """  connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.Incoming with { briefly "c" }"""
          ),
          "ar4-implied-outlet"
        )
        msgs.justErrors.map(_.format) mustBe empty
        errorsOf(msgs, RuleId.TellTargetUnreachable) mustBe empty
    }

    "resolve to the implied INLET of an inbound adaptor when it is the `to` end" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """    inlet In is command Ship with { briefly "i" }
              |    handler SalesHandler is {
              |      on command Ship is { do "ship it" }
              |      on other is { error "unexpected" }
              |    } with { briefly "h" }
              |    adaptor FromFul from context Shop.Ful is {
              |      handler H is {
              |        on r: command Shop.Ful.Receive is { tell command Ship(sku = r.sku) to context Shop.Sales }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }
              |    connector Inward is from outlet Shop.Sales.FromFul to inlet Shop.Sales.In with { briefly "c2" }""".stripMargin,
            """    outlet Out is command Receive with { briefly "o" }
              |    handler FulHandler is {
              |      on r: command Receive is { send r to outlet Out }
              |    } with { briefly "h" }""".stripMargin,
            """  connector Cross is from outlet Shop.Ful.Out to inlet Shop.Sales.FromFul with { briefly "c" }""".stripMargin
          ),
          "ar4-implied-inlet"
        )
        msgs.justErrors.map(_.format) mustBe empty
    }

    "still be REJECTED as the `to` end of an inbound crossing when the adaptor is OUTBOUND" in {
      (td: TestData) =>
        // Negative control for direction on the inlet side.
        val msgs = diagnostics(
          model(
            impliedOutbound,
            """    outlet Out is command Receive with { briefly "o" }
              |    handler FulHandler is { on r: command Receive is { send r to outlet Out } } with { briefly "h" }""".stripMargin,
            """  connector Back is from outlet Shop.Ful.Out to inlet Shop.Sales.ToFul with { briefly "c" }"""
          ),
          "ar4-inlet-wrong-direction"
        )
        errorsOf(msgs, RuleId.BoundaryInlet) must not be empty
    }

    "not report the far inlet it feeds as unconnected" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          impliedOutbound,
          "    inlet Incoming is command Receive with { briefly \"i\" }\n" + fulHandler,
          """  connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.Incoming with { briefly "c" }"""
        ),
        "ar4-not-unconnected"
      )
      msgs.filter(_.message.contains("Inlet 'Incoming' is not connected")) mustBe empty
    }

    "count connectors on an implied outlet against its cardinality of one" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          impliedOutbound,
          """    inlet In1 is command Receive with { briefly "i" }
            |    inlet In2 is command Receive with { briefly "i" }
            |""".stripMargin + fulHandler,
          """  connector C1 is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In1 with { briefly "c" }
            |  connector C2 is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In2 with { briefly "c" }""".stripMargin
        ),
        "ar4-cardinality"
      )
      errorsOf(msgs, RuleId.OutletCardinality) must not be empty
    }
  }

  "AR3: an adaptor's shape" should {

    "be a flow when it declares no ports" in { (td: TestData) =>
      val (root, _) = validate(
        model(
          impliedOutbound,
          "    inlet Incoming is command Receive with { briefly \"i\" }\n" + fulHandler,
          """  connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.Incoming with { briefly "c" }"""
        ),
        "ar3-flow"
      )
      val adaptor = root.domains.head.contexts.head.adaptors.head
      adaptor.effectiveShape mustBe a[Flow]
    }

    "keep validating an adaptor with one declared outlet ascribed `as source` (permissive)" in {
      (td: TestData) =>
        // The corpus carries 31 of these. The ascription Error belongs to the adamant half.
        val msgs = diagnostics(
          model(
            """    inlet In is command Ship with { briefly "i" }
              |    handler SalesHandler is { on command Ship is { do "ship" } } with { briefly "h" }
              |    adaptor FromFul from context Shop.Ful as source is {
              |      outlet Out is command Ship with { briefly "o" }
              |      handler H is { on s: command Ship is { send s to outlet Out } } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin,
            fulHandler,
            """  connector Inward is from outlet Shop.Sales.FromFul.Out to inlet Shop.Sales.In with { briefly "c" }"""
          ),
          "ar3-as-source-permissive"
        )
        msgs.justErrors.filter(_.message.contains("is ascribed")) mustBe empty
    }
  }

  "AR5: a `tell ... to context X` from inside an adaptor" should {

    def salesTelling(fulPorts: String): String =
      model(
        impliedOutbound,
        fulPorts + "\n" + fulHandler,
        """  connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.Incoming with { briefly "c" }"""
      )

    "be accepted when X declares an inlet whose type IS the message type" in { (td: TestData) =>
      val msgs = diagnostics(
        salesTelling("    inlet Incoming is command Receive with { briefly \"i\" }"),
        "ar5-exact"
      )
      errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet) mustBe empty
    }

    "be accepted when X declares an inlet whose alternation CONTAINS the message type" in {
      (td: TestData) =>
        val msgs = diagnostics(
          salesTelling(
            """    type FulIn is one of { Shop.Ful.Receive or Shop.Ful.Other } with { briefly "t" }
              |    inlet Incoming is type FulIn with { briefly "i" }""".stripMargin
          ),
          "ar5-alternation"
        )
        errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet) mustBe empty
    }

    "be an Error naming the type and the context when no inlet of X admits the message" in {
      (td: TestData) =>
        val msgs = diagnostics(
          salesTelling("    inlet Incoming is command Other with { briefly \"i\" }"),
          "ar5-none"
        )
        val found = errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet)
        found must not be empty
        found.head.message must include("'Ful'")
        found.head.message must include("'Receive'")
        found.head.message must include("admits")
    }

    "NOT fire for the same tell from a processor that is not an adaptor" in { (td: TestData) =>
      // Negative control: the rule is about the adaptor's far end, not about tells in general.
      val msgs = diagnostics(
        model(
          """    streamlet Teller as flow is {
            |      inlet I is command Ship with { briefly "i" }
            |      outlet O is command Shop.Ful.Receive with { briefly "o" }
            |      handler H is {
            |        on ship: command Ship is { tell command Shop.Ful.Receive(sku = ship.sku) to context Shop.Ful }
            |      } with { briefly "h" }
            |    } with { briefly "t" }""".stripMargin,
          "    inlet Incoming is command Other with { briefly \"i\" }\n" + fulHandler,
          """  connector Cross is from outlet Shop.Sales.Teller.O to inlet Shop.Ful.Incoming with { briefly "c" }"""
        ),
        "ar5-negative-control"
      )
      errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet) mustBe empty
    }
  }

  "the boundary rule for transmission statements" should {

    "let a sender in B address A's INBOUND adaptor from B directly" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    inlet In is command Ship with { briefly "i" }
            |    handler SalesHandler is { on command Ship is { do "ship" } } with { briefly "h" }
            |    adaptor FromFul from context Shop.Ful is {
            |      handler H is {
            |        on r: command Shop.Ful.Receive is { tell command Ship(sku = r.sku) to context Shop.Sales }
            |      } with { briefly "h" }
            |    } with { briefly "a" }
            |    connector Inward is from outlet Shop.Sales.FromFul to inlet Shop.Sales.In with { briefly "c2" }""".stripMargin,
          """    streamlet Sender as flow is {
            |      inlet I is command Receive with { briefly "i" }
            |      outlet O is command Receive with { briefly "o" }
            |      handler H is {
            |        on r: command Receive is { tell command Receive(sku = r.sku) to adaptor Shop.Sales.FromFul }
            |      } with { briefly "h" }
            |    } with { briefly "s" }""".stripMargin,
          """  connector Cross is from outlet Shop.Ful.Sender.O to inlet Shop.Sales.FromFul with { briefly "c" }"""
        ),
        "boundary-inbound-adaptor-ok"
      )
      errorsOf(msgs, RuleId.TargetCrossesBoundary) mustBe empty
    }

    "still reject a sender in B addressing A's OUTBOUND adaptor toward B" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          impliedOutbound,
          """    streamlet Sender as source is {
            |      outlet O is command Receive with { briefly "o" }
            |      handler H is {
            |        on r: command Receive is { tell r to adaptor Shop.Sales.ToFul }
            |      } with { briefly "h" }
            |    } with { briefly "s" }""".stripMargin,
          ""
        ),
        "boundary-outbound-adaptor-rejected"
      )
      errorsOf(msgs, RuleId.TargetCrossesBoundary) must not be empty
    }
  }

  "the old two-hop shape" should {

    "still validate: adaptor outlet -> context inlet -> context outlet -> far context inlet" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """    inlet SIn is command Shop.Ful.Receive with { briefly "i" }
              |    outlet SOut is command Shop.Ful.Receive with { briefly "o" }
              |    handler SalesHandler is {
              |      on r: command Shop.Ful.Receive is { send r to outlet SOut }
              |      on other is { error "unexpected" }
              |    } with { briefly "h" }
              |    adaptor ToFul to context Shop.Ful is {
              |      outlet Out is command Shop.Ful.Receive with { briefly "o" }
              |      handler H is {
              |        on ship: command Ship is { send command Shop.Ful.Receive(sku = ship.sku) to outlet Out }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }
              |    connector Hop1 is from outlet Shop.Sales.ToFul.Out to inlet Shop.Sales.SIn with { briefly "c" }""".stripMargin,
            "    inlet In is command Receive with { briefly \"i\" }\n" + fulHandler,
            """  connector Hop2 is from outlet Shop.Sales.SOut to inlet Shop.Ful.In with { briefly "c" }""".stripMargin
          ),
          "two-hop-still-legal"
        )
        msgs.justErrors.map(_.format) mustBe empty
    }
  }
}
