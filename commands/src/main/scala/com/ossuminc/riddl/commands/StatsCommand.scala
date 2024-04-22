/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.Logger
import com.ossuminc.riddl.analyses.{StatsOutput, StatsPass}
import scopt.OParser
import pureconfig.{ConfigCursor, ConfigReader}

import java.io.File
import java.nio.file.Path

object StatsCommand {
  val cmdName: String = "stats"
  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] = None
  ) extends PassCommandOptions
      with CommandOptions {
    def command: String = cmdName
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
      outDirRes <- objCur.atKey("output-dir").map(_.asString)
      inFile <- inFileRes
      outDir <- outDirRes
    yield {
      Options(inputFile = Some(Path.of(inFile)))
    }
  }

  def getOptions: (OParser[Unit, Options], StatsCommand.Options) = {
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
    options.copy(outputDir = Some(newOutputDir))
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

  override def run(
    originalOptions: Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    val result = super.run(originalOptions, commonOptions, log, outputDirOverride) 
    result match {
      case Left(messages) =>
        Messages.logMessages(messages, log, commonOptions)
      case Right(passesResult) =>
        val stats = passesResult.outputOf[StatsOutput](StatsPass.name)
        println(stats)
    }
    result 
  }

}
