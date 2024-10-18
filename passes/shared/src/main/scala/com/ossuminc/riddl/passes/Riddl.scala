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
import com.ossuminc.riddl.utils.{PlatformIOContext, Logger, SysLogger}
import com.ossuminc.riddl.passes.PassCreators

import java.nio.file.Path

/** Primary Interface to Riddl Language parsing and validating */
object Riddl {

  /** Parse an input with options and return the result
    *
    * @param input
    * The [[com.ossuminc.riddl.language.parsing.RiddlParserInput]] to use as the input of the parsing
    * @param commonOptions
    * The [[com.ossuminc.riddl.language.CommonOptions]] to use during the parsing
    * @return
    */
  def parse(input: RiddlParserInput, commonOptions: CommonOptions = CommonOptions.empty)(using io: PlatformIOContext): Either[Messages,Root] = {
    TopLevelParser.parseInput(input, commonOptions)
}

  /** Run the standard passes after parsing
    *
    * @param root
    * The root object of the model which is the product of parsing
    * @param options
    * The common options to use for the validation
    * @param shouldFailOnError
    * Whether this should just return the Left[Messages] if an error occurs in them, or not
    * @return
    */
  def validate(
    root: Root,
    options: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true
  )(using PlatformIOContext) = {
    val result = Pass.runStandardPasses(root, options)
    if shouldFailOnError && result.messages.hasErrors then Left(result.messages)
    else Right(result)
  }

  /** Parse and validate some [[com.ossuminc.riddl.language.parsing.RiddlParserInput]]
    *
    * @param input
    * The [[com.ossuminc.riddl.language.parsing.RiddlParserInput]] to use as the input to the parser
    * @param commonOptions
    * The [[com.ossuminc.riddl.language.CommonOptions]] to use during parsing and validation
    * @param shouldFailOnError
    * If set to true if the parsing succeeds and the validation generates errors in which case the errors will
    * simply be returned, otherwise the PassesResult will be returned.
    * @param passes
    * The set of passes to be run after parsing. It defaults to the standard passes: symbols,
    * reference resolution, validation
    * @param log
    * The log to which messages should be logged
    * @return
    */
  def parseAndValidate(
    input: RiddlParserInput,
    commonOptions: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
    extraPasses: PassCreators = Seq.empty[PassCreator]
  )(using io: PlatformIOContext): Either[Messages, PassesResult] = {
    TopLevelParser.parseInput(input, commonOptions) match {
      case Left(messages) =>
        Left(messages)
      case Right(root) =>
        val input = PassInput(root, commonOptions)
        val result = Pass.runThesePasses(input, Pass.standardPasses ++ extraPasses)
        if shouldFailOnError && result.messages.hasErrors then Left(result.messages)
        else Right(result)
    }
  }

  /** Same as [[Riddl.parseAndValidate()]] but with a Java Path as the input instead */
  def parseAndValidatePath(
    path: Path,
    commonOptions: CommonOptions = CommonOptions.empty,
    shouldFailOnError: Boolean = true,
    extraPasses: PassCreators = Seq.empty[PassCreator]
  )(using io: PlatformIOContext): Either[Messages, PassesResult] = {
    val rpi = RiddlParserInput.fromCwdPath(path)
    parseAndValidate(rpi, commonOptions, shouldFailOnError, Pass.standardPasses ++ extraPasses)
  }
}
