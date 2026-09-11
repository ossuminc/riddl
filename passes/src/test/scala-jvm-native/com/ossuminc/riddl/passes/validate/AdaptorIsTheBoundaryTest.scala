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

/** A103: the adaptor IS the boundary for the pair it names (Reid, 2026-09-05/06; CM §§7.2, 7.7,
  * 8.1).
  *
  * An Adaptor declared in context A `to context B` or `from context B` is boundary surface of A for
  * that ordered pair and direction, so a cross-context connector between A and B may terminate on
  * one of its DECLARED portlets (AR1). A `tell ... to context X` from inside an adaptor is validated
  * against X's DECLARED portlets and nothing is synthesised (AR5). The ADAMANT half
  * (`AdaptorIsExclusiveTest`) then made the adaptor exclusive (AR2, AR6, AR8).
  *
  * **What A103 ALSO said and no longer does ([1.25], Reid 2026-09-10/11):** it IMPLIED an adaptor's
  * two ports from its direction (AR3, "always a flow") and let a connector endpoint name the adaptor
  * itself as its implied port (AR4). Both are abolished: nothing is implied for any processor, a
  * port the handlers need and the definition lacks is INCOMPLETE (`ProcessorPortsIncompleteTest`),
  * and an endpoint naming an adaptor is `ref-wrong-kind` again (`PortAbstentionTest`). The AR4 and
  * AR3 groups below record the revised behaviour on the same fixtures.
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

  /** An OUTBOUND adaptor with DECLARED ports, telling the far context (probe A's shape, with the
    * ports A103 used to imply written out).
    */
  private val outboundAdaptor: String =
    """    adaptor ToFul to context Shop.Ful is {
      |      inlet In is command Ship with { briefly "i" }
      |      outlet Out is command Shop.Ful.Receive with { briefly "o" }
      |      handler H is {
      |        on ship: command Ship is { tell command Shop.Ful.Receive(sku = ship.sku) to context Shop.Ful }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "a" }""".stripMargin

  private def validate(source: String, origin: String): (Root, Messages) =
    var captured: (Root, Messages) = (Root.empty, Messages.empty)
    // Missing warnings are asserted below, and `pc.options` is global state other suites mutate.
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (root, _, messages) =>
        captured = (root, messages)
        succeed
      }
    }
    captured

  private def diagnostics(source: String, origin: String): Messages = validate(source, origin)._2

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  /** The INTRA-context privilege (Reid, 2026-09-09, closing BACKLOG [3.8]).
    *
    * The open question was whether an adaptor's connectors must ALSO be context-to-context, from
    * his 2026-09-03 remark that *"adaptors need to use only context-to-context connectors"*. What
    * shipped from that was the STATEMENT rule (`adaptor-targets-context-only`); the connector half
    * was left unbuilt rather than inferred. A103 then answered the CROSS-context part of it — a
    * connector reaching from an adaptor's portlet into another context's contained entity draws
    * `stream-boundary-outlet` and `stream-boundary-inlet` — leaving only the intra-context case,
    * which Reid ruled legal:
    *
    * > inside one context, a connector may run from an adaptor's outlet to a contained entity's
    * > inlet, per the intra-context ruling. The adaptor is part of the context (its boundary) and
    * > therefore enjoys the same privilege as other processors in that context.
    *
    * **Being the boundary does not make an adaptor a stranger to its own context.** A103 makes it
    * special about what CROSSES the boundary; it changes nothing about wiring inside one.
    *
    * Pinned here because nothing pinned it. `SharedAdaptorTest`'s "allow wrapper adaptations"
    * carries this exact shape but asserts only that the adaptor parses with the right id — a
    * boundary rule that started erroring on it would leave that test green.
    */
  "the intra-context privilege [3.8]" should {

    "let a connector run from an adaptor's outlet to a contained entity's inlet" in {
      (td: TestData) =>
        val src = model(
          """    entity MyEntity is {
            |      inlet commands is command Sales.Ship with { briefly "i" }
            |      handler x is {
            |        on command Sales.Ship is { do "handle" }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "e" }
            |    adaptor ToFul to context Shop.Ful is {
            |      outlet forMyEntity is command Sales.Ship with { briefly "o" }
            |      handler H is {
            |        on command Sales.Ship is { send command Sales.Ship(sku = "x") to outlet forMyEntity }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }
            |    connector only is { from outlet Sales.ToFul.forMyEntity to inlet Sales.MyEntity.commands }
            |      with { briefly "c" }""".stripMargin,
          fulHandler,
          ""
        )
        val msgs = diagnostics(src, td.name)
        errorsOf(msgs, RuleId.BoundaryOutlet) mustBe empty
        errorsOf(msgs, RuleId.BoundaryInlet) mustBe empty
        msgs.justErrors.map(_.format) mustBe empty
    }

    // The negative control, and the half A103 already enforces: the SAME shape reaching across a
    // context boundary is two Errors, one per end. Without this, deleting the boundary rule would
    // look identical to scoping it to cross-context only.
    "still REJECT the same shape when it reaches into ANOTHER context's entity" in {
      (td: TestData) =>
        val src = model(
          """    adaptor ToFul to context Shop.Ful is {
            |      outlet forMyEntity is command Sales.Ship with { briefly "o" }
            |      handler H is {
            |        on command Sales.Ship is { send command Sales.Ship(sku = "x") to outlet forMyEntity }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin,
          """    entity FarEntity is {
            |      inlet commands is command Sales.Ship with { briefly "i" }
            |      handler x is {
            |        on command Sales.Ship is { do "handle" }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "e" }
            |""".stripMargin + fulHandler,
          """  connector cross is { from outlet Sales.ToFul.forMyEntity to inlet Ful.FarEntity.commands }
            |    with { briefly "c" }""".stripMargin
        )
        val msgs = diagnostics(src, td.name)
        (errorsOf(msgs, RuleId.BoundaryOutlet) ++ errorsOf(msgs, RuleId.BoundaryInlet)) must not be empty
    }
  }

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

    "be ALLOWED to leave from an INBOUND adaptor toward the context it is FROM (the reply leg)" in {
      (td: TestData) =>
        // Until 2026-09-11 this was REJECTED: the exemption was directional, so `from context
        // Ful` could face Ful only on its inlet side. Reid's ruling (the ask task): the adaptor
        // that answers a query from Ful owns the reply's outlet toward Ful, because connectors
        // are unidirectional and the reply cannot ride the request connector. The adaptor is
        // the boundary for its PAIR, in both directions; the keyword names its translation
        // duty, not which way its wires run.
        val msgs = diagnostics(
          model(
            """    adaptor FromFul from context Shop.Ful is {
              |      outlet Out is command Shop.Ful.Receive with { briefly "o" }
              |      handler H is { on other is { do "translate" } } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin,
            "    inlet In is command Receive with { briefly \"i\" }\n" + fulHandler,
            """  connector Cross is from outlet Shop.Sales.FromFul.Out to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar1-reply-direction"
        )
        errorsOf(msgs, RuleId.BoundaryOutlet) mustBe empty
    }
  }

  "AR4 (revised): a connector endpoint naming an adaptor's DECLARED portlet" should {

    "make the one-hop model validate with no errors" in { (td: TestData) =>
      // Probe A: A6's unreachable-target Error disappears once the adaptor's own outlet is wired.
      val msgs = diagnostics(
        model(
          outboundAdaptor,
          "    inlet Incoming is command Receive with { briefly \"i\" }\n" + fulHandler,
          """  connector Cross is from outlet Shop.Sales.ToFul.Out to inlet Shop.Ful.Incoming with { briefly "c" }"""
        ),
        "ar4-declared-outlet"
      )
      msgs.justErrors.map(_.format) mustBe empty
      errorsOf(msgs, RuleId.TellTargetUnreachable) mustBe empty
    }

    "accept the declared INLET of an inbound adaptor as the `to` end" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    inlet In is command Ship with { briefly "i" }
            |    handler SalesHandler is {
            |      on command Ship is { do "ship it" }
            |      on other is { error "unexpected" }
            |    } with { briefly "h" }
            |    adaptor FromFul from context Shop.Ful is {
            |      inlet In is event Shop.Ful.Shipped with { briefly "i" }
            |      outlet ShipOut is command Ship with { briefly "o" }
            |      handler H is {
            |        on s: event Shop.Ful.Shipped is { tell command Ship(sku = s.sku) to context Shop.Sales }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }
            |    connector Inward is from outlet Shop.Sales.FromFul.ShipOut to inlet Shop.Sales.In with { briefly "c2" }""".stripMargin,
          """    event Shipped is { sku: String } with { briefly "e" }
            |    outlet Out is event Shipped with { briefly "o" }
            |    handler FulHandler is {
            |      on r: command Receive is { send event Shipped(sku = r.sku) to outlet Out }
            |    } with { briefly "h" }""".stripMargin,
          """  connector Cross is from outlet Shop.Ful.Out to inlet Shop.Sales.FromFul.In with { briefly "c" }""".stripMargin
        ),
        "ar4-declared-inlet"
      )
      msgs.justErrors.map(_.format) mustBe empty
    }

    "be ALLOWED as the `to` end of a crossing back from Ful when the adaptor is OUTBOUND (the reply)" in {
      (td: TestData) =>
        // The mirror of the reply-leg case above: an asking `to context Ful` adaptor owns the
        // inlet its answer lands on (Reid, 2026-09-11). Was a negative control for direction
        // until then. `AskReplyThroughAdaptorTest` keeps the third-context negative control.
        val msgs = diagnostics(
          model(
            outboundAdaptor,
            """    outlet Out is command Receive with { briefly "o" }
              |    handler FulHandler is { on r: command Receive is { send r to outlet Out } } with { briefly "h" }""".stripMargin,
            """  connector Back is from outlet Shop.Ful.Out to inlet Shop.Sales.ToFul.In with { briefly "c" }"""
          ),
          "ar4-inlet-reply-direction"
        )
        errorsOf(msgs, RuleId.BoundaryInlet) mustBe empty
    }

    "not report the far inlet it feeds as unconnected" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          outboundAdaptor,
          "    inlet Incoming is command Receive with { briefly \"i\" }\n" + fulHandler,
          """  connector Cross is from outlet Shop.Sales.ToFul.Out to inlet Shop.Ful.Incoming with { briefly "c" }"""
        ),
        "ar4-not-unconnected"
      )
      msgs.filter(_.message.contains("Inlet 'Incoming' is not connected")) mustBe empty
    }

    "count connectors on the adaptor's outlet against its cardinality of one" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          outboundAdaptor,
          """    inlet In1 is command Receive with { briefly "i" }
            |    inlet In2 is command Receive with { briefly "i" }
            |""".stripMargin + fulHandler,
          """  connector C1 is from outlet Shop.Sales.ToFul.Out to inlet Shop.Ful.In1 with { briefly "c" }
            |  connector C2 is from outlet Shop.Sales.ToFul.Out to inlet Shop.Ful.In2 with { briefly "c" }""".stripMargin
        ),
        "ar4-cardinality"
      )
      errorsOf(msgs, RuleId.OutletCardinality) must not be empty
    }

    "be `ref-wrong-kind` when the endpoint names the ADAPTOR itself (A103's implied port, abolished)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            outboundAdaptor,
            "    inlet Incoming is command Receive with { briefly \"i\" }\n" + fulHandler,
            """  connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.Incoming with { briefly "c" }"""
          ),
          "ar4-names-the-adaptor"
        )
        errorsOf(msgs, RuleId.WrongKind) must not be empty
    }
  }

  "AR3 (revised): an adaptor's shape" should {

    "be a flow when it declares one inlet and one outlet, like any processor" in { (td: TestData) =>
      val (root, _) = validate(
        model(
          outboundAdaptor,
          "    inlet Incoming is command Receive with { briefly \"i\" }\n" + fulHandler,
          """  connector Cross is from outlet Shop.Sales.ToFul.Out to inlet Shop.Ful.Incoming with { briefly "c" }"""
        ),
        "ar3-flow"
      )
      val adaptor = root.domains.head.contexts.head.adaptors.head
      adaptor.effectiveShape mustBe a[Flow]
    }

    "be INCOMPLETE, not a flow, when it declares no ports and handles messages" in { (td: TestData) =>
      // Under A103 this derived `flow` by implication. Now it is (0, 0) with a Missing warning
      // naming the inlet it lacks, and no rule reasons past that.
      val portless =
        """    adaptor ToFul to context Shop.Ful is {
          |      handler H is {
          |        on ship: command Ship is { do "translate" }
          |        on other is { error "unexpected" }
          |      } with { briefly "h" }
          |    } with { briefly "a" }""".stripMargin
      val (root, msgs) = validate(model(portless, fulHandler, ""), "ar3-portless")
      val adaptor = root.domains.head.contexts.head.adaptors.head
      adaptor.effectiveShape must not be a[Flow]
      msgs.filter(_.ruleId.contains(RuleId.StreamProcessorNoInlet)).count(_.message.contains("'ToFul'")) mustBe 1
      errorsOf(msgs, RuleId.AscribedShapeMismatch) mustBe empty
    }

    "ABSTAIN on `as source` with one declared outlet while the inlet is missing (the corpus's 31)" in {
      (td: TestData) =>
        // Under A103's adamant half this was an Error (the inlet was implied, so it was a flow).
        // Now the inlet is MISSING and reported as such; the ascription is judged once it exists.
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
          "ar3-as-source-abstains"
        )
        errorsOf(msgs, RuleId.AscribedShapeMismatch) mustBe empty
        msgs.filter(_.ruleId.contains(RuleId.StreamProcessorNoInlet)).count(_.message.contains("'FromFul'")) mustBe 1
    }
  }

  "AR5: a `tell ... to context X` from inside an adaptor" should {

    def salesTelling(fulPorts: String): String =
      model(
        outboundAdaptor,
        fulPorts + "\n" + fulHandler,
        """  connector Cross is from outlet Shop.Sales.ToFul.Out to inlet Shop.Ful.Incoming with { briefly "c" }"""
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
            |      inlet In is command Shop.Ful.Receive with { briefly "i" }
            |      outlet Out is command Ship with { briefly "o" }
            |      handler H is {
            |        on r: command Shop.Ful.Receive is { tell command Ship(sku = r.sku) to context Shop.Sales }
            |      } with { briefly "h" }
            |    } with { briefly "a" }
            |    connector Inward is from outlet Shop.Sales.FromFul.Out to inlet Shop.Sales.In with { briefly "c2" }""".stripMargin,
          """    streamlet Sender as flow is {
            |      inlet I is command Receive with { briefly "i" }
            |      outlet O is command Receive with { briefly "o" }
            |      handler H is {
            |        on r: command Receive is { tell command Receive(sku = r.sku) to adaptor Shop.Sales.FromFul }
            |      } with { briefly "h" }
            |    } with { briefly "s" }""".stripMargin,
          """  connector Cross is from outlet Shop.Ful.Sender.O to inlet Shop.Sales.FromFul.In with { briefly "c" }"""
        ),
        "boundary-inbound-adaptor-ok"
      )
      errorsOf(msgs, RuleId.TargetCrossesBoundary) mustBe empty
    }

    "still reject a sender in B addressing A's OUTBOUND adaptor toward B" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          outboundAdaptor,
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

    "be REJECTED once the adaptor is exclusive: the context's own outlet bypasses it (adamant)" in {
      (td: TestData) =>
        // Validated under the permissive half; under AR2 the connector from Sales' own outlet into
        // Ful bypasses the adaptor Sales declares toward Ful, and that is exactly what exclusivity
        // forbids. The adaptor's outlet -> context inlet hop is still legal; the second hop is not.
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
          "two-hop-now-rejected"
        )
        errorsOf(msgs, RuleId.ConnectorBypassesAdaptor) must not be empty
    }
  }
}
