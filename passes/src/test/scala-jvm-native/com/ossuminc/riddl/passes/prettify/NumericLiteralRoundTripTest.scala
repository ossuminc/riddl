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

/** A numeric literal stores its text AS WRITTEN, so prettify must reproduce it byte-for-byte.
  *
  * These assertions are on the literal's EXACT TEXT, not on whether the output re-parses. `1.5`
  * re-parses perfectly well after `1.50` has been mangled into it — a test that only re-parsed
  * would pass while the fidelity claim was false, which is the whole failure this guards.
  */
class NumericLiteralRoundTripTest extends AbstractValidatingTest {

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

  private def model(literal: String): String =
    s"""domain D is {
       |  context C is {
       |    handler H is {
       |      on init {
       |        let x = $literal
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def literalTextIn(root: Root): String =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .headOption
      .map(_.expression)
      .collect { case nl: NumericLiteral => nl.text }
      .getOrElse(fail("no NumericLiteral found in a let statement"))

  private val forms =
    Seq("5", "-1", "+3", "007", "1.50", "-0.25", "1e3", "1.5e-3", "2E+8")

  "a numeric literal" should {
    for form <- forms do
      s"survive a prettify round trip byte-exact: $form" in { (td: TestData) =>
        val original = parse(model(form), s"orig-$form")
        literalTextIn(original) mustBe form

        val emitted = prettify(original)
        withClue(s"emitted source was:\n$emitted\n") {
          emitted must include(s"let x = $form")
        }

        val reparsed = parse(emitted, s"reparsed-$form")
        literalTextIn(reparsed) mustBe form
      }
    end for
  }
}
