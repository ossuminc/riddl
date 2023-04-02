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
import com.reactific.riddl.language.passes.validation.{ValidationOutput, ValidationPass}

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
      case ad: ApplicationDefinition => processApplicationDefinition(ad, parentsAsSeq)
      case ed: EntityDefinition => processEntityDefinition(ed, parentsAsSeq)
      case rd: RepositoryDefinition => processRepositoryDefinition(rd, parentsAsSeq)
      case sd: SagaDefinition => processSagaDefinition(sd, parentsAsSeq)
      case cd: ContextDefinition => processContextDefinition(cd, parentsAsSeq)
      case dd: DomainDefinition => processDomainDefinition(dd, parentsAsSeq)
      case ad: AdaptorDefinition => processAdaptorDefinition(ad, parentsAsSeq)
      case pd: ProjectorDefinition => processProjectorDefinition(pd, parentsAsSeq)
      case _: RootContainer => () // ignore
      case unimplemented: Definition =>
        throw new NotImplementedError(
          s"Validation of ${unimplemented.identify} is not implemented."
        )
    }
    if (definition.hasDefinitions) {
      processKids(definition, parents)
    }
  }


  /**
   * Process one leaf definition from the model. Leaf definitions occur
   * at the leaves of the definitional hierarchy, and have no further children
   * @param leaf
   * The definition to consider
   * @param parents
   * The parents of the definition as a stack from nearest to the Root
   */

  def processLeafDefinition(leaf: LeafDefinition, parents: Seq[Definition]): Unit
  def processHandlerDefinition(hd: HandlerDefinition, parents: Seq[Definition]): Unit
  def processApplicationDefinition(appDef: ApplicationDefinition, parents: Seq[Definition]): Unit
  def processEntityDefinition(entDef: EntityDefinition, parents: Seq[Definition]): Unit
  def processRepositoryDefinition(repoDef: RepositoryDefinition, parents: Seq[Definition]): Unit
  def processProjectorDefinition(pd: ProjectorDefinition, parents: Seq[Definition]): Unit
  def processSagaDefinition(sagaDef: SagaDefinition, parents: Seq[Definition]): Unit
  def processContextDefinition(contextDef: ContextDefinition, parents: Seq[Definition]): Unit
  def processDomainDefinition(domDef: DomainDefinition, parents: Seq[Definition]): Unit
  def processAdaptorDefinition(adaptDef: AdaptorDefinition, parents: Seq[Definition]): Unit

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

  type AggregateOutput = (ParserOutput, SymbolsOutput, ResolutionOutput, ValidationOutput)

  def apply(
    input: ParserOutput
  ): Either[Messages.Message,AggregateOutput]  = {
    try {
      // Using.Manager { implicit use =>
      val symbolsOutput = runSymbols(input)
      val resolutionOutput = runResolution(symbolsOutput)
      val validationOutput = runValidation(resolutionOutput)
      Right((input, symbolsOutput, resolutionOutput, validationOutput))
    } catch {
      case NonFatal(xcptn) =>
        val message = ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")
        Left(Messages.severe(message, At.empty))
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
      pass.result
    }
  }
}
