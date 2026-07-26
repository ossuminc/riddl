/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{Aggregation, Function, Root, TypeRef}
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.{pc, ec, Await, PathUtils}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Parity guard for A9 named-type `requires`/`returns`: the corpus fixture
  * `language/input/requires-returns-ref.riddl` is validated against the EBNF grammar by the TatSu
  * validator. This proves the fastparse parser accepts the SAME file (see CLAUDE.md "Parser/EBNF
  * Synchronization Requirement").
  */
class RequiresReturnsRefFileTest extends AnyWordSpec with Matchers {

  "requires-returns-ref.riddl" should {
    "parse with fastparse (type-ref and deprecated inline requires/returns; parity with the EBNF)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/requires-returns-ref.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root: Root) =>
            val funcs = Finder(root).recursiveFindByType[Function]
            def byName(n: String): Function = funcs.find(_.id.value == n).get

            // Bare type-alias reference -> TypeRef
            byName("Unary").input.get mustBe a[TypeRef]
            byName("Unary").input.get.asInstanceOf[TypeRef].pathId.format mustBe "Age"

            // Keyworded record reference -> TypeRef with the `record` keyword
            val wr = byName("WithRecord").input.get.asInstanceOf[TypeRef]
            wr.keyword mustBe "record"
            wr.pathId.format mustBe "Args"

            // Deprecated inline aggregation still parses -> Aggregation
            byName("LegacyInline").input.get mustBe a[Aggregation]
      }
      Await.result(future, 10.seconds)
    }
  }
}
