package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.Terminals.Keywords

import scala.collection.immutable.ListMap
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
    def format: String = ""

    def isEmpty: Boolean = false

    @deprecatedOverriding("nonEmpty is defined as !isEmpty; override isEmpty instead")
    def nonEmpty: Boolean = !isEmpty
  }

  /** */
  sealed trait RiddlValue extends RiddlNode {
    def loc: Location
  }

  case class Identifier(loc: Location, value: String) extends RiddlValue {
    override def format: String = value

    override def isEmpty: Boolean = value.isEmpty
  }

  case class RiddlOption(loc: Location, name: String) extends RiddlValue

  case class LiteralString(loc: Location, s: String) extends RiddlValue {
    override def format = s"\"$s\""

    override def isEmpty: Boolean = s.isEmpty
  }

  case class PathIdentifier(loc: Location, value: Seq[String]) extends RiddlValue {
    override def format: String = {value.reverse.mkString(".")}

    override def isEmpty: Boolean = value.isEmpty || value.forall(_.isEmpty)
  }

  case class Description(
    loc: Location = 0 -> 0,
    lines: Seq[LiteralString] = Seq.empty[LiteralString])
    extends RiddlValue {
    override def isEmpty: Boolean = lines.isEmpty || lines.forall(_.s.isEmpty)
  }

  sealed trait Reference extends RiddlValue {
    def id: PathIdentifier

    override def isEmpty: Boolean = id.isEmpty
  }

  trait DefRef[+T <: Definition] extends Reference

  sealed trait DescribedValue extends DefinitionValue {
    def description: Option[Description]
  }

  sealed trait DefinitionValue extends RiddlValue

  sealed trait ContainerValue[+CT <: Definition] extends DefinitionValue {
    def contents: Seq[CT]

    override def isEmpty: Boolean = contents.isEmpty
  }

  def keyword(definition: Definition): String = {
    definition match {
      case _: Adaptation => Keywords.adapt
      case _: Adaptor => Keywords.adaptor
      case _: Context => Keywords.context
      case _: Domain => Keywords.domain
      case _: Entity => Keywords.entity
      case _: Enumerator => ""
      case _: Example => Keywords.example
      case _: Feature => Keywords.feature
      case _: Field => ""
      case _: Function => Keywords.function
      case _: Handler => Keywords.handler
      case _: Inlet => Keywords.inlet
      case _: Interaction => Keywords.interaction
      case _: Invariant => Keywords.invariant
      case _: Joint => Keywords.joint
      case _: MessageAction => Keywords.message
      case _: Outlet => Keywords.outlet
      case _: Pipe => Keywords.pipe
      case _: Plant => Keywords.plant
      case _: Processor => Keywords.processor
      case _: RootContainer => ""
      case _: Saga => Keywords.saga
      case _: SagaAction => Keywords.action
      case _: State => Keywords.state
      case _: Type => Keywords.`type`
      case _ => ""
    }
  }

  def kind(definition: Definition): String = {
    definition match {
      case _: Adaptation => "Adaptation"
      case _: Adaptor => "Adaptor"
      case _: Context => "Context"
      case _: Domain => "Domain"
      case _: Entity => "Entity"
      case _: Enumerator => "Enumerator"
      case _: Example => "Example"
      case _: Feature => "Feature"
      case _: Field => "Field"
      case _: Function => "Function"
      case _: Handler => "Handler"
      case _: Inlet => "Inlet"
      case _: Interaction => "Interaction"
      case _: Invariant => "Invariant"
      case _: Joint => "Joint"
      case _: MessageAction => "Message"
      case _: Outlet => "Outlet"
      case _: Pipe => "Pipe"
      case _: Plant => "Plant"
      case _: Processor => "Processor"
      case _: RootContainer => ""
      case _: Saga => "Saga"
      case _: SagaAction => "SagaAction"
      case _: State => "State"
      case _: Type => "Type"
      case _ => "Definition"
    }
  }

  sealed trait Definition extends DescribedValue {
    def id: Identifier

    def identify: String = s"${AST.kind(this)} '${id.value}'"
  }

  sealed trait AdaptorDefinition extends Definition

  sealed trait ContextDefinition extends Definition

  sealed trait DomainDefinition extends Definition

  sealed trait EntityDefinition extends Definition

  sealed trait Container[+CV <: Definition] extends Definition with ContainerValue[CV]

  case class RootContainer(domains: Seq[Domain]) extends Container[Domain] {

    def id: Identifier = Identifier((0, 0), "<file root>")

    def description: Option[Description] = None

    def loc: Location = (0, 0)

    override def contents: Seq[Domain] = domains
  }

  object RootContainer {
    val empty: RootContainer = RootContainer(Seq.empty[Domain])
  }

  // ////////////////////////////////////////////////////////// TYPES

  sealed trait TypeExpression extends DefinitionValue

  def kind(te: TypeExpression): String = {
    te match {
      case TypeRef(_, id) => s"Reference To ${id.format}"
      case Optional(_, typeExp) => kind(typeExp) + "?"
      case ZeroOrMore(_, typeExp) => kind(typeExp) + "*"
      case OneOrMore(_, typeExp) => kind(typeExp) + "+"
      case _: Enumeration => "Enumeration"
      case _: Alternation => "Alternation"
      case _: Aggregation => "Aggregation"
      case Mapping(_, from, to) => s"Map From ${kind(from)} To ${kind(to)}"
      case RangeType(_, min, max) => s"Range($min,$max)"
      case ReferenceType(_, entity) => s"Reference To Entity ${entity.id.format}"
      case _: Pattern => s"Pattern"
      case UniqueId(_, entityPath) => s"Id(${entityPath.format})"
      case MessageType(_, messageKind, _) => messageKind.kind
      case predefinedType: PredefinedType => predefinedType.kind
      case _ => "<unknown type expression>"
    }
  }

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
    enumerators: Seq[Enumerator])
      extends TypeExpression with ContainerValue[Enumerator] {
    lazy val contents: Seq[Enumerator] = enumerators
  }

  case class Alternation(
    loc: Location,
    of: Seq[TypeExpression])
      extends TypeExpression

  case class Field(
    loc: Location,
    id: Identifier,
    typeEx: TypeExpression,
    description: Option[Description] = None)
    extends Definition {}

  case class Aggregation(
    loc: Location,
    fields: Seq[Field] = Seq.empty[Field])
      extends TypeExpression with ContainerValue[Field] {
    lazy val contents: Seq[Field] = fields
  }

  case class Mapping(
    loc: Location,
    from: TypeExpression,
    to: TypeExpression)
      extends TypeExpression

  case class RangeType(
    loc: Location,
    min: LiteralInteger,
    max: LiteralInteger)
      extends TypeExpression

  case class ReferenceType(
    loc: Location,
    entity: EntityRef)
      extends TypeExpression

  case class Pattern(
    loc: Location,
    pattern: Seq[LiteralString])
      extends TypeExpression

  case class UniqueId(
    loc: Location,
    entityPath: PathIdentifier)
      extends TypeExpression

  case class MessageType(
    loc: Location,
    messageKind: MessageKind,
    fields: Seq[Field] = Seq.empty[Field])
      extends TypeExpression with EntityValue

  abstract class PredefinedType extends TypeExpression {
    def loc: Location

    def kind: String
  }

  object PredefinedType {
    final def unapply(preType: PredefinedType): Option[String] = Some(preType.kind)
  }

  case class Strng(
    loc: Location,
    min: Option[LiteralInteger] = None,
    max: Option[LiteralInteger] = None)
    extends PredefinedType {
    override lazy val kind: String = "String"
  }

  case class Bool(loc: Location) extends PredefinedType {
    override lazy val kind: String = "Boolean"
  }

  case class Number(loc: Location) extends PredefinedType {
    def kind: String = "Number"
  }

  case class Integer(loc: Location) extends PredefinedType {
    def kind: String = "Integer"
  }

  case class Decimal(loc: Location) extends PredefinedType {
    def kind: String = "Decimal"
  }

  case class Real(loc: Location) extends PredefinedType {
    def kind: String = "Real"
  }

  case class Date(loc: Location) extends PredefinedType {
    def kind: String = "Date"
  }

  case class Time(loc: Location) extends PredefinedType {
    def kind: String = "Time"
  }

  case class DateTime(loc: Location) extends PredefinedType {
    def kind: String = "DateTime"
  }

  case class TimeStamp(loc: Location) extends PredefinedType {
    def kind: String = "TimeStamp"
  }

  case class Duration(loc: Location) extends PredefinedType {
    def kind: String = "Duration"
  }

  case class UUID(loc: Location) extends PredefinedType {
    def kind: String = "UUID"
  }

  case class URL(loc: Location, Numbereme: Option[LiteralString] = None) extends PredefinedType {
    def kind: String = "URL"
  }

  case class LatLong(loc: Location) extends PredefinedType {
    def kind: String = "LatLong"
  }

  case class Nothing(loc: Location) extends PredefinedType {
    def kind: String = "Nothing"
  }

  case class Type(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    description: Option[Description] = None)
    extends Definition with ContextDefinition with EntityDefinition with DomainDefinition {}

  // ///////////////////////////////// ///////////////////////// VALUE EXPRESSIONS
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

  case class ArgList(
                      args: ListMap[Identifier, Expression] = ListMap.empty[Identifier, Expression]
                    ) extends RiddlNode {
    override def format: String = args.map { case (id, exp) =>
      id.format + "=" + exp.format
    }.mkString("(", ", ", ")")
  }

  case class FunctionCallExpression(
                                     loc: Location,
                                     name: PathIdentifier,
                                     arguments: ArgList)
    extends Expression {
    override def format: String = name.format + arguments.format
  }

  case class MathExpression(
                             loc: Location,
                             operator: String,
                             arguments: ArgList
                           ) extends Expression {
    override def format: String = operator + arguments.format
  }

  case class LiteralInteger(loc: Location, n: BigInt) extends Expression {
    override def format: String = n.toString()
  }

  case class LiteralDecimal(loc: Location, d: BigDecimal) extends Expression {
    override def format: String = d.toString
  }

  ///////////////////////////////////////////////////////////// Conditional Expressions

  sealed trait Condition extends RiddlValue

  case class True(loc: Location) extends Condition

  case class False(loc: Location) extends Condition

  case class StringCondition(cond: LiteralString) extends Condition {
    override def loc: Location = cond.loc
  }

  case class FunctionCallCondition(
                                    loc: Location,
                                    id: PathIdentifier,
                                    args: ArgList
                                  ) extends Condition

  sealed trait Comparator extends RiddlNode

  case object lt extends Comparator

  case object gt extends Comparator

  case object le extends Comparator

  case object ge extends Comparator

  case object eq extends Comparator

  case object ne extends Comparator

  case class Comparison(
                         loc: Location,
                         op: Comparator,
                         left: Condition,
                         right: Condition)
    extends Condition

  case class ReferenceCondition(loc: Location, ref: PathIdentifier) extends Condition

  abstract class UnaryCondition extends Condition {
    def cond1: Condition
  }

  case class NotCondition(loc: Location, cond1: Condition) extends UnaryCondition

  abstract class BinaryCondition extends Condition {
    def cond1: Condition

    def cond2: Condition
  }

  case class AndCondition(loc: Location, cond1: Condition, cond2: Condition) extends BinaryCondition

  case class OrCondition(loc: Location, cond1: Condition, cond2: Condition) extends BinaryCondition


  // ////////////////////////////////////////////////////////// Entities

  sealed trait EntityValue extends RiddlValue

  trait OptionValue extends RiddlValue {
    def name: String

    def args: Seq[LiteralString] = Seq.empty[LiteralString]

    override def format: String = name + args.map(_.format).mkString("(", ", ", ")")
  }

  trait OptionsDef[T <: OptionValue] extends RiddlValue {
    def options: Seq[T]

    def hasOption[OPT <: T : ClassTag]: Boolean = options
      .exists(_.getClass == implicitly[ClassTag[OPT]].runtimeClass)

    override def format: String = {
      options.size match {
        case 0 => ""
        case 1 => s"option is ${options.head.format}"
        case x: Int if x > 1 => s"options ( ${options.map(_.format).mkString(" ", ", ", " )")}"
      }
    }

    override def isEmpty: Boolean = options.isEmpty
  }

  sealed abstract class EntityOption(val name: String) extends EntityValue with OptionValue

  case class EntityEventSourced(loc: Location) extends EntityOption("event sourced")

  case class EntityValueOption(loc: Location) extends EntityOption("value")

  case class EntityAggregate(loc: Location) extends EntityOption("aggregate")

  case class EntityPersistent(loc: Location) extends EntityOption("persistent")

  case class EntityConsistent(loc: Location) extends EntityOption("consistent")

  case class EntityAvailable(loc: Location) extends EntityOption("available")

  case class EntityFiniteStateMachine(loc: Location) extends EntityOption("finite state machine")

  case class EntityKind(loc: Location, override val args: Seq[LiteralString]) extends
    EntityOption("kind")

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
    extends ProcessorDefinition {
    override def isEmpty: Boolean = givens.isEmpty && whens.isEmpty && thens.isEmpty && buts.isEmpty
  }

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
    input: Option[TypeExpression] = None,
    output: Option[TypeExpression] = None,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None)
      extends Container[Example] with EntityDefinition {
    override lazy val contents: Seq[Example] = examples

    override def isEmpty: Boolean = examples.isEmpty && input.isEmpty && output.isEmpty
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
    expression: Seq[LiteralString] = Seq.empty[LiteralString],
    description: Option[Description] = None)
    extends EntityDefinition {
    override def isEmpty: Boolean = expression.isEmpty
  }

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
                                 args: ArgList = ArgList())
      extends RiddlNode {
    override def format: String = msg.format + {
      if (args.nonEmpty) {
        args.format
      } else {
        ""
      }
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
    extends EntityDefinition {
    override def isEmpty: Boolean = super.isEmpty && clauses.isEmpty
  }

  case class State(
    loc: Location,
    id: Identifier,
    typeEx: Aggregation,
    description: Option[Description] = None)
      extends EntityDefinition with Container[Field] {

    override def contents: Seq[Field] = typeEx.fields

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
    description: Option[Description] = None)
      extends Container[EntityDefinition] with ContextDefinition with OptionsDef[EntityOption] {

    lazy val contents: Seq[EntityDefinition] = {
      (states ++ types ++ handlers ++ functions ++ invariants).toList
    }

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty
  }

  case class Adaptation(
    loc: Location,
    id: Identifier,
    event: EventRef,
    command: CommandRef,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None)
    extends AdaptorDefinition with ContainerValue[Example] {
    override def contents: Seq[Example] = examples
  }

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
    adaptations: Seq[Adaptation] = Seq.empty[Adaptation],
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

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty
  }

  sealed trait PlantDefinition extends Definition

  case class Pipe(
    loc: Location,
    id: Identifier,
    transmitType: Option[TypeRef],
    description: Option[Description] = None)
    extends PlantDefinition

  trait ProcessorDefinition extends Definition

  trait Streamlet extends ProcessorDefinition

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
    extends PlantDefinition with Container[ProcessorDefinition] {
    override def contents: Seq[ProcessorDefinition] = inlets ++ outlets ++ examples
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
    extends Container[Example] {
    override def isEmpty: Boolean = example.isEmpty

    override def contents: Seq[Example] = example
  }

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

    override def isEmpty: Boolean = super.isEmpty && options.isEmpty && input.isEmpty &&
      output.isEmpty
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
    options: Seq[InteractionOption] = Seq.empty[InteractionOption],
    actions: Seq[ActionDefinition] = Seq.empty[ActionDefinition],
    description: Option[Description] = None)
      extends Container[ActionDefinition]
      with DomainDefinition
      with ContextDefinition
      with OptionsDef[InteractionOption] {
    lazy val contents: Seq[ActionDefinition] = actions

    override def isEmpty: Boolean = super.isEmpty && options.isEmpty
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
