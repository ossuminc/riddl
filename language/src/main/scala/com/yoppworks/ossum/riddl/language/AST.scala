package com.yoppworks.ossum.riddl.language

import scala.annotation.unused
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

// scalastyle:off number.of.methods

/** Abstract Syntax Tree This object defines the model for processing IDDL and producing a raw AST
  * from it. This raw AST has no referential integrity, it just results from applying the parsing
  * rules to the input. The RawAST models produced from parsing are syntactically correct but have
  * no semantic validation. The Transformation passes convert RawAST model to AST model which is
  * referentially and semantically consistent (or the user gets an error).
  */
object AST {

  /** The root trait of all things RIDDL AST */
  sealed trait RiddlNode

  final val defaultSourceName = "default"

  /** A location of an item in the input */
  case class Location(
    line: Int = 0,
    col: Int = 0,
    source: String = defaultSourceName) {
    override def toString: String = { s"$source($line:$col)" }
  }

  object Location {
    val empty: Location = Location()

    implicit def apply(line: Int): Location = { Location(line, 0, defaultSourceName) }

    implicit def apply(
      pair: (Int, Int)
    ): Location = { Location(pair._1, pair._2, defaultSourceName) }

    implicit def apply(triple: (Int, Int, String)): Location = {
      Location(triple._1, triple._2, triple._3)
    }
  }

  /** */
  sealed trait RiddlValue extends RiddlNode {
    def loc: Location
    def kind: String = this.getClass.getSimpleName
  }

  case class Identifier(loc: Location, value: String) extends RiddlValue
  case class RiddlOption(loc: Location, name: String) extends RiddlValue
  case class LiteralString(loc: Location, s: String) extends RiddlValue

  case class PathIdentifier(loc: Location, value: Seq[String]) extends RiddlValue {

    def format: String = { if (value.isEmpty) "<empty>" else value.reverse.mkString(".") }
  }

  case class Description(
    loc: Location = 0 -> 0,
    brief: Seq[LiteralString] = Seq.empty[LiteralString],
    details: Seq[LiteralString] = Seq.empty[LiteralString],
    nameOfItems: Option[LiteralString] = None,
    items: Map[Identifier, Seq[LiteralString]] = Map.empty[Identifier, Seq[LiteralString]],
    citations: Seq[LiteralString] = Seq.empty[LiteralString])

  sealed trait Reference extends RiddlValue {
    def id: PathIdentifier
  }

  sealed trait DefinitionValue extends RiddlValue

  sealed trait DefinitionWithDescription extends DefinitionValue {
    def description: Option[Description]
  }

  sealed trait Definition extends DefinitionWithDescription {
    def id: Identifier
    def identify: String = s"$kind '${id.value}'"
  }

  sealed trait AdaptorDefinition extends Definition
  sealed trait ContextDefinition extends Definition
  sealed trait DomainDefinition extends Definition
  sealed trait EntityDefinition extends Definition

  sealed trait Container extends Definition {
    def contents: Seq[Definition]
  }

  case class RootContainer(contents: Seq[Container]) extends Container {
    def id: Identifier = Identifier((0, 0), "root")
    def description: Option[Description] = None
    def loc: Location = (0, 0)
  }

  object RootContainer {
    val empty: RootContainer = RootContainer(Seq.empty[Container])
  }

  case class DefRef[+T <: Definition](loc: Location, id: PathIdentifier) extends Reference

  // ////////////////////////////////////////////////////////// TYPES

  sealed trait TypeExpression extends DefinitionValue

  case class TypeRef(loc: Location, id: PathIdentifier) extends Reference with TypeExpression {}

  case class Optional(loc: Location, texp: TypeExpression) extends TypeExpression

  case class ZeroOrMore(loc: Location, texp: TypeExpression) extends TypeExpression

  case class OneOrMore(loc: Location, texp: TypeExpression) extends TypeExpression

  /** Represents one variant among (one or) many variants that comprise an [[Enumeration]]
    *
    * @param id
    *   the identifier (name) of the Enumerator
    * @param enumVal
    *   the optional int value
    * @param typeRef
    *   enumerators can optionally refer to an aggregation type, such as `Keyboard` in:
    * {{{
    *                 type KeyboardType is { locale: Locale }
    *                 type Device is any of { Keyboard is type KeyboardType Mouse Monitor}
    * }}}
    * @param description
    *   the description of the enumerator. Each Enumerator in an enumeration may define independent
    *   descriptions
    */
  case class Enumerator(
    loc: Location,
    id: Identifier,
    enumVal: Option[LiteralInteger] = None,
    typeRef: Option[TypeRef] = None,
    description: Option[Description] = None)
      extends Definition

