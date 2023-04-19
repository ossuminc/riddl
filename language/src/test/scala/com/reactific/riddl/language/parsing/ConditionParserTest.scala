/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.{AST, ParsingTest}
import org.scalatest.Assertion

import scala.collection.immutable.ListMap

/** Unit Tests For ConditionParser */
class ConditionParserTest extends ParsingTest {

  def parseCondition(
    input: String
  )(check: Condition => Assertion
  ): Assertion = {
    val rpi = RiddlParserInput(input)
    parse[Condition, Condition](
      rpi,
      StringParser("").condition(_),
      identity
    ) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right((content, _)) => check(content)
    }
  }

  "ConditionParser" should {
    "accept true" in {
      parseCondition("true") { (cond: Condition) => cond mustBe True(At(1 -> 1)) }
    }
    "accept false" in {
      parseCondition("false") { (cond: Condition) =>
        cond mustBe False(At(1 -> 1))
      }
    }
    "accept literal string" in {
      parseCondition("\"decide\"") { (cond: Condition) =>
        cond mustBe
          ArbitraryCondition(At(1 -> 1), LiteralString(At(1 -> 1), "decide"))
      }
    }
    "accept and(true,false)" in {
      parseCondition("and(true,false)") { (cond: Condition) =>
        cond mustBe
          AndCondition(At(1 -> 1), Seq(True(At(1 -> 5)), False(At(1 -> 10))))
      }
    }
    "accept or(true,false)" in {
      parseCondition("or(true,false)") { (cond: Condition) =>
        cond mustBe
          OrCondition(At(1 -> 1), Seq(True(At(1 -> 4)), False(At(1 -> 9))))
      }
    }
    "accept xor(true,false)" in {
      parseCondition("xor(true,false)") { (cond: Condition) =>
        cond mustBe
          XorCondition(At(1 -> 1), Seq(True(At(1 -> 5)), False(At(1 -> 10))))
      }
    }
    "accept not(true,false)" in {
      parseCondition("not(true)") { (cond: Condition) =>
        cond mustBe NotCondition(At(1 -> 1), True(At(1 -> 5)))
      }
    }
    "accept function call" in {
      parseCondition("This.That(x=42)") { (cond: Condition) =>
        cond mustBe FunctionCallCondition(
          At(1 -> 1),
          PathIdentifier(At(1 -> 1), Seq("This", "That")),
          ArgList(ListMap(
            Identifier(At(1 -> 11), "x") ->
              IntegerValue(At(1 -> 13), BigInt(42))
          ))
        )
      }
    }
    "accept comparison expressions" in {
      parseCondition("or(<(@a,42),<(@b,SomeFunc()))") { (cond: Condition) =>
        cond mustBe OrCondition(
          1 -> 1,
          Seq(
            Comparison(
              1 -> 4,
              lt,
              ValueOperator(1 -> 6, PathIdentifier(1 -> 7, Seq("a"))),
              IntegerValue(1 -> 9, BigInt(42))
            ),
            Comparison(
              1 -> 13,
              lt,
              ValueOperator(1 -> 15, PathIdentifier(1 -> 16, Seq("b"))),
              FunctionCallExpression(
                1 -> 18,
                PathIdentifier(1 -> 18, Seq("SomeFunc")),
                ArgList()
              )
            )
          )
        )
      }
    }
    "accept complicated conditional expression" in {
      val input =
        """or(and(not(==("sooth", false)),SomeFunc(x=42)),true)""".stripMargin
      parseCondition(input) { (cond: Condition) =>
        cond mustBe OrCondition(
          1 -> 1,
          Seq(
            AndCondition(
              1 -> 4,
              Seq(
                NotCondition(
                  1 -> 8,
                  Comparison(
                    1 -> 12,
                    AST.eq,
                    ArbitraryCondition(
                      1 -> 15,
                      LiteralString(1 -> 15, "sooth")
                    ),
                    False(1 -> 24)
                  )
                ),
                FunctionCallCondition(
                  1 -> 32,
                  PathIdentifier(1 -> 32, Seq("SomeFunc")),
                  ArgList(ListMap(
                    Identifier(1 -> 41, "x") ->
                        IntegerValue(1 -> 43, BigInt(42))
                  ))
                )
              )
            ),
            True(1 -> 48)
          )
        )
      }
    }
  }

}
