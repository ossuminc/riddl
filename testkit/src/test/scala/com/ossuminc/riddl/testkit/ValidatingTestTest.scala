package com.ossuminc.riddl.testkit

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.Assertion
import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages.Messages

class ValidatingTestTest extends ValidatingTest {

  "ValidatingTest" must {
    "parseAndValidate" in {
      val input = "domain foo is { ??? }"

      def validation(root: Root, messages: Messages): Assertion = {
        info(messages.format)
        messages.size must be(3)
      }

      parseAndValidate(input, "testcase")(validation)
    }
  }
}
