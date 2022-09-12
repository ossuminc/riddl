package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.testkit.ValidatingTest

class ContextValidationTest extends ValidatingTest {

  "Context" should {
    "allow options" in {
      val input = """options (wrapper, service, gateway, package("foo"))"""
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe (empty)
          context.options.size mustBe 4
          context.options must contain(WrapperOption((2, 11, rpi)))
          context.options must contain(GatewayOption((2, 29, rpi)))
          context.options must contain(ServiceOption((2, 20, rpi)))
          context.options must contain(ContextPackageOption((2,38,rpi),
            Seq(LiteralString((2,46,rpi), "foo"))))
      }
    }
    "allow types" in { pending }
    "allow functions" in { pending }
    "allow entities" in { pending }
    "allow pipelines" in { pending }
  }
}
