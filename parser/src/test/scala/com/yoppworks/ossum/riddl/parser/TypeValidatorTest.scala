package com.yoppworks.ossum.riddl.parser

import Validation._
import org.scalatest._

/** Unit Tests For TypeValidationTest */
class TypeValidatorTest extends WordSpec with MustMatchers {

  def checkType(input: String)(
    validation: (Seq[ValidationMessage]) => Assertion
  ): Assertion = {
    TopLevelParser.parse(input) match {
      case Left(msg) =>
        fail(msg)
      case Right(model) =>
        val msgs = Validation.validate(model)
        validation(msgs)
    }
  }

  "TypeValidator" should {
    "ensure type names start with capital letter" in {
      checkType(
        """
          |domain foo { type bar is String }
          |""".stripMargin
      ) { msgs: Seq[ValidationMessage] =>
        if (msgs.isEmpty)
          fail("Type 'bar' should have generated warning")
        else if (msgs.map(_.message).exists(_.contains("must start with"))) {
          succeed
        } else {
          fail("No such message")
        }
      }
    }
  }
}
