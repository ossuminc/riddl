package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Entity
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessages

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "catch missing things" in {
      val input = "entity Hamburger is { state foo is {field:  SomeType } }"
      parseAndValidate[Entity](input) {
        case (_: Entity, msgs: ValidationMessages) =>
          msgs.size mustEqual 5
          assertValidationMessage(
            msgs,
            Validation.Error,
            "'SomeType' is not defined"
          )
          assertValidationMessage(
            msgs,
            Validation.Error,
            "entity must consume a topic"
          )
          assertValidationMessage(
            msgs,
            Validation.MissingWarning,
            "should have a description"
          )
      }
    }

    "identify when an entity consumer references a topic that doesn't exist" in {
      val input =
        """
          |entity Hamburger  is {
          |  consumer foo of topic EntityChannel {}
          |}
          |""".stripMargin
      parseAndValidate[Entity](input) {
        case (_: Entity, msgs: ValidationMessages) =>
          assertValidationMessage(
            msgs,
            Validation.Error,
            "'EntityChannel' is not defined but should be a Topic"
          )
      }
    }

    "produce an error for persistent entity with no event producer" in {
      val input =
        """
          |domain foo {
          |topic EntityChannel {
          |  commands { Foo is {} yields event bar }
          |  events { bar is {} } queries {} results {}
          |}
          |context bar {
          |  entity Hamburger  is {
          |    options (aggregate persistent)
          |    state field is  SomeType
          |    consumer foo of topic EntityChannel {}
          |  }
          |}
          |}
          |""".stripMargin
      parseAndValidate[Domain](input) {
        case (_: Domain, msgs: ValidationMessages) =>
          assertValidationMessage(
            msgs,
            Validation.MissingWarning,
            "has only empty topic"
          )
      }
    }
  }
}
