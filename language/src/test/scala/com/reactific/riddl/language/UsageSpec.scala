package com.reactific.riddl.language
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
                    |    type T is D.T
                    |    command DoIt is { ref: Id(C.E), f1: C.T }
                    |    entity E is {
                    |      state S is {
                    |        fields {
                    |          f1: D.T
                    |          f2: C.T
                    |        }
                    |        handler H is {
                    |         on command DoIt {
                    |          then set S.f2 to @DoIt.f1
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
          info(result.messages.format)
          info(
            result.uses.map { case (key, value) =>
              s"${key.identify} => ${value.map(_.identify).mkString(",")}"
            }.mkString("\n")
          )

      }
    }
  }

}
