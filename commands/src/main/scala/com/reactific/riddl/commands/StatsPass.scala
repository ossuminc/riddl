package com.reactific.riddl.commands

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.passes.{PassInput, PassOutput}
import com.reactific.riddl.language.passes.translate.TranslationPass

import scala.collection.mutable

/** Unit Tests For StatsPass */
class StatsPass(input: PassInput) extends TranslationPass(input) {
  override protected def process(definition: AST.Definition, parents: mutable.Stack[AST.Definition]): Unit = ???

  override def postProcess(root: AST.RootContainer): Unit = ???

  /**
   * Generate the output of this Pass. This will only be called after all the calls
   * to process have completed.
   *
   * @return an instance of the output type
   */
  override def result: PassOutput = ???
}
