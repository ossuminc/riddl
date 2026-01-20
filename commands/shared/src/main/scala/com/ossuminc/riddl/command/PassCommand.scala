/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.TopLevelParser
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, Logger, PathUtils, PlatformContext, URL, Await}
import com.ossuminc.riddl.passes.{Pass, PassCreators, PassInput, PassesResult}

import java.nio.file.Path
import scala.reflect.ClassTag
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

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
abstract class PassCommand[OPT <: PassCommandOptions: ClassTag](name: String)(using pc: PlatformContext)
    extends Command[OPT](name) {

  /** Get the passes to run given basic input for pass creation
    *
    * @param log
    *   The log to use
    * @param commonOptions
    *   The common options for the command
    * @param options
    *   The command options
    * @return
    *   A [[com.ossuminc.riddl.passes.PassCreator]] function that creates the pass
    */
  def getPasses(
    options: OPT
  ): PassCreators

  /** A method to override the options */
  def overrideOptions(options: OPT, newOutputDir: Path): OPT

  private final def doRun(
    options: OPT
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputPath: Path) =>
      val url = PathUtils.urlFromPath(inputPath)
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match {
          case Left(errors) =>
            Left[Messages, PassesResult](errors)
          case Right(root) =>
            val input: PassInput = PassInput(root)
            val passes = getPasses(options)
            val result = Pass.runThesePasses(input, passes)
            if result.messages.hasErrors then Left(result.messages)
            else
              if pc.options.debug then
                println(s"Errors after running ${this.name}:")
                println(result.messages.format)
              Right(result)
        }
      }
      Await.result(future, 20.seconds)
    }
  }

  /** The basic implementation of the command. This should be called with `super.run(...)` from the subclass
    * implementation.
    * @param originalOptions
    *   The original options to the command
    * @param commonOptions
    *   The options common to all commands
    * @param log
    *   A log for logging errors, warnings, and info
    * @param outputDirOverride
    *   Any override to the outputDir option from the command line
    * @return
    *   Either a set of Messages on error or a Unit on success
    */
  override def run(
    originalOptions: OPT,
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
      doRun(options)
    }
  }
}
