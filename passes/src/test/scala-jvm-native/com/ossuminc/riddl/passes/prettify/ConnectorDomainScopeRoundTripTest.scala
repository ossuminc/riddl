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

/** RIDDL is reflective: a domain-scoped connector must not only parse and validate but also emit
  * (prettify) and re-parse back to the same shape. Guards the reflection loop for the "Connectors
  * at Domain scope" feature.
  */
class ConnectorDomainScopeRoundTripTest extends AbstractValidatingTest {

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

  "Connector at domain scope" should {

    "round-trip a domain-scoped cross-context connector through prettify" in { (td: TestData) =>
      val src =
        """domain d is {
          |  type T is { x: Integer }
          |  context a is { source src is { outlet out is type d.T } }
          |  context b is { sink snk is { inlet in is type d.T } }
          |  connector c is {
          |    from outlet d.a.src.out to inlet d.b.snk.in
          |  } with { option persistent }
          |}
          |""".stripMargin

      // Parsed: the connector is a direct child of the domain.
      val root1 = parse(src, "src")
      val domain1 = Finder(root1).recursiveFindByType[Domain].head
      domain1.connectors.map(_.id.value) mustBe Seq("c")

      // Emitted: the connector keyword survives at domain level.
      val pretty = prettify(root1)
      pretty must include("connector c is")

      // Re-parsed: still at domain scope — not dropped, not relocated into a context.
      val root2 = parse(pretty, "regen")
      val domain2 = Finder(root2).recursiveFindByType[Domain].head
      domain2.connectors.map(_.id.value) mustBe Seq("c")
      domain2.contexts.flatMap(_.connectors) mustBe empty
    }
  }
}
