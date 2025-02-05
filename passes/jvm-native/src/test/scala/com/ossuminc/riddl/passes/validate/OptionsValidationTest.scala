/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.TestData

/** Unit Tests for Options Validation */
class OptionsValidationTest extends AbstractValidatingTest {

  "Options" should {
    "identify incorrect css" in { (td: TestData) =>

      val input: String =
        """domain ignore {
          |  context invalid {
          |    type JustHereToConformToSyntax = String
          |  } with {
          |    option css("fill:#333", "color:white")
          |  }
          |}
          |""".stripMargin
      parseAndValidate(input, "identify incorrect css test case") {
        (_: Root, _: RiddlParserInput, messages: Messages) =>
          if messages.justErrors.nonEmpty then fail(messages.justErrors.format)
          else succeed
          end if
      }
    }
  }
}
