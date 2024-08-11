/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.command.{Command, CommandOptions, CommonOptionsHelper}
import com.ossuminc.riddl.utils.TestingBasis
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class CommonOptionsTest extends TestingBasis {
  "CommonOptions" should {
    "handle --suppress-warnings options" in {
      val args = Array("--suppress-warnings")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe false
          config.showStyleWarnings mustBe false
          config.showMissingWarnings mustBe false
          config.showUsageWarnings mustBe false
          config.showInfoMessages mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --suppress-style-warnings options" in {
      val args = Array("--suppress-style-warnings")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe false
          config.showMissingWarnings mustBe true
          config.showUsageWarnings mustBe true
          config.showInfoMessages mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --suppress-missing-warnings options" in {
      val args = Array("--suppress-missing-warnings")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe true
          config.showMissingWarnings mustBe false
          config.showUsageWarnings mustBe true
          config.showInfoMessages mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --suppress-usage-warnings options" in {
      val args = Array("--suppress-usage-warnings")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe true
          config.showMissingWarnings mustBe true
          config.showUsageWarnings mustBe false
          config.showInfoMessages mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --suppress-info-messages options" in {
      val args = Array("--suppress-info-messages")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe true
          config.showMissingWarnings mustBe true
          config.showUsageWarnings mustBe true
          config.showInfoMessages mustBe false
        case None => fail("Failed to parse options")
      }
    }

    "handle --hide-warnings options" in {
      val args = Array("--hide-warnings")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe false
          config.showStyleWarnings mustBe false
          config.showMissingWarnings mustBe false
          config.showUsageWarnings mustBe false
          config.showInfoMessages mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --hide-style-warnings options" in {
      val args = Array("--hide-style-warnings")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe false
          config.showMissingWarnings mustBe true
          config.showUsageWarnings mustBe true
          config.showInfoMessages mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --hide-missing-warnings options" in {
      val args = Array("--hide-missing-warnings")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe true
          config.showMissingWarnings mustBe false
          config.showUsageWarnings mustBe true
          config.showInfoMessages mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --hide-usage-warnings options" in {
      val args = Array("--hide-usage-warnings")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe true
          config.showMissingWarnings mustBe true
          config.showUsageWarnings mustBe false
          config.showInfoMessages mustBe true
        case None => fail("Failed to parse options")
      }
    }

    "handle --hide-info-messages options" in {
      val args = Array("--hide-info-messages")
      val (common, _) = CommonOptionsHelper.parseCommonOptions(args)
      common match {
        case Some(config) =>
          config.showWarnings mustBe true
          config.showStyleWarnings mustBe true
          config.showMissingWarnings mustBe true
          config.showUsageWarnings mustBe true
          config.showInfoMessages mustBe false
        case None => fail("Failed to parse options")
      }
    }


    "options at top level do not override in common object" in {
      val optionFile = Path.of("riddlc/src/test/input/common-overrides.conf")
      CommonOptionsHelper.loadCommonOptions(optionFile) match {
        case Left(messages) => fail(messages.format)
        case Right(opts) =>
          opts.showWarnings mustBe false
          opts.showStyleWarnings mustBe false
          opts.showMissingWarnings mustBe false
      }
    }

    "empty args are eliminated" in {
      val opts = Array("--show-times", "test",  "", "  ", "file.riddl", "arg1")
      val (comm, remaining) = CommonOptionsHelper.parseCommonOptions(opts)
      comm match {
        case Some(options) =>
          options.showTimes must be(true)
          Commands.parseCommandOptions(remaining) match {
            case Right(options) => options.inputFile must be(Some(Path.of("file.riddl")))
            case Left(messages) => fail(messages.format)
          }
        case _ => fail("Failed to parse options")
      }
    }

    "load message related common options from a file" in {
      val optionFile = Path.of("command/src/test/input/message-options.conf")
      CommonOptionsHelper.loadCommonOptions(optionFile) match {
        case Right(opts: CommonOptions) =>
          opts.showTimes mustBe true
          opts.showIncludeTimes mustBe true
          opts.verbose mustBe true
          opts.dryRun mustBe false
          opts.quiet mustBe false
          opts.showWarnings mustBe true
          opts.showMissingWarnings mustBe false
          opts.showStyleWarnings mustBe false
          opts.showUsageWarnings mustBe true
          opts.showInfoMessages mustBe  false
          opts.debug mustBe true
          opts.pluginsDir must be ( Some(Path.of(".")) )
          opts.sortMessagesByLocation must be ( true )
          opts.groupMessagesByKind must be ( true)
          opts.noANSIMessages must be ( true )
          opts.maxParallelParsing must be (12)
          opts.maxIncludeWait must be ( 1.minute )
          opts.warningsAreFatal must be(true)
        case Left(messages) =>
          fail(messages.format)
      }
    }

    "parse less frequently used options" in {
      val opts = Array(
        "--suppress-usage-warnings",
        "--suppress-info-messages",
        "--hide-info-messages",
        "--plugins-dir:.",
        "--max-include-wait:5",
        "--max-parallel-parsing:12",
        "test", "", "  ", "file.riddl", "arg1")
      val (comm, remaining) = CommonOptionsHelper.parseCommonOptions(opts)
      comm match {
        case Some(options: CommonOptions) =>
          options.showUsageWarnings must be(false)
          options.showInfoMessages must be(false)
          options.pluginsDir must be(Some(Path.of(".")))
          options.maxIncludeWait must be(5.seconds)
          options.maxParallelParsing must be(12)
          Commands.parseCommandOptions(remaining) match {
            case Right(options) => options.inputFile must be( Some(Path.of("file.riddl")))
            case Left(messages) => fail(messages.format)
          }
        case x => fail(s"Failed to parse options: $x")
      }

    }
  }
}