  case class Enumeration(
    loc: Location,
    of: Seq[Enumerator],
    description: Option[Description] = None)
      extends TypeExpression

  case class Alternation(
    loc: Location,
    of: Seq[TypeExpression],
    description: Option[Description] = None)
      extends TypeExpression

  case class Field(
    loc: Location,
    id: Identifier,
    typeEx: TypeExpression,
    description: Option[Description] = None)
      extends Definition

  case class Aggregation(
    loc: Location,
    fields: Seq[Field] = Seq.empty[Field],
    description: Option[Description] = None)
      extends TypeExpression with EntityValue

  case class Mapping(
    loc: Location,
    from: TypeExpression,
    to: TypeExpression,
    description: Option[Description] = None)
      extends TypeExpression

  case class RangeType(
    loc: Location,
    min: LiteralInteger,
    max: LiteralInteger,
    description: Option[Description] = None)
      extends TypeExpression

  case class ReferenceType(
    loc: Location,
    entity: EntityRef,
    description: Option[Description] = None)
      extends TypeExpression

  case class Pattern(
    loc: Location,
    pattern: Seq[LiteralString],
    description: Option[Description] = None)
      extends TypeExpression

  case class UniqueId(
    loc: Location,
    entityPath: PathIdentifier,
    description: Option[Description] = None)
      extends TypeExpression

  abstract class PredefinedType extends TypeExpression {
    def loc: Location
  }

  object PredefinedType {
    final def unapply(preType: PredefinedType): Option[String] = Some(preType.kind)
  }

  case class Strng(
    loc: Location,
    min: Option[LiteralInteger] = None,
    max: Option[LiteralInteger] = None)
      extends PredefinedType {
    override def kind: String = "String"
  }

  case class Bool(loc: Location) extends PredefinedType {
    override def kind: String = "Boolean"
  }
  case class Number(loc: Location) extends PredefinedType
  case class Integer(loc: Location) extends PredefinedType
  case class Decimal(loc: Location) extends PredefinedType
  case class Real(loc: Location) extends PredefinedType
  case class Date(loc: Location) extends PredefinedType
  case class Time(loc: Location) extends PredefinedType
  case class DateTime(loc: Location) extends PredefinedType
  case class TimeStamp(loc: Location) extends PredefinedType
  case class Duration(loc: Location) extends PredefinedType
  case class UUID(loc: Location) extends PredefinedType
  case class URL(loc: Location, scheme: Option[LiteralString] = None) extends PredefinedType
  case class LatLong(loc: Location) extends PredefinedType
  case class Nothing(loc: Location) extends PredefinedType

  sealed trait TypeDefinition extends Definition

  case class Type(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    description: Option[Description] = None)
      extends TypeDefinition with ContextDefinition with EntityDefinition with DomainDefinition

//////////////////////////////////////////////////////////// EXPRESSIONS
  sealed trait Expression extends RiddlValue

  case class UnknownExpression(loc: Location) extends Expression

  case class FieldExpression(loc: Location, path: PathIdentifier) extends Expression

  case class GroupExpression(loc: Location, expression: Expression) extends Expression

  case class FunctionCallExpression(
    loc: Location,
    name: Identifier,
    arguments: ListMap[Identifier, Expression])
      extends Expression

  case class MathExpression(
    loc: Location,
    operator: String,
    arguments: ListMap[Identifier, Expression])
      extends Expression

  case class LiteralInteger(loc: Location, n: BigInt) extends Expression
  case class LiteralDecimal(loc: Location, d: BigDecimal) extends Expression

  // ////////////////////////////////////////////////////////// TOPICS

  case class TopicRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  case class Topic(
    loc: Location,
    id: Identifier,
    commands: Seq[Command] = Seq.empty[Command],
    events: Seq[Event] = Seq.empty[Event],
    queries: Seq[Query] = Seq.empty[Query],
    results: Seq[Result] = Seq.empty[Result],
    description: Option[Description] = None)
      extends Container with DomainDefinition {

    lazy val contents: Seq[Definition] = (commands.iterator ++ events ++ queries ++ results).toList
  }

  sealed trait MessageReference extends Reference

  sealed trait MessageDefinition extends Container {
    def typ: TypeExpression

    override def contents: Seq[Definition] = typ match {
      case a: Aggregation => a.fields
      case _              => Seq.empty[Definition]
    }
  }

  case class CommandRef(
    loc: Location,
    id: PathIdentifier)
      extends MessageReference

  case class Command(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    events: EventRefs,
    description: Option[Description] = None)
      extends MessageDefinition

