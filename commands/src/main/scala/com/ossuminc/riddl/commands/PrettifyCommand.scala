/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.{PassInput, PassOptions, PassesCreator, PassesOutput}
import com.ossuminc.riddl.utils.Logger
import com.ossuminc.riddl.command.{CommandOptions, PassCommandOptions, TranslationCommand}
import com.ossuminc.riddl.command.CommandOptions.optional
import com.ossuminc.riddl.passes.prettify.PrettifyPass
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

object PrettifyCommand {
  val cmdName = "prettify"

  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] = Some(Path.of(System.getProperty("java.io.tmpdir"))),
    projectName: Option[String] = None,
    singleFile: Boolean = true
  ) extends TranslationCommand.Options
      with PassOptions
      with PassCommandOptions:
    def command: String = cmdName
}

/** A command to Prettify RIDDL Source */
class PrettifyCommand extends TranslationCommand[PrettifyCommand.Options](PrettifyCommand.cmdName) {

  import PrettifyCommand.Options

  def overrideOptions(options: PrettifyCommand.Options, newOutputDir: Path): PrettifyCommand.Options = {
    options.copy(outputDir = Some(newOutputDir))
  }

  override def getOptionsParser: (OParser[Unit, PrettifyCommand.Options], PrettifyCommand.Options) = {
    val builder = OParser.builder[PrettifyCommand.Options]
    import builder.*
    cmd(PrettifyCommand.cmdName)
      .children(
        inputFile((v, c) => c.copy(inputFile = Option(v.toPath))),
        outputDir((v, c) => c.copy(outputDir = Option(v.toPath))),
        opt[String]("project-name")
          .action((v, c) => c.copy(projectName = Option(v)))
          .text("The name of the project to prettify"),
        opt[Boolean]('s', name = "single-file")
          .action((v, c) => c.copy(singleFile = v))
          .text(
            """Resolve all includes and imports and write a single file with the
            |same file name as the input placed in the out-dir""".stripMargin
          )
      )
      .text("""Parse and validate the input-file and then reformat it to a
             |standard layout written to the output-dir.  """.stripMargin) ->
      PrettifyCommand.Options()
  }

  override def getConfigReader: ConfigReader[PrettifyCommand.Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      cmdCur <- topCur.atKey(PrettifyCommand.cmdName)
      objCur <- cmdCur.asObjectCursor
      content <- cmdCur.asObjectCursor
      inputPathRes <- content.atKey("input-file")
      inputPath <- inputPathRes.asString
      outputPathRes <- content.atKey("output-dir")
      outputPath <- outputPathRes.asString
      projectName <- optional(content, "project-name", "No Project Name Specified") { cur => cur.asString }
      singleFileRes <- objCur.atKey("single-file")
      singleFile <- singleFileRes.asBoolean
    yield PrettifyCommand.Options(
      Option(Path.of(inputPath)),
      Option(Path.of(outputPath)),
      Option(projectName),
      singleFile
    )
  }

  override def getPasses(
    log: Logger,
    commonOptions: CommonOptions,
    options: PrettifyCommand.Options
  ): PassesCreator = {
    standardPasses ++ Seq(
      { (input: PassInput, outputs: PassesOutput) =>
        PrettifyPass(input, outputs, PrettifyPass.Options(flatten = options.singleFile))
      }
    )
  }
}
