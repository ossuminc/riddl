/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.utils.{Logger, SysLogger}
import com.ossuminc.riddl.passes.PassesCreator

import java.nio.file.Path

/** Primary Interface to Riddl Language parsing and validating */
object Riddl {

  def validate(
    root: Root,
    options: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true
  ): Either[Messages, PassesResult] = {
    val result = Pass.runStandardPasses(root, options)
    if shouldFailOnError && result.messages.hasErrors then Left(result.messages)
    else Right(result)
  }

  def parseAndValidate(
    input: RiddlParserInput,
    commonOptions: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
    passes: PassesCreator = Pass.standardPasses,
    logger: Logger = SysLogger()
  ): Either[Messages, PassesResult] = {
    TopLevelParser.parseInput(input, commonOptions) match {
      case Left(messages) => Left(messages)
      case Right(root) =>
        val input = PassInput(root, commonOptions)
        val result = Pass.runThesePasses(input, passes, logger)
        if shouldFailOnError && result.messages.hasErrors then Left(result.messages)
        else Right(result)
    }
  }

  def parseAndValidatePath(
    path: Path,
    commonOptions: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
    passes: PassesCreator = Pass.standardPasses,
    logger: Logger = SysLogger()
  ): Either[Messages, PassesResult] = {
    parseAndValidate(RiddlParserInput(path), commonOptions, shouldFailOnError, passes, logger)
  }
}
