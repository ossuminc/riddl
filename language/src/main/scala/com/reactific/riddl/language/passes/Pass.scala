package com.reactific.riddl.language.passes

import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.passes.resolution.{ReferenceMap, ResolutionOutput, ResolutionPass}
import com.reactific.riddl.language.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.reactific.riddl.utils.{Logger, SysLogger, Timer}
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.unused
import scala.collection.mutable
import scala.util.control.NonFatal
import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.passes.validation.{ValidationOutput, ValidationPass}

/** Description of Parser's Output */
case class ParserOutput(
  root: RootContainer,
  commonOptions: CommonOptions = CommonOptions(),
  messages: Messages.Messages = Messages.empty
) extends PassOutput

abstract class PassOutput {
  def root: RootContainer
  def commonOptions: CommonOptions
  def messages: Messages.Messages
}

object PassOutput {
  def empty: PassOutput = new PassOutput {
    def root: RootContainer = RootContainer.empty
    def commonOptions: CommonOptions = CommonOptions()
    def messages: Messages.Messages = Messages.empty
  }
}

/** Abstract Pass definition  */
abstract class Pass[IN <: PassOutput, OUT <: PassOutput](@unused in: IN) {
  /**
   * THe name of the pass for inclusion in messages it produces
   * @return A string value giving the name of this pass
   */
  def name: String = "unnamed pass"

  final def traverse(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    process(definition, parents)
    if (definition.hasDefinitions) {
      parents.push(definition)
      definition.contents.foreach { item => traverse(item, parents) }
      parents.pop()
    }
  }

  protected def process(
    definition: Definition,
    parents: mutable.Stack[Definition],
  ): Unit

  def postProcess(): Unit

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

  case class AggregateOutput(
    root: RootContainer,
    commonOptions: CommonOptions,
    messages: Messages.Messages,
    symbols: SymbolsOutput,
    refMap: ReferenceMap,
    uses: Map[Definition, Seq[Definition]],
    usedBy: Map[Definition, Seq[Definition]],
    inlets: Seq[Inlet],
    outlets: Seq[Outlet],
    connectors: Seq[Connector],
    streamlets: Seq[Streamlet],
    sends: Map[SendAction, Seq[Definition]]
  ) extends PassOutput

  def apply(
    model: RootContainer,
    options: CommonOptions = CommonOptions(),
    shouldFailOnErrors: Boolean = true
  ): Either[Messages.Messages,AggregateOutput]  = {
    val parserOutput = ParserOutput(model, options)
    apply(parserOutput, shouldFailOnErrors)
  }

  def apply(
    input: ParserOutput,
    shouldFailOnErrors: Boolean
  ): Either[Messages.Messages,AggregateOutput]  = {
    try {
      val symbolsOutput = runSymbols(input)
      val resolutionOutput = runResolution(symbolsOutput)
      val validationOutput = runValidation(resolutionOutput)
      val messages = input.messages ++ symbolsOutput.messages ++ resolutionOutput.messages ++ validationOutput.messages
      val result = AggregateOutput(
        input.root, input.commonOptions, messages, symbolsOutput, resolutionOutput.refMap,
        resolutionOutput.uses, resolutionOutput.usedBy, validationOutput.inlets, validationOutput.outlets,
        validationOutput.connectors, validationOutput.streamlets, validationOutput.sends
      )
      if (messages.hasErrors && shouldFailOnErrors) {
        Left(messages)
      } else {
        Right(result)
      }
    } catch {
      case NonFatal(xcptn) =>
        val message = ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")
        val messages: Messages.Messages = List(Messages.severe(message, At.empty))
        Left(messages)
    }
  }

  def runSymbols(input: ParserOutput): SymbolsOutput = {
    Pass.runOnePass[ParserOutput, SymbolsOutput](input, { SymbolsPass(input) } )
  }

  def runResolution(input: SymbolsOutput): ResolutionOutput = {
    runOnePass[SymbolsOutput,ResolutionOutput](input, { ResolutionPass(input)})
  }

  def runValidation(input: ResolutionOutput): ValidationOutput = {
    runOnePass[ResolutionOutput, ValidationOutput](input, {ValidationPass(input)} )
  }

  private def runOnePass[IN <: PassOutput, OUT <: PassOutput](
    in: IN,
    mkPass: => Pass[IN,OUT],
    logger: Logger = SysLogger()
  ): OUT = {
    val pass: Pass[IN,OUT] = mkPass
    Timer.time[OUT](pass.name, in.commonOptions.showTimes, logger) {
      val parents: mutable.Stack[Definition] = mutable.Stack.empty
      pass.traverse(in.root, parents)
      pass.postProcess()
      pass.result
    }
  }
}
