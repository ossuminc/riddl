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

/** A processor whose ports are NEEDED and not DECLARED is incomplete (Reid, 2026-09-10/11; [1.25]).
  *
  * A103 had given an Adaptor IMPLIED ports; that is abolished. Nothing is implied for any processor
  * kind: a port is declared, or it is absent. A side that the processor's own handlers need -- an
  * inlet when it handles messages, an outlet when it transmits them -- and that it does not declare
  * is reported as a **Missing** warning, `???`'s kind, because it is the same fact: the author has
  * not written something the definition owes. *"Missing is missing, that's incomplete."* Not a
  * Deprecation (the old spelling is not a spelling, it is an omission) and not Completeness (the
  * written things are not failing to connect; a thing is unwritten).
  *
  * The message names the types from the handlers, so the author knows what to declare. And every
  * rule that would read the missing side ABSTAINS (`PortAbstentionTest`), exactly as rules abstain
  * from a `???` body: *"further analysis before the portlets have connectors attached isn't
  * worthwhile since you can't say anything about the constructed graph."*
  *
  * One rule, two spellings, for a recorded reason: `entity-no-inlet`/`entity-no-outlet` shipped
  * first and are published codes a consumer keys on (synapify's `EmitterConformanceTest`), and a
  * published code means the same thing forever. Every other kind emits
  * `stream-processor-no-inlet`/`stream-processor-no-outlet`.
  */
class ProcessorPortsIncompleteTest extends AbstractValidatingTest {

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

