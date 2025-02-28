/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl
import com.ossuminc.riddl.language.AST.{Branch, Root, Token}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.PassCreators
import com.ossuminc.riddl.passes.prettify.PrettifyOutput
import com.ossuminc.riddl.passes.prettify.PrettifyPass
import com.ossuminc.riddl.utils.{Await, PlatformContext, URL}

import scala.collection.IndexedSeqView

/** Primary Interface to Riddl Language parsing and validating */
object Riddl {

  /** Parse an input with options and return the result
    *
    * @param input
    *   The [[com.ossuminc.riddl.language.parsing.RiddlParserInput]] to use as the input of the
    *   parsing
    * @return
    */
  def parse(input: RiddlParserInput)(using io: PlatformContext): Either[Messages, Root] = {
    TopLevelParser.parseInput(input)
  }

  /** Run the standard passes after parsing
    *
    * @param root
    *   The root object of the model which is the product of parsing
    * @param shouldFailOnError
    *   Whether this should just return the Left[Messages] if an error occurs in them, or not
    * @return
    */
  def validate(
    root: Root,
    shouldFailOnError: Boolean = true
  )(using PlatformContext): Either[Messages, PassesResult] = {
    val result = Pass.runStandardPasses(root)
    if shouldFailOnError && result.messages.hasErrors then Left(result.messages)
    else Right(result)
  }

  /** Parse and validate some [[com.ossuminc.riddl.language.parsing.RiddlParserInput]]
    *
    * @param input
    *   The [[com.ossuminc.riddl.language.parsing.RiddlParserInput]] to use as the input to the
    *   parser
    * @param shouldFailOnError
    *   If set to true if the parsing succeeds and the validation generates errors in which case the
    *   errors will simply be returned, otherwise the PassesResult will be returned.
    * @return
    */
  def parseAndValidate(
    input: RiddlParserInput,
    shouldFailOnError: Boolean = true,
    extraPasses: PassCreators = Seq.empty[PassCreator]
  )(using io: PlatformContext): Either[Messages, PassesResult] = {
    TopLevelParser.parseInput(input) match {
      case Left(messages) =>
        Left(messages)
      case Right(root) =>
        val input = PassInput(root)
        val result = Pass.runThesePasses(input, Pass.standardPasses ++ extraPasses)
        if shouldFailOnError && result.messages.hasErrors then Left(result.messages)
        else Right(result)
    }
  }

  /** Same as [[Riddl.parseAndValidate()]] but with a Java Path as the input instead */
  def parseAndValidatePath(
    path: String,
    shouldFailOnError: Boolean = true,
    extraPasses: PassCreators = Seq.empty[PassCreator]
  )(using io: PlatformContext): Either[Messages, PassesResult] =
    val url = URL.fromCwdPath(path)
    val rpi = Await.result(RiddlParserInput.fromURL(url), 10)
    parseAndValidate(rpi, shouldFailOnError, Pass.standardPasses ++ extraPasses)
  end parseAndValidatePath

  /** Convert a previously parsed Root back into plain text */
  def toRiddlText(root: Branch[?])(using pc: PlatformContext): String =
    val input: PassInput = PassInput(root)
    val outputs: PassesOutput = PassesOutput()
    val result = Pass.runPass[PrettifyOutput](
      input,
      outputs,
      PrettifyPass(input, outputs, PrettifyPass.Options(flatten = true))
    )
    result.state.filesAsString
  end toRiddlText

  /** Convert a previously parsed Root to Tokens and corresponding string text */
  def mapTextAndToken[T](
    root: Branch[?]
  )(f: (IndexedSeqView[Char],Token) => T)
  (using pc: PlatformContext): Either[Messages, List[T]] =
    val text = toRiddlText(root)
    val rpi = RiddlParserInput(text, "")
    TopLevelParser.mapTextAndToken[T](rpi)(f)
  end mapTextAndToken
}
