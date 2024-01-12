/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt
import java.nio.file.Path

class CommonOptionsTest extends AnyWordSpec with Matchers {
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
      CommandOptions.loadCommonOptions(optionFile) match {
        case Left(messages) => fail(messages.format)
        case Right(opts) =>
          opts.showWarnings mustBe false
          opts.showStyleWarnings mustBe false
          opts.showMissingWarnings mustBe false
      }
    }

    "empty args are eliminated" in {
      val opts = Array("--show-times", "parse", "", "  ", "file.riddl")
      val (comm, remaining) = CommonOptionsHelper.parseCommonOptions(opts)
      comm match {
        case Some(options) =>
          options.showTimes must be(true)
          CommandOptions.parseCommandOptions(remaining) match {
            case Right(options) => options.inputFile mustBe
                Some(Path.of("file.riddl"))
            case Left(messages) => fail(messages.format)
          }
        case _ => fail("Failed to parse options")
      }
    }

    "load message related common options from a file" in {
      val optionFile = Path.of("commands/src/test/input/message-options.conf")
      CommandOptions.loadCommonOptions(optionFile) match {
        case Right(opts) =>
          opts.showTimes mustBe true
          opts.verbose mustBe true
          opts.quiet mustBe false
          opts.dryRun mustBe false
          opts.showWarnings mustBe true
          opts.showStyleWarnings mustBe false
          opts.showMissingWarnings mustBe false
          opts.maxIncludeWait mustBe 1.minute
          opts.sortMessagesByLocation mustBe true
          opts.groupMessagesByKind mustBe true
          opts.noANSIMessages mustBe true
        case Left(messages) =>
          fail(messages.format)
      }
    }
  }
}
