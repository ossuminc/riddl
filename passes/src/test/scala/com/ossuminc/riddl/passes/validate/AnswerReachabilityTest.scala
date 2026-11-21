/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** synapify, 2026-11-21 (D4 Phase 7c): a processor that sends a command declaring `yields E` must
  * be able to receive E back over the model's wiring -- a `yield` is published on the answerer's
  * outlets and there is no unwired return channel (CM §20.4). reactive-bbq's
  * `ReservationEventSplit` sends `CreateOrder` and has no inlet admitting `OrderCreated`.
  */
class AnswerReachabilityTest extends AbstractValidatingTest {

  private def model(
    frontPorts: String = "",
    wiring: String = "",
    sendLine: String = "send command C.Create(id = t.id) to outlet C.Front.ToOrders",
    createDecl: String = "command Create yields event Created is { id: String }"
  ): String =
    s"""domain D is {
       |  context C is {
       |    $createDecl with { briefly "c" }
       |    event Created is { id: String } with { briefly "e" }
       |    event Trigger is { id: String } with { briefly "t" }
       |    entity Orders is {
       |      inlet In is command C.Create with { briefly "i" }
       |      outlet Out is event C.Created with { briefly "o" }
       |      handler H is {
       |        on c: command C.Create is { yield event C.Created(id = c.id) }
       |      } with { briefly "h" }
       |    } with { briefly "o" }
       |    entity Front is {
       |      inlet Trig is event C.Trigger with { briefly "i" }
       |      outlet ToOrders is command C.Create with { briefly "o" }
       |$frontPorts
       |      handler H is {
       |        on t: event C.Trigger is { $sendLine }
       |        on event C.Created is { do "note it" }
       |      } with { briefly "h" }
       |    } with { briefly "f" }
       |    connector A is from outlet C.Front.ToOrders to inlet C.Orders.In
       |$wiring
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def found(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse:\n${m.format}")
      case None    => captured.filter(_.ruleId.contains(RuleId.AnswerUnreachable))

  "a send of a command that yields" should {
    "be reported when the sender has no inlet admitting the answer" in { (td: TestData) =>
      val ms = found(model(), td.name)
      withClue(ms.map(_.format).mkString("\n")) {
        ms must have size 1
        ms.head.kind mustBe Messages.CompletenessWarning
        ms.head.message must include("declares no inlet admitting it")
        ms.head.message must include("Created")
      }
    }
    "be reported when the admitting inlet is not connected back" in { (td: TestData) =>
      val ms = found(model(frontPorts = """      inlet Back is event C.Created with { briefly "i" }"""), td.name)
      withClue(ms.map(_.format).mkString("\n")) {
        ms must have size 1
        ms.head.message must include("no connector route carries it")
      }
    }
    "be clean when the answer is wired back" in { (td: TestData) =>
      val ms = found(
        model(
          frontPorts = """      inlet Back is event C.Created with { briefly "i" }""",
          wiring = "    connector B is from outlet C.Orders.Out to inlet C.Front.Back"
        ),
        td.name
      )
      withClue(ms.map(_.format).mkString("\n")) { ms mustBe empty }
    }
    "be clean for a command that yields nothing" in { (td: TestData) =>
      found(model(createDecl = "command Create is { id: String }"), td.name) mustBe empty
    }
    "be reported for a tell as well" in { (td: TestData) =>
      val ms = found(model(sendLine = "tell command C.Create(id = t.id) to entity C.Orders"), td.name)
      ms must have size 1
    }
  }
}
