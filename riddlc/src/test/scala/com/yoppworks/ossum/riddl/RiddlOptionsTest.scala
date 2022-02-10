package com.yoppworks.ossum.riddl

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL
import java.nio.file.Path

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
        case None => fail("Failed to parse options")
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
        case None => fail("Failed to parse options")
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
        case None => fail("Failed to parse options")
      }
    }
    "load from a file" in {
      val optionFile = Path.of("riddlc/src/test/input/hugoOptions.conf")
      val options = RiddlOptions().copy(optionsPath = Option(optionFile))
      val result = RiddlOptions.loadHugoTranslatingOptions(options)

      result match {
        case None =>
          fail("Previously reported failures")
        case Some(opts) =>
          opts.projectName mustBe Option("Reactive BBQ")
          opts.outputPath mustBe Option(Path.of(
            "/Users/reid/Code/Yoppworks/Ossum/riddl/examples/target/translator/ReactiveBBQ"
          ))
          opts.baseUrl mustBe Option(new URL("https://riddl.yoppworks.com"))
          opts.sourceURL mustBe Option(new URL( "https://gitlab.com/Yoppworks/Ossum/riddl/"))
          opts.editPath mustBe Option("blob/main/examples/src/riddl/ReactiveBBQ")
      }
    }
  }
}
