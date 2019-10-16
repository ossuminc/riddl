package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.TopLevelParser
import Validation._
import org.scalatest._

/** Unit Tests For TypeValidationTest */
class TypeValidatorTest extends WordSpec with MustMatchers {

  def checkType(input: String)(
    validation: Seq[ValidationMessage] => Assertion
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
    "identify undefined type references" in {
      checkType(
        """
          |domain foo {
          |type Rename is Bar
          |type OneOrMore is Bar+
          |type ZeroOrMore is Bar*
          |type Optional is Bar?
          |type Aggregate is combine {a: Bar, b: Foo}
          |type Alternation is choose Bar or Foo
          |}
          |""".stripMargin
      ) { msgs: Seq[ValidationMessage] =>
        assert(msgs.size == 8, "Should have 8 errors")
        assert(msgs.forall(_.kind == Error), "Should be an error")
        assert(
          msgs.forall(_.message.contains("not defined")),
          "Wrong message"
        )
      }
    }
  }
}
