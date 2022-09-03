package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
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
      parseAndValidateInContext[Entity](input) {
        case (entity: Entity, rpi, msgs: Messages) =>
          msgs.count(_.kind.isError) mustBe 2
          entity.options must contain(EntityIsFiniteStateMachine((3, 10, rpi)))
          entity.options must contain(EntityMessageQueue((3, 15, rpi)))
          entity.options must contain(EntityIsAggregate((3, 19, rpi)))
          entity.options must contain(EntityTransient((3, 30, rpi)))
          entity.options must contain(EntityIsAvailable((3, 41, rpi)))
      }
    }

    "handle entity with multiple states" in {
      val input = """entity MultiState is {
                    |  options(fsm)
                    |  state foo is { field: String }
                    |  state bar is { field2: Number }
                    |  handler fum is { ??? }
                    |}""".stripMargin
      parseAndValidateInContext[Entity](input) {
        case (entity: Entity, _, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe empty
          entity.states.size mustBe 2
      }
    }
    "error for finite-state-machine entities without at least two states" in {
      val input = """entity MultiState is {
                    |  options(fsm)
                    |  state foo is { field: String }
                    |}""".stripMargin
      parseAndValidateInContext[Entity](input) {
        case (_: Entity, _, msgs: Messages) => assertValidationMessage(
            msgs,
            Error,
            "Entity 'MultiState' is declared as an fsm, but doesn't have " +
              "at least two states"
          )
      }
    }
    "catch missing things" in {
      val input = "entity Hamburger is { state foo is {field:  SomeType } }"
      parseAndValidateInContext[Entity](input) {
        case (_: Entity, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            Error,
            "Path 'SomeType' was not resolved, in Field 'field', " +
              "but should refer to a Type"
          )
          assertValidationMessage(
            msgs,
            Error,
            "Entity 'Hamburger' must define a handler"
          )
          assertValidationMessage(
            msgs,
            MissingWarning,
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
      parseAndValidate[Domain](input) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          MissingWarning,
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
                                            |""".stripMargin) {
        case (feature, _, msgs) =>
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
      parseAndValidate[Domain](input) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          s"Field 'a' was not set in message constructor"
        )
      }

    }
  }
}
