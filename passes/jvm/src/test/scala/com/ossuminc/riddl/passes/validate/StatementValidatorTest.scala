package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages.{Messages, StyleWarning}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{pc, ec}
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
        (root: Root, messages: Messages) =>
          // info(messages.format)
          root.isEmpty mustBe false
          messages.hasErrors mustBe false
          val warnings = messages.justWarnings
          warnings.isEmpty mustBe false
          messages.exists { (msg: Messages.Message) =>
            msg.kind == StyleWarning &&
            msg.message.contains("Cross-context references are ill-advised")
          } must be(true)
      }

    }
  }
}
