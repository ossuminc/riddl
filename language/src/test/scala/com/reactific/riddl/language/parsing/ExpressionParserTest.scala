/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ParsingTest
import com.reactific.riddl.language.ast.At
import org.scalatest.Assertion

import scala.collection.immutable.ListMap

/** Unit Tests For ExpressionParser */
class ExpressionParserTest extends ParsingTest {

  def parseExpression(
    input: String
  )(check: Expression => Assertion): Assertion = {
    parse[Expression, Expression](
      input,
      StringParser("").expression(_),
      identity
    ) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right((expression, _)) => check(expression)
    }
  }

  final val magic = 42

  "ExpressionParser" should {
    "accept literal integer" in {
      parseExpression("42") { (expr: Expression) =>
        expr mustBe IntegerValue(At(1 -> 1), BigInt(magic))
      }
    }
    "accept literal decimal" in {
      parseExpression("42.21") { (expr: Expression) =>
        expr mustBe
          DecimalValue(At(1 -> 1), BigDecimal(magic.toDouble + 0.21))
      }
    }
    "accept plus binary" in {
      parseExpression("+(1,1)") { (expr: Expression) =>
        expr mustBe ArithmeticOperator(
          At(1 -> 1),
          "+",
          Seq(IntegerValue(At(1 -> 3), 1), IntegerValue(At(1 -> 5), 1))
        )
      }
    }
    "accept minus binary" in {
      parseExpression("-(1,1)") { (expr: Expression) =>
        expr mustBe ArithmeticOperator(
          At(1 -> 1),
          "-",
          Seq(IntegerValue(At(1 -> 3), 1), IntegerValue(At(1 -> 5), 1))
        )
      }
    }
    "accept times binary" in {
      parseExpression("*(1,1)") { (expr: Expression) =>
        expr mustBe ArithmeticOperator(
          At(1 -> 1),
          "*",
          Seq(IntegerValue(At(1 -> 3), 1), IntegerValue(At(1 -> 5), 1))
        )
      }
    }
    "accept div binary" in {
      parseExpression("/(1,1)") { (expr: Expression) =>
        expr mustBe ArithmeticOperator(
          At(1 -> 1),
          "/",
          Seq(IntegerValue(At(1 -> 3), 1), IntegerValue(At(1 -> 5), 1))
        )
      }
    }
    "accept mod binary" in {
      parseExpression("%(1,1)") { (expr: Expression) =>
        expr mustBe ArithmeticOperator(
          At(1 -> 1),
          "%",
          Seq(IntegerValue(At(1 -> 3), 1), IntegerValue(At(1 -> 5), 1))
        )
      }
    }
    "accept pow abstract binary" in {
      parseExpression("pow(2,3)") { (expr: Expression) =>
        expr mustBe NumberFunction(
          At(1 -> 1),
          "pow",
          Seq(IntegerValue(At(1 -> 5), 2), IntegerValue(At(1 -> 7), 3))
        )
      }
    }
    "accept function call expression " in {
      parseExpression("Entity.Function(i=42, j=21)") { (expr: Expression) =>
        expr mustBe FunctionCallExpression(
          At(1 -> 1),
          PathIdentifier(At(1 -> 1), Seq("Entity", "Function")),
          ArgList(
            ListMap(
              Identifier(At(1 -> 17), "i") -> IntegerValue(
                At(1 -> 19),
                magic
              ),
              Identifier(At(1 -> 23), "j") ->
                IntegerValue(At(1 -> 25), magic / 2)
            )
          )
        )
      }
    }
    "accept arbitrary expression with many  args" in {
      parseExpression("wow(a=0,b=0,c=0)") { (expr: Expression) =>
        expr mustBe ArbitraryOperator(
          At(1 -> 1),
          LiteralString(1 -> 1, "wow"),
          ArgList(
            ListMap(
              Identifier(At(1 -> 5), "a") -> IntegerValue(At(1 -> 7), 0),
              Identifier(At(1 -> 9), "b") -> IntegerValue(At(1 -> 11), 0),
              Identifier(At(1 -> 13), "c") -> IntegerValue(At(1 -> 15), 0)
            )
          )
        )
      }
    }
    "accept a ternary operator" in {
      parseExpression("if(<(@a,@b),42,21)") { (expr: Expression) =>
        expr mustBe Ternary(
          1 -> 1,
          Comparison(
            1 -> 4,
            lt,
            ValueOperator(1 -> 6, PathIdentifier(1 -> 7, Seq("a"))),
            ValueOperator(1 -> 9, PathIdentifier(1 -> 10, Seq("b")))
          ),
          IntegerValue(1 -> 13, BigInt(42)),
          IntegerValue(1 -> 16, BigInt(21))
        )
      }
    }
    "accept the now() operator" in {
      parseExpression("now()") { (expr: Expression) =>
        expr mustBe TimeStampFunction(1 -> 1, "now", Seq.empty[Expression])
      }
    }
    "accept the today() operator" in {
      parseExpression("today()") { (expr: Expression) =>
        expr mustBe DateFunction(1 -> 1, "today", Seq.empty[Expression])
      }
    }
    "accept the random() operator" in {
      parseExpression("random()") { (expr: Expression) =>
        expr mustBe NumberFunction(1 -> 1, "random", Seq.empty[Expression])
      }
    }
    "accept the length() operator" in {
      parseExpression("length()") { (expr: Expression) =>
        expr mustBe NumberFunction(1 -> 1, "length", Seq.empty[Expression])
      }
    }
    "accept the trim() operator" in {
      parseExpression("trim()") { (expr: Expression) =>
        expr mustBe StringFunction(1 -> 1, "trim", Seq.empty[Expression])
      }
    }
  }
}
