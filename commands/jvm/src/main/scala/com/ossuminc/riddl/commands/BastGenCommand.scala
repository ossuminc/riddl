/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.bast.{BASTOutput, BASTWriter}
import com.ossuminc.riddl.command.{CommandOptions, PassCommand, PassCommandOptions}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.{PassCreators, PassesResult}
import com.ossuminc.riddl.utils.PlatformContext
import org.ekrich.config.Config
import scopt.OParser

import java.nio.file.{Files, Path}

object BastGenCommand {
  val cmdName = "bast-gen"

  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] = None,
    outputFile: Option[Path] = None,
    command: String = cmdName
  ) extends PassCommandOptions {
    override def check: Messages = {
      val msgs1 = if inputFile.isEmpty then {
        Messages.errors("An input file was not provided.")
      } else Messages.empty
      // outputDir check is optional for bast-gen since we allow -o for direct file output
      msgs1
    }
  }
}

/** A command to generate BAST (Binary AST) files from RIDDL input.
  *
  * Usage:
  *   riddlc bast-gen <input.riddl> -o <output.bast>
  *   riddlc bast-gen <input.riddl> --output-dir <dir>
  */
class BastGenCommand(using pc: PlatformContext) extends PassCommand[BastGenCommand.Options](BastGenCommand.cmdName) {
  import BastGenCommand.Options

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(BastGenCommand.cmdName)
      .text("Generate a BAST (Binary AST) file from RIDDL input")
      .children(
        inputFile((v, c) => c.copy(inputFile = Some(v.toPath))),
        opt[java.io.File]('o', "output")
          .optional()
          .action((v, c) => c.copy(outputFile = Some(v.toPath)))
          .text("The output BAST file path"),
        outputDir((v, c) => c.copy(outputDir = Some(v.toPath)))
      ) -> Options()
  }

  override def interpretConfig(config: Config): Options = {
    val obj = config.getObject(commandName).toConfig
    val inputFile = Path.of(obj.getString("input-file"))
    val outputDir = if obj.hasPath("output-dir") then Some(Path.of(obj.getString("output-dir"))) else None
    val outputFile = if obj.hasPath("output") then Some(Path.of(obj.getString("output"))) else None
    Options(Some(inputFile), outputDir, outputFile, commandName)
  }

  override def overrideOptions(options: Options, newOutputDir: Path): Options = {
    options.copy(outputDir = Some(newOutputDir))
  }

  override def getPasses(options: Options): PassCreators = {
    Seq(BASTWriter.creator())
  }

  override def replaceInputFile(opts: Options, inputFile: Path): Options = {
    opts.copy(inputFile = Some(inputFile))
  }

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    // First run the passes to generate the BAST
    super.run(options, outputDirOverride) match {
      case Left(errors) => Left(errors)
      case Right(result) =>
        // Get the BAST output
        result.outputOf[BASTOutput](BASTWriter.name) match {
          case None =>
            Left(Messages.errors("BASTWriter did not produce output"))
          case Some(bastOutput) =>
            // Determine the output path
            val outputPath = determineOutputPath(options, outputDirOverride)

            // Write the BAST file
            try {
              // Create parent directories if needed
              val parent = outputPath.getParent
              if parent != null && !Files.exists(parent) then
                Files.createDirectories(parent)
              end if

              Files.write(outputPath, bastOutput.bytes)
              pc.log.info(s"Generated BAST file: $outputPath (${bastOutput.bytes.length} bytes, ${bastOutput.nodeCount} nodes)")
              Right(result)
            } catch {
              case ex: Exception =>
                Left(Messages.errors(s"Failed to write BAST file: ${ex.getMessage}"))
            }
        }
    }
  }

  private def determineOutputPath(options: Options, outputDirOverride: Option[Path]): Path = {
    // Priority: explicit output file > output dir + default (next to input)
    options.outputFile match {
      case Some(path) => path
      case None =>
        val inputPath = options.inputFile.get.toAbsolutePath
        val inputName = inputPath.getFileName.toString
        val bastName = inputName.replaceAll("\\.(riddl|RIDDL)$", "") + ".bast"

        // Default: place .bast file next to the source .riddl file
        val dir = outputDirOverride
          .orElse(options.outputDir)
          .getOrElse(inputPath.getParent match {
            case null => Path.of(".")
            case p => p
          })

        dir.resolve(bastName)
    }
  }

  override def loadOptionsFrom(configFile: Path): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }
}
