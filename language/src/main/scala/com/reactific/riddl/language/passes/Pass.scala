package com.reactific.riddl.language.passes

import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.passes.resolution.{ResolutionOutput, ResolutionPass}
import com.reactific.riddl.language.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.reactific.riddl.utils.{Logger, SysLogger, Timer}
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.unused
import scala.collection.mutable
import scala.util.control.NonFatal

import com.reactific.riddl.language.AST.RootContainer

/** Description of Parser's Output */
case class ParserOutput(
  root: RootContainer,
  commonOptions: CommonOptions,
) extends PassOutput {
  val messages: Messages.Accumulator = new Messages.Accumulator(commonOptions)
}

abstract class PassOutput {
  def root: RootContainer
  def commonOptions: CommonOptions
  def messages: Messages.Accumulator
}

object PassOutput {
  def empty: PassOutput = new PassOutput {
    def root: RootContainer = RootContainer.empty
    def commonOptions: CommonOptions = CommonOptions()
    def messages: Messages.Accumulator = Messages.Accumulator.empty
  }
}

/** Abstract Pass definition  */
abstract class Pass[IN <: PassOutput, OUT <: PassOutput](@unused in: IN) {
  /**
   * THe name of the pass for inclusion in messages it produces
   * @return A string value giving the name of this pass
   */
  def name: String = "unnamed pass"

  /**
   * Process one definition from the model. This is where the work of this
   * pass gets done.
   * @param definition
   * The definition to consider
   * @param parents
   * The parents of the definition as a stack from nearest to the Root
   */
  def process(definition: Definition, parents: mutable.Stack[Definition]): Unit

  /**
   * Generate the output of this Pass. This will only be called after all the calls
   * to process have completed.
   * @return an instance of the output type
   */
  def result: OUT

  /**
   * Close any resources used so this can be used with AutoCloseable or Using.Manager
   */
  def close: Unit = ()
}

object Pass {

  type AggregateOutput = (ParserOutput, SymbolsOutput, ResolutionOutput)

  def apply(
    input: ParserOutput
  ): Either[Messages.Message,AggregateOutput]  = {
    try {
      // Using.Manager { implicit use =>
      val symbolsOutput = runOnePass[ParserOutput,SymbolsOutput](input, { SymbolsPass(input) })
      val resolutionOutput = runOnePass[SymbolsOutput,ResolutionOutput](symbolsOutput, { ResolutionPass(symbolsOutput)})
      Right((input, symbolsOutput, resolutionOutput))
    } catch {
      case NonFatal(xcptn) =>
        val message = ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")
        Left(Messages.severe(message, At.empty))
    }
  }

  def runSymbols(input: ParserOutput): SymbolsOutput = {
    Pass.runOnePass[ParserOutput, SymbolsOutput](input, { SymbolsPass(input) }, SysLogger() )
  }

  private def runOnePass[IN <: PassOutput, OUT <: PassOutput](
    in: IN,
    mkPass: => Pass[IN,OUT],
    logger: Logger = SysLogger()
  ): OUT = {
    val pass: Pass[IN,OUT] = mkPass
    Timer.time[OUT](pass.name, in.commonOptions.showTimes, logger) {
      val parents: mutable.Stack[Definition] = mutable.Stack.empty
      traverse(in.root, parents, pass)
      pass.result
    }
  }


  private def traverse[IN <: PassOutput, OUT <: PassOutput](
    definition: Definition,
    parents: mutable.Stack[Definition],
    pass: Pass[IN,OUT]
  ): Unit = {
    definition match {
      case leaf: LeafDefinition =>
        pass.process(leaf, parents)
      case container: Definition =>
        pass.process(container, parents)
        parents.push( container )
        container.contents.foreach { item => traverse(item, parents, pass) }
        parents.pop()
    }
  }
}
