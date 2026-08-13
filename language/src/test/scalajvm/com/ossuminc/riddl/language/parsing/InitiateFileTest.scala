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

/** Parity guard for Task 4's `initiate` value: the corpus fixture
  * `language/input/initiate-value.riddl` is validated against the EBNF grammar by the TatSu
  * validator (which scans every input-directory riddl file). This test proves fastparse accepts
  * the SAME file -- both the parenthesized (with arguments) and bare (no parameters) forms --
  * so the documented grammar and the implementation stay in sync (see CLAUDE.md "Parser/EBNF
  * Synchronization Requirement").
  *
  * `Finder.recursiveFindByType` does not descend into a `LetStatement`'s `expression` field (its
  * `consider` walk only descends `Container`/`When`/`Match`/`Foreach`/`SagaStep`), so this test
  * walks the two `let` statements directly instead of relying on it.
  */
class InitiateFileTest extends AnyWordSpec with Matchers {

  "initiate-value.riddl" should {
    "parse with fastparse (parity with the EBNF grammar)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/initiate-value.riddl"))
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
            val onClause = handler.clauses.collectFirst { case oc: OnMessageClause => oc }.get
            val lets = onClause.contents.toSeq.collect { case ls: LetStatement => ls }
            lets.size mustBe 2

            lets.head.expression match
              case init: Initiate =>
                init.processor mustBe a[EntityRef]
                init.args.size mustBe 1
              case other => fail(s"expected an Initiate, got $other")

            lets(1).expression match
              case init: Initiate =>
                init.processor mustBe a[EntityRef]
                init.args mustBe empty
              case other => fail(s"expected an Initiate, got $other")
      }
      Await.result(future, 10.seconds)
    }
  }
}
