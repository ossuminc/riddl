/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Contents, Finder, Messages}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** A9 closes the gap where a Saga's `requires`/`returns` were never validated. These tests exercise
  * the new named-type-reference form and the deprecated inline-aggregation form on a Saga.
  */
class SagaValidatorTest extends AbstractValidatingTest {

  private def saga(src: String, td: TestData)(
    check: (Saga, Messages.Messages) => org.scalatest.Assertion
  ) =
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
       |      step StepTwo is { do "do it" } reverted by { do "undo it" }
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

  // A12: a saga step's do-block is all-or-nothing, so it must have AT MOST ONE potential failure
  // point. `stepModel` varies only StepOne's do-block; StepTwo is a trivial valid filler step.
  private def stepModel(stepOneDo: String): String =
    s"""domain d is {
       |  context c is {
       |    record Args is { a: Integer, b: Integer }
       |    record Sum is { total: Integer }
       |    command Go is { xfield: Integer }
       |    command UndoGo is { xfield: Integer }
       |    function Add is {
       |      requires record Args
       |      returns record Sum
       |      return record Sum(total = "t")
       |    }
       |    entity e is { sink tank is { inlet inn is command Go } }
       |    saga sag is {
       |      step StepOne is { $stepOneDo }
       |        reverted by { send command UndoGo to inlet d.c.e.tank.inn }
       |      step StepTwo is { do "do it" } reverted by { do "undo it" }
       |    }
       |  }
       |}
       |""".stripMargin

  private def a12Warnings(messages: Messages.Messages): Seq[Messages.Message] =
    messages.filter(_.message.contains("potential failure points in its do-block")).toSeq

  private def stepCheck(stepOneDo: String, td: TestData)(
    check: Messages.Messages => org.scalatest.Assertion
  ) =
    parseAndValidateDomain(RiddlParserInput(stepModel(stepOneDo), td), shouldFailOnErrors = false) {
      case (_, _, messages) => check(messages)
    }

  "A12 single-failure-point per saga do-block" should {

    "not warn on a step with ONE failure point (one send)" in { (td: TestData) =>
      stepCheck("send command Go to inlet d.c.e.tank.inn", td) { messages =>
        a12Warnings(messages) mustBe empty
      }
    }

    "not warn on a step with ONE failure point (one embedded call)" in { (td: TestData) =>
      stepCheck("""let r = call function Add(a = "1", b = "2")""", td) { messages =>
        a12Warnings(messages) mustBe empty
      }
    }

    "not warn on a step with ZERO failure points (let with a plain value)" in { (td: TestData) =>
      stepCheck("""let r = "just text"""", td) { messages =>
        a12Warnings(messages) mustBe empty
      }
    }

    "warn on a step with TWO failure points (send + embedded call)" in { (td: TestData) =>
      stepCheck(
        """send command Go to inlet d.c.e.tank.inn
          |        let r = call function Add(a = "1", b = "2")""".stripMargin,
        td
      ) { messages =>
        val warns = a12Warnings(messages)
        warns.size mustBe 1
        warns.head.message must include("has 2 potential failure points")
      }
    }

    "warn on a step with TWO failure points nested across a when (when send; send)" in {
      (td: TestData) =>
        stepCheck(
          """when "ready" then
            |          send command Go to inlet d.c.e.tank.inn
            |        end
            |        send command Go to inlet d.c.e.tank.inn""".stripMargin,
          td
        ) { messages =>
          val warns = a12Warnings(messages)
          warns.size mustBe 1
          warns.head.message must include("has 2 potential failure points")
        }
    }
  }

  "Statement.canFail (A12/A36)" should {
    "be true for send/tell/yield/put and false for set/let/return/when" in { _ =>
      val at = At.empty
      val pid = PathIdentifier.empty
      val litOne = LiteralString(at, "1")
      // Failure points by themselves:
      SendStatement(at, MessageRef.empty, InletRef.empty).canFail mustBe true
      TellStatement(at, MessageRef.empty, EntityRef(at, pid)).canFail mustBe true
      YieldStatement(at, MessageRef.empty).canFail mustBe true
      PutStatement(at, litOne, OutputRef(at, "output", pid)).canFail mustBe true
      // Not failure points by themselves:
      SetStatement(at, StateRef(at, pid), litOne).canFail mustBe false
      LetStatement(at, Identifier(at, "x"), None, litOne).canFail mustBe false
      ReturnStatement(at, litOne).canFail mustBe false
      WhenStatement(at, litOne, Contents.empty[Statements]()).canFail mustBe false
    }
  }
}
