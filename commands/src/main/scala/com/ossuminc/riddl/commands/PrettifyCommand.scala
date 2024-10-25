/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.{PassCreators, PassInput, PassOptions, PassesOutput, PassesResult}
import com.ossuminc.riddl.utils.{ExceptionUtils, Logger, PlatformIOContext}
import com.ossuminc.riddl.command.{CommandOptions, PassCommandOptions, TranslationCommand}
import com.ossuminc.riddl.command.CommandOptions.optional
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass, RiddlFileEmitter}
import com.ossuminc.riddl.utils.{pc, ec}

import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.charset.Charset
import java.nio.file.{Files, Path, StandardOpenOption}

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
class PrettifyCommand(using io: PlatformIOContext)
    extends TranslationCommand[PrettifyCommand.Options](PrettifyCommand.cmdName) {

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
    options: PrettifyCommand.Options
  ): PassCreators = {
    standardPasses ++ Seq(
      { (input: PassInput, outputs: PassesOutput) =>
        PrettifyPass(input, outputs, PrettifyPass.Options(flatten = options.singleFile))
      }
    )
  }

  override def run(
    originalOptions: PrettifyCommand.Options,
    outputDirOverride: Option[Path]
  ): Either[List[Messages.Message], PassesResult] =
    super.run(originalOptions, outputDirOverride) match
      case Left(messages) => Left(messages)
      case result @ Right(passesResult: PassesResult) =>
        passesResult.outputOf[PrettifyOutput](PrettifyPass.name) match
          case Some(output: PrettifyOutput) =>
            writeOutput(output, originalOptions, outputDirOverride)
            result
          case None =>
            // shouldn't happen
            Left(List(Messages.error("No output from Prettify Pass", At.empty)))
        end match
    end match
  end run

  private def writeOutput(
    output: PrettifyOutput,
    originalOptions: Options,
    dirOverrides: Option[Path],
  )(using io: PlatformIOContext): Unit =
    try {
      val dir = originalOptions.outputDir
        .getOrElse(
          dirOverrides
            .getOrElse(Path.of(Option(System.getProperty("user.dir")).getOrElse(".")))
        )
      Files.createDirectories(dir)
      if output.state.flatten then
        val path = dir.resolve("prettify-output.riddl")
        Files.writeString(
          path,
          output.state.filesAsString,
          Charset.forName("UTF-8"),
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE
        )
      else
        val base = dirOverrides.getOrElse(Path.of("."))
        output.state.withFiles { (file: RiddlFileEmitter) =>
          val content = file.toString
          val path = base.resolve(file.url.path)
          Files.writeString(
            path,
            content,
            Charset.forName("UTF-8"),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
          )
        }
      end if
    } catch {
      case e: java.io.IOException =>
        io.log.info(s"Exception while writing: ${e.getClass.getName}: ${e.getMessage}")
        val stackTrace = ExceptionUtils.getRootCauseStackTrace(e).mkString("\n")
        io.log.info(stackTrace)
    }
  end writeOutput
}
