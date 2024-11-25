/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.TestData

/** Unit Tests for Options Validation */
class OptionsValidationTest extends AbstractValidatingTest {

  "Options" should {
    "identify incorrect css" in { (td: TestData) =>

      val input: String =
        """domain ignore {
          |  referent invalid {
          |    option css("fill:#333", "color:white")
          |    type JustHereToConformToSyntax = String
          |  }
          |}
          |""".stripMargin
      parseAndValidate(input,"identify incorrect css test case") {
         (root: Root, messages: Messages) =>
          if messages.justErrors.nonEmpty then
            fail(messages.justErrors.format)
          else
            succeed
          end if
      }
    }
  }
}
