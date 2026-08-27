/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.*

/** RIDDL has two message pairings, and as of 2.0 the syntax says which one you are in.
  *
  * {{{
  *   command Pay yields  event  Paid   ->  yield event Paid
  *   query   Ask replies result Answer ->  reply result Answer
  * }}}
  *
  * Until 2.0 `yield` spelled BOTH and `reply` was a deprecated synonym for it (`type ReplyStatement =
  * YieldStatement`), so a handler body did not say whether it was emitting an event or answering a
  * question. Reid split them (2026-08-08) both for readability and because `ask` needs something to
  * name: the value an `ask` produces is the one a `reply` provides.
  *
  * The split was a HARD SWITCH, not a deprecation: `yield result` is an Error immediately. That was
  * Reid's call, made knowing it reddens `RiddlModelsRoundTripTest` until ../riddl-models migrates
  * its 406 sites (../riddl-examples has 31 more). Task files went to both.
  */
class ReplyYieldPairingTest extends AbstractValidatingTest {

  /** Style warnings ON: the pairing diagnostics are Errors, but pinning options keeps this suite
    * from depending on whichever suite last touched the global flags. See `PathThroughFunctionTest`
    * for the run where that dependency produced an EMPTY message list and five vacuous passes.
    */
  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsFor(src: String, origin: String): String =
    diagnostics(src, origin).justErrors.map(_.message).mkString("\n")

  /** Messages from PARSING, which `parseAndValidate` discards.
    *
    * The DECLARATION pairing (`query X yields` / `command X replies`) is caught in the parser, not
    * ValidationPass, because the AST records only the referenced message -- not which keyword was
    * written -- so by validation time the evidence is gone. Checking it in the parser is what keeps
    * a single `Option[MessageRef]` field instead of adding one whose only job is carrying a
    * syntactic choice forward.
    *
    * These messages DO reach users: `parseInputWithMessages` -> `PassInput.parseMessages` ->
    * `PassesResult.additionalMessages`, so they appear under every riddlc command (verified against
    * a staged binary). Only this test helper needs the other channel.
    */
  private def parseErrorsFor(src: String, origin: String): String =
    TopLevelParser.parseInputWithMessages(RiddlParserInput(src, origin)) match
      case Left(errs)       => errs.map(_.message).mkString("\n")
      case Right((_, msgs)) => msgs.justErrors.map(_.message).mkString("\n")

  /** One model, parameterised on the query's declaration keyword and its clause's statement. */
  private def model(
    queryDecl: String,
    queryStmt: String,
    cmdStmt: String = "yield event D.C.Paid(v = 1)"
  ): String =
    s"""domain D is {
       |  context C is {
       |    result Answer is { v: Integer } with { briefly "r" }
       |    event Paid is { v: Integer } with { briefly "e" }
       |    query Ask $queryDecl result D.C.Answer is { q: Integer } with { briefly "q" }
       |    command Pay yields event D.C.Paid is { p: Integer } with { briefly "cm" }
       |    record R is { total: Integer } with { briefly "rc" }
       |    entity E is {
       |      state S of record D.C.R is {
       |        handler H is {
       |          on query D.C.Ask is { $queryStmt }
       |          on command D.C.Pay is { $cmdStmt }
       |        } with { briefly "h" }
       |      } with { briefly "st" }
       |    } with { briefly "en" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "the correct pairings" should {
    "validate clean: command yields/yield event, query replies/reply result" in { (td: TestData) =>
      errorsFor(model("replies", "reply result D.C.Answer(v = 1)"), td.name) mustBe ""
    }
  }

  "a mismatched STATEMENT" should {

    "reject `yield result` — the hard switch" in { (td: TestData) =>
      val errs = errorsFor(model("replies", "yield result D.C.Answer"), td.name)
      errs must include("`yield` takes an Event")
      errs must include("is a Result")
    }

    "reject `reply event`" in { (td: TestData) =>
      val errs = errorsFor(
        model("replies", "reply result D.C.Answer(v = 1)", "reply event D.C.Paid"),
        td.name
      )
      errs must include("`reply` takes a Result")
      errs must include("is an Event")
    }
  }

  "a mismatched DECLARATION" should {

    "reject `query X yields`" in { (td: TestData) =>
      parseErrorsFor(model("yields", "reply result D.C.Answer(v = 1)"), td.name) must
        include("a Query declares its response with `replies`")
    }

    "reject `command X replies`" in { (td: TestData) =>
      val src = model("replies", "reply result D.C.Answer(v = 1)")
        .replace("command Pay yields event", "command Pay replies event")
      parseErrorsFor(src, td.name) must include("a Command declares its response with `yields`")
    }

    "reject a response clause on a kind that declares none" in { (td: TestData) =>
      // VALIDATION's message, not the parser's. The parser deliberately does NOT check this: a
      // parse-time error stops the pass chain, so it would PREEMPT this more specific diagnostic
      // and be the only thing the author sees. The parser keeps only the check validation cannot
      // make -- `usecase` is in the AST, the KEYWORD written is not.
      val src = model("replies", "reply result D.C.Answer(v = 1)")
        .replace("event Paid is {", "event Paid yields event D.C.Paid is {")
      errorsFor(src, td.name) must include("Only command and query types may declare")
    }
  }

  "query conformance" should {

    "require a reply on every path, as a command requires a yield" in { (td: TestData) =>
      // The query half of `checkYieldConformance`, which used to test for YieldStatement and so
      // could not see a `reply` at all.
      errorsFor(model("replies", """do "nothing" """), td.name) must
        include("does not reply it on every path")
    }

    "accept a refusal as settling the query's obligation" in { (td: TestData) =>
      errorsFor(model("replies", """error "declined" """), td.name) mustNot
        include("does not reply it on every path")
    }
  }

  "the AST" should {
    "give ReplyStatement its own identity, no longer an alias for YieldStatement" in {
      (td: TestData) =>
        val ref = ResultRef(At.empty, PathIdentifier(At.empty, Seq("R")))
        val reply = ReplyStatement(At.empty, ref)
        val yieldS = YieldStatement(At.empty, ref)
        reply.kind mustBe "Reply Statement"
        yieldS.kind mustBe "Yield Statement"
        reply.format mustBe s"reply ${ref.format}"
        // The point of the split: they are no longer the same type.
        (reply: Statement) mustNot be(a[YieldStatement])
    }
  }
}
