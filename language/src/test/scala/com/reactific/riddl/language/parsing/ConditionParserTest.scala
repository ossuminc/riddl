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
  )(check: Condition => Assertion): Assertion = {
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
      parseCondition("function This.That(x=42)") { (cond: Condition) =>
        val expected = FunctionCallCondition(
          At(1 -> 1),
          FunctionRef(1 -> 1, PathIdentifier(1 -> 10, Seq("This", "That"))),
          ParameterValues(
            1 -> 19,
            Map.from(
              Seq(
                Identifier(1 -> 20, "x") ->
                  IntegerValue(1 -> 22, BigInt(42))
              )
            )
          )
        )
        cond mustBe expected
      }
    }
    "accept comparison" in {
      parseCondition("or(<(constant a, 42), <(field b, calc()))") { (cond: Condition) =>
        cond mustBe OrCondition(
          1 -> 1,
          Seq(
            Comparison(
              1 -> 4,
              lt,
              ConstantValue(1 -> 6, PathIdentifier(1 -> 15, Seq("a"))),
              IntegerValue(1 -> 18, BigInt(42))
            ),
            Comparison(
              1 -> 23,
              lt,
              FieldValue(1 -> 25, PathIdentifier(1 -> 31, Seq("b"))),
              ComputedValue(
                1 -> 34,
                "calc",
                Seq.empty[Value]
              )
            )
          )
        )
      }
    }
    "accept complicated conditional expression" in {
      val input =
        """or(and(not(==("sooth", false)),function SomeFunc(x=42)),true)""".stripMargin
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
                    ArbitraryValue(
                      1 -> 15,
                      LiteralString(1 -> 15, "sooth")
                    ),
                    BooleanValue(1 -> 24, false)
                  )
                ),
                FunctionCallCondition(
                  1 -> 32,
                  FunctionRef(1 -> 32, PathIdentifier(1 -> 41, Seq("SomeFunc"))),
                  ParameterValues(
                    1 -> 49,
                    Map(
                      Identifier(1 -> 50, "x") ->
                        IntegerValue(1 -> 52, BigInt(42))
                    )
                  )
                )
              )
            ),
            True(1 -> 57)
          )
        )
      }
    }
  }

}