  case class EventRef(
    loc: Location,
    id: PathIdentifier)
      extends MessageReference

  type EventRefs = Seq[EventRef]

  case class Event(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    description: Option[Description] = None)
      extends MessageDefinition

  case class QueryRef(
    loc: Location,
    id: PathIdentifier)
      extends MessageReference

  case class Query(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    result: ResultRef,
    description: Option[Description] = None)
      extends MessageDefinition

  case class ResultRef(
    loc: Location,
    id: PathIdentifier)
      extends MessageReference

  case class Result(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    description: Option[Description] = None)
      extends MessageDefinition

  sealed trait EntityValue extends RiddlValue

  sealed abstract class EntityOption(val name: String) extends EntityValue

  case class EntityEventSourced(loc: Location) extends EntityOption("event sourced")
  case class EntityValueOption(loc: Location) extends EntityOption("value")
  case class EntityAggregate(loc: Location) extends EntityOption("aggregate")
  case class EntityPersistent(loc: Location) extends EntityOption("persistent")
  case class EntityConsistent(loc: Location) extends EntityOption("consistent")
  case class EntityAvailable(loc: Location) extends EntityOption("available")
  case class EntityFiniteStateMachine(loc: Location) extends EntityOption("finite state machine")

  sealed abstract class EntityKind(@unused name: String) extends EntityValue

  case class ConceptEntityKind(loc: Location) extends EntityKind("concept")
  case class DeviceEntityKind(loc: Location) extends EntityKind("device")
  case class ActorEntityKind(loc: Location) extends EntityKind("actor")
  case class UserEntityKind(loc: Location) extends EntityKind("role")

  case class EntityRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  sealed trait FeatureValue extends RiddlValue
  case class Given(loc: Location, fact: Seq[LiteralString]) extends FeatureValue

  case class When(loc: Location, situation: Seq[LiteralString]) extends FeatureValue

  case class Then(loc: Location, result: Seq[LiteralString]) extends FeatureValue

  case class Else(loc: Location, otherwise: Seq[LiteralString]) extends FeatureValue

  case class Background(loc: Location, givens: Seq[Given] = Seq.empty[Given]) extends FeatureValue

  case class Example(
    loc: Location,
    id: Identifier,
    givens: Seq[Given] = Seq.empty[Given],
    whens: Seq[When] = Seq.empty[When],
    thens: Seq[Then] = Seq.empty[Then],
    elses: Seq[Else] = Seq.empty[Else],
    description: Option[Description] = None)
      extends Definition

  case class FeatureRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  case class Feature(
    loc: Location,
    id: Identifier,
    background: Option[Background] = None,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None)
      extends Container with EntityDefinition {
    lazy val contents: Seq[Definition] = examples
  }

  case class FunctionRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  case class Function(
    loc: Location,
    id: Identifier,
    input: Option[TypeExpression],
    output: TypeExpression,
    description: Option[Description])
      extends EntityDefinition {}

  case class InvariantRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  case class Invariant(
    loc: Location,
    id: Identifier,
    expression: Seq[LiteralString],
    description: Option[Description] = None)
      extends EntityDefinition {}

  sealed trait OnClauseStatement extends RiddlValue {
    def description: Option[Description]
  }

  case class SetStatement(
    loc: Location,
    target: PathIdentifier,
    value: Expression,
    description: Option[Description] = None)
      extends OnClauseStatement

  case class AppendStatement(
    loc: Location,
    value: PathIdentifier,
    target: Identifier,
    description: Option[Description] = None)
      extends OnClauseStatement

  case class MessageConstructor(
    msg: MessageReference,
    args: ListMap[Identifier, Expression])

  case class PublishStatement(
    loc: Location,
    msg: MessageConstructor,
    topic: TopicRef,
    description: Option[Description] = None)
      extends OnClauseStatement

  case class SendStatement(
    loc: Location,
    msg: MessageConstructor,
    entity: EntityRef,
    description: Option[Description] = None)
      extends OnClauseStatement

  case class RemoveStatement(
    loc: Location,
    id: PathIdentifier,
    from: PathIdentifier,
    description: Option[Description] = None)
      extends OnClauseStatement

  case class ExecuteStatement(
    loc: Location,
    id: Identifier,
    description: Option[Description] = None)
      extends OnClauseStatement

  sealed trait Condition extends RiddlValue
  case class True(loc: Location) extends Condition
  case class False(loc: Location) extends Condition

  case class ExpressionCondition(
    loc: Location,
    id: Identifier,
    args: ListMap[Identifier, Expression],
    description: Option[Description])
      extends Condition

