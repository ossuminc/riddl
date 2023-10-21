/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.{AST, At, CommonOptions, Messages}
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

trait PassOutput {
  def messages: Messages.Messages
}

object PassOutput {
  def empty: PassOutput = new PassOutput { val messages: Messages.Messages = Messages.empty }
}

case class PassesOutput() {

  private val outputs: mutable.HashMap[String, PassOutput] = mutable.HashMap.empty

  def getAllMessages: Messages = {
    outputs.values.foldLeft(Messages.empty) { case (prior, current) =>
      prior.appendedAll(current.messages)
    }
  }

  def outputOf[T <: PassOutput](passName: String): Option[T] = {
    outputs.get(passName).map(_.asInstanceOf[T])
  }

  def outputIs(passName: String, output: PassOutput): Unit = {
    outputs.put(passName, output)
  }

  def hasPassOutput(passName: String): Boolean = {
    outputs.contains(passName)
  }

  def getNonStandardOutputs: Map[String, PassOutput] =
    outputs.toMap.filterNot(Pass.standardPassNames.contains(_))
}

case class PassInput(
  root: RootContainer,
  commonOptions: CommonOptions = CommonOptions.empty,
  outputs: PassesOutput = PassesOutput()
)

case class PassesResult(
  root: RootContainer = RootContainer.empty,
  commonOptions: CommonOptions = CommonOptions.empty,
  messages: Messages.Messages = Messages.empty,
  symbols: SymbolsOutput = SymbolsOutput(),
  refMap: ReferenceMap = ReferenceMap.empty,
  usage: Usages = Usages.empty,
  outputs: PassesOutput = PassesOutput()
) extends PassOutput {
  def outputOf[T <: PassOutput](passName: String): Option[T] = {
    outputs.outputOf[T](passName)
  }

  def hasWarnings: Boolean = {
    messages.hasWarnings
  }
}

object PassesResult {
  val empty: PassesResult = PassesResult()
  def apply(input: PassInput, outputs: PassesOutput, messages: Messages): PassesResult = {
    val maybeResult = for {
      symbols <- outputs.outputOf[SymbolsOutput](SymbolsPass.name)
      resolution <- outputs.outputOf[ResolutionOutput](ResolutionPass.name)
      validation <- outputs.outputOf[ValidationOutput](ValidationPass.name)
    } yield {
      PassesResult(
        input.root,
        input.commonOptions,
        outputs.getAllMessages ++ messages,
        symbols,
        resolution.refMap,
        resolution.usage,
        outputs
      )
    }
    maybeResult match {
      case Some(result) => result
      case None => PassesResult()
    }
  }
}

/** Abstract Pass definition */
abstract class Pass(@unused val in: PassInput, val out: PassesOutput) {

  /** THe name of the pass for inclusion in messages it produces
    * @return
    *   A string value giving the name of this pass
    */
  def name: String

  /** If your pass requires the output from other passes, call this function from your pass's constructor. It will
    * ensure that your pass will fail construction if the input doesn't contain that required pass's output.
    * @param passInfo
    *   The pass's companion object from which this function will obtain the pass's name
    */
  protected final def requires(passInfo: PassInfo): Unit = {
    require(out.hasPassOutput(passInfo.name), s"Required pass '${passInfo.name}' was not run prior to $name'")
  }

  protected def process(
    definition: Definition,
    parents: mutable.Stack[Definition]
  ): Unit

  def postProcess(root: RootContainer): Unit

  /** Generate the output of this Pass. This will only be called after all the calls to process have completed.
    * @return
    *   an instance of the output type
    */
  def result: PassOutput

