/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** AR9 (riddl-generator, 2026-09-07): a connector whose ends are IMPLIED adaptor ports is
  * type-checked like any other, and the AR5 far-end check accepts the far context's inbound
  * adaptor as the admitting port.
  *
  * What an implied port's TYPE is (riddlg's derivation, adopted here): an implied OUTLET carries
  * what its adaptor `tell`s or `forward`s to a context -- the operand's type, resolved through the
  * enclosing `let`, the constructor, or the message reference; an implied INLET accepts what its
  * adaptor HANDLES. The SOURCE decides what a connector carries; the destination's expectation is
  * not evidence about what arrives. An adaptor telling several distinct types is an ambiguity
  * reported by name, never resolved by taking the first.
  *
  * The AR5 repair found while triaging this task: `checkAdaptorTargetAdmitsMessage` looked only
  * at the far context's OWN inlets, but AR6 requires the crossing to land on the far context's
  * inbound adaptor instead -- so the two rules contradicted each other on the exact shape
  * exclusivity demands. The corpus escaped only because its adaptor tells are `let`-bound, which
  * AR5 did not resolve. Both halves fixed here, with negative controls.
  */
class ImpliedPortTypeTest extends AbstractValidatingTest {

  private def model(sales: String, ful: String, wiring: String): String =
    s"""domain Shop is {
       |  context Sales is {
       |    event OrderPlaced is { sku: String } with { briefly "e" }
       |    command Ship is { sku: String } with { briefly "s" }
       |$sales
       |  } with { briefly "sales" }
       |  context Ful is {
       |    command Receive is { sku: String } with { briefly "r" }
       |    command Stock is { sku: String } with { briefly "st" }
       |$ful
       |  } with { briefly "ful" }
       |$wiring
       |} with { briefly "shop" }
       |""".stripMargin

  /** Outbound adaptor telling Receive (as a constructor) to Ful. */
  private val tellsReceive: String =
    """    adaptor ToFul to context Shop.Ful is {
      |      handler H is {
      |        on p: event OrderPlaced is { tell command Shop.Ful.Receive(sku = p.sku) to context Shop.Ful }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "a" }""".stripMargin

  /** Outbound adaptor telling a `let`-bound Ship to Ful (probe f's shape). */
  private val tellsLetShip: String =
    """    adaptor ToFul to context Shop.Ful is {
      |      handler H is {
      |        on p: event OrderPlaced is {
      |          let ship: type Sales.Ship = prompt("the shipment for this order")
      |          tell ship to context Shop.Ful
      |        }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "a" }""".stripMargin

  /** Inbound adaptor in Ful handling the given clauses; forwards inward to Ful.In as Stock. */
  private def fromSalesHandling(clauses: String): String =
    s"""    inlet In is command Stock with { briefly "i" }
       |    handler FulHandler is {
       |      on command Stock is { do "stock it" }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |    adaptor FromSales from context Shop.Sales is {
       |      handler H is {
       |$clauses
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "a" }
       |    connector Inward is from outlet Shop.Ful.FromSales to inlet Shop.Ful.In with { briefly "c" }""".stripMargin

