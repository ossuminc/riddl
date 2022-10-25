/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.ast

/** Unit Tests For TypeExpression */
trait TypeExpression extends AbstractDefinitions {

///////////////////////////////////////////////////////////// TYPES
  sealed trait TypeDefinition extends Definition

  /** Base trait of an expression that defines a type
    */
  sealed trait TypeExpression extends RiddlValue {
    def isAssignmentCompatible(other: TypeExpression): Boolean = {
      (other == this) || (other.getClass == this.getClass) ||
      (other.getClass == classOf[Abstract]) ||
      (this.getClass == classOf[Abstract])
    }
  }

  /** A TypeExpression that references another type by PathIdentifier */
  case class AliasedTypeExpression(loc: Location, pid: PathIdentifier)
      extends TypeExpression {
    override def format: String = s"Reference To ${pid.format}"
  }

  /** A utility function for getting the kind of a type expression.
    *
    * @param te
    *   The type expression to examine
    * @return
    *   A string indicating the kind corresponding to te
    */
  def kind(te: TypeExpression): String = {
    te match {
      case AliasedTypeExpression(_, pid) => s"Reference To ${pid.format}"
      case Optional(_, typeExp)          => kind(typeExp) + "?"
      case ZeroOrMore(_, typeExp)        => kind(typeExp) + "*"
      case OneOrMore(_, typeExp)         => kind(typeExp) + "+"
      case _: Enumeration                => "Enumeration"
      case _: Alternation                => "Alternation"
      case _: Aggregation                => "Aggregation"
      case Mapping(_, from, to)   => s"Map From ${kind(from)} To ${kind(to)}"
      case RangeType(_, min, max) => s"Range($min,$max)"
      case EntityReferenceTypeExpression(_, entity) =>
        s"Reference To Entity ${entity.format}"
      case _: Pattern                     => s"Pattern"
      case UniqueId(_, entityPath)        => s"Id(${entityPath.format})"
      case MessageType(_, messageKind, _) => messageKind.format
      case predefinedType: PredefinedType => predefinedType.kind
      case _                              => "<unknown type expression>"
    }
  }

  // //////////////////////////////////////////////////////////////////////// TYPES

  /** Base of an enumeration for the four kinds of message types */
  sealed trait MessageKind {
    def kind: String
    def format: String = kind.capitalize
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

  final case object OtherKind extends MessageKind {
    def kind: String = "other"
  }

  /** Base trait of the cardinality type expressions */
  sealed trait Cardinality extends TypeExpression {
    def typeExp: TypeExpression
  }

  /** A cardinality type expression that indicates another type expression as
    * being optional; that is with a cardinality of 0 or 1.
    *
    * @param loc
    *   The location of the optional cardinality
    * @param typeExp
    *   The type expression that is indicated as optional
    */
  case class Optional(loc: Location, typeExp: TypeExpression)
      extends Cardinality {
    override def format: String = s"${typeExp.format}?"
  }

  /** A cardinality type expression that indicates another type expression as
    * having zero or more instances.
    *
    * @param loc
    *   The location of the zero-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of zero or
    *   more.
    */
  case class ZeroOrMore(loc: Location, typeExp: TypeExpression)
      extends Cardinality {
    override def format: String = s"${typeExp.format}*"
  }

  /** A cardinality type expression that indicates another type expression as
    * having one or more instances.
    *
    * @param loc
    *   The location of the one-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of one or more.
    */
  case class OneOrMore(loc: Location, typeExp: TypeExpression)
      extends Cardinality {
    override def format: String = s"${typeExp.format}+"
  }

  /** A cardinality type expression that indicates another type expression as
    * having a specific range of instances
    *
    * @param loc
    *   The location of the one-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of one or more.
    * @param min
    *   The minimum number of items
    * @param max
    *   The maximum number of items
    */
  case class SpecificRange(
    loc: Location,
    typeExp: TypeExpression,
    min: Long,
    max: Long)
      extends Cardinality {
    override def format: String = s"${typeExp.format}{$min,$max}"
  }

