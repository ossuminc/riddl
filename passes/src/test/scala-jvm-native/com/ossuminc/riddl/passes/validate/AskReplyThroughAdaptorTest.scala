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

/** The adaptor is the boundary for its PAIR in BOTH directions (Reid, 2026-09-11).
  *
  * riddl-models found, on the first riddlc carrying [1.25], that an asking `to context X` adaptor
  * could not be completed: declare its ports and `msg-ask-reply-unreachable` demands a connector
  * from X back into it, while `stream-boundary-inlet` refused exactly that connector, because the
  * boundary exemption was DIRECTIONAL -- an outbound adaptor could be only the `from` end of a
  * crossing into X. Two rules each refusing the other's remedy, the unrelated-domains shape again.
  *
  * The ruling: *the asking adaptor owns both legs* -- an inlet for the result and an outlet carrying
  * the request -- and, connectors being unidirectional, the reply needs its own outlet-to-inlet
  * path. By the same token the inbound adaptor that ANSWERS a query from X owns the reply's outlet
  * toward X (an adaptor may not `reply` itself; the CONTEXT does, on its own outlet, which the
  * boundary rule always allowed). The direction keyword names the adaptor's translation duty (which
  * `adaptor-direction-advisory` judges), not which way its wires may run.
  *
  * Exclusivity (AR2/AR6) is untouched and pinned here: it fires only when a crossing end is the
  * CONTEXT'S OWN portlet, and the reply lands on the adaptor's.
  */
class AskReplyThroughAdaptorTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  /** The water-utility shape: `Water` asks `Scada` for readings through an outbound adaptor that
    * declares BOTH its request outlet and its reply inlet; `Scada` declares the request inlet and
    * the reply outlet; `wiring` supplies the connectors.
    */
  private def askModel(wiring: String, replyInletOwner: String = "adaptor"): String =
    val replyInlet =
      if replyInletOwner == "adaptor" then
        "      inlet Replies is result D.Scada.Readings with { briefly \"r\" }\n"
      else ""
    val contextReplyInlet =
      if replyInletOwner == "context" then
        "    inlet WaterReplies is result D.Scada.Readings with { briefly \"r\" }\n"
      else ""
    s"""domain D is {
       |  context Water is {
       |    event AlertRaised is { id: String } with { briefly "e" }
       |    inlet Alerts is event AlertRaised with { briefly "i" }
       |$contextReplyInlet    handler WH is {
       |      on event AlertRaised is { do "raise it" }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |    adaptor ToScada to context D.Scada is {
       |      inlet In is event AlertRaised with { briefly "i" }
       |      outlet Requests is query D.Scada.GetReadings with { briefly "o" }
       |$replyInlet      handler AH is {
       |        on event AlertRaised is {
       |          let readings: type D.Scada.Readings = ask query D.Scada.GetReadings of context D.Scada
       |          do "act on the readings"
       |        }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "a" }
       |  } with { briefly "w" }
       |  context Scada is {
       |    result Readings is { value: Integer } with { briefly "r" }
       |    query GetReadings replies result D.Scada.Readings is { id: String } with { briefly "q" }
       |    inlet RequestsIn is query GetReadings with { briefly "i" }
       |    outlet RepliesOut is result Readings with { briefly "o" }
       |    handler SH is {
       |      on query GetReadings is { reply result D.Scada.Readings(value = 1) }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |  } with { briefly "s" }
       |$wiring
       |} with { briefly "d" }
       |""".stripMargin

  private val request =
    """  persistent connector Request is from outlet D.Water.ToScada.Requests to inlet D.Scada.RequestsIn with { briefly "c" }"""
  private val replyToAdaptor =
    """  persistent connector Reply is from outlet D.Scada.RepliesOut to inlet D.Water.ToScada.Replies with { briefly "c" }"""

  "the reply to an ask from an outbound adaptor" should {

    "land on the ASKING adaptor's declared inlet, both legs wired, with zero errors" in {
      (td: TestData) =>
        val msgs = diagnostics(askModel(request + "\n" + replyToAdaptor), td.name)
        errorsOf(msgs, RuleId.BoundaryInlet) mustBe empty
        errorsOf(msgs, RuleId.AskReplyUnreachable) mustBe empty
        errorsOf(msgs, RuleId.AskTargetUnreachable) mustBe empty
        errorsOf(msgs, RuleId.ConnectorBypassesAdaptor) mustBe empty
        withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "still be REQUIRED: with only the request leg wired, the reply leg is reported" in {
      (td: TestData) =>
        // Negative control on the ask side: the boundary opening did not delete the reply rule.
        val msgs = diagnostics(askModel(request), td.name)
        errorsOf(msgs, RuleId.AskReplyUnreachable).size mustBe 1
    }

    "still be REJECTED when it arrives at an adaptor declared toward a THIRD context" in {
      (td: TestData) =>
        // The exemption is for the PAIR, not for adaptors in general.
        val third = askModel(request + "\n" + replyToAdaptor)
          .replace("adaptor ToScada to context D.Scada is {", "adaptor ToScada to context D.Other is {")
          .replace(
            "  context Scada is {",
            "  context Other is {\n    command Noop is { n: Integer } with { briefly \"n\" }\n" +
              "    inlet OIn is command Noop with { briefly \"i\" }\n" +
              "    handler OH is { on command Noop is { do \"x\" } } with { briefly \"h\" }\n" +
              "  } with { briefly \"o\" }\n  context Scada is {"
          )
        errorsOf(diagnostics(third, td.name), RuleId.BoundaryInlet) must not be empty
    }

    "still be REJECTED when it reaches past the boundary onto a contained ENTITY" in {
      (td: TestData) =>
        val entity = askModel(request)
          .replace(
            "  } with { briefly \"w\" }",
            "    entity Reader is {\n" +
              "      inlet ReaderIn is result D.Scada.Readings with { briefly \"i\" }\n" +
              "      record F is { v: Integer } with { briefly \"f\" }\n" +
              "      state S of record Reader.F is {\n" +
              "        handler RH is { on result D.Scada.Readings is { do \"read\" } } with { briefly \"h\" }\n" +
              "      } with { briefly \"s\" }\n" +
              "    } with { briefly \"e\" }\n" +
              "  } with { briefly \"w\" }"
          )
          .replace(
            request,
            request + "\n" +
              """  persistent connector Leak is from outlet D.Scada.RepliesOut to inlet D.Water.Reader.ReaderIn with { briefly "c" }"""
          )
        errorsOf(diagnostics(entity, td.name), RuleId.BoundaryInlet) must not be empty
    }
  }

  /** A CONTEXT that asks directly, with no `on other` to mask `stream-inlet-not-received`. An
    * adaptor must declare `on other`, so the consumption rule can only be observed here.
    */
  private def contextAsker(replyType: String): String =
    s"""domain D is {
       |  context Water is {
       |    event AlertRaised is { id: String } with { briefly "e" }
       |    inlet Alerts is event AlertRaised with { briefly "i" }
       |    inlet Replies is result D.Scada.$replyType with { briefly "r" }
       |    outlet Requests is query D.Scada.GetReadings with { briefly "o" }
       |    handler WH is {
       |      on event AlertRaised is {
       |        let readings: type D.Scada.Readings = ask query D.Scada.GetReadings of context D.Scada
       |        do "act on the readings"
       |      }
       |    } with { briefly "h" }
       |  } with { briefly "w" }
       |  context Scada is {
       |    result Readings is { value: Integer } with { briefly "r" }
       |    result Other is { value: Integer } with { briefly "r2" }
       |    query GetReadings replies result D.Scada.Readings is { id: String } with { briefly "q" }
       |    inlet RequestsIn is query GetReadings with { briefly "i" }
       |    outlet RepliesOut is result $replyType with { briefly "o" }
       |    handler SH is {
       |      on query GetReadings is { reply result D.Scada.Readings(value = 1) }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |  } with { briefly "s" }
       |  persistent connector Request is from outlet D.Water.Requests to inlet D.Scada.RequestsIn with { briefly "c" }
       |  persistent connector Reply is from outlet D.Scada.RepliesOut to inlet D.Water.Replies with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "the reply inlet an asker owns" should {

    "be CONSUMED by the ask's value, so it is not an inlet nothing receives" in { (td: TestData) =>
      // The answer arrives as the ask's VALUE, not through an `on result` clause -- which an
      // outbound adaptor may not even declare (`adaptor-outbound-wrong-message`).
      val msgs = diagnostics(contextAsker("Readings"), td.name)
      msgs.filter(_.ruleId.contains(RuleId.InletNotReceived)) mustBe empty
      errorsOf(msgs, RuleId.AskReplyUnreachable) mustBe empty
    }

    "still be reported when its type is one no ask answers with (negative control)" in {
      (td: TestData) =>
        diagnostics(contextAsker("Other"), td.name)
          .filter(_.ruleId.contains(RuleId.InletNotReceived)) must not be empty
    }
  }

  "exclusivity" should {

    "be UNTOUCHED: a crossing onto the context's OWN inlet still bypasses its inbound adaptor" in {
      (td: TestData) =>
        // Water also declares an inbound adaptor from Scada; a reply wired to Water's OWN inlet
        // rather than to an adaptor is a bypass (AR6), exactly as before this change.
        val bypass = askModel(
          request + "\n" +
            """  persistent connector Reply is from outlet D.Scada.RepliesOut to inlet D.Water.WaterReplies with { briefly "c" }""",
          replyInletOwner = "context"
        ).replace(
          "  } with { briefly \"w\" }",
          "    adaptor FromScada from context D.Scada is {\n" +
            "      inlet FIn is result D.Scada.Readings with { briefly \"i\" }\n" +
            "      handler FH is { on result D.Scada.Readings is { do \"translate\" } } with { briefly \"h\" }\n" +
            "    } with { briefly \"a\" }\n" +
            "  } with { briefly \"w\" }"
        )
        errorsOf(diagnostics(bypass, td.name), RuleId.ConnectorBypassesAdaptor) must not be empty
    }
  }

}
