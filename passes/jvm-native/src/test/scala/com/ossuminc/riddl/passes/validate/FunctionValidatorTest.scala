/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.{Inside, TestData}

class FunctionValidatorTest extends AbstractValidatingTest with Inside {

  "FunctionValidator" should {
    "accept function but warn about descriptions" in { (_: TestData) =>
      parseAndValidateInContext[Entity]("""
         |entity user is {
         |  function fun is {
         |    requires {b: Boolean }
         |    returns {r: Integer }
         |    ???
         |  }
         |}
         |""".stripMargin) { (e, rpi, msgs) =>
        inside(e.functions.head) { (f: Function) =>
          f.input must be(
            Some(
              Aggregation(
                At(5, 14, rpi),
                Contents(
                  Field(
                    At(5, 15, rpi),
                    Identifier(At(5, 15, rpi), "b"),
                    AST.Bool(At(5, 18, rpi)),
                    Contents.empty()
                  )
                )
              )
            )
          )
        }
        assert(
          msgs.exists(_.message == "Function 'fun' should have a description")
        )
      }
    }
    "validate simple function" in { (td: TestData) =>
      val input =
        """function percent {
        |  requires { number: Number }
        |  returns { result: Number }
        |  set field percent.result to "a percentage result"
        |}
        |""".stripMargin
      parseAndValidateInContext[Function](input, shouldFailOnErrors = false) {
        case (function, _, msgs) =>
          function.id.value mustBe "percent"
          function.statements.size mustBe 1
          msgs.justErrors must be(empty)
      }

    }
    "validate function prompt statements" in { (td: TestData) =>
      val input =
        """
        |  function AnAspect is {
        |    when "and(everybody hates me, I'm depressed)" then
        |      prompt "I go fishing"
        |      prompt "I'll just eat worms"
        |    end
        |    prompt "I'm happy"
        |  } with { described as "foo" }
        |""".stripMargin

      parseAndValidateInContext[Function](input, shouldFailOnErrors = false) {
        case (function, _, msgs) =>
          function.id.value mustBe "AnAspect"
          function.statements.size mustBe 2
          msgs mustNot be(empty)
          val text = msgs.format
          text must include("Function 'AnAspect' is unused")
      }
    }
  }
}
