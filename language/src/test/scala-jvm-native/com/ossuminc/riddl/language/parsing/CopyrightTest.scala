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

/** A47: `copyright` is a NAMED leaf definition establishing a copyright scope at [[Root]],
  * [[Module]], [[Domain]] and every [[Processor]] — Adaptor, Context, Entity, Projector, Repository
  * and Streamlet: NINE scopes in all.
  *
  * Unlike [[Version]], which COMPOSES a coordinate out of every versioned ancestor, a copyright is
  * NEAREST-WINS: the applicable notice is the one from the closest ancestor that declares one.
  */
class CopyrightTest extends ParsingTest {

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

  private def applicable(root: Root, id: String): Option[Copyright] =
    val (d, parents) = parentsOf(root, id)
    findCopyright(d, parents)

  /** One model exercising all NINE permitted scopes at once. */
  private val allNine =
    """copyright Root is "© 2026 Ossum Inc."
      |domain D is {
      |  copyright Domain is "© 2026 Ossum Inc. (domain)"
      |  context C is {
      |    copyright Context is "© 2026 Ossum Inc. (context)"
      |    entity E is {
      |      copyright Entity is "© 2026 Ossum Inc. (entity)"
      |      record R(x: Integer)
      |      state S of record D.C.E.R is { handler H is { on other is { do "x" } } }
      |    }
      |    repository Repo is {
      |      copyright Repo is "© 2026 Ossum Inc. (repository)"
      |    }
      |    projector Proj is {
      |      copyright Proj is "© 2026 Ossum Inc. (projector)"
      |    }
      |    processor Src is {
      |      copyright Streamlet is "© 2026 Ossum Inc. (streamlet)"
      |    }
      |    adaptor Ad to context D.C is {
      |      copyright Adaptor is "© 2026 Ossum Inc. (adaptor)"
      |    }
      |  }
      |}
      |module M is { copyright Module is "© 2026 Ossum Inc. (module)" }
      |""".stripMargin

  "Copyright parsing" should {
    "accept a copyright at all NINE permitted scopes" in { (td: TestData) =>
      val root = parse(allNine, td)
      root.copyright.map(_.id.value) mustBe Some("Root")
      root.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc.")
      val finder = Finder(root)
      finder.recursiveFindByType[Domain].head.copyright.map(_.id.value) mustBe Some("Domain")
      finder.recursiveFindByType[Context].head.copyright.map(_.id.value) mustBe Some("Context")
      finder.recursiveFindByType[Entity].head.copyright.map(_.id.value) mustBe Some("Entity")
      finder.recursiveFindByType[Repository].head.copyright.map(_.id.value) mustBe Some("Repo")
      finder.recursiveFindByType[Projector].head.copyright.map(_.id.value) mustBe Some("Proj")
      finder.recursiveFindByType[Streamlet].head.copyright.map(_.id.value) mustBe Some("Streamlet")
      finder.recursiveFindByType[Adaptor].head.copyright.map(_.id.value) mustBe Some("Adaptor")
      finder.recursiveFindByType[Module].head.copyright.map(_.id.value) mustBe Some("Module")
    }

    "carry the notice VERBATIM, © symbol and all" in { (td: TestData) =>
      val root =
        parse("""domain D is { copyright C is "© 2026 Ossum Inc. All rights reserved." }""", td)
      val c =
        Finder(root).recursiveFindByType[Domain].head.copyright.getOrElse(fail("no copyright"))
      c.notice mustBe "© 2026 Ossum Inc. All rights reserved."
      c.text.s mustBe "© 2026 Ossum Inc. All rights reserved."
    }

    "carry metadata" in { (td: TestData) =>
      val root = parse(
        """domain D is { copyright C is "© 2026 Ossum Inc." with { briefly "the notice" } }""",
        td
      )
      val c =
        Finder(root).recursiveFindByType[Domain].head.copyright.getOrElse(fail("no copyright"))
      c.metadata.nonEmpty mustBe true
    }

    "REQUIRE a name" in { (td: TestData) =>
      TopLevelParser.parseInput(
        RiddlParserInput("""domain D is { copyright "© 2026" }""", td)
      ) match
        case Right(_)   => fail("an unnamed copyright must not parse")
        case Left(msgs) => msgs.nonEmpty mustBe true
    }

    "REQUIRE a literal string, not an identifier" in { (td: TestData) =>
      TopLevelParser.parseInput(RiddlParserInput("domain D is { copyright C is Ossum }", td)) match
        case Right(_)   => fail("an unquoted notice must not parse")
        case Left(msgs) => msgs.nonEmpty mustBe true
    }

    "leave `copyright` usable as an ordinary field/type name" in { (td: TestData) =>
      // `copyright` became a keyword, but keywords are not excluded from the `identifier`
      // production, so existing models that name a field or a type `copyright` keep parsing.
      val root = parse(
        """domain D is {
          |  type copyright is String
          |  context C is { record R(copyright: String, x: Integer) }
          |}
          |""".stripMargin,
        td
      )
      Finder(root).recursiveFindByType[Type].map(_.id.value) must contain("copyright")
    }
  }

