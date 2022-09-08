package com.reactific.riddl.commands

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class OptionsReadingTest extends AnyWordSpec with Matchers {

  "Options Reading" must {
    "load repeat options from a file" in {
      val optionFile = Path.of("commands/src/test/input/repeat-options.conf")
      CommandOptions.loadCommonOptions(optionFile) match {
        case Right(opts) =>
          opts.showTimes mustBe true
          opts.verbose mustBe true
          opts.quiet mustBe false
          opts.dryRun mustBe false
          opts.showWarnings mustBe true
          opts.showStyleWarnings mustBe false
          opts.showMissingWarnings mustBe false
        case Left(messages) => fail(messages.format)
      }
      CommandPlugin.loadCommandNamed("repeat") match {
        case Right(cmd) => cmd.loadOptionsFrom(optionFile) match {
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
