package com.ossuminc.riddl.language.parsing

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.Assertion

import java.net.{URI, URL}
import java.io.File
import scala.None
import scala.io.{Codec, Source}

class RiddlParserInputTest extends AnyWordSpec with Matchers {

  val fullPath = "/ossuminc/riddl-examples/main/src/riddl/dokn/dokn.riddl"
  val src = s"https://raw.githubusercontent.com$fullPath"
  val expected: String = """domain dokn is {
                   |    type Address = {
                   |        line1: String,
                   |        line2: String,
                   |        line3: String,
                   |        line4: String,
                   |        city: String,
                   |        province: String,
                   |        country: String,
                   |        postalCode: String
                   |    }
                   |""".stripMargin

  def getFromURI(uri: URI): String = {
    val source: Source = Source.fromURL(uri.toURL)
    try {
      source.getLines().mkString("\n")
    } finally {
      source.close()
    }
  }

  def checkRPI(rpi: RiddlParserInput): Assertion = {
    rpi.data.substring(0, 16) mustBe (expected.substring(0, 16))
    val exception = intercept[ArrayIndexOutOfBoundsException] {
      rpi.offsetOf(-1)
    }
    rpi.offsetOf(2) mustBe (38)
    rpi.lineOf(38) mustBe (2)
    rpi.rangeOf(0) mustBe (0, 17)
    val loc = rpi.location(0)
    rpi.rangeOf(loc) mustBe (0, 17)
    loc.col mustBe (1)
    loc.line mustBe (1)
  }

  def checkRPI(rpi: RiddlParserInput, uri: URI): Assertion = {
    rpi.path match
      case Some(path) => path.toString.mustBe(fullPath)
      case x          => fail(s"Unexpected: $x")
    rpi.origin.mustBe(src)
    rpi.from.mustBe(src)
    rpi.root.mustBe(new File("/ossuminc/riddl-examples/main/src/riddl/dokn/dokn.riddl"))
    val expected = getFromURI(uri)
    checkRPI(rpi)
  }

  "RiddlParserInput" should {
    "has empty" in {
      RiddlParserInput.empty mustBe EmptyParserInput()
    }

    "has path" in {
      RiddlParserInput.empty.path must be(None)
    }

    "has prettyIndex" in {
      RiddlParserInput.empty.prettyIndex(0) must be("empty(1:1)")
    }

    "has root" in {
      RiddlParserInput.empty.root must be(File("/"))
    }

    "construct from string" in {
      val rpi = RiddlParserInput(expected)
      checkRPI(rpi)
      rpi.from must be("empty")
    }

    "construct from string and origin" in {
      val rpi = RiddlParserInput(expected, "test")
      checkRPI(rpi)
      rpi.from must be("test")
    }

    "construct from URI" in {
      val uri = URI.create(src)
      val rpi = RiddlParserInput(uri)
      checkRPI(rpi, uri)
      rpi.isEmpty must be(false)
    }
    "construct from URL" in {
      val uri = URI.create(src)
      val rpi = RiddlParserInput(uri.toURL)
      checkRPI(rpi, uri)
      rpi.isEmpty must be(false)
    }
    "construct from Source" in {
      val uri = URI.create(src)
      val source = Source.fromString(expected)
      val rpi = RiddlParserInput(source)
      checkRPI(rpi)
      rpi.isEmpty must be(false)
    }
  }
}
