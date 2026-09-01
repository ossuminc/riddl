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

/** RIDDL is reflective: the unified processor model (context intention, an optional ascribed shape,
  * and ports declared in any processor body) must not only parse but also prettify and re-parse to
  * the same shape. Prettify NORMALIZES the deprecated shape keywords -- AND the deprecated
  * `processor` keyword, which the input below deliberately still uses -- to `streamlet`.
  */
class ProcessorModelRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

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

  "Processor model" should {

    "round-trip intention, ascribed shape (Some and None), and ports through prettify" in {
      (td: TestData) =>
        val src =
          """domain d is {
            |  type T is String
            |  application context Orders as flow is {
            |    processor P as split is {
            |      inlet i is T
            |      outlet o1 is T
            |      outlet o2 is T
            |    }
            |    processor Q is {
            |      inlet qi is T
            |    }
            |    entity E is {
            |      inlet ei is T
            |    }
            |  }
            |}
            |""".stripMargin

        val root1 = parse(src, "src")
        val context1 = Finder(root1).recursiveFindByType[Context].head
        context1.intention mustBe Some(Intention.Application)
        context1.ascribedShape.map(_.keyword) mustBe Some("flow")

        val pretty = prettify(root1)
        // Streamlet keyword normalized to `streamlet`; the deprecated `processor` spelling the
        // input was written with, and the deprecated `split`/`flow` keywords, never lead.
        pretty must include("application context Orders as flow is")
        pretty must include("streamlet P as split is")
        pretty must include("streamlet Q is")
        pretty must not include ("split P")
        pretty must not include ("flow Orders")

        val root2 = parse(pretty, "regen")
        val context2 = Finder(root2).recursiveFindByType[Context].head
        context2.intention mustBe Some(Intention.Application)
        context2.ascribedShape.map(_.keyword) mustBe Some("flow")

        val p = Finder(root2).recursiveFindByType[Streamlet].find(_.id.value == "P").get
        p.ascribedShape.map(_.keyword) mustBe Some("split")
        p.inlets.map(_.id.value) mustBe Seq("i")
        p.outlets.map(_.id.value) mustBe Seq("o1", "o2")

        val q = Finder(root2).recursiveFindByType[Streamlet].find(_.id.value == "Q").get
        q.ascribedShape mustBe None
        q.inlets.map(_.id.value) mustBe Seq("qi")

        val e = Finder(root2).recursiveFindByType[Entity].head
        e.inlets.map(_.id.value) mustBe Seq("ei")
    }
  }
}
