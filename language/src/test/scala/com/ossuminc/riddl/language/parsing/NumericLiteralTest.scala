/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** Numeric literals store their text AS WRITTEN so prettify is byte-exact: `1.50`, `007` and `+3`
  * are not recoverable from a parsed number.
  */
// ABSTRACT with `(using PlatformContext)`, matching every sibling in this directory. ScalaTest
// cannot instantiate a suite that takes parameters, so the concrete subclasses live in the two
// platform aggregators; without them this suite silently never runs.
abstract class NumericLiteralTest(using PlatformContext) extends AbstractParsingTest {

  private def firstLetValue(src: String, td: TestData): Value =
    val input = RiddlParserInput(src, td)
    TopLevelParser.parseInput(input, true) match
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")
      case Right(root) =>
        // `Finder` takes the CONTAINER, not its contents.
        val lets = Finder(root).recursiveFindByType[LetStatement]
        lets.headOption.map(_.expression).getOrElse(fail("no let statement found"))

  private def wrap(expr: String): String =
    s"""domain D is {
       |  context C is {
       |    function F is {
       |      let x = $expr
       |    }
       |  }
       |}
       |""".stripMargin

  "NumericLiteral" should {
    "store an integer as written" in { (td: TestData) =>
      firstLetValue(wrap("5"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "5"
          nl.isInteger mustBe true
          nl.asLong mustBe 5L
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "compute asLong on a negative integer" in { (td: TestData) =>
      firstLetValue(wrap("-1"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "-1"
          nl.isInteger mustBe true
          nl.asLong mustBe -1L
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "preserve trailing zeros in a decimal" in { (td: TestData) =>
      firstLetValue(wrap("1.50"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "1.50"
          nl.isInteger mustBe false
          nl.format mustBe "1.50"
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "preserve leading zeros" in { (td: TestData) =>
      firstLetValue(wrap("007"), td) match
        case nl: NumericLiteral => nl.text mustBe "007"
        case other              => fail(s"expected NumericLiteral, got $other")
    }

    "preserve an explicit plus sign" in { (td: TestData) =>
      firstLetValue(wrap("+3"), td) match
        case nl: NumericLiteral => nl.text mustBe "+3"
        case other              => fail(s"expected NumericLiteral, got $other")
    }

    "accept a negative decimal" in { (td: TestData) =>
      firstLetValue(wrap("-0.25"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "-0.25"
          nl.asBigDecimal mustBe BigDecimal("-0.25")
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "accept scientific notation" in { (td: TestData) =>
      firstLetValue(wrap("2E+8"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "2E+8"
          nl.isInteger mustBe false
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "accept a negative exponent" in { (td: TestData) =>
      firstLetValue(wrap("1.5e-3"), td) match
        case nl: NumericLiteral => nl.text mustBe "1.5e-3"
        case other              => fail(s"expected NumericLiteral, got $other")
    }

    // Regression: fastparse's `.rep` skips whitespace BETWEEN repetitions under
    // MultiLineWhitespace regardless of `~~` at the rule's own boundaries. With
    // `CharIn("0-9").rep(1)` this made "1 2" parse as ONE literal of text "1 2" -- isInteger then
    // reported true, and asLong would throw NumberFormatException, instead of the author getting
    // an "expected ',' or ')'"-style parse error. `numericLiteral` must use `CharsWhileIn`, which
    // has no such gap: two whitespace-separated numbers are never one literal. Either outcome
    // below is acceptable -- a parse failure, or a successful parse whose literal's text is just
    // the first number -- but "1 2" swallowed whole as a single literal is not.
    "not swallow whitespace-separated digits into a single literal" in { (td: TestData) =>
      val input = RiddlParserInput(wrap("1 2"), td)
      TopLevelParser.parseInput(input, true) match
        case Left(_) => succeed
        case Right(root) =>
          val lets = Finder(root).recursiveFindByType[LetStatement]
          lets.headOption.map(_.expression) match
            case Some(nl: NumericLiteral) => nl.text must not be "1 2"
            case other => fail(s"expected a parse failure or a NumericLiteral, got $other")
    }
  }
}
