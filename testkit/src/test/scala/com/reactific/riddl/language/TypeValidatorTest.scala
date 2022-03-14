package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Validation.*
import com.reactific.riddl.language.testkit.ValidatingTest

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
          val errors = msgsAndWarnings.filter(_.kind == Validation.Error)
          assert(errors.size == 9, "Should have 9 errors")
          assert(errors.forall(_.message.contains("not defined")), "Wrong message")
      }
    }
    "allow ??? in aggregate bodies without warning" in {
      parseAndValidate[Domain]("""domain foo {
                                 |type Empty is { ??? } explained as "empty"
                                 |} explained as "nothing"
                                 |""".stripMargin) { case (_: Domain, msgs: ValidationMessages) =>
        msgs mustBe empty
      }
    }
    "generate 'sender' field in messages" in {
      val cases = Seq("command", "event", "query", "result")
      for { messageKind <- cases } {
        val input = s"""domain foo is {
                       |type Message is $messageKind {
                       |  two: String explained as "a field"
                       |} explained as "subject"
                       |} explained as "nothing"
                       |""".stripMargin
        parseAndValidate[Domain](input) { case (d: Domain, msgs: ValidationMessages) =>
          msgs mustBe empty
          val typ = d.types.head
          typ.typ match {
            case MessageType(_, kind, fields) =>
              kind.kind mustBe messageKind
              fields.head.id.value mustBe "sender"
              fields.head.typeEx match {
                case ReferenceType(_, EntityRef(_, id)) => id mustBe empty
                case x: TypeExpression => fail(s"Expected a ReferenceType but got: $x")
              }
            case x: TypeExpression => fail(s"Expected an MessageType but got: $x")
          }
        }
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
          "'TypeTest' was expected to be an Entity but is a Context instead"
        )
      }
    }
  }
}
