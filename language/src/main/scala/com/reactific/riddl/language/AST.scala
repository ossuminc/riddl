/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.Terminals.Keywords
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
object AST extends ast.Expressions with ast.TypeExpression {

  /** A [[RiddlValue]] that holds the author's information
   *
   * @param loc
   * The location of the author information
   * @param name
   * The full name of the author
   * @param email
   * The author's email address
   * @param organization
   * The name of the organization the author is associated with
   * @param title
   * The author's title within the organization
   * @param url
   * A URL associated with the author
   */
  case class AuthorInfo(
    loc: Location,
    id: Identifier,
    name: LiteralString,
    email: LiteralString,
    organization: Option[LiteralString] = None,
    title: Option[LiteralString] = None,
    url: Option[java.net.URL] = None,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
    extends LeafDefinition with DomainDefinition
      with ContextDefinition with EntityDefinition with StoryDefinition
      with PlantDefinition {
    override def isEmpty: Boolean = {
      name.isEmpty && email.isEmpty && organization.isEmpty && title.isEmpty
    }

    final val kind: String = "Author Info"
  }


  /** Base trait of any definition that is a container and contains types
   */
  trait TypeContainer  {
    def types: Seq[Type]
    def collectMessages: Seq[Type] = {
      types.filter(_.isMessageKind)
    }
  }


  /** Added to definitions that support a list of term definitions */
  sealed trait WithTerms {
    def terms: Seq[Term]
  }

  /** Added to definitions that support includes */
  sealed trait WithIncludes {
    def includes: Seq[Include]
  }


  /** A term definition for the glossary */
  case class Term(
    loc: Location,
    id: Identifier,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None)
      extends LeafDefinition with DomainDefinition
        with ContextDefinition with PlantDefinition {
    override def isEmpty: Boolean = description.isEmpty
    final val kind: String = "Term"
  }

  case class Include(
    loc: Location = Location(RiddlParserInput.empty),
    contents: Seq[Definition] = Seq.empty[Definition],
    path: Option[Path] = None)
      extends Definition
      with AdaptorDefinition
      with ContextDefinition
      with DomainDefinition
      with EntityDefinition
      with PlantDefinition {

    def id: Identifier = Identifier.empty

    def brief: Option[LiteralString] = Option.empty[LiteralString]

    def description: Option[Description] = None

    override def isRootContainer: Boolean = true
    final val kind: String = "Include"

  }

  /** The root of the containment hierarchy, corresponding roughly to a level
    * about a file.
    *
    * @param contents
    *   The sequence of domains contained by this root container
    */
  case class RootContainer(
    contents: Seq[Domain] = Nil,
    inputs: Seq[RiddlParserInput] = Nil
  ) extends Definition {

    override def isRootContainer: Boolean = true

    def loc: Location = Location.empty

    override def id: Identifier = Identifier(loc, "Root")

    override def identify: String = "Root"

    override def identifyWithLoc: String = "Root"

    override def description: Option[Description] = None

    override def brief: Option[LiteralString] = None
    final val kind: String = "Root"

  }

  object RootContainer {
    val empty: RootContainer =
      RootContainer(Seq.empty[Domain], Seq.empty[RiddlParserInput])
  }




  /** Base trait for the four kinds of message references */
  sealed trait MessageRef extends Reference[Type] {
    def messageKind: MessageKind

    override def format: String = s"${messageKind.kind} ${id.format}"
  }

  /** A Reference to a command type
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier to the event type
    */
  case class CommandRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = CommandKind
  }

