package com.reactific.riddl

import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.hugo.HugoCommand
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class OptionsReadingTest extends AnyWordSpec with Matchers  {

  "Options Reading" must {
    "load hugo options from a file" in {
      val optionFile = Path.of(
        "riddlc/src/test/input/hugo-options.conf")
      CommandOptions.loadCommonOptions(optionFile) match {
        case Right(opts) =>
          opts.showTimes mustBe true
          opts.verbose mustBe true
          opts.quiet mustBe false
          opts.dryRun mustBe false
          opts.showWarnings mustBe true
          opts.showStyleWarnings mustBe false
          opts.showMissingWarnings mustBe false
        case Left(messages) =>
          fail(messages.format)
      }
      CommandPlugin.loadCommandNamed("hugo") match {
        case Right(cmd) =>
          cmd.loadOptionsFrom(optionFile) match {
            case Left(errors) =>
              fail(errors.format)
            case Right(options) =>
              val opts = options.asInstanceOf[HugoCommand.Options]

              opts.command mustBe "hugo"
              opts.inputFile must not(be(empty))
              opts.inputFile.get.toString must include("ReactiveBBQ.riddl")
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
