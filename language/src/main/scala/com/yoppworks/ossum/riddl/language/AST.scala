package com.yoppworks.ossum.riddl.language

import scala.annotation.unused
import scala.collection.immutable.ListMap
import scala.language.implicitConversions
import scala.reflect.ClassTag

// scalastyle:off number.of.methods

/** Abstract Syntax Tree This object defines the model for processing RIDDL and producing a raw AST
  * from it. This raw AST has no referential integrity, it just results from applying the parsing
  * rules to the input. The RawAST models produced from parsing are syntactically correct but have
  * no semantic validation. The Transformation passes convert RawAST model to AST model which is
  * referentially and semantically consistent (or the user gets an error).
  */
object AST {

  /** The root trait of all things RIDDL AST */
  sealed trait RiddlNode {
    def kind: String = this.getClass.getSimpleName.toLowerCase()

    def format: String = ""
  }

  final val defaultSourceName = "default"

  /** A location of an item in the input */
  case class Location(
    line: Int = 0,
    col: Int = 0,
    source: String = defaultSourceName)
      extends Ordered[Location] {
    override def toString: String = { s"$source($line:$col)" }

    override def compare(that: Location): Int = {
      if (that.line == line) {
        if (this.col == that.col) { this.source.compare(that.source) }
        else { this.col - that.col }
      } else { this.line - that.line }
    }
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
  }

  case class Identifier(loc: Location, value: String) extends RiddlValue {
    override def format: String = value
  }

  case class RiddlOption(loc: Location, name: String) extends RiddlValue

  case class LiteralString(loc: Location, s: String) extends RiddlValue {
    override def format = s"\"$s\""
  }

  case class PathIdentifier(loc: Location, value: Seq[String]) extends RiddlValue {
    override def format: String = { if (value.isEmpty) "." else value.reverse.mkString(".") }
  }

  case class Description(
    loc: Location = 0 -> 0,
    lines: Seq[LiteralString] = Seq.empty[LiteralString])
      extends RiddlValue

  sealed trait Reference extends RiddlValue {
    def id: PathIdentifier
  }

  sealed trait DescribedValue extends DefinitionValue {
    def description: Option[Description]
  }

  sealed trait DefinitionValue extends RiddlValue

  sealed trait ContainerValue[+CT <: Definition] extends DefinitionValue {
    def contents: Seq[CT]
  }

  sealed trait Definition extends DescribedValue {
    def id: Identifier

    def identify: String = s"$kind '${id.value}'"
  }

  sealed trait AdaptorDefinition extends Definition

  sealed trait ContextDefinition extends Definition

  sealed trait DomainDefinition extends Definition
  sealed trait EntityDefinition extends Definition

  sealed trait Container[+CV <: Definition] extends Definition with ContainerValue[CV]

  case class RootContainer(domains: Seq[Domain]) extends Container[Domain] {
    def id: Identifier = Identifier((0, 0), "root")

    def description: Option[Description] = None

    def loc: Location = (0, 0)

    override def contents: Seq[Domain] = domains
  }

  object RootContainer {
    val empty: RootContainer = RootContainer(Seq.empty[Domain])
  }

  trait DefRef[+T <: Definition] extends Reference {
    def loc: Location

    def id: PathIdentifier
  }

  // ////////////////////////////////////////////////////////// TYPES

  sealed trait TypeExpression extends DefinitionValue

  sealed trait TypeContainer extends TypeExpression with ContainerValue[Type]

  case class TypeRef(loc: Location, id: PathIdentifier) extends DefRef[Type] with TypeExpression {
    override def format: String = s"type ${id.format}"
  }

  sealed trait MessageKind {
    lazy val kind: String = this.getClass.getSimpleName.dropRight("Kind".length + 1).toLowerCase
  }

  case object CommandKind extends MessageKind

  case object EventKind extends MessageKind

  case object QueryKind extends MessageKind

