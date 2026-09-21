/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.AST.ArithmeticOperator.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** B4 (Reid, 2026-09-18/21): `+ - * /`, string concatenation, duration literals and constant
  * value expressions. The ladder is `or < and < not < comparison < additive < multiplicative <
  * atom`; the operators are left-associative and NON-cutting.
  */
abstract class ArithmeticExpressionParsingTest(using PlatformContext) extends AbstractParsingTest {

  private def parse(src: String, td: TestData): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, td), true) match
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")
      case Right(root) => root

  private def letValue(expr: String, td: TestData): Value =
    val src =
      s"""domain D is {
         |  context C is {
         |    function F is {
         |      let x = $expr
         |    }
         |  }
         |}
         |""".stripMargin
    Finder(parse(src, td)).recursiveFindByType[LetStatement].headOption
      .map(_.expression).getOrElse(fail("no let statement found"))

  private def ref(v: Value): String = v match
    case vr: ValueRef => vr.path.format
    case other        => fail(s"expected a ValueRef, got ${other.getClass.getSimpleName}")

  "arithmetic expressions" should {

    "give * precedence over +, folding left" in { (td: TestData) =>
      letValue("a + b * c", td) match
        case ArithmeticExpression(_, Add, l, ArithmeticExpression(_, Multiply, bl, cr)) =>
          ref(l) mustBe "a"; ref(bl) mustBe "b"; ref(cr) mustBe "c"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "let parentheses override precedence" in { (td: TestData) =>
      letValue("(a + b) * c", td) match
        case ArithmeticExpression(_, Multiply, ArithmeticExpression(_, Add, _, _), r) =>
          ref(r) mustBe "c"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "be left-associative" in { (td: TestData) =>
      letValue("a - b - c", td) match
        case ArithmeticExpression(_, Subtract, ArithmeticExpression(_, Subtract, _, _), r) =>
          ref(r) mustBe "c"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "parse `a - 3` and `a -3` both as subtraction, and `a-3` as ONE identifier (the trap)" in {
      (td: TestData) =>
        letValue("a - 3", td) mustBe a[ArithmeticExpression]
        letValue("a -3", td) match
          case ArithmeticExpression(_, Subtract, _, nl: NumericLiteral) => nl.text mustBe "3"
          case other => fail(s"wrong shape: ${other.format}")
        // Identifiers may contain `-`, so this never reaches the arithmetic level. Documented.
        letValue("a-3", td) match
          case vr: ValueRef => vr.path.format mustBe "a-3"
          case other        => fail(s"expected a ValueRef, got ${other.format}")
    }

    "concatenate strings with +" in { (td: TestData) =>
      letValue(""""hello " + name""", td) match
        case ArithmeticExpression(_, Add, ls: LiteralString, r) =>
          ls.s mustBe "hello "; ref(r) mustBe "name"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "not mistake a division for the start of a comment" in { (td: TestData) =>
      letValue("x / y // a comment", td) match
        case ArithmeticExpression(_, Divide, l, r) => ref(l) mustBe "x"; ref(r) mustBe "y"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "be a comparison operand" in { (td: TestData) =>
      letValue("a + b > 10", td) match
        case ComparisonExpression(_, ComparisonOperator.GT, ArithmeticExpression(_, Add, _, _), nl: NumericLiteral) =>
          nl.text mustBe "10"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "leave a bare literal, a bare ref and a bare string exactly as before" in { (td: TestData) =>
      letValue("5", td) mustBe a[NumericLiteral]
      letValue("a", td) mustBe a[ValueRef]
      letValue(""""s"""", td) mustBe a[LiteralString]
      letValue("true", td) mustBe a[BooleanLiteral]
    }

    "re-parse its own format, parentheses included" in { (td: TestData) =>
      Seq("(a + b) * c", "a - (b - c)", "a * (b + c) > d", "a / (b * c)", "a + b * c").foreach {
        text =>
          val first = letValue(text, td)
          letValue(first.format, td).format mustBe first.format
      }
    }
  }

  "duration literals" should {

    "parse a plural and a singular unit, keeping the amount as written" in { (td: TestData) =>
      letValue("30 days", td) match
        case DurationLiteral(_, amount, unit) => amount.text mustBe "30"; unit mustBe "days"
        case other                            => fail(s"wrong shape: ${other.format}")
      letValue("1.50 hours", td) match
        case DurationLiteral(_, amount, unit) => amount.text mustBe "1.50"; unit mustBe "hours"
        case other                            => fail(s"wrong shape: ${other.format}")
      letValue("1 week", td) mustBe a[DurationLiteral]
    }

    "not read a word that merely STARTS with a unit as one" in { (td: TestData) =>
      // `5 daysOfWeek` is `5` followed by something that is not a unit -- and not a statement
      // either, so the enclosing parse must fail rather than silently produce a duration.
      val src =
        """domain D is { context C is { function F is { let x = 5 daysOfWeek } } }"""
      TopLevelParser.parseInput(RiddlParserInput(src, td), true).isLeft mustBe true
    }

    "combine with a timestamp" in { (td: TestData) =>
      letValue("system.now + 30 days", td) match
        case ArithmeticExpression(_, Add, _: SystemValue, _: DurationLiteral) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }
  }

  "constant value expressions" should {

    def constants(td: TestData): Seq[Constant] =
      val src =
        """domain D is {
          |  context C is {
          |    constant A: Natural = 10
          |    constant B: Natural = A + 1
          |    constant W: Duration = 30 days
          |    constant S: String = "a" + "b"
          |    constant R: Natural = constant A * 2
          |  }
          |}
          |""".stripMargin
      Finder(parse(src, td)).recursiveFindByType[Constant]

    "parse an arithmetic expression over another constant" in { (td: TestData) =>
      val cs = constants(td)
      cs.size mustBe 5
      cs(1).value match
        case ArithmeticExpression(_, Add, vr: ValueRef, nl: NumericLiteral) =>
          vr.path.format mustBe "A"; nl.text mustBe "1"
        case other => fail(s"wrong shape: ${other.format}")
      cs(2).value mustBe a[DurationLiteral]
      cs(3).value mustBe a[ArithmeticExpression]
      cs(4).value match
        case ArithmeticExpression(_, Multiply, _: ConstantRef, _) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "refuse a comparison, a lookup and `self` as a constant's value" in { (td: TestData) =>
      Seq("a > b", "xs at 1", "self.id", "a and b").foreach { bad =>
        val src = s"domain D is { context C is { constant K: Natural = $bad } }"
        withClue(bad) {
          TopLevelParser.parseInput(RiddlParserInput(src, td), true).isLeft mustBe true
        }
      }
    }
  }
}
