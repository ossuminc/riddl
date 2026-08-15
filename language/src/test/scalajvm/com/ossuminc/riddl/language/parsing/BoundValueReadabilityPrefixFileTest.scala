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

/** Parity guard for Fix B (2026-08-15): the corpus fixture
  * `language/input/bound-value-readability-prefix.riddl` is validated against the EBNF grammar by
  * the TatSu validator (which scans every input-directory riddl file). This test proves fastparse
  * accepts the SAME file, so the documented grammar and the implementation stay in sync (see
  * CLAUDE.md "Parser/EBNF Synchronization Requirement"). Mirrors `TellByFileTest`'s style.
  */
class BoundValueReadabilityPrefixFileTest extends AnyWordSpec with Matchers {

  "bound-value-readability-prefix.riddl" should {
    "parse with fastparse (parity with the EBNF grammar)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/bound-value-readability-prefix.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root: Root) =>
            val domain = root.contents.toSeq.collectFirst { case d: Domain => d }.get
            val context = domain.contents.toSeq.collectFirst { case c: Context => c }.get
            val entityE = context.contents.toSeq.collectFirst {
              case e: Entity if e.id.value == "e" => e
            }.get
            val handler = entityE.contents.toSeq.collectFirst { case h: Handler => h }.get
            val onClause = handler.clauses.collectFirst { case oc: OnMessageClause => oc }.get
            onClause.binding.map(_.value) mustBe Some("tourCompleted")

            val tells = onClause.contents.toSeq.collect { case ts: TellStatement => ts }
            tells.size mustBe 1
            tells.head.msg mustBe a[ValueRef]

            val sends = onClause.contents.toSeq.collect { case ss: SendStatement => ss }
            sends.size mustBe 1
            sends.head.msg mustBe a[ValueRef]
      }
      Await.result(future, 10.seconds)
    }
  }
}
