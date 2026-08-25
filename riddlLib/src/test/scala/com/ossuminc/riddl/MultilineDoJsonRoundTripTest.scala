/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Multi-line `do` / `prompt(...)` across JSON, the fourth serialization surface.
  *
  * The JSON-identity fixed point is the strong assertion, but it is NOT sufficient on its own here:
  * a shape that joined the lines into one string would still be a perfect fixed point while having
  * destroyed the line structure. So the line COUNT is asserted too.
  *
  * The single-line cases pin the additive guarantee on the wire: `"text": "..."` stays a bare
  * string, so the corpus's 190 models serialize byte-identically to before.
  */
// NOTE: a plain AnyWordSpec, so cases take NO `(td: TestData)` parameter -- writing one would
// construct a Function1 and never evaluate the body, a silently passing test.
class MultilineDoJsonRoundTripTest extends AnyWordSpec with Matchers {

  private def model(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    handler H is {
       |      on init {
       |        $stmt
       |      }
       |    }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def roundTripped(src: String): (Root, String) =
    RiddlLib.parseString(src) match
      case RiddlResult.Success(root0) =>
        val json1 = RiddlLib.root2Json(root0)
        RiddlLib.parseJson(json1) match
          case RiddlResult.Success(root1) =>
            withClue("JSON is not an identity fixed point: ") {
              RiddlLib.root2Json(root1) mustBe json1
            }
            (root1, json1)
          case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
      case RiddlResult.Failure(errors) => fail(s"parse failed: $errors")

  private def dos(root: Root): Seq[DoStatement] =
    Finder(root).recursiveFindByType[DoStatement].toSeq

  private def prompts(root: Root): Seq[PromptValue] =
    Finder(root).recursiveFindByType[PromptValue].toSeq

  "a multi-line do" should {
    "keep every line through a JSON round trip" in {
      val (root, _) = roundTripped(model("""do { "first" "second" "third" }"""))
      dos(root).head.what.map(_.s) mustBe Seq("first", "second", "third")
    }

    "serialize its lines as an array" in {
      val (_, json) = roundTripped(model("""do { "a" "b" }"""))
      json must include("\"text\"")
      // An array, not a joined string: a join would be a perfect fixed point and still wrong.
      json mustNot include("a\\nb")
    }
  }

  "a single-line do" should {
    "still serialize as a bare string, so existing models' JSON does not move" in {
      val (root, json) = roundTripped(model("""do "only one""""))
      dos(root).head.what.map(_.s) mustBe Seq("only one")
      json must include("\"text\": \"only one\"")
    }
  }

  "a multi-line prompt value" should {
    "keep every line, and its ascription, through a JSON round trip" in {
      val (root, _) = roundTripped(model("""let x = prompt({ "one" "two" }) as Real"""))
      val pv = prompts(root).head
      pv.prompt.map(_.s) mustBe Seq("one", "two")
      pv.typeEx mustBe defined
    }
  }
}
