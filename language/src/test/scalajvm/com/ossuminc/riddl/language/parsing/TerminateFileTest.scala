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

/** Parity guard for Task 5's `terminate` statement: the corpus fixture
  * `language/input/terminate-statement.riddl` is validated against the EBNF grammar by the TatSu
  * validator (which scans every input-directory riddl file). This test proves fastparse accepts
  * the SAME file, so the documented grammar and the implementation stay in sync (see CLAUDE.md
  * "Parser/EBNF Synchronization Requirement").
  *
  * The parentheses are MANDATORY as of the final review of the instance-identity branch: the bare
  * `terminate P` spelling parsed but could never validate (`on term`'s leading Id(...) parameter is
  * required), so it was dead syntax. The rejection is pinned below.
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
            val onClause = handler.clauses.collectFirst { case oc: OnInitializationClause => oc }.get
            val terminates = onClause.contents.toSeq.collect { case ts: TerminateStatement => ts }
            terminates.size mustBe 2

            terminates.head.processor mustBe a[EntityRef]
            terminates.head.args.size mustBe 1

            terminates(1).processor mustBe a[EntityRef]
            terminates(1).args.size mustBe 2
      }
      Await.result(future, 10.seconds)
    }

    // Inverted 2026-08-14. The bare form was rejected because `on term`'s leading `Id(...)`
    // parameter was required, so a no-argument `terminate` could never satisfy the arity check --
    // it parsed to something that always failed validation. Reid dropped the requirement (`self.id`
    // already names the instance being terminated), which left nothing justifying the asymmetry
    // with `initiate P`, so the bare form is legal again and is the canonical spelling for the
    // argumentless case.
    "ACCEPT the bare `terminate P` form (no parentheses)" in {
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
      TopLevelParser.parseInput(RiddlParserInput(src, "bare-terminate")) match
        case Left(msgs) => fail(s"the bare `terminate P` form must parse:\n${msgs.format}")
        case Right(root) =>
          // Not merely "it parses" -- it must produce a TerminateStatement with an EMPTY argument
          // list, the same AST `terminate Widget()` produces. A bare form that parsed to something
          // else would be a second shape for validation and every downstream walk to handle.
          val terminates = Finder(root).recursiveFindByType[TerminateStatement]
          terminates.size mustBe 1
          terminates.head.args mustBe empty
          terminates.head.processor mustBe a[EntityRef]
    }
  }
}