  private def missingOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.MissingWarning && m.ruleId.contains(rule))

  private def anyOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(_.ruleId.contains(rule))

  /** Two contexts so an adaptor has something to be `to`. `near` is the body of context Near. */
  private def model(near: String, far: String = ""): String =
    s"""domain D is {
       |  context Far is {
       |    command Receive is { sku: String } with { briefly "r" }
       |    event Received is { sku: String } with { briefly "e" }
       |    inlet In is command Receive with { briefly "i" }
       |    handler H is { on command Receive is { do "x" } } with { briefly "h" }
       |$far
       |  } with { briefly "far" }
       |  context Near is {
       |    command Ship is { sku: String } with { briefly "s" }
       |    command Cancel is { sku: String } with { briefly "c" }
       |    event Shipped is { sku: String } with { briefly "e" }
       |$near
       |  } with { briefly "near" }
       |} with { briefly "d" }
       |""".stripMargin

  "a processor that handles messages but declares no inlet" should {

    "be a Missing warning on an ADAPTOR, naming every handled type" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    adaptor ToFar to context D.Far is {
            |      handler H is {
            |        on command Ship is { do "translate" }
            |        on command Cancel is { do "translate" }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin
        ),
        td.name
      )
      val found = missingOf(msgs, RuleId.StreamProcessorNoInlet)
      found.size mustBe 1
      found.head.message must include("Adaptor 'ToFar'")
      found.head.message must include("declares no inlet")
      found.head.message must include("Ship")
      found.head.message must include("Cancel")
      // Not the entity spelling, and not Completeness.
      anyOf(msgs, RuleId.EntityNoInlet) mustBe empty
      msgs.filter(_.ruleId.contains(RuleId.StreamProcessorNoInlet)).forall(_.kind == MissingWarning) mustBe true
    }

    "be `entity-no-inlet` on an ENTITY, folding STATE handlers in, and Missing not Completeness" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """    entity E is {
              |      record Fields is { sku: String } with { briefly "f" }
              |      state Main of record E.Fields is {
              |        handler H is {
              |          on command Ship is { do "handle" }
              |          on other is { error "unexpected" }
              |        } with { briefly "h" }
              |      } with { briefly "s" }
              |    } with { briefly "e" }""".stripMargin
          ),
          td.name
        )
        val found = anyOf(msgs, RuleId.EntityNoInlet)
        found.size mustBe 1
        found.head.kind mustBe MissingWarning
        found.head.message must include("Ship")
        anyOf(msgs, RuleId.StreamProcessorNoInlet) mustBe empty
    }

    "be reported on a CONTEXT, a REPOSITORY, a PROJECTOR and a STREAMLET alike" in { (td: TestData) =>
      val bodies = Seq(
        "Context" ->
          """    handler H is { on command Ship is { do "x" } } with { briefly "h" }""",
        "Repository" ->
          """    repository R is {
            |      handler H is { on command Ship is { do "x" } } with { briefly "h" }
            |    } with { briefly "r" }""".stripMargin,
        "Projector" ->
          """    projector P is {
            |      record View is { sku: String } with { briefly "v" }
            |      handler H is { on event Shipped is { do "x" } } with { briefly "h" }
            |    } with { briefly "p" }""".stripMargin,
        "Streamlet" ->
          """    streamlet S as flow is {
            |      outlet Out is command Ship with { briefly "o" }
            |      handler H is { on command Ship is { do "x" } } with { briefly "h" }
            |    } with { briefly "s" }""".stripMargin
      )
      bodies.foreach { case (kind, body) =>
        val found = missingOf(diagnostics(model(body), s"${td.name}-$kind"), RuleId.StreamProcessorNoInlet)
        withClue(s"$kind:") {
          found.size mustBe 1
          found.head.message must include("Ship")
        }
      }
    }

    "NOT fire when the only clause is `on other { error }` -- a refusal needs no inlet" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """    adaptor ToFar to context D.Far is {
              |      handler H is { on other is { error "unexpected" } } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin
          ),
          td.name
        )
        anyOf(msgs, RuleId.StreamProcessorNoInlet) mustBe empty
        anyOf(msgs, RuleId.StreamProcessorNoOutlet) mustBe empty
    }

    "fire when `on other` DOES something, since that receives everything" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    adaptor ToFar to context D.Far is {
            |      handler H is { on other is { do "log whatever arrives" } } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin
        ),
        td.name
      )
      missingOf(msgs, RuleId.StreamProcessorNoInlet).size mustBe 1
    }

    "NOT fire for a `???` body -- the author already said so" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""    adaptor ToFar to context D.Far is { ??? } with { briefly "a" }"""),
        td.name
      )
      anyOf(msgs, RuleId.StreamProcessorNoInlet) mustBe empty
    }

    "still fire when the only inlet is an `error-sink` -- infrastructure is not dataflow" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model(
            """    adaptor ToFar to context D.Far is {
              |      inlet Errs is record Riddl.GeneratorError with { option error-sink }
              |      handler H is { on command Ship is { do "translate" } } with { briefly "h" }
              |    } with { briefly "a" }""".stripMargin
          ),
          td.name
        )
        missingOf(msgs, RuleId.StreamProcessorNoInlet).size mustBe 1
    }

    "NOT fire once an inlet is declared (negative control)" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    adaptor ToFar to context D.Far is {
            |      inlet In is command Ship with { briefly "i" }
            |      handler H is { on command Ship is { do "translate" } } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin
        ),
        td.name
      )
      anyOf(msgs, RuleId.StreamProcessorNoInlet) mustBe empty
    }
  }

  "a processor that transmits messages but declares no outlet" should {

    "be a Missing warning on an ADAPTOR, naming the transmitted type" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
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
      val found = missingOf(msgs, RuleId.StreamProcessorNoOutlet)
      found.size mustBe 1
      found.head.message must include("Adaptor 'ToFar'")
      found.head.message must include("declares no outlet")
      found.head.message must include("Receive")
      anyOf(msgs, RuleId.EntityNoOutlet) mustBe empty
    }

    "be `entity-no-outlet` on an ENTITY that yields, Missing not Completeness" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    entity E is {
            |      inlet In is command Ship with { briefly "i" }
            |      record Fields is { sku: String } with { briefly "f" }
            |      state Main of record E.Fields is {
            |        handler H is {
            |          on s: command Ship is { yield event Shipped(sku = s.sku) }
            |          on other is { error "unexpected" }
            |        } with { briefly "h" }
            |      } with { briefly "s" }
            |    } with { briefly "e" }""".stripMargin
        ),
        td.name
      )
      val found = anyOf(msgs, RuleId.EntityNoOutlet)
      found.size mustBe 1
      found.head.kind mustBe MissingWarning
      found.head.message must include("Shipped") // a yielded type is NAMED, like a told one
      anyOf(msgs, RuleId.StreamProcessorNoOutlet) mustBe empty
    }

    "count `forward` as transmission" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          """    adaptor ToFar to context D.Far is {
            |      inlet In is command D.Far.Receive with { briefly "i" }
            |      handler H is {
            |        on r: command D.Far.Receive is { forward r to context D.Far }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "a" }""".stripMargin
        ),
        td.name
      )
      missingOf(msgs, RuleId.StreamProcessorNoOutlet).size mustBe 1
    }

    "NOT fire once an outlet is declared (negative control)" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
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
      anyOf(msgs, RuleId.StreamProcessorNoOutlet) mustBe empty
    }
  }
}
