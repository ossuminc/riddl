package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.Validation._
import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Type
import org.scalatest._

/** Unit Tests For TypeValidationTest */
class TypeValidatorTest extends ValidatingTest {

  "TypeValidator" should {
    "ensure type names start with capital letter" in {
      parseAndValidate[Domain]("""domain foo is {
                                 |type bar is String
                                 |}
                                 |""".stripMargin) {
        case (_: Domain, msgs: Seq[ValidationMessage]) =>
          if (msgs.isEmpty) fail("Type 'bar' should have generated warning")
          else if (msgs.map(_.message).exists(_.contains("should start with"))) { succeed }
          else { fail("No such message") }
      }
    }
    "identify undefined type references" in {
      parseAndValidate[Domain]("""
                                 |domain foo is {
                                 |type Rename is Bar
                                 |type OneOrMore is many Bar
                                 |type ZeroOrMore is many optional Bar
                                 |type Optional is optional Bar
                                 |type Aggregate is {a: Bar, b: Foo}
                                 |type Alternation is one of { Bar or Foo }
                                 |type Order is Id(Bar)
                                 |}
                                 |""".stripMargin) {
        case (_: Domain, msgsAndWarnings: Seq[ValidationMessage]) =>
          val errors = msgsAndWarnings.filter(_.kind == Error)
          assert(errors.size == 9, "Should have 9 errors")
          assert(errors.forall(_.message.contains("not defined")), "Wrong message")
      }
    }

    "identify when pattern type does not refer to a valid pattern" in {
      parseAndValidate[Domain]("""
                                 |domain foo is {
                                 |type pat is Pattern("[")
                                 |}
                                 |""".stripMargin) { case (_: Domain, msgs: ValidationMessages) =>
        assertValidationMessage(msgs, Validation.Error, "Unclosed character class")
      }
    }

    "identify when unique ID types reference something other than an entity" in {
      parseAndValidate[Domain]("""
                                 |domain foo is {
                                 |context TypeTest is { ??? }
                                 |type Order is Id(TypeTest)
                                 |}
                                 |""".stripMargin) { case (_: Domain, msgs: ValidationMessages) =>
        assertValidationMessage(
          msgs,
          Validation.Error,
          "'TypeTest' is not defined but should be a Entity"
        )
      }
    }
  }
}
