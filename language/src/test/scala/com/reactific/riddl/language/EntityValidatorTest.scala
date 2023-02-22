/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*

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
          info(msgs.format)
          msgs.count(_.kind.isError) mustBe 1
          // msgs.count(_.kind.isWarning) mustBe 1
          val numMissing =
            msgs.count(_.kind.isMissing)
          numMissing mustBe 3
          entity.options must contain(EntityIsFiniteStateMachine((3, 10, rpi)))
          entity.options must contain(EntityMessageQueue((3, 15, rpi)))
          entity.options must contain(EntityIsAggregate((3, 19, rpi)))
          entity.options must contain(EntityTransient((3, 30, rpi)))
          entity.options must contain(EntityIsAvailable((3, 41, rpi)))
      }
    }

    "handle entity with multiple states" in {
      val input =
        """entity MultiState is {
          |  options(fsm)
          |  record fields is { field: String  }
          |  state foo of ^fields is { handler x is {???} }
          |  state bar of ^fields is { handler x is {???} }
          |  handler fum is { ??? }
          |}""".stripMargin
      parseAndValidateInContext[Entity](input) {
        case (entity: Entity, _, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe empty
          entity.states.size mustBe 2
      }
    }
    "error for finite-state-machine entities without at least two states" in {
      val input =
        """entity MultiState is {
          |  options(fsm)
          |  record fields is { field: String }
          |  state foo of ^fields is {  handler x is {???}  }
          |}""".stripMargin
      parseAndValidateInContext[Entity](input) {
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
                    |  record fields is { field:  SomeType }
                    |  state foo of ^fields
                    |}""".stripMargin
      parseAndValidateInContext[Entity](input) {
        case (_: Entity, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            Error,
            "Entity 'Hamburger' has 1 state but no handlers"
          )
          assertValidationMessage(
            msgs,
            Error,
            "Path 'SomeType' was not resolved, in Field 'field', " +
              "but should refer to a Type"
          )
          assertValidationMessage(
            msgs,
            MissingWarning,
            "Type 'fields' should have a description"
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
          |    options (aggregate, transient)
          |    record fields is { field: SomeType }
          |    state field of ^fields is {  handler x is {???}  }
          |    handler foo is {}
          |  }
          |}
          |}
          |""".stripMargin
      parseAndValidateDomain(input) { case (_: Domain, _, msgs: Messages) =>
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
          |    options (aggregate, transient)
          |    outlet ridOfIt is event Message
          |    record fields is { field: SomeType } handler x is { ??? }
          |    state field of ^fields is { }
          |    handler baz is {
          |      on command DoIt {
          |        then send event Message() to outlet ridOfIt
          |      }
          |    }
          |  }
          |}
          |}
          |""".stripMargin
      parseAndValidateDomain(input) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          s"Field 'a' was not set in message constructor"
        )
      }

    }
  }
}
