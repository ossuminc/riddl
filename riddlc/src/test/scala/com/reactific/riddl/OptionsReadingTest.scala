package com.reactific.riddl

import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.hugo.HugoCommand
import com.reactific.riddl.utils.SysLogger
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class OptionsReadingTest extends AnyWordSpec with Matchers  {

  "Options Reading" must {
    "load hugo options from a file" in {
      val log = SysLogger()
      val optionFile = Path.of(
        "riddlc/src/test/input/hugo-options.conf")
      CommandOptions.loadCommonOptions(optionFile,log) match {
        case Some(opts) =>
          opts.showTimes mustBe true
          opts.verbose mustBe true
          opts.quiet mustBe false
          opts.dryRun mustBe false
          opts.showWarnings mustBe true
          opts.showStyleWarnings mustBe false
          opts.showMissingWarnings mustBe false
        case None =>
          fail("Didn't read common optionss")
      }
      CommandPlugin.loadCommandNamed("hugo") match {
        case Right(cmd) =>
          cmd.loadOptionsFrom(optionFile, log) match {
            case Left(errors) =>
              fail(errors.format)
            case Right(options) =>
              val opts = options.asInstanceOf[HugoCommand.Options]

              opts.command mustBe "hugo"
              opts.inputFile mustBe Option(Path.of("ReactiveBBQ.riddl"))
              opts.outputDir mustBe Option(Path.of(
                "/tmp/foo/ReactiveBBQ"
              ))
              opts.eraseOutput mustBe true
              opts.projectName mustBe Option("Reactive BBQ")
              opts.baseUrl mustBe Option(
                new java.net.URL("https://riddl.tech"))
              opts.sourceURL mustBe Option(
                new java.net.URL("https://github.com/reactific/riddl"))
              opts.editPath mustBe Option("blob/main/examples/src/riddl/ReactiveBBQ")
              opts.siteLogoPath mustBe Option("images/RBBQ.png")
          }
        case Left(errors) =>
          fail(errors.format)
      }
    }
  }
}
