/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.codify

import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.{
  PassCreator,
  PassInfo,
  PassInput,
  PassesOutput,
  TranslatingPass,
  TranslatingPassOutput
}
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.symbols.Symbols.ParentStack
import com.ossuminc.riddl.passes.validate.ValidationPass

object CodifyPass extends PassInfo {
  val name: String = "codify"
  val creator: PassCreator = { (in: PassInput, out: PassesOutput) => CodifyPass(in, out) }
}

/** The output from running the CodifyPass */
case class CodifyOutput(
  messages: Messages = Messages.empty,
  newASTRoot: Root
) extends TranslatingPassOutput(messages, newASTRoot)

/** A pass that translates RIDDL definitions into RIDDL code statements */
case class CodifyPass(input: PassInput, outputs: PassesOutput) extends TranslatingPass(input, outputs) {

  def name: String = CodifyPass.name

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)

  protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit = ()

  private var newRootAST: Root = ???

  def postProcess(root: Root): Unit = {
    // TODO: Postprocess root to transform it into newRootAST
    newRootAST = root
  }

  /** Generate the output of this Pass. This will only be called after all the calls to process have completed.
    *
    * @return
    *   an instance of the output type
    */
  override def result: CodifyOutput = {
    CodifyOutput(
      Messages.empty,
      newRootAST
    )
  }
}
