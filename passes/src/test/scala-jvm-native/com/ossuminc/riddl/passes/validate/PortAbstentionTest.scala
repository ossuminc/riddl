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

/** A rule ABSTAINS on the side it cannot read (Reid, 2026-09-11; [1.25]).
  *
  * When a processor needs a port and has not declared it, `ProcessorPortsIncompleteTest`'s Missing
  * warning is the whole report. Every rule that would read that side declines -- inlet-reading rules
  * wait for the inlet, outlet-reading rules for the outlet, rules needing both wait for both --
  * because *"you can't say anything about the constructed graph"* until the port exists. This is
  * `???`'s treatment generalised: the author has been told what to write, and nothing reasons from
  * what is not there.
  *
  * The consequence that breaks models is the OTHER direction: nothing is implied any more, so a
  * connector endpoint that names an ADAPTOR (A103's "the definition is the port") is a wrong-kind
  * reference again -- 26 corpus sites at the time of writing, every one of them written in the belief
  * that `<Context>.<Adaptor>` named a portlet.
  *
  * Each abstention has a companion showing the rule RETURNS once the port is declared, so an
  * abstention cannot pass by the rule having been deleted.
  */
class PortAbstentionTest extends AbstractValidatingTest {

  /** Missing warnings are dropped by the accumulator when `showMissingWarnings` is off, and
    * `pc.options` is global state other suites mutate -- so the defaults are pinned per call.
    */
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

  private def anyOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(_.ruleId.contains(rule))

  /** Near tells Far. Far always has an admitting inlet and a handler, so only the SENDER varies. */
  private def tellModel(near: String, wiring: String = ""): String =
    s"""domain D is {
       |  context Far is {
       |    command Receive is { sku: String } with { briefly "r" }
       |    inlet In is command Receive with { briefly "i" }
       |    handler H is {
       |      on command Receive is { do "x" }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |  } with { briefly "far" }
       |  context Near is {
       |    command Ship is { sku: String } with { briefly "s" }
       |$near
       |  } with { briefly "near" }
       |$wiring
       |} with { briefly "d" }
       |""".stripMargin

  "tell reachability" should {

    "ABSTAIN for an adaptor sender that declares no outlet (the Missing warning is the report)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          tellModel(
            """    adaptor ToFar to context D.Far is {
              |      inlet In is command Ship with { briefly "i" }
              |      handler H is {
              |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin
          ),
          td.name
        )
        errorsOf(msgs, RuleId.TellTargetUnreachable) mustBe empty
        anyOf(msgs, RuleId.StreamProcessorNoOutlet).size mustBe 1
    }

    "RETURN once the adaptor declares its outlet and still has no connector" in { (td: TestData) =>
      val msgs = diagnostics(
        tellModel(
          """    adaptor ToFar to context D.Far is {
            |      inlet In is command Ship with { briefly "i" }
            |      outlet Out is command D.Far.Receive with { briefly "o" }
            |      handler H is {
            |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin
        ),
        td.name
      )
      errorsOf(msgs, RuleId.TellTargetUnreachable).size mustBe 1
      anyOf(msgs, RuleId.StreamProcessorNoOutlet) mustBe empty
    }

