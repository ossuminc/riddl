/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.{AST, CommonOptions, Messages}
import com.reactific.riddl.passes.resolve.{ReferenceMap, ResolutionOutput, ResolutionPass, Usages}
import com.reactific.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.reactific.riddl.passes.validate.{ValidationOutput, ValidationPass}
import com.reactific.riddl.utils.{Logger, SysLogger, Timer}
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.unused
import scala.collection.mutable
import scala.util.control.NonFatal

trait PassInfo {
  def name: String
}

/**
 * An abstract notion of the minimum notion
 */
case class PassInput(root: RootContainer, commonOptions: CommonOptions = CommonOptions.empty) {

  private[passes]  val priorOutputs: mutable.HashMap[String,PassOutput] = mutable.HashMap.empty

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private var messages: Messages = Messages.empty

  def getMessages: Messages = messages
  def addMessages(addOn: Messages): Unit = { messages = messages ++ addOn }

  def outputOf[T <: PassOutput](passName: String): T = {
    priorOutputs(passName).asInstanceOf[T]
  }

  def outputIs(passName: String, output: PassOutput): Unit = {
    priorOutputs.put(passName, output)
  }

  def hasPassOutput(passName: String): Boolean = {
    priorOutputs.contains(passName)
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
) extends PassOutput {
  def outputOf[T <: PassOutput](passName: String): Option[T] = {
    others.get(passName).map(_.asInstanceOf[T])
  }
}

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
  def name: String

  /**
   * If your pass requires the output from other passes, call this function from your pass's constructor.
   * It will ensure that your pass will fail construction if the input doesn't contain that required pass's output.
   * @param passInfo
   *   The pass's companion object from which this function will obtain the pass's name
   */
  protected final def requires(passInfo: PassInfo): Unit = {
    require(in.hasPassOutput(passInfo.name), s"Required pass '${passInfo.name}' was not run prior to $name'")
  }

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
  def close(): Unit = ()

  protected def traverse(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    process(definition, parents)
    if definition.hasDefinitions then {
      parents.push(definition)
      definition.contents.foreach { item => traverse(item, parents) }
      parents.pop()
    }
  }
}

/**
 * A pass base class that allows the node processing to be done in a depth first hierarchical order by calling:
 *   - openContainer at the start of container's processing
 *   - processLeaf for any leaf node
 *   - closeContainer after all the container's contents have been processed
 *     This kind of Pass allows the processing to follow the AST hierarchy so that container nodes can run before all
 *     their content (openContainer) and also after all its content (closeContainer). This is necessary for passes that
 *     must maintain the hierarchical structure of the AST model in their processing
 *
 * @param input
 * The PassInput to process
 */
abstract class HierarchyPass(input: PassInput) extends Pass(input) {

  // not required in this kind of pass, final override it
  override final def process(definition: AST.Definition, parents: mutable.Stack[AST.Definition]): Unit = ()

  // Instead traverse will use these three methods:
  protected def openContainer(definition: Definition, parents: Seq[Definition]): Unit

  protected def processLeaf(definition: LeafDefinition, parents: Seq[Definition]): Unit

  protected def closeContainer(definition: Definition, parents: Seq[Definition]): Unit

  override protected def traverse(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    definition match {
      case leaf: LeafDefinition =>
        processLeaf(leaf, parents.toSeq)
      case container: Definition =>
        openContainer(container, parents.toSeq)
        if container.hasDefinitions then {
          parents.push(definition)
          definition.contents.foreach { item => traverse(item, parents) }
          parents.pop()
        }
        closeContainer(container, parents.toSeq)
    }
  }
}

case class FoldingPassOutput[T](
  messages: Messages = Messages.empty,
  folded: Seq[T] = Seq.empty[T]
) extends PassOutput

/**
 * A pass base class that allows the node processing to be done in a depth first hierarchical order by calling:
 *   - openContainer at the start of container's processing
 *   - processLeaf for any leaf node
 *   - closeContainer after all the container's contents have been processed
 *     This kind of Pass allows the processing to follow the AST hierarchy so that container nodes can run before all
 *     their content (openContainer) and also after all its content (closeContainer). This is necessary for passes that
 *     must maintain the hierarchical structure of the AST model in their processing
 *
 * @param input
 * The PassInput to process
 */
abstract class FoldingPass[F](input: PassInput) extends Pass(input) {

  // not required in this kind of pass, final override it
  override final def process(definition: AST.Definition, parents: mutable.Stack[AST.Definition]): Unit = ()

  // Instead traverse will use this fold method
  protected def fold(definition: Definition, parents: mutable.Stack[AST.Definition]): F

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  var resultAccumulator: Seq[F] = Seq.empty[F]

  override protected def traverse(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    resultAccumulator :+ fold(definition, parents)
    if definition.hasDefinitions then {
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
    { (input: PassInput) => SymbolsPass(input) },
    { (input: PassInput) => ResolutionPass(input) },
    { (input: PassInput) => ValidationPass(input) }
  )

  val standardPassNames: Seq[String] = Seq(SymbolsPass.name, ResolutionPass.name, ValidationPass.name)

  def apply(
    input: PassInput,
    shouldFailOnErrors: Boolean = true,
    passes: PassesCreator = standardPasses,
    logger: Logger = SysLogger()
  ): Either[Messages.Messages,PassesResult] = {
    try {
      for  pass <- passes  yield {
        val aPass = pass(input)
        val output = runOnePass(input, aPass, logger)
        input.outputIs(aPass.name, output)
      }
      val messages = input.getMessages
      if messages.hasErrors && shouldFailOnErrors then {
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

  private def runPass[OUT <: PassOutput](input: PassInput, pass: Pass): OUT = {
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
