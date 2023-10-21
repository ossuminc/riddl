/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.{CommonOptions, Parser}
import com.reactific.riddl.passes.Pass.PassesCreator
import com.reactific.riddl.utils.{Logger, SysLogger}

import java.nio.file.Path


/** Primary Interface to Riddl Language parsing and validating */
object Riddl {

  def validate(
    root: RootContainer,
    options: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
  ): Either[Messages, PassesResult] = {
    Pass.runStandardPasses(root, options, shouldFailOnError)
  }

  def parseAndValidate(
    input: RiddlParserInput,
    commonOptions: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
    passes: PassesCreator = Pass.standardPasses,
    logger: Logger = SysLogger(),
  ): Either[Messages, PassesResult] = {
    Parser.parse(input, commonOptions) match {
      case Left(messages) => Left(messages)
      case Right(root) =>
       val input = PassInput(root, commonOptions)
       Pass.runThesePasses(input, shouldFailOnError, passes, logger)
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
