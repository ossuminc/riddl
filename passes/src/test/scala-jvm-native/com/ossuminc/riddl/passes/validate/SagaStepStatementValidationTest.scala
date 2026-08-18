/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** Saga step statements must be RESOLVED and VALIDATED like any others.
  *
  * Until 2.0 they were neither. `SagaStep` is a `Leaf` whose statements live in the
  * `doStatements`/`undoStatements` FIELDS rather than in `contents`, and the base `Pass.traverse`
  * matched it as an ordinary Leaf -- processing the step and never descending. Since BOTH
  * `ResolutionPass` and `ValidationPass` extend `Pass` directly, neither ever saw a saga statement,
  * so a step could name definitions that DO NOT EXIST and validate completely clean. It was a
  * silent correctness hole, not a missing warning.
  *
  * The shape of the bug is worth remembering: the same `tell` statement was reported when written
  * in an entity handler and IGNORED when written in a saga step. The test below pins exactly that
  * asymmetry closed.
  */
class SagaStepStatementValidationTest extends AbstractValidatingTest {

  /** A saga step naming `Missing*` things, plus a well-formed handler for contrast. */
  private def model(sagaDo: String): String =
    s"""domain Dom is {
       |  context Ours is {
       |    command Doit is { x: Integer } with { briefly "d" }
       |    entity Caller is {
       |      handler H is {
       |        on command Dom.Ours.Doit is { do "noop" }
       |      } with { briefly "h" }
       |    } with { briefly "c" }
       |    saga Flow is {
       |      step One is {
       |        $sagaDo
       |      } reverted by { do "undo" } with { briefly "s1" }
       |    } with { briefly "f" }
       |  } with { briefly "ctx" }
       |} with { briefly "dom" }
       |""".stripMargin

  "a saga step's do-statements" should {

    "report an unresolved command reference" in { (td: TestData) =>
      parseAndValidate(
        model("tell command Dom.Ours.NoSuchCommand to entity Dom.Ours.Caller"),
        td.name,
        shouldFailOnErrors = false
      ) { (_, _, messages) =>
        val errs = messages.justErrors.format
        errs must include("NoSuchCommand")
      }
    }

    "report an unresolved entity reference" in { (td: TestData) =>
      parseAndValidate(
        model("tell command Dom.Ours.Doit to entity Dom.Ours.NoSuchEntity"),
        td.name,
        shouldFailOnErrors = false
      ) { (_, _, messages) =>
        messages.justErrors.format must include("NoSuchEntity")
      }
    }

    "stay clean when every reference resolves" in { (td: TestData) =>
      // The other half of the contract. Without this, the two cases above would also pass if
      // the new traversal reported EVERYTHING as unresolved.
      parseAndValidate(
        model("tell command Dom.Ours.Doit to entity Dom.Ours.Caller"),
        td.name,
        shouldFailOnErrors = false
      ) { (_, _, messages) =>
        val errs = messages.justErrors
        withClue(s"unexpected errors:\n${errs.format}\n") {
          errs.filter(_.message.contains("was not resolved")) mustBe empty
        }
      }
    }
  }

  "a saga step's undo-statements" should {

    "report an unresolved reference too" in { (td: TestData) =>
      // `undoStatements` is a SEPARATE field from `doStatements`; traversing only the first
      // would leave compensation logic unvalidated, which is where a saga's correctness
      // actually matters most.
      val src =
        """domain Dom is {
          |  context Ours is {
          |    command Doit is { x: Integer } with { briefly "d" }
          |    entity Caller is {
          |      handler H is {
          |        on command Dom.Ours.Doit is { do "noop" }
          |      } with { briefly "h" }
          |    } with { briefly "c" }
          |    saga Flow is {
          |      step One is {
          |        tell command Dom.Ours.Doit to entity Dom.Ours.Caller
          |      } reverted by {
          |        tell command Dom.Ours.BogusUndo to entity Dom.Ours.Caller
          |      } with { briefly "s1" }
          |    } with { briefly "f" }
          |  } with { briefly "ctx" }
          |} with { briefly "dom" }
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { (_, _, messages) =>
        messages.justErrors.format must include("BogusUndo")
      }
    }
  }
}
