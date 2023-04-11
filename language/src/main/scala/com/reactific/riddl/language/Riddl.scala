/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.FileParserInput
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.parsing.TopLevelParser
import com.reactific.riddl.language.passes.Pass.PassesCreator
import com.reactific.riddl.language.passes.{PassesResult, Pass, PassInput}
import com.reactific.riddl.utils.{Logger, Timer, SysLogger}

import java.nio.file.Files
import java.nio.file.Path

case class CommonOptions(
  showTimes: Boolean = false,
  verbose: Boolean = false,
  dryRun: Boolean = false,
  quiet: Boolean = false,
  showWarnings: Boolean = true,
  showMissingWarnings: Boolean = true,
  showStyleWarnings: Boolean = true,
  showUsageWarnings: Boolean = true,
  debug: Boolean = false,
  pluginsDir: Option[Path] = None,
  sortMessagesByLocation: Boolean = false,
  groupMessagesByKind: Boolean = true
)

object CommonOptions {
  def empty: CommonOptions = CommonOptions()
  def noWarnings: CommonOptions = CommonOptions(showWarnings = false)
  def noMinorWarnings: CommonOptions =
    CommonOptions(showMissingWarnings = false, showStyleWarnings = false, showUsageWarnings = false)
}

/** Primary Interface to Riddl Language parsing and validating */
object Riddl {

  def parse(
    path: Path,
    options: CommonOptions
  ): Either[Messages, RootContainer] = {
    if Files.exists(path) then {
      val input = new FileParserInput(path)
      parse(input, options)
    } else {
      Left(
        List(Messages.error(s"Input file `${path.toString} does not exist."))
      )
    }
  }

  def parse(
    input: RiddlParserInput,
    options: CommonOptions = CommonOptions.empty
  ): Either[Messages, RootContainer] = {
    Timer.time("parse", options.showTimes) {
      TopLevelParser.parse(input)
    }
  }

  def validate(
    root: RootContainer,
    options: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
  ): Either[Messages, PassesResult] = {
    Pass(root, options, shouldFailOnError)
  }

  def parseAndValidate(
    input: RiddlParserInput,
    commonOptions: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
    passes: PassesCreator = Pass.standardPasses,
    logger: Logger = SysLogger(),
  ): Either[Messages, PassesResult] = {
    parse(input, commonOptions) match {
      case Left(messages) => Left(messages)
      case Right(root) =>
       val input = PassInput(root, commonOptions)
       Pass(input, shouldFailOnError, passes, logger)
    }
  }

  def parseAndValidatePath(
    path: Path,
    commonOptions: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
    passes: PassesCreator = Pass.standardPasses,
    logger: Logger = SysLogger(),
  ): Either[Messages, PassesResult] = {
    parseAndValidate(RiddlParserInput(path), commonOptions, shouldFailOnError, passes, logger)
  }
}
