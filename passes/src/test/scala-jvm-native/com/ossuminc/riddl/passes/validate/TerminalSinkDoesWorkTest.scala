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

/** A sink that DOES something but tells nothing is TERMINAL (Reid, 2026-09-25).
  *
  * `handler-streamlet-foreign-message` USED to ask a sink "you handle messages but never dispatch
  * to an entity -- why?". That is a fair question for an INTAKE sink and wrong for a TERMINAL one, and
  * the rule had already been narrowed twice for the same reason (split/merge/flow; then
  * Repository/Projector at rc.16) before riddl-models' kitchen display made it three: a display
  * logs and renders, has no entity to tell, and was being asked to invent one -- the round trip
  * riddl-generator had just had removed.
  *
  * The question is now the one the rule can answer honestly: has the sink SAID what it does with
  * what it receives? Work says yes; prose does not.
  */
class TerminalSinkDoesWorkTest extends AbstractValidatingTest {

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

  /** A context with an entity (the rule's outer guard) and a sink whose clause body varies. */
  private def model(sinkBody: String): String =
    s"""domain Kitchen is {
       |  context Tickets is {
       |    command Cook is { id: String } with { briefly "c" }
       |    event Cooked is { id: String } with { briefly "e" }
       |    entity Ticket is {
       |      record F is { id: String } with { briefly "f" }
       |      state S of record Ticket.F
       |      inlet In is command Tickets.Cook with { briefly "i" }
       |      outlet Out is event Tickets.Cooked with { briefly "o" }
       |      handler TH is {
       |        on init is { yield event Tickets.Cooked(id = "x") }
       |        on c: command Tickets.Cook is { yield event Tickets.Cooked(id = c.id) }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "t" }
       |    streamlet Display as sink is {
       |      inlet Shown is event Tickets.Cooked with { briefly "i" }
       |      handler DH is {
       |        on ev: event Tickets.Cooked is {
       |          $sinkBody
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "the display" }
       |    persistent connector C is from outlet Ticket.Out to inlet Display.Shown with { briefly "c" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a terminal sink" should {

    "draw nothing when it LOGS what it receives -- riddl-models' kitchen display" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model("""log ev
                  |          do "render the ticket on the kitchen display screen"""".stripMargin),
          td.name
        )
        of(msgs, RuleId.StreamletForeignMessage) mustBe empty
    }

    "draw nothing when the work is a CODE block -- any real work counts" in { (td: TestData) =>
      // Not just `log`: the rule asks whether the sink has SAID what it does, and a verbatim
      // code block says it more concretely than anything else in the language.
      val work = "```scala renderTicket(ev) ```"
      of(diagnostics(model(work), td.name), RuleId.StreamletForeignMessage) mustBe empty
    }

    "STILL report a sink whose clauses are only prose (it has not said what it does)" in {
      (td: TestData) =>
        of(diagnostics(model("""do "something happens here""""), td.name),
          RuleId.StreamletForeignMessage).size mustBe 1
    }

    "draw nothing when it does dispatch (the original shape, unchanged)" in { (td: TestData) =>
      of(diagnostics(model("""tell event Tickets.Cooked(id = ev.id) to entity Tickets.Ticket"""), td.name),
        RuleId.StreamletForeignMessage) mustBe empty
    }
  }
}
