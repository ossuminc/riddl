/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{At, Finder, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** `Anything` is the DUAL of `Nothing`: it absorbs (and is absorbed by) every other type. It
  * replaces the old `Abstract` spelling, which still parses but is deprecated. RIDDL is reflective,
  * so `Anything` must also survive prettify and re-parse.
  */
class AnythingTypeTest extends AbstractValidatingTest {

  private def model(typeExpr: String): String =
    s"""domain D is {
       |  type Whatever is $typeExpr
       |}
       |""".stripMargin

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass
      .runThesePasses(PassInput(root), creators)
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def theTypeExpression(root: Root): TypeExpression =
    Finder(root).recursiveFindByType[Type].find(_.id.value == "Whatever") match
      case Some(t) => t.typEx
      case None    => fail("the `Whatever` type was not found")

  "Anything" must {

    "parse and validate cleanly with no deprecation" in { (td: TestData) =>
      val rpi = RiddlParserInput(model("Anything"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          result.messages.justErrors mustBe empty
          result.messages.justDeprecations mustBe empty
    }

    "round-trip through prettify as `Anything`" in { (td: TestData) =>
      val root1 = parse(model("Anything"), "anything-src")
      theTypeExpression(root1) mustBe a[Anything]
      val pretty = prettify(root1)
      pretty must include("Anything")
      pretty mustNot include("Abstract")
      theTypeExpression(parse(pretty, "anything-regen")) mustBe a[Anything]
    }

    "be assignment compatible with an arbitrary other type in BOTH directions" in {
      (td: TestData) =>
        val anything = Anything(At.empty)
        val other: TypeExpression = String_(At.empty)
        anything.isAssignmentCompatible(other) mustBe true
        other.isAssignmentCompatible(anything) mustBe true
        // ...and Nothing, its dual, remains incompatible in the `Nothing`-receives direction.
        Nothing(At.empty).isAssignmentCompatible(anything) mustBe false
    }
  }

  "the deprecated `Abstract` spelling" must {

    "still parse, yield an Anything node, and emit exactly ONE deprecation" in { (td: TestData) =>
      val rpi = RiddlParserInput(model("Abstract"), td)
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          result.messages.justErrors mustBe empty
          val deprecations = result.messages.justDeprecations
          deprecations.size mustBe 1
          deprecations.head.message must include("`Abstract`")
          deprecations.head.message must include("`Anything`")
    }

    "yield the same Anything node and prettify back out as `Anything`" in { (td: TestData) =>
      val root1 = parse(model("Abstract"), "abstract-src")
      theTypeExpression(root1) mustBe a[Anything]
      val pretty = prettify(root1)
      pretty must include("Anything")
      pretty mustNot include("Abstract")
      theTypeExpression(parse(pretty, "abstract-regen")) mustBe a[Anything]
    }
  }
}
