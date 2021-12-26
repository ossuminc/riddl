package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.Validation.MissingWarning

class InvariantValidator extends ValidatingTest {

  "InvariantValidator" should {
    "validate expressions in invariants in" in {
      parseAndValidate[AST.Entity](
        """
          |entity user is {
          | invariant small is { ??? } described as { "self explanatory!" }
          |}
          |""".stripMargin
      ) { (_, msgs) =>
        assertValidationMessage(
          msgs,
          Validation.Error,
          "Expression in Invariant 'small' should not be empty"
        )
      }
    }
    "validate invariants have descriptions in" in {
      parseAndValidate[AST.Entity](
        """
          |entity user is {
          | invariant large is { "x must be greater or equal to 10" } described as "self explanatory!"
          |}
          |""".stripMargin
      ) { (_, msgs) => assertValidationMessage(msgs, MissingWarning, "should have a description") }
    }
  }

}
