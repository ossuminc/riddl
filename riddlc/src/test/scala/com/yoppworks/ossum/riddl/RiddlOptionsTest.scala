package com.yoppworks.ossum.riddl

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RiddlOptionsTest extends AnyWordSpec with Matchers {
  "RiddlOptions" should {
    "handle --suppress-warnings options" in {
      val args = Array("--suppress-warnings")
      val result = RiddlOptions.parse(args)
      result match {
        case Some(options) =>
          options.validatingOptions.showWarnings mustBe false
          options.validatingOptions.showStyleWarnings mustBe false
          options.validatingOptions.showMissingWarnings mustBe false
        case None =>
          fail("Failed to parse options")
      }
    }

    "handle --show-style-warnings options" in {
      val args = Array("--show-style-warnings")
      val result = RiddlOptions.parse(args)
      result match {
        case Some(config) =>
          config.validatingOptions.showWarnings mustBe true
          config.validatingOptions.showStyleWarnings mustBe true
          config.validatingOptions.showMissingWarnings mustBe false
        case None =>
          fail("Failed to parse options")
      }
    }

    "handle --show-missing-warnings options" in {
      val args = Array("--show-missing-warnings")
      val result = RiddlOptions.parse(args)
      result match {
        case Some(config) =>
          config.validatingOptions.showWarnings mustBe true
          config.validatingOptions.showStyleWarnings mustBe false
          config.validatingOptions.showMissingWarnings mustBe true
        case None =>
          fail("Failed to parse options")
      }
    }
  }
}
