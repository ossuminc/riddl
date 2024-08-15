/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import org.scalatest.TestData

/** Unit Tests For RegressionTests */
class RegressionTests extends ValidatingTest {
  "Regressions" should {
    "allow descriptions as a single string" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is { ??? }
          |explained as { "foo" }
          |""".stripMargin,td)
      parseDefinition[Domain](input) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, _)) =>
          domain.description match {
            case Some(_) => succeed
            case None    => fail("no description")
          }
      }
    }
    "allow descriptions as a doc block" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is { ??? }
          |explained as {
          |  |ladeedah
          |}
          |""".stripMargin,td)
      parseDefinition[Domain](input) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, _)) =>
          domain.description match {
            case Some(desc) => desc.lines.nonEmpty.mustBe(true)
            case None       => fail("no description")
          }
      }
    }

    "allow simple descriptions" in {  (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |type DeliveryInstruction is any of {
          |  FrontDoor(20), SideDoor(21), Garage(23), FrontDesk(24), DeliverToPostOffice(25)
          |} explained as {
          |    |20 Front door
          |    |21 Side door
          |    |23 Garage
          |    |24 Front desk or superintendent
          |    |25 Deliver to post office
          |    |20 Porte d'entrée
          |    |21 Porte de côté
          |    |23 Garage
          |    |24 Réception ou surveillant
          |    |25 Livrer au bureau de poste
          |}
          |}
          |""".stripMargin,td)
      parseDomainDefinition[Type](input, _.types.last) match {
        case Left(errors) => fail(errors.format)
        case Right(_)     =>
          // info(typeDef.toString)
          succeed
      }
    }
    "catch types with predefined expression with a suffix" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  type Bug: IntegerRange
          |}""".stripMargin,td)

      def extract(root: Root): Type = {
        root.domains.head.types.head
      }
      parseTopLevelDomain[Type](input, extract) match {
        case Left(messages) =>
          val errors = messages.justErrors
          errors.size.mustBe(1)
          errors.head.message.contains ("whitespace after keyword")
        case Right((typ, rpi)) =>
          import scala.language.postfixOps
          val expected: Type = Type(
            (2, 3, rpi),
            Identifier((2, 8, rpi), "Bug"),
            AliasedTypeExpression(
              (2, 15, rpi),
              "type",
              PathIdentifier((2, 20, rpi), List("IntegerRange"))
            )
          )
          typ.mustBe(expected)
      }
    }

    "catch types with bad predefined expression in an aggregation" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  type DateRange = Duration
          |  type SomePlace = Location
          |  type Thing is {
          |    locationId: type SomePlace,
          |    schedule: type DateRange+
          |  }
          |}
          |""".stripMargin,td)
      def extract(root: Root): Type = {
        root.domains.head.types(2)
      }
      parseTopLevelDomain[Type](input, extract) match {
        case Left(messages) => fail(messages.format)
        case Right((typ, rpi)) =>
          import scala.language.postfixOps
          val expected: Type = Type(
            (4, 3, rpi),
            Identifier((4, 8, rpi), "Thing"),
            Aggregation(
              (4, 17, rpi),
              List(
                Field(
                  (5, 5, rpi),
                  Identifier((5, 5, rpi), "locationId"),
                  AliasedTypeExpression(
                    (5, 17, rpi),
                    "type",
                    PathIdentifier((5, 22, rpi), List("SomePlace"))
                  ),
                  None,
                  None
                ),
                Field(
                  (6, 5, rpi),
                  Identifier((6, 5, rpi), "schedule"),
                  OneOrMore(
                    (6, 15, rpi),
                    AliasedTypeExpression(
                      (6, 15, rpi),
                      "type",
                      PathIdentifier((6, 20, rpi), List("DateRange"))
                    )
                  ),
                  None,
                  None
                )
              )
            ),
            None,
            None
          )
          typ.mustBe(expected)
      }
    }
    "357: Nested fields in State constructors do not compile" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Example is {
          |   context ExampleContext is {
          |     type Info {
          |       name: String
          |     }
          |
          |    command Foo {
          |     info: ExampleContext.Info
          |    }
          |
          |    entity ExampleEntity is {
          |      handler ExampleHandler is {
          |          on command Foo {
          |            morph entity ExampleContext.ExampleEntity to state ExampleEntity.FooExample with command Foo
          |          }
          |          on other {
          |            error "You must first create an event using ScheduleEvent command."
          |          }
          |      }
          |
          |      record FooExampleState is {
          |        info: String,
          |        name: String
          |      }
          |      state FooExample of FooExampleState
          |      handler FooExampleHandler {
          |        on other {
          |          error "You must first create an event using ScheduleEvent command."
          |        }
          |      }
          |    }
          |	 }
          |}
          |""".stripMargin,td)
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        val errors: Messages.Messages = msgs.justErrors
        errors must be(empty)
      }
    }
    "359: empty names in error message" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Example is {
          |  context ErrorsToDemonstrateClutter{
          |    type IntentionalErrors {
          |      garbage: Blah,
          |      moreGarbage: BlahBlah
          |    }
          |  }
          |  context ExampleContext is {
          |    type Foo {
          |       name: String
          |     }
          |
          |    type Foo {
          |      number: Integer
          |    }
          |  }
          |  context WarningsToDemonstrateClutter{
          |    type Bar is { ??? }
          |    source UnusedWarningSource is {
          |      outlet Unused is type Bar
          |    }
          |    source SecondUnusedWarningSource is {
          |      outlet Unused is type Bar
          |    }
          |  }
          |}
          |""".stripMargin,td)
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, msgs) =>
        msgs mustNot be(empty)
        val duplicate =
          msgs.find(_.message.contains("has duplicate content names"))
        duplicate mustNot be(empty)
        val dup = duplicate.get
        dup.message must include(
          """Context 'ExampleContext' has duplicate content names:
                |  Type 'Foo' at empty(9:5), and Type 'Foo' at empty(13:5)
                |""".stripMargin
        )
      }
    }
  }
}
