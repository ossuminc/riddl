/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions, CommonOptionsHelper}
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{PlatformIOContext, Logger}
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser
import scopt.RenderingMode.OneColumn

import java.nio.file.Path
import com.ossuminc.riddl.command.{Command, CommandOptions}

/** Unit Tests For FromCommand */
object HelpCommand {
  case class Options(command: String = "help", inputFile: Option[Path] = None, targetCommand: Option[String] = None)
      extends CommandOptions

  private def commandOptionsParser(using io: PlatformIOContext): OParser[Unit, ?] = {
    val optionParsers = Seq(
      AboutCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      DumpCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      FlattenCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      FromCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      HelpCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      HugoCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      InfoCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      OnChangeCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      ParseCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      PrettifyCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      RepeatCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      StatsCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      ValidateCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      VersionCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]]
    )
    OParser.sequence[Unit, CommandOptions](optionParsers.head, optionParsers.tail*)
  }

}

class HelpCommand(using io: PlatformIOContext) extends Command[HelpCommand.Options]("help") {
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
    commonOptions: CommonOptions,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    if commonOptions.verbose || !commonOptions.quiet then {
      val usage: String = {
        val common = OParser.usage(CommonOptionsHelper.commonOptionsParser, OneColumn)
        val commands = OParser.usage(HelpCommand.commandOptionsParser, OneColumn)
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
