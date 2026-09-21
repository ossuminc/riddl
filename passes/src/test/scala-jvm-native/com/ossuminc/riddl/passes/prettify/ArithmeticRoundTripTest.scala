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

/** B4 (2026-09-21): the prettify half of the reflectivity contract for arithmetic, duration
  * literals, `constant X` as a value, and constant value expressions. Two copies of the
  * parenthesizing rule exist (`ArithmeticExpression.format` and
  * `RiddlFileEmitter.emitArithmeticOperand`) and this suite is what keeps them honest: each case
  * asserts the emitted TEXT and that a re-parse rebuilds the same tree.
  */
class ArithmeticRoundTripTest extends AbstractValidatingTest {

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
       |    constant A: Natural = 10
       |    constant B: Natural = A + 1
       |    constant W: Duration = 30 days
       |    handler H is {
       |      on init {
       |        $stmt
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def firstLet(root: Root): Value =
    Finder(root).recursiveFindByType[LetStatement].headOption.map(_.expression)
      .getOrElse(fail("no let"))

  "arithmetic prettify" should {

    "re-emit each expression with exactly the parentheses its tree needs" in { (td: TestData) =>
      val cases = Seq(
        "a + b * c" -> "a + b * c",
        "(a + b) * c" -> "(a + b) * c",
        "a - (b - c)" -> "a - (b - c)",
        "a - b - c" -> "a - b - c",
        "a / (b * c)" -> "a / (b * c)",
        "a * (b + c) > d" -> "a * (b + c) > d",
        "(a > b) and c" -> "a > b and c", // comparison binds tighter; the tree needs no parens
        "\"x\" + name" -> "\"x\" + name",
        "system.now + 30 days" -> "system.now + 30 days",
        "1.50 hours" -> "1.50 hours",
        "constant A * 2" -> "constant A * 2"
      )
      cases.foreach { case (text, expected) =>
        val root = parse(model(s"let v = $text"), td.name)
        val emitted = prettify(root)
        withClue(s"'$text' emitted:\n$emitted") { emitted must include(s"let v = $expected") }
        val reparsed = parse(emitted, td.name + "-re")
        withClue(s"'$text' re-parse:") { firstLet(reparsed).format mustBe firstLet(root).format }
      }
    }

    "round-trip constant value expressions" in { (td: TestData) =>
      val root = parse(model("""do "nothing""""), td.name)
      val emitted = prettify(root)
      emitted must include("constant B: Natural = A + 1")
      emitted must include("constant W: Duration = 30 days")
      val again = prettify(parse(emitted, td.name + "-re"))
      again mustBe emitted
    }
  }
}
