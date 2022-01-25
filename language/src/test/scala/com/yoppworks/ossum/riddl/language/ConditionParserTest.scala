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
        cond mustBe FunctionCallCondition(Location(1 -> 1), PathIdentifier(Location(1 -> 1),
          Seq("That", "This")), ArgList(
          ListMap(Identifier(Location(1 -> 11), "x") -> LiteralInteger(Location(1 -> 13), BigInt(42)))))
      }
    }
    "accept complicated conditional expression" in {
      val input = """or(and(not(==("sooth", false)),(SomeFunc(x=42))),true)""".stripMargin
      parseCondition(input) { cond: Condition =>
        cond mustBe OrCondition(Location(1 -> 1),
          AndCondition(Location(1 -> 4),
            NotCondition(Location(1 -> 8), Comparison(Location(1 -> 12), AST.eq,
              ArbitraryCondition(LiteralString(Location(1 -> 15), "sooth")),
              False(Location(1 -> 24)))),
            FunctionCallCondition(Location(1 -> 33), PathIdentifier(Location(1 -> 33), Seq("SomeFunc"))
              , ArgList(ListMap(Identifier(Location(1 -> 42), "x") -> LiteralInteger(Location(1 -> 44),
                BigInt(42)))))
          ), True(Location(1 -> 50)))
      }
    }
  }

}