  /** Represents one variant among (one or) many variants that comprise an
    * [[Enumeration]]
    *
    * @param id
    *   the identifier (name) of the Enumerator
    * @param enumVal
    *   the optional int value
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   the description of the enumerator. Each Enumerator in an enumeration may
    *   define independent descriptions
    */
  case class Enumerator(
    loc: Location,
    id: Identifier,
    enumVal: Option[Long] = None,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends LeafDefinition with TypeDefinition {
    override def format: String = id.format
    final val kind: String = "Enumerator"
    override def isEmpty: Boolean = true
  }

  /** A type expression that defines its range of possible values as being one
    * value from a set of enumerated values.
    *
    * @param loc
    *   The location of the enumeration type expression
    * @param enumerators
    *   The set of enumerators from which the value of this enumeration may be
    *   chosen.
    */
  case class Enumeration(
    loc: Location,
    enumerators: Seq[Enumerator])
      extends TypeExpression {
    override def format: String = "{ " + enumerators.map(_.format)
      .mkString(",") + " }"

  }

  /** A type expression that that defines its range of possible values as being
    * any one of the possible values from a set of other type expressions.
    *
    * @param loc
    *   The location of the alternation type expression
    * @param of
    *   The set of type expressions from which the value for this alternation
    *   may be chosen
    */
  case class Alternation(
    loc: Location,
    of: Seq[AliasedTypeExpression])
      extends TypeExpression {
    override def format: String =
      s"one of { ${of.map(_.format).mkString(", ")} }"
  }

  /** A definition that is a field of an aggregation type expressions. Fields
    * associate an identifier with a type expression.
    *
    * @param loc
    *   The location of the field definition
    * @param id
    *   The name of the field
    * @param typeEx
    *   The type of the field
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the field.
    */
  case class Field(
    loc: Location,
    id: Identifier,
    typeEx: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None)
      extends LeafDefinition
      with AlwaysEmpty
      with TypeDefinition
      with SagaDefinition
      with StateDefinition
      with FunctionDefinition
      with ProjectionDefinition {
    override def format: String = s"${id.format}: ${typeEx.format}"
    final val kind: String = "Field"
  }

  /** A type expression that contains an aggregation of fields
    *
    * This is used as the base trait of Aggregations and Messages
    */
  trait AggregateTypeExpression extends TypeExpression with Container[Field] {
    def fields: Seq[Field]
    final lazy val contents: Seq[Field] = fields
    override def format: String = s"{ ${fields.map(_.format).mkString(", ")} }"

  }

  /** A type expression that takes a set of named fields as its value.
    *
    * @param loc
    *   The location of the aggregation definition
    * @param fields
    *   The fields of the aggregation
    */
  case class Aggregation(
    loc: Location,
    fields: Seq[Field] = Seq.empty[Field])
      extends AggregateTypeExpression {}

  object Aggregation {
    val empty: Aggregation = { Aggregation(Location.empty, Seq.empty[Field]) }
    def empty(loc: Location = Location.empty): Aggregation = {
      Aggregation(loc, Seq.empty[Field])
    }
  }

  /** A type expressions that defines a mapping from a key to a value. The value
    * of a Mapping is the set of mapped key -> value pairs, based on which keys
    * have been provided values.
    *
    * @param loc
    *   The location of the mapping type expression
    * @param from
    *   The type expression for the keys of the mapping
    * @param to
    *   The type expression for the values of the mapping
    */
  case class Mapping(
    loc: Location,
    from: TypeExpression,
    to: TypeExpression)
      extends TypeExpression {
    override def format: String = s"mapping from ${from.format} to ${to.format}"
  }

  /** A type expression whose value is a reference to an instance of an entity.
    *
    * @param loc
    *   The location of the reference type expression
    * @param entity
    *   The type of entity referenced by this type expression.
    */
  case class EntityReferenceTypeExpression(
    loc: Location,
    entity: PathIdentifier)
      extends TypeExpression {
    override def format: String = s"reference to Entity ${entity.format}"
  }

  /** A type expression that defines a string value constrained by a Java
    * Regular Expression
    *
    * @param loc
    *   The location of the pattern type expression
    * @param pattern
    *   The Java Regular Expression to which values of this type expression must
    *   obey.
    * @see
    *   https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
    */
  case class Pattern(
    loc: Location,
    pattern: Seq[LiteralString])
      extends TypeExpression {
    override def format: String =
      s"pattern(${pattern.map(_.format).mkString(", ")})"

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Strng]
    }
  }

