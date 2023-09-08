/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.AST.*

class FunctionValidatorTest extends ValidatingTest {

  "FunctionValidator" should {
    "accept function but warn about descriptions" in {
      parseAndValidateInContext[Entity]("""
                                          |entity user is {
                                          |  function foo is {
                                          |    requires {b: Boolean }
                                          |    returns {r: Integer }
                                          |    {}
                                          |  }
                                          |}
                                          |""".stripMargin) { (e, _, msgs) =>
        e.functions must matchPattern {
          case Seq(
                AST.Function(
                  _,
                  Identifier(_, "foo"),
                  Some(Aggregation(_, Seq(Field(_, _, AST.Bool(_), _, _)))),
                  Some(Aggregation(_, Seq(Field(_, _, AST.Integer(_), _, _)))),
                  _,
                  _,
                  _,
                  _,
                  _,
                  _,
                  _,
                  None,
                  None
                )
              ) =>
        }
        assert(
          msgs.exists(_.message == "Function 'foo' should have a description")
        )
      }
    }
    "validate simple function" in {
      val input =
        """function percent {
        |  requires { number: Number }
        |  returns { result: Number }
        |  {
        |    set field percent.result to "a percentage result"
        |  }
        |}
        |""".stripMargin
      parseAndValidateInContext[Function](input, shouldFailOnErrors = true) { case (function, _, msgs) =>
        function.id.value mustBe "percent"
        function.statements mustNot be(empty)
        msgs.justErrors must be(empty)
      }

    }
    "validate function examples" in {
      val input = """
                    |  function AnAspect is {
                    |    { if and("everybody hates me", "I'm depressed") then
                    |        "I go fishing"
                    |        "I'll just eat worms"
                    |      else
                    |        "I'm happy"
                    |      end
                    |    }
                    |  } described as "foo"
                    |""".stripMargin

      parseAndValidateInContext[Function](input, shouldFailOnErrors = false) { case (function, _, msgs) =>
        function.id.value mustBe "AnAspect"
        function.statements mustNot be(empty)
        msgs mustNot be(empty)
        val text = msgs.format
        text must include("Function 'AnAspect' is unused")
        text must include("Vital definitions should have an author reference")
      }
    }
  }
}
