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

/** RIDDL is reflective: A38's refusal interaction step (`<source> refuses <user> "<reason>"`) must
  * emit (prettify) and re-parse to the same shape — same from ref, same user, same reason.
  */
class RefusalRoundTripTest extends AbstractValidatingTest {

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
    """domain ImprovingApp is {
      |  context OrganizationContext is {
      |    entity Organization is { ??? }
      |  }
      |  user Owner is "a person"
      |  epic EstablishOrganization is {
      |    user ImprovingApp.Owner wants "to establish an organization" so that "business happens"
      |    case primary is {
      |      user ImprovingApp.Owner wants "to incorporate" so that "it can be used"
      |      step entity ImprovingApp.OrganizationContext.Organization
      |        refuses user ImprovingApp.Owner "not authorized"
      |    }
      |  }
      |}
      |""".stripMargin

  "refusal interaction step" should {
    "round-trip a refusal step through prettify preserving source/user/reason" in {
      (td: TestData) =>
        val pretty = prettify(parse(src, "src"))
        pretty must include("refuses")
        pretty must include("not authorized")

        val refusals = Finder(parse(pretty, "regen")).recursiveFindByType[RefusalInteraction]
        refusals.size mustBe 1
        val r = refusals.head
        r.from.pathId.value mustBe Seq("ImprovingApp", "OrganizationContext", "Organization")
        r.to.pathId.value mustBe Seq("ImprovingApp", "Owner")
        r.reason.s mustBe "not authorized"
    }
  }
}
