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

/** A `create` DELEGATES a creation command's answer to the created entity (Reid, 2026-11-23,
  * closing riddl-models' 11 creation sites): when the clause handling `Create yields Created`
  * `create`s an entity whose `on init` publishes `Created` on every path, the `create` settles the
  * `yields`, as `forward` does, and the answer travels from that entity's outlets (CM §20.7).
  */
class CreationAnswerDelegationTest extends AbstractValidatingTest {

  private val publish = "send event C.Created(id = id) to outlet C.Shipment.Out"

  private def model(
    orderBody: String = "let s = create entity C.Shipment(id = c.id)",
    initBody: String = publish,
    wiring: String = "    connector B is from outlet C.Shipment.Out to inlet C.Front.Back"
  ): String =
    s"""domain D is {
       |  context C is {
       |    command Create yields event Created is { id: String } with { briefly "c" }
       |    event Created is { id: String } with { briefly "e" }
       |    event Other is { id: String } with { briefly "e" }
       |    event Trigger is { id: String } with { briefly "t" }
       |    entity Orders is {
       |      inlet In is command C.Create with { briefly "i" }
       |      outlet Out is event C.Created with { briefly "o" }
       |      handler H is {
       |        on c: command C.Create is {
       |          $orderBody
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "o" }
       |    entity Shipment is {
       |      record SF is { id: String } with { briefly "r" }
       |      state Live of record Shipment.SF
       |      outlet Out is event C.Created with { briefly "o" }
       |      outlet Else is event C.Other with { briefly "o" }
       |      handler H is {
       |        on init(id: String) is {
       |          morph entity C.Shipment to state C.Shipment.Live with record Shipment.SF(id = id)
       |          $initBody
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "s" }
       |    entity Front is {
       |      inlet Trig is event C.Trigger with { briefly "i" }
       |      inlet Back is event C.Created with { briefly "i" }
       |      outlet ToOrders is command C.Create with { briefly "o" }
       |      handler H is {
       |        on t: event C.Trigger is { send command C.Create(id = t.id) to outlet C.Front.ToOrders }
       |        on event C.Created is { do "note it" }
       |      } with { briefly "h" }
       |    } with { briefly "f" }
       |    connector A is from outlet C.Front.ToOrders to inlet C.Orders.In
       |$wiring
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def all(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse:\n${m.format}")
      case None    => captured

  private def of(ms: Messages, rule: RuleId): Messages = ms.filter(_.ruleId.contains(rule))
  private def clue(ms: Messages): String = ms.map(_.format).mkString("\n")

  "a create whose entity publishes the answer in on init" should {
    "settle the yields and carry the answer, with no yield in the clause" in { (td: TestData) =>
      val ms = all(model(), td.name)
      withClue(clue(ms)) {
        ms.filter(_.kind == Messages.Error) mustBe empty
        of(ms, RuleId.YieldUndeclared) mustBe empty
        of(ms, RuleId.AnswerUnreachable) mustBe empty
        of(ms, RuleId.YieldDuplicatesCreation) mustBe empty
      }
    }

    "report a yield of the same answer beside it as a second publication" in { (td: TestData) =>
      val ms = all(
        model(orderBody =
          "let s = create entity C.Shipment(id = c.id)\n          yield event C.Created(id = c.id)"),
        td.name
      )
      withClue(clue(ms)) {
        val dup = of(ms, RuleId.YieldDuplicatesCreation)
        dup must have size 1
        dup.head.kind mustBe Messages.StyleWarning
        dup.head.message must include("Shipment")
        of(ms, RuleId.AnswerUnreachable) mustBe empty
      }
    }

    "settle when the create is on one branch and an error on the other" in { (td: TestData) =>
      val ms = all(
        model(orderBody =
          """when c.id == "" then error "no id" else let s = create entity C.Shipment(id = c.id) end"""),
        td.name
      )
      withClue(clue(ms)) { of(ms, RuleId.YieldUndeclared) mustBe empty }
    }

    "name the created entity when its answer cannot reach the sender" in { (td: TestData) =>
      val ms = all(model(wiring = ""), td.name)
      withClue(clue(ms)) {
        of(ms, RuleId.YieldUndeclared) mustBe empty
        val lost = of(ms, RuleId.AnswerUnreachable)
        lost must have size 1
        lost.head.message must include("Shipment")
      }
    }
  }

  "a create does NOT delegate" should {
    "when on init publishes the answer on only one path" in { (td: TestData) =>
      val ms = all(
        model(initBody = s"""when id == "" then do "nothing" else $publish end"""),
        td.name
      )
      withClue(clue(ms)) { of(ms, RuleId.YieldUndeclared) must have size 1 }
    }

    "when on init publishes a different event" in { (td: TestData) =>
      val ms = all(
        model(initBody = "send event C.Other(id = id) to outlet C.Shipment.Else"),
        td.name
      )
      withClue(clue(ms)) {
        of(ms, RuleId.YieldUndeclared) must have size 1
        of(ms, RuleId.YieldDuplicatesCreation) mustBe empty
      }
    }

    "and an ordinary yield still settles and is not reported" in { (td: TestData) =>
      val ms = all(
        model(orderBody = "yield event C.Created(id = c.id)", initBody = "", wiring = ""),
        td.name
      )
      withClue(clue(ms)) {
        of(ms, RuleId.YieldUndeclared) mustBe empty
        of(ms, RuleId.YieldDuplicatesCreation) mustBe empty
      }
    }
  }
}
