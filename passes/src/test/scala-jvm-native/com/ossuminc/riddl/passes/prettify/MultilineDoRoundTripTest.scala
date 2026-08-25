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
import org.scalatest.TestData

/** Multi-line `do` and `prompt(...)`: `do { "a" "b" }`, matching `doc_block`'s shape.
  *
  * RIDDL is fully reflective, so a construct is only half-done when it parses -- prettify must emit
  * it and a re-parse must recover the SAME AST. These cases assert the emitted TEXT as well, since
  * output that merely re-parses can still have been mangled into something else.
  *
  * The single-line cases are the load-bearing half. The design is additive, so if a one-line `do`
  * moved, every model in the corpus would need reformatting for a feature it does not use.
  */
class MultilineDoRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass
      .runThesePasses(PassInput(root), creators)
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def model(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    handler H is {
       |      on init {
       |        $stmt
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def dos(root: Root): Seq[DoStatement] =
    Finder(root.contents).recursiveFindByType[DoStatement].toSeq

  private def prompts(root: Root): Seq[PromptValue] =
    Finder(root.contents).recursiveFindByType[PromptValue].toSeq

  "a multi-line do" should {

    "parse into one statement holding every line" in { (_: TestData) =>
      val stmts = dos(parse(model("""do { "first" "second" "third" }"""), "multi.riddl"))
      stmts must have size 1
      stmts.head.what.map(_.s) mustBe Seq("first", "second", "third")
    }

    "expose the lines to a generator as newline-separated text" in { (_: TestData) =>
      // The accessor riddlg reads. Derived from `what`, so the two cannot disagree.
      dos(parse(model("""do { "alpha" "beta" }"""), "text.riddl")).head.text mustBe "alpha\nbeta"
    }

    "survive a prettify round trip with its lines intact" in { (_: TestData) =>
      val again = parse(prettify(parse(model("""do { "a" "b" "c" }"""), "rt.riddl")), "rt2.riddl")
      dos(again).head.what.map(_.s) mustBe Seq("a", "b", "c")
    }

    "emit one string per line, as every other block does" in { (_: TestData) =>
      val out = prettify(parse(model("""do { "first" "second" }"""), "layout.riddl"))
      out must include("do {")
      out must include("\"first\"")
      out must include("\"second\"")
      // Not squashed onto one line -- the defect InvariantBlock was caught having.
      out mustNot include("""do { "first" "second" }""")
    }

    "converge: prettifying the output again changes nothing" in { (_: TestData) =>
      val once = prettify(parse(model("""do { "a" "b" }"""), "c1.riddl"))
      prettify(parse(once, "c2.riddl")) mustBe once
    }
  }

  "a single-line do" should {

    "still be one line, byte-identical to before multi-line existed" in { (_: TestData) =>
      val out = prettify(parse(model("""do "just the one""""), "single.riddl"))
      out must include("""do "just the one"""")
      out mustNot include("do {")
    }

    "hold exactly one line" in { (_: TestData) =>
      dos(parse(model("""do "only""""), "one.riddl")).head.what.map(_.s) mustBe Seq("only")
    }
  }

  "a multi-line prompt value" should {

    "parse, and round-trip through prettify" in { (_: TestData) =>
      val root = parse(model("""let x = prompt({ "one" "two" })"""), "pv.riddl")
      prompts(root).head.prompt.map(_.s) mustBe Seq("one", "two")
      prompts(parse(prettify(root), "pv2.riddl")).head.prompt.map(_.s) mustBe Seq("one", "two")
    }

    "keep its ascription" in { (_: TestData) =>
      val pv = prompts(parse(model("""let x = prompt({ "one" "two" }) as Real"""), "asc.riddl")).head
      pv.prompt.map(_.s) mustBe Seq("one", "two")
      pv.typeEx mustBe defined
    }

    "leave the single-line form unchanged" in { (_: TestData) =>
      prettify(parse(model("""let x = prompt("just one")"""), "pv1.riddl")) must
        include("""prompt("just one")""")
    }
  }
}
