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

/** A47: `copyright` is permitted at root, module, domain and all six processors, and `version` was
  * widened to the same set. RIDDL is reflective, so both must emit and survive a parse → prettify →
  * re-parse round-trip at the SAME scope — not dropped, not relocated.
  */
class CopyrightRoundTripTest extends AbstractValidatingTest {

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

  /** One model bearing a copyright AND a version at every one of the nine permitted scopes. */
  private val src =
    """copyright Root is "© 2026 Ossum Inc."
      |version Jellyfish
      |domain D is {
      |  copyright Domain is "© 2026 Ossum Inc. (domain)"
      |  version Garibaldi
      |  context C is {
      |    copyright Context is "© 2026 Ossum Inc. (context)"
      |    version 4
      |    command Ping(at: TimeStamp)
      |    entity E is {
      |      copyright Entity is "© 2026 Ossum Inc. (entity)"
      |      version 3
      |      record R(x: Integer)
      |      state S of record D.C.E.R is { handler H is { on other is { do "a" } } }
      |    }
      |    repository Repo is {
      |      copyright Repository is "© 2026 Third Party Ltd."
      |      version 2
      |    }
      |    projector Proj is {
      |      copyright Projector is "© 2026 Ossum Inc. (projector)"
      |      version 1
      |    }
      |    processor Src as source is {
      |      copyright Streamlet is "© 2026 Ossum Inc. (streamlet)"
      |      version 5
      |      outlet Out is type D.C.Ping
      |    }
      |    adaptor Ad to context D.C is {
      |      copyright Adaptor is "© 1998 Legacy Systems Inc."
      |      version 7
      |    }
      |  }
      |}
      |module M is {
      |  copyright Module is "© 2026 Ossum Inc. (module)"
      |  version 9
      |}
      |""".stripMargin

  private def assertShape(root: Root, where: String): Unit =
    root.copyright.map(_.id.value) mustBe Some("Root")
    root.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc.")
    root.version.map(_.component) mustBe Some("Jellyfish")
    val finder = Finder(root)
    val d = finder.recursiveFindByType[Domain].head
    d.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc. (domain)")
    d.version.map(_.component) mustBe Some("Garibaldi")
    val c = finder.recursiveFindByType[Context].head
    c.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc. (context)")
    c.version.flatMap(_.number) mustBe Some(4L)
    val e = finder.recursiveFindByType[Entity].head
    e.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc. (entity)")
    e.version.flatMap(_.number) mustBe Some(3L)
    val r = finder.recursiveFindByType[Repository].head
    r.copyright.map(_.notice) mustBe Some("© 2026 Third Party Ltd.")
    r.version.flatMap(_.number) mustBe Some(2L)
    val p = finder.recursiveFindByType[Projector].head
    p.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc. (projector)")
    p.version.flatMap(_.number) mustBe Some(1L)
    val s = finder.recursiveFindByType[Streamlet].head
    s.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc. (streamlet)")
    s.version.flatMap(_.number) mustBe Some(5L)
    val a = finder.recursiveFindByType[Adaptor].head
    a.copyright.map(_.notice) mustBe Some("© 1998 Legacy Systems Inc.")
    a.version.flatMap(_.number) mustBe Some(7L)
    val m = finder.recursiveFindByType[Module].head
    m.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc. (module)")
    m.version.flatMap(_.number) mustBe Some(9L)
    withClue(where) { succeed }

  "Copyright (and the widened Version)" should {
    "parse into every permitted scope's accessor" in { (td: TestData) =>
      assertShape(parse(src, "src"), "original parse")
    }

    "round-trip through prettify, keeping both leaves at their own scope" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("""copyright Root is "© 2026 Ossum Inc."""")
      pretty must include("""copyright Adaptor is "© 1998 Legacy Systems Inc."""")
      pretty must include("version 7")
      assertShape(parse(pretty, "regen"), "after prettify")
    }
  }

  "A scope declaring more than one copyright" should {
    "be an Error" in { (td: TestData) =>
      val two =
        """domain D is {
          |  copyright A is "© 2026 Ossum Inc."
          |  copyright B is "© 2026 Someone Else"
          |  context C is { ??? }
          |}
          |""".stripMargin
      parseAndValidateDomain(RiddlParserInput(two, td), shouldFailOnErrors = false) {
        case (_, _, messages) =>
          messages.justErrors.exists(_.message.contains("at most one")) mustBe true
      }
    }

    "be fine with exactly one, at a processor scope too" in { (td: TestData) =>
      val one =
        """domain D is {
          |  copyright A is "© 2026 Ossum Inc."
          |  context C is { copyright B is "© 2026 Someone Else" }
          |}
          |""".stripMargin
      parseAndValidateDomain(RiddlParserInput(one, td), shouldFailOnErrors = false) {
        case (_, _, messages) =>
          messages.justErrors.exists(_.message.contains("at most one")) mustBe false
      }
    }
  }
}
