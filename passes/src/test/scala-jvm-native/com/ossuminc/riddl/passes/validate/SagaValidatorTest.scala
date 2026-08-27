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
       |    result Answer is { v: Integer }
       |    query Ask replies result Answer is { q: Integer }
       |    record QState is { total: Integer }
       |    entity e is { sink tank is { inlet inn is command Go } }
       |    entity q is {
       |      state S of record d.c.QState is {
       |        handler H is { on query d.c.Ask is { reply result d.c.Answer } }
       |      }
       |    }
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

    // A12 counts failure-bearing VALUES, not only statements (Reid, 2026-08-09), and `ask` was
    // added to that census alongside `call` and `get`.
    //
    // SUPERSEDED for sagas by Reid's 2026-08-10 ruling: a saga may not `ask` AT ALL, not even as a
    // value, because a saga must not depend on dynamic state or the same inputs could yield
    // different transaction results at different times. So these two cases no longer assert a
    // failure-point COUNT — they assert the prohibition, and the count is deliberately suppressed
    // when an ask is present (its remedy, "split into multiple steps", produces an ask-only step
    // that then fails the mandatory-'tell' rule; the advice could not be taken).
    //
    // The two-failure-point counting itself is still covered, by the `send + embedded call` case
    // below. Full coverage of the prohibition lives in SagaAskProhibitedTest.
    "reject a lone ask outright, rather than counting it" in { (td: TestData) =>
      stepCheck("""let a = ask query d.c.Ask of entity d.c.q""", td) { messages =>
        messages
          .filter(_.kind == Messages.Error)
          .exists(_.message.contains("may not 'ask'")) mustBe true
      }
    }

    "reject ask + send as a prohibition, not as a failure-point count" in { (td: TestData) =>
      stepCheck(
        """let a = ask query d.c.Ask of entity d.c.q
          |        send command Go to inlet d.c.e.tank.inn""".stripMargin,
        td
      ) { messages =>
        messages
          .filter(_.kind == Messages.Error)
          .exists(_.message.contains("may not 'ask'")) mustBe true
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

  /** Reid ruled 2026-08-14: `initiate` and `terminate` ARE legal inside a saga step -- *"a saga may
    * need new entities to be created."*
    *
    * **This test exists because the behaviour was correct BY ACCIDENT, not by decision.**
    * `checkInstanceEffectScope` bans the two in exactly two shapes -- a parent that is an
    * `OnActivationClause`/`OnPassivationClause`, or a `Function` in the parent chain -- and for a
    * saga-step statement `parents.head` is the **Saga** (a `SagaStep` is a `Leaf` and is never
    * pushed; see `Pass.traverse`), so both predicates are structurally false. Nothing tested either
    * way, so the next person to tighten that method would have removed the legality with nothing
    * going red. That is what this pins.
    *
    * Note the asymmetry the ruling settles deliberately: `self` IS banned in a saga step while
    * these two are legal. That is coherent -- a saga has no instance identity of its own, but it
    * may create and destroy instances.
    *
    * The assertion is `justErrors mustBe empty` over the WHOLE model, not the absence of a
    * particular message. A test that merely asserted "no ban error" would also pass for a model
    * that failed to parse -- the trap recorded in CLAUDE.md and hit on this branch before.
    */
  "initiate/terminate in a saga step (Reid, 2026-08-14)" should {

    /** Deliberately minimal: `do` for every block that is not under test, and no `send` anywhere.
      * The first draft reused this file's `stepModel` shape and failed for three reasons that had
      * nothing to do with instance identity -- an entity with no handler, a saga with one step, and
      * a BARE `send command UndoGo` which became an Error the same day. Fixture noise that can fail
      * a "validates clean" assertion is worse here than anywhere, because the assertion is exactly
      * "nothing is reported".
      */
    def instanceSaga(stepOne: String, revertOne: String, td: TestData): Unit =
      val src =
        s"""domain d is {
           |  context c is {
           |    record OState is { total: Integer }
           |    command Go is { xfield: Integer }
           |    entity Order is {
           |      state OS of record d.c.OState is {
           |        handler OH is {
           |          on command d.c.Go is { do "go" }
           |          on init is { do "start" }
           |          on term is { do "end" }
           |        }
           |      }
           |    }
           |    saga sag is {
           |      step StepOne is { $stepOne } reverted by { $revertOne }
           |      step StepTwo is { do "do it" } reverted by { do "undo it" }
           |    }
           |  }
           |}
           |""".stripMargin
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, messages) => messages.justErrors mustBe empty
      }

    "accept `initiate` and `terminate` in a do-block" in { (td: TestData) =>
      instanceSaga(
        """let oid = initiate entity d.c.Order
          |        terminate oid""".stripMargin,
        """do "undo one"""",
        td
      )
    }

    "accept `initiate` and `terminate` in an UNDO block too" in { (td: TestData) =>
      // A revert that destroys what the forward action created is the motivating shape, and the
      // `ask` prohibition showed undo-statements are walked separately from do-blocks -- so a ban
      // could reach one and not the other. Both halves are pinned.
      instanceSaga(
        """do "forward"""",
        """let oid = initiate entity d.c.Order
          |        terminate oid""".stripMargin,
        td
      )
    }
  }
}
