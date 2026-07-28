/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, Finder, nonEmpty, toSeq}
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

  /** The parents chain RIDDL hands passes: LEAF→ROOT. */
  private def parentsOf(root: Root, id: String): (Definition, Contents[RiddlValue]) =
    def search(
      here: Definition,
      ancestors: List[Definition]
    ): Option[(Definition, List[Definition])] =
      if here.id.value == id then Some(here -> ancestors)
      else
        here match
          case b: Branch[?] =>
            b.contents.toSeq.iterator
              .collect { case d: Definition => d }
              .map(d => search(d, here :: ancestors))
              .collectFirst { case Some(found) => found }
          case _ => None
    search(root, Nil) match
      case Some((d, ancestors)) => d -> Contents[RiddlValue](ancestors*)
      case None                 => fail(s"no definition named '$id'")

  private def composed(root: Root, id: String): Seq[String] =
    val (d, parents) = parentsOf(root, id)
    composedVersion(d, parents)

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

  // domain Garibaldi / context 4 / entity 3  =>  Garibaldi.4.3
  private val nested =
    """domain D is {
        |  version Garibaldi
        |  context C is {
        |    version 4
        |    entity E is {
        |      version 3
        |      command Cmd(x: Integer)
        |      record R(x: Integer)
        |      state S of record D.C.E.R is { handler H is { on command D.C.E.Cmd { do "x" } } }
        |    }
        |  }
        |}
        |""".stripMargin

  "Version composition" should {
    "compose every versioned ancestor root→leaf" in { (td: TestData) =>
      val root = parse(nested, td)
      composed(root, "E") mustBe Seq("Garibaldi", "4", "3")
      composedVersionString(parentsOf(root, "E")._1, parentsOf(root, "E")._2) mustBe "Garibaldi.4.3"
    }

    "give a message the composed version of its CONTAINING definition" in { (td: TestData) =>
      val root = parse(nested, td)
      // A message declares no version scope of its own, so it takes the entity's coordinate.
      composed(root, "Cmd") mustBe Seq("Garibaldi", "4", "3")
      composed(root, "R") mustBe Seq("Garibaldi", "4", "3")
    }

    "honour the MISSING-LEVEL rule (unversioned scopes contribute nothing)" in { (td: TestData) =>
      val root = parse(
        """domain D is {
          |  version 3
          |  context C is {
          |    entity E is {
          |      version 6
          |      record R(x: Integer)
          |      state S of record D.C.E.R is { handler H is { on other is { do "x" } } }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      composed(root, "E") mustBe Seq("3", "6")
      composed(root, "C") mustBe Seq("3")
    }

    "yield an empty coordinate when nothing in the chain is versioned" in { (td: TestData) =>
      val root = parse("domain D is { context C is { ??? } }", td)
      composed(root, "C") mustBe empty
      composedVersionString(parentsOf(root, "C")._1, parentsOf(root, "C")._2) mustBe ""
    }

    "work for an all-named and an all-numeric model alike" in { (td: TestData) =>
      val named = parse(
        "domain D is { version Alpha  context C is { version Beta  entity E is { version Gamma " +
          "record R(x: Integer) state S of record D.C.E.R is { handler H is { on other is { do \"x\" } } } } } }",
        td
      )
      composed(named, "E") mustBe Seq("Alpha", "Beta", "Gamma")

      val numeric = parse(
        "domain D is { version 1  context C is { version 2  entity E is { version 3 " +
          "record R(x: Integer) state S of record D.C.E.R is { handler H is { on other is { do \"x\" } } } } } }",
        td
      )
      composed(numeric, "E") mustBe Seq("1", "2", "3")
    }
  }
}
