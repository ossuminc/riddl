/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages.{Messages, Warning}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, ec}
import com.ossuminc.riddl.utils.CommonOptions

import org.scalatest.TestData

class StatementValidatorTest extends AbstractValidatingTest {

  "Statement Validation" must {
    "identify cross-context references" in { (td: TestData) =>
      val input =
        """domain test {
          |  context one {
          |    command fee { ??? }
          |    handler oneH is {
          |      on command fee {
          |        tell command two.pho to context test.two
          |      }
          |    }
          |  }
          |  context two {
          |    command pho { ??? }
          |    handler twoH is {
          |      on command pho {
          |        tell command one.fee to context test.one
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(input, "test case", shouldFailOnErrors = false) {
        (root: Root, _: RiddlParserInput, messages: Messages) =>
          // info(messages.format)
          root.isEmpty mustBe false
          // UPDATED 2026-08-16 for the isolation-seam ruling. This case is the in-repo record of
          // the OLD behaviour: it asserted `hasErrors mustBe false` for two tells that each cross
          // into the other context naming that context's own message -- which is now exactly the
          // violation Reid's seam makes an Error. The pre-existing Warning is retained and still
          // asserted, so this case now pins BOTH diagnostics rather than trading one for the other.
          messages.hasErrors mustBe true
          messages.justErrors.map(_.message).mkString("\n") must include(
            "crosses the context isolation seam"
          )
          val warnings = messages.justWarnings
          warnings.isEmpty mustBe false
          messages.exists { (msg: Messages.Message) =>
            msg.kind == Warning &&
            msg.message.contains("Cross-context references violate")
          } must be(true)
      }

    }
  }
}
