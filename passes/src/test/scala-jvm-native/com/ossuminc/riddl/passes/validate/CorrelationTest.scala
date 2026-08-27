/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A70 — correlations in projectors.
  *
  * A projection frequently must join facts arriving from different entities at different times, and
  * a Projector otherwise has nowhere to hold the partial join while it waits. The semantics are
  * specified in `RIDDL-Computational-Model.md` §6.2 and §6.5–§6.8, which is the authority for any
  * lowering decision; these cases pin only what riddlc must REPORT.
  *
  * The check that earns the feature is the first one below: every required non-key field of the
  * yielded record is set by some fold. It turns "this correlation can never complete" from a
  * production mystery into a compile-time fact, exactly as the event-sourcing rules did for
  * entities.
  */
class CorrelationTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsFor(src: String, origin: String): String =
    diagnostics(src, origin).justErrors.map(_.message).mkString("\n")

  /** One model, parameterised on the yielded command's fields and the folds that fill them.
    *
    * The events carry `customerId`/`orderId` so the key-reachability rule is satisfied by default;
    * the case that tests that rule overrides the event declarations.
    *
    * `repository Store is { ??? }` keeps the repository EXEMPT from the "has no handler for the
    * yielded command" completeness warning, so the cases below see only the rule each is about. The
    * two cases that exercise that warning declare a repository with a real body instead.
    */
  private def model(
    fields: String = "customerId: String, orderId: String, paidAmount: Number",
    folds: String = "on e: event PaymentTaken is { set field paidAmount to e.amount }",
    keys: String = "customerId, orderId",
    timeout: String = "30 days",
    eventFields: String = "customerId: String, orderId: String, amount: Number, confirmed: Boolean"
  ): String =
    s"""domain D is {
       |  context C is {
       |    command RecordFulfillment is { $fields } with { briefly "the joined write" }
       |    event PaymentTaken is { $eventFields } with { briefly "payment" }
       |    command ReportStalled is { why: String } with { briefly "alert" }
       |    entity Monitor is {
       |      handler M is { on command ReportStalled is { do "record it" } }
       |    } with { briefly "monitor" }
       |    repository Store is { ??? } with { briefly "store" }
       |    projector FulfillmentView is {
       |      updates repository Store
       |      correlation FulfillmentJoin by $keys yields command RecordFulfillment is {
       |        handler Collect is {
       |          $folds
       |        } with { briefly "folds" }
       |      } times out after "$timeout" {
       |        do "escalate to operations"
       |      } with { briefly "the correlation" }
       |    } with { briefly "the projector" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "Correlation completeness" should {

    "accept a correlation whose folds set every required non-key field" in { (td: TestData) =>
      errorsFor(model(), "correlation-complete") must be("")
    }

    "reject a correlation that can never complete" in { (td: TestData) =>
      // `shippedAt` is required, is not a key, and no fold sets it -- so no arrival of events
      // could ever populate the record. That is the whole point of the check.
      val errors = errorsFor(
        model(fields =
          "customerId: String, orderId: String, paidAmount: Number, shippedAt: String"
        ),
        "correlation-incomplete"
      )
      errors must include("can never complete")
      errors must include("shippedAt")
    }

    "exempt key fields from the must-be-set rule" in { (td: TestData) =>
      // §6.5 populates key fields implicitly from the correlation key. Demanding a fold set them
      // would reject every correct correlation, so this case would fail if the exemption were lost.
      errorsFor(model(), "correlation-keys-exempt") must not(include("customerId"))
    }

    "reject a `yields` naming something that is not a command" in { (td: TestData) =>
      // Reid, 2026-08-12: a projector's only output is a change to a repository, and a repository
      // is changed by handling a command. The GRAMMAR rejects the wrong keyword -- `yields record
      // R` no longer parses -- but only validation has the resolved referent, so `yields command
      // Foo` where Foo was declared an event has to be caught here. Without this the model would
      // name a target no repository handler could ever accept.
      // Anchored on the leading indentation so it rewrites the DECLARATION only: the `yields`
      // clause names the same type on one line, and an unanchored replace would rewrite that too,
      // turning the case into a parse failure that proves nothing about validation.
      val src = model().replace(
        "    command RecordFulfillment is {",
        "    event RecordFulfillment is {"
      )
      errorsFor(src, "correlation-yields-non-command") must include("must yield a command")
    }

    "not require an optional field to be set by a fold" in { (td: TestData) =>
      // `?` and `*` both admit "nothing there", so neither blocks completion.
      errorsFor(
        model(fields =
          "customerId: String, orderId: String, paidAmount: Number, note: String?, tags: String*"
        ),
        "correlation-optional"
      ) must be("")
    }

    "resolve a bare `set field` against the correlation's yielded record" in { (td: TestData) =>
      // A70 chose the bare form deliberately: the enclosing correlation says which record the name
      // belongs to. If that scoping were missing this would fail to resolve rather than validate.
      errorsFor(
        model(folds = "on e: event PaymentTaken is { set field paidAmount to e.amount }"),
        "correlation-bare-set"
      ) must be("")
    }

    "count a `set` nested inside a when/then block" in { (td: TestData) =>
      // Reachability is all the check asks; it does not try to prove the branch is taken.
      errorsFor(
        model(folds = """on e: event PaymentTaken is {
                        |            when e.confirmed then
                        |              set field paidAmount to e.amount
                        |            end
                        |          }""".stripMargin),
        "correlation-nested-set"
      ) must not(include("can never complete"))
    }
  }

  /** The rules that make a correlation's RESULT well-defined, rather than its completion possible.
    */
  "Correlation soundness" should {

    "reject a vague timeout duration" in { (td: TestData) =>
      // The bound left metadata for the grammar (A70); the duration check must not have been left
      // behind with it, or `times out after "banana"` would compile.
      errorsFor(model(timeout = "banana"), "correlation-vague-timeout") must include(
        "vague duration"
      )
    }

    "reject a non-positive timeout" in { (td: TestData) =>
      errorsFor(model(timeout = "0s"), "correlation-zero-timeout") must include(
        "non-positive duration"
      )
    }

    "reject two clauses writing the same field" in { (td: TestData) =>
      // A race: arrival order across sources is not guaranteed, so the completed record would
      // differ between runs over identical events. §6.6 rejects it rather than describing it.
      val errors = errorsFor(
        model(folds = """on e: event PaymentTaken is { set field paidAmount to e.amount }
                        |          on f: event PaymentTaken is { set field paidAmount to f.amount }
                        |""".stripMargin),
        "correlation-race"
      )
      errors must include("more than one clause")
      errors must include("arrival order")
    }

    "reject a fold that sets nothing" in { (td: TestData) =>
      errorsFor(
        model(folds = """on e: event PaymentTaken is { set field paidAmount to e.amount }
                        |          on f: event PaymentTaken is { do "look busy" }
                        |""".stripMargin),
        "correlation-no-set"
      ) must include("must terminate in a 'set'")
    }

    "reject an effect inside a fold" in { (td: TestData) =>
      // Purity is what makes re-running a fold safe (§6.5), so this is an Error and not a style
      // warning. The same statement is legal in the timeout block, which the next case pins.
      errorsFor(
        model(folds = """on e: event PaymentTaken is {
                        |            set field paidAmount to e.amount
                        |            tell command ReportStalled(why = "it stalled") to entity Monitor
                        |          }""".stripMargin),
        "correlation-effect-in-fold"
      ) must include("may not 'tell'")
    }

    "allow the same effect in the timeout block" in { (td: TestData) =>
      // §6.7: the timeout block EXISTS to have an effect. Banning effects there would leave it
      // unable to do anything, so the ban must bind folds only -- this is the case that proves the
      // previous one is not simply banning `tell` everywhere in a projector.
      val src = model().replace(
        """do "escalate to operations"""",
        """tell command ReportStalled(why = "it stalled") to entity Monitor"""
      )
      errorsFor(src, "correlation-effect-in-timeout") must be("")
    }

    "leave a correlation-free projector validated exactly as before" in { (td: TestData) =>
      // Reid, 2026-08-11: zero correlations is NOT an error. Plenty of projectors just translate
      // events to commands 1-for-1 and have nothing to accumulate. Correlations RELAXED two
      // projector rules; this pins that the relaxation is one-way, so the translator shape below
      // is judged by the pre-existing rules and not by anything A70 added.
      val translator =
        """domain D is {
          |  context C is {
          |    event OrderPlaced is { orderId: String } with { briefly "e" }
          |    command RecordOrder is { orderId: String } with { briefly "cm" }
          |    record View is { orderId: String } with { briefly "r" }
          |    repository Store is {
          |      handler S is { on command RecordOrder is { do "store it" } }
          |    } with { briefly "store" }
          |    projector Translate is {
          |      updates repository Store
          |      record Mirror is { orderId: String } with { briefly "rec" }
          |      handler T is {
          |        on event OrderPlaced is { tell command RecordOrder(orderId = "the order") to repository Store }
          |      } with { briefly "h" }
          |    } with { briefly "p" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsFor(translator, "projector-no-correlation") must be("")
    }

    "accept a translator whose record is only the type it sends to the repository" in {
      (td: TestData) =>
        // Reid's ruling, 2026-08-11: the projector's record is the type it SENDS -- here the
        // command `RecordOrder` going to `repository Store` -- and it does NOT have to be declared
        // inside the projector. This model previously drew "lacks a required Record definition".
        // `RecordOrder` IS defined inside Store, so the placement Warning must stay silent.
        val src =
          """domain D is {
            |  context C is {
            |    event OrderPlaced is { orderId: String } with { briefly "e" }
            |    repository Store is {
            |      command RecordOrder is { orderId: String } with { briefly "cm" }
            |      handler S is { on command RecordOrder is { do "store it" } }
            |    } with { briefly "store" }
            |    projector Translate is {
            |      updates repository Store
            |      handler T is {
            |        on event OrderPlaced is { tell command Store.RecordOrder(orderId = "the order") to repository Store }
            |      } with { briefly "h" }
            |    } with { briefly "p" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = diagnostics(src, "translator-record-in-repo")
        msgs.justErrors.map(_.message).mkString("\n") must be("")
        msgs.map(_.message).mkString("\n") must not(include("is not defined in it"))
    }

    "warn when the type populating the repository is defined elsewhere" in { (td: TestData) =>
      // Same model, but the command lives in the context rather than in the repository. That is
      // legal -- hence a Warning, not an Error -- but the data that populates the database ought
      // to be associated with the repository.
      val src =
        """domain D is {
          |  context C is {
          |    event OrderPlaced is { orderId: String } with { briefly "e" }
          |    command RecordOrder is { orderId: String } with { briefly "cm" }
          |    repository Store is {
          |      handler S is { on command RecordOrder is { do "store it" } }
          |    } with { briefly "store" }
          |    projector Translate is {
          |      updates repository Store
          |      handler T is {
          |        on event OrderPlaced is { tell command RecordOrder(orderId = "the order") to repository Store }
          |      } with { briefly "h" }
          |    } with { briefly "p" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, "translator-record-outside-repo")
      msgs.justErrors.map(_.message).mkString("\n") must be("")
      msgs.map(_.message).mkString("\n") must include("populates")
      msgs.map(_.message).mkString("\n") must include("is not defined in it")
    }

    /** Fix A (2026-08-15): `ValidationPass.validateProjector`'s `sentToRepository` walk used to
      * pattern-match the `tell` operand's syntactic shape and give up on a `ValueRef` — an
      * on-clause binding — with `case _: ValueRef => None // type comes from the clause; not a
      * declaration here`. riddl-models measured the fallout corpus-wide: migrating 10,298
      * forwarding sites from the bare form to the bound form took this check from 863 warnings to
      * 9, with nothing about the models changed (see
      * `task/2026-08-14-valueref-migration-blinds-the-populates-repository-check.md`, now in
      * `task/done/`). The three cases below assert an EXACT count on both spellings of the SAME
      * defect, plus a negative control, because `nonEmpty` cannot see a drop from 2 to 1 -- only a
      * count can.
      */
    def populatesWarnings(msgs: Messages): Seq[String] =
      msgs
        .filter(m =>
          m.kind == Messages.Warning &&
            m.message.contains("populates") &&
            m.message.contains("is not defined in it")
        )
        .map(_.message)

    "warn once when an un-owned event populates a repository via the bare form" in {
      (td: TestData) =>
        // The message is FIELD-LESS on purpose: as of 2026-08-14 a bare (uncnstructed) operand
        // naming a message with fields is itself an Error (Task 4, "message-value-source"), so a
        // genuinely bare `tell event OrderPlaced to ...` is only legal for a type with no fields to
        // source. That is orthogonal to what THIS test pins (the pre-existing, never-blind
        // `MessageRef` arm of the populates-repository check).
        val src =
          """domain D is {
            |  context C is {
            |    event OrderPlaced is { ??? } with { briefly "e" }
            |    repository Store is {
            |      command Noop is { z: String } with { briefly "n" }
            |      handler S is { on command Noop is { do "ignore" } }
            |    } with { briefly "store" }
            |    projector Translate is {
            |      updates repository Store
            |      handler T is {
            |        on event OrderPlaced is { tell event OrderPlaced to repository Store }
            |      } with { briefly "h" }
            |    } with { briefly "p" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = diagnostics(src, "populates-bare-form")
        msgs.justErrors.map(_.message).mkString("\n") must be("")
        populatesWarnings(msgs).size mustBe 1
    }

    "warn once when an un-owned event populates a repository via an on-clause binding" in {
      (td: TestData) =>
        // Identical to the bare-form case immediately above except for HOW the operand is
        // written: `placed` is the binding the on-clause declares, resolved through the same
        // `ValueRef` arm the bug silenced. Before the fix this produced ZERO warnings.
        val src =
          """domain D is {
            |  context C is {
            |    event OrderPlaced is { orderId: String } with { briefly "e" }
            |    repository Store is {
            |      command Noop is { z: String } with { briefly "n" }
            |      handler S is { on command Noop is { do "ignore" } }
            |    } with { briefly "store" }
            |    projector Translate is {
            |      updates repository Store
            |      handler T is {
            |        on placed: event OrderPlaced is { tell placed to repository Store }
            |      } with { briefly "h" }
            |    } with { briefly "p" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = diagnostics(src, "populates-bound-form")
        msgs.justErrors.map(_.message).mkString("\n") must be("")
        populatesWarnings(msgs).size mustBe 1
    }

    "stay silent (negative control) when the bound operand's type IS defined in the repository" in {
      (td: TestData) =>
        // Same bound-operand shape as the case above, but `OrderPlaced` is declared INSIDE
        // `Store` this time, so the check must NOT fire -- proving the fix resolves the operand's
        // real type rather than warning unconditionally whenever it sees a `ValueRef`.
        val src =
          """domain D is {
            |  context C is {
            |    repository Store is {
            |      event OrderPlaced is { orderId: String } with { briefly "e" }
            |      handler S is { on event OrderPlaced is { do "store it" } }
            |    } with { briefly "store" }
            |    projector Translate is {
            |      updates repository Store
            |      handler T is {
            |        on placed: event Store.OrderPlaced is { tell placed to repository Store }
            |      } with { briefly "h" }
            |    } with { briefly "p" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = diagnostics(src, "populates-negative-control")
        msgs.justErrors.map(_.message).mkString("\n") must be("")
        populatesWarnings(msgs).size mustBe 0
    }

    "reject a key component missing from a handled event" in { (td: TestData) =>
      // §6.6 makes the key the distribution key, so an event without it could not be routed to the
      // instance holding that tuple's partial in the first place.
      errorsFor(
        model(eventFields = "customerId: String, amount: Number, confirmed: Boolean"),
        "correlation-key-missing"
      ) must include("every key component must be present on every handled event")
    }
  }

  /** The three rules finished on 2026-08-12: whether the repository actually accepts what the
    * correlation yields, whether anything emits the events it folds, and whether a fold writes a
    * value that a later write certainly discards.
    */
  "Correlation completeness, continued" should {

    /** As [[model]] but with a REAL repository, so the "no handler for the yielded command" rule is
      * in play; `???` would exempt it. `emitter` lets a case declare something that actually
      * produces `PaymentTaken`, which the unemitted-event rule otherwise reports.
      */
    def stored(repoHandles: String, emitter: String = "", folds: String = ""): String =
      s"""domain D is {
         |  context C is {
         |    command RecordFulfillment is {
         |      customerId: String, orderId: String, paidAmount: Number
         |    } with { briefly "the joined write" }
         |    command Unrelated is { why: String } with { briefly "other" }
         |    event PaymentTaken is {
         |      customerId: String, orderId: String, amount: Number, confirmed: Boolean
         |    } with { briefly "payment" }
         |    $emitter
         |    repository Store is {
         |      handler S is { on command $repoHandles is { do "store it" } }
         |    } with { briefly "store" }
         |    projector FulfillmentView is {
         |      updates repository Store
         |      correlation FulfillmentJoin by customerId, orderId
         |        yields command RecordFulfillment is {
         |        handler Collect is {
         |          ${
          if folds.isEmpty then "on e: event PaymentTaken is { set field paidAmount to e.amount }"
          else folds
        }
         |        } with { briefly "folds" }
         |      } times out after "30 days" {
         |        do "escalate to operations"
         |      } with { briefly "the correlation" }
         |    } with { briefly "the projector" }
         |  } with { briefly "c" }
         |} with { briefly "d" }
         |""".stripMargin

    "warn when the repository has no handler for the yielded command" in { (td: TestData) =>
      // Reid, 2026-08-12: a Completeness warning, not an Error -- a repository missing the handler
      // is under-specified, not self-contradictory. This is plain identity because `yields` names
      // a COMMAND; the earlier design had to infer acceptance from a command that "held" a record,
      // since a record is nameable by no `on` clause at all (A9b).
      val msgs = diagnostics(stored("Unrelated"), "correlation-repo-missing-handler")
      msgs.justErrors.map(_.message).mkString("\n") must be("")
      msgs.map(_.message).mkString("\n") must include("has no handler for")
      msgs.map(_.message).mkString("\n") must include("RecordFulfillment")
    }

    "stay silent when the repository handles the yielded command" in { (td: TestData) =>
      diagnostics(stored("RecordFulfillment"), "correlation-repo-handles")
        .map(_.message)
        .mkString("\n") must not(include("has no handler for"))
    }

    "exempt a `???` repository from the handler requirement" in { (td: TestData) =>
      // Reid's standing ruling: `???` says "known to be incomplete", so it earns a Missing warning
      // about its body and nothing else. A check that reasons from what a `???` body does NOT
      // contain fires on nearly every stub in the corpus.
      diagnostics(model(), "correlation-repo-unwritten")
        .map(_.message)
        .mkString("\n") must not(include("has no handler for"))
    }

    // A70's "handled events that nothing emits" rule is satisfied by the MODEL-WIDE check #17
    // (`… is defined but nothing in the model emits it`), not by a correlation-scoped one. A twin
    // scoped to folds shipped for one day and was deleted: it reported the same defect as #17 in
    // different words, so a correlation folding an unemitted event drew two messages for one fact.
    // These two cases stay because a correlation is still the sharpest way to exercise the rule --
    // they now assert the surviving message.
    "warn when a folded event is emitted by nothing in the model" in { (td: TestData) =>
      // The mirror image of "can never complete": a fold that can never RUN. Nothing in `stored`
      // sends, tells or yields `PaymentTaken`, and no outlet carries it.
      val msgs = diagnostics(stored("RecordFulfillment"), "correlation-event-unemitted")
      msgs.justErrors.map(_.message).mkString("\n") must be("")
      msgs.map(_.message).mkString("\n") must include("nothing in the model emits it")
    }

    "count an outlet declaration as emitting the event" in { (td: TestData) =>
      // A source whose body is `???` but which declares `outlet o is event PaymentTaken` has SAID
      // it produces the event. Warning there would be reasoning from an unwritten body.
      val src = stored(
        "RecordFulfillment",
        emitter = """source Feed is {
                    |      outlet o is event PaymentTaken
                    |    } with { briefly "feed" }""".stripMargin
      )
      diagnostics(src, "correlation-event-from-outlet")
        .map(_.message)
        .mkString("\n") must not(include("nothing in the model emits it"))
    }

    "count a `yield` as emitting the event, which the old check did not" in { (td: TestData) =>
      // The defect that made rewriting #17 worth doing rather than merely de-duplicating: it
      // counted only `send` and `tell`, so `yield event X` -- the canonical spelling in an
      // event-sourced entity -- was reported as produced by nothing.
      val src = stored(
        "RecordFulfillment",
        emitter = """command Pay is { customerId: String, orderId: String, amount: Number }
                    |    entity Payer is {
                    |      record PS is { total: Number }
                    |      state St of record PS is {
                    |        handler H is { on command Pay is { yield event PaymentTaken } }
                    |      }
                    |    } with { briefly "payer" }""".stripMargin
      )
      diagnostics(src, "correlation-event-from-yield")
        .map(_.message)
        .mkString("\n") must not(include("nothing in the model emits it"))
    }

    "warn about a `set` that a later `set` overrides on every path" in { (td: TestData) =>
      // Dead work: the first value can never reach the yielded command. Within ONE fold, unlike
      // the cross-clause case, statement order IS guaranteed -- which is why this is a Warning
      // about waste rather than the Error about a race.
      val msgs = diagnostics(
        stored(
          "RecordFulfillment",
          folds = """on e: event PaymentTaken is {
                    |            set field paidAmount to e.amount
                    |            set field paidAmount to e.amount
                    |          }""".stripMargin
        ),
        "correlation-set-overridden"
      )
      msgs.justErrors.map(_.message).mkString("\n") must be("")
      msgs.map(_.message).mkString("\n") must include("set again on every path")
    }

    "stay silent when the later `set` is only conditional" in { (td: TestData) =>
      // A `when` with no `else` is an escape path, so the first write may well survive. Reporting a
      // merely POSSIBLE override is the noise this rule exists to avoid, and `dischargesOnEveryPath`
      // is what draws the line.
      val msgs = diagnostics(
        stored(
          "RecordFulfillment",
          folds = """on e: event PaymentTaken is {
                    |            set field paidAmount to e.amount
                    |            when e.confirmed then
                    |              set field paidAmount to e.amount
                    |            end
                    |          }""".stripMargin
        ),
        "correlation-set-conditional"
      )
      msgs.map(_.message).mkString("\n") must not(include("set again on every path"))
    }
  }
}
