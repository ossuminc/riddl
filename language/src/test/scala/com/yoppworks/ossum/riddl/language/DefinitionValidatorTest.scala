package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.Validation._

class DefinitionValidatorTest extends ValidatingTest {

  "Definition validation" should {
    "warn when an identifier is less than 3 characters" in {
      parseAndValidate[Domain](
        """domain po {
          |type Ba is String
          |}
          |""".stripMargin
      ) {
        case (_: Domain, msgs: Seq[ValidationMessage]) =>
          if (msgs.isEmpty)
            fail(
              "Identifiers with less than 3 characters should generate a warning"
            )
          else {
            val styleWarnings = msgs.filter(_.kind == StyleWarning)
            styleWarnings.size mustEqual 2
            assertValidationMessage(
              styleWarnings,
              StyleWarning,
              "Domain identifier 'po' is too short. Identifiers should be at least 3 characters."
            )
            assertValidationMessage(
              styleWarnings,
              StyleWarning,
              "Type identifier 'Ba' is too short. Identifiers should be at least 3 characters."
            )
          }
      }
    }
  }
}
