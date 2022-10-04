package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*

class ContextValidationTest extends ValidatingTest {

  "Context" should {
    "allow options" in {
      val input =
        """options (wrapper, service, gateway, package("foo"), technology("http"))"""
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe (empty)
          context.options.size mustBe 5
          context.options must contain(WrapperOption((2, 11, rpi)))
          context.options must contain(GatewayOption((2, 29, rpi)))
          context.options must contain(ServiceOption((2, 20, rpi)))
          context.options must contain(ContextPackageOption(
            (2, 38, rpi),
            Seq(LiteralString((2, 46, rpi), "foo"))
          ))
          context.options must contain(ContextTechnologyOption(
            (2, 54, rpi),
            Seq(LiteralString((2, 65, rpi), "http"))
          ))
      }
    }
    "allow types" in { pending } // TODO: write this case
    "allow functions" in { pending } // TODO: write this case
    "allow entities" in { pending } // TODO: write this case
    "allow authors" in { pending } // TODO: write this case
    "allow terms" in { pending } // TODO: write this case
    "allow includes" in { pending } // TODO: write this case
    "allow processors" in { pending } // TODO: write this case
    "allow projections" in { pending } // TODO: write this case
  }
}
