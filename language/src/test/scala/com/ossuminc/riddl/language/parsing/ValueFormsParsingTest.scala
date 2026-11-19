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

/** BACKLOG [1.27] (Reid's rulings 2026-11-18, spellings 2026-11-19): the value forms riddl-models
  * could not write -- `<unit> of d`, `sum of`, `one of`, `otherwise` and the conditional value.
  *
  * The hazard every form shares is that its new words are NOT reserved. The corpus has fields
  * named `hours`, `days` and `count`, so each form is recognised only by its full keyword shape
  * and a bare field of the same name must still parse as a reference.
  */
abstract class ValueFormsParsingTest(using PlatformContext) extends AbstractParsingTest {

  private def parse(src: String, td: TestData) =
    TopLevelParser.parseInput(RiddlParserInput(src, td), true)

  private def wrap(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    record Line is { qty: Integer, price: Integer, sku: String } with { briefly "l" }
       |    record Data is { lines: C.Line*, wait: Duration, hours: Integer, days: Integer,
       |      t1: TimeStamp, t2: TimeStamp } with { briefly "d" }
       |    entity E is {
       |      state S of record C.Data
       |      handler H is { on init is { $stmt } } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def letValue(expr: String, td: TestData): Value =
    parse(wrap(s"let v = $expr"), td) match
      case Left(msgs) => fail(s"parse failed:\n${msgs.format}")
      case Right(root) =>
        Finder(root).recursiveFindByType[LetStatement].headOption.map(_.expression)
          .getOrElse(fail("no let"))

  "`<unit> of d`" should {

    "parse every plural unit word" in { (td: TestData) =>
      DurationUnitsValue.units.foreach { u =>
        letValue(s"$u of Data.wait", td) match
          case DurationUnitsValue(_, unit, _: ValueRef) => unit mustBe u
          case other => fail(s"$u: wrong shape: ${other.format}")
      }
    }

    "take a parenthesized difference as its operand" in { (td: TestData) =>
      letValue("days of (Data.t2 - Data.t1)", td) match
        case DurationUnitsValue(_, "days", _: ArithmeticExpression) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "bind tighter than arithmetic" in { (td: TestData) =>
      letValue("minutes of Data.wait + 1", td) match
        case ArithmeticExpression(_, ArithmeticOperator.Add, _: DurationUnitsValue, _) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "leave a field named like a unit word alone" in { (td: TestData) =>
      letValue("Data.hours", td) mustBe a[ValueRef]
      letValue("hours", td) mustBe a[ValueRef]
      letValue("days + 1", td) mustBe a[ArithmeticExpression]
    }

    "not accept a singular unit word" in { (td: TestData) =>
      parse(wrap("let v = minute of Data.wait"), td) mustBe a[Left[?, ?]]
    }
  }

  "`sum of`" should {

    "parse the bare form" in { (td: TestData) =>
      letValue("sum of Data.lines", td) match
        case SumValue(_, _: ValueRef, None, None) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "parse a projection, taking a product" in { (td: TestData) =>
      letValue("sum of Data.lines as l by l.qty * l.price", td) match
        case SumValue(_, _, Some(e), Some(_: ArithmeticExpression)) => e.value mustBe "l"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "leave a following `+` outside the projection" in { (td: TestData) =>
      letValue("sum of Data.lines as l by l.qty + 1", td) match
        case ArithmeticExpression(_, ArithmeticOperator.Add, SumValue(_, _, _, Some(_: ValueRef)), _) =>
          succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "take a parenthesized sum as its projection" in { (td: TestData) =>
      letValue("sum of Data.lines as l by (l.qty + l.price)", td) match
        case SumValue(_, _, _, Some(ArithmeticExpression(_, ArithmeticOperator.Add, _, _))) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "leave a field named `sum` alone" in { (td: TestData) =>
      letValue("sum", td) mustBe a[ValueRef]
      letValue("sum + 1", td) mustBe a[ArithmeticExpression]
    }
  }

  "`one of`" should {

    "parse with an element binding and a full predicate" in { (td: TestData) =>
      letValue("""one of Data.lines as l where l.sku == "x" and l.qty > 0""", td) match
        case OneOfValue(_, _: ValueRef, e, _: LogicalExpression) => e.value mustBe "l"
        case other => fail(s"wrong shape: ${other.format}")
    }

    "compare when parenthesized" in { (td: TestData) =>
      letValue("""(one of Data.lines as l where l.sku == "x") != empty""", td) match
        case ComparisonExpression(_, _, _: OneOfValue, _: EmptyValue) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "leave the `one of { … }` type form alone" in { (td: TestData) =>
      val src =
        """domain D is { context C is {
          |  type Pick is one of { A, B }
          |} with { briefly "c" } } with { briefly "d" }
          |""".stripMargin
      parse(src, td) mustBe a[Right[?, ?]]
    }
  }

  "`otherwise`" should {

    "bind loosest of all" in { (td: TestData) =>
      letValue("Data.hours + 1 otherwise 0", td) match
        case OtherwiseValue(_, _: ArithmeticExpression, _: NumericLiteral) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "fold left" in { (td: TestData) =>
      letValue("Data.hours otherwise Data.days otherwise 0", td) match
        case OtherwiseValue(_, OtherwiseValue(_, _, _), _: NumericLiteral) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "stop an `empty` ascription" in { (td: TestData) =>
      letValue("empty otherwise 0", td) match
        case OtherwiseValue(_, ev: EmptyValue, _) => ev.typeRef mustBe empty
        case other => fail(s"wrong shape: ${other.format}")
    }

    "follow a `one of`, outside its predicate" in { (td: TestData) =>
      letValue("""one of Data.lines as l where l.sku == "x" otherwise empty""", td) match
        case OtherwiseValue(_, _: OneOfValue, _: EmptyValue) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "fill a constructor argument" in { (td: TestData) =>
      parse(wrap("let v = Data.hours otherwise 0  set field S.hours to Data.days otherwise 1"), td) match
        case Left(msgs) => fail(msgs.format)
        case Right(root) =>
          Finder(root).recursiveFindByType[SetStatement].head.value mustBe a[OtherwiseValue]
    }
  }

  "a conditional value" should {

    "parse as a value with a boolean condition" in { (td: TestData) =>
      letValue("when Data.hours > 8 then Data.hours - 8 else 0 end", td) match
        case ConditionalValue(_, _: ComparisonExpression, _: ArithmeticExpression, _: NumericLiteral) =>
          succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "nest a multi-way choice in the else" in { (td: TestData) =>
      letValue("when Data.hours > 8 then 2 else when Data.hours > 4 then 1 else 0 end end", td) match
        case ConditionalValue(_, _, _, _: ConditionalValue) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "be an operand without parentheses, since `end` closes it" in { (td: TestData) =>
      letValue("when Data.hours > 8 then 2 else 1 end * Data.days", td) match
        case ArithmeticExpression(_, ArithmeticOperator.Multiply, _: ConditionalValue, _) => succeed
        case other => fail(s"wrong shape: ${other.format}")
    }

    "take an empty branch" in { (td: TestData) =>
      letValue("when Data.hours > 0 then Data.hours else empty end", td) match
        case ConditionalValue(_, _, _, ev: EmptyValue) => ev.typeRef mustBe empty
        case other => fail(s"wrong shape: ${other.format}")
    }

    "require the else" in { (td: TestData) =>
      parse(wrap("let v = when Data.hours > 0 then 1 end"), td) mustBe a[Left[?, ?]]
    }

    "leave the when STATEMENT alone" in { (td: TestData) =>
      parse(wrap("""let v = 1  when Data.hours > 0 then do "x" else do "y" end"""), td) match
        case Left(msgs) => fail(msgs.format)
        case Right(root) => Finder(root).recursiveFindByType[WhenStatement].size mustBe 1
    }
  }
}
