/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, At}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.*

/** `ask query Foo of entity Bar` — a request whose ANSWER is a value.
  *
  * RIDDL could already send a message (`tell`) and declare what handling one produces
  * (`yields`/`replies`). What it could NOT say is that two messages are two halves of ONE
  * interaction: `yield` names no destination, `tell` says nothing about a reply, and the word
  * `correlation` appeared nowhere in `language/src/main`. A generator therefore could not
  * distinguish fire-and-forget from a request whose answer the caller awaits.
  *
  * `ask` declares that correlation and NOTHING more. It implies no Future, no temp actor, no
  * correlation-id field and no blocking call — all four are lowerings a generator should choose
  * between, on the principle that settled `message_envelope`.
  *
  * QUERIES ONLY (Reid, 2026-08-08), and the restriction is STRUCTURAL: the parser takes a
  * `queryRef`, not a general `messageRef`, so `ask command X` cannot be built — only mis-parsed.
  * That is a stronger guarantee than a validation rule, at the cost of a parse-shaped diagnostic;
  * Reid confirmed the trade.
  */
class AskTest extends AbstractValidatingTest {

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

  /** One model, parameterised on the query's declaration and the asking statement. */
  private def model(
    queryDecl: String = "replies result D.C.Answer",
    askStmt: String = "let answer = ask query D.C.Ask of entity D.C.Ledger"
  ): String =
    s"""domain D is {
       |  context C is {
       |    result Answer is { v: Integer } with { briefly "r" }
       |    query Ask $queryDecl is { q: Integer } with { briefly "q" }
       |    command Go is { g: Integer } with { briefly "cm" }
       |    record R is { total: Integer } with { briefly "rc" }
       |    entity Ledger is {
       |      state S of record D.C.R is {
       |        handler H is {
       |          on query D.C.Ask is { reply result D.C.Answer(v = "the answer") }
       |        } with { briefly "h" }
       |      } with { briefly "st" }
       |    } with { briefly "en" }
       |    entity Caller is {
       |      state S2 of record D.C.R is {
       |        handler H2 is {
       |          on command D.C.Go is {
       |            $askStmt
       |            do "use the answer"
       |          }
       |        } with { briefly "h2" }
       |      } with { briefly "st2" }
       |    } with { briefly "en2" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a well-formed ask" should {

    "validate clean" in { (td: TestData) =>
      errorsFor(model(), td.name) mustBe ""
    }

    "parse into an Ask value bound by the let" in { (td: TestData) =>
      TopLevelParser.parseInput(RiddlParserInput(model(), td.name)) match
        case Left(msgs) => fail(s"did not parse:\n${msgs.format}")
        case Right(root) =>
          val lets = Finder(root).recursiveFindByType[LetStatement]
          val ask = lets
            .map(_.expression)
            .collectFirst { case a: Ask => a }
            .getOrElse(fail(s"no Ask in any let; found ${lets.map(_.expression.getClass)}"))
          ask.query.pathId.value.last mustBe "Ask"
          ask.processor.pathId.value.last mustBe "Ledger"
    }
  }

  "the query-only restriction" should {

    "make `ask command` UNREPRESENTABLE, not merely invalid" in { (td: TestData) =>
      // Structural, because the parser takes a QueryRef. The diagnostic is therefore a parse
      // failure naming `query` among the expected tokens, rather than a validation sentence —
      // the deliberate trade for an AST that cannot hold the wrong thing.
      val src = model(askStmt = "let answer = ask command D.C.Go of entity D.C.Ledger")
      TopLevelParser.parseInput(RiddlParserInput(src, td.name)) match
        case Left(msgs) => msgs.format must include("query")
        case Right(root) =>
          Finder(root).recursiveFindByType[LetStatement].map(_.expression).collect { case a: Ask =>
            a
          } mustBe empty
    }
  }

  "an unanswerable ask" should {

    "be an error when the query declares no `replies`" in { (td: TestData) =>
      // `replies` is OPTIONAL in general. This is the ONE place that makes it mandatory, which is
      // why the requirement lives at the ask site rather than on every query.
      errorsFor(model(queryDecl = "is { q: Integer } with { briefly \"q\" } //"), td.name) must
        include("declares no `replies`")
    }

    "be an error when the processor has no clause for the query" in { (td: TestData) =>
      errorsFor(
        model(askStmt = "let answer = ask query D.C.Ask of entity D.C.Caller"),
        td.name
      ) must include("no clause handling")
    }

    "NOT fire the no-clause error when the processor DOES handle it" in { (td: TestData) =>
      // The control. Without it, "no clause handling" would pass by never firing at all.
      errorsFor(model(), td.name) mustNot include("no clause handling")
    }
  }

  "the answer's type" should {

    "come from the query's declared `replies result X`" in { (td: TestData) =>
      // The reason Phase A had to land first: before `replies`, a query had no per-query
      // declaration to take the answer's type FROM. A `let` bound to an ask is typed, always —
      // there is no untyped case to handle.
      val src = model(askStmt = "let answer: D.C.Answer = ask query D.C.Ask of entity D.C.Ledger")
      errorsFor(src, td.name) mustBe ""
    }

    "reject an ascription that contradicts the declared result" in { (td: TestData) =>
      val src = model(askStmt = "let answer: D.C.R = ask query D.C.Ask of entity D.C.Ledger")
      errorsFor(src, td.name) mustNot be("")
    }
  }
}
