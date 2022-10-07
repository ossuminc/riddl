package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.AST.Type
import com.reactific.riddl.language.parsing.RiddlParserInput

/** Unit Tests For RegressionTests */
class RegressionTests extends ParsingTest {
  "Regressions" should {
    "allow descriptions as a single string" in {
      val input = """domain foo is {
                    |} explained as { "foo" }
                    |""".stripMargin
      parseDefinition[Domain](RiddlParserInput(input)) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, _)) => domain.description match {
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
        case Right((domain, _)) => domain.description match {
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
                    |  type Bug is IntegerRange briefly "oops"
                    |}""".stripMargin

      def extract(root: AST.RootContainer): Type = {
        root.contents.head.types.head
      }
      parseTopLevelDomain[Type](input, extract) match {
        case Left(messages) =>
          messages mustNot be(empty)
          messages.size mustBe (1)
          val msg = messages.head
          msg.kind mustBe (Messages.Error)
          msg.format.contains("IntegerRange") mustBe (true)
          succeed
        case _ => fail("should have generated an error")
      }
    }

    "case types with predefined expression in an aggregation" in {
      val input = """domain foo {
                    |  type DateRange = Duration
                    |  type Thing is {
                    |    locationId: LocationId,
                    |    schedule: DateRange+
                    |  }
                    |}
                    |""".stripMargin
      def extract(root: AST.RootContainer): Type = {
        root.contents.head.types.head
      }
      parseTopLevelDomain[Type](input, extract) match {
        case Left(messages) =>
          messages mustNot be(empty)
          messages.size mustBe (1)
          val msg = messages.head
          msg.kind mustBe (Messages.Error)
          msg.format.contains("DateRange+") mustBe (true)
          succeed
        case _ => fail("should have generated an error")

      }
    }
  }
}
