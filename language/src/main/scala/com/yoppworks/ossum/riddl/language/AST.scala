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

  /** The root trait of all things RIDDL AST. Every node in the tree is a RiddlNode. */
  sealed trait RiddlNode {
    def format: String = ""

    def isEmpty: Boolean = false

    @deprecatedOverriding("nonEmpty is defined as !isEmpty; override isEmpty instead")
    def nonEmpty: Boolean = !isEmpty
  }

  /** The root trait of all parsable values. If a parser returns something, its a RiddlValue */
  sealed trait RiddlValue extends RiddlNode {
    def loc: Location
  }

  /**
   * A RiddlValue that is a parsed identifier, typically the name of a definition.
   *
   * @param loc   The location in the input where the identifier starts
   * @param value The parsed value of the identifer
   */
  case class Identifier(loc: Location, value: String) extends RiddlValue {
    override def format: String = value

    override def isEmpty: Boolean = value.isEmpty
  }

  /**
   * Represents a literal string parsed between quote characters in the input
   *
   * @param loc The location in the input of the opening quote character
   * @param s   The parsed value of the string content
   */
  case class LiteralString(loc: Location, s: String) extends Condition {
    override def format = s"\"$s\""

    override def isEmpty: Boolean = s.isEmpty
  }

  /**
   * Represents a segmented identifier to a definition in the model. Path Identifiers are parsed
   * from a dot-separated list of identifiers in the input. Path identifiers are used to
   * reference other definitions in the model.
   *
   * @param loc   Location in the input of the first letter of the path identifier
   * @param value The list of strings that make up the path identifer
   */
  case class PathIdentifier(loc: Location, value: Seq[String]) extends RiddlValue {
    override def format: String = {
      value.reverse.mkString(".")
    }

    override def isEmpty: Boolean = value.isEmpty || value.forall(_.isEmpty)
  }

  /**
   * The description of a definition. All definitions have a name and an optional description.
   * This class provides the description part.
   *
   * @param loc   The location in the input of the description
   * @param lines The lines of markdown that provide the description
   */
  case class Description(
    loc: Location = 0 -> 0,
    lines: Seq[LiteralString] = Seq.empty[LiteralString])
    extends RiddlValue {
    override def isEmpty: Boolean = lines.isEmpty || lines.forall(_.s.isEmpty)
  }

  /**
   * A reference to a definition of a specific type.
   *
   * @tparam T The type of definition to which the references refers.
   */
  sealed abstract class Reference[+T <: Definition] extends RiddlValue {
    def id: PathIdentifier

    override def isEmpty: Boolean = id.isEmpty
  }

  /**
   * Base trait of all values that have an optional Description
   */
  sealed trait DescribedValue extends RiddlValue {
    def description: Option[Description]
  }

  /**
   * Base trait of all values that contain definitions
   *
   * @tparam CT The contained type of definition
   */
  sealed trait ContainerValue[+CT <: Definition] extends RiddlValue {
    def contents: Seq[CT]

    override def isEmpty: Boolean = contents.isEmpty
  }

  /**
   * A function to translate between a definition and the keyword that introduces them.
   *
   * @param definition The definition to look up
   * @return A string providing the definition keyword, if any. Enumerators and fields don't have
   *         their own keywords
   */
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

  /**
   * A function to provide the kind of thing that a DescribedValue is
   *
   * @param definition The DescribedValue for which the kind is returned
   * @return A string for the kind of DescribedValue
   */
  def kind(definition: DescribedValue): String = {
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

  /**
   * Base trait for all definitions requiring an identifier for the definition and providing the
   * identify method to yield a string that provides the kind and name
   */
  sealed trait Definition extends DescribedValue {
    def id: Identifier

    def identify: String = s"${AST.kind(this)} '${id.format}'"
  }

  /**
   * Base trait of any definition that is in the content of an adaptor
   */
  sealed trait AdaptorDefinition extends Definition

  /**
   * Base trait of any definition that is in the content of a context
   */
  sealed trait ContextDefinition extends Definition

  /**
   * Base trait of any definition that is in the content of a domain
   */
  sealed trait DomainDefinition extends Definition

  /**
   * Base trait of any definition that is in the content of an entity.
   */
  sealed trait EntityDefinition extends Definition

  /** Base trait of any definition that is also a ContainerValue
   *
   * @tparam CV The kind of definition that is contained by the container
   */
  sealed trait Container[+CV <: Definition] extends Definition with ContainerValue[CV]

  /**
   * The root of the containment hierarchy, corresponding roughly to a level about a file.
   *
   * @param domains The sequence of domains contained by the root
   */
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

  /**
   * Base trait of an expression that defines a type
   */
  sealed trait TypeExpression extends RiddlValue

  /**
   * A utility function for getting the kind of a type expression.
   *
   * @param te The type expression to examine
   * @return A string indicating the kind corresponding to te
   */
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

  /**
   * Base trait for a type expression that is also a container value
   */
  sealed trait TypeContainer extends TypeExpression with ContainerValue[Type]

  /**
   * A reference to a type definition
   *
   * @param loc The location in the source where the reference to the type is made
   * @param id  The path identifier of the reference type
   */
  case class TypeRef(loc: Location, id: PathIdentifier) extends Reference[Type] with
    TypeExpression {
    override def format: String = s"type ${id.format}"
  }

  /** Base of an enumeration for the four kinds of message types */
  sealed trait MessageKind {
    def kind: String
  }

  /** An enumerator value for command types */
  final case object CommandKind extends MessageKind {
    def kind: String = "command"
  }

  /** An enumerator value for event types */
  final case object EventKind extends MessageKind {
    def kind: String = "event"
  }

  /** An enumerator value for query types */
  final case object QueryKind extends MessageKind {
    def kind: String = "query"
  }

  /** An enumerator value for result types */
  final case object ResultKind extends MessageKind {
    def kind: String = "result"
  }

  /** Base trait for the four kinds of message references */
  sealed trait MessageRef extends Reference[Type] {
    def messageKind: MessageKind

    override def format: String = s"${messageKind.kind} ${id.format}"
  }

  /** A Reference to a command type
   *
   * @param loc The location of the reference
   * @param id  The path identifier to the event type
   */
  case class CommandRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = CommandKind
  }

  /** A Reference to an event type
   *
   * @param loc The location of the reference
   * @param id  The path identifier to the event type
   */
  case class EventRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = EventKind
  }

  /** A reference to a query type
   *
   * @param loc The location of the reference
   * @param id  The path identifier to the query type
   */
  case class QueryRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = QueryKind
  }

  /** A reference to a result type
   *
   * @param loc The location of the reference
   * @param id  The path identifier to the result type
   */
  case class ResultRef(loc: Location, id: PathIdentifier) extends MessageRef {
    def messageKind: MessageKind = ResultKind
  }

  /** Base trait of the cardinality type expressions */
  sealed trait Cardinality extends TypeExpression

  /**
   * A cardinality type expression that indicates another type expression as being optional; that
   * is with a cardinality of 0 or 1.
   *
   * @param loc     The location of the optional cardinality
   * @param typeExp The type expression that is indicated as optional
   */
  case class Optional(loc: Location, typeExp: TypeExpression) extends Cardinality

  /**
   * A cardinality type expression that indicates another type expression as having zero or more
   * instances.
   *
   * @param loc     The location of the zero-or-more cardinality
   * @param typeExp The type expression that is indicated with a cardinality of zero or more.
   */
  case class ZeroOrMore(loc: Location, typeExp: TypeExpression) extends Cardinality

  /**
   * A cardinality type expression that indicates another type expression as having one or more
   * instances.
   *
   * @param loc     The location of the one-or-more cardinality
   * @param typeExp The type expression that is indicated with a cardinality of one or more.
   */
  case class OneOrMore(loc: Location, typeExp: TypeExpression) extends Cardinality

  /** Represents one variant among (one or) many variants that comprise an [[Enumeration]]
   *
   * @param id
   * the identifier (name) of the Enumerator
   * @param enumVal
   * the optional int value
   * @param description
   * the description of the enumerator. Each Enumerator in an enumeration may define independent
   * descriptions
   */
  case class Enumerator(
    loc: Location,
    id: Identifier,
    enumVal: Option[LiteralInteger] = None,
    description: Option[Description] = None)
    extends Definition

  /**
   * A type expression that defines its range of possible values as being one value from a set of
   * enumerated values.
   *
   * @param loc         The location of the enumeration type expression
   * @param enumerators The set of enumerators from which the value of this enumeration may be
   *                    chosen.
   */
  case class Enumeration(
    loc: Location,
    enumerators: Seq[Enumerator])
    extends TypeExpression with ContainerValue[Enumerator] {
    lazy val contents: Seq[Enumerator] = enumerators
  }

  /**
   * A type expression that that defines its range of possible values as being any one of the
   * possible values from a set of other type expressions.
   *
   * @param loc The location of the alternation type expression
   * @param of  The set of type expressions from which the value for this alternation may be chosen
   */
  case class Alternation(
    loc: Location,
    of: Seq[TypeExpression])
    extends TypeExpression

  /**
   * A definition that is a field of an aggregation type expressions. Fields associate an
   * identifier with a type expression.
   *
   * @param loc         The location of the field definition
   * @param id          The name of the field
   * @param typeEx      The type of the field
   * @param description An optional description of the field.
   */
  case class Field(
    loc: Location,
    id: Identifier,
    typeEx: TypeExpression,
    description: Option[Description] = None)
    extends Definition {}

  /**
   * A type expression that takes a set of named fields as its value.
   *
   * @param loc    The location of the aggregation definition
   * @param fields The fields of the aggregation
   */
  case class Aggregation(
    loc: Location,
    fields: Seq[Field] = Seq.empty[Field])
    extends TypeExpression with ContainerValue[Field] {
    lazy val contents: Seq[Field] = fields
  }

  /**
   * A type expressions that defines a mapping from a key to a value. The value of a mapping is
   * the set of mapped key -> value pairs, based on which keys have been provided values.
   *
   * @param loc  The location of the mapping type expression
   * @param from The type expression for the keys of the mapping
   * @param to   The type expression for the values of the mapping
   */
  case class Mapping(
    loc: Location,
    from: TypeExpression,
    to: TypeExpression)
    extends TypeExpression

  /**
   * A type expression that defines a set of integer values from a minimum value
   * to a maximum value, inclusively.
   *
   * @param loc The location of the range type expression
   * @param min The minimum value of the range
   * @param max The maximum value of the range
   */
  case class RangeType(
    loc: Location,
    min: LiteralInteger,
    max: LiteralInteger)
    extends TypeExpression

  /**
   * A type expression whose value is a reference to an entity.
   *
   * @param loc    The location of the reference type expression
   * @param entity The entity referenced by this type expression.
   */
  case class ReferenceType(
    loc: Location,
    entity: EntityRef)
    extends TypeExpression

  /**
   * A type expression that defines a string value constrained by a Java Regular Expression
   *
   * @param loc     The location of the pattern type expression
   * @param pattern The Java Regular Expression to which values of this type expression must obey.
   * @see https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
   */
  case class Pattern(
    loc: Location,
    pattern: Seq[LiteralString])
    extends TypeExpression

  /**
   * A type expression for values that ensure a unique identifier for a specific entity.
   *
   * @param loc        The location of the unique identifier type expression
   * @param entityPath The path identifier of the entity type
   */
  case class UniqueId(
    loc: Location,
    entityPath: PathIdentifier)
    extends TypeExpression

  /**
   * A type expression for an aggregation type expression that is marked as being one of the four
   * message kinds.
   *
   * @param loc         The location of the message type expression
   * @param messageKind The kind of message defined
   * @param fields      The fields of the message's aggregation
   */
  case class MessageType(
    loc: Location,
    messageKind: MessageKind,
    fields: Seq[Field] = Seq.empty[Field])
    extends TypeExpression with EntityValue

  /**
   * Base class of all pre-defined type expressions
   */
  abstract class PredefinedType extends TypeExpression {
    def loc: Location

    def kind: String
  }

  object PredefinedType {
    final def unapply(preType: PredefinedType): Option[String] = Option(preType.kind)
  }

  /**
   * A type expression for values of arbitrary string type, possibly bounded by length.
   *
   * @param loc The location of the Strng type expression
   * @param min The minimum length of the string (default: 0)
   * @param max The maximum length of the string (default: MaxInt)
   */
  case class Strng(
    loc: Location,
    min: Option[LiteralInteger] = None,
    max: Option[LiteralInteger] = None)
    extends PredefinedType {
    override lazy val kind: String = "String"
  }

  /**
   * A predefined type expression for boolean values (true / false)
   *
   * @param loc The locatin of the Bool type expression
   */
  case class Bool(loc: Location) extends PredefinedType {
    override lazy val kind: String = "Boolean"
  }

  /**
   * A predefined type expression for an arbitrary number value
   *
   * @param loc The location of the number type expression
   */
  case class Number(loc: Location) extends PredefinedType {
    def kind: String = "Number"
  }

  /**
   * A predefined type expression for an integer value
   *
   * @param loc The locaiton of the integer type expression
   */
  case class Integer(loc: Location) extends PredefinedType {
    def kind: String = "Integer"
  }

  /**
   * A predefined type expression for a decimal value including IEEE
   * floating point syntax.
   *
   * @param loc The location of the decimal integer type expression
   */
  case class Decimal(loc: Location) extends PredefinedType {
    def kind: String = "Decimal"
  }

  /**
   * A predefined type expression for a real number value.
   *
   * @param loc The locaiton of the real number type expression
   */
  case class Real(loc: Location) extends PredefinedType {
    def kind: String = "Real"
  }

  /**
   * A predefined type expression for a calendar date.
   *
   * @param loc The location of the date type expression.
   */
  case class Date(loc: Location) extends PredefinedType {
    def kind: String = "Date"
  }

  /**
   * A predefined type expression for a clock time with hours, minutes, seconds.
   *
   * @param loc The location of the time type expression.
   */
  case class Time(loc: Location) extends PredefinedType {
    def kind: String = "Time"
  }

  /**
   * A predefined type expression for a calendar date and clock time combination.
   *
   * @param loc The location of the datetime type expression.
   */
  case class DateTime(loc: Location) extends PredefinedType {
    def kind: String = "DateTime"
  }

  /**
   * A predefined type expression for a timestamp that records the number of
   * milliseconds from the epoch.
   *
   * @param loc The location of the timestamp
   */
  case class TimeStamp(loc: Location) extends PredefinedType {
    def kind: String = "TimeStamp"
  }

  /**
   * A predefined type expression for a time duration that records the number of
   * milliseconds between two fixed points in time
   *
   * @param loc The location of the duration type expression
   */
  case class Duration(loc: Location) extends PredefinedType {
    def kind: String = "Duration"
  }

  /**
   * A predefined type expression for a universally unique identifier as defined
   * by the Java Virtual Machine.
   *
   * @param loc The location of the UUID type expression
   */
  case class UUID(loc: Location) extends PredefinedType {
    def kind: String = "UUID"
  }

  /**
   * A predefined type expression for a Uniform Resource Locator of a specific schema.
   *
   * @param loc    The location of the URL type expression
   * @param scheme The scheme to which the URL is constrained.
   */
  case class URL(loc: Location, scheme: Option[LiteralString] = None) extends PredefinedType {
    def kind: String = "URL"
  }

  /**
   * A predefined type expression for a location on earth given in latitude and longitude.
   *
   * @param loc The location of the LatLong type expression.
   */
  case class LatLong(loc: Location) extends PredefinedType {
    def kind: String = "LatLong"
  }

  /**
   * A predefined type expression for a type that can have no values
   *
   * @param loc The location of the nothing type expression.
   */
  case class Nothing(loc: Location) extends PredefinedType {
    def kind: String = "Nothing"
  }

  /**
   * A type definition which associates an identifier with a type expression.
   *
   * @param loc         The location of the type definition
   * @param id          The name of the type being defined
   * @param typ         The type expression of the type being defined
   * @param description An optional description of the type.
   */
  case class Type(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    description: Option[Description] = None)
    extends Definition with ContextDefinition with EntityDefinition with DomainDefinition {}

  // ///////////////////////////////// ///////////////////////// VALUE EXPRESSIONS
  sealed trait Expression extends RiddlValue

  case class ArbitraryExpression(loc: Location, what: LiteralString) extends Expression {
    override def format: String = what.format
  }

  case class ArithmeticOperator(
    loc: Location, operator: String, operands: Seq[Expression]) extends Expression {
    override def format: String = operator + operands.mkString("(", ",", ")")
  }

  case class ValueExpression(loc: Location, path: PathIdentifier) extends Condition {
    override def format: String = path.format
  }

  case class GroupExpression(loc: Location, expression: Expression) extends Expression {
    override def format: String = s"(${expression.format})"
  }

  case class ArgList(args: ListMap[Identifier, Expression] = ListMap.empty[Identifier, Expression])
    extends RiddlNode {
    override def format: String = args.map { case (id, exp) =>
      id.format + "=" + exp.format
    }.mkString("(", ", ", ")")
  }

  case class FunctionCallExpression(loc: Location, name: PathIdentifier, arguments: ArgList)
    extends Expression with Condition {
    override def format: String = name.format + arguments.format
  }

  case class LiteralInteger(loc: Location, n: BigInt) extends Expression {
    override def format: String = n.toString()
  }

  case class LiteralDecimal(loc: Location, d: BigDecimal) extends Expression {
    override def format: String = d.toString
  }

  ///////////////////////////////////////////////////////////// Conditional Expressions

  sealed trait Condition extends Expression

  case class True(loc: Location) extends Condition {
    override def format: String = "true"
  }

  case class False(loc: Location) extends Condition {
    override def format: String = "false"
  }

  case class ArbitraryCondition(cond: LiteralString) extends Condition {
    override def loc: Location = cond.loc

    override def format: String = cond.format
  }

  sealed trait Comparator extends RiddlNode

  final case object lt extends Comparator {
    override def format: String = "<"
  }

  final case object gt extends Comparator {
    override def format: String = ">"
  }

  final case object le extends Comparator {
    override def format: String = "<="
  }

  final case object ge extends Comparator {
    override def format: String = ">="
  }

  final case object eq extends Comparator {
    override def format: String = "=="
  }

  final case object ne extends Comparator {
    override def format: String = "!="
  }

  case class Comparison(
    loc: Location,
    op: Comparator,
    expr1: Expression,
    expr2: Expression) extends Condition {
    override def format: String =
      op.format + Seq(expr1.format, expr2.format).mkString("(", ",", ")")
  }

  case class NotCondition(loc: Location, cond1: Condition) extends Condition {
    override def format: String = "!(" + cond1 + ")"
  }

  abstract class BinaryCondition extends Condition {
    def cond1: Condition

    def cond2: Condition

    override def format: String =
      Seq(cond1.format, cond2.format).mkString("(", ",", ")")
  }

  case class AndCondition(loc: Location, cond1: Condition, cond2: Condition) extends
    BinaryCondition {
    override def format: String = "and" + super.format
  }

  case class OrCondition(loc: Location, cond1: Condition, cond2: Condition) extends
    BinaryCondition {
    override def format: String = "or" + super.format
  }

  case class UndefinedCondition(loc: Location) extends Condition {
    override def format: String = Terminals.Punctuation.undefined

    override def isEmpty: Boolean = true
  }

  ///////////////////////////////////////////////////////////// Actions

  sealed trait Action extends DescribedValue

  /** An action whose behavior is specified as a text string allowing extension to arbitrary
   * actions not otherwise handled by RIDDL
   *
   * @param loc         The location where the action occurs in the source
   * @param what        The action to take (emitted as pseudo-code)
   * @param description An optional description of the action
   */
  case class ArbitraryAction(
    loc: Location, what: LiteralString,
    description: Option[Description]) extends Action {
    override def format: String = what.format
  }

  /** An action whose behavior is to set the value of a state field to some expression
   *
   * @param loc         The location where the action occurs int he source
   * @param target      The path identifier of the entity's state field that is to be set
   * @param value       An expression for the value to set the field to
   * @param description An optional description of the action
   */
  case class SetAction(
    loc: Location,
    target: PathIdentifier,
    value: Expression,
    description: Option[Description] = None)
    extends Action {
    override def format: String = {
      s"set ${target.format} to ${value.format}"
    }
  }

  /** A helper class for publishing messages that represents the construction of the message to
   * be sent.
   *
   * @param msg  A message reference that specifies the specific type of message to construct
   * @param args An argument list that should correspond to teh fields of the message
   */
  case class MessageConstructor(msg: MessageRef, args: ArgList = ArgList())
    extends RiddlNode {
    override def format: String = msg.format + {
      if (args.nonEmpty) {
        args.format
      } else {
        "()"
      }
    }
  }

  /** An action that publishes a message to a pipe
   *
   * @param loc         The location in the source of the publish action
   * @param msg         The constructed message to be published
   * @param pipe        The pipe onto which the message is published
   * @param description An optional description of the action
   */
  case class PublishAction(
    loc: Location,
    msg: MessageConstructor,
    pipe: PipeRef,
    description: Option[Description] = None)
    extends Action {
    override def format: String = s"publish ${msg.format} to ${pipe.format}"
  }

  /** An action that morphs the state of an entity to a new structure
   *
   * @param loc         The location of the morph action in the source
   * @param entity      The entity to be affected
   * @param state       The reference to the new state structure
   * @param description An optional description of this action
   */
  case class MorphAction(
    loc: Location,
    entity: EntityRef,
    state: StateRef,
    description: Option[Description] = None) extends Action {
    override def format: String = s"morph ${}"
  }

  /** An action that changes the behavior of an entity by making it use a new
   * handler for its messages; named for the "become" operation in Akka that
   * does the same for an actor.
   *
   * @param loc         The location in the source of the become action
   * @param entity      The entity whose behavior is to change
   * @param handler     The reference to the new handler for the entity
   * @param description An optional description of this action
   */
  case class BecomeAction(
    loc: Location,
    entity: EntityRef,
    handler: HandlerRef,
    description: Option[Description] = None
  ) extends Action {
    override def format: String = s"become ${}"
  }

  case class TellAction(
    loc: Location,
    entity: EntityRef,
    msg: MessageConstructor,
    description: Option[Description] = None)
    extends Action {
    override def format: String = s"tell ${entity.format} to ${msg.format}"
  }

  case class AskAction(
    loc: Location,
    entity: EntityRef,
    msg: MessageConstructor,
    description: Option[Description] = None) extends Action {
    override def format: String = s"ask ${entity.format} to ${msg.format}"
  }

  // ////////////////////////////////////////////////////////// Gherkin

  sealed trait GherkinValue extends RiddlValue

  sealed trait GherkinClause extends GherkinValue

  case class GivenClause(loc: Location, scenario: Seq[LiteralString]) extends GherkinClause

  case class WhenClause(loc: Location, condition: Condition) extends GherkinClause

  case class ThenClause(loc: Location, action: Action) extends GherkinClause

  case class ButClause(loc: Location, action: Action) extends GherkinClause

  case class Example(
    loc: Location,
    id: Identifier,
    givens: Seq[GivenClause] = Seq.empty[GivenClause],
    whens: Seq[WhenClause] = Seq.empty[WhenClause],
    thens: Seq[ThenClause] = Seq.empty[ThenClause],
    buts: Seq[ButClause] = Seq.empty[ButClause],
    description: Option[Description] = None)
    extends ProcessorDefinition {
    override def isEmpty: Boolean = givens.isEmpty && whens.isEmpty && thens.isEmpty && buts.isEmpty
  }

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

  case class EntityRef(loc: Location, id: PathIdentifier) extends Reference[Entity] {
    override def format: String = s"${Keywords.entity} ${id.format}"
  }


  case class FeatureRef(loc: Location, id: PathIdentifier) extends Reference[Feature] {
    override def format: String = s"${Keywords.feature} ${id.format}"
  }

  case class Feature(
    loc: Location,
    id: Identifier,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None)
    extends Container[Example] with EntityDefinition with ContextDefinition {
    lazy val contents: Seq[Example] = examples
  }

  case class FunctionRef(loc: Location, id: PathIdentifier) extends Reference[Function] {
    override def format: String = s"${Keywords.function} ${id.format}"
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
    extends Reference[Invariant] {
    override def format: String = s"${Keywords.invariant} ${id.format}"
  }

  case class Invariant(
    loc: Location,
    id: Identifier,
    expression: Condition,
    description: Option[Description] = None)
    extends EntityDefinition {
    override def isEmpty: Boolean = expression.isEmpty
  }

  case class OnClause(
    loc: Location,
    msg: MessageRef,
    examples: Seq[Example] = Seq.empty[Example],
    description: Option[Description] = None
  ) extends EntityValue with DescribedValue {
    override def isEmpty: Boolean = examples.isEmpty
  }

  case class Handler(
    loc: Location,
    id: Identifier,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    description: Option[Description] = None)
    extends EntityDefinition {
    override def isEmpty: Boolean = super.isEmpty && clauses.isEmpty
  }

  case class HandlerRef(loc: Location, id: PathIdentifier) extends Reference[Handler] {
    override def format: String = s"${Keywords.handler} ${id.format}"
  }

  case class State(
    loc: Location,
    id: Identifier,
    typeEx: Aggregation,
    description: Option[Description] = None)
    extends EntityDefinition with Container[Field] {

    override def contents: Seq[Field] = typeEx.fields
  }

  case class StateRef(loc: Location, id: PathIdentifier) extends Reference[State] {
    override def format: String = s"${Keywords.state} ${id.format}"
  }

  /** Definition of an Entity
   *
   * @param options
   * The options for the entity
   * @param loc
   * The location in the input
   * @param id
   * The name of the entity
   * @param states
   * The state values of the entity
   * @param types
   * Type definitions useful internally to the entity definition
   * @param handlers
   * A set of event handlers
   * @param functions
   * Utility functions defined for the entity
   * @param invariants
   * Invariant properties of the entity
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

  case class ContextRef(loc: Location, id: PathIdentifier) extends Reference[Context] {
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

  case class PipeRef(loc: Location, id: PathIdentifier) extends Reference[Pipe] {
    override def format: String = s"pipe ${id.format}"
  }

  sealed trait StreamletRef[+T <: Definition] extends Reference[T]

  case class InletRef(loc: Location, id: PathIdentifier) extends StreamletRef[Inlet] {
    override def format: String = s"inlet ${id.format}"
  }

  case class OutletRef(loc: Location, id: PathIdentifier) extends StreamletRef[Outlet] {
    override def format: String = s"outlet ${id.format}"
  }

  sealed trait Joint extends PlantDefinition

  case class InletJoint(
    loc: Location,
    id: Identifier,
    streamletRef: InletRef,
    pipe: PipeRef,
    description: Option[Description] = None)
    extends Joint

  case class OutletJoint(
    loc: Location,
    id: Identifier,
    streamletRef: OutletRef,
    pipe: PipeRef,
    description: Option[Description] = None)
    extends Joint

  case class Plant(
    loc: Location,
    id: Identifier,
    pipes: Seq[Pipe] = Seq.empty[Pipe],
    processors: Seq[Processor] = Seq.empty[Processor],
    inJoints: Seq[InletJoint] = Seq.empty[InletJoint],
    outJoints: Seq[OutletJoint] = Seq.empty[OutletJoint],
    description: Option[Description] = None)
    extends Container[PlantDefinition] with DomainDefinition {
    lazy val contents: Seq[PlantDefinition] = pipes ++ processors ++ inJoints ++ outJoints
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

  case class DomainRef(loc: Location, id: PathIdentifier) extends Reference[Domain] {
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
