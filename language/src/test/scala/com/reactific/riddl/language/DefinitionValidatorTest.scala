/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.Messages.*

class DefinitionValidatorTest extends ValidatingTest {

  "Definition Validation" should {
    "warn when an identifier is less than 3 characters" in {
      parseAndValidateDomain("""domain po is {
                               |type Ba is String
                               |}
                               |""".stripMargin) {
        case (_: Domain, _, msgs: Seq[Message]) =>
          if (msgs.isEmpty) {
            fail(
              "Identifiers with less than 3 characters should generate a warning"
            )
          } else {
            val styleWarnings = msgs.filter(_.kind == StyleWarning)
            styleWarnings.size mustEqual 3
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
