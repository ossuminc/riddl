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

  protected final def processKids(container: Definition, parents: mutable.Stack[Definition]): Unit = {
    require(container.hasDefinitions)
    parents.push(container)
    container.contents.foreach { item => process(item, parents) }
    parents.pop()
  }

  protected def process(
    definition: Definition,
    parents: mutable.Stack[Definition],
  ): Unit = {
    val parentsAsSeq = parents.toSeq
    definition match {
      case leaf: LeafDefinition => processLeafDefinition(leaf, parentsAsSeq)
      case hd: HandlerDefinition => processHandlerDefinition(hd, parentsAsSeq)
      case od: OnClauseDefinition => processOnClauseDefinition(od, parentsAsSeq)
      case ad: ApplicationDefinition => processApplicationDefinition(ad, parentsAsSeq)
      case ud: UseCaseDefinition => processUseCaseDefinition(ud, parentsAsSeq)
      case ed: EntityDefinition => processEntityDefinition(ed, parentsAsSeq)
      case sd: StateDefinition => processStateDefinition(sd, parentsAsSeq)
      case rd: RepositoryDefinition => processRepositoryDefinition(rd, parentsAsSeq)
      case sd: SagaDefinition => processSagaDefinition(sd, parentsAsSeq)
      case cd: ContextDefinition => processContextDefinition(cd, parentsAsSeq)
      case sd: StreamletDefinition => processStreamletDefinition(sd, parentsAsSeq)
      case ad: AdaptorDefinition => processAdaptorDefinition(ad, parentsAsSeq)
      case pd: ProjectorDefinition => processProjectorDefinition(pd, parentsAsSeq)
      case fd: FunctionDefinition => processFunctionDefinition(fd, parentsAsSeq)
      case ed: EpicDefinition => processEpicDefinition(ed, parentsAsSeq)
      case rd: RootDefinition => processRootDefinition(rd, parentsAsSeq)
      case dd: DomainDefinition => processDomainDefinition(dd, parentsAsSeq)
      case _: RootContainer => ()
    }
    if (definition.hasDefinitions) {
      processKids(definition, parents)
    }
  }

  def postProcess(): Unit

  /**
   * Process one leaf definition from the model. Leaf definitions occur
   * at the leaves of the definitional hierarchy, and have no further children
   * @param leaf
   * The definition to consider
   * @param parents
   * The parents of the definition as a stack from nearest to the Root
   */

  def processAdaptorDefinition(adaptDef: AdaptorDefinition, parents: Seq[Definition]): Unit
  def processApplicationDefinition(appDef: ApplicationDefinition, parents: Seq[Definition]): Unit
  def processContextDefinition(contextDef: ContextDefinition, parents: Seq[Definition]): Unit
  def processDomainDefinition(domDef: DomainDefinition, parents: Seq[Definition]): Unit
  def processEntityDefinition(entDef: EntityDefinition, parents: Seq[Definition]): Unit
  def processEpicDefinition(epicDef: EpicDefinition, parents: Seq[Definition]): Unit
  def processFunctionDefinition(funcDef: FunctionDefinition, parents: Seq[Definition]): Unit
  def processHandlerDefinition(hd: HandlerDefinition, parents: Seq[Definition]): Unit
  def processLeafDefinition(leaf: LeafDefinition, parents: Seq[Definition]): Unit
  def processOnClauseDefinition(ocd: OnClauseDefinition, parents: Seq[Definition]): Unit
  def processProjectorDefinition(pd: ProjectorDefinition, parents: Seq[Definition]): Unit
  def processRepositoryDefinition(repoDef: RepositoryDefinition, parents: Seq[Definition]): Unit
  def processRootDefinition(rootDef: RootDefinition, parents: Seq[Definition]): Unit
  def processSagaDefinition(sagaDef: SagaDefinition, parents: Seq[Definition]): Unit
  def processStateDefinition(stateDef: StateDefinition, parents: Seq[Definition]): Unit
  def processStreamletDefinition(streamDef: StreamletDefinition, parents: Seq[Definition]): Unit
  def processUseCaseDefinition(ucDef: UseCaseDefinition, parents: Seq[Definition]): Unit

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
      pass.process(in.root, parents)
      pass.postProcess()
      pass.result
    }
  }
}