  private val cross: String =
    """  persistent connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.FromSales with { briefly "c" }"""

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    captured

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  "a connector between two implied ports" should {

    "be an Error naming both types when the source tells one type and the destination handles another" in {
      (td: TestData) =>
        // Probe f: the wire carries Ship; FromSales handles OrderPlaced.
        val msgs = diagnostics(
          model(
            tellsLetShip,
            fromSalesHandling(
              "        on e: event Shop.Sales.OrderPlaced is { tell command Stock(sku = e.sku) to context Shop.Ful }"
            ),
            cross
          ),
          "ar9-mismatch"
        )
        val found = errorsOf(msgs, RuleId.ConnectorTypeMismatch)
        found must not be empty
        found.head.message must include("'Ship'")
        found.head.message must include("'OrderPlaced'")
        found.head.message must include("'ToFul'")
        found.head.message must include("'FromSales'")
    }

    "validate when the destination handles what the source tells (negative control)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            tellsReceive,
            fromSalesHandling(
              "        on r: command Receive is { tell command Stock(sku = r.sku) to context Shop.Ful }"
            ),
            cross
          ),
          "ar9-match"
        )
        errorsOf(msgs, RuleId.ConnectorTypeMismatch) mustBe empty
        msgs.justErrors.map(_.format) mustBe empty
    }

    "resolve a `let`-bound operand to its declared type, and accept when it matches" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            tellsLetShip,
            fromSalesHandling(
              "        on s: command Shop.Sales.Ship is { tell command Stock(sku = s.sku) to context Shop.Ful }"
            ),
            cross
          ),
          "ar9-let-match"
        )
        errorsOf(msgs, RuleId.ConnectorTypeMismatch) mustBe empty
    }

    "accept a destination that handles everything with `on other`" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          tellsReceive,
          fromSalesHandling("        on other is { do \"translate whatever arrives\" }")
            .replace("        on other is { error \"unexpected\" }\n", ""),
          cross
        ),
        "ar9-on-other"
      )
      errorsOf(msgs, RuleId.ConnectorTypeMismatch) mustBe empty
    }
  }

  "a connector with one implied end" should {

    "accept an implied outlet facing a declared inlet whose alternation CONTAINS the told type" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            tellsReceive,
            """    type FulIn is one of { Shop.Ful.Receive or Shop.Ful.Stock } with { briefly "t" }
              |    inlet In is type FulIn with { briefly "i" }
              |    handler FulHandler is {
              |      on command Receive is { do "receive" }
              |      on command Stock is { do "stock" }
              |      on other is { error "unexpected" }
              |    } with { briefly "h" }""".stripMargin,
            """  persistent connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar9-alternation-ok"
        )
        errorsOf(msgs, RuleId.ConnectorTypeMismatch) mustBe empty
    }

    "reject an implied outlet facing a declared inlet that does not admit the told type" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            tellsReceive,
            """    inlet In is command Stock with { briefly "i" }
              |    handler FulHandler is {
              |      on command Stock is { do "stock" }
              |      on other is { error "unexpected" }
              |    } with { briefly "h" }""".stripMargin,
            """  persistent connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar9-declared-inlet-mismatch"
        )
        errorsOf(msgs, RuleId.ConnectorTypeMismatch) must not be empty
    }

    "require an implied inlet to handle EVERY member of a declared alternation outlet" in {
      (td: TestData) =>
        def salesWithOutlet(clauses: String): (String, String) = (
          """    type SalesOut is one of { Shop.Sales.OrderPlaced or Shop.Sales.Ship } with { briefly "t" }
            |    outlet Out is type SalesOut with { briefly "o" }
            |    handler SalesHandler is {
            |      on p: event OrderPlaced is { send p to outlet Out }
            |    } with { briefly "h" }""".stripMargin,
          fromSalesHandling(clauses)
        )
        val wiring =
          """  persistent connector Cross is from outlet Shop.Sales.Out to inlet Shop.Ful.FromSales with { briefly "c" }"""
        val (s1, f1) = salesWithOutlet(
          "        on e: event Shop.Sales.OrderPlaced is { tell command Stock(sku = e.sku) to context Shop.Ful }"
        )
        errorsOf(diagnostics(model(s1, f1, wiring), "ar9-members-partial"), RuleId.ConnectorTypeMismatch) must not be empty
        val (s2, f2) = salesWithOutlet(
          """        on e: event Shop.Sales.OrderPlaced is { tell command Stock(sku = e.sku) to context Shop.Ful }
            |        on s: command Shop.Sales.Ship is { tell command Stock(sku = s.sku) to context Shop.Ful }""".stripMargin
        )
        errorsOf(diagnostics(model(s2, f2, wiring), "ar9-members-all"), RuleId.ConnectorTypeMismatch) mustBe empty
    }
  }

  "an adaptor telling several distinct types through its implied outlet" should {

    "be an Error naming the types, never silently the first" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    adaptor ToFul to context Shop.Ful is {
            |      handler H is {
            |        on p: event OrderPlaced is { tell command Shop.Ful.Receive(sku = p.sku) to context Shop.Ful }
            |        on s: command Ship is { tell command Shop.Ful.Stock(sku = s.sku) to context Shop.Ful }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin,
          fromSalesHandling(
            """        on r: command Receive is { tell command Stock(sku = r.sku) to context Shop.Ful }
              |        on st: command Stock is { tell st to context Shop.Ful }""".stripMargin
          ),
          cross
        ),
        "ar9-ambiguous"
      )
      val found = errorsOf(msgs, RuleId.AdaptorImpliedOutletAmbiguous)
      found must not be empty
      found.head.message must include("'Receive'")
      found.head.message must include("'Stock'")
    }

    "NOT be an Error when several tells carry the SAME type" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    adaptor ToFul to context Shop.Ful is {
            |      handler H is {
            |        on p: event OrderPlaced is { tell command Shop.Ful.Receive(sku = p.sku) to context Shop.Ful }
            |        on s: command Ship is { tell command Shop.Ful.Receive(sku = s.sku) to context Shop.Ful }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin,
          fromSalesHandling(
            "        on r: command Receive is { tell command Stock(sku = r.sku) to context Shop.Ful }"
          ),
          cross
        ),
        "ar9-same-type-twice"
      )
      errorsOf(msgs, RuleId.AdaptorImpliedOutletAmbiguous) mustBe empty
    }
  }

  "AR5, the far-end check" should {

    "accept the far context's INBOUND adaptor as the admitting port (the exclusive shape)" in {
      (td: TestData) =>
        // Before this fix: `no inlet on Context 'Ful' admits Command 'Receive'` on the very
        // arrangement AR6 requires.
        val msgs = diagnostics(
          model(
            tellsReceive,
            fromSalesHandling(
              "        on r: command Receive is { tell command Stock(sku = r.sku) to context Shop.Ful }"
            ),
            cross
          ),
          "ar5-inbound-adaptor-admits"
        )
        errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet) mustBe empty
    }

    "still fire when the far inbound adaptor does not handle the told type either" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            tellsReceive,
            fromSalesHandling(
              "        on e: event Shop.Sales.OrderPlaced is { tell command Stock(sku = e.sku) to context Shop.Ful }"
            ).replace("        on other is { error \"unexpected\" }\n      } with { briefly \"h\" }\n    } with { briefly \"a\" }",
              "      } with { briefly \"h\" }\n    } with { briefly \"a\" }"),
            cross
          ),
          "ar5-inbound-adaptor-no-admit"
        )
        errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet) must not be empty
    }

    "resolve a `let`-bound operand, so a far end with nothing admitting it is now reported" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            tellsLetShip,
            """    inlet In is command Stock with { briefly "i" }
              |    handler FulHandler is { on command Stock is { do "stock" } } with { briefly "h" }""".stripMargin,
            """  persistent connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar5-let-resolved"
        )
        errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet) must not be empty
    }
  }
}
