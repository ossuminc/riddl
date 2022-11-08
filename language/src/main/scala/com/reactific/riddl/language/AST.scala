/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.ast.Location
import com.reactific.riddl.language.parsing.RiddlParserInput

import java.nio.file.Path

// scalastyle:off number.of.methods

/** Abstract Syntax Tree This object defines the model for processing RIDDL and
  * producing a raw AST from it. This raw AST has no referential integrity, it
  * just results from applying the parsing rules to the input. The RawAST models
  * produced from parsing are syntactically correct but have no semantic
  * validation. The Transformation passes convert RawAST model to AST model
  * which is referentially and semantically consistent (or the user gets an
  * error).
  */
object AST extends ast.Expressions with ast.Options with parsing.Terminals {

  /** Base trait of any definition that is in the content of an adaptor
    */
  sealed trait AdaptorDefinition extends Definition

  /** Base trait of any definition that is in the content of an Application
    */
  sealed trait ApplicationDefinition extends Definition

  /** Base trait of any definition that is in the content of a context
    */
  sealed trait ContextDefinition extends Definition

  /** Base trait of any definition that is in the content of a domain
    */
  sealed trait DomainDefinition extends Definition

  /** Base trait of any definition that is in the content of an entity.
    */
  sealed trait EntityDefinition extends Definition

  /** Base trait of definitions that are part of a Handler Definition */
  sealed trait HandlerDefinition extends Definition

  /** Base trait of any definition that occurs in the body of a plant
    */
  sealed trait PlantDefinition extends Definition

  /** Base trait of definitions defined in a processor
    */
  sealed trait ProcessorDefinition extends Definition

  /** Base trait of definitions defined in a repository */
  sealed trait RepositoryDefinition extends Definition

  /** Base trait of definitions defined at root scope */
  sealed trait RootDefinition extends Definition

  /** Base trait of definitions that are in the body of a Story definition */
  sealed trait StoryDefinition extends Definition

  sealed trait VitalDefinitionDefinition
      extends AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with DomainDefinition
      with EntityDefinition
      with FunctionDefinition
      with HandlerDefinition
      with PlantDefinition
      with ProcessorDefinition
      with ProjectionDefinition
      with RepositoryDefinition
      with SagaDefinition
      with StoryDefinition

  /** Base trait of definitions that can be used in a Story */
  sealed trait MessageTakingRef[+T <: Definition] extends Reference[T]

