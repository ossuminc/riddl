/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `msg-tell-no-route-for-type` (Reid, 2026-10-21): connectors join a `tell`'s sender to its
  * target, so A6 is satisfied, but no route CARRIES the told type there. Found in reactive-bbq,
  * whose split tells EVENTS to `TableOrder`, an entity whose inlets admit only commands; the
  * generator had picked an outlet by type-blind reachability and delivered the event anyway.
  */
class TellNoRouteForTypeTest extends AbstractValidatingTest {

  /** A sender `S` wired to `Order` through `wiring`; `Order`'s inlet type is `orderInlet`. */
  private def model(orderInlet: String, wiring: String, tellTo: String = "entity C.Order"): String =
    s"""domain D is {
       |  context C is {
       |    event Placed is { id: String } with { briefly "p" }
       |    command Place is { id: String } with { briefly "c" }
       |    entity Order is {
       |      inlet OrderIn is $orderInlet with { briefly "i" }
       |      handler OH is {
       |        on event C.Placed is { do "record it" }
       |        on command C.Place is { do "place it" }
       |      } with { briefly "h" }
       |    } with { briefly "o" }
       |    streamlet S as source is {
       |      outlet Out is event C.Placed with { briefly "o" }
       |      handler SH is {
       |        on init is { tell event C.Placed(id = "x") to $tellTo }
       |      } with { briefly "h" }
       |    } with { briefly "s" }
       |$wiring
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private val direct = "    connector A is from outlet C.S.Out to inlet C.Order.OrderIn"

  private def relay(clause: String): String =
    s"""    streamlet Relay as flow is {
       |      inlet RelayIn is event C.Placed with { briefly "i" }
       |      outlet RelayOut is event C.Placed with { briefly "o" }
       |      handler RH is { $clause } with { briefly "h" }
       |    } with { briefly "r" }
       |    connector A is from outlet C.S.Out to inlet C.Relay.RelayIn
       |    connector B is from outlet C.Relay.RelayOut to inlet C.Order.OrderIn""".stripMargin

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse:\n${m.format}")
      case None    => captured

  private def ruled(src: String, origin: String, rule: RuleId): Messages =
    diagnostics(src, origin).filter(_.ruleId.contains(rule))

  "a tell no route can carry" should {
    "be an Error when no inlet of the target admits the message (reactive-bbq's shape)" in {
      (td: TestData) =>
        // The connector exists, but OrderIn admits the COMMAND, not the told event.
        val src = model("command C.Place", "    connector A is from outlet C.S.Out to inlet C.Order.OrderIn")
        val all = diagnostics(src, td.name)
        val found = all.filter(_.ruleId.contains(RuleId.TellNoRouteForType))
        withClue(all.map(_.format).mkString("\n")) {
          found must have size 1
          found.head.kind.isError mustBe true
          found.head.message must include("none of Entity 'Order''s inlets admits it")
          all.filter(_.ruleId.contains(RuleId.TellTargetUnreachable)) mustBe empty
        }
    }

    "be an Error when the only path runs through a processor that consumes the message" in {
      (td: TestData) =>
        val src = model("event C.Placed", relay("""on event C.Placed is { do "show it" }"""))
        val all = diagnostics(src, td.name)
        val found = all.filter(_.ruleId.contains(RuleId.TellNoRouteForType))
        withClue(all.map(_.format).mkString("\n")) {
          found must have size 1
          found.head.message must include("does not admit Event 'Placed' or does not pass it on")
        }
    }
  }

  "a tell some route carries" should {
    "draw nothing over a direct connector into an admitting inlet" in { (td: TestData) =>
      ruled(model("event C.Placed", direct), td.name, RuleId.TellNoRouteForType) mustBe empty
    }

    "draw nothing through a processor that handles the message and re-sends it" in {
      (td: TestData) =>
        val src = model("event C.Placed", relay("on p: event C.Placed is { send p to outlet C.Relay.RelayOut }"))
        ruled(src, td.name, RuleId.TellNoRouteForType) mustBe empty
    }
  }

  "a tell another rule already answers" should {
    "leave a tell with no connector at all to A6, reporting it once" in { (td: TestData) =>
      val all = diagnostics(model("command C.Place", ""), td.name)
      withClue(all.map(_.format).mkString("\n")) {
        all.filter(_.ruleId.contains(RuleId.TellNoRouteForType)) mustBe empty
        all.filter(_.ruleId.contains(RuleId.TellTargetUnreachable)) must not be empty
      }
    }
  }
}