  /** Close any resources used so this can be used with AutoCloseable or Using.Manager
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

  protected val messages: Messages.Accumulator = Messages.Accumulator(in.commonOptions)
}

/** A pass base class that allows the node processing to be done in a depth first hierarchical order by calling:
  *   - openContainer at the start of container's processing
  *   - processLeaf for any leaf node
  *   - closeContainer after all the container's contents have been processed This kind of Pass allows the processing to
  *     follow the AST hierarchy so that container nodes can run before all their content (openContainer) and also after
  *     all its content (closeContainer). This is necessary for passes that must maintain the hierarchical structure of
  *     the AST model in their processing
  *
  * @param input
  *   The PassInput to process
  */
abstract class HierarchyPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

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

abstract class CollectingPassOutput[T](
  messages: Messages = Messages.empty,
  collected: Seq[T] = Seq.empty[T]
) extends PassOutput

/** A pass base class that allows the node processing to be done in a depth first hierarchical order by calling:
  *   - openContainer at the start of container's processing
  *   - processLeaf for any leaf node
  *   - closeContainer after all the container's contents have been processed This kind of Pass allows the processing to
  *     follow the AST hierarchy so that container nodes can run before all their content (openContainer) and also after
  *     all its content (closeContainer). This is necessary for passes that must maintain the hierarchical structure of
  *     the AST model in their processing
  *
  * @param input
  *   The PassInput to process
  */
abstract class CollectingPass[F](input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  // not required in this kind of pass, final override it
  override final def process(definition: AST.Definition, parents: mutable.Stack[AST.Definition]): Unit = ()

  // Instead traverse will use this fold method
  protected def collect(definition: Definition, parents: mutable.Stack[AST.Definition]): F

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  protected var collectedValues: Seq[F] = Seq.empty[F]

  override protected def traverse(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    collectedValues = collectedValues :+ collect(definition, parents)
    if definition.hasDefinitions then {
      parents.push(definition)
      definition.contents.foreach { item => traverse(item, parents) }
      parents.pop()
    }
  }
}

object Pass {

  type PassCreator = (PassInput, PassesOutput) => Pass
  type PassesCreator = Seq[PassCreator]

  val standardPasses: PassesCreator = Seq(
    { (input: PassInput, outputs: PassesOutput) => SymbolsPass(input, outputs) },
    { (input: PassInput, outputs: PassesOutput) => ResolutionPass(input, outputs) },
    { (input: PassInput, outputs: PassesOutput) => ValidationPass(input, outputs) }
  )

  val standardPassNames: Seq[String] = Seq(SymbolsPass.name, ResolutionPass.name, ValidationPass.name)

  def runThesePasses(
    input: PassInput,
    passes: PassesCreator = standardPasses,
    logger: Logger = SysLogger()
  ): PassesResult = {
    val outputs = PassesOutput()
    try {
      for pass <- passes yield {
        val aPass = pass(input, outputs)
        val output = runOnePass(input, outputs, aPass, logger)
        outputs.outputIs(aPass.name, output)
      }
      PassesResult(input, outputs, Messages.empty)
    } catch {
      case NonFatal(xcptn) =>
        val message = ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")
        val messages: Messages.Messages = List(Messages.severe(message, At.empty))
        PassesResult(input,outputs, messages)
    }
  }

  def runStandardPasses(
    input: PassInput,
  ): PassesResult = {
    runThesePasses(input, standardPasses, SysLogger())
  }

  def runStandardPasses(
    model: RootContainer,
    options: CommonOptions,
  ): PassesResult = {
    val input: PassInput = PassInput(model, options)
    runStandardPasses(input)
  }

  def runSymbols(input: PassInput, outputs: PassesOutput): SymbolsOutput = {
    runPass[SymbolsOutput](input, outputs, SymbolsPass(input, outputs))
  }

  def runResolution(input: PassInput, outputs: PassesOutput): ResolutionOutput = {
    runPass[ResolutionOutput](input, outputs, ResolutionPass(input, outputs))
  }

  def runValidation(input: PassInput, outputs: PassesOutput): ValidationOutput = {
    runPass[ValidationOutput](input, outputs, ValidationPass(input, outputs))
  }

  private def runPass[OUT <: PassOutput](input: PassInput, outputs: PassesOutput, pass: Pass): OUT = {
    Pass.runOnePass(input, outputs, pass).asInstanceOf[OUT]
  }

  private def runOnePass(
    in: PassInput,
    outs: PassesOutput,
    mkPass: => Pass,
    logger: Logger = SysLogger()
  ): PassOutput = {
    val pass: Pass = mkPass
    Timer.time[PassOutput](pass.name, in.commonOptions.showTimes, logger) {
      val parents: mutable.Stack[Definition] = mutable.Stack.empty
      pass.traverse(in.root, parents)
      pass.postProcess(in.root)
      val output = pass.result
      outs.outputIs(pass.name, output)
      output
    }
  }
}
