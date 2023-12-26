/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*

class FunctionValidatorTest extends ValidatingTest {

  "FunctionValidator" should {
    "accept function but warn about descriptions" in {
      parseAndValidateInContext[Entity]("""
                                          |entity user is {
                                          |  function foo is {
                                          |    requires {b: Boolean }
                                          |    returns {r: Integer }
                                          |    body ???
                                          |  }
                                          |}
                                          |""".stripMargin) { (e, _, msgs) =>
        e.functions must matchPattern {
          case Seq(
                AST.Function(
                  _,
                  Identifier(_, "foo"),
                  _,
                  Some(Aggregation(_, Seq(Field(_, _, AST.Bool(_), _, _)), _)),
                  Some(Aggregation(_, Seq(Field(_, _, AST.Integer(_), _, _)), _)),
                  _,
                  _,
                  _
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
        |  body {
        |    set field percent.result to "a percentage result"
        |  }
        |}
        |""".stripMargin
      parseAndValidateInContext[Function](input, shouldFailOnErrors = false) { case (function, _, msgs) =>
        function.id.value mustBe "percent"
        function.statements.size mustBe 1
        msgs.justErrors must be(empty)
      }

    }
    "validate function empty statements" in {
      val input = """
                    |  function AnAspect is {
                    |    body {
                    |      "if and(everybody hates me, I'm depressed) then"
                    |        "I go fishing"
                    |        "I'll just eat worms"
                    |      "else"
                    |        "I'm happy"
                    |      "end"
                    |    }
                    |  } described as "foo"
                    |""".stripMargin

      parseAndValidateInContext[Function](input, shouldFailOnErrors = false) { case (function, _, msgs) =>
        function.id.value mustBe "AnAspect"
        function.statements.size mustBe 6
        msgs mustNot be(empty)
        val text = msgs.format
        text must include("Function 'AnAspect' is unused")
        text must include("Vital definitions should have an author reference")
      }
    }
  }
}
