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

/** Information a pass must provide, basically its name
  */
trait PassInfo {
  def name: String
}

/** Information that a Pass must produce, currently just any messages it generated. Passes should derive their own
  * concrete PassOutput classes from this trait
  */
trait PassOutput {
  def messages: Messages.Messages
}

object PassOutput {
  def empty: PassOutput = new PassOutput { val messages: Messages.Messages = Messages.empty }
}

/** The input to a Pass in order to do its work. This consists of just the parsed model and the common options. Passes
  * cannot extend this.
  * @param root
  *   The result of the parsing run, consisting of the RootContainer from which all AST content can be reached
  * @param commonOptions
  *   THe common options that should be used to run the pass
  */
case class PassInput(
  root: RootContainer,
  commonOptions: CommonOptions = CommonOptions.empty
)
object PassInput {
  val empty: PassInput = PassInput(RootContainer.empty)
}

/** The output from running a set of Passes. This collects the PassOutput instances from each Pass run and provides
  * utility messages for getting that information.
  */
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

  def getNonStandardOutputs: Map[String, PassOutput] = {
    outputs.toMap.filterNot(Pass.standardPassNames.contains(_))
  }

  lazy val messages: Messages = getAllMessages

  lazy val symbols: SymbolsOutput =
    outputOf[SymbolsOutput](SymbolsPass.name).getOrElse(SymbolsOutput())
  lazy val resolution: ResolutionOutput =
    outputOf[ResolutionOutput](ResolutionPass.name).getOrElse(ResolutionOutput())
  lazy val validation: ValidationOutput =
    outputOf[ValidationOutput](ValidationPass.name).getOrElse(ValidationOutput())

  def refMap: ReferenceMap = resolution.refMap
  def usage: Usages = resolution.usage

}

/** The result of running a set of passes. This provides the input and ouputs of the run as well as any additional
  * messages (likely from an exception) This provides convenience methods for accessing the various output content
  *
  * @param input
  *   The input provided to the run of the passes
  * @param outputs
  *   The PassesOutput collected from teh run of the Passes
  * @param additionalMessages
  *   Any additional messages, likely from an exception or other unusual circumstance.
  */
case class PassesResult(
  input: PassInput = PassInput.empty,
  outputs: PassesOutput = PassesOutput(),
  additionalMessages: Messages = Messages.empty
) {
  def root: RootContainer = input.root
  def commonOptions: CommonOptions = input.commonOptions

  def outputOf[T <: PassOutput](passName: String): Option[T] = outputs.outputOf[T](passName)

  lazy val messages: Messages = outputs.messages ++ additionalMessages
  lazy val symbols: SymbolsOutput = outputs.symbols
  lazy val resolution: ResolutionOutput = outputs.resolution
  lazy val validation: ValidationOutput = outputs.validation

  def refMap: ReferenceMap = resolution.refMap
  def usage: Usages = resolution.usage

  def hasWarnings: Boolean = messages.hasWarnings
  def hasErrors: Boolean = messages.hasErrors
}

object PassesResult {
  val empty: PassesResult = PassesResult()
}

/** Abstract Pass definition.
  * @param in
  *   The input to the pass. This provides the data over which the pass is executed
  * @param out
  *   The output from previous runs of OTHER passes, which is a form of input to the pass, perhaps.
  */
abstract class Pass(@unused val in: PassInput, val out: PassesOutput) {

  /** THe name of the pass for inclusion in messages it produces. This must be implemented by the subclass
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

  /** The main implementation of the Pass. The AST is walked in a depth first manner calling this function for each
    * definition it encounters.
    *
    * @param definition
    *   The definition to be processed
    * @param parents
    *   The stack of definitions that are the parents of [[definition]]. This stack goes from immediate parent towards
    *   the root. The root is deepest in the stack.
    */
  protected def process(
    definition: Definition,
    parents: mutable.Stack[Definition]
  ): Unit

  /** A signal that the processing is complete and no more calls to [[process]] will be made. This also gives the Pass
    * subclass a chance to do post-processing as some computations can only be done after collecting data from the
    * entire AST
    *
    * @param root
    *   The [[in]].root field just as a convenience
    * @return
    *   Unit
    */
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

/** A Pass base class that allows the processing to be done based on containers, and calling these methods:
  *   - openContainer at the start of container's processing
  *   - processLeaf for any leaf nodes within the container
  *   - closeContainer after all the container's contents have been processed
  *
  * This kind of Pass allows the processing to follow the AST hierarchy so that container nodes can run before all their
  * content (openContainer) and also after all its content (closeContainer). This is necessary for passes that must
  * maintain the hierarchical structure of the AST model in their processing.
  *
  * @param input
  *   The PassInput to process
  */
abstract class HierarchyPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  // not required in this kind of pass, final override it as a result
  override final def process(definition: AST.Definition, parents: mutable.Stack[AST.Definition]): Unit = ()

  // Instead traverse will use these three methods:
  protected def openContainer(definition: Definition, parents: Seq[Definition]): Unit

  protected def processLeaf(definition: LeafDefinition, parents: Seq[Definition]): Unit

