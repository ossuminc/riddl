package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages

class OptionsValidationTest extends ValidatingTest {

  "Options" should {
    "identify incorrect css" in { // TODO: This needs to check failures once CSS validation is implemented
      val input: String =
        """domain ignore {
          |  context invalid {
          |    options(css("fill:#333", "color:white"))
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
