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
import org.ekrich.config.*
import scopt.OParser
import scopt.RenderingMode.OneColumn

import java.nio.file.Path

/** Unit Tests For FromCommand */
object HelpCommand {
  case class Options(command: String = "help")
      extends CommandOptions {
    def inputFile: Option[Path] = None
  }
}

class HelpCommand(using val pc: PlatformContext) extends Command[HelpCommand.Options]("help") {
  import HelpCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(commandName)
      .action((_, c) => c.copy(command = commandName))
      .text("Print out how to use this program") -> HelpCommand.Options()
  }

  override def interpretConfig(config: Config): Options =
    Options(commandName)
  end interpretConfig
  

  override def run(
    options: HelpCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    if pc.options.verbose || !pc.options.quiet then {
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
      pc.log.info(usage)
    }
    Right(PassesResult())
  }
}
