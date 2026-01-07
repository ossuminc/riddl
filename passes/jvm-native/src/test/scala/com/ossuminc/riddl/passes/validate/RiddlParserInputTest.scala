/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.AbstractTestingBasisWithTestData
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.parsing.RiddlParserInput

import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.TestData
import scala.io.Source

class RiddlParserInputTest extends AbstractTestingBasisWithTestData {

  "RiddlParserInput" should {
    "length" should {
      "be equal to input length" in { (td: TestData) =>
        val inputs = List(
          "",
          "hi",
          "this is a little bit longer",
          """"this one is even longer,
            |and it has newlines
            |""".stripMargin
        )

        for {
          input <- inputs
          rpi:RiddlParserInput = RiddlParserInput(input, td)
        } do {
          rpi.length.mustBe(input.length)
          rpi.innerLength.mustBe(input.length)
        }
      }
    }

    "lineRangeOf" should {
      "convert a Location to a pair " in { (td: TestData) =>
        val rpi: RiddlParserInput = RiddlParserInput(
          """12345
            |6789
            |0
            |1234
            |56
            |""".stripMargin,
          td
        )
        Map((1 -> 4) -> (0, 6), (4 -> 3) -> (13, 18)).foreach { case (loc, offset) =>
          rpi.lineRangeOf(At(loc,rpi)).mustBe(offset)
        }

      }
      "return the line and column of a char index" in { (td: TestData) =>
        val input = RiddlParserInput(
          """12345
                                       |6789
                                       |0
                                       |1234
                                       |56
                                       |""".stripMargin,
          td
        )

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
          result.mustBe(out)
        }
      }
    }
  }
}
