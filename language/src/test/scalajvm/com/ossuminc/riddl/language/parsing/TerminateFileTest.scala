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
  * the SAME file -- both the parenthesized (with arguments) and bare (no parameters) forms -- so
  * the documented grammar and the implementation stay in sync (see CLAUDE.md "Parser/EBNF
  * Synchronization Requirement").
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
            terminates(1).args mustBe empty
      }
      Await.result(future, 10.seconds)
    }
  }
}
