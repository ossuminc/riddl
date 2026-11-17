/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** riddl-models, 2026-11-17: reactive-bbq twice lost the only `send` that fed a boundary clause,
  * and riddlc reported nothing either time. Two Completeness rules answer it:
  * `msg-clause-undeliverable` (an `on X` clause nothing can deliver X to) and
  * `stream-outlet-never-sent` (a connected outlet its owner never transmits on).
  */
class UndeliverableClauseTest extends AbstractValidatingTest {

  /** `Src` handles `Go` and (per `srcBody`) puts `Ev` on `Out`, wired to `Dst.In`, where `on event
    * Ev` waits. `extra` adds definitions to the context.
    */
  private def model(srcBody: String, dstInlet: String = "inlet In is event C.Ev", extra: String = ""): String =
    s"""domain D is { context C is {
       |  command Go is { x: Integer }
       |  event Ev is { x: Integer }
       |  entity Src is {
       |    inlet SrcIn is command C.Go
       |    outlet Out is event C.Ev
       |    handler H is { on command C.Go { $srcBody } }
       |  }
       |  entity Dst is {
       |    $dstInlet
       |    handler H is { on event C.Ev { do "record it" } }
       |  }
       |  connector K is { from outlet Src.Out to inlet Dst.In }
       |  $extra
       |} }
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

  "A clause nothing can deliver to" should {
    "be silent when the upstream processor sends the message (negative control)" in { (td: TestData) =>
      val msgs = messages(model("send event C.Ev(x = 1) to outlet Src.Out"), td)
      of(msgs, RuleId.ClauseUndeliverable) mustBe empty
      of(msgs, RuleId.OutletNeverSent) mustBe empty
    }

    "be reported, with the outlet, when the only send is deleted (the riddl-models repro)" in {
      (td: TestData) =>
        val msgs = messages(model("do \"forgot to send\""), td)
        val clause = of(msgs, RuleId.ClauseUndeliverable)
        clause.size mustBe 1
        clause.head.kind mustBe Messages.CompletenessWarning
        clause.head.message must include("Entity 'Dst'")
        clause.head.message must include("Event 'Ev'")
        val outlet = of(msgs, RuleId.OutletNeverSent)
        outlet.size mustBe 1
        outlet.head.kind mustBe Messages.CompletenessWarning
        outlet.head.message must include("Outlet 'Out'")
    }

    "count a yield on an outlet admitting it" in { (td: TestData) =>
      val msgs = messages(model("yield event C.Ev(x = 1)"), td)
      of(msgs, RuleId.ClauseUndeliverable) mustBe empty
      of(msgs, RuleId.OutletNeverSent) mustBe empty
    }

    "count a send of a DIFFERENT type as not delivering this one" in { (td: TestData) =>
      val extra = "event Other is { y: Integer }"
      val src = model("send event C.Other(y = 1) to outlet Src.Out", extra = extra)
      // The outlet IS used (the send names it), so only the clause is reported.
      val msgs = messages(src, td)
      of(msgs, RuleId.ClauseUndeliverable).size mustBe 1
      of(msgs, RuleId.OutletNeverSent) mustBe empty
    }

    "accept a tell as a direct delivery" in { (td: TestData) =>
      val msgs = messages(model("tell event C.Ev(x = 1) to entity C.Dst"), td)
      of(msgs, RuleId.ClauseUndeliverable) mustBe empty
      // The tell rides the connected outlet (A6), so the outlet is in use too.
      of(msgs, RuleId.OutletNeverSent) mustBe empty
    }

    "abstain when the upstream processor is opaque" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  event Ev is { x: Integer }
          |  source Src is { outlet Out is event C.Ev }
          |  entity Dst is {
          |    inlet In is event C.Ev
          |    handler H is { on event C.Ev { do "record it" } }
          |  }
          |  connector K is { from outlet Src.Out to inlet Dst.In }
          |} }
          |""".stripMargin
      val msgs = messages(src, td)
      of(msgs, RuleId.ClauseUndeliverable) mustBe empty
      of(msgs, RuleId.OutletNeverSent) mustBe empty
    }

    "abstain when the admitting inlet is not connected at all" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  event Ev is { x: Integer }
          |  entity Dst is {
          |    inlet In is event C.Ev
          |    handler H is { on event C.Ev { do "record it" } }
          |  }
          |} }
          |""".stripMargin
      of(messages(src, td), RuleId.ClauseUndeliverable) mustBe empty
    }

    "report a clause none of whose inlets admits the message, with no direct delivery" in {
      (td: TestData) =>
        val msgs = messages(model("send event C.Ev(x = 1) to outlet Src.Out",
          dstInlet = "inlet In is event C.Ev\n    inlet Other is command C.Go"), td)
        of(msgs, RuleId.ClauseUndeliverable) mustBe empty // control: Ev still arrives
        val src =
          """domain D is { context C is {
            |  command Go is { x: Integer }
            |  event Ev is { x: Integer }
            |  entity Dst is {
            |    inlet In is command C.Go
            |    handler H is {
            |      on command C.Go { do "go" }
            |      on event C.Ev { do "record it" }
            |    }
            |  }
            |} }
            |""".stripMargin
        val errs = of(messages(src, td), RuleId.ClauseUndeliverable)
        errs.size mustBe 1
        errs.head.message must include("no inlet of Entity 'Dst' admits it")
    }

    "leave a processor with no inlet at all to the missing-inlet rule" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  event Ev is { x: Integer }
          |  entity Dst is {
          |    handler H is { on event C.Ev { do "record it" } }
          |  }
          |} }
          |""".stripMargin
      val msgs = messages(src, td)
      of(msgs, RuleId.ClauseUndeliverable) mustBe empty
      of(msgs, RuleId.EntityNoInlet).size mustBe 1
    }

    "exempt a ??? clause body" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  command Go is { x: Integer }
          |  event Ev is { x: Integer }
          |  entity Dst is {
          |    inlet In is command C.Go
          |    handler H is {
          |      on command C.Go { do "go" }
          |      on event C.Ev { ??? }
          |    }
          |  }
          |} }
          |""".stripMargin
      of(messages(src, td), RuleId.ClauseUndeliverable) mustBe empty
    }
  }
}
