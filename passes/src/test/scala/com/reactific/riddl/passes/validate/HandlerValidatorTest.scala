/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.Messages.*

class HandlerValidatorTest extends ValidatingTest {

  "Handler Validation" should {
    "produce an error when on clause references a command that does not exist" in {
      val input =
        """
          |domain entityTest is {
          |context EntityContext is {
          |entity Hamburger is {
          |  type StateFields is { field1: Number, field2: String }
          |  state HamburgerState of StateFields = {
          |    handler foo is {
          |      on command EntityCommand {
          |        set field HamburgerState.field1 to "345"
          |      }
          |      on event EntityEvent {
          |        set field HamburgerState.field2 to "678"
          |      }
          |    } described as "Irrelevant"
          |  } described as "Irrelevant"
          |} described as "Irrelevant"
          |} described as "Irrelevant"
          |} described as "Irrelevant"
          |""".stripMargin
      parseAndValidateDomain(input, CommonOptions.noMinorWarnings, shouldFailOnErrors = false) {
        case (_: Domain, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            Error,
            """Path 'EntityCommand' was not resolved, in OnMessageClause 'On command EntityCommand'
              |and it should refer to a Type""".stripMargin
          )
          assertValidationMessage(
            msgs,
            Error,
            """Path 'EntityEvent' was not resolved, in OnMessageClause 'On event EntityEvent'
              |and it should refer to a Type""".stripMargin
          )
      }
    }

    "produce an error when on clause references a message of the wrong type" in {
      val input =
        """
          |domain entityTest is {
          | context EntityContext is {
          |  entity Hamburger is {
          |   type StateFields is { field1: Number }
          |   state HamburgerState of Hamburger.StateFields is {
          |    handler foo is {
          |     on event EntityContext.Incoming {
          |       set field HamburgerState.field1 to "678"
          |     }
          |    }
          |   }
          |  }
          | }
          |}
          |""".stripMargin
      parseAndValidateDomain(input, CommonOptions.noMinorWarnings, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            Error,
            """Path 'EntityContext.Incoming' was not resolved, in OnMessageClause 'On event EntityContext.Incoming' because
              |the search through the parents ended at:
              |  entityTest.EntityContext
              |and there was no match to the elements of the PathId:
              |  EntityContext.Incoming
              |and it should refer to a Type""".stripMargin
          )
      }
    }

    "produce an error when on clause doesn't reference a message type" in {
      val input =
        """domain entityTest is {
          |context EntityContext is {
          |entity Hamburger is {
          |  type Incoming is String
          |  record Fields is { field1: Number }
          |  state HamburgerState of Fields is {
          |    handler foo is {
          |      on event Incoming {
          |       set field HamburgerState.field1 to "678"
          |     }
          |    }
          |  }
          |}
          |}
          |}
          |""".stripMargin
      parseAndValidateDomain(input, CommonOptions.noMinorWarnings, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            Error,
            "Type 'Incoming'(8:10) should reference an Event but is a String type instead"
          )
      }
    }

    "allow message clauses to name the message and it resolves" in {
      val input =
        """domain entityTest is {
          |context EntityContext is {
          |entity Hamburger is {
          |  type EntityCommand is command { foo: String }
          |  record Fields is { field1: String }
          |  state HamburgerState of Fields = {
          |    handler doit is {
          |      on command ec:EntityCommand {
          |        set field HamburgerState.field1 to "field ec.foo"
          |      }
          |    }
          |  }
          |}
          |}
          |}
          |""".stripMargin
      parseAndValidateDomain(input, CommonOptions.noMinorWarnings, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          msgs.justErrors mustBe empty

      }
    }
    "produce a warning for commands with no events sent" in {
      val input =
        """domain ignore is {
          |  context ignore is {
          |    command C is { field: Integer }
          |    command D is { field: Integer }
          |    outlet results is Integer
          |    entity example is {
          |      handler default is {
          |        on command C { ??? }
          |        on command D {
          |          send result Foo to outlet results
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin
      parseAndValidate(input, "test", CommonOptions(), shouldFailOnErrors = false) {
        case (root, rpi, messages: Messages) =>
          val warnings = messages.justWarnings.format
          info(warnings)
          warnings mustNot be(empty)
          warnings must include("commands should result in sending an event")
      }
    }
  }
}
