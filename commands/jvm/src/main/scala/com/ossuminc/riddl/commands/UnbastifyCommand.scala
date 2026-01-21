/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions, PassCommandOptions}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass, RiddlFileEmitter}
import com.ossuminc.riddl.utils.{PlatformContext, URL}
import org.ekrich.config.Config
import scopt.OParser

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object UnbastifyCommand {
  val cmdName = "unbastify"

  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] = None,
    command: String = cmdName
  ) extends CommandOptions {
    override def check: Messages = {
      if inputFile.isEmpty then
        Messages.errors("A .bast input file is required.")
      else if !inputFile.get.toString.endsWith(".bast") then
        Messages.errors("Input file must have .bast extension.")
      else
        Messages.empty
    }
  }
}

/** A command to convert BAST (Binary AST) files back to RIDDL source.
  *
  * This is the inverse of the bastify command. It reads a .bast file,
  * deserializes the AST, and uses PrettifyPass to regenerate RIDDL source.
  * Includes are reconstructed as separate files.
  *
  * Usage:
  *   riddlc unbastify <input.bast> -o <output-dir>
  */
class UnbastifyCommand(using pc: PlatformContext) extends Command[UnbastifyCommand.Options](UnbastifyCommand.cmdName) {
  import UnbastifyCommand.Options

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(UnbastifyCommand.cmdName)
      .text("Convert a BAST (Binary AST) file back to RIDDL source files")
      .children(
        arg[java.io.File]("<input.bast>")
          .required()
          .action((v, c) => c.copy(inputFile = Some(v.toPath)))
          .text("The input BAST file to convert"),
        opt[java.io.File]('o', "output-dir")
          .optional()
          .action((v, c) => c.copy(outputDir = Some(v.toPath)))
          .text("Output directory for RIDDL files (default: next to input)")
      ) -> Options()
  }

  override def interpretConfig(config: Config): Options = {
    val obj = config.getObject(commandName).toConfig
    val inputFile = Path.of(obj.getString("input-file"))
    val outputDir = if obj.hasPath("output-dir") then Some(Path.of(obj.getString("output-dir"))) else None
    Options(Some(inputFile), outputDir, commandName)
  }

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    val inputPath = options.inputFile.get.toAbsolutePath

    // Step 1: Read the BAST file
    val bytes = try {
      Files.readAllBytes(inputPath)
    } catch {
      case ex: Exception =>
        return Left(Messages.errors(s"Failed to read BAST file: ${ex.getMessage}"))
    }

    // Step 2: Deserialize the AST
    pc.log.info(s"Read ${bytes.length} bytes from BAST file, now deserializing...")
    BASTReader.read(bytes) match {
      case Left(errors) =>
        return Left(errors)
      case Right(nebula) =>
        // Step 3: Determine output directory
        val outputDir = outputDirOverride
          .orElse(options.outputDir)
          .getOrElse(inputPath.getParent match {
            case null => Path.of(".")
            case p => p
          })

        // Step 4: Determine the top-level output file name
        val inputName = inputPath.getFileName.toString
        val riddlName = inputName.replaceAll("\\.bast$", ".riddl")

        // Step 5: Run PrettifyPass to convert AST back to RIDDL text
        // Use Nebula directly as the PassRoot (since Nebula extends Branch[?])
        val passInput = PassInput(nebula)
        val prettifyOptions = PrettifyPass.Options(flatten = false)

        // Create the pass and run it using Pass.runThesePasses
        val passes: PassCreators = Seq(
          (input: PassInput, outputs: PassesOutput) =>
            PrettifyPass(input, outputs, prettifyOptions)
        )
        val result = Pass.runThesePasses(passInput, passes)

        if result.messages.hasErrors then
          return Left(result.messages.justErrors)
        end if

        // Step 6: Extract the prettify output
        result.outputOf[PrettifyOutput](PrettifyPass.name) match {
          case None =>
            return Left(Messages.errors("PrettifyPass did not produce output"))
          case Some(prettifyOutput) =>
            // Step 7: Write output files
            try {
              Files.createDirectories(outputDir)

              var fileCount = 0
              prettifyOutput.state.withFiles { (file: RiddlFileEmitter) =>
                val content = file.toString
                // Determine file path - use the URL path or default to top-level name
                val filePath = if file.url.path.nonEmpty && file.url.path != "nada" then
                  outputDir.resolve(file.url.path)
                else
                  outputDir.resolve(riddlName)

                // Create parent directories if needed
                val parent = filePath.getParent
                if parent != null && !Files.exists(parent) then
                  Files.createDirectories(parent)
                end if

                Files.writeString(filePath, content, StandardCharsets.UTF_8)
                fileCount += 1
                pc.log.info(s"Generated: $filePath")
              }

              pc.log.info(s"Unbastify complete: $fileCount file(s) written to $outputDir")
              Right(result)

            } catch {
              case ex: Exception =>
                Left(Messages.errors(s"Failed to write RIDDL files: ${ex.getMessage}"))
            }
        }
    }
  }

  override def loadOptionsFrom(configFile: Path): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }

  override def replaceInputFile(opts: Options, inputFile: Path): Options = {
    opts.copy(inputFile = Some(inputFile))
  }
}
