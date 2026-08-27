/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.utils.{pc, ec, Await, PathUtils}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Parity guard for A70's `terminate` statement: the corpus fixture
  * `language/input/terminate-statement.riddl` is validated against the EBNF grammar by the TatSu
  * validator (which scans every input-directory riddl file). This test proves fastparse accepts the
  * SAME file, so the documented grammar and the implementation stay in sync (see CLAUDE.md
  * "Parser/EBNF Synchronization Requirement").
  *
  * The target is a VALUE typed `Id(entity E)` since 2026-08-15, not a `processorRef`, and arguments
  * sit behind `with (...)` rather than bare parentheses. Both are pinned below -- `terminate` must
  * NOT accept a processor name, or the old spelling would keep parsing into an AST whose target
  * names a kind rather than an instance.
  *
  * Unlike `initiate` (a VALUE typically wrapped in a `let`), `terminate` is a bare STATEMENT
  * sitting directly in an on-clause's `contents`, so the two `terminate` occurrences are simply
  * collected from the clause's contents rather than walked out of a `let`'s expression field.
  */
class TerminateFileTest extends AnyWordSpec with Matchers {

  "terminate-statement.riddl" should {
    "parse with fastparse (parity with the EBNF grammar)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/terminate-statement.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root: Root) =>
            val domain = root.contents.toSeq.collectFirst { case d: Domain => d }.get
            val context = domain.contents.toSeq.collectFirst { case c: Context => c }.get
            val caller = context.contents.toSeq.collectFirst {
              case e: Entity if e.id.value == "Caller" => e
            }.get
            val state = caller.contents.toSeq.collectFirst { case s: State => s }.get
            val handler = state.contents.toSeq.collectFirst { case h: Handler => h }.get
            val onClause = handler.clauses.collectFirst { case oc: OnInitializationClause =>
              oc
            }.get
            val terminates = onClause.contents.toSeq.collect { case ts: TerminateStatement => ts }
            terminates.size mustBe 2

            // The target is a VALUE naming the instance -- here a `let`-bound id -- NOT a
            // processor reference. Asserting the ValueRef is the point: an `EntityRef` here
            // would mean the statement had gone back to naming a kind.
            terminates.head.target mustBe a[ValueRef]
            terminates.head.target.asInstanceOf[ValueRef].path.value mustBe Seq("orderId")
            terminates.head.args mustBe empty

            terminates(1).target mustBe a[ValueRef]
            terminates(1).target.asInstanceOf[ValueRef].path.value mustBe Seq("widgetId")
            terminates(1).args.size mustBe 1
      }
      Await.result(future, 10.seconds)
    }

    "accept `self.id` as the target" in {
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    entity Widget is {
          |      handler H is {
          |        on init { terminate self.id }
          |      } with { briefly "h" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      TopLevelParser.parseInput(RiddlParserInput(src, "self-terminate")) match
        case Left(msgs) => fail(s"`terminate self.id` must parse:\n${msgs.format}")
        case Right(root) =>
          val terminates = Finder(root).recursiveFindByType[TerminateStatement]
          terminates.size mustBe 1
          terminates.head.args mustBe empty
          terminates.head.target mustBe a[SelfValue]
          terminates.head.target.asInstanceOf[SelfValue].field.map(_.value) mustBe Some("id")
    }

    // The grammar's half of the 2026-08-15 change. Validation reports a non-`Id` target, but only
    // for targets that PARSE as values -- a bare entity NAME parses as a ValueRef and would reach
    // validation, whereas the keyword-qualified `entity Widget` spelling must not parse at all.
    "REJECT the old `terminate entity Widget` spelling" in {
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    entity Widget is {
          |      handler H is {
          |        on init { terminate entity Widget }
          |      } with { briefly "h" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      TopLevelParser.parseInput(RiddlParserInput(src, "old-terminate")) match
        case Left(_) => succeed
        case Right(root) =>
          fail(
            "`terminate entity Widget` must no longer parse -- it names a KIND, not an " +
              s"instance, and silently accepting it would keep the old meaning alive:\n$root"
          )
    }
  }
}
