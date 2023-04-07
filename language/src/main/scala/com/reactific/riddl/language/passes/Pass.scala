package com.reactific.riddl.language.passes

import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.passes.resolve.{ReferenceMap, ResolutionOutput, ResolutionPass, Usages}
import com.reactific.riddl.language.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.reactific.riddl.utils.{Logger, SysLogger, Timer}
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.unused
import scala.collection.mutable
import scala.util.control.NonFatal
import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.passes.validate.{ValidationOutput, ValidationPass}

trait PassOptions extends AnyRef

/**
 * An abstract notion of the minimum notion
 */
case class PassInput(root: RootContainer, commonOptions: CommonOptions = CommonOptions.empty) {

  private[passes]  val priorOutputs: mutable.HashMap[String,PassOutput] = mutable.HashMap.empty
  private var messages: Messages = Messages.empty

  def getMessages: Messages = messages
  def addMessages(addOn: Messages): Unit = { messages = messages ++ addOn }

  def outputOf[T <: PassOutput](passName: String): T = {
    priorOutputs(passName).asInstanceOf[T]
  }

  def outputIs(passName: String, output: PassOutput): Unit = {
    priorOutputs.put(passName, output)
  }
}

abstract class PassOutput {
  def messages: Messages.Messages
}

object PassOutput {
  def empty: PassOutput = new PassOutput {
    def messages: Messages.Messages = Messages.empty
  }
}

case class PassesResult(
  root: RootContainer = RootContainer.empty,
  commonOptions: CommonOptions = CommonOptions.empty,
  messages: Messages.Messages = Messages.empty,
  symbols: SymbolsOutput = SymbolsOutput(),
  refMap: ReferenceMap = ReferenceMap.empty,
  usage: Usages = Usages.empty,
  others: Map[String, PassOutput] = Map.empty
) extends PassOutput

object PassesResult {
  val empty: PassesResult = PassesResult()
  def apply(input: PassInput): PassesResult = {
    val symbols = input.outputOf[SymbolsOutput](SymbolsPass.name)
    val resolution = input.outputOf[ResolutionOutput](ResolutionPass.name)
    //val validation = input.outputOf[ValidationOutput](ValidationPass.name)
    val others = input.priorOutputs.toMap.filterNot(Pass.standardPassNames.contains(_))
    PassesResult(input.root, input.commonOptions, input.getMessages, symbols,
      resolution.refMap, resolution.usage, others)
  }
}


/** Abstract Pass definition  */
abstract class Pass(@unused in: PassInput) {
  /**
   * THe name of the pass for inclusion in messages it produces
   * @return A string value giving the name of this pass
   */
  def name: String = "unnamed pass"

  def requires: Pass.PassesCreator = Seq()

  protected def process(
    definition: Definition,
    parents: mutable.Stack[Definition],
  ): Unit

  def postProcess(root: RootContainer): Unit

  /**
   * Generate the output of this Pass. This will only be called after all the calls
   * to process have completed.
   * @return an instance of the output type
   */
  def result: PassOutput

  /**
   * Close any resources used so this can be used with AutoCloseable or Using.Manager
   */
  def close: Unit = ()

  protected final def traverse(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    process(definition, parents)
    if (definition.hasDefinitions) {
      parents.push(definition)
      definition.contents.foreach { item => traverse(item, parents) }
      parents.pop()
    }
  }

}



object Pass {

  type PassCreator = PassInput => Pass
  type PassesCreator = Seq[PassCreator]

  val standardPasses: PassesCreator = Seq(
    { input: PassInput => SymbolsPass(input) },
    { input: PassInput => ResolutionPass(input) },
    { input: PassInput => ValidationPass(input)  }
  )

  val standardPassNames = Seq(SymbolsPass.name, ResolutionPass.name, ValidationPass.name)

  def apply(
    input: PassInput,
    shouldFailOnErrors: Boolean = true,
    passes: PassesCreator = standardPasses,
    logger: Logger = SysLogger()
  ): Either[Messages.Messages,PassesResult] = {
    try {
      for { pass <- passes } yield {
        val aPass = pass(input)
        val output = runOnePass(input, aPass, logger)
        input.outputIs(aPass.name, output)
      }
      val messages = input.getMessages
      if (messages.hasErrors && shouldFailOnErrors) {
        Left(messages)
      } else {
        Right(PassesResult(input))
      }
      } catch {
        case NonFatal(xcptn) =>
          val message = ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")
          val messages: Messages.Messages = List(Messages.severe(message, At.empty))
          Left(messages)
      }
  }

  def apply(
    model: RootContainer,
    options: CommonOptions,
    shouldFailOnErrors: Boolean
  ): Either[Messages.Messages,PassesResult]  = {
    val input: PassInput = PassInput(model, options)
    apply(input, shouldFailOnErrors, standardPasses, SysLogger())
  }

  def runSymbols(input: PassInput): SymbolsOutput = {
    runPass[SymbolsOutput](input, SymbolsPass(input) )
  }

  def runResolution(input: PassInput): ResolutionOutput = {
    runPass[ResolutionOutput](input, ResolutionPass(input))
  }

  def runValidation(input: PassInput): ValidationOutput = {
    runPass[ValidationOutput](input, ValidationPass(input) )
  }

  def runPass[OUT <: PassOutput](input: PassInput, pass: Pass): OUT = {
    Pass.runOnePass(input, pass).asInstanceOf[OUT]
  }

  private def runOnePass(
    in: PassInput,
    mkPass: => Pass,
    logger: Logger = SysLogger()
  ): PassOutput = {
    val pass: Pass = mkPass
    Timer.time[PassOutput](pass.name, in.commonOptions.showTimes, logger) {
      val parents: mutable.Stack[Definition] = mutable.Stack.empty
      pass.traverse(in.root, parents)
      pass.postProcess(in.root)
      val output = pass.result
      in.outputIs(pass.name, output)
      in.addMessages(output.messages)
      output
    }
  }
}
