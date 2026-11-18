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

    // riddl-models, 2026-11-18: a context's `tell` to its own entity travels context outlet ->
    // router -> entity inlet, and the router relays it. `msg-tell-no-route-for-type` counts that
    // relay as the tell's route, so the router's clause is delivered to.
    def relayModel(routerClause: String): String =
      s"""domain D is { context C is {
         |  command Start is { x: Integer }
         |  command Go is { x: Integer }
         |  command Other is { y: Integer }
         |  inlet CIn is command C.Start
         |  outlet COut is command C.Go
         |  handler CH is { on command C.Start { tell command C.Go(x = 1) to entity C.E } }
         |  router R is {
         |    inlet RIn is command C.Go
         |    inlet ROtherIn is command C.Other
         |    outlet ROut is command C.Go
         |    outlet ROtherOut is command C.Other
         |    handler RH is {
         |      $routerClause
         |      on o: command C.Other is { send o to outlet C.R.ROtherOut }
         |    }
         |  }
         |  entity E is {
         |    inlet EIn is command C.Go
         |    handler EH is { on command C.Go { do "go" } }
         |  }
         |  connector K1 is { from outlet C.COut to inlet C.R.RIn }
         |  connector K2 is { from outlet C.R.ROut to inlet C.E.EIn }
         |} }
         |""".stripMargin

    "count a tell riding through a relaying router as delivering to the router's clause" in {
      (td: TestData) =>
        val msgs = messages(relayModel("on g: command C.Go is { send g to outlet C.R.ROut }"), td)
        of(msgs, RuleId.ClauseUndeliverable) mustBe empty
        of(msgs, RuleId.TellNoRouteForType) mustBe empty
    }

    "leave msg-tell-no-route-for-type to report a deleted relay (negative control)" in {
      (td: TestData) =>
        val msgs = messages(relayModel(""), td)
        of(msgs, RuleId.TellNoRouteForType).size mustBe 1
    }

    // riddl-models, 2026-11-18: a projector correlation's `yields command` is transmitted on the
    // projector's outlets that admit it, like a `yield`.
    def correlationModel(repoInletType: String): String =
      s"""domain D is { context C is {
         |  event Seen is { k: String, n: Integer }
         |  command Persist is { k: String, n: Integer }
         |  command Noted is { k: String }
         |  type RepoCommand is one of { command C.Persist or command C.Noted }
         |  repository Repo is {
         |    inlet RepoIn is $repoInletType
         |    handler RH is {
         |      on command C.Persist { do "persist" }
         |      on command C.Noted { do "note" }
         |    }
         |  }
         |  projector P is {
         |    updates repository C.Repo
         |    inlet PIn is event C.Seen
         |    outlet POut is $repoInletType
         |    handler PH is { on e: event C.Seen is { send command C.Noted(k = e.k) to outlet C.P.POut } }
         |    correlation J by k yields command C.Persist is {
         |      handler Collect is { on e: event C.Seen is { set field n to e.n } }
         |    } times out after "1 day" { do "give up" }
         |  }
         |  connector K is { from outlet C.P.POut to inlet C.Repo.RepoIn }
         |} }
         |""".stripMargin

    "count a projector correlation's yielded command as transmitted on its outlets" in {
      (td: TestData) =>
        val msgs = messages(correlationModel("type C.RepoCommand"), td)
        of(msgs, RuleId.ClauseUndeliverable) mustBe empty
    }

    "still report the clause when the inlet no longer admits the yielded command" in {
      (td: TestData) =>
        val errs = of(messages(correlationModel("command C.Noted"), td), RuleId.ClauseUndeliverable)
        errs.size mustBe 1
        errs.head.message must include("Command 'Persist'")
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
