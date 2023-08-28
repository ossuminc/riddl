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

/** Unit Tests For ConditionParser */
class ValueParserTest extends ParsingTest {

  def parseValue(
    input: String
  )(check: Value => Assertion): Assertion = {
    parse[Value, Value](
      input,
      StringParser("").value(_),
      identity
    ) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right((value, _)) => check(value)
    }
  }

  final val magic = 42

  "ValueParser" should {
    "accept literal integer" in {
      parseValue("42") { (expr: Value) =>
        expr mustBe IntegerValue(At(1 -> 1), BigInt(magic))
      }
    }
    "accept literal decimal" in {
      parseValue("42.21") { (expr: Value) =>
        expr mustBe
          DecimalValue(1 -> 1, BigDecimal.decimal(magic.toDouble + 0.21))
      }
    }
    "accept plus binary" in {
      parseValue("+(1,1)") { (expr: Value) =>
        expr mustBe ComputedValue(
          1 -> 1,
          "+",
          Seq(IntegerValue(1 -> 3, 1), IntegerValue(1 -> 5, 1))
        )
      }
    }
    "accept minus binary" in {
      parseValue("-(1,1)") { (expr: Value) =>
        expr mustBe ComputedValue(
          At(1 -> 1),
          "-",
          Seq(IntegerValue(1 -> 3, 1), IntegerValue(1 -> 5, 1))
        )
      }
    }
    "accept times binary" in {
      parseValue("*(1,1)") { (expr: Value) =>
        expr mustBe ComputedValue(
          At(1 -> 1),
          "*",
          Seq(IntegerValue(1 -> 3, 1), IntegerValue(1 -> 5, 1))
        )
      }
    }
    "accept div binary" in {
      parseValue("/(1,1)") { (expr: Value) =>
        expr mustBe ComputedValue(
          At(1 -> 1),
          "/",
          Seq(IntegerValue(1 -> 3, 1), IntegerValue(1 -> 5, 1))
        )
      }
    }
    "accept mod binary" in {
      parseValue("%(1,1)") { (expr: Value) =>
        expr mustBe ComputedValue(
          At(1 -> 1),
          "%",
          Seq(IntegerValue(1 -> 3, 1), IntegerValue(1 -> 5, 1))
        )
      }
    }
    "accept pow abstract binary" in {
      parseValue("pow(2,3)") { (value: Value) =>
        val expected = ComputedValue(
          At(1 -> 1),
          "pow",
          Seq(IntegerValue(At(1 -> 5), 2), IntegerValue(At(1 -> 7), 3))
        )
        value mustBe expected
      }
    }
    "accept function call value " in {
      parseValue("function Entity.Function(i=42, j=21)") { (value: Value) =>
        val expected = FunctionCallValue(
          At(1 -> 1),
          FunctionRef(1 -> 1, PathIdentifier(1 -> 10, Seq("Entity", "Function"))),
          ParameterValues(
            1 -> 25,
            Map.from[Identifier, Value](
              Seq(
                Identifier(1 -> 26, "i") ->
                  IntegerValue(1 -> 28, magic),
                Identifier(1 -> 32, "j") ->
                  IntegerValue(1 -> 34, magic / 2)
              )
            )
          )
        )
        value mustBe expected
      }
    }
    "accept arbitrary computed value with many args" in {
      parseValue("wow(0,0,0)") { (expr: Value) =>
        val expected = ComputedValue(
          1 -> 1,
          "wow",
          Seq[Value](
            IntegerValue(1 -> 5, 0),
            IntegerValue(1 -> 7, 0),
            IntegerValue(1 -> 9, 0)
          )
        )
        expr mustBe expected
      }
    }
    "accept the now() operator" in {
      parseValue("now()") { (value: Value) =>
        val expected = ComputedValue(1 -> 1, "now", Seq.empty[Value])
        value mustBe expected
      }
    }
    "accept the today() operator" in {
      parseValue("today()") { (value: Value) =>
        val expected = ComputedValue(1 -> 1, "today", Seq.empty[Value])
        value mustBe expected
      }
    }
    "accept the random() operator" in {
      parseValue("random()") { (value: Value) =>
        val expected = ComputedValue(1 -> 1, "random", Seq.empty[Value])
        value mustBe expected
      }
    }
    "accept the length() operator" in {
      parseValue("length()") { (value: Value) =>
        val expected = ComputedValue(1 -> 1, "length", Seq.empty[Value])
        value mustBe expected
      }
    }
    "accept the trim() operator" in {
      parseValue("trim()") { (value: Value) =>
        val expected = ComputedValue(1 -> 1, "trim", Seq.empty[Value])
        value mustBe expected
      }
    }
  }
}
