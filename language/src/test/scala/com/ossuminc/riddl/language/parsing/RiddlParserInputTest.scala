package com.ossuminc.riddl.language.parsing

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.Assertion
import java.net.{URL, URI}
import java.io.File
import scala.io.Source

class RiddlParserInputTest extends AnyWordSpec with Matchers {

  val fullPath = "/ossuminc/riddl-examples/main/src/riddl/dokn/dokn.riddl"
  val src = s"https://raw.githubusercontent.com$fullPath"

  def getFromURI(uri: URI): String = {
    val source: Source = Source.fromURL(uri.toURL)
    try {
      source.getLines().mkString("\n")
    } finally {
      source.close()
    }
  }

  def checkRPI(rpi: RiddlParserInput, uri: URI): Assertion = {
    rpi.path match
      case Some(path) => path.toString.mustBe(fullPath)
      case x          => fail(s"Unexpected: $x")
    rpi.origin.mustBe(src)
    rpi.from.mustBe(src)
    rpi.root.mustBe(new File("/ossuminc/riddl-examples/main/src/riddl/dokn/dokn.riddl"))
    val expected = getFromURI(uri)
    rpi.data.mustBe(expected)
    val exception = intercept[ArrayIndexOutOfBoundsException] { rpi.offsetOf(-1) }
    rpi.offsetOf(2) mustBe (38)
    rpi.lineOf(38) mustBe (2)
    rpi.rangeOf(0) mustBe (0, 17)
    val loc = rpi.location(0)
    rpi.rangeOf(loc) mustBe (0, 17)
    loc.col mustBe (1)
    loc.line mustBe (1)
  }

  "RiddlParserInput" should {
    "has empty" in {
      RiddlParserInput.empty mustBe EmptyParserInput()
    }

    "construct from string" in {
      val rpi = RiddlParserInput("This is the text to parse")
      rpi.data.mustBe("This is the text to parse")
      rpi.from
    }

    "construct from URI" in {
      val uri = URI.create(src)
      val rpi = RiddlParserInput(uri)
      checkRPI(rpi, uri)
    }
    "construct from URL" in {
      val uri = URI.create(src)
      val rpi2 = RiddlParserInput(uri.toURL)
      checkRPI(rpi2, uri)
    }
  }
}