  /** A term definition for the glossary */
  case class Term(
    loc: Location,
    id: Identifier,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends LeafDefinition with VitalDefinitionDefinition {
    override def isEmpty: Boolean = description.isEmpty
    def format: String = ""
    final val kind: String = "Term"
  }

  /** Added to definitions that support a list of term definitions */
  trait WithTerms {
    def terms: Seq[Term]
    def hasTerms: Boolean = terms.nonEmpty
  }

  /** A [[RiddlValue]] to record an inclusion of a file while parsing.
    * @param loc
    *   The location of the include statement in the source
    * @param contents
    *   The Vital Definitions read from the file
    * @param path
    *   The [[java.nio.file.Path]] to the file included.
    */
  case class Include[T <: Definition](
    loc: Location = Location(RiddlParserInput.empty),
    contents: Seq[T] = Seq.empty[T],
    path: Option[Path] = None)
      extends Definition with VitalDefinitionDefinition with RootDefinition {

    def id: Identifier = Identifier.empty

    def brief: Option[LiteralString] = Option.empty[LiteralString]

    def description: Option[Description] = None

    override def isRootContainer: Boolean = true
    def format: String = ""
    final val kind: String = "Include"

  }

  /** Added to definitions that support includes */
  trait WithIncludes[T <: Definition] extends Container[T] {
    def includes: Seq[Include[T]]
    def contents: Seq[T] = { includes.flatMap(_.contents) }
  }

  /** A [[RiddlValue]] that holds the author's information
    *
    * @param loc
    *   The location of the author information
    * @param name
    *   The full name of the author
    * @param email
    *   The author's email address
    * @param organization
    *   The name of the organization the author is associated with
    * @param title
    *   The author's title within the organization
    * @param url
    *   A URL associated with the author
    */
  case class Author(
    loc: Location,
    id: Identifier,
    name: LiteralString,
    email: LiteralString,
    organization: Option[LiteralString] = None,
    title: Option[LiteralString] = None,
    url: Option[java.net.URL] = None,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends LeafDefinition with VitalDefinitionDefinition {
    override def isEmpty: Boolean = {
      name.isEmpty && email.isEmpty && organization.isEmpty && title.isEmpty
    }

    final val kind: String = "Author"
    def format: String = ""
  }

  case class AuthorRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Author] {
    override def format: String = s"${Keywords.author} ${pathId.format}"
    def kind: String = ""
  }

  trait WithAuthors extends Definition {
    def authors: Seq[AuthorRef]
    override def hasAuthors: Boolean = authors.nonEmpty
  }

  def findAuthors(
    defn: Definition,
    parents: Seq[Definition]
  ): Seq[AuthorRef] = {
    if (defn.hasAuthors) { defn.asInstanceOf[WithAuthors].authors }
    else {
      parents.find(d =>
        d.isInstanceOf[WithAuthors] && d.asInstanceOf[WithAuthors].hasAuthors
      ).map(_.asInstanceOf[WithAuthors].authors).getOrElse(Seq.empty[AuthorRef])
    }
  }

  sealed trait VitalDefinition[T <: OptionValue, CT <: Definition]
      extends Definition
      with WithOptions[T]
      with WithAuthors
      with WithIncludes[CT]
      with WithTerms {

    /** Compute the 'maturity' of a definition. Maturity is a score with no
      * maximum but with scoring rules that target 100 points per definition.
      * Maturity is broken down this way:
      *   - has a description - up to 50 points depending on # of non empty
      *     lines
      *   - has a brief description - 5 points
      *   - has options specified - 5 points
      *   - has terms defined -
      *   - has an author in or above the definition - 5 points \-
      *   - definition specific things: 0.65
      * @return
      */
    def maturity: Int = {
      var score = 0
      if (hasOptions) score += 5
      if (hasTerms) score += 5
      if (description.nonEmpty) {
        score += 5 + Math.max(description.get.lines.count(_.nonEmpty), 50)
      }
      if (brief.nonEmpty) score += 5
      if (includes.nonEmpty) score += 3
      score += authors.size
      score
    }

    override def isVital: Boolean = true
  }

  final val maxMaturity = 100

  /** Base trait of any definition that is a container and contains types
    */
  trait WithTypes extends Definition {
    def types: Seq[Type]

    override def hasTypes: Boolean = types.nonEmpty
  }

  /** The root of the containment hierarchy, corresponding roughly to a level
    * about a file.
    *
    * @param contents
    *   The sequence of domains contained by this root container
    */
  case class RootContainer(
    contents: Seq[Domain] = Nil,
    inputs: Seq[RiddlParserInput] = Nil)
      extends Definition {

    override def isRootContainer: Boolean = true

    def loc: Location = Location.empty

    override def id: Identifier = Identifier(loc, "Root")

    override def identify: String = "Root"

    override def identifyWithLoc: String = "Root"

    override def description: Option[Description] = None

    override def brief: Option[LiteralString] = None
    final val kind: String = "Root"
    def format: String = ""
  }

  object RootContainer {
    val empty: RootContainer =
      RootContainer(Seq.empty[Domain], Seq.empty[RiddlParserInput])
  }

  /** Base trait for the four kinds of message references */
  sealed trait MessageRef extends Reference[Type] {
    def messageKind: MessageKind

    override def format: String = s"${messageKind.kind} ${pathId.format}"
  }

  /** A Reference to a command type
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier to the event type
    */
  case class CommandRef(loc: Location, pathId: PathIdentifier)
      extends MessageRef {
    def messageKind: MessageKind = CommandKind
  }

  /** A Reference to an event type
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier to the event type
    */
  case class EventRef(loc: Location, pathId: PathIdentifier)
      extends MessageRef {
    def messageKind: MessageKind = EventKind
  }

  /** A reference to a query type
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier to the query type
    */
  case class QueryRef(loc: Location, pathId: PathIdentifier)
      extends MessageRef {
    def messageKind: MessageKind = QueryKind
  }

  /** A reference to a result type
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier to the result type
    */
  case class ResultRef(loc: Location, pathId: PathIdentifier)
      extends MessageRef {
    def messageKind: MessageKind = ResultKind
  }

  /** A type definition which associates an identifier with a type expression.
    *
    * @param loc
    *   The location of the type definition
    * @param id
    *   The name of the type being defined
    * @param typ
    *   The type expression of the type being defined
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the type.
    */
  case class Type(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends Definition
      with ApplicationDefinition
      with ContextDefinition
      with EntityDefinition
      with StateDefinition
      with FunctionDefinition
      with RepositoryDefinition
      with DomainDefinition {
    override def contents: Seq[TypeDefinition] = {
      typ match {
        case Aggregation(_, fields)      => fields
        case Enumeration(_, enumerators) => enumerators
        case MessageType(_, _, fields)   => fields
        case _                           => Seq.empty[TypeDefinition]
      }
    }
    final val kind: String = "Type"
    def format: String = ""
  }
  type Command = Type
  type Event = Type
  type Query = Type
  type Result = Type

  /** A reference to a type definition
    *
    * @param loc
    *   The location in the source where the reference to the type is made
    * @param id
    *   The path identifier of the reference type
    */
  case class TypeRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Type] {
    override def format: String = s"${Keywords.`type`} ${pathId.format}"
  }

  // /////////////////////////////////////////////////////////// Actions

  /** An action whose behavior is specified as a text string allowing extension
    * to arbitrary actions not otherwise handled by RIDDL's syntax.
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param what
    *   The action to take (emitted as pseudo-code)
    * @param description
    *   An optional description of the action
    */
  case class ArbitraryAction(
    loc: Location,
    what: LiteralString,
    description: Option[Description])
      extends Action {
    override def format: String = what.format
  }

  /** An action that is intended to generate a runtime error in the generated
    * application or otherwise indicate an error condition
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param message
    *   The error message to report
    * @param description
    *   An optional description of the action
    */
  case class ErrorAction(
    loc: Location,
    message: LiteralString,
    description: Option[Description])
      extends Action {
    override def format: String = s"severe \"${message.format}\""
  }

  /** An action whose behavior is to set the value of a state field to some
    * expression
    *
    * @param loc
    *   The location where the action occurs int he source
    * @param target
    *   The path identifier of the entity's state field that is to be set
    * @param value
    *   An expression for the value to set the field to
    * @param description
    *   An optional description of the action
    */
  case class SetAction(
    loc: Location,
    target: PathIdentifier,
    value: Expression,
    description: Option[Description] = None)
      extends Action {
    override def format: String = { s"set ${target.format} to ${value.format}" }
  }

  case class AppendAction(
    loc: Location,
    value: Expression,
    target: PathIdentifier,
    description: Option[Description] = None)
      extends Action {
    override def format: String = {
      s"append ${value.format} to ${target.format}"
    }
  }

  /** A helper class for publishing messages that represents the construction of
    * the message to be sent.
    *
    * @param msg
    *   A message reference that specifies the specific type of message to
    *   construct
    * @param args
    *   An argument list that should correspond to teh fields of the message
    */
  case class MessageConstructor(
    loc: Location,
    msg: MessageRef,
    args: ArgList = ArgList())
      extends RiddlNode {
    override def format: String = msg.format + {
      if (args.nonEmpty) { args.format }
      else { "()" }
    }
  }

  /** An action that returns a value from a function
    *
    * @param loc
    *   The location in the source of the publish action
    * @param value
    *   The value to be returned
    * @param description
    *   An optional description of the yield action
    */
  case class ReturnAction(
    loc: Location,
    value: Expression,
    description: Option[Description] = None)
      extends Action {
    override def format: String = s"return ${value.format}"
  }

  /** An action that places a message on an entity's event channel
    *
    * @param loc
    *   The location in the source of the publish action
    * @param msg
    *   The constructed message to be yielded
    * @param description
    *   An optional description of the yield action
    */
  case class YieldAction(
    loc: Location,
    msg: MessageConstructor,
    description: Option[Description] = None)
      extends Action {
    override def format: String = s"yield ${msg.format}"
  }

  /** An action that publishes a message to a pipe
    *
    * @param loc
    *   The location in the source of the publish action
    * @param msg
    *   The constructed message to be published
    * @param pipe
    *   The pipe onto which the message is published
    * @param description
    *   An optional description of the action
    */
  case class PublishAction(
    loc: Location,
    msg: MessageConstructor,
    pipe: PipeRef,
    description: Option[Description] = None)
      extends SagaStepAction {
    override def format: String = s"publish ${msg.format} to ${pipe.format}"
  }

  case class SubscribeAction(
    loc: Location,
    msgs: MessageConstructor,
    pipe: PipeRef,
    description: Option[Description] = None)
      extends Action {
    def format: String = ""
  }

  case class FunctionCallAction(
    loc: Location,
    function: PathIdentifier,
    arguments: ArgList,
    description: Option[Description] = None)
      extends SagaStepAction {
    override def format: String = s"call ${function.format}${arguments.format}"
  }

  /** An action that morphs the state of an entity to a new structure
    *
    * @param loc
    *   The location of the morph action in the source
    * @param entity
    *   The entity to be affected
    * @param state
    *   The reference to the new state structure
    * @param description
    *   An optional description of this action
    */
  case class MorphAction(
    loc: Location,
    entity: EntityRef,
    state: StateRef,
    description: Option[Description] = None)
      extends Action {
    override def format: String = s"morph ${entity.format} to ${state.format}"
  }

  /** An action that changes the behavior of an entity by making it use a new
    * handler for its messages; named for the "become" operation in Akka that
    * does the same for an actor.
    *
    * @param loc
    *   The location in the source of the become action
    * @param entity
    *   The entity whose behavior is to change
    * @param handler
    *   The reference to the new handler for the entity
    * @param description
    *   An optional description of this action
    */
  case class BecomeAction(
    loc: Location,
    entity: EntityRef,
    handler: HandlerRef,
    description: Option[Description] = None)
      extends Action {
    override def format: String =
      s"become ${entity.format} to ${handler.format}"
  }

  /** An action that tells a message to an entity. This is very analogous to the
    * tell operator in Akka.
    *
    * @param loc
    *   The location of the tell action
    * @param entity
    *   The entity to which the message is directed
    * @param msg
    *   A constructed message value to send to the entity, probably a command
    * @param description
    *   An optional description for this action
    */
  case class TellAction(
    loc: Location,
    msg: MessageConstructor,
    entity: MessageTakingRef[Definition],
    description: Option[Description] = None)
      extends SagaStepAction {
    override def format: String = s"tell ${msg.format} to ${entity.format}"
  }

  /** An action that asks a query to an entity. This is very analogous to the
    * ask operator in Akka.
    *
    * @param loc
    *   The location of the ask action
    * @param entity
    *   The entity to which the message is directed
    * @param msg
    *   A constructed message value to send to the entity, probably a query
    * @param description
    *   An optional description of the action.
    */
  case class AskAction(
    loc: Location,
    entity: EntityRef,
    msg: MessageConstructor,
    description: Option[Description] = None)
      extends SagaStepAction {
    override def format: String = s"ask ${entity.format} to ${msg.format}"
  }

  /** An action that tells a message to an entity. This is very analogous to the
    * tell operator in Akka.
    *
    * @param loc
    *   The location of the tell action
    * @param msg
    *   A constructed message value to send to the entity, probably a command
    * @param description
    *   An optional description for this action
    */
  case class ReplyAction(
    loc: Location,
    msg: MessageConstructor,
    description: Option[Description] = None)
      extends SagaStepAction {
    override def format: String = s"reply with ${msg.format}"
  }

  /** An action that is a set of other actions.
    *
    * @param loc
    *   The location of the compound action
    * @param actions
    *   The actions in the compound group of actions
    * @param description
    *   An optional description for the action
    */
  case class CompoundAction(
    loc: Location,
    actions: Seq[Action],
    description: Option[Description] = None)
      extends Action {
    override def format: String = actions.mkString("{", ",", "}")
  }

  // ////////////////////////////////////////////////////////// Gherkin

  /** A GherkinClause for the Given part of a Gherkin [[Example]]
    *
    * @param loc
    *   The location of the Given clause
    * @param scenario
    *   The strings that define the scenario
    */
  case class GivenClause(loc: Location, scenario: Seq[LiteralString])
      extends GherkinClause {
    def format: String = ""
  }

  /** A [[GherkinClause]] for the When part of a Gherkin [[Example]]
    *
    * @param loc
    *   The location of the When clause
    * @param condition
    *   The condition expression that defines the trigger for the [[Example]]
    */
  case class WhenClause(loc: Location, condition: Condition)
      extends GherkinClause {
    def format: String = ""
  }

  /** A [[GherkinClause]] for the Then part of a Gherkin [[Example]]. This part
    * specifies what should be done if the [[WhenClause]] evaluates to true.
    *
    * @param loc
    *   The location of the Then clause
    * @param action
    *   The action to be performed
    */
  case class ThenClause(loc: Location, action: Action) extends GherkinClause {
    def format: String = ""
  }

  /** A [[GherkinClause]] for the But part of a Gherkin [[Example]]. This part
    * specifies what should be done if the [[WhenClause]] evaluates to false.
    *
    * @param loc
    *   The location of the But clause
    * @param action
    *   The action to be performed
    */
  case class ButClause(loc: Location, action: Action) extends GherkinClause {
    def format: String = ""
  }

  /** A Gherkin example. Examples have names, [[id]], and a sequence of each of
    * the four kinds of Gherkin clauses: [[GivenClause]], [[WhenClause]],
    * [[ThenClause]], [[ButClause]]
    *
    * @see
    *   [[https://cucumber.io/docs/gherkin/reference/ The Gherkin Reference]]
    * @param loc
    *   The location of the start of the example
    * @param id
    *   The name of the example
    * @param givens
    *   The list of Given/And statements
    * @param whens
    *   The list of When/And statements
    * @param thens
    *   The list of Then/And statements
    * @param buts
    *   The List of But/And statements
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the example
    */
  case class Example(
    loc: Location,
    id: Identifier,
    givens: Seq[GivenClause] = Seq.empty[GivenClause],
    whens: Seq[WhenClause] = Seq.empty[WhenClause],
    thens: Seq[ThenClause] = Seq.empty[ThenClause],
    buts: Seq[ButClause] = Seq.empty[ButClause],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = Option.empty[Description])
      extends LeafDefinition
      with ProcessorDefinition
      with FunctionDefinition
      with StoryDefinition {
    final val kind: String = "Example"
    def format: String = ""
    override def isEmpty: Boolean = givens.isEmpty && whens.isEmpty &&
      thens.isEmpty && buts.isEmpty
  }

  // ////////////////////////////////////////////////////////// Entities

  /** A reference to an entity
    *
    * @param loc
    *   The location of the entity reference
    * @param id
    *   The path identifier of the referenced entity.
    */
  case class EntityRef(loc: Location, pathId: PathIdentifier)
      extends MessageTakingRef[Entity] {
    override def format: String = s"${Keywords.entity} ${pathId.format}"
  }

  /** A reference to a function.
    *
    * @param loc
    *   The location of the function reference.
    * @param id
    *   The path identifier of the referenced function.
    */
  case class FunctionRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Function] {
    override def format: String = s"${Keywords.function} ${pathId.format}"
  }

