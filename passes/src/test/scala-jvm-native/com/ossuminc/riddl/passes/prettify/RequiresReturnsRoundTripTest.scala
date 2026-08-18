/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** RIDDL is reflective: A9's named-type `requires`/`returns` (a `TypeRef`) and the deprecated
  * inline `Aggregation` must both emit (prettify) and re-parse to the same shape.
  */
class RequiresReturnsRoundTripTest extends AbstractValidatingTest {

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
    """domain d is { context c is {
      |  record Args is { a: Integer }
      |  function f is { requires record Args returns record Args ??? }
      |  function g is { requires { b: Boolean } returns { r: Integer } ??? }
      |}}
      |""".stripMargin

  "requires/returns" should {
    "round-trip a named type ref and a deprecated inline aggregation through prettify" in {
      (td: TestData) =>
        val pretty = prettify(parse(src, "src"))
        pretty must include("requires record Args")
        pretty must include("returns  record Args")

        val funcs = Finder(parse(pretty, "regen")).recursiveFindByType[Function]
        val f = funcs.find(_.id.value == "f").get
        f.input.get mustBe a[TypeRef]
        val tr = f.input.get.asInstanceOf[TypeRef]
        tr.keyword mustBe "record"
        tr.pathId.format mustBe "Args"

        val g = funcs.find(_.id.value == "g").get
        g.input.get mustBe a[Aggregation]
        g.input.get.asInstanceOf[Aggregation].fields.head.id.value mustBe "b"
    }

    /** The defect that motivated moving the clauses into contents.
      *
      * The body grammar was `[func_input] [func_output] {definitions}` — a fixed PREFIX. Once a
      * comment became a legal definition, a comment written above `requires` consumed the
      * definitions slot and `requires` was then rejected, so the working rule was
      * "`requires`/`returns` must be the very first tokens of the body" — exactly where a reader
      * wants a comment explaining them.
      */
    "accept comments above, between and below the clauses in a function" in { (td: TestData) =>
      val src =
        """domain d is { context c is {
          |  record Args is { a: Integer }
          |  function f is {
          |    // what it needs
          |    requires record Args
          |    // and what it gives back
          |    returns record Args
          |    // that's all
          |  }
          |}}
          |""".stripMargin
      val f = Finder(parse(src, "comments"))
        .recursiveFindByType[Function]
        .find(_.id.value == "f")
        .getOrElse(fail("function f did not parse"))
      f.input.get mustBe a[TypeRef]
      f.output.get mustBe a[TypeRef]
      // Order, not just presence: the clauses sit BETWEEN the comments, where they were written.
      f.contents.toSeq
        .map {
          case _: Requires => "requires"
          case _: Returns  => "returns"
          case _: Comment  => "comment"
          case other       => other.getClass.getSimpleName
        }
        .mustBe(Seq("comment", "requires", "comment", "returns", "comment"))
    }

    "keep a comment above `requires` above it through a prettify round trip" in { (td: TestData) =>
      val src =
        """domain d is { context c is {
          |  record Args is { a: Integer }
          |  function f is {
          |    // what it needs
          |    requires record Args
          |  }
          |}}
          |""".stripMargin
      val pretty = prettify(parse(src, "src"))
      // Emitting from the `input` accessor in `openFunction` would put the clause first and the
      // comment after it — a round trip that rewrites the author's document.
      pretty.indexOf("// what it needs") must be < pretty.indexOf("requires record Args")

      val f = Finder(parse(pretty, "regen"))
        .recursiveFindByType[Function]
        .find(_.id.value == "f")
        .getOrElse(fail("function f did not survive the round trip"))
      f.contents.toSeq.map(_.getClass.getSimpleName).mustBe(Seq("LineComment", "Requires"))
    }

    "accept a comment above `requires` in a saga and keep its place" in { (td: TestData) =>
      val src =
        """domain d is { context c is {
          |  record Args is { a: Integer }
          |  saga s is {
          |    // what the saga needs
          |    requires record Args
          |    step one is { do "it" } reverted by { do "undo it" }
          |    step two is { do "more" } reverted by { do "undo more" }
          |  }
          |}}
          |""".stripMargin
      val saga = Finder(parse(src, "saga-comments"))
        .recursiveFindByType[Saga]
        .headOption
        .getOrElse(fail("saga s did not parse"))
      saga.input.get mustBe a[TypeRef]
      saga.contents.toSeq
        .map {
          case _: Requires => "requires"
          case _: Comment  => "comment"
          case _: SagaStep => "step"
          case other       => other.getClass.getSimpleName
        }
        .mustBe(Seq("comment", "requires", "step", "step"))

      val pretty = prettify(parse(src, "saga-comments"))
      pretty.indexOf("// what the saga needs") must be < pretty.indexOf("requires record Args")
    }

    /** `requires`/`returns` are a `rep` element now, so the grammar cannot bound them; the parser
      * bounds them after the parse so that `Function.input`'s `headOption` is a complete answer
      * rather than a truncation.
      */
    "reject a second `requires` on a function" in { (td: TestData) =>
      val src =
        """domain d is { context c is {
          |  record Args is { a: Integer }
          |  function f is { requires record Args requires record Args }
          |}}
          |""".stripMargin
      TopLevelParser.parseInput(RiddlParserInput(src, "two-requires")) match
        case Right(_) => fail("a second `requires` must be an error")
        case Left(msgs) =>
          msgs.format must include("at most one 'requires' clause")
    }

    "reject a second `returns` on a saga" in { (td: TestData) =>
      val src =
        """domain d is { context c is {
          |  record Args is { a: Integer }
          |  saga s is {
          |    returns record Args
          |    returns record Args
          |    step one is { do "it" } reverted by { do "undo it" }
          |    step two is { do "more" } reverted by { do "undo more" }
          |  }
          |}}
          |""".stripMargin
      TopLevelParser.parseInput(RiddlParserInput(src, "two-returns")) match
        case Right(_) => fail("a second `returns` must be an error")
        case Left(msgs) =>
          msgs.format must include("at most one 'returns' clause")
    }
  }
}
