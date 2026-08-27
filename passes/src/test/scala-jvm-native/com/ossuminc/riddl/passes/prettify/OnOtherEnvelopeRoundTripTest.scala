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

/** A57: `on other as x [: <envelope>]` must survive prettify → re-parse in BOTH forms.
  *
  * This is a regression test with a specific history: the first implementation put the rendering on
  * `OnOtherClause.format` and the prettifier silently DROPPED it, emitting a bare `on other is {`.
  * The fix moved it to `Declaration.ascription`, the one implementation `openDef` and `format` both
  * read. So the assertions below are about the emitted TEXT, not just about the AST surviving.
  */
class OnOtherEnvelopeRoundTripTest extends AbstractValidatingTest {

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

  private def model(clause: String): String =
    s"""domain D is {
       |  context C is {
       |    command Ping is { note: String }
       |    entity E is {
       |      handler H is {
       |        on command D.C.Ping is { do "handle" }
       |        $clause
       |      }
       |    }
       |  } with { option message_envelope("Riddl.Envelope") }
       |}
       |""".stripMargin

  private def otherClause(root: Root): OnOtherClause =
    Finder(root)
      .recursiveFindByType[OnOtherClause]
      .headOption
      .getOrElse(fail("no OnOtherClause found"))

  "A57 on-other envelope binding" should {

    "round-trip the bare binding form" in { (td: TestData) =>
      val root1 = parse(model("""on other as env is { do "log it" }"""), "src")
      otherClause(root1).binding.map(_.value) mustBe Some("env")
      otherClause(root1).envelopeType mustBe None

      val pretty = prettify(root1)
      pretty must include("on other as env is")

      val root2 = parse(pretty, "regen")
      otherClause(root2).binding.map(_.value) mustBe Some("env")
      otherClause(root2).envelopeType mustBe None
    }

    "round-trip the ascribed form, keeping the type" in { (td: TestData) =>
      val root1 = parse(model("""on other as env: Riddl.Envelope is { do "log it" }"""), "src")
      otherClause(root1).binding.map(_.value) mustBe Some("env")
      otherClause(root1).envelopeType.map(_.pathId.format) mustBe Some("Riddl.Envelope")

      val pretty = prettify(root1)
      pretty must include("on other as env: Riddl.Envelope is")

      val root2 = parse(pretty, "regen")
      otherClause(root2).binding.map(_.value) mustBe Some("env")
      otherClause(root2).envelopeType.map(_.pathId.format) mustBe Some("Riddl.Envelope")
    }

    "leave a plain `on other` emitting exactly what it did before A57" in { (td: TestData) =>
      val root1 = parse(model("""on other is { do "ignore" }"""), "src")
      otherClause(root1).binding mustBe None
      val pretty = prettify(root1)
      pretty must include("on other is")
      // No stray `as` from an absent binding.
      pretty must not include ("on other as")
      otherClause(parse(pretty, "regen")).binding mustBe None
    }
  }
}