    "ABSTAIN for an ENTITY sender with no outlet -- symmetric, one omission reported once" in {
      (td: TestData) =>
        val msgs = diagnostics(
          tellModel(
            """    entity E is {
              |      inlet In is command Ship with { briefly "i" }
              |      record Fields is { sku: String } with { briefly "f" }
              |      state Main of record E.Fields is {
              |        handler H is {
              |          on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
              |          on other is { error "unexpected" }
              |        } with { briefly "h" }
              |      } with { briefly "s" }
              |    } with { briefly "e" }""".stripMargin
          ),
          td.name
        )
        errorsOf(msgs, RuleId.TellTargetUnreachable) mustBe empty
        anyOf(msgs, RuleId.EntityNoOutlet).size mustBe 1
    }
  }

  /** Asker `A` and answerer `B`; `asker` is A's port declarations, `wiring` the connectors. */
  private def askModel(askerPorts: String, wiring: String = ""): String =
    s"""domain D is {
       |  result Xs is { n: Integer } with { briefly "x" }
       |  query GetX replies result D.Xs is { id: String } with { briefly "q" }
       |  event Trigger is { id: String } with { briefly "t" }
       |  context A is {
       |    inlet Ain is event D.Trigger with { briefly "i" }
       |    handler AH is {
       |      on event D.Trigger is {
       |        let answer: type D.Xs = ask query D.GetX of context D.B
       |        do "use the answer"
       |      }
       |      on result D.Xs is { do "the reply arrives here" }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |  } with { briefly "a" }
       |  context B is {
       |    inlet Bin is query D.GetX with { briefly "i" }
       |    outlet Bout is result D.Xs with { briefly "o" }
       |    handler BH is {
       |      on query D.GetX is { reply result D.Xs(n = 1) }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |  } with { briefly "b" }
       |$wiring
       |} with { briefly "d" }
       |""".stripMargin

  "ask reachability" should {

    "ABSTAIN on the QUESTION leg when the asker declares no outlet" in { (td: TestData) =>
      // A has an inlet for the trigger and the reply, but no outlet. Only the question leg reads
      // the asker's outlet, so only it abstains; the reply leg has an inlet to read and no
      // connector to find, so it still reports.
      val msgs = diagnostics(askModel("", ""), td.name)
      errorsOf(msgs, RuleId.AskTargetUnreachable) mustBe empty
      anyOf(msgs, RuleId.StreamProcessorNoOutlet).exists(_.message.contains("'A'")) mustBe true
    }

    "RETURN on both legs once the asker declares its outlet and nothing is wired" in {
      (td: TestData) =>
        val withOutlet = askModel("", "")
          .replace("    inlet Ain is event D.Trigger with { briefly \"i\" }",
            "    inlet Ain is event D.Trigger with { briefly \"i\" }\n" +
              "    outlet Aout is query D.GetX with { briefly \"o\" }")
        val msgs = diagnostics(withOutlet, td.name)
        errorsOf(msgs, RuleId.AskTargetUnreachable).size mustBe 1
        errorsOf(msgs, RuleId.AskReplyUnreachable).size mustBe 1
    }
  }

  "the shape ascription check" should {

    "ABSTAIN for `as flow` on an adaptor whose inlet is still missing" in { (td: TestData) =>
      val msgs = diagnostics(
        tellModel(
          """    adaptor ToFar to context D.Far as flow is {
            |      handler H is {
            |        on command Ship is { do "translate" }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin
        ),
        td.name
      )
      errorsOf(msgs, RuleId.AscribedShapeMismatch) mustBe empty
      anyOf(msgs, RuleId.StreamProcessorNoInlet).size mustBe 1
    }

    "ABSTAIN for `as source` on an adaptor with one declared outlet whose inlet is missing" in {
      (td: TestData) =>
        // The corpus's 31 `as source` adaptors: under A103 this was an Error (implied inlet made
        // it a flow). Now the inlet is simply MISSING, and the shape is not yet knowable.
        val msgs = diagnostics(
          tellModel(
            """    adaptor ToFar to context D.Far as source is {
              |      outlet Out is command D.Far.Receive with { briefly "o" }
              |      handler H is {
              |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin
          ),
          td.name
        )
        errorsOf(msgs, RuleId.AscribedShapeMismatch) mustBe empty
        anyOf(msgs, RuleId.StreamProcessorNoInlet).size mustBe 1
    }

    "RETURN once both ports are declared: `as source` with an inlet and an outlet is a flow" in {
      (td: TestData) =>
        val msgs = diagnostics(
          tellModel(
            """    adaptor ToFar to context D.Far as source is {
              |      inlet In is command Ship with { briefly "i" }
              |      outlet Out is command D.Far.Receive with { briefly "o" }
              |      handler H is {
              |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin
          ),
          td.name
        )
        errorsOf(msgs, RuleId.AscribedShapeMismatch).size mustBe 1
    }

    "ABSTAIN from the ports-without-shape NUDGE while a needed outlet is missing" in { (td: TestData) =>
      // An adaptor that tells but has no outlet yet derives `sink` today and `flow` once the
      // outlet is written; nudging it toward `as sink` now would prescribe the lie.
      val msgs = pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
        var captured: Messages = Messages.empty
        parseAndValidate(
          tellModel(
            """    adaptor ToFar to context D.Far is {
              |      inlet In is command Ship with { briefly "i" }
              |      handler H is {
              |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin
          ),
          td.name,
          shouldFailOnErrors = false
        ) { (_, _, m) => captured = m; succeed }
        captured
      }
      anyOf(msgs, RuleId.PortsWithoutShape).filter(_.message.contains("'ToFar'")) mustBe empty
      anyOf(msgs, RuleId.StreamProcessorNoOutlet).size mustBe 1
    }

    "NUDGE toward the truth about what is written once the ports are complete (`as sink` for a consumer)" in {
      (td: TestData) =>
        // A consume-only inbound adaptor with one inlet and no ascription IS a sink; the migration
        // for one that carried `as flow` under A103 is to delete the ascription and let this say so.
        // `provideTips` is required to see a suggestion at all (the accumulator strips it otherwise).
        val msgs = pc.withOptions(
          CommonOptions(showStyleWarnings = true, showWarnings = true, provideTips = true)
        ) { _ =>
          var captured: Messages = Messages.empty
          parseAndValidate(
            tellModel(
              """    adaptor FromFar from context D.Far is {
                |      inlet In is command D.Far.Receive with { briefly "i" }
                |      handler H is {
                |        on command D.Far.Receive is { do "note it; nothing is passed on" }
                |        on other is { error "unexpected" }
                |      } with { briefly "h" }
                |    } with { briefly "a" }""".stripMargin
            ),
            td.name,
            shouldFailOnErrors = false
          ) { (_, _, m) => captured = m; succeed }
          captured
        }
        val nudge = anyOf(msgs, RuleId.PortsWithoutShape).filter(_.message.contains("'FromFar'"))
        nudge.size mustBe 1
        nudge.head.suggestion must include("as sink")
        errorsOf(msgs, RuleId.AscribedShapeMismatch) mustBe empty
    }

    "accept `as merge` on an adaptor with two inlets and one outlet -- adaptors are not special" in {
      (td: TestData) =>
        val msgs = diagnostics(
          tellModel(
            """    command Cancel is { sku: String } with { briefly "c" }
              |    adaptor ToFar to context D.Far as merge is {
              |      inlet Ships is command Ship with { briefly "i" }
              |      inlet Cancels is command Cancel with { briefly "i2" }
              |      outlet Out is command D.Far.Receive with { briefly "o" }
              |      handler H is {
              |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
              |        on c: command Cancel is { tell command D.Far.Receive(sku = c.sku) to context D.Far }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin
          ),
          td.name
        )
        errorsOf(msgs, RuleId.AscribedShapeMismatch) mustBe empty
    }
  }

  /** Near's outbound adaptor tells Far, whose OWN inlets do not admit the message; Far's inbound
    * adaptor from Near is where AR5 looks next. `farInbound` is that adaptor's port declaration.
    */
  private def ar5Model(farInboundPorts: String): String =
    s"""domain D is {
       |  context Far is {
       |    command Receive is { sku: String } with { briefly "r" }
       |    command Other is { n: Integer } with { briefly "o" }
       |    inlet In is command Other with { briefly "i" }
       |    handler H is {
       |      on command Other is { do "x" }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |    adaptor FromNear from context D.Near is {
       |$farInboundPorts
       |      handler AH is {
       |        on r: command Receive is { tell command D.Far.Other(n = 1) to context D.Far }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "in" }
       |  } with { briefly "far" }
       |  context Near is {
       |    command Ship is { sku: String } with { briefly "s" }
       |    adaptor ToFar to context D.Far is {
       |      inlet In is command Ship with { briefly "i" }
       |      outlet Out is command D.Far.Receive with { briefly "o" }
       |      handler H is {
       |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "a" }
       |  } with { briefly "near" }
       |} with { briefly "d" }
       |""".stripMargin

  "AR5's far-inbound-adaptor arm" should {

    "ABSTAIN when the far inbound adaptor has not declared its inlet" in { (td: TestData) =>
      val msgs = diagnostics(ar5Model(""), td.name)
      errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet) mustBe empty
    }

    "be satisfied by a DECLARED inlet on the far inbound adaptor that admits the message" in {
      (td: TestData) =>
        val msgs = diagnostics(
          ar5Model("""      inlet In is command D.Far.Receive with { briefly "i" }"""),
          td.name
        )
        errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet) mustBe empty
    }

    "be an Error when the far inbound adaptor's declared inlet does NOT admit the message" in {
      (td: TestData) =>
        val msgs = diagnostics(
          ar5Model("""      inlet In is command D.Far.Other with { briefly "i" }"""),
          td.name
        )
        errorsOf(msgs, RuleId.AdaptorTargetNoAdmittingInlet).size mustBe 1
    }

    "resolve a `let`-bound operand to its declared type (AR9's repair of AR5 survives)" in {
      (td: TestData) =>
        // The corpus idiom: `let x: type T = prompt(...)` then `tell x to context C`. Before AR9
        // this operand was invisible to AR5, so the far end was never checked.
        val letBound = ar5Model("""      inlet In is command D.Far.Other with { briefly "i" }""")
          .replace(
            "on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }",
            """on s: command Ship is {
              |          let r: type D.Far.Receive = prompt("translate")
              |          tell r to context D.Far
              |        }""".stripMargin
          )
        errorsOf(diagnostics(letBound, td.name), RuleId.AdaptorTargetNoAdmittingInlet).size mustBe 1
    }
  }

  "a connector endpoint that names an adaptor" should {

    "be a wrong-kind reference, since nothing is implied for it to name" in { (td: TestData) =>
      val msgs = diagnostics(
        tellModel(
          """    adaptor ToFar to context D.Far is {
            |      inlet In is command Ship with { briefly "i" }
            |      outlet Out is command D.Far.Receive with { briefly "o" }
            |      handler H is {
            |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin,
          """  connector C is from outlet D.Near.ToFar to inlet D.Far.In with { briefly "c" }"""
        ),
        td.name
      )
      val wrong = errorsOf(msgs, RuleId.WrongKind)
      wrong.size mustBe 1
      wrong.head.message must include("ToFar")
    }

    "validate cleanly when the endpoint names the adaptor's declared PORTLET (negative control)" in {
      (td: TestData) =>
        val msgs = diagnostics(
          tellModel(
            """    adaptor ToFar to context D.Far is {
              |      inlet In is command Ship with { briefly "i" }
              |      outlet Out is command D.Far.Receive with { briefly "o" }
              |      handler H is {
              |        on s: command Ship is { tell command D.Far.Receive(sku = s.sku) to context D.Far }
              |        on other is { error "unexpected" }
              |      } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin,
            """  connector C is from outlet D.Near.ToFar.Out to inlet D.Far.In with { briefly "c" }"""
          ),
          td.name
        )
        errorsOf(msgs, RuleId.WrongKind) mustBe empty
        errorsOf(msgs, RuleId.TellTargetUnreachable) mustBe empty
    }
  }
}
