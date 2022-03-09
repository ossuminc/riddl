package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.StringParser
import org.scalatest.Assertion

import scala.collection.immutable.ListMap

/** Unit Tests For ExpressionParser */
class ExpressionParserTest extends ParsingTest {

  def parseExpression(input: String)(check: Expression => Assertion): Assertion = {
    parse[Expression, Expression](input, StringParser("").expression(_), identity) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(expression) => check(expression)
    }
  }

  final val magic = 42

  "ExpressionParser" should {
    "accept literal integer" in {
      parseExpression("42") { expr: Expression =>
        expr mustBe LiteralInteger(Location(1 -> 1), BigInt(magic))
      }
    }
    "accept literal decimal" in {
      parseExpression("42.21") { expr: Expression =>
        expr mustBe LiteralDecimal(Location(1 -> 1), BigDecimal(magic.toDouble + 0.21))
      }
    }
    "accept plus binary" in {
      parseExpression("+(1,1)") { expr: Expression =>
        expr mustBe ArithmeticOperator(
          Location(1 -> 1),
          "+",
          Seq(LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
        )
      }
    }
    "accept minus binary" in {
      parseExpression("-(1,1)") { expr: Expression =>
        expr mustBe ArithmeticOperator(
          Location(1 -> 1),
          "-",
          Seq(LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
        )
      }
    }
    "accept times binary" in {
      parseExpression("*(1,1)") { expr: Expression =>
        expr mustBe ArithmeticOperator(
          Location(1 -> 1),
          "*",
          Seq(LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
        )
      }
    }
    "accept div binary" in {
      parseExpression("/(1,1)") { expr: Expression =>
        expr mustBe ArithmeticOperator(
          Location(1 -> 1),
          "/",
          Seq(LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
        )
      }
    }
    "accept mod binary" in {
      parseExpression("%(1,1)") { expr: Expression =>
        expr mustBe ArithmeticOperator(
          Location(1 -> 1),
          "%",
          Seq(LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
        )
      }
    }
    "accept pow abstract binary" in {
      parseExpression("pow(2,3)") { expr: Expression =>
        expr mustBe ArithmeticOperator(
          Location(1 -> 1),
          "pow",
          Seq(LiteralInteger(Location(1 -> 5), 2), LiteralInteger(Location(1 -> 7), 3))
        )
      }
    }
    "accept function call expression " in {
      parseExpression("Entity.Function(i=42, j=21)") { expr: Expression =>
        expr mustBe FunctionCallExpression(
          Location(1 -> 1),
          PathIdentifier(Location(1 -> 1), Seq("Function", "Entity")),
          ArgList(ListMap(
            Identifier(Location(1 -> 17), "i") -> LiteralInteger(Location(1 -> 19), magic),
            Identifier(Location(1 -> 23), "j") -> LiteralInteger(Location(1 -> 25), magic / 2)
          ))
        )
      }
    }
    "accept arithmetic expression with 0 number of args" in {
      parseExpression("now()") { expr: Expression =>
        expr mustBe ArithmeticOperator(Location(1 -> 1), "now", Seq.empty[Expression])
      }
    }
    "accept arbitrary expression with many  args" in {
      parseExpression("wow(0,0,0,0,0,0)") { expr: Expression =>
        expr mustBe ArithmeticOperator(
          Location(1 -> 1),
          "wow",
          Seq(
            LiteralInteger(Location(1 -> 5), 0),
            LiteralInteger(Location(1 -> 7), 0),
            LiteralInteger(Location(1 -> 9), 0),
            LiteralInteger(Location(1 -> 11), 0),
            LiteralInteger(Location(1 -> 13), 0),
            LiteralInteger(Location(1 -> 15), 0)
          )
        )
      }
    }
    "accept a ternary operator" in {
      parseExpression("if(<(@a,@b),42,21)") { cond: Expression =>
        cond mustBe Ternary(
          1 -> 1,
          Comparison(
            1 -> 4,
            lt,
            ValueCondition(1 -> 6, PathIdentifier(1 -> 7, Seq("a"))),
            ValueCondition(1 -> 9, PathIdentifier(1 -> 10, Seq("b")))
          ),
          LiteralInteger(1 -> 13, BigInt(42)),
          LiteralInteger(1 -> 16, BigInt(21))
        )
      }
    }

  }
}
