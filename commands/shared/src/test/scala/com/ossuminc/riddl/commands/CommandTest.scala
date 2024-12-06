/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

/** Unit Tests For Running Riddlc Commands from Plugins */

import com.ossuminc.riddl.utils.{AbstractTestingBasis, ec, pc}
import org.ekrich.config.*
import scopt.*

import java.nio.file.Path

class CommandTest extends AbstractTestingBasis {
//PluginSpecBase(
//      svcClassPath = Path.of("com/ossuminc/riddl/command/CommandPlugin.class"),
//      implClassPath = Path
//        .of("com/ossuminc/riddl/commands/ASimpleTestCommand.class"),
//      moduleName = "command",
//      jarFilename = "test-command.jar"
//    ) {

  "CommandTest" should {
    "get options from command line" in {
      val cmd = ASimpleTestCommand()
      val args: Seq[String] = Seq("test", "input-file", "Success!")
      val (parser, default) = cmd.getOptionsParser
      OParser.parse(parser, args, default) match {
        case Some(to) =>
          to.command must be("test")
          to.inputFile.get.toString must be("input-file")
          to.arg1 must be("Success!")
        case None =>
          fail("No options returned from OParser.parse")
      }
    }
    "get options from config file" in {
      val cmd = ASimpleTestCommand()
      val path: Path = Path.of("commands/input/test.conf")
      val options: ConfigParseOptions = 
        ConfigParseOptions.defaults
          .setAllowMissing(true)
           .setOriginDescription(path.getFileName.toString)
      val config = ConfigFactory.parseFile(path.toFile, options)
      val loadedOptions = cmd.interpretConfig(config) 
      loadedOptions.arg1 mustBe "Success!"
    }

    "run a command" in {
      val args = Array("info")
      Commands.runMain(args) mustBe 0
    }

    "handle wrong file as input" in {
      val args = Array(
        "--verbose",
        "--suppress-style-warnings",
        "--suppress-missing-warnings",
        "parse",
        "commands/input/foo.riddl", // wrong file!
        "hugo"
      )
      val rc = Commands.runMain(args)
      rc must be(6)
    }

    "handle wrong command as target" in {
      val args = Array(
        "--verbose",
        "--suppress-style-warnings",
        "--suppress-missing-warnings",
        "test",
        "commands/input/repeat-options.conf",
        "flumox" // unknown command
      )
      val rc = Commands.runMain(args)
      rc must be(6)
    }
  }
}
