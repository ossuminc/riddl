package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Aggregation
import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Topic
import com.yoppworks.ossum.riddl.language.AST.Type

/** Unit Tests For RegressionTests */
class RegressionTests extends ParsingTest {
  "Regressions" should {
    "allow descriptions as a single string" in {
      val input = """domain foo is {
                    |} explained as { "foo" }
                    |""".stripMargin
      parseDefinition[Domain](RiddlParserInput(input)) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(domain) => domain.description match {
            case Some(desc) => succeed
            case None       => fail("no description")
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
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(domain) => domain.description match {
            case Some(desc) =>
              desc.details.nonEmpty mustBe true
              desc.details.head.s mustEqual ("ladeedah")
            case None => fail("no description")
          }
      }
    }

    "allow simple descriptions" in {
      val input =
        """domain foo is {
          |type DeliveryInstruction is any of {
          |  FrontDoor(20), SideDoor(21), Garage(23), FrontDesk(24), DeliverToPostOffice(25)
          |} explained as {
          |  details {
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
          |  }
          |}
          |}
          |""".stripMargin
      parseDomainDefinition[Type](RiddlParserInput(input), _.types.last) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(typeDef) =>
          info(typeDef.toString)
          succeed
      }
    }

    "handle descriptions in fields of messages" in {
      val input =
        """topic TrackServices is {
          |  events {
          |    PreInducted is {
          |      expectedDeliveryTimeStamp: TimeStamp described by {
          |        |EXPECTEDDELIVERYDATE		Character Field Length = 10	CHAR10
          |        |EXPECTEDDELIVERYTIME		Character field, 8 characters long	CHAR8
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseDefinition[Topic](RiddlParserInput(input)) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(topic: Topic @unchecked) =>
          info(topic.toString)
          val evt = topic.events.head
          evt.typ match {
            case a: Aggregation => a.fields.head.description.nonEmpty mustBe
                true
            case _ => succeed
          }

      }
    }
  }
}
