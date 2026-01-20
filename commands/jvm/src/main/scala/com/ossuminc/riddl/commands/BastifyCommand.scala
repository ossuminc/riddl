/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{CommandOptions, PassCommand, PassCommandOptions}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, PassCreators, PassesResult}
import com.ossuminc.riddl.utils.PlatformContext
import org.ekrich.config.Config
import scopt.OParser

import java.nio.file.{Files, Path}

object BastifyCommand {
  val cmdName = "bastify"

  case class Options(
    inputFile: Option[Path] = None,
    command: String = cmdName
  ) extends PassCommandOptions {
    def outputDir: Option[Path] = inputFile.map(_.getParent).orElse(Some(Path.of(".")))

    override def check: Messages = {
      if inputFile.isEmpty then
        Messages.errors("A .riddl input file is required.")
      else if !inputFile.get.toString.endsWith(".riddl") then
        Messages.errors("Input file must have .riddl extension.")
      else
        Messages.empty
    }
  }
}

/** A command to generate BAST (Binary AST) files from RIDDL input.
  *
  * The output .bast file is placed next to the input .riddl file.
  *
  * Usage:
  *   riddlc bastify <input.riddl>
  */
class BastifyCommand(using pc: PlatformContext) extends PassCommand[BastifyCommand.Options](BastifyCommand.cmdName) {
  import BastifyCommand.Options

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(BastifyCommand.cmdName)
      .text("Convert a RIDDL file to BAST (Binary AST) format")
      .children(
        inputFile((v, c) => c.copy(inputFile = Some(v.toPath)))
      ) -> Options()
  }

  override def interpretConfig(config: Config): Options = {
    val obj = config.getObject(commandName).toConfig
    val inputFile = Path.of(obj.getString("input-file"))
    Options(Some(inputFile), commandName)
  }

  override def overrideOptions(options: Options, newOutputDir: Path): Options = {
    options // outputDir is derived from inputFile, so no override needed
  }

  override def getPasses(options: Options): PassCreators = {
    Seq(BASTWriterPass.creator())
  }

  override def replaceInputFile(opts: Options, inputFile: Path): Options = {
    opts.copy(inputFile = Some(inputFile))
  }

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    super.run(options, outputDirOverride) match {
      case Left(errors) => Left(errors)
      case Right(result) =>
        result.outputOf[BASTOutput](BASTWriterPass.name) match {
          case None =>
            Left(Messages.errors("BASTWriter did not produce output"))
          case Some(bastOutput) =>
            val outputPath = determineOutputPath(options)
            try {
              val parent = outputPath.getParent
              if parent != null && !Files.exists(parent) then
                Files.createDirectories(parent)
              end if

              Files.write(outputPath, bastOutput.bytes)
              pc.log.info(s"Generated: $outputPath (${bastOutput.bytes.length} bytes, ${bastOutput.nodeCount} nodes)")
              Right(result)
            } catch {
              case ex: Exception =>
                Left(Messages.errors(s"Failed to write BAST file: ${ex.getMessage}"))
            }
        }
    }
  }

  private def determineOutputPath(options: Options): Path = {
    val inputPath = options.inputFile.get.toAbsolutePath
    val inputName = inputPath.getFileName.toString
    val bastName = inputName.replaceAll("\\.riddl$", ".bast")
    val dir = inputPath.getParent match {
      case null => Path.of(".")
      case p => p
    }
    dir.resolve(bastName)
  }

  override def loadOptionsFrom(configFile: Path): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }
}
