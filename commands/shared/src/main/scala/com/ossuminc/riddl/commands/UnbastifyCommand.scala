/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions, PassCommandOptions}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.toSeq
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass, RiddlFileEmitter}
import com.ossuminc.riddl.utils.PlatformContext
import com.ossuminc.riddl.utils.StringHelpers.dropRightWhile
import org.ekrich.config.Config
import scopt.OParser

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

object UnbastifyCommand {
  val cmdName = "unbastify"

  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] = None,
    singleFile: Boolean = false,
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
          .text("Output directory for RIDDL files (default: next to input)"),
        opt[Boolean]('s', "single-file")
          .action((v, c) => c.copy(singleFile = v))
          .text("Resolve all includes and write a single flattened file")
      ) -> Options()
  }

  override def interpretConfig(config: Config): Options = {
    val obj = config.getObject(commandName).toConfig
    val inputFile = Path.of(obj.getString("input-file"))
    val outputDir = if obj.hasPath("output-dir") then Some(Path.of(obj.getString("output-dir"))) else None
    val singleFile = if obj.hasPath("single-file") then obj.getBoolean("single-file") else false
    Options(Some(inputFile), outputDir, singleFile, commandName)
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

        // Step 5: Derive inputDir from the nebula's first content node's
        // source origin (the FILE_CHANGE_MARKER path from the original
        // root .riddl file). Its parent directory = inputDir.
        // Note: URL.apply("file:///path/to/file") stores path without
        // leading '/', so inputDir must also omit the leading '/' to
        // match when toDestination strips the prefix.
        val inputDir =
          val contents = nebula.contents.toSeq
          if contents.nonEmpty then
            val origin = contents.head.loc.source.origin
            // Strip file:// scheme and leading '/' to match URL.path format
            val path = origin.replace("file://", "")
            val normalized = if path.startsWith("/") then path.drop(1) else path
            val lastSlash = normalized.lastIndexOf('/')
            if lastSlash >= 0 then normalized.substring(0, lastSlash)
            else ""
          else ""

        // Step 6: Run PrettifyPass to convert AST back to RIDDL text
        val passInput = PassInput(nebula)
        val prettifyOptions = PrettifyPass.Options(
          flatten = options.singleFile,
          topFile = riddlName,
          outputDir = outputDir.toString,
          inputDir = inputDir
        )

        val passes: PassCreators = Seq(
          (input: PassInput, outputs: PassesOutput) =>
            PrettifyPass(input, outputs, prettifyOptions)
        )
        val result = Pass.runThesePasses(passInput, passes)

        if result.messages.hasErrors then
          return Left(result.messages.justErrors)
        end if

        // Step 7: Extract the prettify output and write files
        result.outputOf[PrettifyOutput](PrettifyPass.name) match {
          case None =>
            return Left(Messages.errors("PrettifyPass did not produce output"))
          case Some(prettifyOutput) =>
            try {
              Files.createDirectories(outputDir)

              if options.singleFile then
                // Single flattened file
                val filePath = outputDir.resolve(riddlName)
                val content = prettifyOutput.state.filesAsString
                Files.writeString(filePath, content, StandardCharsets.UTF_8)
                pc.log.info(s"Generated: $filePath")
                pc.log.info(s"Unbastify complete: 1 file written to $outputDir")
              else
                // Multi-file: write each file emitter to its own path
                var fileCount = 0
                prettifyOutput.state.withFiles { (file: RiddlFileEmitter) =>
                  val content = file.toString
                  val path = outputDir.resolve(file.url.path)
                  Files.createDirectories(path.getParent)
                  Files.writeString(
                    path,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                  )
                  pc.log.info(s"Generated: $path")
                  fileCount += 1
                }
                pc.log.info(s"Unbastify complete: $fileCount files written to $outputDir")
              end if

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
