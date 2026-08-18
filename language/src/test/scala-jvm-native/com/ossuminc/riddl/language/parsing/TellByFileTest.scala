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

/** Parity guard for Task 6's `tell ... by <field>` clause: the corpus fixture
  * `language/input/tell-by-clause.riddl` is validated against the EBNF grammar by the TatSu
  * validator (which scans every input-directory riddl file). This test proves fastparse accepts the
  * SAME file, so the documented grammar and the implementation stay in sync (see CLAUDE.md
  * "Parser/EBNF Synchronization Requirement"). Mirrors `TerminateFileTest`'s style.
  */
class TellByFileTest extends AnyWordSpec with Matchers {

  "tell-by-clause.riddl" should {
    "parse with fastparse (parity with the EBNF grammar)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/tell-by-clause.riddl"))
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
            val tells = onClause.contents.toSeq.collect { case ts: TellStatement => ts }
            tells.size mustBe 1

            tells.head.processorRef mustBe a[EntityRef]
            tells.head.by.map(_.value) mustBe Some("toOrder")
      }
      Await.result(future, 10.seconds)
    }
  }
}
