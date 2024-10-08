/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.TopLevelParser
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesCreator, PassesResult}
import com.ossuminc.riddl.utils.Logger

import java.nio.file.Path
import scala.reflect.ClassTag

trait PassCommandOptions extends CommandOptions {
  def outputDir: Option[Path]
  override def check: Messages = {
    val msgs1 = super.check
    val msgs2 = if outputDir.isEmpty then {
      Messages.errors("An output directory was not provided.")
    } else {
      Messages.empty
    }
    msgs1 ++ msgs2
  }
}

/** An abstract base class for commands that use passes.
  *
  * @param name
  *   The name of the command to pass to [[Command]]
  * @tparam OPT
  *   The option type for the command
  */
abstract class PassCommand[OPT <: PassCommandOptions: ClassTag](name: String) extends Command[OPT](name) {

  /** Get the passes to run given basic input for pass creation
    *
    * @param log
    *   The logger to use
    * @param commonOptions
    *   The common options for the command
    * @param options
    *   The command options
    * @return
    *   A [[com.ossuminc.riddl.passes.PassCreator]] function that creates the pass
    */
  def getPasses(
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): PassesCreator

  /** A method to override the options */
  def overrideOptions(options: OPT, newOutputDir: Path): OPT

  private final def doRun(
    options: OPT,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputPath: Path) =>
      import com.ossuminc.riddl.language.parsing.RiddlParserInput
      val rpi = RiddlParserInput.fromPath(inputPath)
      TopLevelParser.parseInput(rpi, commonOptions) match {
        case Left(errors) =>
          Left[Messages, PassesResult](errors)
        case Right(root) =>
          val input: PassInput = PassInput(root, commonOptions)
          val passes = getPasses(log, commonOptions, options)
          val result = Pass.runThesePasses(input, passes, log)
          if result.messages.hasErrors then Left(result.messages)
          else
            if commonOptions.debug then
              println(s"Errors after running ${this.name}:")
              println(result.messages.format)
            Right(result)
      }
    }
  }

  /** The basic implementation of the command. This should be called with `super.run(...)` from the subclass
    * implementation.
    * @param originalOptions
    *   The original options to the command
    * @param commonOptions
    *   The options common to all commands
    * @param log
    *   A logger for logging errors, warnings, and info
    * @param outputDirOverride
    *   Any override to the outputDir option from the command line
    * @return
    *   Either a set of Messages on error or a Unit on success
    */
  override def run(
    originalOptions: OPT,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    val options = if outputDirOverride.nonEmpty then
      val path = outputDirOverride.fold(Path.of(""))(identity)
      overrideOptions(originalOptions, path)
    else originalOptions

    val messages = options.check

    if messages.nonEmpty then {
      Left[Messages, PassesResult](messages) // no point even parsing if there are option errors
    } else {
      doRun(options, commonOptions, log)
    }
  }
}
