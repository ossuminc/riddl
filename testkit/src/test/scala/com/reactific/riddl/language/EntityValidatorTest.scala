package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Validation.ValidationMessages
import com.reactific.riddl.language.testkit.ValidatingTest

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "handle a variety of options" in {
      val input = """entity WithOptions is {
                    | options(fsm, mq, aggregate, transient, available)
                    |
                    |}
                    |""".stripMargin
      parseAndValidateInContext[Entity](input) { case (entity: Entity, msgs: ValidationMessages) =>
        msgs.count(_.kind.isError) mustBe 2
        entity.options must contain(EntityFiniteStateMachine(3 -> 10))
        entity.options must contain(EntityMessageQueue(3 -> 15))
        entity.options must contain(EntityAggregate(3 -> 19))
        entity.options must contain(EntityTransient(3 -> 30))
        entity.options must contain(EntityAvailable(3 -> 41))
      }
    }

    "handle entity with multiple states" in {
      val input = """entity MultiState is {
                    |  options(fsm)
                    |  state foo is { field: String }
                    |  state bar is { field2: Number }
                    |  handler fum is { ??? }
                    |}""".stripMargin
      parseAndValidateInContext[Entity](input) { case (entity: Entity, msgs: ValidationMessages) =>
        msgs.filter(_.kind.isError) mustBe empty
        entity.states.size mustBe 2
      }
    }
    "error for finite-state-machine entities without at least two states" in {
      val input = """entity MultiState is {
                    |  options(fsm)
                    |  state foo is { field: String }
                    |}""".stripMargin
      parseAndValidateInContext[Entity](input) { case (_: Entity, msgs: ValidationMessages) =>
        assertValidationMessage(
          msgs,
          Validation.Error,
          "Entity 'MultiState' is declared as an fsm, but doesn't have " +
            "at least two states"
        )
      }
    }
    "catch missing things" in {
      val input = "entity Hamburger is { state foo is {field:  SomeType } }"
      parseAndValidateInContext[Entity](input) { case (_: Entity, msgs: ValidationMessages) =>
        assertValidationMessage(
          msgs,
          Validation.Error,
          "'SomeType' is not defined but should be a Type"
        )
        assertValidationMessage(msgs, Validation.Error, "Entity 'Hamburger' must define a handler")
        assertValidationMessage(
          msgs,
          Validation.MissingWarning,
          "Entity 'Hamburger' should have a description"
        )
      }
    }

    "produce an error for transient entity with empty event handler" in {
      val input = """
                    |domain foo is {
                    |context bar is {
                    |  entity Hamburger  is {
                    |    options (aggregate, transient)
                    |    state field is { field: SomeType }
                    |    handler foo is {}
                    |  }
                    |}
                    |}
                    |""".stripMargin
      parseAndValidate[Domain](input) { case (_: Domain, msgs: ValidationMessages) =>
        assertValidationMessage(
          msgs,
          Validation.MissingWarning,
          "Entity 'Hamburger' has only empty handler"
        )
      }
    }
    "validate function examples" in {
      parseAndValidateInContext[Function]("""
                                            |  function AnAspect is {
                                            |    EXAMPLE foobar {
                                            |      GIVEN "everybody hates me"
                                            |      AND "I'm depressed"
                                            |      WHEN "I go fishing"
                                            |      THEN "I'll just eat worms"
                                            |      ELSE "I'm happy"
                                            |    } described as {
                                            |     "brief description"
                                            |     "detailed description"
                                            |    }
                                            |  } described as "foo"
                                            |""".stripMargin) { case (feature, msgs) =>
        feature.id.value mustBe "AnAspect"
        assert(feature.examples.nonEmpty)
        assert(msgs.isEmpty)
        assert(msgs.forall(_.message.contains("should have a description")))
      }
    }
    "produce correct field references" in {
      val input = """domain foo is {
                    |context bar is {
                    |  type DoIt = command { ??? }
                    |  type Message = event { a: Integer }
                    |  entity Hamburger  is {
                    |    options (aggregate, transient)
                    |    state field is { field: SomeType }
                    |    handler baz is {
                    |      on command DoIt {
                    |        then tell event Message() to entity Hamburger
                    |      }
                    |    }
                    |  }
                    |}
                    |}
                    |""".stripMargin
      parseAndValidate[Domain](input) { case (_: Domain, msgs: ValidationMessages) =>
        msgs.map(_.format) must
          contain("Error: default(10:19): Field 'a' was not set in message constructor")
      }

    }
  }
}
