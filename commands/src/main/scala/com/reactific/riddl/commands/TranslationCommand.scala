/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Riddl
import com.reactific.riddl.language.TranslatingOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.passes.AggregateOutput
import com.reactific.riddl.utils.{Logger, Timer}

import java.nio.file.Path
import scala.reflect.ClassTag

object TranslationCommand {
  trait Options extends TranslatingOptions with CommandOptions {
    def inputFile: Option[Path]
    def outputDir: Option[Path]
    def projectName: Option[String]
  }
}

/** An abstract base class for translation style commands. That is, they
  * translate an input file into an output directory of files.
  * @param name
  *   The name of the command to pass to [[CommandPlugin]]
  * @tparam OPT
  *   The option type for the command
  */
abstract class TranslationCommand[OPT <: TranslationCommand.Options: ClassTag](
  name: String)
    extends CommandPlugin[OPT](name) {

  /** Implement this in your subclass to do the translation. The input will have
    * been parsed and validated already so the job is to translate the root
    * argument into the directory of files.
    *
    * @param log
    *   A Logger to use for messages. Use sparingly, not for errors
    * @param commonOptions
    *   The options common to all commands
    * @param options
    *   The options specific to your subclass implementation
    * @return
    *   A Right[Unit] if successful or Left[Messages] if not
    */
  protected def translateImpl(
    validationResult: AggregateOutput,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Either[Messages, Unit]

  def overrideOptions(options: OPT, newOutputDir: Path): OPT

  private final def checkOptions(options: OPT): Messages = {
    val msgs1: Messages =
      if (options.inputFile.isEmpty) {
        Messages.errors("An input path was not provided.")
      } else { Messages.empty }
    val msgs2: Messages =
      if (options.outputDir.isEmpty) {
        Messages.errors("An output path was not provided.")
      } else { Messages.empty }
    msgs1 ++ msgs2
  }

  private final def doRun(
    options: OPT,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, Unit] = {
    options.withInputFile { inputFile: Path =>
      for {
        root <- Riddl.parse(inputFile, commonOptions)
        result <- Riddl.validate(root, commonOptions)
      } yield {
        if (result.messages.hasErrors) {
          if (commonOptions.debug) {
            println("Errors after running validation:")
            println(result.messages.format)
          }
          Left[Messages, Unit](result.messages)
        } else {
          val showTimes = commonOptions.showTimes
          Timer.time(stage = "translate", showTimes) {
            translateImpl(result, log, commonOptions, options)
          }
        }
      }
    }
  }

  override final def run(
    originalOptions: OPT,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, Unit] = {
    val options =
      if (outputDirOverride.nonEmpty) {
        overrideOptions(originalOptions, outputDirOverride.get)
      } else { originalOptions }

    val messages = checkOptions(options)

    if (messages.nonEmpty) {
      Left[Messages, Unit](
        messages
      ) // no point even parsing if there are option errors
    } else { doRun(options, commonOptions, log) }
  }
}
