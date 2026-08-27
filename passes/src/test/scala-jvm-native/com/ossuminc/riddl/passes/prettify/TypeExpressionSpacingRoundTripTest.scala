/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** Two type expressions were emitted with the wrong separators.
  *
  *   - `table of T of [ d… ]` lost its SECOND `of`, emitting `table of T[ d… ]`. That is not a
  *     cosmetic defect: the output is a hard parse error (`Expected one of ("(" | "*" | "+" | "." |
  *     "?" | "of" | "|")`), so prettify produced source that riddlc itself rejects.
  *   - `replica of T` lost the space after `of`, emitting `replica ofT`. This one reparses, so it
  *     is ugly rather than lossy — but it is the same missing-separator bug and is fixed together.
  *
  * Both are asserted by RE-PARSING the prettified output rather than by matching strings, because
  * the defect that matters is unparseability, and a string assertion would pass against output that
  * still failed to parse for some other reason.
  *
  * Reported by riddl-models (`task/2026-08-14-prettify-emitter-drops-method-and-shown-by.md`).
  */
class TypeExpressionSpacingRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

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
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain Dom is {
      |  type Tbl is table of String of [ 4, 4 ]
      |  type Rep is replica of Integer
      |}
      |""".stripMargin

  private def typeExprNamed(root: Root, name: String): TypeExpression =
    Finder(root)
      .recursiveFindByType[Type]
      .find(_.id.value == name)
      .getOrElse(fail(s"no type named $name was parsed"))
      .typEx

  "a `table of` type expression" should {

    "keep both `of` keywords so the emitted source re-parses" in { (_: TestData) =>
      val pretty = prettify(parse(src, "spacing"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("table of String of [")

        val again = parse(pretty, "regen")
        typeExprNamed(again, "Tbl") match
          case t: Table => t.dimensions mustBe Seq(4, 4)
          case other    => fail(s"Tbl came back as ${other.getClass.getSimpleName}")
      }
    }
  }

  "a `replica of` type expression" should {

    "keep the space after `of`" in { (_: TestData) =>
      val pretty = prettify(parse(src, "spacing"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("replica of Integer")

        val again = parse(pretty, "regen")
        typeExprNamed(again, "Rep") mustBe a[Replica]
      }
    }
  }
}
