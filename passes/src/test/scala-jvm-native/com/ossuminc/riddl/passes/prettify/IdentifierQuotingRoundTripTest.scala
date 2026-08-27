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

/** Verifies that special-character identifiers survive a parse -> prettify -> parse round-trip now
  * that `Identifier.format` / `PathIdentifier.format` single-quote non-bare names.
  */
class IdentifierQuotingRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** Run the prettifier (flatten) over a Root and return the rendered source. */
  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    val result = Pass.runThesePasses(PassInput(root), creators)
    result.outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  "Identifier quoting" should {

    "round-trip a user whose name has special characters (the repro)" in { (td: TestData) =>
      val src = """domain D is { user 'CI/CD Pipeline' is "an operator" }"""
      val root1 = parse(src, "src")
      Finder(root1).recursiveFindByType[User].head.id.value mustBe "CI/CD Pipeline"

      val pretty = prettify(root1)
      pretty must include("user 'CI/CD Pipeline'")

      // The prettified output must re-parse to the same identifier value.
      val root2 = parse(pretty, "regen")
      Finder(root2).recursiveFindByType[User].head.id.value mustBe "CI/CD Pipeline"
    }

    "round-trip a type whose name has special characters" in { (td: TestData) =>
      val src = """domain D is { type 'CI/CD Pipeline' is String }"""
      val pretty = prettify(parse(src, "src"))
      pretty must include("type 'CI/CD Pipeline' is")
      val types = Finder(parse(pretty, "regen")).recursiveFindByType[Type]
      types.map(_.id.value) must contain("CI/CD Pipeline")
    }

    "round-trip a path reference with a special-character component" in { (td: TestData) =>
      // A path whose middle component has a space: emitted as one quoted
      // whole path 'D.Weird Name', parsed back to the same components.
      val src =
        """domain D is {
            |  type 'Weird Name' is String
            |  type Alias is 'D.Weird Name'
            |}
            |""".stripMargin
      def aliasPath(root: Root): Seq[String] =
        Finder(root).recursiveFindByType[Type].find(_.id.value == "Alias").get.typEx match
          case ate: AliasedTypeExpression => ate.pathId.value
          case other                      => fail(s"expected AliasedTypeExpression, got $other")

      val root1 = parse(src, "src")
      aliasPath(root1) mustBe Seq("D", "Weird Name")

      val pretty = prettify(root1)
      pretty must include("'D.Weird Name'")

      aliasPath(parse(pretty, "regen")) mustBe Seq("D", "Weird Name")
    }
  }
}