  case object ResultKind extends MessageKind

  sealed trait MessageRef extends DefRef[Type] {
    def messageKind: MessageKind
  }

  case class CommandRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = CommandKind

    override def format: String = s"command ${id.format}"
  }
  case class EventRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = EventKind

    override def format: String = s"event ${id.format}"
  }
  case class QueryRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = QueryKind

    override def format: String = s"query ${id.format}"
  }
  case class ResultRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = ResultKind

    override def format: String = s"result ${id.format}"
  }

  case class Optional(loc: Location, typeExp: TypeExpression) extends TypeExpression

  case class ZeroOrMore(loc: Location, typeExp: TypeExpression) extends TypeExpression

  case class OneOrMore(loc: Location, typeExp: TypeExpression) extends TypeExpression

  /** Represents one variant among (one or) many variants that comprise an [[Enumeration]]
    *
    * @param id
    *   the identifier (name) of the Enumerator
    * @param enumVal
    *   the optional int value
    * @param description
    *   the description of the enumerator. Each Enumerator in an enumeration may define independent
    *   descriptions
    */
  case class Enumerator(
    loc: Location,
    id: Identifier,
    enumVal: Option[LiteralInteger] = None,
    description: Option[Description] = None)
      extends Definition

  case class Enumeration(
    loc: Location,
    of: Seq[Enumerator],
    description: Option[Description] = None)
      extends TypeExpression with ContainerValue[Enumerator] {
    lazy val contents: Seq[Enumerator] = of
  }

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
      extends TypeExpression with ContainerValue[Field] {
    lazy val contents: Seq[Field] = fields
  }

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

  case class MessageType(
    loc: Location,
    messageKind: MessageKind,
    fields: Seq[Field] = Seq.empty[Field],
    description: Option[Description] = None)
      extends TypeExpression with EntityValue

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

  case class Type(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    description: Option[Description] = None)
      extends Definition with ContextDefinition with EntityDefinition with DomainDefinition

  // ////////////////////////////////////////////////////////// EXPRESSIONS
  sealed trait Expression extends RiddlValue

  case class UnknownExpression(loc: Location) extends Expression {
    override def format: String = "???"
  }

  case class FieldExpression(loc: Location, path: PathIdentifier) extends Expression {
    override def format: String = path.format
  }

  case class GroupExpression(loc: Location, expression: Expression) extends Expression {
    override def format: String = s"(${expression.format})"
  }

  case class FunctionCallExpression(
    loc: Location,
    name: Identifier,
    arguments: ListMap[Identifier, Expression])
      extends Expression {
    override def format: String = s""
  }

  case class MathExpression(
    loc: Location,
    operator: String,
    arguments: ListMap[Identifier, Expression])
      extends Expression

  case class LiteralInteger(loc: Location, n: BigInt) extends Expression {
    override def format: String = n.toString()
  }

  case class LiteralDecimal(loc: Location, d: BigDecimal) extends Expression {
    override def format: String = d.toString
  }

  // ////////////////////////////////////////////////////////// Entities

  sealed trait EntityValue extends RiddlValue

  trait OptionValue extends RiddlValue {
    def name: String

    override def format: String = name
  }

  trait OptionsDef[T <: OptionValue] extends RiddlValue {
    def options: Seq[T]

    def hasOption[OPT <: T: ClassTag]: Boolean = options
      .exists(_.getClass == implicitly[ClassTag[OPT]].runtimeClass)

    override def format: String = {
      options.size match {
        case 0               => ""
        case 1               => s"option is ${options.head.format}"
        case x: Int if x > 1 => s"options ( ${options.map(_.format).mkString(" ", ", ", " )")}"
      }
    }
  }

  sealed abstract class EntityOption(val name: String) extends EntityValue with OptionValue

  case class EntityEventSourced(loc: Location) extends EntityOption("event sourced")

  case class EntityValueOption(loc: Location) extends EntityOption("value")

  case class EntityAggregate(loc: Location) extends EntityOption("aggregate")

  case class EntityPersistent(loc: Location) extends EntityOption("persistent")

  case class EntityConsistent(loc: Location) extends EntityOption("consistent")

  case class EntityAvailable(loc: Location) extends EntityOption("available")

  case class EntityFiniteStateMachine(loc: Location) extends EntityOption("finite state machine")

  sealed abstract class EntityKind(
    @unused
    name: String)
      extends EntityValue

  case class ConceptEntityKind(loc: Location) extends EntityKind("concept")

  case class DeviceEntityKind(loc: Location) extends EntityKind("device")

  case class ActorEntityKind(loc: Location) extends EntityKind("actor")

  case class UserEntityKind(loc: Location) extends EntityKind("role")

  case class EntityRef(loc: Location, id: PathIdentifier) extends DefRef[Entity] {
    override def format: String = s"entity ${id.format}"
  }

  sealed trait ExampleValue extends RiddlValue

  case class GherkinClause(loc: Location, fact: Seq[LiteralString]) extends ExampleValue

  case class Example(
    loc: Location,
    id: Identifier,
    givens: Seq[GherkinClause] = Seq.empty[GherkinClause],
    whens: Seq[GherkinClause] = Seq.empty[GherkinClause],
    thens: Seq[GherkinClause] = Seq.empty[GherkinClause],
    buts: Seq[GherkinClause] = Seq.empty[GherkinClause],
    description: Option[Description] = None)
      extends Definition

  case class FeatureRef(loc: Location, id: PathIdentifier) extends DefRef[Feature] {
    override def format: String = s"feature ${id.format}"
  }

  case class Feature(
    loc: Location,
    id: Identifier,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None)
      extends Container[Example] with EntityDefinition with ContextDefinition {
    lazy val contents: Seq[Example] = examples
  }

  case class FunctionRef(loc: Location, id: PathIdentifier) extends DefRef[Function] {
    override def format: String = s"function ${id.format}"
  }

  case class Function(
    loc: Location,
    id: Identifier,
    input: Option[TypeExpression],
    output: Option[TypeExpression],
    examples: Seq[Example],
    description: Option[Description])
      extends Container[Example] with EntityDefinition {
    override lazy val contents: Seq[Example] = examples
  }

  case class InvariantRef(
    loc: Location,
    id: PathIdentifier)
      extends DefRef[Invariant] {
    override def format: String = s"invariant ${id.format}"
  }

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
      extends OnClauseStatement {
    override def format: String = { s"set ${target.format} to ${value.format}" }
  }

  case class AppendStatement(
    loc: Location,
    value: PathIdentifier,
    target: Identifier,
    description: Option[Description] = None)
      extends OnClauseStatement {
    override def format: String = s"append ${value.format} to ${target.format}"
  }

  case class MessageConstructor(
    msg: MessageRef,
    args: ListMap[Identifier, Expression])
      extends RiddlNode {
    override def format: String = msg.format + {
      if (args.nonEmpty) {
        args.map { case (id, exp) => id.format + "=" + exp.format }.mkString("(", ", ", ")")
      } else { "" }
    }
  }

  case class PublishStatement(
    loc: Location,
    msg: MessageConstructor,
    pipe: PipeRef,
    description: Option[Description] = None)
      extends OnClauseStatement {
    override def format: String = s"publish ${msg.format} to ${pipe.format}"
  }

  case class SendStatement(
    loc: Location,
    msg: MessageConstructor,
    entity: EntityRef,
    description: Option[Description] = None)
      extends OnClauseStatement {
    override def format: String = s"send ${msg.format} to ${entity.format}"
  }

  case class RemoveStatement(
    loc: Location,
    id: PathIdentifier,
    from: PathIdentifier,
    description: Option[Description] = None)
      extends OnClauseStatement {
    override def format: String = s"remove ${id.format} from ${from.format}"
  }

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
      extends OnClauseStatement {
    override def format: String = {
      s"when ${condition.format} then ${actions.map(_.format).mkString("{", "\n", "}")}"
    }
  }

  case class OnClause(
    loc: Location,
    msg: MessageRef,
    actions: Seq[OnClauseStatement] = Seq.empty[OnClauseStatement],
    description: Option[Description] = None)
      extends EntityValue with DescribedValue

  case class Handler(
    loc: Location,
    id: Identifier,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    description: Option[Description] = None)
      extends EntityDefinition

  case class State(
    loc: Location,
    id: Identifier,
    typeEx: Aggregation,
    description: Option[Description] = None)
      extends EntityDefinition with Container[Field] {

    override def contents: Seq[Field] = typeEx match {
      case Aggregation(_, fs, _) => fs
      case _                     => Seq.empty[Field]
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
      extends Container[EntityDefinition] with ContextDefinition with OptionsDef[EntityOption] {

    lazy val contents: Seq[EntityDefinition] =
      (states.iterator ++ types ++ handlers ++ features ++ functions ++ invariants).toList

  }

  case class Adaptation(
    loc: Location,
    id: Identifier,
    event: EventRef,
    command: CommandRef,
    examples: Seq[Example],
    description: Option[Description] = None)
      extends AdaptorDefinition

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
    adaptations: Seq[Adaptation],
    description: Option[Description] = None)
      extends Container[Adaptation] with ContextDefinition {
    lazy val contents: Seq[Adaptation] = adaptations
  }

  sealed trait ContextOption extends OptionValue

  case class WrapperOption(loc: Location) extends ContextOption {
    def name: String = "wrapper"
  }

  case class FunctionOption(loc: Location) extends ContextOption {
    def name: String = "function"
  }

  case class GatewayOption(loc: Location) extends ContextOption {
    def name: String = "gateway"
  }

  case class ContextRef(loc: Location, id: PathIdentifier) extends DefRef[Context] {
    override def format: String = s"context ${id.format}"
  }

  case class Context(
    loc: Location,
    id: Identifier,
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    types: Seq[Type] = Seq.empty[Type],
    entities: Seq[Entity] = Seq.empty[Entity],
    adaptors: Seq[Adaptor] = Seq.empty[Adaptor],
    sagas: Seq[Saga] = Seq.empty[Saga],
    features: Seq[Feature] = Seq.empty[Feature],
    interactions: Seq[Interaction] = Seq.empty[Interaction],
    description: Option[Description] = None)
      extends Container[ContextDefinition] with DomainDefinition with OptionsDef[ContextOption] {
    lazy val contents: Seq[ContextDefinition] = types ++ entities ++ adaptors ++ sagas ++
      features ++ interactions
  }

  sealed trait PlantDefinition extends Definition

  case class Pipe(
    loc: Location,
    id: Identifier,
    transmitType: Option[TypeRef],
    description: Option[Description] = None)
      extends PlantDefinition

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
    examples: Seq[Example],
    description: Option[Description] = None)
      extends PlantDefinition with Container[Streamlet] {
    override def contents: Seq[Streamlet] = inlets ++ outlets
  }

  case class PipeRef(loc: Location, id: PathIdentifier) extends DefRef[Pipe] {
    override def format: String = s"pipe ${id.format}"
  }

  sealed trait StreamletRef extends Reference

  case class InletRef(loc: Location, id: PathIdentifier) extends StreamletRef with DefRef[Inlet] {
    override def format: String = s"inlet ${id.format}"
  }

  case class OutletRef(loc: Location, id: PathIdentifier) extends StreamletRef with DefRef[Outlet] {
    override def format: String = s"outlet ${id.format}"
  }

  case class Joint(
    loc: Location,
    id: Identifier,
    streamletRef: StreamletRef,
    pipe: PipeRef,
    description: Option[Description] = None)
      extends PlantDefinition

  case class Plant(
    loc: Location,
    id: Identifier,
    pipes: Seq[Pipe] = Seq.empty[Pipe],
    processors: Seq[Processor] = Seq.empty[Processor],
    joints: Seq[Joint] = Seq.empty[Joint],
    description: Option[Description] = None)
      extends Container[PlantDefinition] with DomainDefinition {
    lazy val contents: Seq[PlantDefinition] = pipes ++ processors ++ joints
  }

  case class SagaAction(
    loc: Location,
    id: Identifier,
    entity: EntityRef,
    doCommand: CommandRef,
    undoCommand: CommandRef,
    example: Seq[Example],
    description: Option[Description] = None)
      extends Definition

  sealed trait SagaOption extends OptionValue

  case class SequentialOption(loc: Location) extends SagaOption {
    def name: String = "sequential"
  }

  case class ParallelOption(loc: Location) extends SagaOption {
    def name: String = "sequential"
  }

  case class Saga(
    loc: Location,
    id: Identifier,
    options: Seq[SagaOption] = Seq.empty[SagaOption],
    input: Option[TypeExpression],
    output: Option[TypeExpression],
    sagaActions: Seq[SagaAction] = Seq.empty[SagaAction],
    description: Option[Description] = None)
      extends Container[SagaAction] with ContextDefinition with OptionsDef[SagaOption] {
    lazy val contents: Seq[SagaAction] = sagaActions
  }

  sealed trait InteractionOption extends OptionValue

  case class GatewayInteraction(loc: Location) extends InteractionOption {
    def name: String = "gateway"
  }

  sealed trait ActionDefinition extends Definition {
    def reactions: Seq[Reaction]
  }

  /** Definition of an Interaction
    *
    * Interactions define an exemplary interaction between the system being designed and other
    * actors. The basic ideas of an Interaction are much like UML Sequence Diagram.
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
    options: Seq[InteractionOption],
    actions: Seq[ActionDefinition] = Seq.empty[ActionDefinition],
    description: Option[Description] = None)
      extends Container[ActionDefinition]
      with DomainDefinition
      with ContextDefinition
      with OptionsDef[InteractionOption] {
    lazy val contents: Seq[ActionDefinition] = actions
  }

  sealed trait RoleOption extends RiddlValue

  case class HumanOption(loc: Location) extends RoleOption

  case class DeviceOption(loc: Location) extends RoleOption

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
      extends DescribedValue

  type Actions = Seq[ActionDefinition]

  sealed trait MessageOption extends OptionValue

  case class SynchOption(loc: Location) extends MessageOption {
    def name: String = "synch"
  }

  case class AsynchOption(loc: Location) extends MessageOption {
    def name: String = "async"
  }

  case class ReplyOption(loc: Location) extends MessageOption {
    def name: String = "reply"
  }

  /** An Interaction based on entity messaging between two entities in the system.
    *
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
    message: MessageRef,
    reactions: Seq[Reaction],
    description: Option[Description] = None)
      extends ActionDefinition with OptionsDef[MessageOption]

  case class DomainRef(loc: Location, id: PathIdentifier) extends DefRef[Domain] {
    override def format: String = s"domain ${id.format}"
  }

  case class Domain(
    loc: Location,
    id: Identifier,
    types: Seq[Type] = Seq.empty[Type],
    contexts: Seq[Context] = Seq.empty[Context],
    interactions: Seq[Interaction] = Seq.empty[Interaction],
    plants: Seq[Plant] = Seq.empty[Plant],
    domains: Seq[Domain] = Seq.empty[Domain],
    description: Option[Description] = None)
      extends Container[DomainDefinition] with DomainDefinition {

    lazy val contents: Seq[DomainDefinition] =
      (domains ++ types.iterator ++ contexts ++ interactions ++ plants).toList
  }
}
