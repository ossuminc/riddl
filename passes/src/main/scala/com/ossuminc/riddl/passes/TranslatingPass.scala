/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassCreator, PassInput, PassesCreator, PassesOutput, PassesResult, Riddl}
import com.ossuminc.riddl.utils.Logger

import java.nio.file.Path

/** An abstract PassOutput for use with passes that derive from TranslatingPass. This just provides a standard
  * field name for the data that is collected.
  *
  * @param messages
  *   The required messages field from the PassOutput trait
  * @param newASTRoot
  *   The new AST Root after the AST was translated
  * @tparam T
  * The element type of the collected data
  */
abstract class TranslatingPassOutput(
  messages: Messages = Messages.empty,
  newASTRoot: Root
) extends PassOutput

/** Base class of all Translators
  */
abstract class TranslatingPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  def passes: PassesCreator = Seq.empty[PassCreator]

  final def parseValidateTranslate(
    input: RiddlParserInput,
    log: Logger,
    commonOptions: CommonOptions
  ): Either[Messages, PassesResult] = {
    val allPasses = Pass.standardPasses ++ passes
    Riddl.parseAndValidate(input, commonOptions, shouldFailOnError = true, allPasses, log)
  }
}
