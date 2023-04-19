/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.commands

import com.reactific.riddl.language.{CommonOptions, Messages, Parser}
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.passes.Pass.PassesCreator
import com.reactific.riddl.passes.{Pass, PassInput, PassesResult}
import com.reactific.riddl.utils.Logger

import java.nio.file.Path
import scala.reflect.ClassTag

trait PassCommandOptions extends CommandOptions {
  def outputDir: Option[Path]
}

/** An abstract base class for translation style commands. That is, they
 * translate an input file into an output directory of files.
 *
 * @param name
 * The name of the command to pass to [[CommandPlugin]]
 * @tparam OPT
 * The option type for the command
 */
abstract class PassCommand[OPT <: PassCommandOptions : ClassTag](name: String) extends CommandPlugin[OPT](name) {

  def getPasses(
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): PassesCreator

  def overrideOptions(options: OPT, newOutputDir: Path): OPT

  private  final def doRun(
    options: OPT,
    commonOptions: CommonOptions,
    log: Logger
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      Parser.parse(inputFile, commonOptions) match {
        case Left(errors) =>
          Left[Messages, PassesResult](errors)
        case Right(root) =>
          val input: PassInput = PassInput(root, commonOptions)
          val passes = getPasses(log, commonOptions, options)
          Pass(input, true, passes, log) match {
            case Left(messages) => Left(messages)
            case Right(result) =>
              if commonOptions.debug then {
                println("Errors after running validation:")
                println(result.messages.format)
              }
          }
          val output = PassesResult(input)
          Right(output)
      }
    }
  }

  override final def run(
    originalOptions: OPT,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    val options =
      if outputDirOverride.nonEmpty then {
        overrideOptions(originalOptions, outputDirOverride.get)
      } else {originalOptions}

    val messages = checkOptions(options)

    if messages.nonEmpty then {
      Left[Messages, PassesResult](messages) // no point even parsing if there are option errors
    } else {
      doRun(options, commonOptions, log)
    }
  }

  private final def checkOptions(options: OPT): Messages = {
    val msgs1: Messages =
      if options.inputFile.isEmpty then {
        Messages.errors("An input path was not provided.")
      } else {Messages.empty}
    val msgs2: Messages =
      if options.outputDir.isEmpty then {
        Messages.errors("An output path was not provided.")
      } else {Messages.empty}
    msgs1 ++ msgs2
  }
}
