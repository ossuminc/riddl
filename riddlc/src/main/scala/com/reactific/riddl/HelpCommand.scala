/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl

import com.reactific.riddl.commands.CommandOptions.commandOptionsParser
import com.reactific.riddl.commands.CommonOptionsHelper.commonOptionsParser
import com.reactific.riddl.commands.CommandOptions
import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.passes.PassesResult
import com.reactific.riddl.utils.Logger
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser
import scopt.RenderingMode.OneColumn

import java.nio.file.Path

/** Unit Tests For FromCommand */
object HelpCommand {
  case class Options(
    command: String = "help",
    inputFile: Option[Path] = None,
    targetCommand: Option[String] = None)
      extends CommandOptions
}

class HelpCommand extends CommandPlugin[HelpCommand.Options]("help") {
  import HelpCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(pluginName).action((_, c) => c.copy(command = pluginName))
      .text("Print out how to use this program") -> HelpCommand.Options()
  }

  override def getConfigReader: ConfigReader[HelpCommand.Options] = {
    (cur: ConfigCursor) =>
      for {
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey(pluginName)
        cmd <- topRes.asObjectCursor
      } yield { Options(cmd.path) }
  }

  override def run(
    options: HelpCommand.Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    if (commonOptions.verbose || !commonOptions.quiet) {
      val usage: String = {
        val common = OParser.usage(commonOptionsParser, OneColumn)
        val commands = OParser.usage(commandOptionsParser, OneColumn)
        val improved_commands = commands.split(System.lineSeparator())
          .flatMap { line =>
            if (line.isEmpty || line.forall(_.isWhitespace)) {
              Seq.empty[String]
            } else if (line.startsWith("Command:")) {
              Seq(System.lineSeparator() + line)
            } else if (line.startsWith("Usage:")) { Seq(line) }
            else { Seq("  " + line) }
          }.mkString(System.lineSeparator())
        common ++ "\n\n" ++ improved_commands
      }
      println(usage)
    }
    Right(PassesResult())
  }
}
