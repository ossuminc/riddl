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

/** Origination is about what a processor RECEIVES (Reid, 2026-09-23, on riddl-models' report).
  *
  * *"Origination is denoted by having outlets that send commands or queries. Having inlets that
  * ONLY receive events or results isn't an indication of origination, just receipt of a reply to
  * the originating command or query. So, just denoting origination by the absence of inlets is
  * incorrect. … any processor that uses the reply or yield statements is an originator of the
  * reply (a result or event), so when the application gets that reply, it is not the originator
  * but the receiver."*
  *
  * Both halves are asserted here, because the rule CHANGED in two directions: an application
  * that consumes results is now an origin (it was not, so no pure sink below one could ever be
  * reached), and a processor whose inlet carries a COMMAND is NOT an origin even with no inbound
  * edge (it was, by `isGraphHead`).
  */
class SinkReachedFromApplicationTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def of(msgs: Messages, rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))

  /** reactive-bbq's shape: an application sends commands and consumes results; a display sink
    * below the responder receives only events.
    */
  private val throughApplication =
    """domain Rest is {
      |  context App is {
      |    command Order is { id: String } with { briefly "c" }
      |    result Confirmed is { id: String } with { briefly "r" }
      |    outlet Commands is command App.Order with { briefly "o" }
      |    inlet Replies is result App.Confirmed with { briefly "i" }
      |    handler AH is {
      |      on result App.Confirmed is { do "show it" }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }
      |  } with { briefly "the application" option application }
      |  context Kitchen is {
      |    event Cooked is { id: String } with { briefly "e" }
      |    inlet Orders is command App.Order with { briefly "i" }
      |    outlet Replies is result App.Confirmed with { briefly "o" }
      |    outlet Events is event Kitchen.Cooked with { briefly "o" }
      |    handler KH is {
      |      on command App.Order is { send result App.Confirmed(id = "x") to outlet Kitchen.Replies }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }
      |    streamlet Display as sink is {
      |      inlet Shown is event Kitchen.Cooked with { briefly "i" }
      |      handler DH is {
      |        on event Kitchen.Cooked is { do "display it" }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "a display that only receives" }
      |    persistent connector C3 is from outlet Kitchen.Events to inlet Kitchen.Display.Shown with { briefly "c" }
      |  } with { briefly "the kitchen" }
      |  persistent connector C1 is from outlet App.Commands to inlet Kitchen.Orders with { briefly "c" }
      |  persistent connector C2 is from outlet Kitchen.Replies to inlet App.Replies with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  /** The same graph with the application's command outlet REMOVED, so the only thing above the
    * sink is a context whose inlet carries a command and which nothing feeds: a responder with no
    * origin behind it.
    */
  private val commandFromNowhere =
    """domain Rest is {
      |  context Kitchen is {
      |    command Order is { id: String } with { briefly "c" }
      |    event Cooked is { id: String } with { briefly "e" }
      |    inlet Orders is command Kitchen.Order with { briefly "i" }
      |    outlet Events is event Kitchen.Cooked with { briefly "o" }
      |    handler KH is {
      |      on command Kitchen.Order is { send event Kitchen.Cooked(id = "x") to outlet Kitchen.Events }
      |      on other is { error "unexpected" }
      |    } with { briefly "h" }
      |    streamlet Display as sink is {
      |      inlet Shown is event Kitchen.Cooked with { briefly "i" }
      |      handler DH is {
      |        on event Kitchen.Cooked is { do "display it" }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "a display" }
      |    persistent connector C3 is from outlet Kitchen.Events to inlet Kitchen.Display.Shown with { briefly "c" }
      |  } with { briefly "the kitchen" }
      |} with { briefly "d" }
      |""".stripMargin

  "sink reachability" should {

    "accept a sink whose chain originates in an application that consumes results" in {
      (td: TestData) =>
        of(diagnostics(throughApplication, td.name), RuleId.SinkReachedByNoSource) mustBe empty
    }

    "still report a sink whose only ancestor RECEIVES a command and is fed by nothing" in {
      (td: TestData) =>
        // The half of the ruling that TIGHTENS: "just denoting origination by the absence of
        // inlets is incorrect". Before this, the Kitchen context was a graph head (an outlet and
        // no inbound edge) and the sink below it was considered reached.
        of(diagnostics(commandFromNowhere, td.name), RuleId.SinkReachedByNoSource).size mustBe 1
    }
  }
}
