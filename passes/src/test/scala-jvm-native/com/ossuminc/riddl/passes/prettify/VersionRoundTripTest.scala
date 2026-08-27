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

/** A53: `version` is permitted at root, module, domain, context and entity. RIDDL is reflective, so
  * BOTH component forms (a name and a natural number) must emit and survive a parse → prettify →
  * re-parse round-trip at the SAME scope — not dropped, not relocated.
  */
class VersionRoundTripTest extends AbstractValidatingTest {

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

  /** Named at root/domain, numeric at context/entity, and a versioned module — one model that
    * exercises every permitted scope and both component forms at once.
    */
  private val src =
    """version Jellyfish
      |domain D is {
      |  version Garibaldi
      |  context C is {
      |    version 4
      |    entity E is {
      |      version 3
      |      record R(x: Integer)
      |      state S of record D.C.E.R is { handler H is { on other is { do "a" } } }
      |    }
      |  }
      |}
      |module M is { version 9 }
      |""".stripMargin

  private def assertShape(root: Root, where: String): Unit =
    root.version.map(_.component) mustBe Some("Jellyfish")
    root.version.flatMap(_.number) mustBe None
    val finder = Finder(root)
    val d = finder.recursiveFindByType[Domain].head
    d.version.map(_.component) mustBe Some("Garibaldi")
    d.version.flatMap(_.number) mustBe None
    val c = finder.recursiveFindByType[Context].head
    c.version.map(_.component) mustBe Some("4")
    c.version.flatMap(_.number) mustBe Some(4L)
    val e = finder.recursiveFindByType[Entity].head
    e.version.map(_.component) mustBe Some("3")
    e.version.flatMap(_.number) mustBe Some(3L)
    val m = finder.recursiveFindByType[Module].head
    m.version.map(_.component) mustBe Some("9")
    m.version.flatMap(_.number) mustBe Some(9L)
    withClue(where) { succeed }

  "Version" should {
    "parse into every permitted scope's `version` accessor" in { (td: TestData) =>
      assertShape(parse(src, "src"), "original parse")
    }

    "round-trip through prettify, keeping BOTH forms at their own scope" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("version Jellyfish")
      pretty must include("version Garibaldi")
      pretty must include("version 4")
      pretty must include("version 3")
      pretty must include("version 9")
      assertShape(parse(pretty, "regen"), "after prettify")
    }
  }

  "A scope declaring more than one version" should {
    "be an Error" in { (td: TestData) =>
      val two =
        """domain D is {
          |  version 1
          |  version 2
          |  context C is { ??? }
          |}
          |""".stripMargin
      parseAndValidateDomain(RiddlParserInput(two, td), shouldFailOnErrors = false) {
        case (_, _, messages) =>
          val errs = messages.justErrors
          errs.exists(_.message.contains("at most one")) mustBe true
      }
    }

    "be fine with exactly one" in { (td: TestData) =>
      val one =
        """domain D is {
          |  version 1
          |  context C is { ??? }
          |}
          |""".stripMargin
      parseAndValidateDomain(RiddlParserInput(one, td), shouldFailOnErrors = false) {
        case (_, _, messages) =>
          messages.justErrors.exists(_.message.contains("at most one")) mustBe false
      }
    }
  }
}
