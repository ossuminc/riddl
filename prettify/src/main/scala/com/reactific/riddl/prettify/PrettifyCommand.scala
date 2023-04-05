/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.prettify

import com.reactific.riddl.commands.CommandOptions.optional
import com.reactific.riddl.commands.CommandOptions
import com.reactific.riddl.commands.TranslationCommand
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.passes.AggregateOutput
import com.reactific.riddl.prettify.PrettifyCommand.cmdName
import com.reactific.riddl.utils.Logger
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

object PrettifyCommand {
  val cmdName = "prettify"
  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] =
      Some(Path.of(System.getProperty("java.io.tmpdir"))),
    projectName: Option[String] = None,
    singleFile: Boolean = true)
      extends CommandOptions with TranslationCommand.Options {
    def command: String = cmdName

  }
}

/** A command to Prettify RIDDL Source */
class PrettifyCommand
    extends TranslationCommand[PrettifyCommand.Options](cmdName) {
  import PrettifyCommand.Options

  def overrideOptions(options: Options, newOutputDir: Path): Options = {
    options.copy(outputDir = Some(newOutputDir))
  }

  override def translateImpl(
    results: AggregateOutput,
    log: Logger,
    commonOptions: CommonOptions,
    options: Options
  ): Either[Messages, Unit] = {
    PrettifyTranslator.translate(results, log, commonOptions, options)
      .map(_ => ())
  }

  override def getOptions: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    cmd(pluginName).children(
      inputFile((v, c) => c.copy(inputFile = Option(v.toPath))),
      outputDir((v, c) => c.copy(outputDir = Option(v.toPath))),
      opt[String]("project-name")
        .action((v, c) => c.copy(projectName = Option(v)))
        .text("The name of the project to prettify"),
      opt[Boolean]('s', name = "single-file")
        .action((v, c) => c.copy(singleFile = v)).text(
          """Resolve all includes and imports and write a single file with the
            |same file name as the input placed in the out-dir""".stripMargin
        )
    ).text("""Parse and validate the input-file and then reformat it to a
             |standard layout written to the output-dir.  """.stripMargin) ->
      PrettifyCommand.Options()
  }

  override def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for {
      topCur <- cur.asObjectCursor
      cmdCur <- topCur.atKey(cmdName)
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
    } yield PrettifyCommand.Options(
      Option(Path.of(inputPath)),
      Option(Path.of(outputPath)),
      Option(projectName),
      singleFile
    )
  }
}
