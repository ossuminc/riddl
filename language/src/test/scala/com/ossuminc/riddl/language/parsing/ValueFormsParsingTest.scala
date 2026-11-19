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
}
