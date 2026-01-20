/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Domain
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

class DefinitionValidatorTest extends AbstractValidatingTest {

  "Definition Validation" should {
    "warn when an identifier is less than 3 characters" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain po is {
          |type Ba is String
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Seq[Message]) =>
        if msgs.isEmpty then {
          fail(
            "Identifiers with less than 3 characters should generate a warning"
          )
        } else {
          val styleWarnings = msgs.filter(_.isStyle)
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