  case class Comparison(
    loc: Location,
    op: String,
    left: Expression,
    right: Expression)
      extends Condition

  case class ReferenceCondition(loc: Location, ref: PathIdentifier) extends Condition

  case class Miscellaneous(
    loc: Location,
    description: Seq[LiteralString])
      extends Condition

  abstract class UnaryCondition extends Condition {
    def cond1: Condition
  }

  case class NotCondition(loc: Location, cond1: Condition) extends UnaryCondition

  abstract class BinaryCondition extends UnaryCondition {
    def cond2: Condition
  }

  case class AndCondition(loc: Location, cond1: Condition, cond2: Condition) extends BinaryCondition

  case class OrCondition(loc: Location, cond1: Condition, cond2: Condition) extends BinaryCondition

  case class EqualityCondition(
    loc: Location,
    cond1: Condition,
    cond2: Condition)
      extends BinaryCondition

  case class InequalityCondition(
    loc: Location,
    cond1: Condition,
    cond2: Condition)
      extends BinaryCondition

  case class WhenStatement(
    loc: Location,
    condition: Condition,
    actions: Seq[OnClauseStatement] = Seq.empty[OnClauseStatement],
    description: Option[Description] = None)
      extends OnClauseStatement

  case class OnClause(
    loc: Location,
    msg: MessageReference,
    actions: Seq[OnClauseStatement] = Seq.empty[OnClauseStatement],
    description: Option[Description] = None)
      extends EntityValue with DefinitionWithDescription

  case class OnMessage(
    loc: Location,
    msg: MessageReference,
    actions: Seq[OnClauseStatement] = Seq.empty[OnClauseStatement],
    description: Option[Description] = None)
      extends EntityValue

  case class Consumer(
    loc: Location,
    id: Identifier,
    topic: TopicRef,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    description: Option[Description] = None)
      extends EntityDefinition

  case class Handler(
    loc: Location,
    id: Identifier,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    description: Option[Description] = None)
      extends EntityDefinition

