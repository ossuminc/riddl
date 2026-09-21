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

/** B7 (2026-09-21): `log` is deterministic, not state, not a message, legal everywhere. Each
  * of those is a rule that must NOT fire, plus the one that must: an operand that resolves
  * nowhere is `value-ref-unresolved`.
  */
class LogStatementValidationTest extends AbstractValidatingTest {

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

  /** An event-sourced entity so R3 (`on init` may not `set`) is live; `initBody` and `cmdBody`
    * are the two clause bodies.
    */
  private def model(initBody: String, cmdBody: String, extra: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    command Open yields event Acct.Opened is { id: String, n: Natural } with { briefly "c" }
       |    event-sourced entity Acct is {
       |      record Fields is { id: String, n: Natural } with { briefly "f" }
       |      event Opened is { id: String, n: Natural } with { briefly "e" }
       |      state Main of record Acct.Fields
       |      inlet In is command Open with { briefly "i" }
       |      outlet Out is event Acct.Opened with { briefly "o" }
       |      handler H is {
       |        on init is { $initBody }
       |        on o: command Open is { $cmdBody }
       |        on ev: event Acct.Opened is { set field Acct.Fields.n to ev.n }
       |        on other as m is { log m }
       |      } with { briefly "h" }
       |    } with { briefly "a" }
       |$extra
       |  } with { briefly "c" option message_envelope("Riddl.Envelope") }
       |} with { briefly "d" }
       |""".stripMargin

  private val init = """log "created" yield event Acct.Opened(id = "x", n = 0)"""
  private val cmd = """log "opening " + o.id yield event Acct.Opened(id = o.id, n = o.n)"""

  "log" should {

    "validate clean in `on init` of an event-sourced entity, in a command clause, and under `on other as m`" in {
      (td: TestData) =>
        val msgs = diagnostics(model(init, cmd), td.name)
        withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "report an operand that resolves nowhere" in { (td: TestData) =>
      val msgs = diagnostics(model(init, """log nonsense yield event Acct.Opened(id = o.id, n = o.n)"""), td.name)
      of(msgs, RuleId.ValueRefUnresolved).size mustBe 1
    }

    "NOT settle a `yields` -- the yield-conformance Error still fires when log is all there is" in {
      (td: TestData) =>
        val msgs = diagnostics(model(init, """log "opening""""), td.name)
        msgs.justErrors.exists(_.message.toLowerCase.contains("yield")) mustBe true
    }

    "be legal in a function body" in { (td: TestData) =>
      val msgs = diagnostics(
        model(init, cmd,
          extra = """    function Cost is {
                    |      requires { n: Natural }
                    |      returns { c: Natural }
                    |      log "costing"
                    |      return n * 2
                    |    } with { briefly "f" }""".stripMargin),
        td.name
      )
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "count as executable work, so `on other is { log m }` is not a prompt-only handler" in {
      (td: TestData) =>
        of(diagnostics(model(init, cmd), td.name), RuleId.HandlerOnlyDoStatements) mustBe empty
    }

    "be unreachable after `error`, like any statement" in { (td: TestData) =>
      val msgs = diagnostics(model(init, """error "no" log "never""""), td.name)
      msgs.justErrors.exists(_.message.toLowerCase.contains("unreachable")) mustBe true
    }
  }
}
