package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.Validation.{StyleWarning, ValidationMessage}

class DefinitionValidatorTest extends ValidatingTest {

  "Definition Validation" should {
    "warn when an identifier is less than 3 characters" in {
      parseAndValidate[Domain]("""domain po is {
                                 |type Ba is String
                                 |}
                                 |""".stripMargin) {
        case (_: Domain, msgs: Seq[ValidationMessage]) =>
          if (msgs.isEmpty) {
            fail("Identifiers with less than 3 characters should generate a warning")
          } else {
            val styleWarnings = msgs.filter(_.kind == StyleWarning)
            styleWarnings.size mustEqual 2
            assertValidationMessage(
              styleWarnings,
              StyleWarning,
              "Domain identifier 'po' is too short. The minimum length is 3"
            )
            assertValidationMessage(
              styleWarnings,
              StyleWarning,
              "Type identifier 'Ba' is too short. The minimum length is 3"
            )
          }
      }
    }
  }
}