  /** A Reference to an event type
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier to the event type
    */
  case class EventRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = EventKind
  }

  /** A reference to a query type
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier to the query type
    */
  case class QueryRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = QueryKind
  }

  /** A reference to a result type
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier to the result type
    */
  case class ResultRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = ResultKind
  }

  case class OtherRef(loc: Location) extends MessageRef {
    def id: PathIdentifier = PathIdentifier(loc, Seq.empty[String])
    def messageKind: MessageKind = OtherKind

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
      extends Definition with ContextDefinition
      with EntityDefinition with FunctionDefinition
      with DomainDefinition  {
    override def contents: Seq[Definition] = {
      typ match {
        case Aggregation(_, fields)      => fields
        case Enumeration(_, enumerators) => enumerators
        case MessageType(_, _, fields)   => fields
        case _                           => Seq.empty[Definition]
      }
    }
    def isMessageKind: Boolean = {
      typ.isInstanceOf[MessageType]
    }
    final val kind: String = "Type"
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
  case class TypeRef(loc: Location, id: PathIdentifier)
      extends Reference[Type] {
    override def format: String = s"type ${id.format}"
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
      extends SagaStepAction {
    override def format: String = what.format
  }


  /** An action that is intended to generate a runtime error in the
   * generated application or otherwise indicate an error condition
   *
   * @param loc
   * The location where the action occurs in the source
   * @param message
   * The error message to report
   * @param description
   * An optional description of the action
   */
  case class ErrorAction(
    loc: Location,
    message: LiteralString,
    description: Option[Description])
    extends SagaStepAction {
    override def format: String = message.format
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
    description: Option[Description] = None
  ) extends Action {
    override def format: String = { s"append ${value.format} to ${target.format}"}
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
  case class MessageConstructor(msg: MessageRef, args: ArgList = ArgList())
      extends RiddlNode {
    override def format: String = msg.format + {
      if (args.nonEmpty) { args.format }
      else { "()" }
    }
  }


  /** An action that returns a value from a function
   *
   * @param loc
   * The location in the source of the publish action
   * @param value
   * The value to be returned
   * @param description
   * An optional description of the yield action
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
    override def format: String = s"morph ${}"
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
    override def format: String = s"become ${}"
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
    entity: EntityRef,
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
      extends GherkinClause

  /** A [[GherkinClause]] for the When part of a Gherkin [[Example]]
    *
    * @param loc
    *   The location of the When clause
    * @param condition
    *   The condition expression that defines the trigger for the [[Example]]
    */
  case class WhenClause(loc: Location, condition: Condition)
      extends GherkinClause

  /** A [[GherkinClause]] for the Then part of a Gherkin [[Example]]. This part
    * specifies what should be done if the [[WhenClause]] evaluates to true.
    *
    * @param loc
    *   The location of the Then clause
    * @param action
    *   The action to be performed
    */
  case class ThenClause(loc: Location, action: Action) extends GherkinClause

  /** A [[GherkinClause]] for the But part of a Gherkin [[Example]]. This part
    * specifies what should be done if the [[WhenClause]] evaluates to false.
    *
    * @param loc
    *   The location of the But clause
    * @param action
    *   The action to be performed
    */
  case class ButClause(loc: Location, action: Action) extends GherkinClause

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
        with ProcessorDefinition with FunctionDefinition with StoryDefinition  {
    final val kind: String = "Example"
    override def isEmpty: Boolean = givens.isEmpty && whens.isEmpty &&
      thens.isEmpty && buts.isEmpty  }

  // ////////////////////////////////////////////////////////// Entities

  /** Base trait of any value used in the definition of an entity
    */
  sealed trait EntityValue extends RiddlValue

  /** Abstract base class of options for entities
    *
    * @param name
    *   the name of the option
    */
  sealed abstract class EntityOption(val name: String)
      extends EntityValue with OptionValue

  /** An [[EntityOption]] that indicates that this entity should store its state
    * in an event sourced fashion.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityEventSourced(loc: Location)
      extends EntityOption("event sourced")

  /** An [[EntityOption]] that indicates that this entity should store only the
    * latest value without using event sourcing. In other words, the history of
    * changes is not stored.
    *
    * @param loc
    *   The location of the option
    */
  case class EntityValueOption(loc: Location) extends EntityOption("value")

  /** An [[EntityOption]] that indicates that this entity should not persist its
    * state and is only available in transient memory. All entity values will be
    * lost when the service is stopped.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityTransient(loc: Location) extends EntityOption("transient")

  /** An [[EntityOption]] that indicates that this entity is an aggregate root
    * entity through which all commands and queries are sent on behalf of the
    * aggregated entities.
    *
    * @param loc
    *   The location of the option
    */
  case class EntityIsAggregate(loc: Location) extends EntityOption("aggregate")

  /** An [[EntityOption]] that indicates that this entity favors consistency
    * over availability in the CAP theorem.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsConsistent(loc: Location) extends EntityOption("consistent")

  /** A [[EntityOption]] that indicates that this entity favors availability
    * over consistency in the CAP theorem.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsAvailable(loc: Location) extends EntityOption("available")

  /** An [[EntityOption]] that indicates that this entity is intended to
    * implement a finite state machine.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsFiniteStateMachine(loc: Location)
      extends EntityOption("finite state machine")

  /** An [[EntityOption]] that indicates that this entity should allow receipt
    * of commands and queries via a message queue.
    *
    * @param loc
    *   The location at which this option occurs.
    */
  case class EntityMessageQueue(loc: Location)
      extends EntityOption("message queue")

  /** An [[EntityOption]] that indicates the general kind of entity being
    * defined. This option takes a value which provides the kind. Examples of
    * useful kinds are "device", "actor", "concept", "machine", and similar
    * kinds of entities. This entity option may be used by downstream AST
    * processors, especially code generators.
    *
    * @param loc
    *   The location of the entity kind option
    * @param args
    *   The argument to the option
    */
  case class EntityKind(loc: Location, override val args: Seq[LiteralString])
      extends EntityOption("kind")

  /** A reference to an entity
    *
    * @param loc
    *   The location of the entity reference
    * @param id
    *   The path identifier of the referenced entity.
    */
  case class EntityRef(loc: Location, id: PathIdentifier)
      extends Reference[Entity] {
    override def format: String = s"${Keywords.entity} ${id.format}"
  }

  /** A reference to a function.
    *
    * @param loc
    *   The location of the function reference.
    * @param id
    *   The path identifier of the referenced function.
    */
  case class FunctionRef(loc: Location, id: PathIdentifier)
      extends Reference[Function] {
    override def format: String = s"${Keywords.function} ${id.format}"
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
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends EntityDefinition
      with ContextDefinition with FunctionDefinition {
    override lazy val contents: Seq[FunctionDefinition] = {
      input.map(_.fields).getOrElse(Seq.empty[Field]) ++
      output.map(_.fields).getOrElse(Seq.empty[Field]) ++
      types ++ functions ++ examples
    }

    override def isEmpty: Boolean = examples.isEmpty && input.isEmpty &&
      output.isEmpty

    final val kind: String = "Function"

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
      extends LeafDefinition with EntityDefinition {
    override def isEmpty: Boolean = expression.isEmpty
    final val kind: String = "Invariant"
  }


  /** Defines the actions to be taken when a particular message is received by
    * an entity. [[OnClause]]s are used in the definition of a [[Handler]] with
    * one for each kind of message that handler deals with.
    *
    * @param loc
    *   The location of the "on" clause
    * @param msg
    *   A reference to the message type that is handled
    * @param examples
    *   A set of examples that define the behavior when the [[msg]] is received.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnClause(
    loc: Location,
    msg: MessageRef,
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends HandlerDefinition {
    def id: Identifier = Identifier(msg.loc, s"On ${msg.format}")
    override def isEmpty: Boolean = examples.isEmpty
    override def contents: Seq[Example] = examples
    final val kind: String = "On Clause"

  }

  /** A named handler of messages (commands, events, queries) that bundles
    * together a set of [[OnClause]] definitions and by doing so defines the
    * behavior of an entity. Note that entities may define multiple handlers and
    * switch between them to change how it responds to messages over time or in
    * response to changing conditions
    *
    * @param loc
    *   The location of the handler definition
    * @param id
    *   The name of the handler.
    * @param clauses
    *   The set of [[OnClause]] definitions that define how the entity responds
    *   to received messages.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the handler
    */
  case class Handler(
    loc: Location,
    id: Identifier,
    applicability: Option[Reference[?]] = None,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends ContextDefinition with EntityDefinition {
    override def isEmpty: Boolean = super.isEmpty && clauses.isEmpty
    override def contents: Seq[OnClause] = clauses
    final val kind: String = "Handler"

  }

  /** A reference to a Handler
    *
    * @param loc
    *   The location of the handler reference
    * @param id
    *   The path identifier of the referenced handler
    */
  case class HandlerRef(loc: Location, id: PathIdentifier)
      extends Reference[Handler] {
    override def format: String = s"${Keywords.handler} ${id.format}"
  }

  /** Represents the state of an entity. The [[MorphAction]] can cause the state
    * definition of an entity to change.
    *
    * @param loc
    *   The location of the state definition
    * @param id
    *   The name of the state definition
    * @param typeEx
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
    typeEx: Aggregation,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends EntityDefinition {

    override def contents: Seq[Field] = typeEx.fields
    final val kind: String = "State"
  }

  /** A reference to an entity's state definition
    *
    * @param loc
    *   The location of the state reference
    * @param id
    *   The path identifier of the referenced state definition
    */
  case class StateRef(loc: Location, id: PathIdentifier)
      extends Reference[State] {
    override def format: String = s"${Keywords.state} ${id.format}"
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
    includes: Seq[Include] = Seq.empty[Include],
    authors: Seq[AuthorInfo] = Seq.empty[AuthorInfo],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends ContextDefinition
      with TypeContainer
      with OptionsDef[EntityOption]
      with WithIncludes {

    lazy val contents: Seq[EntityDefinition] = {
      states ++ types ++ handlers ++ functions ++ invariants ++ includes
      ++ authors
    }

    final val kind: String = "Entity"

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty
  }

  sealed trait Adaptation extends AdaptorDefinition {
    def messageRef: MessageRef
    def examples: Seq[Example]
  }

  /** The specification of a single adaptation based on message
    *
    * @param loc
    *   The location of the adaptation definition
    * @param id
    *   The name of the adaptation
    * @param messageRef
    *   The event that triggers the adaptation, inherited from [[Adaptation]]
    * @param command
    *   The command that adapts the event to the bounded context
    * @param examples
    *   Optional set of Gherkin [[Example]]s to define the adaptation
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   Optional description of the adaptation.
    */
  case class EventCommandA8n(
    loc: Location,
    id: Identifier,
    messageRef: EventRef,
    command: CommandRef,
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends Adaptation {
    override def contents: Seq[Example] = examples
    final val kind: String = "Event Command Adaptation"

  }

  /** The specification of a single adaptation based on message
   *
   * @param loc
   *   The location of the adaptation definition
   * @param id
   *   The name of the adaptation
   * @param messageRef
   *   The command that triggers the adaptation, inherited from [[Adaptation]]
   * @param command
   *   The command resulting from the adaptation of the [[messageRef]] to the
   *   bounded context
   * @param examples
   *   Optional set of Gherkin [[Example]]s to define the adaptation
   * @param brief
   *   A brief description (one sentence) for use in documentation
   * @param description
   *   Optional description of the adaptation.
   */
  case class CommandCommandA8n(
    loc: Location,
    id: Identifier,
    messageRef: CommandRef,
    command: CommandRef,
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
    extends Adaptation {
    override def contents: Seq[Example] = examples
    final val kind: String = "Command Command Adaptation"
  }

  /** An adaptation that takes an action on event receipt
    *
    * @param loc
    *   The location of the ActionAdaptation
    * @param id
    *   The identifier for this ActionAdaptation
    * @param messageRef
    *   The event to which this adaptation adapts, inherited from [[Adaptation]]
    * @param actions
    *   The actions to be taken when [[messageRef]] is received
    * @param brief
    *   The brief description of this adaptation
    * @param description
    *   The full description of this adaptation
    */
  case class EventActionA8n(
    loc: Location,
    id: Identifier,
    messageRef: EventRef,
    actions: Seq[Action] = Seq.empty[Action],
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = Option.empty[Description])
      extends Adaptation {
    def contents: Seq[Example] = examples
    override def isEmpty: Boolean = examples.isEmpty && actions.isEmpty
    final val kind: String = "Event Action Adaptation"
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
    * @param ref
    *   A reference to the bounded context from which messages are adapted
    * @param adaptations
    *   A set of [[AdaptorDefinition]]s that indicate what to do when messages
    *   occur.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   Optional description of the adaptor.
    */
  case class Adaptor(
    loc: Location,
    id: Identifier,
    ref: ContextRef,
    adaptations: Seq[Adaptation] = Seq.empty[Adaptation],
    includes: Seq[Include] = Seq.empty[Include],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends ContextDefinition
      with WithIncludes {
    lazy val contents: Seq[AdaptorDefinition] = adaptations ++ includes
    final val kind: String = "Adaptor"
  }

  case class Projection(
    loc: Location,
    id: Identifier,
    fields: Seq[Field],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends ContextDefinition {
    lazy val contents: Seq[Field] = fields
    final val kind: String = "Projection"
  }

  /** A reference to an context's projection definition
   *
   * @param loc
   * The location of the state reference
   * @param id
   * The path identifier of the referenced projection definition
   */
  case class ProjectionRef(loc: Location, id: PathIdentifier)
    extends Reference[Projection] {
    override def format: String = s"${Keywords.projection} ${id.format}"
  }

  /** Base trait for all options a Context can have.
    */
  sealed abstract class ContextOption(val name: String) extends OptionValue

  case class ContextPackageOption(loc: Location, override val args: Seq[LiteralString])
    extends ContextOption("package")


  /** A context's "wrapper" option. This option suggests the bounded context is
    * to be used as a wrapper around an external system and is therefore at the
    * boundary of the context map
    *
    * @param loc
    *   The location of the wrapper option
    */
  case class WrapperOption(loc: Location) extends ContextOption("wrapper")

  /** A context's "service" option. This option suggests the bounded context is
    * intended to be a DDD service, similar to a wrapper but without any
    * persistent state and more of a stateless service aspect to its nature
    *
    * @param loc
    *   The location at which the option occurs
    */
  case class ServiceOption(loc: Location) extends ContextOption("service")

  /** A context's "function" option that suggests
    *
    * @param loc
    *   The location of the function option
    */
  case class FunctionOption(loc: Location) extends ContextOption("function")

  /** A context's "gateway" option that suggests the bounded context is intended
    * to be an application gateway to the model. Gateway's provide
    * authentication and authorization access to external systems, usually user
    * applications.
    *
    * @param loc
    *   The location of the gateway option
    */
  case class GatewayOption(loc: Location) extends ContextOption("gateway")

  /** A reference to a bounded context
    *
    * @param loc
    *   The location of the reference
    * @param id
    *   The path identifier for the referenced context
    */
  case class ContextRef(loc: Location, id: PathIdentifier)
      extends Reference[Context] {
    override def format: String = s"context ${id.format}"
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
    includes: Seq[Include] = Seq.empty[Include],
    handlers: Seq[Handler] = Seq.empty[Handler],
    projections: Seq[Projection] = Seq.empty[Projection],
    authors: Seq[AuthorInfo] = Seq.empty[AuthorInfo],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends DomainDefinition
      with TypeContainer
      with OptionsDef[ContextOption]
      with WithIncludes
      with WithTerms {
    lazy val contents: Seq[ContextDefinition] = types ++ entities ++ adaptors ++
      sagas ++ functions ++ terms ++ includes ++ authors
    final val kind: String = "Context"

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty
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
    final val kind: String = "Pipe"
  }

  /** Base trait of definitions defined in a processor
    */
  trait ProcessorDefinition extends Definition

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
    final val kind: String = "Outlet"
  }

  sealed trait ProcessorShape extends RiddlValue {
    def keyword: String
  }

  case class Source(loc: Location) extends ProcessorShape {
    def keyword: String = Keywords.source
  }

  case class Sink(loc: Location) extends ProcessorShape {
    def keyword: String = Keywords.sink
  }

  case class Flow(loc: Location) extends ProcessorShape {
    def keyword: String = Keywords.flow
  }

  case class Merge(loc: Location) extends ProcessorShape {
    def keyword: String = Keywords.merge
  }

  case class Split(loc: Location) extends ProcessorShape {
    def keyword: String = Keywords.split
  }

  case class Multi(loc: Location) extends ProcessorShape {
    def keyword: String = Keywords.multi
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
    inlets: Seq[Inlet],
    outlets: Seq[Outlet],
    examples: Seq[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends PlantDefinition with ContextDefinition {
    override def contents: Seq[ProcessorDefinition] = inlets ++ outlets ++
      examples
    final val kind: String = shape.getClass.getSimpleName
  }

  /** A reference to a pipe
    *
    * @param loc
    *   The location of the pipe reference
    * @param id
    *   The path identifier for the referenced pipe.
    */
  case class PipeRef(loc: Location, id: PathIdentifier)
      extends Reference[Pipe] {
    override def format: String = s"pipe ${id.format}"
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
  case class InletRef(loc: Location, id: PathIdentifier)
      extends StreamletRef[Inlet] {
    override def format: String = s"inlet ${id.format}"
  }

  /** A reference to an [[Outlet]]
    *
    * @param loc
    *   The location of the outlet reference
    * @param id
    *   The path identifier of the referenced [[Outlet]]
    */
  case class OutletRef(loc: Location, id: PathIdentifier)
      extends StreamletRef[Outlet] {
    override def format: String = s"outlet ${id.format}"
  }

  /** Sealed base trait for both kinds of Joint definitions
    */
  sealed trait Joint extends LeafDefinition with AlwaysEmpty
    with PlantDefinition with ContextDefinition

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
    includes: Seq[Include] = Seq.empty[Include],
    authors: Seq[AuthorInfo] = Seq.empty[AuthorInfo],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends DomainDefinition
      with WithIncludes
      with WithTerms {
    lazy val contents: Seq[PlantDefinition] = pipes ++ processors ++ inJoints ++
      outJoints ++ terms ++ includes
    final val kind: String = "Plant"
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
    doAction: SagaStepAction,
    undoAction: SagaStepAction,
    examples: Seq[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends SagaDefinition {
    def contents: Seq[Example] = examples
    final val kind: String = "SagaStep"
  }

  /** Base trait for all options applicable to a saga.
    */
  sealed abstract class SagaOption(val name: String) extends OptionValue

  /** A [[SagaOption]] that indicates sequential (serial) execution of the saga
    * actions.
    *
    * @param loc
    *   The location of the sequential option
    */
  case class SequentialOption(loc: Location) extends SagaOption ("sequential")

  /** A [[SagaOption]] that indicates parallel execution of the saga actions.
    *
    * @param loc
    *   The location of the parallel option
    */
  case class ParallelOption(loc: Location) extends SagaOption ("parallel")

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
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends ContextDefinition with OptionsDef[SagaOption] {
    lazy val contents: Seq[SagaDefinition] = {
      input.map(_.fields).getOrElse(Seq.empty[Field]) ++
      output.map(_.fields).getOrElse(Seq.empty[Field]) ++ sagaSteps
    }
    final val kind: String = "Saga"
    override def isEmpty: Boolean = super.isEmpty && options.isEmpty &&
      input.isEmpty && output.isEmpty
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
    * @param role
    *   The role of the actor involved in the story
    * @param capability
    *   The capability utilized by the actor in the story
    * @param benefit
    *   The benefit, to the user, of using the capability.
    * @param shownBy
    *   A list of URLs to visualizations or other materials related to the story
    * @param implementedBy
    *   A list of PathIdentifiers, presumably contexts, that implement the story
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
    role: LiteralString = LiteralString.empty,
    capability: LiteralString = LiteralString.empty,
    benefit: LiteralString = LiteralString.empty,
    shownBy: Seq[java.net.URL] = Seq.empty[java.net.URL],
    implementedBy: Seq[DomainRef] = Seq.empty[DomainRef],
    examples: Seq[Example] = Seq.empty[Example],
    authors: Seq[AuthorInfo] = Seq.empty[AuthorInfo],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends DomainDefinition {
    override def contents: Seq[StoryDefinition] = examples ++ authors
    final val kind: String = "Story"
  }

  /** A reference to a domain definition
    *
    * @param loc
    *   The location at which the domain definition occurs
    * @param id
    *   The path identifier for the referenced domain.
    */
  case class DomainRef(loc: Location, id: PathIdentifier)
      extends Reference[Domain] {
    override def format: String = s"domain ${id.format}"
  }
  /** Base trait for all options a Domain can have.
   */
  sealed abstract class DomainOption(val name: String) extends OptionValue

  /** A context's "wrapper" option. This option suggests the bounded context is
   * to be used as a wrapper around an external system and is therefore at the
   * boundary of the context map
   *
   * @param loc
   *   The location of the wrapper option
   */
  case class DomainPackageOption(loc: Location, override val args: Seq[LiteralString])
    extends DomainOption("package")

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
    authors: Seq[AuthorInfo] = Seq.empty[AuthorInfo],
    types: Seq[Type] = Seq.empty[Type],
    contexts: Seq[Context] = Seq.empty[Context],
    plants: Seq[Plant] = Seq.empty[Plant],
    stories: Seq[Story] = Seq.empty[Story],
    domains: Seq[Domain] = Seq.empty[Domain],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include] = Seq.empty[Include],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends TypeContainer
      with OptionsDef[DomainOption]
      with DomainDefinition
      with WithIncludes
      with WithTerms {
    def contents: Seq[DomainDefinition] = {
      domains ++ types ++ contexts ++ plants ++ stories ++ terms ++
        includes ++ authors
    }
    final val kind: String = "Domain"
  }

  //////////////////////////////////////////////////// UTILITY FUNCTIONS

  def authorsOf(defn: Definition): Seq[AuthorInfo] = {
    defn match {
      case d: DomainDefinition =>
        d match {
          case dmn: Domain => dmn.authors
          case ctxt: Context => ctxt.authors
          case ntty: Entity => ntty.authors
          case stry: Story => stry.authors
          case plnt: Plant => plnt.authors
          case _ => Seq.empty[AuthorInfo]
        }
      case _ => Seq.empty[AuthorInfo]
    }
  }


  /** A function to provide the kind of thing that a DescribedValue is
   *
   * @param definition
   * The DescribedValue for which the kind is returned
   * @return
   * A string for the kind of DescribedValue
   */
  def kind(definition: DescribedValue): String = {
    definition match {
      case _: EventActionA8n => "Event -> Action Adaptation"
      case _: EventCommandA8n => "Event -> Command Adaptation"
      case _: CommandCommandA8n => "Command -> Command Adaptation"
      case _: Adaptor => "Adaptor"
      case _: Context => "Context"
      case _: Domain => "Domain"
      case _: Entity => "Entity"
      case _: Enumerator => "Enumerator"
      case _: Example => "Example"
      case _: Field => "Field"
      case _: Function => "Function"
      case _: Handler => "Handler"
      case _: Inlet => "Inlet"
      case _: Invariant => "Invariant"
      case _: Joint => "Joint"
      case _: Outlet => "Outlet"
      case _: Pipe => "Pipe"
      case _: Plant => "Plant"
      case p: Processor => p.shape.getClass.getSimpleName
      case _: RootContainer => "Root"
      case _: Saga => "Saga"
      case _: SagaStep => "SagaStep"
      case _: State => "State"
      case _: Term => "Term"
      case _: Type => "Type"
      case _: AskAction => "Ask Action"
      case _: BecomeAction => "Become Action"
      case _: MorphAction => "Morph Action"
      case _: SetAction => "Set Action"
      case _: PublishAction => "Publish Action"
      case _: TellAction => "Tell Action"
      case _: ArbitraryAction => "Arbitrary Action"
      case _: OnClause => "On Clause"
      case _ => "Definition"
    }
  }

  def kind(c: Container[Definition]): String = {
    c match {
      case _: Type => "Type"
      case _: Enumeration => "Enumeration"
      case _: Aggregation => "Aggregation"
      case _: State => "State"
      case _: Entity => "Entity"
      case _: Context => "Context"
      case _: Function => "Function"
      case _: EventCommandA8n => "Event -> Command Adaptation"
      case _: CommandCommandA8n => "Command -> Command Adaptation"
      case _: Adaptor => "Adaptor"
      case _: Processor => "Processor"
      case _: Plant => "Plant"
      case _: SagaStep => "SagaStep"
      case _: Saga => "Saga"
      case _: Story => "Story"
      case _: Domain => "Domain"
      case _: Include => "Include"
      case _: RootContainer => "Root"
      case _ => throw new IllegalStateException("No other kinds of Containers")
    }
  }
}
