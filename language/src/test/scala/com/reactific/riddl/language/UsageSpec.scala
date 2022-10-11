package com.reactific.riddl.language
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UsageSpec extends AnyWordSpec with Matchers {

  "Usage" should {
    "correctly associated definitions" in {
      val input = """
                    |domain D is {
                    |  type T is Number
                    |  context C is {
                    |    command DoIt is { ref: Id(C.E), f1: C.T }
                    |    type T is D.T
                    |    entity E is {
                    |      state S is {
                    |        fields {
                    |          f2: D.T,
                    |          f3: C.T
                    |        }
                    |        handler H is {
                    |          on command DoIt {
                    |            then set S.f3 to @DoIt.f1
                    |          }
                    |        }
                    |      }
                    |    }
                    |  }
                    |}
                    |""".stripMargin
      Riddl.parse(RiddlParserInput(input), CommonOptions()) match {
        case Left(messages) => fail(messages.format)
        case Right(model) =>
          val result = Validation.validate(model, CommonOptions())
          val errors = result.messages.filter(_.kind > Messages.Warning)
          info("Uses:\n" + result.uses.map { case (key, value) =>
            s"${key.identify} => ${value.map(_.identify).mkString(",")}"
          }.mkString("\n"))
          info("Used By:\n" + result.usedBy.map { case (key, value) =>
            s"${key.identify} <= ${value.map(_.identify).mkString(",")}"

          }.mkString("\n"))
          if (errors.nonEmpty) { fail(errors.format) }
          else {
            // ensure usedBy and uses are reflective
            for {
              (user, uses) <- result.uses
              use <- uses
            } {
              result.usedBy.keys must contain(use)
              result.usedBy(use) must contain(user)
            }
            // print it out for debugging
            println("Uses:")
            result.uses.foreach { case (k, v) =>
              println(k.identify + " => " + v.map(_.identify).mkString(", "))
            }
            println("\nUsed By:")
            result.usedBy.foreach { case (k, v) =>
              println(k.identify + " => " + v.map(_.identify).mkString(", "))
            }
            // But let's make sure we get the right results
            result.uses.size mustBe (7)
            result.usedBy.size mustBe (6)
            val entityE = model.contents.head.contexts.head.entities.head
            val command = model.contents.head.contexts.head.types.head
            val fieldRef = command.typ match {
              case x: MessageType => x.fields.find(_.id.value == "ref").get
              case _              => fail("Wrong kind of type expression")
            }
            result.uses(fieldRef).contains(entityE)
            result.usedBy(entityE).contains(command)
          }
      }
    }
    "unused entities generate a warning" in {
      val input = """domain foo {
                    |  context Bar is {
                    |    command ACommand is { ??? } described as "AC"
                    |    entity fooBar is {
                    |      state AState {
                    |        { field: Number }
                    |        handler fooBarHandlerForAState is {
                    |          on command ACommand {
                    |            ???
                    |          } described as "inconsequential"
                    |        } described as "inconsequential"
                    |      } described as "inconsequential"
                    |    } described as "unused"
                    |  } described as "inconsequential"
                    |} described as "inconsequential"
                    |""".stripMargin
      Riddl.parse(RiddlParserInput(input)) match {
        case Left(messages) => fail(messages.format)
        case Right(model) =>
          val commonOptions = CommonOptions()
          val result = Validation.validate(model, commonOptions)
          info(result.messages.format)
          result.messages.isIgnorable mustBe (false)
          result.messages.size mustBe (1)
          val error = result.messages.head.format
          error must include("Entity 'fooBar' is unused")
      }

    }
    "unused types generate a warning" in {
      val input = """domain foo {
                    |  type Bar = Number described as "consequential"
                    |} described as "inconsequential"
                    |""".stripMargin
      Riddl.parse(RiddlParserInput(input), CommonOptions()) match {
        case Left(messages) => fail(messages.format)
        case Right(model) =>
          val result = Validation.validate(model, CommonOptions())
          result.messages.size mustBe (1)
          result.messages.head.format must include("Type 'Bar' is unused")
      }
    }
  }
}