  protected def closeContainer(definition: Definition, parents: Seq[Definition]): Unit

  // Redefine traverse to make the three calls
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

/** An abstract PassOutput for use with passes that derive from CollectingPass. This just provides a standard field name
  * for the data that is collected, being `collected`.
  *
  * @param messages
  *   The required messages field from the PassOutput trait
  * @param collected
  *   The data that was collected from the CollectingPass's run
  * @tparam T
  *   The element type of the collected data
  */
abstract class CollectingPassOutput[T](
  messages: Messages = Messages.empty,
  collected: Seq[T] = Seq.empty[T]
) extends PassOutput

/** A Pass subclass that processes the AST exactly the same as the depth first search that the Pass class uses. The only
  * difference is that
  *
  * @param input
  *   The PassInput to process
  * @param outputs
  *   The outputs from previous pass runs in case they are needed as input to this CollectingPass
  * @tparam F
  *   The element type of the collected values
  */
abstract class CollectingPass[F](input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  // not required in this kind of pass, final override it
  override final def process(definition: AST.Definition, parents: mutable.Stack[AST.Definition]): Unit = ()

  /** The processing method called at each node, similar to [[Pass.process]] but modified to return an
    *
    * @param definition
    *   The definition from which an [[F]] value is collected.
    * @param parents
    *   The parents of the definition
    * @return
    *   One of the collected values, an [[F]]
    */
  protected def collect(definition: Definition, parents: mutable.Stack[AST.Definition]): Option[F]

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  protected var collectedValues: Seq[F] = Seq.empty[F]

  override def result: CollectingPassOutput[F]

  override protected def traverse(definition: Definition, parents: mutable.Stack[Definition]): Unit = {
    collect(definition, parents).foreach { collected =>
        collectedValues = collectedValues :+ collected
    }

    if definition.hasDefinitions then {
      parents.push(definition)
      definition.contents.foreach { item => traverse(item, parents) }
      parents.pop()
    }
  }
}

object Pass {

  /** A function type that creates a Pass instance */
  type PassCreator = (PassInput, PassesOutput) => Pass

  /** A sequence of PassCreator. This is used to run a set of passes */
  type PassesCreator = Seq[PassCreator]

  /** A PassesCreator of the standard passes that should be run on every AST pass. These generate the symbol table,
    * resolve path references, and validate the input. Only after these three have passed successfull should the model
    * be considered processable by other passes
    */
  val standardPasses: PassesCreator = Seq(
    { (input: PassInput, outputs: PassesOutput) => SymbolsPass(input, outputs) },
    { (input: PassInput, outputs: PassesOutput) => ResolutionPass(input, outputs) },
    { (input: PassInput, outputs: PassesOutput) => ValidationPass(input, outputs) }
  )

  /** The name of the standard passes */
  val standardPassNames: Seq[String] = Seq(SymbolsPass.name, ResolutionPass.name, ValidationPass.name)

  /** Run a set of passes against some input to obtain a result
    *
    * @param input
    *   The post-parsing input to the passes as a PassInput containing a RootContainer and CommonOptions
    * @param passes
    *   The list of Pass construction functions to use to instantiate the passes and run them. The type
    * @see
    *   [[PassesCreator]] type
    * @param logger
    *   The logger to which messages are logged
    * @return
    *   A PassesResult which provides the individual
    */
  def runThesePasses(
    input: PassInput,
    passes: PassesCreator = standardPasses,
    logger: Logger = SysLogger()
  ): PassesResult = {
    val outputs = PassesOutput()
    try {
      for pass <- passes yield {
        val aPass = pass(input, outputs)
        val output: PassOutput = runOnePass(input.root, input.commonOptions, aPass, logger)
        outputs.outputIs(aPass.name, output)
      }
      PassesResult(input, outputs, Messages.empty)
    } catch {
      case NonFatal(xcptn) =>
        val message = ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")
        val messages: Messages.Messages = List(Messages.severe(message, At.empty))
        PassesResult(input, outputs, messages)
    }
  }

  def runStandardPasses(
    input: PassInput
  ): PassesResult = {
    runThesePasses(input, standardPasses, SysLogger())
  }

  def runStandardPasses(
    model: RootContainer,
    options: CommonOptions
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

  def runPass[OUT <: PassOutput](input: PassInput, outputs: PassesOutput, pass: Pass): OUT = {
    val output: OUT = Pass.runOnePass(input.root, input.commonOptions,pass).asInstanceOf[OUT]
    outputs.outputIs(pass.name, output)
    output
  }

  private def runOnePass(
    root: RootContainer,
    commonOptions: CommonOptions,
    mkPass: => Pass,
    logger: Logger = SysLogger()
  ): PassOutput = {
    val pass: Pass = mkPass
    Timer.time[PassOutput](pass.name, commonOptions.showTimes, logger) {
      val parents: mutable.Stack[Definition] = mutable.Stack.empty
      pass.traverse(root, parents)
      pass.postProcess(root)
      pass.close()
      val output = pass.result
      output
    }
  }
}
