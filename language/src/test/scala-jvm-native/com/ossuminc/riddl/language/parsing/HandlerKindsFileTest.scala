/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{
  Root,
  OnActivationClause,
  OnPassivationClause,
  OnEventClause
}
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.{pc, ec, Await, PathUtils}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Parity guard for the RIDDL 2.0 handler-kind syntax: the corpus fixture
  * `language/input/handler-kinds.riddl` is validated against the EBNF grammar by the TatSu
  * validator (which scans every input-directory riddl file). This test proves fastparse accepts the
  * SAME file, so the documented grammar and the implementation stay in sync (see CLAUDE.md
  * "Parser/EBNF Synchronization Requirement").
  */
class HandlerKindsFileTest extends AnyWordSpec with Matchers {

  "handler-kinds.riddl" should {
    "parse with fastparse (parity with the EBNF grammar)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/handler-kinds.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root: Root) =>
            val finder = Finder(root)
            finder.recursiveFindByType[OnActivationClause].size mustBe 1
            finder.recursiveFindByType[OnPassivationClause].size mustBe 1
            // one in the entity, one in the projector, one in the adaptor
            finder.recursiveFindByType[OnEventClause].size mustBe 3
      }
      Await.result(future, 10.seconds)
    }
  }
}
