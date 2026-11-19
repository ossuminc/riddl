/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** riddl-models, 2026-11-19 (`tooling/code-generator`): a handler-less `application context` was
  * trusted to SEND on its outlets but held literally to RECEIVE on its inlets. Reid's rulings:
  *
  *   1. A handler-less Context or Projector -- the two kinds with no "should have a handler" rule --
  *      is judged LITERALLY on both sides. It has no clause a `send` could be written in, so a
  *      connected outlet no group input feeds is `stream-outlet-never-sent`.
  *   2. An `external context` is opaque on BOTH sides: its inlets are not
  *      `stream-inlet-not-received` either.
  *   3. Handler-less entities, streamlets, repositories and adaptors stay opaque: their own
  *      "should have a handler" rule states the omission once.
  *   4. A group input says a USER issues the command; a process issues it from a handler
  *      (`on every`, or `send ... every` in `on init`).
  */
class HandlerlessProcessorSidesTest extends AbstractValidatingTest {

  private def model(app: String, extra: String = ""): String =
    s"""domain D is {
       |  command Go is { x: Integer }
       |  result Done is { x: Integer }
       |  event Ev is { x: Integer }
       |  $app
       |  context Svc is {
       |    inlet In is command D.Go
       |    outlet Back is result D.Done
       |    handler H is { on command D.Go { send result D.Done(x = 1) to outlet Svc.Back } }
       |  }
       |  connector Fwd is { from outlet App.Out to inlet Svc.In }
       |  $extra
       |}
       |""".stripMargin

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))

  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  private val replyWire = "connector Rep is { from outlet Svc.Back to inlet App.Replies }"

  "a handler-less application context" should {
    "draw stream-outlet-never-sent for a connected outlet no input feeds" in { (td: TestData) =>
      val msgs = messages(model("application context App is { outlet Out is command D.Go }"), td)
      withClue(clue(msgs)) {
        val ons = of(msgs, RuleId.OutletNeverSent)
        ons.size mustBe 1
        ons.head.message must include("Outlet 'Out'")
      }
    }
    "be clean when a group input acquires the command" in { (td: TestData) =>
      val app =
        """application context App is {
          |    outlet Out is command D.Go
          |    group Console is { button Run acquires command D.Go }
          |  }""".stripMargin
      val msgs = messages(model(app), td)
      withClue(clue(msgs)) { of(msgs, RuleId.OutletNeverSent) mustBe empty }
    }
    "still draw stream-inlet-not-received on its reply inlet" in { (td: TestData) =>
      val app =
        """application context App is {
          |    outlet Out is command D.Go
          |    inlet Replies is result D.Done
          |  }""".stripMargin
      val msgs = messages(model(app, replyWire), td)
      withClue(clue(msgs)) { of(msgs, RuleId.InletNotReceived).size mustBe 1 }
    }
  }

  "an application context driven by a handler (ruling 4)" should {
    "be clean with `on every` sending the command" in { (td: TestData) =>
      val app =
        """application context App is {
          |    outlet Out is command D.Go
          |    handler Driver is { on every 1 hour is { send command D.Go(x = 1) to outlet App.Out } }
          |  }""".stripMargin
      val msgs = messages(model(app), td)
      withClue(clue(msgs)) { of(msgs, RuleId.OutletNeverSent) mustBe empty }
    }
    "be clean with `send ... every` in `on init`" in { (td: TestData) =>
      val app =
        """application context App is {
          |    outlet Out is command D.Go
          |    handler Driver is { on init is { send command D.Go(x = 1) to outlet App.Out every 1 hour } }
          |  }""".stripMargin
      val msgs = messages(model(app), td)
      withClue(clue(msgs)) { of(msgs, RuleId.OutletNeverSent) mustBe empty }
    }
  }

  "a handler-less external context" should {
    "be trusted on both sides" in { (td: TestData) =>
      val app =
        """external context App is {
          |    outlet Out is command D.Go
          |    inlet Replies is result D.Done
          |  }""".stripMargin
      val msgs = messages(model(app, replyWire), td)
      withClue(clue(msgs)) {
        of(msgs, RuleId.OutletNeverSent) mustBe empty
        of(msgs, RuleId.InletNotReceived) mustBe empty
      }
    }
  }

  "a handler-less projector" should {
    "draw stream-outlet-never-sent for a connected outlet" in { (td: TestData) =>
      val extra =
        """context Views is {
          |    projector P is { outlet POut is event D.Ev }
          |    inlet VIn is event D.Ev
          |    connector PK is { from outlet Views.P.POut to inlet Views.VIn }
          |  }""".stripMargin
      val msgs = messages(model("application context App is { outlet Out is command D.Go }", extra), td)
      withClue(clue(msgs)) {
        of(msgs, RuleId.OutletNeverSent).map(_.message).exists(_.contains("Outlet 'POut'")) mustBe true
      }
    }
  }

  "a handler-less entity (ruling 3, unchanged)" should {
    "stay exempt: its 'should have a handler' report covers the omission" in { (td: TestData) =>
      val extra =
        """context Ents is {
          |    entity E is { outlet EOut is event D.Ev }
          |    inlet EIn is event D.Ev
          |    connector EK is { from outlet Ents.E.EOut to inlet Ents.EIn }
          |  }""".stripMargin
      val msgs = messages(model("application context App is { outlet Out is command D.Go }", extra), td)
      withClue(clue(msgs)) {
        of(msgs, RuleId.OutletNeverSent).map(_.message).exists(_.contains("Outlet 'EOut'")) mustBe false
      }
    }
  }
}
