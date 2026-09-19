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

/** B1 (riddl-generator's task of 2026-09-17; Reid, 2026-09-18: *"Keep A57; m resolves envelope
  * fields first, then union-common message fields"*).
  *
  * Under `on other as m`, `m` is the message's ENVELOPE (A57, unchanged). `m.f` resolves the
  * envelope's field `f` when it has one; otherwise it resolves to the field `f` of the message
  * itself — legal only when EVERY message that can reach the clause carries an `f` of the same
  * type, because `on other` is `case _` and cannot know which one arrived. What can reach the
  * clause is the processor's admitted inlet types minus what sibling `on <message>` clauses take.
  * `kind of m` needs no syntax: it is `m.type`, the envelope's CloudEvents `type` attribute.
  */
class OnOtherUnionFieldTest extends AbstractValidatingTest {

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

  /** Events A, B, C all carry `ticketId`; only A and B carry `table`, C carries `cTable` (a
    * different type when asked for). A is handled by a sibling clause, so the residual set at `on
    * other` is {B, C}. `body` is the `on other` body; `inlet` lets a case drop the inlet.
    */
  private def model(
    body: String,
    envelopeOption: String = """ option message_envelope("Riddl.Envelope")""",
    inlet: String = """      inlet In is type Events with { briefly "i" }""",
    cTable: String = ""
  ): String =
    s"""domain D is {
       |  context Ctx is {
       |    event A is { ticketId: String, table: String } with { briefly "a" }
       |    event B is { ticketId: String, table: String } with { briefly "b" }
       |    event C is { ticketId: String$cTable } with { briefly "c" }
       |    type Events is one of { event A or event B or event C } with { briefly "u" }
       |    command LogIt is { id: String, kind: String } with { briefly "l" }
       |    streamlet Log as flow is {
       |$inlet
       |      outlet Out is command LogIt with { briefly "o" }
       |      handler H is {
       |        on event A is { do "handled here, never reaches on other" }
       |        on other as m is { $body }
       |      } with { briefly "h" }
       |    } with { briefly "s" }
       |  } with { briefly "ctx"$envelopeOption }
       |} with { briefly "d" }
       |""".stripMargin

  private val common = "send command LogIt(id = m.ticketId, kind = m.type) to outlet Log.Out"

  "m.<field> under `on other as m`" should {

    "resolve a field COMMON to every message that can reach the clause, with zero errors" in {
      (td: TestData) =>
        val msgs = diagnostics(model(common), td.name)
        of(msgs, RuleId.OnOtherFieldNotCommon) mustBe empty
        of(msgs, RuleId.ValueRefUnresolved) mustBe empty
        withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "resolve the ENVELOPE's own field first -- `m.type` IS `kind of m`, no syntax needed" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model("send command LogIt(id = m.source, kind = m.type) to outlet Log.Out"),
          td.name
        )
        of(msgs, RuleId.OnOtherFieldNotCommon) mustBe empty
        of(msgs, RuleId.ValueRefUnresolved) mustBe empty
    }

    "be an Error naming the members that LACK the field -- and not the sibling-handled one" in {
      (td: TestData) =>
        val msgs = diagnostics(
          model("send command LogIt(id = m.table, kind = m.type) to outlet Log.Out"),
          td.name
        )
        val found = of(msgs, RuleId.OnOtherFieldNotCommon)
        found.size mustBe 1
        found.head.kind mustBe Error
        found.head.message must include("'C' lack it")
        found.head.message must not include "'A'"
        // One defect, one message: the reference is resolved to a carrying member's field so the
        // generic unresolved-value text does not pile on.
        of(msgs, RuleId.ValueRefUnresolved) mustBe empty
    }

    "be an Error when the members carry the field as DIFFERENT types" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          "send command LogIt(id = m.table, kind = m.type) to outlet Log.Out",
          cTable = ", table: Integer"
        ),
        td.name
      )
      val found = of(msgs, RuleId.OnOtherFieldNotCommon)
      found.size mustBe 1
      found.head.message must include("different type")
      found.head.message must include("'C'")
    }

    "fall back to the old behaviour when the processor declares NO inlet (nothing can reach it)" in {
      (td: TestData) =>
        val msgs = diagnostics(model(common, inlet = ""), td.name)
        of(msgs, RuleId.OnOtherFieldNotCommon) mustBe empty
        of(msgs, RuleId.ValueRefUnresolved).size mustBe 1
    }

    "leave A57 untouched: no `option message_envelope` in scope is still the unbound Error" in {
      (td: TestData) =>
        val msgs = diagnostics(model(common, envelopeOption = ""), td.name)
        of(msgs, RuleId.OnOtherUnbound).size mustBe 1
        of(msgs, RuleId.OnOtherFieldNotCommon) mustBe empty
    }
  }
}
