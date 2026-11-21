/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** riddl-models, 2026-11-21: a keyword-led value OPERAND (`query`, `prompt`, `call`, `ask`,
  * `create`, a constructor) is not an atom of the expression ladder, so as the operand of an
  * operator it needs its parentheses. prettify dropped them, and `any of (query T) as r where p`
  * came back as text that does not parse.
  */
class QueryOperandParenthesesRoundTripTest extends AnyWordSpec with Matchers {

  private val src =
    """domain D is {
      |  context C is {
      |    record Row is { code: String, ready: Boolean } with { briefly "r" }
      |    query Find is { code: String } with { briefly "q" }
      |    repository Store is {
      |      schema S is relational of rows as record C.Row with { briefly "s" }
      |      inlet Queries is query C.Find with { briefly "i" }
      |      handler H is {
      |        on f: query C.Find is {
      |          let a = any of (query S.rows) as r where r.code == f.code
      |          let b = all of (query S.rows where ready == true) as r where r.ready
      |          let c = none of (query one S.rows) as r where r.ready
      |          let d = count of (query S.rows)
      |          let e = count of (query S.rows) > 0
      |          let g = (prompt("how many") as Integer) > 0
      |        }
      |      } with { briefly "h" }
      |    } with { briefly "s" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}\n--- text ---\n$text")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("no prettify output"))
      .state
      .filesAsString

  private def shapes(root: Container[?]): Seq[String] =
    Finder(root.contents).recursiveFindByType[LetStatement].map(_.expression.format)

  private val expected = Seq(
    "any of (query S.rows) as r where r.code == f.code",
    "all of (query S.rows where ready == true) as r where r.ready",
    "none of (query one S.rows) as r where r.ready",
    "count of (query S.rows)",
    "count of (query S.rows) > 0",
    "(prompt(\"how many\") as Integer) > 0"
  )

  "a keyword-led operand" should {
    "keep its parentheses in the AST's format" in {
      shapes(parse(src, "original")) mustBe expected
    }
    "survive prettify: the output re-parses to the same trees" in {
      val text = prettify(parse(src, "original"))
      expected.foreach(e => text must include(e))
      shapes(parse(text, "prettified")) mustBe expected
    }
    "reach a prettify fixed point" in {
      val once = prettify(parse(src, "original"))
      prettify(parse(once, "once")) mustBe once
    }
  }
}
