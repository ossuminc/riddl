package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.parsing.StringParser
import org.scalatest.Assertion

import scala.collection.immutable.ListMap


/** Unit Tests For ExpressionParser */
class ExpressionParserTest extends ParsingTest {

  def parseExpression(input: String)(check: Expression => Assertion): Assertion = {
    parse[Expression, Expression](
      input,
      StringParser("").expression(_),
      identity
    ) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(expression) =>
        check(expression)
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
        expr mustBe Plus(Location(1 -> 1),
          LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
      }
    }
    "accept minus binary" in {
      parseExpression("-(1,1)") { expr: Expression =>
        expr mustBe Minus(Location(1 -> 1),
          LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
      }
    }
    "accept times binary" in {
      parseExpression("*(1,1)") { expr: Expression =>
        expr mustBe Multiply(Location(1 -> 1),
          LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
      }
    }
    "accept div binary" in {
      parseExpression("/(1,1)") { expr: Expression =>
        expr mustBe Divide(Location(1 -> 1),
          LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
      }
    }
    "accept mod binary" in {
      parseExpression("%(1,1)") { expr: Expression =>
        expr mustBe Modulus(Location(1 -> 1),
          LiteralInteger(Location(1 -> 3), 1), LiteralInteger(Location(1 -> 5), 1))
      }
    }
    "accept pow abstract binary" in {
      parseExpression("pow(2,3)") { expr: Expression =>
        expr mustBe AbstractBinary(Location(1 -> 1), "pow",
          LiteralInteger(Location(1 -> 5), 2), LiteralInteger(Location(1 -> 7), 3))
      }
    }
    "accept function call expression " in {
      parseExpression("Entity.Function(i=42, j=21)") { expr: Expression =>
        expr mustBe FunctionCallExpression(Location(1 -> 1),
          PathIdentifier(Location(1 -> 1), Seq("Function", "Entity")),
          ArgList(ListMap(
            Identifier(Location(1 -> 17), "i") -> LiteralInteger(Location(1 -> 19), magic),
            Identifier(Location(1 -> 23), "j") -> LiteralInteger(Location(1 -> 25), magic / 2)
          )))
      }
    }
    "accept arbitrary expression with 0 number of args" in {
      parseExpression("now()") { expr: Expression =>
        expr mustBe ArbitraryExpression(Location(1 -> 1), "now",
          ArgList(ListMap.empty[Identifier, Expression]))
      }
    }
    "accept arbitrary expression with many  args" in {
      parseExpression("wow(i=0,j=0,k=0,l=0,m=0,n=0)") { expr: Expression =>
        expr mustBe ArbitraryExpression(Location(1 -> 1), "wow",
          ArgList(ListMap(
            Identifier(Location(1 -> 5), "i") -> LiteralInteger(Location(1 -> 7), 0),
            Identifier(Location(1 -> 9), "j") -> LiteralInteger(Location(1 -> 11), 0),
            Identifier(Location(1 -> 13), "k") -> LiteralInteger(Location(1 -> 15), 0),
            Identifier(Location(1 -> 17), "l") -> LiteralInteger(Location(1 -> 19), 0),
            Identifier(Location(1 -> 21), "m") -> LiteralInteger(Location(1 -> 23), 0),
            Identifier(Location(1 -> 25), "n") -> LiteralInteger(Location(1 -> 27), 0),
          )))
      }
    }
  }
}
