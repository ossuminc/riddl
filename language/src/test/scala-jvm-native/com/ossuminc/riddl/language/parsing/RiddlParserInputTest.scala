/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{AbstractTestingBasis, Await, URL, ec, pc}
import org.scalatest.Assertion

import scala.concurrent.duration.DurationInt

/** `RiddlParserInput`'s offset arithmetic, checked against a fixture in THIS repository.
  *
  * It used to fetch `dokn.riddl` from `raw.githubusercontent.com/ossuminc/riddl-examples` and
  * assert hardcoded byte offsets into it. That made a unit test of arithmetic depend on the network
  * and on the contents of a different repository: the moment riddl-examples was migrated to 2.0
  * syntax — moving a type out of domain scope, which shortened the second line — the offsets
  * shifted and this failed, having caught no defect in riddl at all.
  *
  * The fixture below lives in `language/input/parser-input/`, so the only way these offsets change
  * is if someone edits it, in which case the same commit updates these expectations. The URL path
  * is still exercised, via a local file URL, so `fromURL` and the loader remain under test.
  */
class RiddlParserInputTest extends AbstractTestingBasis {

  /** Byte offsets of the fixture, which is:
    * {{{
    * line 0 @  0  "domain Offsets is {"   (19 chars)
    * line 1 @ 20  ""
    * line 2 @ 21  "  author Stable is {"
    * line 3 @ 42  "    name: \"Offset Fixture\""
    * }}}
    */
  private val fixture = "language/input/parser-input/offsets.riddl"
  private val OffsetOfLine2 = 21
  private val OffsetInLine3 = 45
  private val Line0Range = (0, 20)

  private def checkRPI(rpi: RiddlParserInput): Assertion = {
    intercept[ArrayIndexOutOfBoundsException] { rpi.offsetOf(-1) }
    rpi.offsetOf(2) mustBe OffsetOfLine2
    rpi.lineOf(OffsetInLine3) mustBe 3
    rpi.rangeOf(0) mustBe Line0Range
    val loc = rpi.location(0)
    rpi.lineRangeOf(loc) mustBe Line0Range
    loc.col mustBe 1
    loc.line mustBe 1
  }

  "RiddlParserInput" should {
    "has empty" in {
      RiddlParserInput.empty mustBe EmptyParserInput
    }

    "construct from string" in {
      val rpi = RiddlParserInput("This is the text to parse", "construct from string")
      rpi.data.mustBe("This is the text to parse")
    }

    "construct from a URL and compute offsets" in {
      val url = URL.fromCwdPath(fixture)
      val rpi = Await.result(RiddlParserInput.fromURL(url), 10.seconds)
      rpi.data must startWith("domain Offsets is {")
      checkRPI(rpi)
    }
  }
}
