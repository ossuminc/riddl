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

/** `URL("https")` must prettify to itself. Until 2026-09-12 `RiddlFileEmitter.emitTypeExpression`
  * wrote `URL"https"` -- a second copy of `AST.URI.format` that had drifted from it -- and the
  * result did not parse (riddl-models, `2026-09-11-prettify-emits-url-scheme-without-parens.md`).
  * Both spellings, with and without a scheme, are pinned through parse -> prettify -> parse.
  */
class UrlSchemeRoundTripTest extends AbstractValidatingTest {

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

  private def model(typeEx: String): String =
    s"""domain D is {
       |  context C is {
       |    record R is { streamUrl: $typeEx } with { briefly "r" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def schemeOf(root: Root): Option[String] =
    Finder(root)
      .recursiveFindByType[Field]
      .find(_.id.value == "streamUrl")
      .map(_.typeEx)
      .collect { case u: URI => u.scheme.map(_.s) }
      .getOrElse(fail("streamUrl is not a URI in the parsed tree"))

  "a URL type with a scheme" should {

    "prettify with the scheme PARENTHESISED, as written" in { (td: TestData) =>
      val pretty = prettify(parse(model("URL(\"https\")"), td.name))
      pretty must include("URL(\"https\")")
      pretty must not include "URL\"https\""
    }

    "round-trip parse -> prettify -> parse, preserving the scheme" in { (td: TestData) =>
      val once = parse(model("URL(\"https\")"), s"${td.name}-1")
      val twice = parse(prettify(once), s"${td.name}-2")
      schemeOf(twice) mustBe Some("https")
    }
  }

  "a URL type without a scheme" should {

    "round-trip unchanged" in { (td: TestData) =>
      val once = parse(model("URL"), s"${td.name}-1")
      val pretty = prettify(once)
      pretty must include("streamUrl: URL ")
      schemeOf(parse(pretty, s"${td.name}-2")) mustBe None
    }
  }
}