  case class State(
    loc: Location,
    id: Identifier,
    typeEx: TypeExpression,
    description: Option[Description] = None)
      extends EntityDefinition with Container {

    override def contents: Seq[Definition] = typeEx match {
      case Aggregation(_, fs, _) => fs
      case _                     => Seq.empty[Definition]
    }
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
    * @param features
    *   Feature definitions of the entity
    * @param functions
    *   Utility functions defined for the entity
    * @param invariants
    *   Invariant properties of the entity
    */
  case class Entity(
    entityKind: EntityKind,
    loc: Location,
    id: Identifier,
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    states: Seq[State] = Seq.empty[State],
    types: Seq[Type] = Seq.empty[Type],
    handlers: Seq[Handler] = Seq.empty[Handler],
    features: Seq[Feature] = Seq.empty[Feature],
    functions: Seq[Function] = Seq.empty[Function],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    description: Option[Description] = None)
      extends Container with ContextDefinition {

    lazy val contents: Seq[Definition] =
      (states.iterator ++ types ++ handlers ++ features ++ functions ++ invariants).toList

    def hasOption[Op <: EntityOption: scala.reflect.ClassTag]: Boolean = options
      .exists(_.getClass == implicitly[scala.reflect.ClassTag[Op]].runtimeClass)
  }

  trait TranslationRule extends Definition {
    def topic: String
  }

  case class MessageTranslationRule(
    loc: Location,
    id: Identifier,
    topic: String,
    input: String,
    output: String,
    rule: String,
    description: Option[Description] = None)
      extends TranslationRule

  /** Definition of an Adapter Adapters are defined in Contexts to convert messaging from one
    * Context to another. Adapters translate incoming events from other Contexts into commands or
    * events that its owning context can understand. There should be one Adapter for each external
    * Context
    *
    * @param loc
    *   Location in the parsing input
    * @param id
    *   Name of the adaptor
    */
  case class Adaptor(
    loc: Location,
    id: Identifier,
    ref: ContextRef,
    examples: Seq[Example],
    description: Option[Description] = None)
      extends Container with ContextDefinition {
    lazy val contents: Seq[Example] = examples
  }

  sealed trait ContextOption extends RiddlValue
  case class WrapperOption(loc: Location) extends ContextOption
  case class FunctionOption(loc: Location) extends ContextOption
  case class GatewayOption(loc: Location) extends ContextOption

  case class ContextRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  case class Context(
    loc: Location,
    id: Identifier,
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    types: Seq[Type] = Seq.empty[Type],
    entities: Seq[Entity] = Seq.empty[Entity],
    adaptors: Seq[Adaptor] = Seq.empty[Adaptor],
    interactions: Seq[Interaction] = Seq.empty[Interaction],
    description: Option[Description] = None)
      extends Container with DomainDefinition {
    lazy val contents: Seq[Definition] = types ++ entities ++ adaptors ++ interactions
  }

  case class Pipe(
    loc: Location,
    id: Identifier,
    transmitType: TypeRef,
    description: Option[Description] = None)
      extends Definition

  case class PipeRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  trait Streamlet extends Definition

  case class Inlet(
    loc: Location,
    id: Identifier,
    type_ : TypeRef,
    description: Option[Description] = None)
      extends Streamlet

  case class Outlet(
    loc: Location,
    id: Identifier,
    type_ : TypeRef,
    description: Option[Description] = None)
      extends Streamlet

  case class Processor(
    loc: Location,
    id: Identifier,
    inlets: Seq[Inlet],
    outlets: Seq[Outlet],
    description: Option[Description] = None)
      extends Definition

  trait JointDefinition extends Definition

  case class Joint(
    loc: Location,
    id: Identifier,
    streamLet: DefRef[Streamlet],
    pipe: DefRef[Pipe],
    description: Option[Description] = None)
      extends JointDefinition

  case class Plant(
    loc: Location,
    id: Identifier,
    pipes: Seq[Pipe] = Seq.empty[Pipe],
    processors: Seq[Processor] = Seq.empty[Processor],
    joints: Seq[Joint] = Seq.empty[Joint],
    description: Option[Description] = None)
      extends Container with DomainDefinition {
    def contents: Seq[Definition] = pipes ++ processors
  }

  /** Definition of an Interaction Interactions define an exemplary interaction between the system
    * being designed and other actors. The basic ideas of an Interaction are much like UML Sequence
    * Diagram.
    *
    * @param loc
    *   Where in the input the Scenario is defined
    * @param id
    *   The name of the scenario
    * @param actions
    *   The actions that constitute the interaction
    */
  case class Interaction(
    loc: Location,
    id: Identifier,
    actions: Seq[ActionDefinition] = Seq.empty[ActionDefinition],
    description: Option[Description] = None)
      extends Container with DomainDefinition with ContextDefinition {
    lazy val contents: Seq[Definition] = actions
  }

  sealed trait RoleOption extends RiddlValue
  case class HumanOption(loc: Location) extends RoleOption
  case class DeviceOption(loc: Location) extends RoleOption

  sealed trait ActionDefinition extends Definition {
    def reactions: Seq[Reaction]
  }

  /** Used to capture reactions to actions. Actions include reactions in their definition to model
    * the precipitating reactions to the action.
    */
  case class Reaction(
    loc: Location,
    id: Identifier,
    entity: EntityRef,
    function: FunctionRef,
    arguments: Seq[LiteralString],
    description: Option[Description] = None)
      extends DefinitionWithDescription

  case class ReactionRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  type Actions = Seq[ActionDefinition]

  sealed trait MessageOption extends RiddlValue
  case class SynchOption(loc: Location) extends MessageOption
  case class AsynchOption(loc: Location) extends MessageOption
  case class ReplyOption(loc: Location) extends MessageOption

  /** An Interaction based on entity messaging between two entities in the system.
    * @param options
    *   Options for the message
    * @param loc
    *   Where the message is located in the input
    * @param id
    *   The displayable text that describes the interaction
    * @param sender
    *   A reference to the entity sending the message
    * @param receiver
    *   A reference to the entity receiving the message
    * @param message
    *   A reference to the kind of message sent & received
    */
  case class MessageAction(
    loc: Location,
    id: Identifier,
    options: Seq[MessageOption] = Seq.empty[MessageOption],
    sender: EntityRef,
    receiver: EntityRef,
    message: MessageReference,
    reactions: Seq[Reaction],
    description: Option[Description] = None)
      extends ActionDefinition

  case class DomainRef(
    loc: Location,
    id: PathIdentifier)
      extends Reference

  case class Domain(
    loc: Location,
    id: Identifier,
    types: Seq[Type] = Seq.empty[Type],
    topics: Seq[Topic] = Seq.empty[Topic],
    contexts: Seq[Context] = Seq.empty[Context],
    interactions: Seq[Interaction] = Seq.empty[Interaction],
    plants: Seq[Plant] = Seq.empty[Plant],
    domains: Seq[Domain] = Seq.empty[Domain],
    description: Option[Description] = None)
      extends Container with DomainDefinition {

    lazy val contents: Seq[DomainDefinition] =
      (types.iterator ++ topics ++ contexts ++ interactions ++ plants).toList
  }
}
