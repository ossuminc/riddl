/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.project.ProjectionPass
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.commands.project.ProjectionOutput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest

/** `dump --json` — the flat, machine-readable projection.
  *
  * It exists because riddl-models' campaign scripts had no way to ask a model a structural question
  * and were re-implementing the grammar in regex: nine defects in one session, three of which
  * reported a confident number computed over nothing.
  *
  * **This is NOT the JSON round-trip surface.** `root2Json` is a reflectivity artifact whose tests
  * deliberately ignore locations; it carries neither spans nor resolved references, which are the
  * two things a query needs. The two answer different questions and are allowed to differ.
  */
class DumpProjectionTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  context C is {
      |    type Ev is one of { C.A or C.B }
      |    event A is { w: String(1,9) }
      |    event B is { x: String(1,9) }
      |    record R is { note: String(1,9)?  count: Integer  lots: String(1,9)+ }
      |    entity Ent is {
      |      inlet In is type C.Ev
      |      state S of record C.R is {
      |        handler H is {
      |          on event C.A is { tell event C.B(x = "y") to entity C.Ent }
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def project(text: String, td: TestData): Seq[ujson.Obj] =
    var out: Seq[ujson.Obj] = Nil
    pc.withOptions(CommonOptions(showWarnings = false)) { _ =>
      Riddl.parseAndValidate(RiddlParserInput(text, td), shouldFailOnError = false) match
        case Left(msgs) => fail(s"parse failed:\n${msgs.map(_.message).mkString("\n")}")
        case Right(result) =>
          val projection = Pass.runPass[ProjectionOutput](
            PassInput(result.root),
            PassesOutput(),
            ProjectionPass(PassInput(result.root), result.outputs)
          )
          out = projection.records
    }
    out

  private def ofKind(recs: Seq[ujson.Obj], kind: String): Seq[ujson.Obj] =
    recs.filter(_.value.get("kind").exists(_.str == kind))

  "the projection" should {
    "emit one record per node, with a dotted path that EXCLUDES Root" in { (td: TestData) =>
      val recs = project(src, td)
      recs must not be empty
      val entity = ofKind(recs, "entity").head
      // `Pass.traverse` pushes Root onto the parent stack, but `SymbolsPass` filters it out of the
      // symbol table -- a path including it would not match what the rest of riddlc reports.
      entity("path").str mustBe "D.C.Ent"
      entity("ancestors").arr.map(_.str) mustBe Seq("D", "D.C")
    }

    "reach statements, which no `Finder` helper does with parents" in { (td: TestData) =>
      // findWithParents walks `contents` only, so a statement is invisible to it;
      // recursiveFindByType reaches statements but returns no parents. Only Pass.traverse does both.
      val tells = ofKind(project(src, td), "tell-statement")
      tells must have size 1
      tells.head("target")("resolved").str mustBe "D.C.Ent"
    }

    "resolve references, and say so explicitly" in { (td: TestData) =>
      val inlets = ofKind(project(src, td), "inlet")
      inlets must have size 1
      val t = inlets.head("type")
      t("resolved").str mustBe "D.C.Ev"
      // The alternation's members, each resolved -- what "every inlet carrying an event" needs.
      t("alternation").arr.map(_("resolved").str) mustBe Seq("D.C.A", "D.C.B")
    }

    "emit an UNRESOLVED reference as an explicit null, never by omission" in { (td: TestData) =>
      // "absent" and "did not resolve" are different facts; conflating them is what hid nine errors
      // behind three parse aborts in riddl-models' campaign.
      val broken = src.replace("to entity C.Ent", "to entity C.Nonexistent")
      val tells = ofKind(project(broken, td), "tell-statement")
      tells must have size 1
      tells.head("target").obj must contain key "resolved"
      tells.head("target")("resolved") mustBe ujson.Null
    }

    "carry field cardinality, which is what decides between a value and `empty`" in {
      (td: TestData) =>
        val fields = ofKind(project(src, td), "field")
        def field(n: String) = fields.find(_("id").str == n).get
        field("note")("cardinality").str mustBe "optional"
        field("note")("acceptsEmpty").bool mustBe true
        field("count")("cardinality").str mustBe "exactly-one"
        field("count")("acceptsEmpty").bool mustBe false
        field("lots")("cardinality").str mustBe "one-or-more"
        field("lots")("acceptsEmpty").bool mustBe false
    }

    "carry spans that locate a node in its source file" in { (td: TestData) =>
      val entity = ofKind(project(src, td), "entity").head
      entity.obj must contain key "span"
      entity("span")("start")("line").num.toInt must be > 0
    }

    "derive a processor's shape and arity" in { (td: TestData) =>
      val entity = ofKind(project(src, td), "entity").head
      entity("arity")("inlets").num.toInt mustBe 1
      entity("shape").str mustBe "sink"
    }
  }

  "a statement record" should {

    "carry its VALUE operand, not just its target" in { (td: TestData) =>
      // Reid, 2026-08-26: "the statement should carry its value operand or it is incomplete."
      // A `set-statement` naming only `target` says a field was assigned and refuses to say what
      // it was assigned -- half the fact, and the half a consumer asking what the model DOES needs
      // most.
      val recs = project(
        """domain D is {
          |  context C is {
          |    command Take is { note: String(1,9) }
          |    entity E is {
          |      record F is { total: Integer }
          |      initial state S of record C.E.F
          |      handler H is {
          |        on command C.Take { set field S.total to 42 }
          |      }
          |    }
          |  } with { briefly "c" described as "c" }
          |} with { briefly "d" described as "d" }
          |""".stripMargin,
        td
      )
      val sets = ofKind(recs, "set-statement")
      sets must not be empty
      val set = sets.head
      set.value.get("target") mustBe defined
      withClue(ujson.write(set)) {
        // The literal is rendered as the value it IS. Consistent with the ruling that a literal is
        // not a `value-reference`: it has no referent to resolve, so it is reported rather than
        // linked.
        set.value.get("value").map(ujson.write(_)).getOrElse("") must include("42")
      }
    }

    "expose a `do` statement's prose, which is what a generator reads" in { (td: TestData) =>
      val recs = project(
        """domain D is {
          |  context C is {
          |    command Take is { note: String(1,9) }
          |    entity E is {
          |      record F is { total: Integer }
          |      initial state S of record C.E.F
          |      handler H is {
          |        on command C.Take { do { "first line" "second line" } }
          |      }
          |    }
          |  } with { briefly "c" described as "c" }
          |} with { briefly "d" described as "d" }
          |""".stripMargin,
        td
      )
      val dos = ofKind(recs, "do-statement")
      dos must not be empty
      // `.text` joins a multi-line `do` with newlines; a single-line one is a Seq of one, so this
      // is the same string it always was.
      dos.head.value.get("prose").map(_.str).getOrElse("") mustBe "first line\nsecond line"
    }

    "leave no statement kind without an operand" in { (td: TestData) =>
      // The dispatch is total over all 19 Statement kinds with no wildcard, because `commands`
      // compiles with --no-warnings: a non-exhaustive match would NOT be reported, and a new
      // statement kind would silently project with no facts. This asserts the property over
      // OUTPUT, which is what caught the equivalent gap in the rule ids.
      val recs = project(
        """domain D is {
          |  context C is {
          |    command Take is { note: String(1,9) }
          |    entity E is {
          |      record F is { total: Integer }
          |      initial state S of record C.E.F
          |      handler H is {
          |        on command C.Take {
          |          let n = 5
          |          set field S.total to n
          |          do "something"
          |          error "nope"
          |        }
          |      }
          |    }
          |  } with { briefly "c" described as "c" }
          |} with { briefly "d" described as "d" }
          |""".stripMargin,
        td
      )
      val stmts = recs.filter(_.value.get("kind").exists(_.str.endsWith("statement")))
      stmts must not be empty
      val keys = Set("value", "prose", "message", "condition", "target", "collection")
      val bare = stmts.filterNot(r => keys.exists(r.value.contains))
      withClue(bare.map(ujson.write(_)).mkString("\n")) { bare mustBe empty }
    }
  }
}
