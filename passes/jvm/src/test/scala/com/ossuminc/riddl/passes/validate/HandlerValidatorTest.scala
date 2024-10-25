/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Domain
import com.ossuminc.riddl.language.{Messages}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, ec, CommonOptions}
import org.scalatest.TestData

class HandlerValidatorTest extends AbstractValidatingTest {

  "Handler Validation" should {
    "produce an error when on clause references a command that does not exist" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain entityTest is {
          |context EntityContext is {
          |entity Hamburger is {
          |  type StateFields is { field1: Number, field2: String }
          |  state HamburgerState of StateFields
          |  handler foo is {
          |    on command EntityCommand {
          |      set field HamburgerState.field1 to "345"
          |    }
          |    on event EntityEvent {
          |      set field HamburgerState.field2 to "678"
          |    }
          |  } with { briefly as "Irrelevant" }
          |} with { briefly as "Irrelevant" }
          |} with { briefly as "Irrelevant" }
          |} with { briefly as "Irrelevant" }
          |""".stripMargin,
        td
      )
      pc.setOptions(CommonOptions.noMinorWarnings)
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          """Path 'EntityCommand' was not resolved, in OnMessageClause 'command EntityCommand'
              |because the sought name, 'EntityCommand', was not found in the symbol table,
              |and it should refer to a Type""".stripMargin
        )
        assertValidationMessage(
          msgs,
          Error,
          """Path 'EntityEvent' was not resolved, in OnMessageClause 'event EntityEvent'
              |because the sought name, 'EntityEvent', was not found in the symbol table,
              |and it should refer to a Type""".stripMargin
        )
        msgs.justErrors.size must be(2)
      }
    }

    "produce an error when on clause references a message of the wrong type" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain entityTest is {
          | context EntityContext is {
          |  event HandleMe is { field0: String }
          |  entity Hamburger is {
          |   type StateFields is { field1: Number }
          |   state HamburgerState of Hamburger.StateFields
          |   handler foo is {
          |    on event EntityContext.Incoming {
          |     set field HamburgerState.field1 to "678"
          |    }
          |   }
          |  }
          | } with {
          |  term Incoming is "This is a term definition to generate an error"
          | }
          |}
          |""".stripMargin,
        td
      )
      pc.setOptions(CommonOptions.noMinorWarnings)
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Path 'EntityContext.Incoming' was not resolved, in Context 'EntityContext'\n" +
            "because the name 'Incoming' was not found in Context 'EntityContext'\n" +
            "and it should refer to a Type"
        )
        msgs.justErrors.size must be(1)
      }
    }

    "allow message clauses to name the message and it resolves" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain entityTest is {
          |context EntityContext is {
          |entity Hamburger is {
          |  type EntityCommand is command { foo: String }
          |  record Fields is { field1: String }
          |  state HamburgerState of Fields
          |  handler doit is {
          |    on command EntityCommand {
          |      set field HamburgerState.field1 to "field ec.foo"
          |    }
          |  }
          |}
          |}
          |}
          |""".stripMargin,
        td
      )
      pc.setOptions(CommonOptions.noMinorWarnings)
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.justErrors mustBe empty
      }
    }
    "produce a warning for commands with no events sent" in { (td: TestData) =>
      val input =
        """domain ignore is {
          |  context ignore is {
          |    command C is { field: Integer }
          |    command D is { field: Integer }
          |    source foo is { outlet results is Integer }
          |    entity example is {
          |      handler default is {
          |        on command C { ??? }
          |        on command D {
          |          send result Foo to outlet foo.results
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin
      parseAndValidate(input, "test", shouldFailOnErrors = false) { case (_, messages: Messages) =>
        val warnings = messages.justWarnings.format
        // info(warnings)
        warnings mustNot be(empty)
        warnings must include("commands should result in sending an event")
      }
    }
  }
}
