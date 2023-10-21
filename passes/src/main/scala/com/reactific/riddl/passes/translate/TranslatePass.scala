/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.translate

import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.passes.Pass.PassCreator
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.passes.{Pass, PassInput, PassesOutput, PassesResult, Riddl}
import com.reactific.riddl.utils.Logger

import java.nio.file.Path

trait TranslatingOptions {
  def inputFile: Option[Path]
  def outputDir: Option[Path]
  def projectName: Option[String]
}


/** Base class of all Translators
 *
 * @tparam OPT
  *   The options class used by the translator
  */
abstract class TranslationPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  def passes: Pass.PassesCreator = Seq.empty[PassCreator]

  final def parseValidateTranslate(
    input: RiddlParserInput,
    log: Logger,
    commonOptions: CommonOptions,
  ): Either[Messages, PassesResult] = {
    val allPasses = Pass.standardPasses ++ passes
    Riddl.parseAndValidate(input, commonOptions, shouldFailOnError=true, allPasses, log )
  }
}
