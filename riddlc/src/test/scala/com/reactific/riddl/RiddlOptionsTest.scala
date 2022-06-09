package com.reactific.riddl

import com.reactific.riddl.RiddlOptions.Hugo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class RiddlOptionsTest extends AnyWordSpec with Matchers {
  "RiddlOptions" should {
    "handle --suppress-warnings options" in {
      val args = Array("--suppress-warnings")
      val result = RiddlOptions.parse(args)
      result match {
        case Some(options) =>
          options.commonOptions.showWarnings mustBe false
          options.commonOptions.showStyleWarnings mustBe false
          options.commonOptions.showMissingWarnings mustBe false
        case None => fail("Failed to parse options")
      }
    }

    "handle --suppress-style-warnings options" in {
      val args = Array("--suppress-style-warnings")
      val result = RiddlOptions.parse(args)
      result match {
        case Some(config) =>
          config.commonOptions.showWarnings mustBe true
          config.commonOptions.showStyleWarnings mustBe false
          config.commonOptions.showMissingWarnings mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --suppress-missing-warnings options" in {
      val args = Array("--suppress-missing-warnings")
      val result = RiddlOptions.parse(args)
      result match {
        case Some(config) =>
          config.commonOptions.showWarnings mustBe true
          config.commonOptions.showStyleWarnings mustBe true
          config.commonOptions.showMissingWarnings mustBe false
        case None => fail("Failed to parse options")
      }
    }
    "load from a file" in {
      val optionFile = Path.of("examples/src/riddl/ReactiveBBQ/ReactiveBBQ.conf")
      val options = RiddlOptions()
      val result = RiddlOptions.loadRiddlOptions(options, optionFile)

      result match {
        case None =>
          fail("Previously reported failures")
        case Some(opts) =>
          opts.command mustBe Hugo
          opts.commonOptions.showTimes mustBe true
          opts.commonOptions.verbose mustBe true
          opts.commonOptions.quiet mustBe false
          opts.commonOptions.dryRun mustBe false
          opts.commonOptions.showWarnings mustBe true
          opts.commonOptions.showStyleWarnings mustBe true
          opts.commonOptions.showMissingWarnings mustBe true
          val ho = opts.hugoOptions
          ho.inputFile mustBe Option(Path.of(
            "examples/src/riddl/ReactiveBBQ/ReactiveBBQ.riddl"
          ))
          ho.outputDir mustBe Option(Path.of(
            "examples/target/translator/ReactiveBBQ"
          ))
          ho.eraseOutput mustBe true
          ho.projectName mustBe Option("Reactive BBQ")
          ho.baseUrl mustBe Option(
            new java.net.URL("https://riddl.tech"))
          ho.sourceURL mustBe Option(
            new java.net.URL("https://github.com/reactific/riddl"))
          ho.editPath mustBe Option("/-/blob/main/examples/src/riddl/ReactiveBBQ")
          ho.siteLogo mustBe None
          ho.siteLogoPath mustBe Option("/images/RBBQ.png")
      }
    }
    "common opts override properly" in {
      val optionFile = Path.of("riddlc/src/test/input/common-overrides.conf")
      val options = RiddlOptions()
      val result = RiddlOptions.loadRiddlOptions(options, optionFile)

      result match {
        case None =>
          fail("Previously reported failures")
        case Some(opts) =>
          opts.commonOptions.showWarnings mustBe true
          opts.commonOptions.showStyleWarnings mustBe false
          opts.commonOptions.showMissingWarnings mustBe false
          true
      }
    }

    "empty args are eliminated" in {
      val opts = Array("parse", "", " -i", "  ", "file.riddl")
      RiddlOptions.parse(opts) match {
        case Some(opts) =>
          opts.parseOptions.inputFile mustBe Some(Path.of("file.riddl"))
        case None =>
          fail("Failed to parse options")
      }
    }

    "--hugo-path is supported" in {
      val opts = Array("from", "input.riddl", "--hugo-path", "/path/to/hugo")
      RiddlOptions.parse(opts) match {
        case Some(opts) =>
          opts.fromOptions.hugoPath mustBe(Some(Path.of("/path/to/hugo")))
        case None =>
          fail("failed to parse options")
      }
    }
  }
}
