/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A message-send from OUTSIDE context `C` must address `C` itself, never something `C` contains.
  *
  * Reid's ruling, 2026-08-26: a domain-scope saga sending to various contexts "is typical and
  * usual", but reaching past a context into its entities or streamlets or repositories is an
  * encapsulation violation — it claims the sender may comprehend a context's possibly-changing
  * internal design and so work around the API whose sole purpose is to define a boundary.
  *
  * **Every positive case here is paired with a control that must stay SILENT.** The rule's whole
  * risk is firing on the legal forms: addressing the context itself is the INTENDED spelling, and
  * an intra-context send to a contained entity is what essentially every model already does. A
  * check that flagged either would demand something no legal spelling satisfies — the trap the
  * discard-sink exemption and the adaptor advisory were each built to escape.
  */
class TargetBoundaryTest extends AbstractValidatingTest {

  /** `provideTips` is REQUIRED to see a suggestion at all: `Messages.Accumulator.add` is a single
    * chokepoint that STRIPS `suggestion` unless it is set, so a test asserting one against default
    * options compares against the empty string and fails for a reason unrelated to its subject.
    */
  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, provideTips = true)
    ) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def boundaryErrors(src: String, origin: String): Messages =
    diagnostics(src, origin).filter { m =>
      m.isError && m.message.contains("from outside that context")
    }

  /** One context holding an entity, plus a sender placed by `senderScope`.
    *
    * `target` is written verbatim, so a case chooses precisely what it addresses.
    */
  private def model(senderInsideContext: Boolean, target: String, keyword: String = "tell"): String =
    val step =
      s"""    step StepAdvance is {
         |      do "advance the run"
         |      let advance: type Racing.Running.Advance = prompt("the advance command")
         |      $keyword advance to $target
         |    } reverted by { do "undo" }
         |    step StepFinish is {
         |      do "finish"
         |    } reverted by { do "unfinish" }""".stripMargin
    val saga = s"  saga RaceSaga is {\n$step\n  }"
    val entity =
      """    record Snapshot is { steps: Integer }
        |    command Advance is { runId: String }
        |    entity Runner is {
        |      inlet arrivals is command Advance
        |      state Progress of record Racing.Running.Snapshot is {
        |        handler Handle is {
        |          on command Advance is { do "advance" }
        |        }
        |      }
        |    }""".stripMargin
    if senderInsideContext then
      s"domain Racing is {\n  context Running is {\n$entity\n$saga\n  }\n}"
    else
      s"domain Racing is {\n  context Running is {\n$entity\n  }\n$saga\n}"
  end model

  "TargetBoundary" should {
    "reject a domain-scope saga telling an entity inside a context" in { (td: TestData) =>
      val errors = boundaryErrors(model(senderInsideContext = false, "entity Racing.Running.Runner"), td.name)
      errors.size mustBe 1
      errors.head.message must include("Entity 'Runner'")
      errors.head.message must include("Context 'Running'")
      errors.head.ruleId.map(_.code) mustBe Some("msg-target-crosses-boundary")
    }

    "name the fix, and name the context the way the author spelled it" in { (td: TestData) =>
      val errors = boundaryErrors(model(senderInsideContext = false, "entity Racing.Running.Runner"), td.name)
      errors.head.suggestion must include("to context Racing.Running")
    }

    // The control that matters most: this is the spelling the ruling ASKS FOR.
    "accept a domain-scope saga telling the context itself" in { (td: TestData) =>
      boundaryErrors(model(senderInsideContext = false, "context Racing.Running"), td.name) mustBe empty
    }

    // The second control: essentially every existing model does this.
    "accept an intra-context saga telling a contained entity" in { (td: TestData) =>
      boundaryErrors(model(senderInsideContext = true, "entity Racing.Running.Runner"), td.name) mustBe empty
    }

    "reject a cross-context `forward` reaching into another context's entity" in { (td: TestData) =>
      // `forward` was not in the filed ask; it is here because it carries the same target shapes,
      // and leaving it out would make the one statement that DELEGATES free to ignore the boundary.
      val src =
        """domain Racing is {
          |  context Running is {
          |    command Advance is { runId: String }
          |    event Advanced is { runId: String }
          |    record Snapshot is { steps: Integer }
          |    entity Runner is {
          |      inlet arrivals is command Advance
          |      state Progress of record Racing.Running.Snapshot is {
          |        handler Handle is {
          |          on command Advance is { do "advance" }
          |        }
          |      }
          |    }
          |  }
          |  context Other is {
          |    command Kickoff yields event Racing.Running.Advanced is { runId: String }
          |    entity Starter is {
          |      inlet requests is command Kickoff
          |      state S of record Racing.Running.Snapshot is {
          |        handler H is {
          |          on command Kickoff is { forward command Racing.Other.Kickoff to entity Racing.Running.Runner }
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin
      val errors = boundaryErrors(src, td.name)
      errors.size mustBe 1
      errors.head.message must startWith("'forward' addresses")
    }
  }

  /** The `checkCrossContextReference` half — see the commit message and NOTEBOOK.
    *
    * Its container-has-no-context arm did NOTHING, and that silence is the mechanical reason a
    * domain-scope saga could reach into a context's entity without a word while the identical
    * reference from a SIBLING context warned. That is now fixed at the site that mattered, by
    * `checkTargetBoundary`, at Error severity rather than Warning.
    *
    * There is deliberately NO case here asserting the `None` arm warns on its own. After the
    * transmission targets were routed to `checkTargetBoundary` — they must be, or a cross-context
    * tell is double-reported as one warning and one error — that arm has no caller which can reach
    * it with a contained processor: `become` is the only other processor-valued reference and is
    * not legal outside an entity handler (verified: it does not parse in a saga step), while a
    * message TYPE is a context's published surface and must stay silent. A test asserting an
    * unreachable arm would pass against any implementation, including none.
    */
  "checkCrossContextReference, from outside every context" should {
    "stay silent about a message type published by the context" in { (td: TestData) =>
      // `let advance: type Racing.Running.Advance` names a command across the boundary in EVERY
      // case above, including the two that must be clean — so if this warned, those would fail too.
      val text = diagnostics(model(senderInsideContext = false, "context Racing.Running"), td.name)
        .map(_.message)
        .mkString("\n")
      text must not include ("is outside every context")
    }
  }
}
