/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.stats

import com.reactific.riddl.commands.InputFileCommandPlugin
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.passes.{Pass, PassesResult}
import com.reactific.riddl.language.{CommonOptions, Riddl}
import com.reactific.riddl.utils.Logger

import java.nio.file.Path

/** Validate Command */
class StatsCommand extends InputFileCommandPlugin("stats") {
  import InputFileCommandPlugin.Options

  override def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { inputFile: Path =>
      val passes = Pass.standardPasses ++ Seq({ input => StatsPass(input) })
      Riddl.parseAndValidatePath(inputFile, commonOptions, passes = passes, logger = log) match {
        case Left(messages) => Left(messages)
        case Right(result) =>
          result.outputOf[StatsOutput](StatsPass.name) match {
            case Some(stats) =>
              println(s"Number of Definitions: ${stats.count}")
              println(s"Number of Terms: ${stats.term_count}")
              println(s"Maximum Depth: ${stats.maximum_depth}")
              for {(k, v) <- stats.categories} {
                println(s"$k: $v")
              }
              println()
            case None => println("No statistics generated")
          }
          Right(result)
      }
    }
  }

  override def replaceInputFile(
    opts: Options,
    inputFile: Path
  ): Options = { opts.copy(inputFile = Some(inputFile)) }

  override def loadOptionsFrom(
    configFile: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile, commonOptions).map { options =>
      resolveInputFileToConfigFile(options, commonOptions, configFile)
    }
  }
}

