/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{PassCommandOptions, TranslationCommand}
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass, RiddlFileEmitter}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.{ExceptionUtils, PlatformContext}
import org.ekrich.config.*
import scopt.OParser

import java.nio.charset.StandardCharsets
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
class PrettifyCommand(using pc: PlatformContext)
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

  override def interpretConfig(config: Config): Options =
    val rootConfig = config.getObject(commandName).toConfig
    val inputFile =
      if rootConfig.hasPath("input-file") then
        Some(Path.of(rootConfig.getString("input-file")))
      else
        None
    val outputDir =
      if rootConfig.hasPath("output-dir") then
        Some(Path.of(rootConfig.getString("output-dir")))
      else
        None
    val projectName =
      if rootConfig.hasPath("project-name") then
        Some(rootConfig.getString("project-name"))
      else
       Some("No Project Name Specified")
    val singleFile =
      if rootConfig.hasPath("single-file") then
        rootConfig.getBoolean("single-file")
      else
        false
    PrettifyCommand.Options(inputFile, outputDir, projectName, singleFile)
  end interpretConfig


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
    dirOverrides: Option[Path]
  )(using io: PlatformContext): Unit =
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
          StandardCharsets.UTF_8,
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
            StandardCharsets.UTF_8,
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
