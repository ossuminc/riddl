/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions, CommonOptionsHelper}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{CommonOptions, Logger, PlatformContext}
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser
import scopt.RenderingMode.OneColumn

import java.nio.file.Path

/** Unit Tests For FromCommand */
object HelpCommand {
  case class Options(command: String = "help", inputFile: Option[Path] = None, targetCommand: Option[String] = None)
      extends CommandOptions
}

class HelpCommand(using io: PlatformContext) extends Command[HelpCommand.Options]("help") {
  import HelpCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName)
      .action((_, c) => c.copy(command = pluginName))
      .text("Print out how to use this program") -> HelpCommand.Options()
  }

  override def getConfigReader: ConfigReader[HelpCommand.Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      cmd <- topRes.asObjectCursor
    yield { Options(cmd.path) }
  }

  override def run(
    options: HelpCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    if io.options.verbose || !io.options.quiet then {
      val usage: String = {
        val common = OParser.usage(CommonOptionsHelper.commonOptionsParser, OneColumn)
        val commands = OParser.usage(CommandLoader.commandOptionsParser, OneColumn)
        val improved_commands = commands
          .split(System.lineSeparator())
          .flatMap { line =>
            if line.isEmpty || line.forall(_.isWhitespace) then {
              Seq.empty[String]
            } else if line.startsWith("Command:") then {
              Seq(System.lineSeparator() + line)
            } else if line.startsWith("Usage:") then { Seq(line) }
            else { Seq("  " + line) }
          }
          .mkString(System.lineSeparator())
        common ++ "\n\n" ++ improved_commands
      }
      io.log.info(usage)
    }
    Right(PassesResult())
  }
}
