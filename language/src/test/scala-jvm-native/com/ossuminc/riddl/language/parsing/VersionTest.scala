/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, nonEmpty}
import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.TestData

/** A53: `version` is a leaf definition establishing a version scope at root, module, domain,
  * context and entity. Its component is EITHER a name OR a natural number — never both — and the
  * precise version of any definition is COMPOSED from its versioned ancestors, root→leaf.
  */
class VersionTest extends ParsingTest {

  private def parse(src: String, td: TestData): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, td)) match
      case Right(root) => root
      case Left(msgs)  => fail(msgs.format)

  "Version parsing" should {
    "accept a NAMED component at all five scopes" in { (td: TestData) =>
      val root = parse(
        """version Jellyfish
          |domain D is {
          |  version Garibaldi
          |  context C is {
          |    version Bionic
          |    entity E is {
          |      version Xenial
          |      state S of record D.C.E.R is { handler H is { on other is { do "x" } } }
          |      record R(x: Integer)
          |    }
          |  }
          |}
          |module M is { version Focal }
          |""".stripMargin,
        td
      )
      root.version.map(_.component) mustBe Some("Jellyfish")
      root.version.map(_.isNumeric) mustBe Some(false)
      val finder = Finder(root)
      finder.recursiveFindByType[Domain].head.version.map(_.component) mustBe Some("Garibaldi")
      finder.recursiveFindByType[Context].head.version.map(_.component) mustBe Some("Bionic")
      finder.recursiveFindByType[Entity].head.version.map(_.component) mustBe Some("Xenial")
      finder.recursiveFindByType[Module].head.version.map(_.component) mustBe Some("Focal")
    }

    "accept a NUMERIC component at all five scopes" in { (td: TestData) =>
      val root = parse(
        """version 1
          |domain D is {
          |  version 3
          |  context C is {
          |    version 1
          |    entity E is {
          |      version 6
          |      state S of record D.C.E.R is { handler H is { on other is { do "x" } } }
          |      record R(x: Integer)
          |    }
          |  }
          |}
          |module M is { version 9 }
          |""".stripMargin,
        td
      )
      root.version.map(_.number) mustBe Some(Some(1L))
      root.version.map(_.component) mustBe Some("1")
      val finder = Finder(root)
      finder.recursiveFindByType[Domain].head.version.map(_.number) mustBe Some(Some(3L))
      finder.recursiveFindByType[Context].head.version.map(_.number) mustBe Some(Some(1L))
      finder.recursiveFindByType[Entity].head.version.map(_.number) mustBe Some(Some(6L))
      finder.recursiveFindByType[Module].head.version.map(_.number) mustBe Some(Some(9L))
    }

    "accept a quoted identifier as a named component" in { (td: TestData) =>
      val root = parse("domain D is { version 'Jammy Jellyfish' }", td)
      val v = Finder(root).recursiveFindByType[Domain].head.version.getOrElse(fail("no version"))
      v.component mustBe "Jammy Jellyfish"
      v.isNumeric mustBe false
    }

    "carry metadata" in { (td: TestData) =>
      val root = parse("""domain D is { version 4 with { briefly "the fourth cut" } }""", td)
      val v = Finder(root).recursiveFindByType[Domain].head.version.getOrElse(fail("no version"))
      v.metadata.nonEmpty mustBe true
    }

    "REJECT a signed number" in { (td: TestData) =>
      TopLevelParser.parseInput(RiddlParserInput("domain D is { version -3 }", td)) match
        case Right(_)   => fail("`version -3` must not parse")
        case Left(msgs) => msgs.nonEmpty mustBe true
    }

    "leave `version` usable as an ordinary field/type name" in { (td: TestData) =>
      // `version` became a keyword, but keywords are not excluded from the `identifier`
      // production, so existing models that name a field or a type `version` keep parsing.
      val root = parse(
        """domain D is {
          |  type version is String
          |  context C is { record R(version: String, x: Integer) }
          |}
          |""".stripMargin,
        td
      )
      Finder(root).recursiveFindByType[Type].map(_.id.value) must contain("version")
    }

    "REJECT a name that is not a legal identifier" in { (td: TestData) =>
      TopLevelParser.parseInput(RiddlParserInput("domain D is { version \"Jammy\" }", td)) match
        case Right(_)   => fail("a literal string is not a legal version component")
        case Left(msgs) => msgs.nonEmpty mustBe true
    }
  }
}
