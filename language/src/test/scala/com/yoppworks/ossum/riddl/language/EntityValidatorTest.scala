package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Entity
import com.yoppworks.ossum.riddl.language.AST.Feature
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessages

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "handle entity with multiple states" in {
      val input = """entity MultiState is {
                    |  options(fsm)
                    |  state foo is { field: String }
                    |  state bar is { field2: Number }
                    |}""".stripMargin
      parseAndValidate[Entity](input) { case (entity: Entity, _: ValidationMessages) =>
        assert(entity.states.size == 2)
      }
    }
    "error for finite-state-machine entities without at least two states" in {
      val input = """entity MultiState is {
                    |  options(fsm)
                    |  state foo is { field: String }
                    |}""".stripMargin
      parseAndValidate[Entity](input) { case (_: Entity, msgs: ValidationMessages) =>
        msgs.size mustEqual 5
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
      parseAndValidate[Entity](input) { case (_: Entity, msgs: ValidationMessages) =>
        msgs.size mustEqual 5
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
                    |    options (aggregate persistent)
                    |    state field is  SomeType
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
    "validating examples" in {
      parseAndValidate[Feature]("""
                                  |  feature AnAspect is {
                                  |    BACKGROUND {
                                  |      Given "Nobody loves me"
                                  |    }
                                  |    EXAMPLE foo {
                                  |      GIVEN "everybody hates me"
                                  |      AND "I'm depressed"
                                  |      WHEN "I go fishing"
                                  |      THEN "I'll just eat worms"
                                  |      ELSE "I'm happy"
                                  |    } described as {
                                  |     brief "description"
                                  |     details "description"
                                  |    }
                                  |  }
                                  |""".stripMargin) { case (a, b) =>
        a.id.value mustBe "AnAspect"
        assert(a.background.get.givens.nonEmpty)
        assert(a.examples.nonEmpty)
        assert(b.isEmpty)
      }
    }
  }
}
