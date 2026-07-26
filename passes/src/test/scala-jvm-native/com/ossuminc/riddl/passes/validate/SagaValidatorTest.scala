/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, Messages}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** A9 closes the gap where a Saga's `requires`/`returns` were never validated. These tests exercise
  * the new named-type-reference form and the deprecated inline-aggregation form on a Saga.
  */
class SagaValidatorTest extends AbstractValidatingTest {

  private def saga(src: String, td: TestData)(check: (Saga, Messages.Messages) => org.scalatest.Assertion) =
    parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
      case (domain, _, messages) => check(Finder(domain).recursiveFindByType[Saga].head, messages)
    }

  private def body(requires: String, returns: String): String =
    s"""domain d is {
       |  context c is {
       |    record Args is { afield: Integer }
       |    result Res is { okfield: Boolean }
       |    command Go is { xfield: Integer }
       |    command UndoGo is { xfield: Integer }
       |    entity e is { sink tank is { inlet inn is command Go } }
       |    saga sag is {
       |      $requires
       |      $returns
       |      step StepOne is { send command Go to inlet d.c.e.tank.inn }
       |        reverted by { send command UndoGo to inlet d.c.e.tank.inn }
       |      step StepTwo is { prompt "do it" } reverted by { prompt "undo it" }
       |    }
       |  }
       |}
       |""".stripMargin

  "SagaValidator" should {

    "accept named type references on saga requires/returns (A9)" in { (td: TestData) =>
      saga(body("requires record Args", "returns result Res"), td) { (s, messages) =>
        s.input.get mustBe a[TypeRef]
        s.output.get mustBe a[TypeRef]
        messages.justDeprecations mustBe empty
      }
    }

    "warn (deprecation) on inline aggregation for saga requires/returns (A9)" in { (td: TestData) =>
      saga(body("requires { pf: String }", "returns { sf: String }"), td) { (s, messages) =>
        s.input.get mustBe a[Aggregation]
        messages.justDeprecations.exists(_.message.contains("deprecated")) mustBe true
      }
    }
  }
}
