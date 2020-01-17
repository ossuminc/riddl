package com.yoppworks.ossum.riddl.language

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must

import scala.io.Source

class RiddlParserInputTest extends AnyWordSpec with must.Matchers {

  "RiddlParserInput" should {
    "produce input from scala.io.Source" in {
      val string = "helloooo"
      val source: Source = Source.fromString(string)
      val riddlParserInput = RiddlParserInput(source)
      riddlParserInput.data mustBe string
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
        }

      }
    }

    "rangeOf" should {
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
        ).foreach {
          case (in, out) =>
            input.rangeOf(in) mustBe out
        }
      }
    }
  }

  "SourceParserInput" should {
    "read data from a source" in {
      val string =
        """
          |hello I
          |am a
          |String
          |""".stripMargin
      SourceParserInput(Source.fromString(string), "string").data mustBe string
    }
  }

}
