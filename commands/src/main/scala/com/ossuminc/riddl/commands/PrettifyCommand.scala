/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.CommandOptions.optional
import com.ossuminc.riddl.command.TranslationCommand
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.{PassInput, PassesOutput, PassesCreator}
import com.ossuminc.riddl.utils.Logger
import com.ossuminc.riddl.prettify.*

import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

object PrettifyCommand {
  val cmdName = "prettify"
}

/** A command to Prettify RIDDL Source */
class PrettifyCommand extends TranslationCommand[PrettifyPass.Options](PrettifyCommand.cmdName) {

  import PrettifyPass.Options

  def overrideOptions(options: PrettifyPass.Options, newOutputDir: Path): PrettifyPass.Options = {
    options.copy(outputDir = Some(newOutputDir))
  }

  override def getOptions: (OParser[Unit, PrettifyPass.Options], PrettifyPass.Options) = {
    val builder = OParser.builder[PrettifyPass.Options]
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
      PrettifyPass.Options()
  }

  override def getConfigReader: ConfigReader[PrettifyPass.Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      cmdCur <- topCur.atKey(PrettifyCommand.cmdName)
      objCur <- cmdCur.asObjectCursor
      content <- cmdCur.asObjectCursor
      inputPathRes <- content.atKey("input-file")
      inputPath <- inputPathRes.asString
      outputPathRes <- content.atKey("output-dir")
      outputPath <- outputPathRes.asString
      projectName <-
        optional(content, "project-name", "No Project Name Specified") { cur =>
          cur.asString
        }
      singleFileRes <- objCur.atKey("single-file")
      singleFile <- singleFileRes.asBoolean
    yield PrettifyPass.Options(
      Option(Path.of(inputPath)),
      Option(Path.of(outputPath)),
      Option(projectName),
      singleFile
    )
  }

  override def getPasses(
                          log: Logger,
                          commonOptions: CommonOptions,
                          options: PrettifyPass.Options
  ): PassesCreator = {
    standardPasses ++ Seq(
      { (input: PassInput, outputs: PassesOutput) =>
        val state = PrettifyState(options)
        PrettifyPass(input, outputs, state)
      }
    )
  }
}
