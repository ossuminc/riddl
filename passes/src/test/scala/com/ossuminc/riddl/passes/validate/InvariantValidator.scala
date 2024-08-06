/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.Messages.*

class InvariantValidator extends ValidatingTest {

  "InvariantValidator" should {
    "allow undefined expressions in invariants" in {
      parseAndValidateInContext[AST.Entity](
        """
          |entity user is {
          | invariant small is ??? described as { "self explanatory!" }
          |}
          |""".stripMargin
      ) { (_, _, msgs) =>
        assertValidationMessage(
          msgs,
          MissingWarning,
          "condition in Invariant 'small' should not be empty"
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
    "warn about missing descriptions " in {
      parseAndValidateInContext[AST.Entity](
        """
          |entity user is {
          | invariant large is "x must be greater or equal to 10"
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
    "allow arbitrary conditional" in {
      parseAndValidateInContext[AST.Entity]("""
                                              |entity user is {
                                              | invariant large is "true"
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
