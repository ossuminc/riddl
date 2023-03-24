/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput

/** Unit Tests For RegressionTests */
class RegressionTests extends ValidatingTest {
  "Regressions" should {
    "allow descriptions as a single string" in {
      val input = """domain foo is {
                    |} explained as { "foo" }
                    |""".stripMargin
      parseDefinition[Domain](RiddlParserInput(input)) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, _)) =>
          domain.description match {
            case Some(_) => succeed
            case None    => fail("no description")
          }
      }
    }
    "allow descriptions as a doc block" in {
      val input = """domain foo is {
                    |} explained as {
                    |  |ladeedah
                    |}
                    |""".stripMargin
      parseDefinition[Domain](RiddlParserInput(input)) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, _)) =>
          domain.description match {
            case Some(desc) => desc.lines.nonEmpty mustBe true
            case None       => fail("no description")
          }
      }
    }

    "allow simple descriptions" in {
      val input =
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
          |""".stripMargin
      parseDomainDefinition[Type](RiddlParserInput(input), _.types.last) match {
        case Left(errors) => fail(errors.format)
        case Right(_)     =>
          // info(typeDef.toString)
          succeed
      }
    }
    "catch types with predefined expression with a suffix" in {
      val input = """domain foo {
                    |  type Bug is IntegerRange
                    |}""".stripMargin

      def extract(root: AST.RootContainer): Type = {
        root.contents.head.types.head
      }
      parseTopLevelDomain[Type](input, extract) match {
        case Left(messages) => fail(messages.format)
        case Right((typ, rpi)) =>
          val expected: Type = Type(
            (2, 3, rpi),
            Identifier((2, 8, rpi), "Bug"),
            AliasedTypeExpression(
              (2, 15, rpi),
              PathIdentifier((2, 15, rpi), List("IntegerRange"))
            )
          )
          typ mustBe expected

      }
    }

    "catch types with bad predefined expression in an aggregation" in {
      val input = """domain foo {
                    |  type DateRange = Duration
                    |  type SomePlace = Location
                    |  type Thing is {
                    |    locationId: SomePlace,
                    |    schedule: DateRange+
                    |  }
                    |}
                    |""".stripMargin
      def extract(root: AST.RootContainer): Type = {
        root.contents.head.types(2)
      }
      parseTopLevelDomain[Type](input, extract) match {
        case Left(messages) => fail(messages.format)
        case Right((typ, rpi)) =>
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
                    PathIdentifier((5, 17, rpi), List("SomePlace"))
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
                      PathIdentifier((6, 15, rpi), List("DateRange"))
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
          typ mustBe expected
      }
    }
    "359: empty names in error message" in {
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
          |    type Bar is {}
          |      source UnusedWarningSource is {
          |        outlet Unused is type Bar
          |      }
          |     source SecondUnusedWarningSource is {
          |        outlet Unused is type Bar
          |     }
          |  }
          |}
          |""".stripMargin
      )
      parseAndValidateDomain(input) { case (_, _, msgs) =>
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
