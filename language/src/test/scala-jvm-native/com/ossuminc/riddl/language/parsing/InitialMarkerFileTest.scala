/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{Entity, Root}
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.{pc, ec, Await, PathUtils}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Parity guard for the `initial` marker (#14): the corpus fixture
  * `language/input/initial-marker.riddl` is validated against the EBNF grammar by the TatSu
  * validator. This proves the fastparse parser accepts the SAME file (see CLAUDE.md "Parser/EBNF
  * Synchronization Requirement").
  */
class InitialMarkerFileTest extends AnyWordSpec with Matchers {

  "initial-marker.riddl" should {
    "parse with fastparse (explicit `initial` on state and handler; parity with the EBNF)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/initial-marker.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root: Root) =>
            val e = Finder(root).recursiveFindByType[Entity].head
            e.states.find(_.id.value == "Second").get.isInitial mustBe true
            e.states.find(_.id.value == "First").get.isInitial mustBe false
      }
      Await.result(future, 10.seconds)
    }
  }
}
