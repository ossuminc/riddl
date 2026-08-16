/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{Root, OnInitializationClause, OnTerminationClause, UniqueId}
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.{pc, ec, Await, PathUtils}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Parity guard for Task 3's `on init`/`on term` parameter lists: the corpus fixture
  * `language/input/lifecycle-parameters.riddl` is validated against the EBNF grammar by the
  * TatSu validator (which scans every input-directory riddl file). This test proves fastparse
  * accepts the SAME file, so the documented grammar and the implementation stay in sync (see
  * CLAUDE.md "Parser/EBNF Synchronization Requirement").
  */
class LifecycleParametersFileTest extends AnyWordSpec with Matchers {

  "lifecycle-parameters.riddl" should {
    "parse with fastparse (parity with the EBNF grammar)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/lifecycle-parameters.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root: Root) =>
            val finder = Finder(root)
            val inits = finder.recursiveFindByType[OnInitializationClause]
            inits.size mustBe 1
            // The names deliberately do NOT collide with the state record's fields -- see the
            // fixture's own header for why a colliding name hid a shipped defect.
            inits.head.parameters.map(_.name) mustBe Seq("seed", "buyer")

            val terms = finder.recursiveFindByType[OnTerminationClause]
            terms.size mustBe 1
            terms.head.parameters.map(_.name) mustBe Seq("oid", "reason")
            terms.head.parameters.head.typeEx mustBe a[UniqueId]
      }
      Await.result(future, 10.seconds)
    }
  }
}
