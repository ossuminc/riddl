package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.parsing.StringParser
import org.scalatest.Assertion

import scala.collection.immutable.ListMap

/** Unit Tests For ConditionParser */
class ConditionParserTest extends ParsingTest {

  def parseCondition(input: String)(check: Condition => Assertion): Assertion = {
    parse[Condition, Condition](
      input,
      StringParser("").condition(_),
      identity
    ) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(content) =>
        check(content)
    }
  }

  "ConditionParser" should {
    "accept true" in {
      parseCondition("true") { cond: Condition => cond mustBe True(Location(1 -> 1)) }
    }
    "accept false" in {
      parseCondition("false") { cond: Condition => cond mustBe False(Location(1 -> 1)) }
    }
    "accept literal string" in {
      parseCondition("\"decide\"") { cond: Condition =>
        cond mustBe ArbitraryCondition(LiteralString(Location(1 -> 1), "decide"))
      }
    }
    "accept and(true,false)" in {
      parseCondition("and(true,false)") { cond: Condition =>
        cond mustBe AndCondition(Location(1 -> 1), True(Location(1 -> 5)), False(Location(1 -> 10)))
      }
    }
    "accept or(true,false)" in {
      parseCondition("or(true,false)") { cond: Condition =>
        cond mustBe OrCondition(Location(1 -> 1), True(Location(1 -> 4)), False(Location(1 -> 9)))
      }
    }
    "accept not(true,false)" in {
      parseCondition("not(true)") { cond: Condition =>
        cond mustBe NotCondition(Location(1 -> 1), True(Location(1 -> 5)))
      }
    }
    "accept a grouping: (true)" in {
      parseCondition("(true)") { cond: Condition =>
        cond mustBe True(Location(1 -> 2))
      }
    }
    "accept function call" in {
      parseCondition("This.That(x=42)") { cond: Condition =>
        cond mustBe FunctionCallExpression(Location(1 -> 1), PathIdentifier(Location(1 -> 1),
          Seq("That", "This")), ArgList(
          ListMap(Identifier(Location(1 -> 11), "x") -> LiteralInteger(Location(1 -> 13), BigInt
          (42)))))
      }
    }
    "accept comparison expressions" in {
      parseCondition("or(lt(a,42),lt(b,SomeFunc()))") { cond: Condition =>
        cond mustBe OrCondition(1 -> 1,
          Comparison(1 -> 4, lt,
            ValueExpression(1 -> 7, PathIdentifier(1 -> 7, Seq("a"))), LiteralInteger(1 -> 9,
              BigInt(42))),
          Comparison(1 -> 13, lt, ValueExpression(1 -> 16, PathIdentifier(1 -> 16, Seq("b"))),
            FunctionCallExpression(1 -> 18, PathIdentifier(1 -> 18, Seq("SomeFunc")), ArgList()))

        )
      }
    }
    "accept complicated conditional expression" in {
      val input = """or(and(not(==("sooth", false)),(SomeFunc(x=42))),true)""".stripMargin
      parseCondition(input) { cond: Condition =>
        cond mustBe OrCondition(1 -> 1, AndCondition(1 -> 4,
          NotCondition(1 -> 8, Comparison(1 -> 12, AST.eq,
            ArbitraryExpression(1 -> 15, LiteralString(1 -> 15, "sooth")),
            ValueExpression(1 -> 24, PathIdentifier(1 -> 24, Seq("false"))))),
          FunctionCallExpression(1 -> 33, PathIdentifier(1 -> 33, Seq("SomeFunc")),
            ArgList(ListMap(Identifier(1 -> 42, "x") -> LiteralInteger(1 -> 44, BigInt(42)))))
        ), True(1 -> 50))
      }
    }
  }

}
