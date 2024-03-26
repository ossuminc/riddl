/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.stats

import com.ossuminc.riddl.commands.{CommandOptions, InputFileCommandPlugin, PassCommand, PassCommandOptions}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesCreator, PassesOutput, PassesResult, Riddl}
import com.ossuminc.riddl.utils.Logger
import scopt.OParser
import pureconfig.{ConfigCursor, ConfigReader}

import java.io.File
import java.nio.file.Path

object StatsCommand {
  val cmdName: String = "stats"
  case class Options(
    inputFile: Option[Path] = None
  ) extends PassCommandOptions
      with CommandOptions {
    def command: String = cmdName
    def outputDir: Option[Path] = None
  }
}

/** Stats Command */
class StatsCommand extends PassCommand[StatsCommand.Options]("stats") {
  import StatsCommand.Options

  // Members declared in com.ossuminc.riddl.commands.CommandPlugin
  def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      objCur <- topRes.asObjectCursor
      inFileRes <- objCur.atKey("input-file").map(_.asString)
      inFile <- inFileRes
    yield {
      Options(inputFile = Some(Path.of(inFile)))
    }
  }

  def getOptions: (OParser[Unit, Options], com.ossuminc.riddl.stats.StatsCommand.Options) = {
    import builder.*
    cmd(StatsCommand.cmdName)
      .children(
        arg[File]("input-file")
          .action { (file, opt) =>
            opt.copy(inputFile = Some(file.toPath))
          }
          .text("The main input file on which to generate statistics.")
      )
      .text("Loads a configuration file and executes the command in it") ->
      StatsCommand.Options()
  }

  // Members declared in com.ossuminc.riddl.commands.PassCommand
  def overrideOptions(options: Options, newOutputDir: Path): Options = {
    options // we don't support overriding the output dir
  }

  override def replaceInputFile(
    opts: Options,
    inputFile: Path
  ): Options = { opts.copy(inputFile = Some(inputFile)) }

  override def loadOptionsFrom(
    configFile: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }

  override def getPasses(log: Logger, commonOptions: CommonOptions, options: Options): PassesCreator = {
    standardPasses :+ StatsPass.creator
  }

}
