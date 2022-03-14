package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Validation.ValidationMessages
import com.reactific.riddl.language.testkit.ValidatingTest

class ContextValidationTest extends ValidatingTest {

  "Context" should {
    "allow options" in {
      val input = """options (wrapper, service, gateway, function)"""
      parseAndValidateContext(input) { case (context: Context, msgs: ValidationMessages) =>
        msgs.filter(_.kind.isError) mustBe (empty)
        context.options.size mustBe 4
        context.options must contain(WrapperOption(2 -> 11))
        context.options must contain(GatewayOption(2 -> 29))
        context.options must contain(ServiceOption(2 -> 20))
        context.options must contain(FunctionOption(2 -> 38))
      }
    }
  }

}