  /** A function definition which can be part of a bounded context or an entity.
    *
    * @param loc
    *   The location of the function definition
    * @param id
    *   The identifier that names the function
    * @param input
    *   An optional type expression that names and types the fields of the input
    *   of the function
    * @param output
    *   An optional type expression that names and types the fields of the
    *   output of the function
    * @param examples
    *   The set of examples that define the behavior of the function.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the function.
    */
  case class Function(
    loc: Location,
    id: Identifier,
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    types: Seq[Type] = Seq.empty[Type],
    functions: Seq[Function] = Seq.empty[Function],
    examples: Seq[Example] = Seq.empty[Example],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[FunctionDefinition]] = Seq
      .empty[Include[FunctionDefinition]],
    options: Seq[FunctionOption] = Seq.empty[FunctionOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[FunctionOption, FunctionDefinition]
      with WithTypes
      with SagaDefinition
      with EntityDefinition
      with ContextDefinition
      with FunctionDefinition {
    override lazy val contents: Seq[FunctionDefinition] = {
      super.contents ++ input.map(_.fields).getOrElse(Seq.empty[Field]) ++
        output.map(_.fields).getOrElse(Seq.empty[Field]) ++ types ++
        functions ++ examples
    }

    override def isEmpty: Boolean = examples.isEmpty && input.isEmpty &&
      output.isEmpty

    final val kind: String = "Function"

    override def maturity: Int = {
      var score = super.maturity
      if (input.nonEmpty) score += 2
      if (output.nonEmpty) score += 3
      if (types.nonEmpty) score += Math.max(types.count(_.nonEmpty), 13)
      if (examples.nonEmpty) score += Math.max(types.count(_.nonEmpty), 25)
      if (functions.nonEmpty) score += Math.max(functions.count(_.nonEmpty), 12)
      Math.max(score, maxMaturity)
    }
  }

  /** An invariant expression that can be used in the definition of an entity.
    * Invariants provide conditional expressions that must be true at all times
    * in the lifecycle of an entity.
    *
    * @param loc
    *   The location of the invariant definition
    * @param id
    *   The name of the invariant
    * @param expression
    *   The conditional expression that must always be true.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the invariant.
    */
  case class Invariant(
    loc: Location,
    id: Identifier,
    expression: Option[Condition] = None,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends LeafDefinition
      with EntityDefinition
      with ProjectionDefinition
      with StateDefinition {
    override def isEmpty: Boolean = expression.isEmpty
    def format: String = ""
    final val kind: String = "Invariant"
  }

  trait OnClause extends HandlerDefinition {
    def examples: Seq[Example]
  }

  case class OnOtherClause(
    loc: Location,
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends OnClause {
    def id: Identifier = Identifier(loc, s"Other")
    override def isEmpty: Boolean = examples.isEmpty
    override def kind: String = "On Other"
    override def contents: Seq[Example] = examples
    override def format: String = ""
  }

  /** Defines the actions to be taken when a particular message is received by
    * an entity. [[OnMessageClause]]s are used in the definition of a
    * [[Handler]] with one for each kind of message that handler deals with.
    *
    * @param loc
    *   The location of the "on" clause
    * @param msg
    *   A reference to the message type that is handled
    * @param from
    *   Optional message generating
    * @param examples
    *   A set of examples that define the behavior when the [[msg]] is received.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnMessageClause(
    loc: Location,
    msg: MessageRef,
    from: Option[Reference[Definition]],
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends OnClause {
    def id: Identifier = Identifier(msg.loc, s"On ${msg.format}")
    override def isEmpty: Boolean = examples.isEmpty
    override def contents: Seq[Example] = examples
    def format: String = ""
    final val kind: String = "OnMessageClause"
  }

  /** A named handler of messages (commands, events, queries) that bundles
    * together a set of [[OnMessageClause]] definitions and by doing so defines
    * the behavior of an entity. Note that entities may define multiple handlers
    * and switch between them to change how it responds to messages over time or
    * in response to changing conditions
    *
    * @param loc
    *   The location of the handler definition
    * @param id
    *   The name of the handler.
    * @param clauses
    *   The set of [[OnMessageClause]] definitions that define how the entity
    *   responds to received messages.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the handler
    */
  case class Handler(
    loc: Location,
    id: Identifier,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[HandlerDefinition]] = Seq
      .empty[Include[HandlerDefinition]],
    options: Seq[HandlerOption] = Seq.empty[HandlerOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[HandlerOption, HandlerDefinition]
      with AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with EntityDefinition
      with StateDefinition
      with RepositoryDefinition
      with ProcessorDefinition
      with ProjectionDefinition {
    override def isEmpty: Boolean = super.isEmpty && clauses.isEmpty
    override def contents: Seq[HandlerDefinition] = super.contents ++ clauses ++
      terms
    final val kind: String = "Handler"

    override def maturity: Int = {
      var score = super.maturity
      if (clauses.nonEmpty) score +=
        Math.max(clauses.count(_.nonEmpty), maxMaturity)
      Math.max(score, maxMaturity)
    }

  }

  /** A reference to a Handler
    *
    * @param loc
    *   The location of the handler reference
    * @param id
    *   The path identifier of the referenced handler
    */
  case class HandlerRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Handler] {
    override def format: String = s"${Keywords.handler} ${pathId.format}"
  }

  /** Represents the state of an entity. The [[MorphAction]] can cause the state
    * definition of an entity to change.
    *
    * @param loc
    *   The location of the state definition
    * @param id
    *   The name of the state definition
    * @param aggregation
    *   The aggregation that provides the field name and type expression
    *   associations
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the state.
    */
  case class State(
    loc: Location,
    id: Identifier,
    aggregation: Aggregation,
    types: Seq[Type] = Seq.empty[Type],
    handlers: Seq[Handler] = Seq.empty[Handler],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends EntityDefinition {

    override def contents: Seq[StateDefinition] = aggregation.fields ++ types ++
      handlers
    def format: String = ""
    final val kind: String = "State"
  }

  /** A reference to an entity's state definition
    *
    * @param loc
    *   The location of the state reference
    * @param id
    *   The path identifier of the referenced state definition
    */
  case class StateRef(loc: Location, pathId: PathIdentifier)
      extends Reference[State] {
    override def format: String = s"${Keywords.state} ${pathId.format}"
  }

  /** Definition of an Entity
    *
    * @param options
    *   The options for the entity
    * @param loc
    *   The location in the input
    * @param id
    *   The name of the entity
    * @param states
    *   The state values of the entity
    * @param types
    *   Type definitions useful internally to the entity definition
    * @param handlers
    *   A set of event handlers
    * @param functions
    *   Utility functions defined for the entity
    * @param invariants
    *   Invariant properties of the entity
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   Optional description of the entity
    */
  case class Entity(
    loc: Location,
    id: Identifier,
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    states: Seq[State] = Seq.empty[State],
    types: Seq[Type] = Seq.empty[Type],
    handlers: Seq[Handler] = Seq.empty[Handler],
    functions: Seq[Function] = Seq.empty[Function],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    includes: Seq[Include[EntityDefinition]] = Seq
      .empty[Include[EntityDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[EntityOption, EntityDefinition]
      with ContextDefinition
      with WithTypes {

    override lazy val contents: Seq[EntityDefinition] = {
      super.contents ++ states ++ types ++ handlers ++ functions ++
        invariants ++ terms
    }

    final val kind: String = "Entity"

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty

    override def maturity: Int = {
      var score = super.maturity
      if (states.nonEmpty) score += Math.max(states.count(_.nonEmpty), 10)
      if (types.nonEmpty) score += Math.max(types.count(_.nonEmpty), 25)
      if (handlers.nonEmpty) score += 1
      if (invariants.nonEmpty) score +=
        Math.max(invariants.count(_.nonEmpty), 10)
      if (functions.nonEmpty) score += Math.max(functions.count(_.nonEmpty), 5)
      Math.max(score, maxMaturity)
    }
  }

  sealed trait AdaptorDirection extends RiddlValue

  case class InboundAdaptor(loc: Location) extends AdaptorDirection {
    def format: String = "from"
  }

  case class OutboundAdaptor(loc: Location) extends AdaptorDirection {
    def format: String = "to"
  }

  /** Definition of an Adaptor. Adaptors are defined in Contexts to convert
    * messages from another bounded context. Adaptors translate incoming
    * messages into corresponding messages using the ubiquitous language of the
    * defining bounded context. There should be one Adapter for each external
    * Context
    *
    * @param loc
    *   Location in the parsing input
    * @param id
    *   Name of the adaptor
    * @param direction
    * @param context
    *   A reference to the bounded context from which messages are adapted
    * @param handlers
    *   A set of [[Handler]]s that indicate what to do when messages occur.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   Optional description of the adaptor.
    */
  case class Adaptor(
    loc: Location,
    id: Identifier,
    direction: AdaptorDirection,
    context: ContextRef,
    handlers: Seq[Handler] = Seq.empty[Handler],
    includes: Seq[Include[AdaptorDefinition]] = Seq
      .empty[Include[AdaptorDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[AdaptorOption] = Seq.empty[AdaptorOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[AdaptorOption, AdaptorDefinition]
      with ContextDefinition {
    override lazy val contents: Seq[AdaptorDefinition] = {
      super.contents ++ handlers ++ terms
    }
    final val kind: String = "Adaptor"

    override def maturity: Int = {
      var score = super.maturity
      if (handlers.nonEmpty) score +=
        Math.max(handlers.count(_.nonEmpty), maxMaturity)
      Math.max(score, maxMaturity)
    }
  }

  case class AdaptorRef(
    loc: Location,
    pathId: PathIdentifier)
      extends MessageTakingRef[Adaptor] {
    override def format: String = s"${Keywords.adaptor} ${pathId.format}"
  }

  /** A RIDDL repository is an abstraction for anything that can retain
    * information(e.g. messages for retrieval at a later time. This might be a
    * relational database, NoSQL database, data lake, API, or something not yet
    * invented. There is no specific technology implied other than the retention
    * and retrieval of information. You should think of repositories more like a
    * message-oriented version of the Java Repository Pattern than any
    * particular kind ofdatabase.
    * @see
    *   https://java-design-patterns.com/patterns/repository/#explanation
    *
    * @param loc
    *   Location in the source of the Repository
    * @param id
    *   The unique identifier for this Repository
    * @param types
    *   The types, typically messages, that the Repository uses
    * @param handlers
    *   The handler for specifying how messages should be handled by the
    *   repository
    * @param authors
    *   The author(s) who wrote this repository specification.
    * @param includes
    *   Included files
    * @param options
    *   Options that can be used by the translators
    * @param terms
    *   Definitions of terms about this repository
    * @param brief
    *   A brief description of this repository
    * @param description
    *   A detailed description of this repository
    */
  case class Repository(
    loc: Location,
    id: Identifier,
    types: Seq[Type] = Seq.empty[Type],
    handlers: Seq[Handler] = Seq.empty[Handler],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[RepositoryDefinition]] = Seq
      .empty[Include[RepositoryDefinition]],
    options: Seq[RepositoryOption] = Seq.empty[RepositoryOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[RepositoryOption, RepositoryDefinition]
      with ContextDefinition {
    override def kind: String = "Repository"
    override lazy val contents: Seq[RepositoryDefinition] = {
      super.contents ++ types ++ handlers ++ terms
    }
  }

  /** Projections get their name from Euclidean Geometry but are probably more
    * analogous to a relational database view. The concept is very simple in
    * RIDDL: projections gather data from entities and other sources, transform
    * that data into a specific record type, and support querying that data
    * arbitrarily.
    * @see
    *   https://en.wikipedia.org/wiki/View_(SQL)).
    * @see
    *   https://en.wikipedia.org/wiki/Projection_(mathematics)
    *
    * @param loc
    *   Location in the source of the Projection
    * @param id
    *   The unique identifier for this Projection
    * @param aggregation
    *   The projected type
    * @param handlers
    *   Specifies how to handle
    * @param options
    *   Options that can be used by the translators
    * @param terms
    *   Definitions of terms about this Projection
    * @param brief
    *   A brief description of this Projection
    * @param description
    *   A detailed description of this Projection
    */
  case class Projection(
    loc: Location,
    id: Identifier,
    aggregation: Aggregation,
    handlers: Seq[Handler] = Seq.empty[Handler],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[ProjectionDefinition]] = Seq
      .empty[Include[ProjectionDefinition]],
    options: Seq[ProjectionOption] = Seq.empty[ProjectionOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[ProjectionOption, ProjectionDefinition]
      with ContextDefinition {
    override lazy val contents: Seq[ProjectionDefinition] = {
      super.contents ++ aggregation.fields ++ terms
    }
    final val kind: String = "Projection"

    override def maturity: Int = {
      var score = super.maturity
      if (aggregation.fields.nonEmpty) score +=
        Math.max(aggregation.fields.count(_.nonEmpty), maxMaturity)
      Math.max(score, maxMaturity)
    }
  }

  /** A reference to an context's projection definition
    *
    * @param loc
    *   The location of the state reference
    * @param id
    *   The path identifier of the referenced projection definition
    */
  case class ProjectionRef(loc: Location, pathId: PathIdentifier)
      extends MessageTakingRef[Projection] {
    override def format: String = s"${Keywords.projection} ${pathId.format}"
  }

  /** A bounded context definition. Bounded contexts provide a definitional
    * boundary on the language used to describe some aspect of a system. They
    * imply a tightly integrated ecosystem of one or more microservices that
    * share a common purpose. Context can be used to house entities, read side
    * projections, sagas, adaptations to other contexts, apis, and etc.
    *
    * @param loc
    *   The location of the bounded context definition
    * @param id
    *   The name of the context
    * @param options
    *   The options for the context
    * @param types
    *   Types defined for the scope of this context
    * @param entities
    *   Entities defined for the scope of this context
    * @param adaptors
    *   Adaptors to messages from other contexts
    * @param sagas
    *   Sagas with all-or-none semantics across various entities
    * @param functions
    *   Features specified for the context
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the context
    */
  case class Context(
    loc: Location,
    id: Identifier,
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    types: Seq[Type] = Seq.empty[Type],
    entities: Seq[Entity] = Seq.empty[Entity],
    adaptors: Seq[Adaptor] = Seq.empty[Adaptor],
    sagas: Seq[Saga] = Seq.empty[Saga],
    processors: Seq[Processor] = Seq.empty[Processor],
    functions: Seq[Function] = Seq.empty[Function],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[ContextDefinition]] = Seq
      .empty[Include[ContextDefinition]],
    handlers: Seq[Handler] = Seq.empty[Handler],
    projections: Seq[Projection] = Seq.empty[Projection],
    repositories: Seq[Repository] = Seq.empty[Repository],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[ContextOption, ContextDefinition]
      with DomainDefinition
      with WithTypes {
    override lazy val contents: Seq[ContextDefinition] = super.contents ++
      types ++ entities ++ adaptors ++ sagas ++ functions ++ terms ++
      projections ++ handlers
    final val kind: String = "Context"

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty

    override def maturity: Int = {
      var score = super.maturity
      if (types.nonEmpty) score += Math.max(types.count(_.nonEmpty), 10)
      if (adaptors.nonEmpty) score += Math.max(types.count(_.nonEmpty), 5)
      if (sagas.nonEmpty) score += Math.max(types.count(_.nonEmpty), 5)
      if (processors.nonEmpty) score += Math.max(types.count(_.nonEmpty), 10)
      if (functions.nonEmpty) score += Math.max(types.count(_.nonEmpty), 10)
      if (handlers.nonEmpty) score += 10
      if (projections.nonEmpty) score += Math.max(types.count(_.nonEmpty), 10)
      Math.max(score, maxMaturity)
    }
  }

  /** A reference to a bounded context
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier for the referenced context
    */
  case class ContextRef(loc: Location, pathId: PathIdentifier)
      extends MessageTakingRef[Context] {
    override def format: String = s"context ${pathId.format}"
  }

  /** Definition of a pipe for data streaming purposes. Pipes are conduits
    * through which data of a particular type flows.
    *
    * @param loc
    *   The location of the pipe definition
    * @param id
    *   The name of the pipe
    * @param transmitType
    *   The type of data transmitted.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the pipe.
    */
  case class Pipe(
    loc: Location,
    id: Identifier,
    transmitType: Option[TypeRef],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends LeafDefinition with PlantDefinition with ContextDefinition {
    override def isEmpty: Boolean = transmitType.isEmpty
    def format: String = ""
    final val kind: String = "Pipe"
  }

  /** Base trait of an Inlet or Outlet definition
    */
  trait Streamlet extends LeafDefinition with ProcessorDefinition

  /** A streamlet that supports input of data of a particular type.
    *
    * @param loc
    *   The location of the Inlet definition
    * @param id
    *   The name of the inlet
    * @param type_
    *   The type of the data that is received from the inlet
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the Inlet
    */
  case class Inlet(
    loc: Location,
    id: Identifier,
    type_ : TypeRef,
    entity: Option[EntityRef] = None,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends Streamlet with AlwaysEmpty {
    def format: String = ""
    final val kind: String = "Inlet"
  }

  /** A streamlet that supports output of data of a particular type.
    *
    * @param loc
    *   The location of the outlet definition
    * @param id
    *   The name of the outlet
    * @param type_
    *   The type expression for the kind of data put out
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the Outlet.
    */
  case class Outlet(
    loc: Location,
    id: Identifier,
    type_ : TypeRef,
    entity: Option[EntityRef] = None,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends Streamlet with AlwaysEmpty {
    def format: String = ""
    final val kind: String = "Outlet"
  }

  sealed trait ProcessorShape extends RiddlValue {
    def keyword: String
  }

  case class Source(loc: Location) extends ProcessorShape {
    def format: String = ""
    def keyword: String = Keywords.source
  }

  case class Sink(loc: Location) extends ProcessorShape {
    def format: String = ""
    def keyword: String = Keywords.sink
  }

  case class Flow(loc: Location) extends ProcessorShape {
    def format: String = ""
    def keyword: String = Keywords.flow
  }

  case class Merge(loc: Location) extends ProcessorShape {
    def format: String = ""
    def keyword: String = Keywords.merge
  }

  case class Split(loc: Location) extends ProcessorShape {
    def format: String = ""
    def keyword: String = Keywords.split
  }

  case class Multi(loc: Location) extends ProcessorShape {
    def format: String = ""
    def keyword: String = Keywords.multi
  }

  case class Void(loc: Location) extends ProcessorShape {
    def format: String = ""
    override def keyword: String = Keywords.void
  }

  /** A computing element for processing data from [[Inlet]]s to [[Outlet]]s. A
    * processor's processing is specified by Gherkin [[Example]]s
    *
    * @param loc
    *   The location of the Processor definition
    * @param id
    *   The name of the processor
    * @param shape
    *   The shape of the processor's inputs and outputs
    * @param inlets
    *   The list of inlets that provide the data the processor needs
    * @param outlets
    *   The list of outlets that the processor produces
    * @param examples
    *   A set of examples that define the data processing
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the processor
    */
  case class Processor(
    loc: Location,
    id: Identifier,
    shape: ProcessorShape,
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    handlers: Seq[Handler] = Seq.empty[Handler],
    includes: Seq[Include[ProcessorDefinition]] = Seq
      .empty[Include[ProcessorDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[ProcessorOption] = Seq.empty[ProcessorOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[ProcessorOption, ProcessorDefinition]
      with PlantDefinition
      with ContextDefinition {
    override def contents: Seq[ProcessorDefinition] = super.contents ++
      inlets ++ outlets ++ handlers ++ terms
    final val kind: String = shape.getClass.getSimpleName

    override def maturity: Int = {
      var score = super.maturity
      if (inlets.nonEmpty) score += Math.max(inlets.count(_.nonEmpty), 5)
      if (outlets.nonEmpty) score += Math.max(outlets.count(_.nonEmpty), 5)
      if (handlers.nonEmpty) score += Math.max(handlers.count(_.nonEmpty), 40)
      Math.max(score, maxMaturity)
    }

    shape match {
      case Source(_) => require(
          isEmpty || (outlets.size == 1 && inlets.isEmpty),
          s"Invalid Source Streamlet ins: ${outlets.size} == 1, ${inlets.size} == 0"
        )
      case Sink(_) => require(
          isEmpty || (outlets.isEmpty && inlets.size == 1),
          "Invalid Sink Streamlet"
        )
      case Flow(_) => require(
          isEmpty || (outlets.size == 1 && inlets.size == 1),
          "Invalid Flow Streamlet"
        )
      case Merge(_) => require(
          isEmpty || (outlets.size == 1 && inlets.size >= 2),
          "Invalid Merge Streamlet"
        )
      case Split(_) => require(
          isEmpty || (outlets.size >= 2 && inlets.size == 1),
          "Invalid Split Streamlet"
        )
      case Multi(_) => require(
          isEmpty || (outlets.size >= 2 && inlets.size >= 2),
          "Invalid Multi Streamlet"
        )
      case Void(_) => require(
          isEmpty || (outlets.isEmpty && inlets.isEmpty),
          "Invalid Void Stream"
        )
    }

  }

  /** A reference to an context's projection definition
    *
    * @param loc
    *   The location of the state reference
    * @param id
    *   The path identifier of the referenced projection definition
    */
  case class ProcessorRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Processor] {
    override def format: String = s"${Keywords.processor} ${pathId.format}"
  }

  /** A reference to a pipe
    *
    * @param loc
    *   The location of the pipe reference
    * @param id
    *   The path identifier for the referenced pipe.
    */
  case class PipeRef(loc: Location, pathId: PathIdentifier)
      extends MessageTakingRef[Pipe] {
    override def format: String = s"${Keywords.pipe} ${pathId.format}"
  }

  /** Sealed base trait of references to [[Inlet]]s or [[Outlet]]s
    *
    * @tparam T
    *   The type of definition to which the references refers.
    */
  sealed trait StreamletRef[+T <: Definition] extends Reference[T]

  /** A reference to an [[Inlet]]
    *
    * @param loc
    *   The location of the inlet reference
    * @param id
    *   The path identifier of the referenced [[Inlet]]
    */
  case class InletRef(loc: Location, pathId: PathIdentifier)
      extends StreamletRef[Inlet] {
    override def format: String = s"${Keywords.inlet} ${pathId.format}"
  }

  /** A reference to an [[Outlet]]
    *
    * @param loc
    *   The location of the outlet reference
    * @param id
    *   The path identifier of the referenced [[Outlet]]
    */
  case class OutletRef(loc: Location, pathId: PathIdentifier)
      extends StreamletRef[Outlet] {
    override def format: String = s"${Keywords.outlet} ${pathId.format}"
  }

  /** Sealed base trait for both kinds of Joint definitions
    */
  sealed trait Joint
      extends LeafDefinition
      with AlwaysEmpty
      with PlantDefinition
      with ContextDefinition

  /** A joint that connects an [[Processor]]'s [[Inlet]] to a [[Pipe]].
    *
    * @param loc
    *   The location of the InletJoint
    * @param id
    *   The name of the inlet joint
    * @param inletRef
    *   A reference to the inlet being connected
    * @param pipe
    *   A reference to the pipe being connected
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the joint
    */
  case class InletJoint(
    loc: Location,
    id: Identifier,
    inletRef: InletRef,
    pipe: PipeRef,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends Joint {
    def format: String = ""
    final val kind: String = "Inlet Joint"
  }

  /** A joint that connects a [[Processor]]'s [[Outlet]] to a [[Pipe]].
    *
    * @param loc
    *   The location of the OutletJoint
    * @param id
    *   The name of the OutletJoint
    * @param outletRef
    *   A reference to the outlet being connected
    * @param pipe
    *   A reference to the pipe being connected
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the OutletJoint
    */
  case class OutletJoint(
    loc: Location,
    id: Identifier,
    outletRef: OutletRef,
    pipe: PipeRef,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends Joint {
    def format: String = ""
    final val kind: String = "Outlet Joint"
  }

  /** The definition of a plant which brings pipes, processors and joints
    * together into a closed system of data processing.
    *
    * @param loc
    *   The location of the plant definition
    * @param id
    *   The name of the plant
    * @param pipes
    *   The set of pipes involved in the plant
    * @param processors
    *   The set of processors involved in the plant.
    * @param inJoints
    *   The InletJoints connecting pipes and processors
    * @param outJoints
    *   The OutletJoints connecting pipes and processors
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the plant
    */
  case class Plant(
    loc: Location,
    id: Identifier,
    pipes: Seq[Pipe] = Seq.empty[Pipe],
    processors: Seq[Processor] = Seq.empty[Processor],
    inJoints: Seq[InletJoint] = Seq.empty[InletJoint],
    outJoints: Seq[OutletJoint] = Seq.empty[OutletJoint],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[PlantDefinition]] = Seq
      .empty[Include[PlantDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[PlantOption] = Seq.empty[PlantOption],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[PlantOption, PlantDefinition]
      with DomainDefinition {
    override lazy val contents: Seq[PlantDefinition] = super.contents ++
      pipes ++ processors ++ inJoints ++ outJoints ++ terms
    final val kind: String = "Plant"

    override def maturity: Int = {
      var score = super.maturity
      if (pipes.nonEmpty) score += Math.max(pipes.count(_.nonEmpty), 10)
      if (processors.nonEmpty) score +=
        Math.max(processors.count(_.nonEmpty), 20)
      if (inJoints.nonEmpty) score += Math.max(inJoints.count(_.nonEmpty), 10)
      if (outJoints.nonEmpty) score += Math.max(outJoints.count(_.nonEmpty), 10)
      Math.max(score, maxMaturity)
    }
  }

  /** The definition of one step in a saga with its undo step and example.
    *
    * @param loc
    *   The location of the saga action definition
    * @param id
    *   The name of the SagaAction
    * @param doAction
    *   The command to be done.
    * @param undoAction
    *   The command that undoes [[doAction]]
    * @param examples
    *   An list of examples for the intended behavior
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga action
    */
  case class SagaStep(
    loc: Location,
    id: Identifier,
    // TODO: The do and undo actions should be Seq[Example]
    doAction: SagaStepAction,
    undoAction: SagaStepAction,
    examples: Seq[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends SagaDefinition {
    def contents: Seq[Example] = examples
    def format: String = ""
    final val kind: String = "SagaStep"
  }

  /** The definition of a Saga based on inputs, outputs, and the set of
    * [[SagaStep]]s involved in the saga. Sagas define a computing action based
    * on a variety of related commands that must all succeed atomically or have
    * their effects undone.
    *
    * @param loc
    *   The location of the Saga definition
    * @param id
    *   The name of the saga
    * @param options
    *   The options of the saga
    * @param input
    *   A definition of the aggregate input values needed to invoke the saga, if
    *   any.
    * @param output
    *   A definition of the aggregate output values resulting from invoking the
    *   saga, if any.
    * @param sagaSteps
    *   The set of [[SagaStep]]s that comprise the saga.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga.
    */
  case class Saga(
    loc: Location,
    id: Identifier,
    options: Seq[SagaOption] = Seq.empty[SagaOption],
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    sagaSteps: Seq[SagaStep] = Seq.empty[SagaStep],
    functions: Seq[Function] = Seq.empty[Function],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[SagaDefinition]] = Seq.empty[Include[SagaDefinition]],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[SagaOption, SagaDefinition]
      with ContextDefinition {
    override lazy val contents: Seq[SagaDefinition] = {
      super.contents ++ input.map(_.fields).getOrElse(Seq.empty[Field]) ++
        output.map(_.fields).getOrElse(Seq.empty[Field]) ++ sagaSteps ++ terms
    }
    final val kind: String = "Saga"
    override def isEmpty: Boolean = super.isEmpty && options.isEmpty &&
      input.isEmpty && output.isEmpty

    override def maturity: Int = {
      var score = super.maturity
      if (input.nonEmpty) score += 10
      if (output.nonEmpty) score += 10
      if (sagaSteps.nonEmpty) score += Math.max(sagaSteps.count(_.nonEmpty), 40)
      Math.max(score, maxMaturity)
    }
  }

  case class SagaRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Saga] {
    def format: String = s"${Keywords.saga} ${pathId.format}"
  }

  /** An StoryActor (Role) who is the initiator of the user story. Actors may be
    * persons or machines
    *
    * @param loc
    *   The location of the actor in the source
    * @param id
    *   The name (role) of the actor
    * @param is_a
    *   What kind of thing the actor is
    * @param brief
    *   A brief description of the actor
    * @param description
    *   A longer description of the actor and its role
    */
  case class Actor(
    loc: Location,
    id: Identifier,
    is_a: LiteralString,
    brief: Option[LiteralString],
    description: Option[Description] = None)
      extends LeafDefinition with DomainDefinition {
    def format: String = ""
    override def kind: String = "Actor"
  }

  /** A reference to an StoryActor using a path identifier
    * @param loc
    *   THe location of the StoryActor in the source code
    * @param id
    *   The path identifier that locates the references StoryActor
    */
  case class ActorRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Actor] {
    def format: String = s"${Keywords.actor} ${pathId.format}"
  }

  sealed trait InteractionExpression
      extends RiddlValue with BrieflyDescribedValue

  /** An interaction expression that specifies that each contained expression
    * should be executed in parallel
    * @param loc
    *   Location of the parallel group
    * @param contents
    *   The expressions to execute in parallel
    * @param brief
    *   A brief description of the parallel group
    */
  case class ParallelGroup(
    loc: Location,
    contents: Seq[InteractionExpression],
    brief: Option[LiteralString])
      extends InteractionExpression {

    /** Format the node to a string */
    override def format: String = ""
  }

  /** An interaction expression that specifies that its contents are optional
    * @param loc
    *   The location of the optional group
    * @param contents
    *   The optional expressions
    * @param brief
    *   A brief description of the optional group
    */
  case class OptionalGroup(
    loc: Location,
    contents: Seq[InteractionExpression],
    brief: Option[LiteralString])
      extends InteractionExpression {
    override def format: String = ""
  }

  /** One abstract step in an Interaction between things. The set of case
    * classes associated with this sealed trait provide more type specificity to
    * these three fields.
    */
  sealed trait InteractionStep extends InteractionExpression {
    def from: Reference[Definition]
    def relationship: RiddlNode
    def to: Reference[Definition]
  }

  /** An arbitrary interaction step. The abstract nature of the relationship is
    *
    * @param loc
    *   The location of the step
    * @param from
    *   A reference to the source of the interaction
    * @param relationship
    *   A literal spring that specifies the arbitrary relationship
    * @param to
    *   A reference to the destination of the interaction
    * @param brief
    *   A brief description of the interaction
    */
  case class ArbitraryStep(
    loc: Location,
    from: Reference[Definition],
    relationship: LiteralString,
    to: Reference[Definition],
    brief: Option[LiteralString] = None)
      extends InteractionStep {
    override def format: String = ""
  }

  case class SelfProcessingStep(
    loc: Location,
    from: Reference[Definition],
    relationship: LiteralString,
    brief: Option[LiteralString] = None)
      extends InteractionStep {
    override def format: String = ""
    override def to: Reference[Definition] = from
  }

  case class ActivateOutputStep(
    loc: Location,
    from: OutputRef,
    relationship: LiteralString,
    to: ActorRef,
    brief: Option[LiteralString] = None)
      extends InteractionStep {
    override def format: String = ""
  }

  case class ProvideInputStep(
    loc: Location,
    from: ActorRef,
    relationship: LiteralString,
    to: InputRef,
    brief: Option[LiteralString] = None)
      extends InteractionStep {
    override def format: String = ""
  }

  case class StoryCase(
    loc: Location,
    id: Identifier,
    interactions: Seq[InteractionExpression],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends LeafDefinition with StoryDefinition {
    override def kind: String = "StoryCase"
    override def format: String = ""
  }

  /** An agile user story definition
    * @param loc
    *   Location of the user story
    * @param actor
    *   The actor, or instigator, of the story
    * @param capability
    *   The capability the actor wishes to utilize
    * @param benefit
    *   The benefit of that utilization
    */
  case class UserStory(
    loc: Location,
    actor: ActorRef,
    capability: LiteralString,
    benefit: LiteralString)
      extends RiddlValue {
    def format: String = ""
  }

  /** The definition of an agile user story. Stories define functionality from
    * the perspective of a certain kind of user (man or machine), interacting
    * with the system via some role. RIDDL extends the notion of an agile user
    * story by allowing a linkage between the story and the RIDDL features that
    * implement it.
    *
    * @param loc
    *   The location of the story definition
    * @param id
    *   The name of the story
    * @param userStory
    *   The user story per agile and xP
    * @param shownBy
    *   A list of URLs to visualizations or other materials related to the story
    * @param cases
    *   A list of StoryCase's that define the story
    * @param examples
    *   Gherkin examples to specify "done" for the implementation of the user
    *   story
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the
    */
  case class Story(
    loc: Location,
    id: Identifier,
    userStory: Option[UserStory] = Option.empty[UserStory],
    shownBy: Seq[java.net.URL] = Seq.empty[java.net.URL],
    cases: Seq[StoryCase] = Seq.empty[StoryCase],
    examples: Seq[Example] = Seq.empty[Example],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[StoryDefinition]] = Seq
      .empty[Include[StoryDefinition]],
    options: Seq[StoryOption] = Seq.empty[StoryOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[StoryOption, StoryDefinition]
      with DomainDefinition {
    override def contents: Seq[StoryDefinition] = {
      super.contents ++ cases ++ examples ++ terms
    }
    override def isEmpty: Boolean = {
      contents.isEmpty && shownBy.isEmpty && userStory.isEmpty
    }

    final val kind: String = "Story"

    override def maturity: Int = {
      var score = super.maturity
      if (userStory.nonEmpty) score += 3
      if (shownBy.nonEmpty) score += 10
      if (cases.nonEmpty) score += Math.max(examples.count(_.nonEmpty), 25)
      if (examples.nonEmpty) score += Math.max(examples.count(_.nonEmpty), 9)
      Math.max(score, maxMaturity)
    }
  }

  case class StoryRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Story] {
    def format: String = s"${Keywords.story} ${pathId.format}"
  }

  sealed trait UIElement extends ApplicationDefinition

  case class Group(
    loc: Location,
    id: Identifier,
    types: Seq[Type] = Seq.empty[Type],
    elements: Seq[UIElement] = Seq.empty[UIElement],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends UIElement {
    override def kind: String = "Group"

    override lazy val contents: Seq[ApplicationDefinition] = {
      types ++ elements
    }

    /** Format the node to a string */
    override def format: String = ""
  }

  case class GroupRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Group] {
    def format: String = s"${Keywords.group} ${pathId.format}"
  }

  /** A UI Element that presents some information to the user
    * @param loc
    *   Location of the view in the source
    * @param id
    *   unique identifier oof the view
    * @param types
    *   any type definitions the view needs
    * @param viewed
    *   A result reference for the data too be presented
    * @param brief
    *   A brief description of the view
    * @param description
    *   A detailed description of the view
    */
  case class Output(
    loc: Location,
    id: Identifier,
    types: Seq[Type],
    putOut: ResultRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends UIElement {
    override def kind: String = "Output"

    override lazy val contents: Seq[ApplicationDefinition] = types

    /** Format the node to a string */
    override def format: String = ""
  }

  /** A reference to an View using a path identifier
    * @param loc
    *   The location of the ViewRef in the source code
    * @param id
    *   The path identifier that refers to the View
    */
  case class OutputRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Output] {
    def format: String = s"${Keywords.output} ${pathId.format}"
  }

  /** A Give is a UI Element to allow the user to 'give' some data to the
    * application. It is analogous to a form in HTML
    * @param loc
    *   Location of the Give
    * @param id
    *   Name of the give
    * @param types
    *   type definitions needed for the Give
    * @param given
    *   a Type reference of the type given by the user
    * @param brief
    *   A brief description of the Give
    * @param description
    *   a detailed description of the Give
    */
  case class Input(
    loc: Location,
    id: Identifier,
    types: Seq[Type],
    putIn: CommandRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends UIElement {
    override def kind: String = "Input"

    override lazy val contents: Seq[Definition] = types

    /** Format the node to a string */
    override def format: String = ""
  }

  /** A reference to a Give using a path identifier
    * @param loc
    *   THe location of the GiveRef in the source code
    * @param id
    *   The path identifier that refers to the Give
    */
  case class InputRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Input] {
    def format: String = s"${Keywords.input} ${pathId.format}"
  }

  case class Application(
    loc: Location,
    id: Identifier,
    options: Seq[ApplicationOption] = Seq.empty[ApplicationOption],
    types: Seq[Type] = Seq.empty[Type],
    groups: Seq[Group] = Seq.empty[Group],
    handlers: Seq[Handler] = Seq.empty[Handler],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[ApplicationDefinition]] = Seq.empty,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends VitalDefinition[ApplicationOption, ApplicationDefinition]
      with DomainDefinition {
    override def kind: String = "Application"

    override lazy val contents: Seq[ApplicationDefinition] = {
      super.contents ++ types ++ groups ++ terms ++ includes
    }
  }

  /** A reference to an Application using a path identifier
    * @param loc
    *   THe location of the StoryActor in the source code
    * @param id
    *   The path identifier that refers to the Application
    */
  case class ApplicationRef(loc: Location, pathId: PathIdentifier)
      extends MessageTakingRef[Application] {
    def format: String = s"${Keywords.application} ${pathId.format}"
  }

  /** The definition of a domain. Domains are the highest building block in
    * RIDDL and may be nested inside each other to form a hierarchy of domains.
    * Generally, domains follow hierarchical organization structure but other
    * taxonomies and ontologies may be modelled with domains too.
    *
    * @param loc
    *   The location of the domain definition
    * @param id
    *   The name of the domain
    * @param types
    *   The types defined in the scope of the domain
    * @param contexts
    *   The contexts defined in the scope of the domain
    * @param plants
    *   The plants defined in the scope of the domain
    * @param domains
    *   Nested sub-domains within this domain
    * @param terms
    *   Definition of terms pertaining to this domain that provide explanation
    *   of concepts from the domain.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the domain.
    */
  case class Domain(
    loc: Location,
    id: Identifier,
    options: Seq[DomainOption] = Seq.empty[DomainOption],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    authorDefs: Seq[Author] = Seq.empty[Author],
    types: Seq[Type] = Seq.empty[Type],
    contexts: Seq[Context] = Seq.empty[Context],
    plants: Seq[Plant] = Seq.empty[Plant],
    actors: Seq[Actor] = Seq.empty[Actor],
    stories: Seq[Story] = Seq.empty[Story],
    applications: Seq[Application] = Seq.empty[Application],
    domains: Seq[Domain] = Seq.empty[Domain],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[DomainDefinition]] = Seq
      .empty[Include[DomainDefinition]],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends VitalDefinition[DomainOption, DomainDefinition]
      with RootDefinition
      with WithTypes
      with DomainDefinition {

    override lazy val contents: Seq[DomainDefinition] = {
      super.contents ++ domains ++ types ++ contexts ++ plants ++ actors ++
        stories ++ applications ++ terms ++ authorDefs
    }
    final val kind: String = "Domain"

    override def maturity: Int = {
      var score = super.maturity
      if (types.nonEmpty) score += Math.max(types.count(_.nonEmpty), 15)
      if (contexts.nonEmpty) score += Math.max(contexts.count(_.nonEmpty), 15)
      if (plants.nonEmpty) score += Math.max(plants.count(_.nonEmpty), 10)
      if (stories.nonEmpty) score += Math.max(stories.count(_.nonEmpty), 15)
      if (applications.nonEmpty) score += Math.max(stories.count(_.nonEmpty), 5)
      if (domains.nonEmpty) score += Math.max(domains.count(_.nonEmpty), 10)
      Math.max(score, maxMaturity)
    }
  }

  /** A reference to a domain definition
    *
    * @param loc
    *   The location at which the domain definition occurs
    * @param id
    *   The path identifier for the referenced domain.
    */
  case class DomainRef(loc: Location, pathId: PathIdentifier)
      extends Reference[Domain] {
    override def format: String = s"${Keywords.domain} ${pathId.format}"
  }

}
