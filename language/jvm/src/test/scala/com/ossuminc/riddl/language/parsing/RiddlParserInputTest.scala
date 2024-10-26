/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{JVMTestingBasis, URL, Await}
import com.ossuminc.riddl.utils.{pc, ec}

import org.scalatest.Assertion
import scala.io.Source
import scala.concurrent.duration.DurationInt

class RiddlParserInputTest extends JVMTestingBasis {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput._

  val fullPath = "/ossuminc/riddl-examples/main/src/riddl/dokn/dokn.riddl"
  val src = s"https://raw.githubusercontent.com$fullPath"

  def getFromURI(url: URL): String = {
    val contentF = io.load(url)
    Await.result(contentF, 10.seconds)
  }

  def checkRPI(rpi: RiddlParserInput, url: URL): Assertion = {
    rpi.root.toExternalForm.mustBe(src)
    val expected = getFromURI(url)
    rpi.data.mustBe(expected)
    val exception = intercept[ArrayIndexOutOfBoundsException] { rpi.offsetOf(-1) }
    rpi.offsetOf(2) mustBe 38
    rpi.lineOf(38) mustBe 2
    rpi.rangeOf(0) mustBe (0, 17)
    val loc = rpi.location(0)
    rpi.rangeOf(loc) mustBe (0, 17)
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

    "construct from URL" in {
      val url = URL(src)
      val rpi2 = Await.result(fromURL(url), 10.seconds)
      checkRPI(rpi2, url)
    }
  }
}
