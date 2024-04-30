/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.*

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "handle a variety of options" in {
      val input = """entity WithOptions is {
                    | option finite-state-machine
                    | option message-queue
                    | option aggregate
                    | option transient
                    | option available
                    |}
                    |""".stripMargin
      parseAndValidateInContext[Entity](input, shouldFailOnErrors = false) {
        case (entity: Entity, rpi, msgs: Messages) =>
          // info(msgs.format)
          msgs.count(_.kind.isError) mustBe 1
          // msgs.count(_.kind.isWarning) mustBe 1
          val numMissing =
            msgs.count(_.kind.isMissing)
          numMissing mustBe 3
          entity.options must contain(OptionValue((3, 9, rpi), "finite-state-machine"))
          entity.options must contain(OptionValue((4, 9, rpi),"message-queue"))
          entity.options must contain(OptionValue((5, 9, rpi), "aggregate"))
          entity.options must contain(OptionValue((6, 9, rpi), "transient"))
          entity.options must contain(OptionValue((7, 9, rpi), "available"))
      }
    }

    "handle entity with multiple states" in {
      val input =
        """entity MultiState is {
          |  option finite-state-machine
          |  record fields is { field: String  }
          |  state foo of MultiState.fields is { handler x is {???} }
          |  state bar of MultiState.fields is { handler x is {???} }
          |  handler fum is { ??? }
          |}""".stripMargin
      parseAndValidateInContext[Entity](input) { case (entity: Entity, _, msgs: Messages) =>
        msgs.filter(_.kind.isError) mustBe empty
        entity.states.size mustBe 2
      }
    }
    "error for finite-state-machine entities without at least two states" in {
      val input =
        """entity MultiState is {
          |  option finite-state-machine
          |  record fields is { field: String }
          |  state foo of MultiState.fields is {  handler x is {???}  }
          |}""".stripMargin
      parseAndValidateInContext[Entity](input, CommonOptions.noMinorWarnings, shouldFailOnErrors = false) {
        case (_: Entity, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            Error,
            "Entity 'MultiState' is declared as an fsm, but doesn't have " +
              "at least two states"
          )
      }
    }
    "catch missing things" in {
      val input = """entity Hamburger is {
                    |  record fields is { field: SomeType }
                    |  state foo of Hamburger.fields
                    |}""".stripMargin
      parseAndValidateInContext[Entity](input, shouldFailOnErrors = false) { case (_: Entity, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Entity 'Hamburger' has 1 state but no handlers."
        )
        assertValidationMessage(
          msgs,
          Error,
          """Path 'SomeType' was not resolved, in Field 'field'
            |because the sought name, 'SomeType', was not found in the symbol table,
            |and it should refer to a Type""".stripMargin
        )
        assertValidationMessage(
          msgs,
          MissingWarning,
          "Record 'fields' should have a description"
        )
        assertValidationMessage(
          msgs,
          MissingWarning,
          "Entity 'Hamburger' should have a description"
        )
        assertValidationMessage(
          msgs,
          MissingWarning,
          "State 'foo' in Entity 'Hamburger' should have content"
        )

      }
    }

    "produce an error for transient entity with empty event handler" in {
      val input =
        """
          |domain foo is {
          |context bar is {
          |  entity Hamburger  is {
          |    option aggregate option transient
          |    record fields is { field: SomeType }
          |    state field of Hamburger.fields is {  handler x is {???}  }
          |    handler foo is {}
          |  }
          |}
          |}
          |""".stripMargin
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          MissingWarning,
          "Entity 'Hamburger' has only empty handler"
        )
      }
    }
    "produce correct field references" in {
      val input =
        """domain foo is {
          |context bar is {
          |  type DoIt = command { ??? }
          |  event Message is { a: Integer }
          |
          |  entity Hamburger  is {
          |    option aggregate option transient
          |    outlet ridOfIt is event Message
          |    type SomeType is Number
          |    record fields is { field: SomeType } handler x is { ??? }
          |    state field of Hamburger.fields is { }
          |    handler baz is {
          |      on command DoIt {
          |        send event Message to outlet ridOfIt
          |      }
          |    }
          |  }
          |}
          |}
          |""".stripMargin
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        val errors = msgs.justErrors
        if errors.nonEmpty then fail(errors.format)
        succeed
      }
    }
  }
}
