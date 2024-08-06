package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages

class OptionsValidationTest extends ValidatingTest {

  "Options" should {
    "identify incorrect css" in {
      val input: String =
        """domain ignore {
          |  context invalid {
          |    option css("fill:#333", "color:white")
          |    type JustHereToConformToSyntax = String
          |  }
          |}
          |""".stripMargin
      parseAndValidate(input, "identify incorrect css test case") { (root: Root, messages: Messages) =>
        if messages.justErrors.nonEmpty then fail(messages.justErrors.format)
        else succeed
        end if
      }
    }
  }
}
