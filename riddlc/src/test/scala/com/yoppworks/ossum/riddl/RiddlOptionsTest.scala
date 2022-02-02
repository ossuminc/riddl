package com.yoppworks.ossum.riddl

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scopt.OParser

class RiddlOptionsTest extends AnyWordSpec with Matchers {
  "RiddlOptions" should {
    "handle --suppress-warnings options" in {
      val args = Seq("--suppress-warnings")
      OParser.parse(RiddlOptions.parser, args, RiddlOptions()) match {
        case Some(config) =>
          config.showWarnings mustBe false
          config.showStyleWarnings mustBe false
          config.showMissingWarnings mustBe false
        case None =>
          fail("Failed to parse options")
      }
    }
    "handle --show-style-warnings options" in {
      val args = Seq("--show-style-warnings")
      OParser.parse(RiddlOptions.parser, args, RiddlOptions()) match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe true
          config.showMissingWarnings mustBe false
        case None =>
          fail("Failed to parse options")
      }

    }
    "handle --show-missing-warnings options" in {
      val args = Seq("--show-missing-warnings")
      OParser.parse(RiddlOptions.parser, args, RiddlOptions()) match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe false
          config.showMissingWarnings mustBe true
        case None =>
          fail("Failed to parse options")
      }
    }
  }
}
