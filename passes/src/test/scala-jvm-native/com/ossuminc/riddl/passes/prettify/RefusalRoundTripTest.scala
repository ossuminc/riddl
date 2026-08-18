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
  *
  * A38, 2026-08-17: the reason may now also NAME the invariant the request violates, which is what
  * closes a use-case step to the `require invariant X` it describes. Both spellings round-trip and
  * neither converges to the other — unlike `!`/`not`, these are two different facts, not two
  * spellings of one. A prose refusal is the honest form when the handler refuses with `error`, and
  * there is no invariant to name.
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

  private val invariantSrc =
    """domain ImprovingApp is {
      |  context OrganizationContext is {
      |    entity Organization is {
      |      invariant MustBeAuthorized is "the requester holds an owner role"
      |      handler H is { on other is { ??? } }
      |    }
      |  }
      |  user Owner is "a person"
      |  epic EstablishOrganization is {
      |    user ImprovingApp.Owner wants "to establish an organization" so that "business happens"
      |    case primary is {
      |      user ImprovingApp.Owner wants "to incorporate" so that "it can be used"
      |      step entity ImprovingApp.OrganizationContext.Organization
      |        refuses user ImprovingApp.Owner
      |        invariant ImprovingApp.OrganizationContext.Organization.MustBeAuthorized
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
        r.reason mustBe a[LiteralString]
        r.reason.asInstanceOf[LiteralString].s mustBe "not authorized"
    }

    "round-trip a refusal step whose reason NAMES an invariant" in { (td: TestData) =>
      val pretty = prettify(parse(invariantSrc, "src"))
      pretty must include("refuses")
      // Emitted as source, not as a quoted string — a path in quotes would re-parse as prose.
      pretty must include(
        "invariant ImprovingApp.OrganizationContext.Organization.MustBeAuthorized"
      )

      val refusals = Finder(parse(pretty, "regen")).recursiveFindByType[RefusalInteraction]
      refusals.size mustBe 1
      val r = refusals.head
      r.from.pathId.value mustBe Seq("ImprovingApp", "OrganizationContext", "Organization")
      r.to.pathId.value mustBe Seq("ImprovingApp", "Owner")
      r.reason mustBe a[InvariantRef]
      r.reason.asInstanceOf[InvariantRef].pathId.value mustBe
        Seq("ImprovingApp", "OrganizationContext", "Organization", "MustBeAuthorized")
    }

    /* The two forms must stay DISTINCT through the round trip. Prose that happens to look like a
     * path must not become an invariant reference, and vice versa — this is the failure the two
     * separate JSON keys and the BAST discriminator exist to prevent. */
    "keep prose that LOOKS like a path as prose" in { (td: TestData) =>
      val prose = src.replace(
        """"not authorized"""",
        """"ImprovingApp.OrganizationContext.Organization.MustBeAuthorized""""
      )
      val refusals =
        Finder(parse(prettify(parse(prose, "src")), "regen"))
          .recursiveFindByType[RefusalInteraction]
      refusals.size mustBe 1
      refusals.head.reason mustBe a[LiteralString]
    }

    "report an invariant reason that names nothing" in { (td: TestData) =>
      val bad = invariantSrc.replace("MustBeAuthorized\n", "NoSuchInvariant\n")
      parseAndValidateInput(RiddlParserInput(bad, "bad"), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          withClue(s"messages were:\n${msgs.format}\n") {
            msgs.justErrors.exists(_.message.contains("NoSuchInvariant")) mustBe true
          }
      }
    }
  }
}
