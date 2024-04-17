package com.ossuminc.riddl.commands

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class SimpleCommandOptionsTest extends AnyWordSpec with Matchers {

  val confFile = "riddlc/src/test/input/cmdoptions.conf"

  "OptionsReading" should {
    "read About command options" in {
      val cmd = new AboutCommand
      cmd.loadOptionsFrom(Path.of(confFile)) match {
        case Left(errors)   => fail(errors.format)
        case Right(options) => options must be(AboutCommand.Options())
      }
    }

    "read Help command options" in {
      val cmd = new HelpCommand
      cmd.loadOptionsFrom(Path.of(confFile)) match {
        case Left(errors)   => fail(errors.format)
        case Right(options) => options must be(HelpCommand.Options())
      }
    }

    "read Info command options" in {
      val cmd = new InfoCommand
      cmd.loadOptionsFrom(Path.of(confFile)) match {
        case Left(errors)   => fail(errors.format)
        case Right(options) => options must be(InfoCommand.Options())
      }
    }

    "read Version command options" in {
      val cmd = new VersionCommand
      cmd.loadOptionsFrom(Path.of(confFile)) match {
        case Left(errors)   => fail(errors.format)
        case Right(options) => options must be(VersionCommand.Options())
      }
    }
  }
}
