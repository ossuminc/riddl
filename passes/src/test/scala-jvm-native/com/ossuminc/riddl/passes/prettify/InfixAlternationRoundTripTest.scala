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

/** `A | B` is accepted as an alternative spelling of `one of { A or B }`.
  *
  * It is the notation most computer scientists already read, so refusing it is a papercut. It is
  * NOT the canonical form: RIDDL is meant to stay readable by people who are not computer
  * scientists, so PrettifyPass emits the words. Both spellings therefore parse to the IDENTICAL
  * `Alternation`, and a round trip normalises the infix form to `one of { ... }` while losing
  * nothing -- which is the whole reason it is safe to accept a second spelling at all.
  */
class InfixAlternationRoundTripTest extends AbstractValidatingTest {

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

  private def alternationOf(root: Root, name: String): Alternation =
    Finder(root)
      .recursiveFindByType[Type]
      .find(_.id.value == name)
      .map(_.typEx)
      .collect { case a: Alternation => a }
      .getOrElse(fail(s"type '$name' is not an Alternation in the parsed tree"))

  private def paths(alt: Alternation): Seq[String] =
    alt.of.toSeq.map(_.pathId.value.mkString("."))

  private def model(choiceOf: String): String =
    s"""domain D is {
       |  type A is Integer with { briefly "a" }
       |  type B is Integer with { briefly "b" }
       |  type C is Integer with { briefly "c" }
       |  type Choice is $choiceOf with { briefly "ch" }
       |} with { briefly "d" }
       |""".stripMargin

  "the infix spelling `A | B`" should {

    "parse to an Alternation, exactly as `one of { A or B }` does" in { (td: TestData) =>
      val infix = alternationOf(parse(model("D.A | D.B"), "infix"), "Choice")
      val words = alternationOf(parse(model("one of { D.A or D.B }"), "words"), "Choice")
      paths(infix) mustBe Seq("D.A", "D.B")
      paths(infix) mustBe paths(words)
    }

    "accept more than two alternatives" in { (td: TestData) =>
      val alt = alternationOf(parse(model("D.A | D.B | D.C"), "three"), "Choice")
      paths(alt) mustBe Seq("D.A", "D.B", "D.C")
    }

    "prettify to the WORDS, not the bar -- the canonical form stays readable" in { (td: TestData) =>
      val pretty = prettify(parse(model("D.A | D.B"), "infix"))
      pretty must include("one of {")
      // The rendered `Choice` line must not carry a bar. Checked on that line alone, because
      // `|` legitimately begins every `described as` margin line elsewhere in a document.
      val choiceLine = pretty.linesIterator
        .find(_.contains("type Choice is"))
        .getOrElse(
          fail(s"no 'type Choice' line in prettified output:\n$pretty")
        )
      choiceLine mustNot include("|")
    }

    "round-trip: parse -> prettify -> parse preserves the alternation" in { (td: TestData) =>
      val once = parse(model("D.A | D.B"), "infix")
      val again = parse(prettify(once), "regen")
      paths(alternationOf(again, "Choice")) mustBe paths(alternationOf(once, "Choice"))
    }

    "leave a lone type reference alone -- one alternative is not an alternation" in {
      (td: TestData) =>
        // Requiring a `|` is what makes it safe to try the infix rule before every other type
        // expression. If that guard were lost, this would become a one-element Alternation (and
        // draw the single-alternative deprecation) instead of a plain reference.
        val typ = Finder(parse(model("D.A"), "lone"))
          .recursiveFindByType[Type]
          .find(_.id.value == "Choice")
          .getOrElse(fail("type 'Choice' not found"))
        typ.typEx mustBe a[AliasedTypeExpression]
    }

    "not disturb a predefined type" in { (td: TestData) =>
      val typ = Finder(parse(model("Integer"), "predef"))
        .recursiveFindByType[Type]
        .find(_.id.value == "Choice")
        .getOrElse(fail("type 'Choice' not found"))
      typ.typEx mustBe a[Integer]
    }
  }

  "the infix spelling in a FIELD" should {
    "parse to an Alternation too" in { (td: TestData) =>
      val src =
        """domain D is {
          |  type A is Integer with { briefly "a" }
          |  type B is Integer with { briefly "b" }
          |  record R is { choice: D.A | D.B } with { briefly "r" }
          |} with { briefly "d" }
          |""".stripMargin
      val field = Finder(parse(src, "field"))
        .recursiveFindByType[Field]
        .find(_.id.value == "choice")
        .getOrElse(fail("field 'choice' not found"))
      field.typeEx match
        case alt: Alternation =>
          alt.of.toSeq.map(_.pathId.value.mkString(".")) mustBe Seq("D.A", "D.B")
        case other =>
          fail(s"field 'choice' is a ${other.getClass.getSimpleName}, not an Alternation")
    }
  }
}
