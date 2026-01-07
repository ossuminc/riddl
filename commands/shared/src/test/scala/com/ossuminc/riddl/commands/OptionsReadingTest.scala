/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.CommonOptionsHelper
import com.ossuminc.riddl.utils.{AbstractTestingBasis, SysLogger, ec, pc}

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class OptionsReadingTest extends AbstractTestingBasis {

  "RiddlOptions Reading" must {
    "load repeat options from a file" in {
      val optionFile = Path.of("commands/input/repeat-options.conf")
      CommonOptionsHelper.loadCommonOptions(optionFile) match {
        case Right(opts) =>
          opts.showTimes mustBe true
          opts.verbose mustBe true
          opts.quiet mustBe false
          opts.dryRun mustBe false
          opts.showWarnings mustBe false
          opts.showStyleWarnings mustBe true
          opts.showMissingWarnings mustBe true
        case Left(messages) => fail(messages.format)
      }
      CommandLoader.loadCommandNamed("repeat") match {
        case Right(cmd) =>
          cmd.loadOptionsFrom(optionFile) match {
            case Left(errors) => fail(errors.format)
            case Right(options) =>
              val opts = options.asInstanceOf[RepeatCommand.Options]
              opts.command mustBe "repeat"
              opts.inputFile must not(be(empty))
              opts.inputFile.get.toString must include("ReactiveBBQ.conf")
              opts.targetCommand must be("from")
              opts.refreshRate must be(5.seconds)
              opts.maxCycles must be(10)
              opts.interactive must be(true)
          }
        case Left(errors) => fail(errors.format)
      }
    }
  }
}
