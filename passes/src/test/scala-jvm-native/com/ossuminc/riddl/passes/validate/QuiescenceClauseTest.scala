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

/** `on quiescence <window>` — the clause that fires when NOTHING arrived at a processor instance
  * within the window (Reid's rulings, 2026-09-07; CM §18).
  *
  *   - One clause kind, instance-scoped: the clock restarts on every handled message. Inside a
  *     State's handler it is armed only while that state is active.
  *   - Allowed on any handler-bearing processor; not inside a Correlation, which has its own
  *     `times out after`.
  *   - The window is a literal duration string (validated like the correlation timeout) OR a
  *     `Duration`-typed value: a constant or a state/message field. A `let` cannot be named from a
  *     clause header, because lets are clause-local statements.
  *   - It is an EFFECT block: yield/tell/send/terminate/morph/initiate are legal. Event-sourcing's
  *     R3/R4 are unchanged, so in an event-sourced entity state changes only through a yielded
  *     event — which is also what keeps replay from re-firing the timer.
  *
  * Every positive case has a negative control in the same family.
  */
class QuiescenceClauseTest extends AbstractValidatingTest {

  private def entityModel(clauses: String, extra: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    command Touch is { id: String } with { briefly "c" }
       |    event Expired is { id: String } with { briefly "e" }
       |    constant Grace: Duration = "30 minutes" with { briefly "k" }
       |    constant NotADuration: Integer = 5 with { briefly "k2" }
       |$extra
       |    entity Cart is {
       |      record Fields is { id: String, ttl: Duration } with { briefly "f" }
       |      state Open of record Cart.Fields
       |      handler H is {
       |        on command Touch is { do "touch it" }
       |$clauses
       |      } with { briefly "h" }
       |    } with { briefly "cart" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    captured

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  "the window" should {

    "accept a precise literal duration" in { (td: TestData) =>
      val msgs = diagnostics(
        entityModel("""        on quiescence "30 minutes" is { yield event Expired(id = "x") }"""),
        "q-literal-ok"
      )
      errorsOf(msgs, RuleId.VagueDuration) mustBe empty
      errorsOf(msgs, RuleId.NonPositiveDuration) mustBe empty
      msgs.justErrors.map(_.format) mustBe empty
    }

    "reject a vague literal duration, exactly as the correlation timeout does" in { (td: TestData) =>
      val msgs = diagnostics(
        entityModel("""        on quiescence "banana" is { yield event Expired(id = "x") }"""),
        "q-literal-vague"
      )
      errorsOf(msgs, RuleId.VagueDuration) must not be empty
    }

    "reject a non-positive literal duration" in { (td: TestData) =>
      val msgs = diagnostics(
        entityModel("""        on quiescence "0s" is { yield event Expired(id = "x") }"""),
        "q-literal-zero"
      )
      errorsOf(msgs, RuleId.NonPositiveDuration) must not be empty
    }

    "accept a Duration-typed CONSTANT" in { (td: TestData) =>
      val msgs = diagnostics(
        entityModel("""        on quiescence Grace is { yield event Expired(id = "x") }"""),
        "q-constant-ok"
      )
      errorsOf(msgs, RuleId.QuiescenceWindowNotDuration) mustBe empty
      msgs.justErrors.map(_.format) mustBe empty
    }

    "accept a Duration-typed STATE FIELD" in { (td: TestData) =>
      val msgs = diagnostics(
        entityModel("""        on quiescence Cart.Fields.ttl is { yield event Expired(id = "x") }"""),
        "q-field-ok"
      )
      errorsOf(msgs, RuleId.QuiescenceWindowNotDuration) mustBe empty
    }

    "reject a value whose type is not Duration" in { (td: TestData) =>
      val msgs = diagnostics(
        entityModel("""        on quiescence NotADuration is { yield event Expired(id = "x") }"""),
        "q-constant-wrong-type"
      )
      val found = errorsOf(msgs, RuleId.QuiescenceWindowNotDuration)
      found must not be empty
      found.head.message must include("Duration")
    }
  }

  "the clause" should {

    "be at most one per handler" in { (td: TestData) =>
      val msgs = diagnostics(
        entityModel(
          """        on quiescence "30 minutes" is { yield event Expired(id = "x") }
            |        on quiescence "1 hour" is { yield event Expired(id = "y") }""".stripMargin
        ),
        "q-duplicate"
      )
      errorsOf(msgs, RuleId.QuiescenceDuplicate) must not be empty
    }

    "be an effect block: terminate is legal here, unlike activate/passivate" in { (td: TestData) =>
      val msgs = diagnostics(
        entityModel("""        on quiescence "30 minutes" is { terminate self.id }"""),
        "q-terminate-ok"
      )
      errorsOf(msgs, RuleId.EffectNotAllowed) mustBe empty
    }

    "keep event-sourcing's R3: in an event-sourced entity, `set` in the clause is still an Error" in {
      (td: TestData) =>
        val src = entityModel(
          """        on quiescence "30 minutes" is { set field Cart.Fields.id to "expired" }"""
        ).replace("    entity Cart is {", "    event-sourced entity Cart is {")
          .replace(
            """        on command Touch is { do "touch it" }""",
            """        on init is { yield event Expired(id = "new") }
              |        on event Expired is { set field Cart.Fields.id to "expired" }""".stripMargin
          )
        val msgs = diagnostics(src, "q-event-sourced-set")
        msgs.justErrors.exists(_.message.contains("something other than one of its own events")) mustBe true
    }

    "keep event-sourcing's idiom: yielding the entity's own event from the clause is clean" in {
      (td: TestData) =>
        val src = entityModel(
          """        on quiescence "30 minutes" is { yield event Expired(id = "expired") }"""
        ).replace("    entity Cart is {", "    event-sourced entity Cart is {")
          .replace(
            """        on command Touch is { do "touch it" }""",
            """        on init is { yield event Expired(id = "new") }
              |        on event Expired is { set field Cart.Fields.id to "expired" }""".stripMargin
          )
        val msgs = diagnostics(src, "q-event-sourced-yield")
        msgs.justErrors.exists(_.message.contains("something other than one of its own events")) mustBe false
    }

    "be legal on a context, an adaptor, a repository and a streamlet" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    event Tick is { n: Integer } with { briefly "e" }
          |    inlet In is event Tick with { briefly "i" }
          |    handler CH is {
          |      on event Tick is { do "tick" }
          |      on quiescence "5 minutes" is { do "nothing arrived at C" }
          |    } with { briefly "h" }
          |    repository R is {
          |      handler RH is { on quiescence "1 hour" is { do "compact" } } with { briefly "h" }
          |    } with { briefly "r" }
          |    streamlet S as sink is {
          |      inlet SIn is event Tick with { briefly "i" }
          |      handler SH is {
          |        on event Tick is { do "consume" }
          |        on quiescence "10 minutes" is { do "flush" }
          |      } with { briefly "h" }
          |    } with { briefly "s" }
          |    adaptor A from context D.Other is {
          |      handler AH is {
          |        on quiescence "2 hours" is { do "far side went quiet" }
          |        on other is { error "unexpected" }
          |      } with { briefly "h" }
          |    } with { briefly "a" }
          |  } with { briefly "c" }
          |  context Other is {
          |    event Ping is { n: Integer } with { briefly "e" }
          |  } with { briefly "o" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, "q-any-processor")
      msgs.justErrors.filter(_.message.contains("quiescence")) mustBe empty
    }

    "be an Error inside a correlation, which has its own `times out after`" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    event A is { id: String } with { briefly "a" }
          |    event B is { id: String } with { briefly "b" }
          |    command Done is { id: String } with { briefly "d" }
          |    projector P is {
          |      record Row is { id: String } with { briefly "r" }
          |      correlation Join by id yields command Done is {
          |        handler Folds is {
          |          on event A is { set field Done.id to "a" }
          |          on event B is { set field Done.id to "b" }
          |          on quiescence "1 hour" is { do "nothing" }
          |        }
          |      } times out after "30 days" { do "give up" }
          |    } with { briefly "p" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, "q-in-correlation")
      errorsOf(msgs, RuleId.QuiescenceInCorrelation) must not be empty
    }
  }
}
