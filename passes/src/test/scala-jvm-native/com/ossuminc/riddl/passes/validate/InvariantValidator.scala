/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.TestData

class InvariantValidator extends AbstractValidatingTest {

  "InvariantValidator" should {
    "allow undefined expressions in invariants" in { (td: TestData) =>
      parseAndValidateInContext[AST.Entity](
        """
          |entity user is {
          | invariant small is ??? with { described as { "self explanatory!" } }
          | handler x is { ??? }
          |}
          |""".stripMargin
      ) { (_, _, msgs) =>
        assertValidationMessage(
          msgs,
          MissingWarning,
          "Condition in Invariant 'small' should not be empty"
        )
        assertValidationMessage(
          msgs,
          MissingWarning,
          "Entity 'user' must define at least one state"
        )
        assertValidationMessage(
          msgs,
          MissingWarning,
          "Entity 'user' should have a description"
        )
      }
    }
    "warn about missing descriptions " in { (td: TestData) =>
      parseAndValidateInContext[AST.Entity](
        """
          |entity user is {
          | invariant large is "x must be greater or equal to 10"
          | handler x is { ??? }
          |}
          |""".stripMargin
      ) { (_, _, msgs) =>
        assertValidationMessage(
          msgs,
          MissingWarning,
          "Invariant 'large' should have a description"
        )
      }
    }
    "validate a state-scoped invariant without spurious errors (A18)" in { (td: TestData) =>
      // An invariant declared inside a state is validated exactly like a processor-level one:
      // no new error kind, and it lives inside the state's invariants accessor.
      parseAndValidateInContext[AST.Entity](
        """
          |entity user is {
          | type Data is { x: Integer }
          | state S of record foo.bar.user.Data is {
          |   invariant nonNegative is "x must be >= 0" with { described as { "constraint" } }
          |   handler H is { on other is { do "a" } }
          | }
          |}
          |""".stripMargin
      ) { (entity, _, msgs) =>
        val s = entity.states.find(_.id.value == "S").getOrElse(fail("state S missing"))
        assert(s.invariants.map(_.id.value) == Seq("nonNegative"), "state invariant not parsed")
        assert(entity.invariants.isEmpty, "invariant leaked to entity level")
        assert(msgs.justErrors.isEmpty, s"unexpected errors:\n${msgs.format}")
      }
    }
    "allow arbitrary conditional" in { (td: TestData) =>
      parseAndValidateInContext[AST.Entity]("""
                                              |entity user is {
                                              | invariant large is "true"
                                              | handler x is { ??? }
                                              |}
                                              |""".stripMargin) { (_, _, msgs) =>
        assertValidationMessage(
          msgs,
          MissingWarning,
          "Invariant 'large' should have a description"
        )
      }
    }
  }
}
