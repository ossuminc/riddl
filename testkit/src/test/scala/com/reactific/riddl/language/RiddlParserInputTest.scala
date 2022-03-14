package com.reactific.riddl.language

import com.reactific.riddl.language.parsing.{RiddlParserInput, SourceParserInput}
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

class RiddlParserInputTest extends AnyWordSpec with must.Matchers {

  "RiddlParserInput" should {
    "produce input from scala.io.Source" in {
      val string = "helloooo"
      val source: Source = Source.fromString(string)
      val riddlParserInput = RiddlParserInput(source)
      riddlParserInput.data mustBe string
      riddlParserInput.innerLength mustBe 8
      riddlParserInput.length mustBe 8
    }

    "length" should {
      "be equal to input length" in {
        val inputs = List(
          "",
          "hi",
          "this is a little bit longer",
          """"this one is even longer,
            |and it has newlines
            |""".stripMargin
        )

        for (input <- inputs) {
          RiddlParserInput(input).length mustBe input.length
          RiddlParserInput(input).innerLength mustBe input.length
        }
      }
    }

    "rangeOf" should {
      "convert a Location to a pair " in {
        val input = RiddlParserInput("""12345
                                       |6789
                                       |0
                                       |1234
                                       |56
                                       |""".stripMargin)
        Map((1 -> 4) -> (0, 6), (4 -> 3) -> (13, 18)).foreach { case (loc, offset) =>
          input.rangeOf(Location(loc)) mustBe offset
        }

      }
      "return the line and column of a char index" in {
        val input = RiddlParserInput("""12345
                                       |6789
                                       |0
                                       |1234
                                       |56
                                       |""".stripMargin)

        List(
          0 -> (0, 6),
          4 -> (0, 6),
          6 -> (6, 11),
          7 -> (6, 11),
          11 -> (11, 13),
          12 -> (11, 13),
          15 -> (13, 18),
          0 -> (0, 6),
          4 -> (0, 6),
          6 -> (6, 11),
          7 -> (6, 11),
          11 -> (11, 13),
          12 -> (11, 13),
          15 -> (13, 18)
        ).foreach { case (in, out) =>
          val result = input.rangeOf(in)
          result mustBe out
        }
      }
    }
  }

  "SourceParserInput" should {
    "read data from a source" in {
      val string = """
                     |hello I
                     |am a
                     |String
                     |""".stripMargin
      SourceParserInput(Source.fromString(string), "string").data mustBe string
    }
  }

}
