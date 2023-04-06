/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.passes.validation

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.CommonOptions
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
          |      on command EntityCommand { example only { then set HamburgerState.field1 to 345 } }
          |      on event EntityEvent { example only { then set HamburgerState.field2 to string("678") } }
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
            "Path 'EntityCommand' was not resolved, in OnMessageClause " +
              "'On command EntityCommand', but should refer to a Type"
          )
          assertValidationMessage(
            msgs,
            Error,
            "Path 'EntityEvent' was not resolved, in OnMessageClause " +
              "'On event EntityEvent', but should refer to a Type"
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
          |     on event EntityContext.Incoming { example only { then set HamburgerState.field1 to 678 } }
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
            "Path 'EntityContext.Incoming' was not resolved, in OnMessageClause " +
              "'On event EntityContext.Incoming', but should refer to a Type"
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
          |      on event Incoming { example only {
          |       then set HamburgerState.field1 to 678 } }
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
            "Type 'Incoming'(8:10) should reference an Event but is " +
              "a String type instead"
          )
      }
    }

    "produce an error when on clause references a state field that does not exist" in {
      val input = """
                    |domain entityTest is {
                    |context EntityContext is {
                    |entity Hamburger is {
                    |  record Fields is { field1: Number }
                    |  state HamburgerState of Fields = {
                    |    handler foo is {
                    |      on command EntityCommand { example only {
                    |        then set nonExistingField to 123
                    |      } }
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
            "Path 'nonExistingField' was not resolved, in Example " +
              "'only', but should refer to a Field"
          )
      }
    }

    "produce an error when on clause sets state from a message field that does not exist" in {
      val input =
        """
           |domain entityTest is {
           |context EntityContext is {
           |entity Hamburger is {
           |  type EntityCommand is command { foo: Number }
           |  record Fields is {  field1: Number  }
           |  state HamburgerState of Fields = {
           |    handler foo is {
           |      on command EntityCommand { example only {
           |        then set field1 to @bar
           |      } }
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
            "Path 'bar' was not resolved, in Example 'only', but should refer to a Field"
          )
      }
    }

    "produce an error when on clause sets state from incompatible type of message field" in {
      val input = """
                    |domain entityTest is {
                    |context EntityContext is {
                    |entity Hamburger is {
                    |  type EntityCommand is command { foo: String }
                    |  record Fields is { field1: Number }
                    |  state HamburgerState of Fields = {
                    |    handler doit is {
                    |      on command EntityCommand { example only {
                    |        then set field1 to @foo
                    |      } }
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
            "assignment compatibility, but field:\n  field1"
          )
      }
    }
  }
}
