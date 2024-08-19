/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{AST, At, CommonOptions, Messages}
import com.ossuminc.riddl.utils.{Logger, SysLogger, Timer}
import com.ossuminc.riddl.passes.PassCreator
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, ResolutionOutput, ResolutionPass, Usages}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.symbols.Symbols.*

import com.ossuminc.riddl.passes.validate.{ValidationOutput, ValidationPass}
import com.ossuminc.riddl.utils.ExceptionUtils

import scala.annotation.unused
import scala.collection.mutable
import scala.util.control.NonFatal

/** A function type that creates a Pass instance */
type PassCreator = (PassInput, PassesOutput) => Pass

/** A sequence of PassCreator. This is used to run a set of passes */
type PassesCreator = Seq[PassCreator]

/** Base trait for options classes that are needed by passes */
trait PassOptions

object PassOptions {
  val empty: PassOptions = new PassOptions {}
}

/** Information a pass must provide, basically its name and a function to create the pass
  */
trait PassInfo[OPT <: PassOptions] {

  /** The name of the pass */
  def name: String

  /** A function to create an instance of the pass
    *
    * @param options
    *   The options the pass requires
    * @return
    */
  def creator(options: OPT): PassCreator
}

/** Information that a Pass must produce, currently just any messages it generated. Passes should derive their own
  * concrete PassOutput classes from this trait
  */
trait PassOutput {

  /** The new [[com.ossuminc.riddl.language.AST.Root]] of the model, if the [[Pass]] modified it in
    * [[Pass.preProcess()]]
    */
  def root: Root

  /** Any [[com.ossuminc.riddl.language.Messages.Messages]] that were generated by the pass */
  def messages: Messages.Messages
}

object PassOutput {
  def empty: PassOutput = new PassOutput {
    val root: Root = Root.empty
    val messages: Messages.Messages = Messages.empty
  }
}

/** The input to a Pass in order to do its work. This consists of just the parsed model and the common options. Passes
  * cannot extend this.
  * @param root
  *   The result of the parsing run, consisting of the RootContainer from which all AST content can be reached
  * @param commonOptions
  *   THe common options that should be used to run the pass
  */
case class PassInput(
  root: Root,
  commonOptions: CommonOptions = CommonOptions.empty
)
object PassInput {
  val empty: PassInput = PassInput(Root.empty)
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

/** The result of running a set of passes. This provides the input and outputs of the run as well as any additional
  * messages (likely from an exception). This provides convenience methods for accessing the various output content
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
  def root: Root = input.root
  def commonOptions: CommonOptions = input.commonOptions

  /** Get the output of one pass that ran
    *
    * @param passName
    *   The name of the pass. Use the Pass's companion object's [[PassInfo.name]] method to retrieve it.
    * @tparam T
    *   The type of the Pass's output which must derive from [[PassOutput]].
    * @return
    */
  def outputOf[T <: PassOutput](passName: String): Option[T] = outputs.outputOf[T](passName)
  def hasOutputOf(passName: String): Boolean = outputs.hasPassOutput(passName)

  lazy val messages: Messages = outputs.messages ++ additionalMessages
  lazy val symbols: SymbolsOutput = outputs.symbols
  lazy val resolution: ResolutionOutput = outputs.resolution
  lazy val validation: ValidationOutput = outputs.validation

  def refMap: ReferenceMap = resolution.refMap
  def usage: Usages = resolution.usage
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
  protected final def requires[OPT <: PassOptions](passInfo: PassInfo[OPT]): Unit = {
    require(out.hasPassOutput(passInfo.name), s"Required pass '${passInfo.name}' was not run prior to '$name'")
  }

  /** Pre-process the root before actually traversing. This is also a signal that the processing is about to start.
    * @param root
    *   A [[com.ossuminc.riddl.language.AST.Root]] node which encapsulates the whole model. This is the state of the
    *   Root as of the last pass.
    * @return
    *   Typically returns the same Root objects with changes in subsequent levels, but it entirely possibly to
    *   completely reorganize the hierarchy, including its root node.
    */
  def preProcess(root: Root): Root = root

  /** The main implementation of the Pass. The AST is walked in a depth first manner calling this function for each
    * definition it encounters.
    *
    * @param definition
    *   The definition to be processed
    * @param parents
    *   The stack of definitions that are the parents of [[com.ossuminc.riddl.language.AST.Definition]]. This stack goes
    *   from immediate parent towards the root. The root is deepest in the stack.
    */
  protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit

