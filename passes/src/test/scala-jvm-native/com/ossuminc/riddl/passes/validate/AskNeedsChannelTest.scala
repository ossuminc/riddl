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

/** An `ask` needs a modelled path BOTH WAYS (Reid, 2026-09-09/10).
  *
  * riddl-models reported an asymmetry that had already taught them a false rule: in one adaptor
  * touched by no connector at all, `tell` drew two Errors while `ask` drew nothing whatsoever. The
  * only `ask` rules were about the far end's BEHAVIOUR — that it handles the query
  * (`msg-ask-not-handled`), that the query declares `replies` (`msg-ask-no-replies`) — and none
  * about how the question gets there or the answer gets back. Reading "0 errors" off an unwired
  * `ask`, that session concluded and wrote down that *"ask works in unwired adaptors too — wiring
  * is simply irrelevant to it"*, and was about to apply it to 363 sites across 118 models.
  *
  * **A validator silent where the language has a rule does not merely miss a mistake; it teaches
  * the wrong rule, because "it validates" is the evidence modellers use.**
  *
  * Reid's rulings, both quoted because the second is the one that is easy to get backwards:
  *
  * > There are no magic ways for processors to communicate. If an adaptor is going to "ask" an
  * > entity or a repository for some information, that request MUST go over a connector and
  * > therefore it MUST be wired to it. "ask" is just like send except that it implies additional
  * > semantics in the generator … None of that is relevant to the model, it's the generator's
  * > concern. There's no way to communicate without wiring, even in the same process boundaries.
  *
  * > the reply path must be wired in the model just like the query path. Regardless of how the
  * > generator chooses to lower it, the communication must be POSSIBLE in the model, without the
  * > path, it is not.
  *
  * **The distinction that settles the reply leg: the MECHANISM is the generator's, the PATH is the
  * model's.** A reply actor, a correlation id, a future — those are lowering choices with no
  * model-level representation. Whether an answer can physically get back is not.
  *
  * So `ask` now carries `tell`'s reachability question twice, over the same connector graph and
  * with the same exemptions: `msg-ask-target-unreachable` for the question leg and
  * `msg-ask-reply-unreachable` for the reply leg.
  */
class AskNeedsChannelTest extends AbstractValidatingTest {

  /** Asker `A` and answerer `B`, two contexts. `wiring` supplies the connectors, so each case says
    * exactly which legs are modelled.
    */
  private def model(wiring: String): String =
    s"""domain D is {
       |  result Xs is { n: Integer } with { briefly "x" }
       |  query GetX replies result D.Xs is { id: String } with { briefly "q" }
       |  event Trigger is { id: String } with { briefly "t" }
       |  context A is {
       |    inlet Ain is event D.Trigger with { briefly "i" }
       |    inlet Aback is result D.Xs with { briefly "b" }
       |    outlet Aout is query D.GetX with { briefly "o" }
       |    handler AH is {
       |      on event D.Trigger is {
       |        let answer: type D.Xs = ask query D.GetX of context D.B
       |        do "use the answer"
       |      }
       |      on result D.Xs is { do "the reply arrives here" }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |  } with { briefly "a" }
       |  context B is {
       |    inlet Bin is query D.GetX with { briefly "i" }
       |    outlet Bout is result D.Xs with { briefly "o" }
       |    handler BH is {
       |      on query D.GetX is { reply result D.Xs(n = 1) }
       |      on other is { error "unexpected" }
       |    } with { briefly "h" }
       |  } with { briefly "b" }
       |$wiring
       |} with { briefly "d" }
       |""".stripMargin

  private val questionLeg =
    """  connector Q is { from outlet A.Aout to inlet B.Bin } with { briefly "q" }"""
  private val replyLeg =
    """  connector R is { from outlet B.Bout to inlet A.Aback } with { briefly "r" }"""

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    captured

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  "an `ask` with both legs wired" should {

    "draw neither channel error" in { (td: TestData) =>
      val msgs = diagnostics(model(s"$questionLeg\n$replyLeg"), td.name)
      errorsOf(msgs, RuleId.AskTargetUnreachable) mustBe empty
      errorsOf(msgs, RuleId.AskReplyUnreachable) mustBe empty
      msgs.justErrors.map(_.format) mustBe empty
    }
  }

