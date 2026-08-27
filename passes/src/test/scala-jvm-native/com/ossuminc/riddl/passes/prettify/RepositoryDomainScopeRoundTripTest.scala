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

/** RIDDL is reflective: a domain-scoped repository must not only parse and validate but also emit
  * (prettify) and re-parse back to the same shape. This guards the reflection loop for the
  * "Repository at Domain scope" feature.
  */
class RepositoryDomainScopeRoundTripTest extends AbstractValidatingTest {

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

  "Repository at domain scope" should {

    "round-trip a domain-scoped repository through prettify" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context a is { event AEvent is { x: String } }
          |  context b is { event BEvent is { y: String } }
          |  repository synth is {
          |    handler h is {
          |      on event a.AEvent { do "record from a" }
          |      on event b.BEvent { do "record from b" }
          |    }
          |  }
          |}
          |""".stripMargin

      // Parsed: the repository is a direct child of the domain.
      val root1 = parse(src, "src")
      val domain1 = Finder(root1).recursiveFindByType[Domain].head
      domain1.repositories.map(_.id.value) mustBe Seq("synth")

      // Emitted: the repository keyword must appear (it was not dropped).
      val pretty = prettify(root1)
      pretty must include("repository synth is")

      // Re-parsed: the repository must STILL be at domain scope — not dropped, and
      // not silently relocated into one of the contexts.
      val root2 = parse(pretty, "regen")
      val domain2 = Finder(root2).recursiveFindByType[Domain].head
      domain2.repositories.map(_.id.value) mustBe Seq("synth")
      domain2.contexts.flatMap(_.repositories) mustBe empty
    }
  }
}
