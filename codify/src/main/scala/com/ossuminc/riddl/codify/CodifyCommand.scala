/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.codify

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
import java.net.URI

object CodifyCommand {
  val cmdName: String = "codify"
  case class Options(
    inputFile: Option[Path] = None,
    endPoint: Option[URI] = None,
    apiKey: Option[String] = None,
    orgId: Option[String] = None
  ) extends PassCommandOptions
      with CommandOptions {
    def command: String = cmdName
    def outputDir: Option[Path] = None
  }
}

/** Codify Command */
class CodifyCommand extends PassCommand[CodifyCommand.Options](CodifyCommand.cmdName) {
  import CodifyCommand.Options

  // Members declared in com.ossuminc.riddl.commands.CommandPlugin
  def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      objCur <- topRes.asObjectCursor
      inFileRes <- objCur.atKey("input-file").map(_.asString)
      inFile <- inFileRes
      endPointRes <- objCur.atKey("end-point").map(_.asString)
      endPoint <- endPointRes
      apiKeyRes <- objCur.atKey("api-key").map(_.asString)
      apiKey <- apiKeyRes
      orgIdRes <- objCur.atKey("org-id").map(_.asString)
      orgId <- orgIdRes
    yield {
      Options(Some(Path.of(inFile)), Some(URI.create(endPoint)), Some(apiKey), Some(orgId) )
    }
  }

  def getOptions: (OParser[Unit, Options], CodifyCommand.Options) = {
    import builder.*
    cmd(CodifyCommand.cmdName)
      .children(
        arg[File]("input-file")
          .action { (file, opt) =>
            opt.copy(inputFile = Some(file.toPath))
          }
          .text("The main input file on which to generate statistics.")
      )
      .text("Loads a configuration file and executes the command in it") ->
      CodifyCommand.Options()
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
    standardPasses :+ CodifyPass.creator
  }

}
