/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.utils.CommonOptions
import org.scalatest.TestData

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends AbstractValidatingTest {

  "EntityValidator" should {
    "handle a variety of options" in { (_: TestData) =>
      val input =
        """entity WithOptions is { ??? } with {
          | option finite-state-machine
          | option message-queue
          | option aggregate
          | option transient
          | option available
          |}
          |""".stripMargin
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateInContext[Entity](input, shouldFailOnErrors = false) {
          case (entity: Entity, _, msgs: Messages) =>
            // info(msgs.format)
            msgs.count(_.kind.isError) mustBe 1
            val names = entity.options.collect { case o: OptionValue => o.name }
            names must contain("finite-state-machine")
            names must contain("message-queue")
            names must contain("aggregate")
            names must contain("transient")
            names must contain("available")
        }
      }
    }

    "handle entity with multiple states" in { (_: TestData) =>
      val input =
        """entity MultiState is {
          |  record fields is { field: String  }
          |  state One of MultiState.fields
          |  handler x is {???}
          |  state Two of MultiState.fields
          |  handler y is {???}
          |  handler fum is { ??? }
          |} with {
          |  option finite-state-machine
          |}""".stripMargin
      parseAndValidateInContext[Entity](input) { case (entity: Entity, _, msgs: Messages) =>
        msgs.filter(_.kind.isError) mustBe empty
        entity.states.size mustBe 2
      }
    }
    "error for finite-state-machine entities without at least two states" in { (_: TestData) =>
      val input =
        """entity MultiState is {
          |  record fields is { field: String }
          |  state foo of MultiState.fields
          |  handler x is {???}
          |} with {
          |  option finite-state-machine
          |}""".stripMargin
      pc.withOptions(CommonOptions.noMinorWarnings) { _ =>
        parseAndValidateInContext[Entity](input, shouldFailOnErrors = false) {
          case (_: Entity, _, msgs: Messages) =>
            assertValidationMessage(
              msgs,
              Error,
              "Entity 'MultiState' is declared as an fsm, but doesn't have " +
                "at least two states"
            )
        }
      }
    }

    "catch missing things" in { (_: TestData) =>
      val input = """entity Hamburger is {
                    |  record fields is { field: SomeType }
                    |  state foo of Hamburger.fields
                    |}""".stripMargin
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateInContext[Entity](input, shouldFailOnErrors = false) {
          case (_: Entity, _, msgs: Messages) =>
            assertValidationMessage(
              msgs,
              Error,
              "Entity 'Hamburger' has 1 state but no handlers."
            )
            assertValidationMessage(
              msgs,
              Error,
              """Path 'SomeType' was not resolved, in Record 'fields'
              |because the sought name, 'SomeType', was not found in the symbol table,
              |and it should refer to a Type""".stripMargin
            )
            assertValidationMessage(
              msgs,
              MissingWarning,
              "Entity 'Hamburger' should have a description"
            )
        }
      }
    }

    "produce an error for transient entity with empty event handler" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo is {
          |context bar is {
          |  entity Hamburger  is {
          |    record fields is { field: SomeType }
          |    state field of Hamburger.fields
          |    handler foo is { ??? }
          |  } with {
          |    option aggregate option transient
          |  }
          |}
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            assertValidationMessage(
              msgs,
              MissingWarning,
              "Entity 'Hamburger' has only empty handler"
            )
        }
      }
    }
    "produce correct field references" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |context bar is {
          |  type DoIt = command { ??? }
          |  event Message is { a: Integer }
          |
          |  entity Hamburger  is {
          |    source ofitAll is { outlet ridOfIt is event Message }
          |    type SomeType is Number
          |    record fields is { something: SomeType }
          |    handler x is { ??? }
          |    state field of Hamburger.fields
          |    handler baz is {
          |      on command DoIt {
          |        send event Message to outlet ridOfIt
          |      }
          |    }
          |  } with {
          |    option aggregate option transient
          |  }
          |}
          |}
          |""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) {
          case (_: Domain, _, msgs: Messages) =>
            val errors = msgs.justErrors
            if errors.nonEmpty then fail(errors.format)
            succeed
        }
      }
    }
  }
}
