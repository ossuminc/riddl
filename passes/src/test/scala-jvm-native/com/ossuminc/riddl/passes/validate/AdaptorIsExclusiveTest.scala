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

/** A103, the ADAMANT half (Reid, 2026-09-06; CM §§7.2, 7.7, 8.1): *"being adamant about the
  * processing between two contexts being done by the declared adaptor."*
  *
  *   - AR2 exclusivity: if A declares an adaptor toward B in a direction, EVERY crossing between A
  *     and B in that direction goes through it. A connector from A's own outlet into B where A has
  *     an outbound adaptor toward B is an Error naming that adaptor.
  *   - AR6's consequence, the mirror: a connector from B onto A's own inlet where A has an inbound
  *     adaptor from B is an Error. The foreign message reaches the adaptor FIRST.
  *   - AR8: the outlet-ownership rule binds `send` (and `forward` to a portlet) as it already binds
  *     `tell` (A6). A processor publishes only through its OWN outlet; naming its context's outlet,
  *     or another context's, is an Error.
  *   - AR3's Error: an adaptor's shape is derived from its implied ports, so an ascription that
  *     contradicts it -- `as source`, `as merge` -- is an Error, port-less or not.
  *
  * Every one of these makes an Error out of something the corpus writes today, deliberately; the
  * permissive half exists so both shapes validated during the changeover. Negative controls in each
  * family, per the convention `AdaptorTargetsContextTest` set.
  */
class AdaptorIsExclusiveTest extends AbstractValidatingTest {

  private def model(sales: String, ful: String, wiring: String): String =
    s"""domain Shop is {
       |  context Sales is {
       |    command Ship is { sku: String } with { briefly "s" }
       |$sales
       |  } with { briefly "sales" }
       |  context Ful is {
       |    command Receive is { sku: String } with { briefly "r" }
       |$ful
       |  } with { briefly "ful" }
       |  context Other is {
       |    command Note is { n: Integer } with { briefly "n" }
       |    inlet In is command Note with { briefly "i" }
       |    handler H is { on command Note is { do "note" } } with { briefly "h" }
       |  } with { briefly "other" }
       |$wiring
       |} with { briefly "shop" }
       |""".stripMargin

  private val fulInletAndHandler: String =
    """    inlet In is command Receive with { briefly "i" }
      |    handler FulHandler is {
      |      on command Receive is { do "record the receipt" }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }""".stripMargin

  /** Sales' own outlet toward Ful, fed by a handler that receives Ship. */
  private val salesOwnOutlet: String =
    """    inlet SIn is command Ship with { briefly "i" }
      |    outlet SOut is command Shop.Ful.Receive with { briefly "o" }
      |    handler SalesHandler is {
      |      on s: command Ship is { send command Shop.Ful.Receive(sku = s.sku) to outlet SOut }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }""".stripMargin

