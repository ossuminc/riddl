/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

/** Unit Tests For Running Riddlc Commands from Plugins */

import com.ossuminc.riddl.utils.{Plugin, PluginSpecBase}

import java.nio.file.Path
import scopt.*
import pureconfig.*

class CommandPluginTest
    extends PluginSpecBase(
      svcClassPath = Path.of("com/ossuminc/riddl/command/CommandPlugin.class"),
      implClassPath = Path
        .of("com/ossuminc/riddl/command/ASimpleTestCommand.class"),
      moduleName = "command",
      jarFilename = "test-command.jar"
    ) {

  "CommandPlugin " should {
    "get options from command line" in {
      val plugins = Plugin
        .loadPluginsFrom[CommandPlugin[CommandOptions]](tmpDir)
      plugins must not(be(empty))
      val p = plugins.head
      p.getClass must be(classOf[ASimpleTestCommand])
      val plugin = p.asInstanceOf[ASimpleTestCommand]
      val args: Seq[String] = Seq("test", "input-file", "Success!")
      val (parser, default) = plugin.getOptions
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
      val plugins = Plugin
        .loadPluginsFrom[CommandPlugin[CommandOptions]](tmpDir)
      plugins must not(be(empty))
      val p = plugins.head
      p.getClass must be(classOf[ASimpleTestCommand])
      val plugin = p.asInstanceOf[ASimpleTestCommand]
      val reader = plugin.getConfigReader
      val path: Path = Path.of("command/src/test/input/test.conf")
      ConfigSource
        .file(path.toFile)
        .load[ASimpleTestCommand.Options](reader) match {
        case Right(loadedOptions) => loadedOptions.arg1 mustBe "Success!"
        case Left(failures)       => fail(failures.prettyPrint())
      }
    }

    "run a command via a plugin" in {
      val args = Array(s"--plugins-dir=${tmpDir.toString}", "test", "inputfile", "test")
      CommandPlugin.runMain(args) mustBe 0
    }

    "handle wrong file as input" in {
      val args = Array(
        "--verbose",
        "--suppress-style-warnings",
        "--suppress-missing-warnings",
        "test",
        "command/src/test/input/foo.riddl", // wrong file!
        "hugo"
      )
      val rc = CommandPlugin.runMain(args)
      rc must be(0)
    }

    "handle wrong command as target" in {
      val args = Array(
        "--verbose",
        "--suppress-style-warnings",
        "--suppress-missing-warnings",
        "test",
        "command/src/test/input/repeat-options.conf",
        "flumox" // unknown command
      )
      val rc = CommandPlugin.runMain(args)
      rc must be(0)
    }
  }
}