  /** A type expression for values that ensure a unique identifier for a
    * specific entity.
    *
    * @param loc
    *   The location of the unique identifier type expression
    * @param entityPath
    *   The path identifier of the entity type
    */
  case class UniqueId(
    loc: Location,
    entityPath: PathIdentifier)
      extends TypeExpression {
    override def format: String = s"Id(${entityPath.format})"

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A type expression for an aggregation type expression that is marked as
    * being one of the four message kinds.
    *
    * @param loc
    *   The location of the message type expression
    * @param messageKind
    *   The kind of message defined
    * @param fields
    *   The fields of the message's aggregation
    */
  case class MessageType(
    loc: Location,
    messageKind: MessageKind,
    fields: Seq[Field] = Seq.empty[Field])
      extends AggregateTypeExpression {
    override def format: String = { messageKind.kind + " " + super.format }
  }

  /** Base class of all pre-defined type expressions
    */
  abstract class PredefinedType extends TypeExpression {
    def loc: Location

    def kind: String

    override def format: String = s"$kind"
  }

  object PredefinedType {
    final def unapply(preType: PredefinedType): Option[String] =
      Option(preType.kind)
  }

  /** A type expression for values of arbitrary string type, possibly bounded by
    * length.
    *
    * @param loc
    *   The location of the Strng type expression
    * @param min
    *   The minimum length of the string (default: 0)
    * @param max
    *   The maximum length of the string (default: MaxInt)
    */
  case class Strng(
    loc: Location,
    min: Option[Long] = None,
    max: Option[Long] = None)
      extends PredefinedType {
    override lazy val kind: String = Predefined.String

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Pattern]
    }
  }

  case class Currency(loc: Location, country: String) extends PredefinedType {
    def kind: String = Predefined.Currency
  }

  /** The simplest type expression: Abstract An abstract type expression is one
    * that is not defined explicitly. It is treated as a concrete type but
    * without any structural or type information. This is useful for types that
    * are defined only at implementation time or for types whose variations are
    * so complicated they need to remain abstract at the specification level.
    * @param loc
    *   The location of the Bool type expression
    */
  case class Abstract(
    loc: Location)
      extends PredefinedType {
    def kind: String = Predefined.Abstract

    override def isAssignmentCompatible(other: TypeExpression): Boolean = true
  }

  sealed trait NumericType extends PredefinedType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[NumericType]
    }
  }

  /** A predefined type expression for boolean values (true / false)
    *
    * @param loc
    *   The location of the Bool type expression
    */
  case class Bool(loc: Location) extends NumericType {
    def kind: String = Predefined.Boolean
  }

  /** A predefined type expression for an arbitrary number value
    *
    * @param loc
    *   The location of the number type expression
    */
  case class Number(loc: Location) extends NumericType {
    def kind: String = Predefined.Number
  }

  /** A predefined type expression for an integer value
    *
    * @param loc
    *   The location of the integer type expression
    */
  case class Integer(loc: Location) extends NumericType {
    def kind: String = Predefined.Integer
  }

  /** A type expression that defines a set of integer values from a minimum
    * value to a maximum value, inclusively.
    *
    * @param loc
    *   The location of the RangeType type expression
    * @param min
    *   The minimum value of the RangeType
    * @param max
    *   The maximum value of the RangeType
    */
  case class RangeType(
    loc: Location,
    min: Long,
    max: Long)
      extends NumericType {
    override def format: String = s"range($min,$max)"
    def kind: String = Predefined.Range
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[NumericType]
    }
  }

  /** A predefined type expression for a decimal value including IEEE floating
    * point syntax.
    *
    * @param loc
    *   The location of the decimal integer type expression
    */
  case class Decimal(loc: Location) extends NumericType {
    def kind: String = Predefined.Decimal
  }

  /** A predefined type expression for a real number value.
    *
    * @param loc
    *   The location of the real number type expression
    */
  case class Real(loc: Location) extends NumericType {
    def kind: String = Predefined.Real
  }

  /** A predefined type expression for the SI Base unit for Current (amperes)
    * @param loc
    *   \- The locaitonof the current type expression
    */
  case class Current(loc: Location) extends NumericType {
    def kind: String = Predefined.Current
  }

  /** A predefined type expression for the SI Base unit for Length (meters)
    * @param loc
    *   The location of the current type expression
    */
  case class Length(loc: Location) extends NumericType {
    def kind: String = Predefined.Length
  }

  /**
   * A predefined type expression for the SI Base Unit for
   * Luminosity (candela0
   * @param loc
   *   The location of the luminosity expression
   */
  case class Luminosity(loc: Location) extends NumericType {
    def kind: String = Predefined.Luminosity
  }

  case class Mass(loc: Location) extends NumericType {
    def kind: String = Predefined.Mass
  }

  /** A predefined type expression for the SI Base Unit for Mole (mole)
    * @param loc
    *   \- The location of the mass type expression
    */
  case class Mole(loc: Location) extends NumericType {
    def kind: String = Predefined.Mole
  }

  /** A predefined type expression for the SI Base Unit for Temperature (Kelvin)
    * @param loc
    *   \- The location of the mass type expression
    */
  case class Temperature(loc: Location) extends NumericType {
    def kind: String = Predefined.Temperature
  }

  sealed trait TimeType extends PredefinedType

  /** A predefined type expression for a calendar date.
    *
    * @param loc
    *   The location of the date type expression.
    */
  case class Date(loc: Location) extends TimeType {
    def kind: String = Predefined.Date

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a clock time with hours, minutes,
    * seconds.
    *
    * @param loc
    *   The location of the time type expression.
    */
  case class Time(loc: Location) extends TimeType {
    def kind: String = Predefined.Time

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a calendar date and clock time
    * combination.
    *
    * @param loc
    *   The location of the datetime type expression.
    */
  case class DateTime(loc: Location) extends TimeType {
    def kind: String = Predefined.DateTime

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Date] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a timestamp that records the number of
    * milliseconds from the epoch.
    *
    * @param loc
    *   The location of the timestamp
    */
  case class TimeStamp(loc: Location) extends TimeType {
    def kind: String = Predefined.TimeStamp

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[Date] || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a time duration that records the number
    * of milliseconds between two fixed points in time
    *
    * @param loc
    *   The location of the duration type expression
    */
  case class Duration(loc: Location) extends TimeType {
    def kind: String = Predefined.Duration
  }

  /** A predefined type expression for a universally unique identifier as
    * defined by the Java Virtual Machine.
    *
    * @param loc
    *   The location of the UUID type expression
    */
  case class UUID(loc: Location) extends PredefinedType {
    def kind: String = Predefined.UUID
  }

  /** A predefined type expression for a Uniform Resource Locator of a specific
    * schema.
    *
    * @param loc
    *   The location of the URL type expression
    * @param scheme
    *   The scheme to which the URL is constrained.
    */
  case class URL(loc: Location, scheme: Option[LiteralString] = None)
      extends PredefinedType {
    def kind: String = Predefined.URL
  }

  /** A predefined type expression for a location on earth given in latitude and
    * longitude.
    *
    * @param loc
    *   The location of the LatLong type expression.
    */
  case class LatLong(loc: Location) extends PredefinedType {
    def kind: String = Predefined.LatLong
  }

  /** A predefined type expression for a type that can have no values
    *
    * @param loc
    *   The location of the nothing type expression.
    */
  case class Nothing(loc: Location) extends PredefinedType {
    def kind: String = Predefined.Nothing

    override def isAssignmentCompatible(other: TypeExpression): Boolean = false
  }
}