  "Copyright inheritance" should {
    "be NEAREST-WINS, not composed" in { (td: TestData) =>
      val root = parse(allNine, td)
      // The entity declares its own, so its own wins over the context's and the domain's.
      applicable(root, "E").map(_.id.value) mustBe Some("Entity")
      // The state inside it declares none, so it takes the entity's — the NEAREST declaring
      // ancestor — and NOT an accumulation of every ancestor's.
      applicable(root, "S").map(_.id.value) mustBe Some("Entity")
      // A definition in the context but outside the entity takes the context's.
      applicable(root, "Repo").map(_.id.value) mustBe Some("Repo")
    }

    "let an `external context` OVERRIDE its enclosing domain for everything inside it" in {
      (td: TestData) =>
        val root = parse(
          """domain D is {
            |  copyright Ours is "© 2026 Ossum Inc."
            |  external context Legacy is {
            |    copyright Theirs is "© 1998 Legacy Systems Inc."
            |    command Migrate(id: Integer)
            |  }
            |  context Mine is {
            |    command Local(id: Integer)
            |  }
            |}
            |""".stripMargin,
          td
        )
        // Inside the external context the FOREIGN notice applies — not the domain's.
        applicable(root, "Legacy").map(_.notice) mustBe Some("© 1998 Legacy Systems Inc.")
        applicable(root, "Migrate").map(_.notice) mustBe Some("© 1998 Legacy Systems Inc.")
        // A sibling context that declares nothing still inherits the domain's.
        applicable(root, "Local").map(_.notice) mustBe Some("© 2026 Ossum Inc.")
    }

    "yield None when no ancestor declares one" in { (td: TestData) =>
      val root = parse("""domain D is { context C is { command Nothing(id: Integer) } }""", td)
      applicable(root, "Nothing") mustBe None
      applicable(root, "D") mustBe None
    }

    "reach all the way to the root when only the root declares one" in { (td: TestData) =>
      val root = parse(
        """copyright House is "© 2026 Ossum Inc."
          |domain D is { context C is { command Anything(id: Integer) } }
          |""".stripMargin,
        td
      )
      applicable(root, "Anything").map(_.id.value) mustBe Some("House")
    }
  }

  "Version, widened by A47" should {
    "parse on an Adaptor, Projector, Repository and Streamlet" in { (td: TestData) =>
      val root = parse(
        """domain D is {
          |  context C is {
          |    repository Repo is { version 2 }
          |    projector Proj is { version 1 }
          |    processor Src is { version 5 }
          |    adaptor Ad to context D.C is { version 7 }
          |  }
          |}
          |""".stripMargin,
        td
      )
      val finder = Finder(root)
      finder.recursiveFindByType[Repository].head.version.flatMap(_.number) mustBe Some(2L)
      finder.recursiveFindByType[Projector].head.version.flatMap(_.number) mustBe Some(1L)
      finder.recursiveFindByType[Streamlet].head.version.flatMap(_.number) mustBe Some(5L)
      finder.recursiveFindByType[Adaptor].head.version.flatMap(_.number) mustBe Some(7L)
    }

    "COMPOSE through the newly admitted processor scopes" in { (td: TestData) =>
      val root = parse(
        """version Jellyfish
          |domain D is {
          |  version Garibaldi
          |  context C is {
          |    version 4
          |    repository Repo is {
          |      version 2
          |      record Rec(id: Integer)
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      val (repo, repoParents) = parentsOf(root, "Repo")
      composedVersionString(repo, repoParents) mustBe "Jellyfish.Garibaldi.4.2"
      val (rec, recParents) = parentsOf(root, "Rec")
      composedVersionString(rec, recParents) mustBe "Jellyfish.Garibaldi.4.2"
    }
  }
}