  "the QUESTION leg" should {

    "be an Error when no connector carries the query to the target" in { (td: TestData) =>
      // The reply leg is wired, so only the question leg can be at fault -- without that, a single
      // "ask is unwired" fixture would pass for either rule and prove neither.
      val msgs = diagnostics(model(replyLeg), td.name)
      errorsOf(msgs, RuleId.AskTargetUnreachable) must not be empty
      errorsOf(msgs, RuleId.AskReplyUnreachable) mustBe empty
    }
  }

  "the REPLY leg" should {

    "be an Error when no connector carries the answer back" in { (td: TestData) =>
      // The half riddl-models could not have guessed at, and the half a reader is most likely to
      // think the generator supplies.
      val msgs = diagnostics(model(questionLeg), td.name)
      errorsOf(msgs, RuleId.AskReplyUnreachable) must not be empty
      errorsOf(msgs, RuleId.AskTargetUnreachable) mustBe empty
    }

    "name the asker and the answerer, so the missing connector is obvious" in { (td: TestData) =>
      val found = errorsOf(diagnostics(model(questionLeg), td.name), RuleId.AskReplyUnreachable)
      val text = found.head.message
      withClue(s"message was: $text\n") {
        text must include("A")
        text must include("B")
      }
    }
  }

  "an entirely unwired `ask`" should {

    // The exact shape riddl-models reported: no connector anywhere near it. Both legs missing.
    "draw BOTH errors, where it previously drew nothing at all" in { (td: TestData) =>
      val msgs = diagnostics(model(""), td.name)
      errorsOf(msgs, RuleId.AskTargetUnreachable) must not be empty
      errorsOf(msgs, RuleId.AskReplyUnreachable) must not be empty
    }
  }

  "the exemptions" should {

    // Mirrors `tell`: asking YOURSELF needs no channel, since there is nothing to model.
    "not fire when a processor asks itself" in { (td: TestData) =>
      val src =
        """domain D is {
          |  result Xs is { n: Integer } with { briefly "x" }
          |  query GetX replies result D.Xs is { id: String } with { briefly "q" }
          |  event Trigger is { id: String } with { briefly "t" }
          |  context A is {
          |    inlet Ain is event D.Trigger with { briefly "i" }
          |    handler AH is {
          |      on event D.Trigger is {
          |        let answer: type D.Xs = ask query D.GetX of context D.A
          |        do "use the answer"
          |      }
          |      on query D.GetX is { reply result D.Xs(n = 1) }
          |      on other is { error "unexpected" }
          |    } with { briefly "h" }
          |  } with { briefly "a" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, td.name)
      errorsOf(msgs, RuleId.AskTargetUnreachable) mustBe empty
      errorsOf(msgs, RuleId.AskReplyUnreachable) mustBe empty
    }

    // A `???` body is "known to be incomplete" and earns at most a Missing warning -- the standing
    // ruling. It declares no inlet either, so there is nothing to reach.
    "not fire when the target is a `???` stub" in { (td: TestData) =>
      val src =
        """domain D is {
          |  result Xs is { n: Integer } with { briefly "x" }
          |  query GetX replies result D.Xs is { id: String } with { briefly "q" }
          |  event Trigger is { id: String } with { briefly "t" }
          |  context A is {
          |    inlet Ain is event D.Trigger with { briefly "i" }
          |    handler AH is {
          |      on event D.Trigger is {
          |        let answer: type D.Xs = ask query D.GetX of context D.B
          |        do "use the answer"
          |      }
          |      on other is { error "unexpected" }
          |    } with { briefly "h" }
          |  } with { briefly "a" }
          |  context B is { ??? }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, td.name)
      errorsOf(msgs, RuleId.AskTargetUnreachable) mustBe empty
      errorsOf(msgs, RuleId.AskReplyUnreachable) mustBe empty
    }
  }
}
