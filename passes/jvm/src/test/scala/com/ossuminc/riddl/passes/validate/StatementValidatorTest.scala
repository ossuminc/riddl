/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages.{Messages, StyleWarning}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, ec}
import com.ossuminc.riddl.utils.CommonOptions

import org.scalatest.TestData

class StatementValidatorTest extends AbstractValidatingTest {

  "Statement Validation" must {
    "identify cross-referent references" in { (td: TestData) =>
      val input =
        """domain test {
          |  referent one {
          |    command fee { ??? }
          |    handler oneH is {
          |      on command fee {
          |        tell command two.pho to referent test.two
          |      }
          |    }
          |  }
          |  referent two {
          |    command pho { ??? }
          |    handler twoH is {
          |      on command pho {
          |        tell command one.fee to referent test.one
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(input, "test case", shouldFailOnErrors = false) {
        (root: Root, messages: Messages) =>
          // info(messages.format)
          root.isEmpty mustBe false
          messages.hasErrors mustBe false
          val warnings = messages.justWarnings
          warnings.isEmpty mustBe false
          messages.exists { (msg: Messages.Message) =>
            msg.kind == StyleWarning &&
            msg.message.contains("Cross-referent references are ill-advised")
          } must be(true)
      }

    }
  }
}
