/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.find.*
import com.ossuminc.riddl.commands.project.{ProjectedNode, ProjectionOutput, ProjectionPass}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `riddlc find` — Unix find over RIDDL definitions.
  *
  * Every predicate is tested in BOTH directions. That matters more than usual here: a predicate
  * that matches everything and one that works are indistinguishable until pointed at a corpus, and
  * a predicate that matches nothing looks exactly like a clean model. Three of riddl-models' nine
  * scripted defects were that second shape.
  */
class FindCommandTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  context C is {
      |    event A is { w: String(1,9) }
      |    event B is { x: String(1,9) }
      |    type Evs is one of { C.A or C.B }
      |    command Go is { g: String(1,9) }
      |    record R is { note: String(1,9)?  count: Integer }
      |    repository Repo is {
      |      inlet Feed is type C.Evs
      |      handler RH is { on other is { do "store" } }
      |    }
      |    aggregate entity Ent is {
      |      handler H is {
      |        on command C.Go is { tell event C.A(w = "y") to entity C.Ent }
      |      }
      |    }
      |    entity Plain is { ??? }
      |  }
      |}
      |""".stripMargin

  private def nodes(td: TestData): Seq[ProjectedNode] =
    var out: Seq[ProjectedNode] = Nil
    pc.withOptions(CommonOptions(showWarnings = false)) { _ =>
      Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Left(msgs) => fail(s"parse failed:\n${msgs.map(_.message).mkString("\n")}")
        case Right(result) =>
          out = Pass
            .runPass[ProjectionOutput](
              PassInput(result.root),
              PassesOutput(),
              ProjectionPass(PassInput(result.root), result.outputs)
            )
            .nodes
    }
    out

  private def find(expr: String, td: TestData): Seq[ProjectedNode] = {
    val parsed = FindExpression.parse(expr.split(" ").toSeq.filter(_.nonEmpty)) match
      case Right(p)  => p
      case Left(err) => fail(s"expression '$expr' did not parse: $err")
    val ctx = FindContext(depthOf = n => n.parents.size)
    nodes(td).filter(n => parsed.expr.matches(n, ctx))
  }

  private def paths(ns: Seq[ProjectedNode]): Seq[String] = ns.map(FindRender.identity(_))

  "the expression parser" should {
    "imply -a between adjacent terms" in { (td: TestData) =>
      find("-type entity -name Ent", td) must have size 1
    }
    "support -o, and bind it looser than the implied -a" in { (td: TestData) =>
      // `-type entity -o -type repository` is (entity) OR (repository), not entity AND (…).
      paths(find("-type entity -o -type repository", td)).sorted mustBe
        Seq("D.C.Ent", "D.C.Plain", "D.C.Repo")
    }
    "support ! negation" in { (td: TestData) =>
      val all = find("-type entity", td).size
      val negated = find("-type entity ! -name Ent", td).size
      negated mustBe all - 1
    }
    "support parentheses" in { (td: TestData) =>
      paths(find("( -type repository -o -type entity ) -name Repo", td)) mustBe Seq("D.C.Repo")
    }
    "reject an unknown test rather than silently matching everything" in { (_: TestData) =>
      FindExpression.parse(Seq("-nosuchtest", "x")).isLeft mustBe true
    }
    "reject a test that is missing its argument" in { (_: TestData) =>
      FindExpression.parse(Seq("-type")).isLeft mustBe true
    }
  }

  "-type" should {
    "match a concrete keyword" in { (td: TestData) =>
      paths(find("-type repository", td)) mustBe Seq("D.C.Repo")
    }
    "match the `statement` category, covering every statement kind" in { (td: TestData) =>
      val stmts = find("-type statement", td)
      stmts must not be empty
      stmts.foreach(n => n.value mustBe a[com.ossuminc.riddl.language.AST.Statement])
    }
    "match the `processor` category, which INCLUDES the context" in { (td: TestData) =>
      // Under the unified processor model every Processor is port-bearing -- Context, Entity,
      // Projector, Repository, Adaptor and the generic `processor` keyword alike. A context
      // appearing here is the model working, not the category being too wide, and pinning it stops
      // a later reader "fixing" the category to exclude contexts.
      paths(find("-type processor", td)).sorted mustBe
        Seq("D.C", "D.C.Ent", "D.C.Plain", "D.C.Repo")
    }
    "match nothing for a kind that is absent, rather than everything" in { (td: TestData) =>
      find("-type saga", td) mustBe empty
    }
  }

  "containment tests" should {
    "find nodes under a kind with -under-a" in { (td: TestData) =>
      paths(find("-type inlet -under-a repository", td)) mustBe Seq("D.C.Repo.Feed")
    }
    "find nothing under a kind that does not enclose them" in { (td: TestData) =>
      find("-type inlet -under-a entity", td) mustBe empty
    }
    "find nodes under a named container with -in" in { (td: TestData) =>
      find("-type entity -in D.C", td) must have size 2
      find("-type entity -in D.Nonexistent", td) mustBe empty
    }
  }

  "-carries" should {
    "see through an alternation to its members" in { (td: TestData) =>
      // `type Evs is one of { A or B }` on the inlet. Without member-level resolution this returns
      // zero against the corpus idiom -- the alternation blindness the delivery checks also had.
      paths(find("-type inlet -carries event", td)) mustBe Seq("D.C.Repo.Feed")
    }
    "not match a kind the alternation does not admit" in { (td: TestData) =>
      find("-type inlet -carries command", td) mustBe empty
    }
  }

  "-cardinality" should {
    "distinguish optional from exactly-one" in { (td: TestData) =>
      paths(find("-type field -cardinality optional", td)) mustBe Seq("D.C.R.note")
      find("-type field -cardinality exactly-one", td) must not be empty
    }
  }

  "-intention" should {
    "match a declared entity intention, and not one that is absent" in { (td: TestData) =>
      paths(find("-type entity -intention Aggregate", td)) mustBe Seq("D.C.Ent")
      find("-type entity -intention EventSourced", td) mustBe empty
    }
  }

  "-name globbing" should {
    "support * and be case-sensitive, with -iname the insensitive form" in { (td: TestData) =>
      paths(find("-type entity -name E*", td)) mustBe Seq("D.C.Ent")
      find("-type entity -name e*", td) mustBe empty
      paths(find("-type entity -iname e*", td)) mustBe Seq("D.C.Ent")
    }
  }

  "-stub" should {
    "find a `???` body" in { (td: TestData) =>
      paths(find("-type entity -stub", td)) mustBe Seq("D.C.Plain")
    }
  }

  "rendering" should {
    "give a statement an identity even though it has no path" in { (td: TestData) =>
      // A Statement is not a Definition: no id, no path. It is named by location and kind, which is
      // the only stable way to refer to one.
      val tells = find("-type tell-statement", td)
      tells must have size 1
      FindRender.identity(tells.head) must include("tell-statement")
      FindRender.location(tells.head) must include(":")
    }
    "produce a -list table with a header and one row per match" in { (td: TestData) =>
      val rows = FindRender.table(find("-type entity", td))
      rows.head must startWith("KIND")
      rows must have size (find("-type entity", td).size + 1)
    }
    "expand -printf specifiers" in { (td: TestData) =>
      val n = find("-type repository", td).head
      FindRender.printf(n, "%k %p") mustBe s"repository D.C.Repo"
    }
  }

  "-expect-min" should {
    "be parsed off the expression rather than treated as a term" in { (_: TestData) =>
      // In find, actions always succeed; treating them as terms would make
      // `-type entity -print` mean `entity AND true`, which silently changes -o behaviour.
      val parsed = FindExpression.parse(Seq("-type", "entity", "-expect-min", "3")).toOption.get
      parsed.expectMin mustBe Some(3)
    }
    "reject a non-numeric argument" in { (_: TestData) =>
      FindExpression.parse(Seq("-expect-min", "lots")).isLeft mustBe true
    }
  }
}
