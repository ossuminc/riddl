/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** No diagnostic the validator produces may lack a rule id.
  *
  * **This is the test that would have caught the gap, and it did not exist.** The ids were threaded
  * through `Accumulator.add*` and the work reported as complete, while `BasicValidation.check` --
  * a SECOND chokepoint that builds a `Message` directly -- kept emitting `"rule": null` from 68
  * sites, as did four `Messages.warning/error/missing` factory calls. Auditing one path proved
  * nothing about the others; riddl-examples found it from the OUTPUT, by seeing a null in
  * `validate --json`.
  *
  * So this asserts the property over OUTPUT rather than over call sites. A census of call sites is
  * exactly what missed it, twice: the grep that finds `addError(` does not find `check(`, and the
  * grep that finds both does not find `messages.add(warning(...))`.
  *
  * `Option[RuleId]` still makes `None` expressible -- riddl-examples' task points out that only a
  * non-optional type is a guarantee a compiler can enforce. Until that change, this test is the
  * guard, so keep the model below TRIPPING A LOT: its value is proportional to how many distinct
  * checks it reaches.
  */
class EveryDiagnosticHasARuleIdTest extends AbstractValidatingTest {

  /** Deliberately messy: missing metadata, a cross-context reference, an unhandled message, ports
    * that do not match the ascribed shape. Contributed by riddl-examples as their repro.
    */
  private val manyDiagnostics: String =
    """domain D is {
      |  context Owner is {
      |    command Poke is { thingId: Id(Thing) }
      |    event Poked is { thingId: Id(Thing) }
      |    entity Thing as flow is {
      |      inlet In is command D.Owner.Poke
      |      outlet Out is event D.Owner.Poked
      |      record Fields is { thingId: Id(Thing) }
      |      initial state Only of record D.Owner.Thing.Fields
      |      handler H is {
      |        on command D.Owner.Poke { yield event D.Owner.Poked(thingId = prompt("the id")) }
      |        on init { do "initialize" }
      |      }
      |    }
      |  }
      |  context Peer is {
      |    handler Reach is {
      |      on poke: command D.Owner.Poke {
      |        tell poke to entity D.Owner.Thing
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "every diagnostic" should {

    "carry a rule id" in { (td: TestData) =>
      val rpi = RiddlParserInput(manyDiagnostics, "many-diagnostics")
      parseAndValidateAggregate(rpi) { result =>
        val msgs = result.messages
        // Guard the GUARD: a model that stopped tripping checks would make this vacuous, which is
        // the shape that let `0 mustBe 0` pass for months elsewhere in this repo.
        withClue("the fixture must keep tripping many checks, or this test proves nothing: ") {
          msgs.size must be >= 20
        }
        val unruled = msgs.filter(_.ruleId.isEmpty).map(m => s"${m.kind}: ${m.message.take(80)}")
        withClue(s"${unruled.size} diagnostic(s) have no rule id:\n  ${unruled.mkString("\n  ")}\n") {
          unruled mustBe empty
        }
      }
    }
  }
}
