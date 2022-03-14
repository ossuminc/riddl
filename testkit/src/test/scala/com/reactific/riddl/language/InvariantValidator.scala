package com.reactific.riddl.language

import com.reactific.riddl.language.Validation.MissingWarning
import com.reactific.riddl.language.testkit.ValidatingTest

class InvariantValidator extends ValidatingTest {

  "InvariantValidator" should {
    "allow undefined expressions in invariants" in {
      parseAndValidateInContext[AST.Entity](
        """
          |entity user is {
          | invariant small is { ??? } described as { "self explanatory!" }
          |}
          |""".stripMargin
      ) { (_, msgs) =>
        assertValidationMessage(
          msgs,
          Validation.MissingWarning,
          "Condition in Invariant 'small' should not be empty"
        )
        assertValidationMessage(msgs, Validation.Error, "Entity 'user' must define a handler")
        assertValidationMessage(
          msgs,
          Validation.MissingWarning,
          "Entity 'user' should have a description"
        )
      }
    }
    "warn about missing descriptions " in {
      parseAndValidateInContext[AST.Entity](
        """
          |entity user is {
          | invariant large is { "x must be greater or equal to 10" }
          |}
          |""".stripMargin
      ) { (_, msgs) =>
        assertValidationMessage(msgs, MissingWarning, "Invariant 'large' should have a description")
      }
    }
    "allow conditional expressions" in {
      parseAndValidateInContext[AST.Entity](
        """
          |entity user is {
          | invariant large is { and(@Field.name1,@Field.name2) }
          |}
          |""".stripMargin
      ) { (_, msgs) =>
        assertValidationMessage(msgs, MissingWarning, "Invariant 'large' should have a description")
      }

    }
  }

}