  private val outboundAdaptor: String =
    """    adaptor ToFul to context Shop.Ful is {
      |      handler H is {
      |        on ship: command Ship is { tell command Shop.Ful.Receive(sku = ship.sku) to context Shop.Ful }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "a" }""".stripMargin

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    captured

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  "AR2: a connector from a context's OWN outlet into a context it has an outbound adaptor toward" should {

    "be an Error that names the bypassed adaptor" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          salesOwnOutlet + "\n" + outboundAdaptor,
          fulInletAndHandler,
          """  connector Bypass is from outlet Shop.Sales.SOut to inlet Shop.Ful.In with { briefly "c" }
            |  connector Proper is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In with { briefly "c" }""".stripMargin
        ),
        "ar2-bypass"
      )
      val found = errorsOf(msgs, RuleId.ConnectorBypassesAdaptor)
      found must not be empty
      found.head.message must include("'ToFul'")
      found.head.message must include("'Bypass'")
    }

    "NOT fire when the context declares no adaptor toward that context" in { (td: TestData) =>
      // Negative control: an un-adaptored direction crosses context-to-context as before.
      val msgs = diagnostics(
        model(
          salesOwnOutlet,
          fulInletAndHandler,
          """  connector Direct is from outlet Shop.Sales.SOut to inlet Shop.Ful.In with { briefly "c" }"""
        ),
        "ar2-no-adaptor"
      )
      errorsOf(msgs, RuleId.ConnectorBypassesAdaptor) mustBe empty
    }

    "NOT fire when the adaptor is toward a THIRD context" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          salesOwnOutlet +
            """
              |    adaptor ToOther to context Shop.Other is {
              |      handler H is { on other is { do "translate" } } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin,
          fulInletAndHandler,
          """  connector Direct is from outlet Shop.Sales.SOut to inlet Shop.Ful.In with { briefly "c" }"""
        ),
        "ar2-third-context"
      )
      errorsOf(msgs, RuleId.ConnectorBypassesAdaptor) mustBe empty
    }

    "NOT fire when the adaptor toward that context is INBOUND (the other direction)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            salesOwnOutlet +
              """
                |    adaptor FromFul from context Shop.Ful is {
                |      handler H is { on other is { do "translate" } } with { briefly "h" }
                |    } with { briefly "a" }""".stripMargin,
            fulInletAndHandler,
            """  connector Direct is from outlet Shop.Sales.SOut to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar2-other-direction"
        )
        errorsOf(msgs, RuleId.ConnectorBypassesAdaptor) mustBe empty
    }
  }

  "AR6: a connector from B onto A's OWN inlet where A has an inbound adaptor from B" should {

    "be an Error that names the bypassed adaptor" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    inlet In is command Shop.Ful.Receive with { briefly "i" }
            |    handler SalesHandler is { on command Shop.Ful.Receive is { do "handle" } } with { briefly "h" }
            |    adaptor FromFul from context Shop.Ful is {
            |      handler H is { on other is { do "translate" } } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin,
          """    outlet Out is command Receive with { briefly "o" }
            |    handler FulHandler is { on r: command Receive is { send r to outlet Out } } with { briefly "h" }""".stripMargin,
          """  connector Bypass is from outlet Shop.Ful.Out to inlet Shop.Sales.In with { briefly "c" }"""
        ),
        "ar6-bypass"
      )
      val found = errorsOf(msgs, RuleId.ConnectorBypassesAdaptor)
      found must not be empty
      found.head.message must include("'FromFul'")
    }

    "NOT fire when A declares no inbound adaptor from B" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    inlet In is command Shop.Ful.Receive with { briefly "i" }
            |    handler SalesHandler is { on command Shop.Ful.Receive is { do "handle" } } with { briefly "h" }""".stripMargin,
          """    outlet Out is command Receive with { briefly "o" }
            |    handler FulHandler is { on r: command Receive is { send r to outlet Out } } with { briefly "h" }""".stripMargin,
          """  connector Direct is from outlet Shop.Ful.Out to inlet Shop.Sales.In with { briefly "c" }"""
        ),
        "ar6-no-adaptor"
      )
      errorsOf(msgs, RuleId.ConnectorBypassesAdaptor) mustBe empty
    }
  }

  "AR8: a `send` names an outlet" should {

    "be an Error when the outlet belongs to the sender's CONTEXT rather than the sender" in {
      (td: TestData) =>
        // reactive-bbq's `ToKitchen` shape: an adaptor publishing on FrontOfHouse's outlet.
        val msgs = diagnostics(
          model(
            """    outlet SOut is command Shop.Ful.Receive with { briefly "o" }
              |    adaptor ToFul to context Shop.Ful is {
              |      handler H is {
              |        on ship: command Ship is { send command Shop.Ful.Receive(sku = ship.sku) to outlet Shop.Sales.SOut }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin,
            fulInletAndHandler,
            """  connector Cross is from outlet Shop.Sales.SOut to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar8-context-outlet"
        )
        val found = errorsOf(msgs, RuleId.OutletNotOwned)
        found must not be empty
        found.head.message must include("'SOut'")
    }

    "be an Error when the outlet belongs to ANOTHER context entirely" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    adaptor ToFul to context Shop.Ful is {
            |      handler H is {
            |        on ship: command Ship is { send command Shop.Ful.Receive(sku = ship.sku) to outlet Shop.Ful.FOut }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin,
          "    outlet FOut is command Receive with { briefly \"o\" }\n" + fulInletAndHandler,
          ""
        ),
        "ar8-foreign-outlet"
      )
      errorsOf(msgs, RuleId.OutletNotOwned) must not be empty
    }

    "NOT fire for a send on the sender's OWN outlet" in { (td: TestData) =>
      // Negative control, or the rule only proves that sends exist.
      val msgs = diagnostics(
        model(
          """    adaptor ToFul to context Shop.Ful is {
            |      outlet Out is command Shop.Ful.Receive with { briefly "o" }
            |      handler H is {
            |        on ship: command Ship is { send command Shop.Ful.Receive(sku = ship.sku) to outlet Out }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin,
          fulInletAndHandler,
          """  connector Cross is from outlet Shop.Sales.ToFul.Out to inlet Shop.Ful.In with { briefly "c" }"""
        ),
        "ar8-own-outlet"
      )
      errorsOf(msgs, RuleId.OutletNotOwned) mustBe empty
    }

    "NOT fire for a context's own handler sending on the context's outlet" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          salesOwnOutlet,
          fulInletAndHandler,
          """  connector Cross is from outlet Shop.Sales.SOut to inlet Shop.Ful.In with { briefly "c" }"""
        ),
        "ar8-context-handler"
      )
      errorsOf(msgs, RuleId.OutletNotOwned) mustBe empty
    }

    "bind `forward` to a portlet the same way" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    command Order yields event Shop.Sales.Ordered is { sku: String } with { briefly "o" }
            |    event Ordered is { sku: String } with { briefly "e" }
            |    outlet SOut is command Shop.Sales.Order with { briefly "o" }
            |    streamlet Relay as flow is {
            |      inlet I is command Shop.Sales.Order with { briefly "i" }
            |      outlet O is command Shop.Sales.Order with { briefly "o" }
            |      handler H is {
            |        on o: command Shop.Sales.Order is { forward o to outlet Shop.Sales.SOut }
            |      } with { briefly "h" }
            |    } with { briefly "relay" }""".stripMargin,
          fulInletAndHandler,
          ""
        ),
        "ar8-forward"
      )
      errorsOf(msgs, RuleId.OutletNotOwned) must not be empty
    }
  }

  "AR3: an adaptor's shape ascription" should {

    "be an Error when it contradicts the implied flow on a port-less adaptor (`as source`)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            outboundAdaptor.replace("to context Shop.Ful is {", "to context Shop.Ful as source is {"),
            fulInletAndHandler,
            """  connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In with { briefly "c" }"""
          ),
          "ar3-source"
        )
        msgs.justErrors.filter(_.message.contains("is ascribed 'as source'")) must not be empty
    }

    "be an Error for `as merge` on a port-less adaptor" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          outboundAdaptor.replace("to context Shop.Ful is {", "to context Shop.Ful as merge is {"),
          fulInletAndHandler,
          """  connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In with { briefly "c" }"""
        ),
        "ar3-merge"
      )
      msgs.justErrors.filter(_.message.contains("is ascribed 'as merge'")) must not be empty
    }

    "be an Error for `as source` on an adaptor that declares ONE outlet (the corpus's 31)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """    inlet In is command Ship with { briefly "i" }
              |    handler SalesHandler is { on command Ship is { do "ship" } } with { briefly "h" }
              |    adaptor FromFul from context Shop.Ful as source is {
              |      outlet Out is command Ship with { briefly "o" }
              |      handler H is { on s: command Ship is { send s to outlet Out } } with { briefly "h" }
              |    } with { briefly "a" }
              |    connector Inward is from outlet Shop.Sales.FromFul.Out to inlet Shop.Sales.In with { briefly "c" }""".stripMargin,
            fulInletAndHandler,
            ""
          ),
          "ar3-declared-outlet-source"
        )
        msgs.justErrors.filter(_.message.contains("is ascribed 'as source'")) must not be empty
    }

    "accept `as flow`, which is what every adaptor is" in { (td: TestData) =>
      // Negative control.
      val msgs = diagnostics(
        model(
          outboundAdaptor.replace("to context Shop.Ful is {", "to context Shop.Ful as flow is {"),
          fulInletAndHandler,
          """  connector Cross is from outlet Shop.Sales.ToFul to inlet Shop.Ful.In with { briefly "c" }"""
        ),
        "ar3-flow"
      )
      msgs.justErrors.filter(_.message.contains("is ascribed")) mustBe empty
    }
  }
}
