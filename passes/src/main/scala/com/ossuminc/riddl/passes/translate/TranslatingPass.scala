/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.translate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassCreator, PassInput, PassesCreator, PassesOutput, PassesResult, Riddl}
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.utils.Logger

import java.nio.file.Path


/** Base class of all Passes that translate the AST to some other form.
  * 
  * @param input
  * The input to be translated
  * @param outputs
  * The prior outputs from preceding passes
  */
abstract class TranslatingPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  def passes: PassesCreator = Seq.empty[PassCreator]

  final def parseValidateTranslate(
    input: RiddlParserInput,
    log: Logger,
    commonOptions: CommonOptions,
  ): Either[Messages, PassesResult] = {
    val allPasses = Pass.standardPasses ++ passes
    Riddl.parseAndValidate(input, commonOptions, shouldFailOnError=true, allPasses, log )
  }
}
