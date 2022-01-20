package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.{Domain, Entity, Feature}
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessages

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "handle entity with multiple states" in {
      val input = """entity MultiState is {
                    |  options(fsm)
                    |  state foo is { field: String }
                    |  state bar is { field2: Number }
                    |  handler fum is { ??? }
                    |}""".stripMargin
      parseAndValidateInContext[Entity](input) { case (entity: Entity, msgs: ValidationMessages) =>
        msgs.filter(_.kind.isError) mustBe (empty)
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
          "Entity 'MultiState' is declared as a finite-state-machine, but does not have " +
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

    "produce an error for persistent entity with no event producer" in {
      val input = """
                    |domain foo is {
                    |context bar is {
                    |  entity Hamburger  is {
                    |    options (aggregate, persistent)
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
    "validate examples" in {
      parseAndValidateInContext[Feature]("""
                                           |  feature AnAspect is {
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
      }
    }
  }
}