  /** A signal that the processing is complete and no more calls to `process` will be made. This also gives the Pass
    * subclass a chance to do post-processing as some computations can only be done after collecting data from the
    * entire AST
    *
    * @param root
    *   The root of the parsed model just as a convenience for post processing
    */
  def postProcess(root: Root): Unit = ()

  /** Generate the output of this Pass. This will only be called after all the calls to process have completed.
    * @param root
    *   The new [[com.ossuminc.riddl.language.AST.Root]] of the model that the pass computed
    * @return
    *   an instance of the output type
    */
  def result(root: Root): PassOutput

  /** Close any resources used so this can be used with AutoCloseable or Using.Manager
    */
  def close(): Unit = ()

  /** A method for the traversal of the AST hierarchy. While subclasses implement this differently, there is generally
    * no need to override in non-RIDDL code.
    *
    * @param definition
    *   The root (starting point) of the traveral
    * @param parents
    *   The parents of the definition
    */
  protected def traverse(definition: RiddlValue, parents: ParentStack): Unit = {
    definition match {
      case leaf: LeafDefinition =>
        process(leaf, parents)
      case root: Root =>
        parents.push(root)
        root.contents.foreach { value => traverse(value, parents) }
        parents.pop()
      case include: Include[?] =>
        // NOTE: no push/pop here because include is an unnamed container and does not participate in parent stack
        include.contents.foreach { value => traverse(value, parents) }
      case container: BranchDefinition[?] =>
        process(container, parents)
        parents.push(container)
        container.contents.foreach { value => traverse(value, parents) }
        parents.pop()
      case value: RiddlValue =>
        // NOTE: everything else is just a non-definition non-container
        process(value, parents)
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
  * @param outputs
  *   The outputs of previous passes in case this pass needs it
  */
abstract class HierarchyPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  /** not required in this kind of pass, final override it as a result
    *
    * @param definition
    *   The definition to be processed
    * @param parents
    *   The stack of definitions that are the parents of [[com.ossuminc.riddl.language.AST.Definition]]. This stack goes
    *   from immediate parent towards the root. The root is deepest in the stack.
    */
  override final def process(definition: RiddlValue, parents: ParentStack): Unit = ()

  /** Called by traverse when a new container is started Subclasses must implement this method.
    * @param definition
    *   The definition that was opened
    * @param parents
    *   The parents of the definition opened
    */
  protected def openContainer(definition: Definition, parents: Parents): Unit

  /** Called by traverse when a leaf node is encountered Subclasses must implement this method
    * @param definition
    *   The leaf definition that was found
    * @param parents
    *   THe parents of the leaf node
    */
  protected def processLeaf(definition: LeafDefinition, parents: Parents): Unit

  /** Process a non-definition, non-include, value
    *
    * @param value
    *   The value to be processed
    * @param parents
    *   The parent definitions of value
    */
  protected def processValue(value: RiddlValue, parents: Parents): Unit

  protected def openInclude(include: Include[?], parents: Parents): Unit = ()
  protected def closeInclude(include: Include[?], parents: Parents): Unit = ()

  /** Called by traverse after all leaf nodes of an opened node have been processed and the opened node is now being
    * closed. Subclasses must implement this method.
    * @param definition
    *   The opened node that now needs to be closed
    * @param parents
    *   THe parents of the node to be closed; should be the same as when it was opened
    */
  protected def closeContainer(definition: Definition, parents: Parents): Unit

  /** Redefine traverse to make the three calls
    *
    * @param definition
    *   The RiddlValue being considered
    * @param parents
    *   The definition parents of the value
    */
  override protected def traverse(definition: RiddlValue, parents: ParentStack): Unit = {
    definition match {
      case leaf: LeafDefinition =>
        processLeaf(leaf, parents.toSeq)
      case container: BranchDefinition[?] =>
        val def_parents = parents.toSeq
        openContainer(container, def_parents)
        if container.contents.nonEmpty then
          parents.push(container)
          container.contents.foreach {
            case leaf: LeafDefinition   => processLeaf(leaf, parents.toSeq)
            case definition: Definition => traverse(definition, parents)
            case include: Include[?]    => traverse(include, parents)
            case value: RiddlValue      => processValue(value, parents.toSeq)
          }
          parents.pop()
        end if
        closeContainer(container, def_parents)
      case include: Include[?] =>
        openInclude(include, parents.toSeq)
        include.contents.foreach { item => traverse(item, parents) }
        closeInclude(include, parents.toSeq)
      case value: RiddlValue =>
        processValue(value, parents.toSeq)
    }
  }
}

/** The Visitor definition for the [[VisitingPass]]. You must implement all the methods of this class and pass it to the
  * [[VisitingPass]]
  */
trait PassVisitor:
  // Open for each Container Definition
  def openType(typ: Type, parents: Parents): Unit
  def openDomain(domain: Domain, parents: Parents): Unit
  def openContext(context: Context, parents: Parents): Unit
  def openEntity(entity: Entity, parents: Parents): Unit
  def openAdaptor(adaptor: Adaptor, parents: Parents): Unit
  def openEpic(epic: Epic, parents: Parents): Unit
  def openUseCase(uc: UseCase, parents: Parents): Unit
  def openFunction(function: Function, parents: Parents): Unit
  def openSaga(saga: Saga, parents: Parents): Unit
  def openStreamlet(streamlet: Streamlet, parents: Parents): Unit
  def openRepository(repository: Repository, parents: Parents): Unit
  def openProjector(projector: Projector, parents: Parents): Unit
  def openHandler(handler: Handler, parents: Parents): Unit
  def openOnClause(onClause: OnClause, parents: Parents): Unit
  def openApplication(application: Application, parents: Parents): Unit
  def openGroup(group: Group, parents: Parents): Unit
  def openOutput(output: Output, parents: Parents): Unit
  def openInput(input: Input, parents: Parents): Unit
  def openInclude(include: Include[?], parents: Parents): Unit

  // Close for each type of container definition
  def closeType(typ: Type, parents: Parents): Unit
  def closeDomain(domain: Domain, parents: Parents): Unit
  def closeContext(context: Context, parents: Parents): Unit
  def closeEntity(entity: Entity, parents: Parents): Unit
  def closeAdaptor(adaptor: Adaptor, parents: Parents): Unit
  def closeEpic(epic: Epic, parents: Parents): Unit
  def closeUseCase(uc: UseCase, parents: Parents): Unit
  def closeFunction(function: Function, parents: Parents): Unit
  def closeSaga(saga: Saga, parents: Parents): Unit
  def closeStreamlet(streamlet: Streamlet, parents: Parents): Unit
  def closeRepository(repository: Repository, parents: Parents): Unit
  def closeProjector(projector: Projector, parents: Parents): Unit
  def closeHandler(handler: Handler, parents: Parents): Unit
  def closeOnClause(onClause: OnClause, parents: Parents): Unit
  def closeApplication(application: Application, parents: Parents): Unit
  def closeGroup(group: Group, parents: Parents): Unit
  def closeOutput(output: Output, parents: Parents): Unit
  def closeInput(input: Input, parents: Parents): Unit
  def closeInclude(include: Include[?], parents: Parents): Unit

  // LeafDefinitions
  def doField(field: Field): Unit
  def doMethod(method: Method): Unit
  def doTerm(term: Term): Unit
  def doAuthor(author: Author): Unit
  def doConstant(constant: Constant): Unit
  def doInvariant(invariant: Invariant): Unit
  def doSagaStep(sagaStep: SagaStep): Unit
  def doInlet(inlet: Inlet): Unit
  def doOutlet(outlet: Outlet): Unit
  def doConnector(connector: Connector): Unit
  def doUser(user: User): Unit
  def doSchema(schema: Schema): Unit
  def doState(state: State): Unit
  def doEnumerator(enumerator: Enumerator): Unit
  def doContainedGroup(containedGroup: ContainedGroup): Unit

  // Non Definition values
  def doComment(comment: Comment): Unit
  def doAuthorRef(reference: AuthorRef): Unit
  def doBriefDescription(brief: BriefDescription): Unit
  def doDescription(description: Description): Unit
  def doStatement(statement: Statements): Unit
  def doInteraction(interaction: UseCaseContents): Unit
  def doOptionValue(optionValue: OptionValue): Unit

/** An abstract Pass that uses the Visitor pattern (https://refactoring.guru/design-patterns/visitor)
  * @param input
  *   The PassInput to process
  * @param outputs
  *   The outputs of previous passes in case this pass needs it
  */
abstract class VisitingPass[VT <: PassVisitor](val input: PassInput, val outputs: PassesOutput, val visitor: VT)
    extends HierarchyPass(input, outputs):
  protected final def openContainer(container: Definition, parents: Parents): Unit =
    container match
      case typ: Type                => visitor.openType(typ, parents)
      case domain: Domain           => visitor.openDomain(domain, parents)
      case context: Context         => visitor.openContext(context, parents)
      case entity: Entity           => visitor.openEntity(entity, parents)
      case adaptor: Adaptor         => visitor.openAdaptor(adaptor, parents)
      case epic: Epic               => visitor.openEpic(epic, parents)
      case uc: UseCase              => visitor.openUseCase(uc, parents)
      case function: Function       => visitor.openFunction(function, parents)
      case saga: Saga               => visitor.openSaga(saga, parents)
      case streamlet: Streamlet     => visitor.openStreamlet(streamlet, parents)
      case repository: Repository   => visitor.openRepository(repository, parents)
      case projector: Projector     => visitor.openProjector(projector, parents)
      case handler: Handler         => visitor.openHandler(handler, parents)
      case onClause: OnClause       => visitor.openOnClause(onClause, parents)
      case application: Application => visitor.openApplication(application, parents)
      case group: Group             => visitor.openGroup(group, parents)
      case output: Output           => visitor.openOutput(output, parents)
      case input: Input             => visitor.openInput(input, parents)
      case _: Root                  => () // ignore
      case _: Enumerator            => () // not a container
      case _: Field | _: Method | _: Term | _: Author | _: Constant | _: Invariant | _: SagaStep | _: Inlet |
          _: Outlet | _: Connector | _: User | _: Schema | _: State | _: GenericInteraction | _: SelfInteraction |
          _: VagueInteraction | _: ContainedGroup | _: Definition =>
        () // not  containers
    end match
  end openContainer

  protected final def closeContainer(container: Definition, parents: Parents): Unit =
    container match
      case typ: Type                => visitor.closeType(typ, parents)
      case domain: Domain           => visitor.closeDomain(domain, parents)
      case context: Context         => visitor.closeContext(context, parents)
      case entity: Entity           => visitor.closeEntity(entity, parents)
      case adaptor: Adaptor         => visitor.closeAdaptor(adaptor, parents)
      case epic: Epic               => visitor.closeEpic(epic, parents)
      case useCase: UseCase         => visitor.closeUseCase(useCase, parents)
      case function: Function       => visitor.closeFunction(function, parents)
      case saga: Saga               => visitor.closeSaga(saga, parents)
      case streamlet: Streamlet     => visitor.closeStreamlet(streamlet, parents)
      case repository: Repository   => visitor.closeRepository(repository, parents)
      case projector: Projector     => visitor.closeProjector(projector, parents)
      case handler: Handler         => visitor.closeHandler(handler, parents)
      case onClause: OnClause       => visitor.closeOnClause(onClause, parents)
      case application: Application => visitor.closeApplication(application, parents)
      case group: Group             => visitor.closeGroup(group, parents)
      case output: Output           => visitor.closeOutput(output, parents)
      case input: Input             => visitor.closeInput(input, parents)
      case _: Root                  => () // ignore
      case _: Field | _: Method | _: Term | _: Author | _: Constant | _: Invariant | _: SagaStep | _: Inlet |
          _: Outlet | _: Connector | _: User | _: Schema | _: State | _: Enumerator | _: GenericInteraction |
          _: SelfInteraction | _: VagueInteraction | _: ContainedGroup | _: Definition =>
        () // not  containers
    end match
  end closeContainer

  protected final def processLeaf(definition: LeafDefinition, parents: Parents): Unit =
    definition match
      case field: Field                   => visitor.doField(field)
      case method: Method                 => visitor.doMethod(method)
      case enumerator: Enumerator         => visitor.doEnumerator(enumerator)
      case term: Term                     => visitor.doTerm(term)
      case author: Author                 => visitor.doAuthor(author)
      case constant: Constant             => visitor.doConstant(constant)
      case invariant: Invariant           => visitor.doInvariant(invariant)
      case sagaStep: SagaStep             => visitor.doSagaStep(sagaStep)
      case inlet: Inlet                   => visitor.doInlet(inlet)
      case outlet: Outlet                 => visitor.doOutlet(outlet)
      case connector: Connector           => visitor.doConnector(connector)
      case user: User                     => visitor.doUser(user)
      case schema: Schema                 => visitor.doSchema(schema)
      case state: State                   => visitor.doState(state)
      case containedGroup: ContainedGroup => visitor.doContainedGroup(containedGroup)
    end match
  end processLeaf

  protected final def processValue(value: RiddlValue, parents: Parents): Unit =
    value match
      case comment: Comment         => visitor.doComment(comment)
      case authorRef: AuthorRef     => visitor.doAuthorRef(authorRef)
      case brief: BriefDescription  => visitor.doBriefDescription(brief)
      case description: Description => visitor.doDescription(description)
      case statement: Statement     => visitor.doStatement(statement)
      case interaction: Interaction => visitor.doInteraction(interaction)
      case optionValue: OptionValue => visitor.doOptionValue(optionValue)
      case _                        => ()
    end match
  end processValue

  override protected final def openInclude(include: Include[?], parents: Parents): Unit =
    visitor.openInclude(include, parents)
  end openInclude

  override protected final def closeInclude(include: Include[?], parents: Parents): Unit =
    visitor.closeInclude(include, parents)
  end closeInclude
end VisitingPass

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
  root: Root = Root.empty,
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
  * @tparam ET
  *   The element type of the collected values
  */
abstract class CollectingPass[ET](input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  /** The method usually called for each definition that is to be processed but our implementation of traverse instead
    * calls collect so a value can be returned. This implementation is final because it is meant to be ignored.
    *
    * @param definition
    *   The definition to be processed
    * @param parents
    *   The stack of definitions that are the parents of [[com.ossuminc.riddl.language.AST.Definition]]. This stack goes
    *   from immediate parent towards the root. The root is deepest in the stack.
    */
  override final def process(definition: RiddlValue, parents: ParentStack): Unit = {
    val collected: Seq[ET] = collect(definition, parents)
    collectedValues ++= collected
  }

  protected val collectedValues: mutable.ArrayBuffer[ET] = mutable.ArrayBuffer.empty[ET]

  /** The processing method called at each node, similar to [[Pass.process]] but modified to return an sequence of the
    * collectable, [[ET]].
    *
    * @param definition
    *   The definition from which an [[ET]] value is collected.
    * @param parents
    *   The parents of the definition
    *
    * @return
    *   One of the collected values, an [[ET]]
    */
  protected def collect(definition: RiddlValue, parents: ParentStack): Seq[ET]

  override def result(root: Root): CollectingPassOutput[ET]
}

object Pass {

  /** A PassesCreator of the standard passes that should be run on every AST pass. These generate the symbol table,
    * resolve path references, and validate the input. Only after these three have passed successfull should the model
    * be considered processable by other passes
    */
  val standardPasses: PassesCreator = Seq(SymbolsPass.creator(), ResolutionPass.creator(), ValidationPass.creator())

  /** Run a set of passes against some input to obtain a result
    *
    * @param input
    *   The post-parsing input to the passes as a PassInput containing a RootContainer and CommonOptions
    * @param passes
    *   The list of Pass construction functions to use to instantiate the passes and run them. The type
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

  /** Run the standard passes with the input provided */
  def runStandardPasses(
    input: PassInput
  ): PassesResult = {
    runThesePasses(input, standardPasses, SysLogger())
  }

  /** Run the standard passes on a Root and CommonOptions */
  def runStandardPasses(
    model: Root,
    options: CommonOptions
  ): PassesResult = {
    val input: PassInput = PassInput(model, options)
    runStandardPasses(input)
  }

  /** Run the Symbols Pass */
  def runSymbols(input: PassInput, outputs: PassesOutput): SymbolsOutput = {
    runPass[SymbolsOutput](input, outputs, SymbolsPass(input, outputs))
  }

  /** Run the Resolution Pass */
  def runResolution(input: PassInput, outputs: PassesOutput): ResolutionOutput = {
    runPass[ResolutionOutput](input, outputs, ResolutionPass(input, outputs))
  }

  /** Run the Validation pass */
  def runValidation(input: PassInput, outputs: PassesOutput): ValidationOutput = {
    runPass[ValidationOutput](input, outputs, ValidationPass(input, outputs))
  }

  /** Run an arbitrary pass */
  def runPass[OUT <: PassOutput](input: PassInput, outputs: PassesOutput, pass: Pass): OUT = {
    val output: OUT = Pass.runOnePass(input.root, input.commonOptions, pass).asInstanceOf[OUT]
    outputs.outputIs(pass.name, output)
    output
  }

  private def runOnePass(
    root: Root,
    commonOptions: CommonOptions,
    mkPass: => Pass,
    logger: Logger = SysLogger()
  ): PassOutput = {
    val pass: Pass = mkPass
    Timer.time[PassOutput](pass.name, commonOptions.showTimes, logger) {
      val parents: ParentStack = ParentStack.empty
      val root1: Root = pass.preProcess(root)
      pass.traverse(root1, parents)
      pass.postProcess(root1)
      pass.close()
      pass.result(root1)
    }
  }
}
