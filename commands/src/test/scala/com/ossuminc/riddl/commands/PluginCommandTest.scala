/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

/** Unit Tests For Running Riddlc Commands from Plugins */

import com.ossuminc.riddl.utils.{Plugin,PluginSpecBase}

import pureconfig.ConfigSource
import scopt.OParser

import java.nio.file.Path

class PluginCommandTest
    extends PluginSpecBase(
      svcClassPath =
        Path.of("com/ossuminc/riddl/commands/CommandPlugin.class"),
      implClassPath = Path
        .of("com/ossuminc/riddl/commands/ASimpleTestCommand.class"),
      moduleName = "commands",
      jarFilename = "test-command.jar"
    ) {

  "PluginCommandTest " should {
    "get options from command line" in {
      val plugins = Plugin
        .loadPluginsFrom[CommandPlugin[CommandOptions]](tmpDir)
      plugins must not(be(empty))
      val p = plugins.head
      p.getClass must be(classOf[ASimpleTestCommand])
      val plugin = p.asInstanceOf[ASimpleTestCommand]
      val args: Seq[String] = Seq("test", "Success!")
      val (parser, default) = plugin.getOptions
      OParser.parse(parser, args, default) match {
        case Some(to) => to.arg1 must be("Success!")
        case None     => fail("No options returned from OParser.parse")
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
      val path: Path = Path.of("commands/src/test/input/test.conf")
      ConfigSource.file(path.toFile)
        .load[ASimpleTestCommand.Options](reader) match {
        case Right(loadedOptions) => loadedOptions.arg1 mustBe "Success!"
        case Left(failures)       => fail(failures.prettyPrint())
      }
    }

    "run a command via a plugin" in {
      val args =
        Array(s"--plugins-dir=$tmpDir.toString", "test", "fee=fie,foo=fum")
      CommandPlugin.runMain(args) mustBe 0
    }

    "handle wrong file as input" in {
      val args = Array(
        "--verbose",
        "--suppress-style-warnings",
        "--suppress-missing-warnings",
        "from",
        "commands/src/test/input/simple.riddl", // wrong file!
        "hugo"
      )
      val rc = CommandPlugin.runMain(args)
      rc must not(be(0))
    }

    "handle wrong command as target" in {
      val args = Array(
        "--verbose",
        "--suppress-style-warnings",
        "--suppress-missing-warnings",
        "from",
        "commands/src/test/input/repeat-options.conf",
        "flumox" // unknown command
      )
      val rc = CommandPlugin.runMain(args)
      rc must not(be(0))
    }
  }
}
