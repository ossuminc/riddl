/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.parsing.Terminals.{Keywords, Options, Predefined, Readability}

import java.net.URL
import java.nio.file.Path
import scala.reflect.{ClassTag, classTag}

/** Abstract Syntax Tree This object defines the model for processing RIDDL and producing a raw AST from it. This raw
  * AST has no referential integrity, it just results from applying the parsing rules to the input. The RawAST models
  * produced from parsing are syntactically correct but have no semantic validation. The Transformation passes convert
  * RawAST model to AST model which is referentially and semantically consistent (or the user gets an error).
  */
object AST { // extends ast.AbstractDefinitions with ast.Definitions with ast.Options with ast.Types with ast.Statements

  ///////////////////////////////////////////////////////////////////////////////////////////// ABSTRACT DEFINITIONS
  /** The root trait of all things RIDDL AST. Every node in the tree is a RiddlNode. */
  sealed trait RiddlNode {

    /** Format the node to a string */
    def format: String

    /** Determine if this node is a container or not */
    def isContainer: Boolean = false

    // Determine if this node has definitions it contains
    def hasDefinitions: Boolean = false

    // Determine if this ndoe is a definitiono
    def isDefinition: Boolean = false

    /** determine if this node is empty or not. Non-containers are always empty
      */
    def isEmpty: Boolean = true

    @deprecatedOverriding(
      "nonEmpty is defined as !isEmpty; override isEmpty instead"
    ) final def nonEmpty: Boolean = !isEmpty
  }

  /** The root trait of all parsable values. If a parser returns something, its a RiddlValue. The distinguishing factor
    * is the inclusion of the parsing location given by the `loc` field.
    */
  sealed trait RiddlValue extends RiddlNode {

    /** The location in the parse at which this RiddlValue occurs */
    def loc: At
  }

  /** Represents a literal string parsed between quote characters in the input
    *
    * @param loc
    *   The location in the input of the opening quote character
    * @param s
    *   The parsed value of the string content
    */
  case class LiteralString(loc: At, s: String) extends RiddlValue {
    override def format = s"\"$s\""

    override def isEmpty: Boolean = s.isEmpty
  }
  object LiteralString {
    val empty: LiteralString = LiteralString(At.empty, "")
  }

  /** A RiddlValue that is a parsed identifier, typically the name of a definition.
    *
    * @param loc
    *   The location in the input where the identifier starts
    * @param value
    *   The parsed value of the identifier
    */
  case class Identifier(loc: At, value: String) extends RiddlValue {
    override def format: String = value

    override def isEmpty: Boolean = value.isEmpty
  }

  object Identifier {
    val empty: Identifier = Identifier(At.empty, "")
  }

  /** Represents a segmented identifier to a definition in the model. Path Identifiers are parsed from a dot-separated
    * list of identifiers in the input. Path identifiers are used to reference other definitions in the model.
    *
    * @param loc
    *   Location in the input of the first letter of the path identifier
    * @param value
    *   The list of strings that make up the path identifier
    */
  case class PathIdentifier(loc: At, value: Seq[String]) extends RiddlValue {
    override def format: String = { value.mkString(".") }

    override def isEmpty: Boolean = value.isEmpty || value.forall(_.isEmpty)
  }

  object PathIdentifier {
    val empty: PathIdentifier = PathIdentifier(At.empty, Seq.empty[String])
  }

  /** The description of a definition. All definitions have a name and an optional description. This class provides the
    * description part.
    */
  sealed trait Description extends RiddlValue {
    def loc: At

    def lines: Seq[LiteralString]

    override def isEmpty: Boolean = lines.isEmpty || lines.forall(_.isEmpty)
  }
  object Description {
    lazy val empty: Description = new Description {
      val loc: At = At.empty
      val lines = Seq.empty[LiteralString]
      def format: String = ""
    }
  }

  case class BlockDescription(
    loc: At = At.empty,
    lines: Seq[LiteralString] = Seq.empty[LiteralString]
  ) extends Description {
    def format: String = ""
  }

  case class FileDescription(loc: At, file: Path) extends Description {
    lazy val lines: Seq[LiteralString] = {
      val src = scala.io.Source.fromFile(file.toFile)
      src.getLines().toSeq.map(LiteralString(loc, _))
    }
    def format: String = file.toAbsolutePath.toString
  }

  case class URLDescription(loc: At, url: java.net.URL) extends Description {
    lazy val lines: Seq[LiteralString] = Seq.empty[LiteralString]

    /** Format the node to a string */
    override def format: String = url.toExternalForm
  }

  sealed trait BrieflyDescribedValue extends RiddlValue {
    def brief: Option[LiteralString]
    def briefValue: String = {
      brief.map(_.s).getOrElse("No brief description.")
    }
    def hasBriefDescription: Boolean = brief.nonEmpty
  }

  /** Base trait of all values that have an optional Description
    */
  sealed trait DescribedValue extends RiddlValue {
    def description: Option[Description]
    def descriptionValue: String = {
      description
        .map(_.lines.map(_.s))
        .mkString("", System.lineSeparator(), System.lineSeparator())
    }
    def hasDescription: Boolean = description.nonEmpty
  }

  /** Base trait of any definition that is also a ContainerValue
    *
    * @tparam D
    *   The kind of definition that is contained by the container
    */
  sealed trait Container[+D <: RiddlValue] extends RiddlValue {
    def contents: Seq[D]

    override def isEmpty: Boolean = contents.isEmpty

    override def isContainer: Boolean = true

    def isRootContainer: Boolean = false
  }

  /** Base trait for all definitions requiring an identifier for the definition and providing the identify method to
    * yield a string that provides the kind and name
    */
  sealed trait Definition extends DescribedValue with BrieflyDescribedValue with Container[Definition] {
    def id: Identifier

    def kind: String

    def identify: String = {
      if id.isEmpty then { s"Anonymous $kind" }
      else { s"$kind '${id.format}'" }
    }

    def identifyWithLoc: String = s"$identify at $loc"

    override def isDefinition: Boolean = true

    override def hasDefinitions: Boolean = contents.nonEmpty

    def isImplicit: Boolean = id.value.isEmpty

    def isVital: Boolean = false

    def isAppRelated: Boolean = false

    @SuppressWarnings(Array("org.wartremover.warts.asInstanceOf"))
    def asVital[OPT <: OptionValue, DEF <: Definition]: VitalDefinition[OPT, DEF] =
      require(this.isVital, "Not a vital definition")
      this.asInstanceOf[VitalDefinition[OPT, DEF]]

    def hasOptions: Boolean = false

    def hasAuthors: Boolean = false

    def hasTypes: Boolean = false

    def resolveNameTo(name: String): Option[Definition] = {
      contents.find(_.id.value == name)
    }
  }

  sealed trait LeafDefinition extends Definition {
    override def isEmpty: Boolean = true
    final def contents: Seq[Definition] = Seq.empty[Definition]
  }

  sealed trait AlwaysEmpty extends Definition {
    final override def isEmpty: Boolean = true
  }

  /** A reference to a definition of a specific type.
    *
    * @tparam T
    *   The type of definition to which the references refers.
    */
  sealed abstract class Reference[+T <: Definition: ClassTag] extends RiddlValue {

    /** The Path identifier to the referenced definition
      */
    def pathId: PathIdentifier

    /** The optional identifier of the reference to be used locally in some other reference.
      */
    def id: Option[Identifier] = None

    /** @return
      *   String A string that describes this reference
      */
    def identify: String = {
      s"${classTag[T].runtimeClass.getSimpleName} ${
          if id.nonEmpty then { id.map(_.format + ": ") }
          else ""
        }'${pathId.format}'${loc.toShort}"
    }

    override def isEmpty: Boolean = pathId.isEmpty
  }

  object Reference {
    val empty: Reference[Definition] = new Reference[Definition] {
      def pathId: PathIdentifier = PathIdentifier.empty
      def format: String = "Empty Reference"
      def loc: At = At.empty
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.IsInstanceOf"))
  def findAuthors(
    defn: Definition,
    parents: Seq[Definition]
  ): Seq[AuthorRef] = {
    if defn.hasAuthors then {
      defn.asInstanceOf[WithAuthors].authors
    } else {
      parents
        .find(d => d.isInstanceOf[WithAuthors] && d.asInstanceOf[WithAuthors].hasAuthors)
        .map(_.asInstanceOf[WithAuthors].authors)
        .getOrElse(Seq.empty[AuthorRef])
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////// DEFINITIONS

  /** Base trait of any definition that is in the content of an adaptor */
  sealed trait AdaptorDefinition extends Definition

  /** Base trait of any definition that is in the content of an Application */
  sealed trait ApplicationDefinition extends Definition

  /** Base trait of any definition that is in the content of a Group */
  sealed trait GroupDefinition extends Definition

  /** Base trait of any definition that is in the content of an Output */
  sealed trait OutputDefinition extends Definition

  /** Base trait of any definition that is in the content of an Input */
  sealed trait InputDefinition extends Definition

  /** Base trait of any definition that is in the content of a context */
  sealed trait ContextDefinition extends Definition

  /** Base trait of any definition that is in the content of a domain */
  sealed trait DomainDefinition extends Definition

  /** Base trait of any definition that is in the content of an entity */
  sealed trait EntityDefinition extends Definition

  /** Base trait of definitions that are part of a Handler Definition */
  sealed trait HandlerDefinition extends Definition

  /** Base trait of definitions that are part of an On Clause Definition */
  sealed trait OnClauseDefinition extends Definition

  /** Base trait of definitions defined in a processor */
  sealed trait ProcessorDefinition
      extends Definition
      with AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with EntityDefinition
      with ProjectorDefinition
      with RepositoryDefinition
      with StreamletDefinition
      with SagaDefinition

  /** Base trait of definitions defined in a repository */
  sealed trait RepositoryDefinition extends Definition

  /** Base trait of definitions defined at root scope */
  sealed trait RootDefinition extends Definition

  /** Base trait of definitions define within a Streamlet */
  sealed trait StreamletDefinition extends Definition

  /** Base trait of definitions that are in the body of a Story definition */
  sealed trait EpicDefinition extends Definition

  sealed trait VitalDefinitionDefinition
      extends AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with DomainDefinition
      with EntityDefinition
      with FunctionDefinition
      with StreamletDefinition
      with ProjectorDefinition
      with RepositoryDefinition
      with SagaDefinition
      with EpicDefinition

  /** Base trait of definitions that can accept a message directly via a reference
    * @tparam T
    *   The kind of reference needed
    */
  sealed trait ProcessorRef[+T <: Processor[?, ?]] extends Reference[T]

  /** Base trait of any definition that is in the content of a function. */
  sealed trait FunctionDefinition extends Definition

  /** Base trait of definitions that are part of a Saga Definition */
  sealed trait SagaDefinition extends Definition

  /** Base trait of definitions that are part of a Saga Definition */
  sealed trait StateDefinition extends Definition

  /** Base trait of any definition that occurs in the body of a projector */
  sealed trait ProjectorDefinition extends Definition

  /** Base trait of definitions in a UseCase, typically interactions */
  sealed trait UseCaseDefinition extends Definition

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////// TYPES

  // We need "Expression" sealed trait from Expression.scala but it
  // depends on TypeExpression.scala so we make Expression derive from
  // this forward declaration so we can use it here.
  sealed trait TypeDefinition extends Definition

  sealed trait AggregateDefinition extends TypeDefinition {
    def typeEx: TypeExpression
  }

  /** Base trait of an expression that defines a type
    */
  sealed trait TypeExpression extends RiddlValue {
    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    def isAssignmentCompatible(other: TypeExpression): Boolean = {
      (other == this) || (other.getClass == this.getClass) ||
      (other.getClass == classOf[Abstract]) ||
      (this.getClass == classOf[Abstract])
    }
    def hasCardinality: Boolean = false
  }

  sealed trait NumericType extends TypeExpression {

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[NumericType]
    }
  }

  sealed trait IntegerTypeExpression extends NumericType
  sealed trait RealTypeExpression extends NumericType

  /** A TypeExpression that references another type by PathIdentifier
    * @param loc
    *   The location of the AliasedTypeExpression
    * @param pathId
    *   The path identifier to the aliased type
    */
  case class AliasedTypeExpression(loc: At, pathId: PathIdentifier) extends TypeExpression {
    override def format: String = pathId.format
  }

  /** A utility function for getting the kind of a type expression.
    *
    * @param te
    *   The type expression to examine
    * @return
    *   A string indicating the kind corresponding to te
    */
  def errorDescription(te: TypeExpression): String = {
    te match {
      case AliasedTypeExpression(_, pid) => pid.format
      case Optional(_, typeExp)          => errorDescription(typeExp) + "?"
      case ZeroOrMore(_, typeExp)        => errorDescription(typeExp) + "*"
      case OneOrMore(_, typeExp)         => errorDescription(typeExp) + "+"
      case e: Enumeration                => s"Enumeration of ${e.enumerators.size} values"
      case a: Alternation                => s"Alternation of ${a.of.size} types"
      case a: Aggregation                => s"Aggregation of ${a.fields.size} fields"
      case Mapping(_, from, to) =>
        s"Map from ${errorDescription(from)} to ${errorDescription(to)}"
      case EntityReferenceTypeExpression(_, entity) =>
        s"Reference to entity ${entity.format}"
      case _: Pattern              => Predefined.Pattern
      case Decimal(_, whl, frac)   => s"Decimal($whl,$frac)"
      case RangeType(_, min, max)  => s"Range($min,$max)"
      case UniqueId(_, entityPath) => s"Id(${entityPath.format})"
      case m @ AggregateUseCaseTypeExpression(_, messageKind, _, _) =>
        s"${messageKind.format} of ${m.fields.size} fields and ${m.methods.size} methods"
      case pt: PredefinedType => pt.kind
      case _                  => "<unknown type expression>"
    }
  }

  /** Base of an enumeration for the four kinds of message types */
  sealed trait AggregateUseCase {
    @inline def kind: String
    override def toString: String = kind
    def format: String = kind
  }

  /** An enumerator value for command types */
  case object CommandCase extends AggregateUseCase {
    @inline def kind: String = "Command"
  }

  /** An enumerator value for event types */
  case object EventCase extends AggregateUseCase {
    @inline def kind: String = "Event"
  }

  /** An enumerator value for query types */
  case object QueryCase extends AggregateUseCase {
    @inline def kind: String = "Query"
  }

  /** An enumerator value for result types */
  case object ResultCase extends AggregateUseCase {
    @inline def kind: String = "Result"

  }

  case object RecordCase extends AggregateUseCase {
    @inline def kind: String = "Record"
  }

  /** Base trait of the cardinality type expressions */
  sealed trait Cardinality extends TypeExpression {
    def typeExp: TypeExpression
    final override def hasCardinality: Boolean = true
  }

  /** A cardinality type expression that indicates another type expression as being optional; that is with a cardinality
    * of 0 or 1.
    *
    * @param loc
    *   The location of the optional cardinality
    * @param typeExp
    *   The type expression that is indicated as optional
    */
  case class Optional(loc: At, typeExp: TypeExpression) extends Cardinality {
    override def format: String = s"${typeExp.format}?"
  }

  /** A cardinality type expression that indicates another type expression as having zero or more instances.
    *
    * @param loc
    *   The location of the zero-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of zero or more.
    */
  case class ZeroOrMore(loc: At, typeExp: TypeExpression) extends Cardinality {
    override def format: String = s"${typeExp.format}*"
  }

  /** A cardinality type expression that indicates another type expression as having one or more instances.
    *
    * @param loc
    *   The location of the one-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of one or more.
    */
  case class OneOrMore(loc: At, typeExp: TypeExpression) extends Cardinality {
    override def format: String = s"${typeExp.format}+"
  }

  /** A cardinality type expression that indicates another type expression as having a specific range of instances
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
    loc: At,
    typeExp: TypeExpression,
    min: Long,
    max: Long
  ) extends Cardinality {
    override def format: String = s"${typeExp.format}{$min,$max}"
  }

  /** Represents one variant among (one or) many variants that comprise an [[Enumeration]]
    *
    * @param id
    *   the identifier (name) of the Enumerator
    * @param enumVal
    *   the optional int value
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   the description of the enumerator. Each Enumerator in an enumeration may define independent descriptions
    */
  case class Enumerator(
    loc: At,
    id: Identifier,
    enumVal: Option[Long] = None,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with TypeDefinition {
    override def format: String = id.format
    final val kind: String = "Enumerator"
    override def isEmpty: Boolean = true
  }

  /** A type expression that defines its range of possible values as being one value from a set of enumerated values.
    *
    * @param loc
    *   The location of the enumeration type expression
    * @param enumerators
    *   The set of enumerators from which the value of this enumeration may be chosen.
    */
  case class Enumeration(loc: At, enumerators: Seq[Enumerator]) extends IntegerTypeExpression {
    override def format: String = "{ " + enumerators
      .map(_.format)
      .mkString(",") + " }"

  }

  /** A type expression that that defines its range of possible values as being any one of the possible values from a
    * set of other type expressions.
    *
    * @param loc
    *   The location of the alternation type expression
    * @param of
    *   The set of type expressions from which the value for this alternation may be chosen
    */
  case class Alternation(loc: At, of: Seq[AliasedTypeExpression]) extends TypeExpression {
    override def format: String =
      s"one of { ${of.map(_.format).mkString(", ")} }"
  }

  /** A definition that is a field of an aggregation type expressions. Fields associate an identifier with a type
    * expression.
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
    loc: At,
    id: Identifier,
    typeEx: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with AggregateDefinition
      with AlwaysEmpty
      with TypeDefinition
      with SagaDefinition
      with StateDefinition
      with FunctionDefinition
      with ProjectorDefinition {
    override def format: String = s"${id.format}: ${typeEx.format}"
    final val kind: String = "Field"
  }

  /** An argument to a method */
  case class MethodArgument(
    loc: At,
    key: String,
    value: TypeExpression
  ) extends RiddlNode {

    /** Format the node to a string */
    def format: String = s"$key: ${value.format}"

  }

  /** A leaf definition that is a callable method (function) of an aggregation type expressions. Methods associate an
    * identifier with a computed type expression.
    *
    * @param loc
    *   The location of the field definition
    * @param id
    *   The name of the field
    * @param args
    *   The type of the field
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the field.
    */
  case class Method(
    loc: At,
    id: Identifier,
    args: Seq[MethodArgument] = Seq.empty[MethodArgument],
    typeEx: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with AggregateDefinition
      with AlwaysEmpty
      with TypeDefinition
      with SagaDefinition
      with StateDefinition
      with FunctionDefinition
      with ProjectorDefinition {
    override def format: String = s"${id.format}(${args.map(_.format).mkString(", ")}): ${typeEx.format}"
    final val kind: String = "Method"
  }

  /** A type expression that contains an aggregation of fields
    *
    * This is used as the base trait of Aggregations and Messages
    */
  sealed trait AggregateTypeExpression extends TypeExpression with Container[AggregateDefinition] {
    def fields: Seq[Field]
    def methods: Seq[Method]
    final def contents: Seq[AggregateDefinition] = fields ++ methods
    override def format: String = s"{ ${contents.map(_.format).mkString(", ")} }"
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      other match {
        case oate: AggregateTypeExpression =>
          val validity: Seq[Boolean] = for
            ofield <- oate.contents
            myField <- contents.find(_.id.value == ofield.id.value)
            myTypEx = myField.typeEx
            oTypeEx = ofield.typeEx
          yield {
            myTypEx.isAssignmentCompatible(oTypeEx)
          }
          (validity.size == oate.contents.size) && validity.forall(_ == true)
        case _ =>
          super.isAssignmentCompatible(other)
      }
    }
  }

  /** A type expression that takes a set of named fields as its value.
    *
    * @param loc
    *   The location of the aggregation definition
    * @param fields
    *   The fields of the aggregation
    */
  case class Aggregation(
    loc: At,
    fields: Seq[Field] = Seq.empty[Field],
    methods: Seq[Method] = Seq.empty[Method]
  ) extends AggregateTypeExpression

  object Aggregation {
    def empty(loc: At = At.empty): Aggregation = { Aggregation(loc) }
  }

  /** A type expression for a sequence of some other type expression
    * @param loc
    *   Where this type expression occurs in the source code
    * @param of
    *   The type expression of the sequence's elements
    */
  case class Sequence(loc: At, of: TypeExpression) extends TypeExpression {
    override def format: String = s"sequence of ${of.format}"
  }

  /** A type expressions that defines a mapping from a key to a value. The value of a Mapping is the set of mapped key
    * -> value pairs, based on which keys have been provided values.
    *
    * @param loc
    *   The location of the mapping type expression
    * @param from
    *   The type expression for the keys of the mapping
    * @param to
    *   The type expression for the values of the mapping
    */
  case class Mapping(loc: At, from: TypeExpression, to: TypeExpression) extends TypeExpression {
    override def format: String = s"mapping from ${from.format} to ${to.format}"
  }

  /** A mathematical set of some other type of value
    * @param loc
    *   Where the type expression occurs in the source
    * @param of
    *   The type of the elements of the set.
    */
  case class Set(loc: At, of: TypeExpression) extends TypeExpression {

    /** Format the node to a string */
    override def format: String = s"set of ${of.format}"
  }

  /** A type expression whose value is a reference to an instance of an entity.
    *
    * @param loc
    *   The location of the reference type expression
    * @param entity
    *   The type of entity referenced by this type expression.
    */
  case class EntityReferenceTypeExpression(loc: At, entity: PathIdentifier) extends TypeExpression {
    override def format: String = s"${Keywords.entity} ${entity.format}"
  }

  /** A type expression that defines a string value constrained by a Java Regular Expression
    *
    * @param loc
    *   The location of the pattern type expression
    * @param pattern
    *   The Java Regular Expression to which values of this type expression must obey.
    * @see
    *   https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
    */
  case class Pattern(loc: At, pattern: Seq[LiteralString]) extends PredefinedType {
    override def kind: String = Predefined.Pattern
    override def format: String =
      s"${Predefined.Pattern}(${pattern.map(_.format).mkString(", ")})"

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Strng]
    }
  }

  /** A type expression for values that ensure a unique identifier for a specific entity.
    *
    * @param loc
    *   The location of the unique identifier type expression
    * @param entityPath
    *   The path identifier of the entity type
    */
  case class UniqueId(loc: At, entityPath: PathIdentifier) extends PredefinedType {
    @inline def kind: String = Predefined.Id
    override def format: String = s"$kind(${entityPath.format})"

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A type expression for an aggregation that is marked as being one of the use cases. This is used for messages,
    * records, and other aggregate types that need to have their purpose distinguished.
    *
    * @param loc
    *   The location of the message type expression
    * @param usecase
    *   The kind of message defined
    * @param fields
    *   The fields of the message's aggregation
    */
  case class AggregateUseCaseTypeExpression(
    loc: At,
    usecase: AggregateUseCase,
    fields: Seq[Field] = Seq.empty[Field],
    methods: Seq[Method] = Seq.empty[Method]
  ) extends AggregateTypeExpression {
    override def format: String = {
      usecase.format.toLowerCase() + " " + super.format
    }
  }

  /** Base class of all pre-defined type expressions
    */
  abstract class PredefinedType extends TypeExpression {
    override def isEmpty: Boolean = true

    def loc: At

    def kind: String

    override def format: String = kind
  }

  object PredefinedType {
    final def unapply(preType: PredefinedType): Option[String] =
      Option(preType.kind)
  }

  /** A type expression for values of arbitrary string type, possibly bounded by length.
    *
    * @param loc
    *   The location of the Strng type expression
    * @param min
    *   The minimum length of the string (default: 0)
    * @param max
    *   The maximum length of the string (default: MaxInt)
    */
  case class Strng(loc: At, min: Option[Long] = None, max: Option[Long] = None) extends PredefinedType {
    override lazy val kind: String = Predefined.String
    override def format: String = {
      if min.isEmpty && max.isEmpty then kind else s"$kind(${min.getOrElse("")},${max.getOrElse("")})"
    }

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Pattern]
    }
  }

  case class Currency(loc: At, country: String) extends PredefinedType {
    @inline def kind: String = Predefined.Currency
  }

  /** A type expression that is unknown at compile type but will be resolved before validation time.
    */
  case class UnknownType(loc: At) extends PredefinedType {
    @inline def kind: String = Predefined.Unknown
  }

  /** The simplest type expression: Abstract An abstract type expression is one that is not defined explicitly. It is
    * treated as a concrete type but without any structural or type information. This is useful for types that are
    * defined only at implementation time or for types whose variations are so complicated they need to remain abstract
    * at the specification level.
    * @param loc
    *   The location of the Bool type expression
    */
  case class Abstract(loc: At) extends PredefinedType {
    @inline def kind: String = Predefined.Abstract

    override def isAssignmentCompatible(other: TypeExpression): Boolean = true
  }

  case class UserId(loc: At) extends PredefinedType {
    @inline def kind: String = Predefined.UserId

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || {
        other match
          case _: Strng => true
          case _        => false
      }
    }
  }

  /** A predefined type expression for boolean values (true / false)
    *
    * @param loc
    *   The location of the Bool type expression
    */
  case class Bool(loc: At) extends PredefinedType with IntegerTypeExpression {
    @inline def kind: String = Predefined.Boolean
  }

  /** A predefined type expression for an arbitrary number value
    *
    * @param loc
    *   The location of the number type expression
    */
  case class Number(loc: At) extends PredefinedType with IntegerTypeExpression with RealTypeExpression {
    @inline def kind: String = Predefined.Number
  }

  /** A predefined type expression for an integer value
    *
    * @param loc
    *   The location of the integer type expression
    */
  case class Integer(loc: At) extends PredefinedType with IntegerTypeExpression {
    @inline def kind: String = Predefined.Integer
  }

  case class Whole(loc: At) extends PredefinedType with IntegerTypeExpression {
    @inline def kind: String = Predefined.Whole
  }

  case class Natural(loc: At) extends PredefinedType with IntegerTypeExpression {
    @inline def kind: String = Predefined.Whole
  }

  /** A type expression that defines a set of integer values from a minimum value to a maximum value, inclusively.
    *
    * @param loc
    *   The location of the RangeType type expression
    * @param min
    *   The minimum value of the RangeType
    * @param max
    *   The maximum value of the RangeType
    */
  case class RangeType(loc: At, min: Long, max: Long) extends IntegerTypeExpression {
    override def format: String = s"$kind($min,$max)"
    @inline def kind: String = Predefined.Range

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[NumericType]
    }
  }

  /** A predefined type expression for a decimal value including IEEE floating point syntax.
    *
    * @param loc
    *   The location of the decimal integer type expression
    */
  case class Decimal(loc: At, whole: Long, fractional: Long) extends RealTypeExpression {
    @inline def kind: String = Predefined.Decimal

    /** Format the node to a string */
    override def format: String = s"Decimal($whole,$fractional)"
  }

  /** A predefined type expression for a real number value.
    *
    * @param loc
    *   The location of the real number type expression
    */
  case class Real(loc: At) extends PredefinedType with RealTypeExpression {
    @inline def kind: String = Predefined.Real
  }

  /** A predefined type expression for the SI Base unit for Current (amperes)
    * @param loc
    *   \- The locaitonof the current type expression
    */
  case class Current(loc: At) extends PredefinedType with RealTypeExpression {
    @inline def kind: String = Predefined.Current
  }

  /** A predefined type expression for the SI Base unit for Length (meters)
    * @param loc
    *   The location of the current type expression
    */
  case class Length(loc: At) extends PredefinedType with RealTypeExpression {
    @inline def kind: String = Predefined.Length
  }

  /** A predefined type expression for the SI Base Unit for Luminosity (candela)
    * @param loc
    *   The location of the luminosity expression
    */
  case class Luminosity(loc: At) extends PredefinedType with RealTypeExpression {
    @inline def kind: String = Predefined.Luminosity
  }

  case class Mass(loc: At) extends PredefinedType with RealTypeExpression {
    @inline def kind: String = Predefined.Mass
  }

  /** A predefined type expression for the SI Base Unit for Mole (mole)
    * @param loc
    *   \- The location of the mass type expression
    */
  case class Mole(loc: At) extends PredefinedType with RealTypeExpression {
    @inline def kind: String = Predefined.Mole
  }

  /** A predefined type expression for the SI Base Unit for Temperature (Kelvin)
    * @param loc
    *   \- The location of the mass type expression
    */
  case class Temperature(loc: At) extends PredefinedType with RealTypeExpression {
    @inline def kind: String = Predefined.Temperature
  }

  sealed trait TimeType extends PredefinedType

  /** A predefined type expression for a calendar date.
    *
    * @param loc
    *   The location of the date type expression.
    */
  case class Date(loc: At) extends TimeType {
    @inline def kind: String = Predefined.Date

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a clock time with hours, minutes, seconds.
    *
    * @param loc
    *   The location of the time type expression.
    */
  case class Time(loc: At) extends TimeType {
    @inline def kind: String = Predefined.Time

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a calendar date and clock time combination.
    *
    * @param loc
    *   The location of the datetime type expression.
    */
  case class DateTime(loc: At) extends TimeType {
    @inline def kind: String = Predefined.DateTime

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Date] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a timestamp that records the number of milliseconds from the epoch.
    *
    * @param loc
    *   The location of the timestamp
    */
  case class TimeStamp(loc: At) extends TimeType {
    @inline def kind: String = Predefined.TimeStamp

    @SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[Date] || other.isInstanceOf[Strng] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a time duration that records the number of milliseconds between two fixed points
    * in time
    *
    * @param loc
    *   The location of the duration type expression
    */
  case class Duration(loc: At) extends TimeType {
    @inline def kind: String = Predefined.Duration
  }

  /** A predefined type expression for a universally unique identifier as defined by the Java Virtual Machine.
    *
    * @param loc
    *   The location of the UUID type expression
    */
  case class UUID(loc: At) extends PredefinedType {
    @inline def kind: String = Predefined.UUID
  }

  /** A predefined type expression for a Uniform Resource Locator of a specific schema.
    *
    * @param loc
    *   The location of the URL type expression
    * @param scheme
    *   The scheme to which the URL is constrained.
    */
  case class URL(loc: At, scheme: Option[LiteralString] = None) extends PredefinedType {
    @inline def kind: String = Predefined.URL
  }

  /** A predefined type expression for a location on earth given in latitude and longitude.
    *
    * @param loc
    *   The location of the LatLong type expression.
    */
  case class Location(loc: At) extends PredefinedType {
    @inline def kind: String = Predefined.Location
  }

  /** A predefined type expression for a type that can have no values
    *
    * @param loc
    *   The location of the nothing type expression.
    */
  case class Nothing(loc: At) extends PredefinedType {
    @inline def kind: String = Predefined.Nothing

    override def isAssignmentCompatible(other: TypeExpression): Boolean = false
  }

/////////////////////////////////////////////////////////////////////////////////////////////////////////////// OPTIONS

  /** Base trait for option values for any option of a definition.
    */
  sealed trait OptionValue extends RiddlValue {
    def name: String

    def args: Seq[LiteralString] = Seq.empty[LiteralString]

    override def format: String = name + args
      .map(_.format)
      .mkString("(", ", ", ")")
  }

  /** Base trait that can be used in any definition that takes options and ensures the options are defined, can be
    * queried, and formatted.
    *
    * @tparam T
    *   The sealed base trait of the permitted options for this definition
    */
  sealed trait WithOptions[T <: OptionValue] extends Definition {
    def options: Seq[T]

    def hasOption[OPT <: T: ClassTag]: Boolean = options
      .exists(_.getClass == implicitly[ClassTag[OPT]].runtimeClass)

    def getOptionValue[OPT <: T: ClassTag]: Option[Seq[LiteralString]] = options
      .find(_.getClass == implicitly[ClassTag[OPT]].runtimeClass)
      .map(_.args)

    override def format: String = {
      options.size match {
        case 0 => ""
        case 1 => s"option is ${options.head.format}"
        case x: Int if x > 1 =>
          s"options ( ${options.map(_.format).mkString(" ", ", ", " )")}"
      }
    }

    override def isEmpty: Boolean = options.isEmpty && super.isEmpty

    override def hasOptions: Boolean = options.nonEmpty
  }

  //////////////////////////////////////////////////////////////////// ADAPTOR

  sealed abstract class AdaptorOption(val name: String) extends OptionValue

  case class AdaptorTechnologyOption(
    loc: At,
    override val args: Seq[LiteralString]
  ) extends AdaptorOption("technology")

  //////////////////////////////////////////////////////////////////// HANDLER

  sealed abstract class HandlerOption(val name: String) extends OptionValue

  case class PartialHandlerOption(loc: At) extends HandlerOption("partial")

  //////////////////////////////////////////////////////////////////// PROJECTOR

  sealed abstract class ProjectorOption(val name: String) extends OptionValue

  case class ProjectorTechnologyOption(loc: At, override val args: Seq[LiteralString])
      extends ProjectorOption("technology")

  /////////////////////////////////////////////////////////////////// REPOSITORY

  sealed abstract class RepositoryOption(val name: String) extends OptionValue

  case class RepositoryTechnologyOption(loc: At, override val args: Seq[LiteralString])
      extends RepositoryOption("technology")

  /////////////////////////////////////////////////////////////////////// ENTITY

  /** Base trait of any value used in the definition of an entity
    */
  sealed trait EntityValue extends RiddlValue

  /** Abstract base class of options for entities
    *
    * @param name
    *   the name of the option
    */
  sealed abstract class EntityOption(val name: String) extends EntityValue with OptionValue

  /** An [[EntityOption]] that indicates that this entity should store its state in an event sourced fashion.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityEventSourced(loc: At) extends EntityOption("event sourced")

  /** An [[EntityOption]] that indicates that this entity should store only the latest value without using event
    * sourcing. In other words, the history of changes is not stored.
    *
    * @param loc
    *   The location of the option
    */
  case class EntityValueOption(loc: At) extends EntityOption("value")

  /** An [[EntityOption]] that indicates that this entity should not persist its state and is only available in
    * transient memory. All entity values will be lost when the service is stopped.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityTransient(loc: At) extends EntityOption("transient")

  /** An [[EntityOption]] that indicates that this entity is an aggregate root entity through which all commands and
    * queries are sent on behalf of the aggregated entities.
    *
    * @param loc
    *   The location of the option
    */
  case class EntityIsAggregate(loc: At) extends EntityOption("aggregate")

  /** An [[EntityOption]] that indicates that this entity favors consistency over availability in the CAP theorem.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsConsistent(loc: At) extends EntityOption("consistent")

  /** A [[EntityOption]] that indicates that this entity favors availability over consistency in the CAP theorem.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsAvailable(loc: At) extends EntityOption("available")

  /** An [[EntityOption]] that indicates that this entity is intended to implement a finite state machine.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsFiniteStateMachine(loc: At) extends EntityOption("finite state machine")

  /** An [[EntityOption]] that indicates that this entity should allow receipt of commands and queries via a message
    * queue.
    *
    * @param loc
    *   The location at which this option occurs.
    */
  case class EntityMessageQueue(loc: At) extends EntityOption("message queue")

  case class EntityIsDevice(loc: At) extends EntityOption("device")

  case class EntityTechnologyOption(loc: At, override val args: Seq[LiteralString]) extends EntityOption("technology")

  /** An [[EntityOption]] that indicates the general kind of entity being defined. This option takes a value which
    * provides the kind. Examples of useful kinds are "device", "user", "concept", "machine", and similar kinds of
    * entities. This entity option may be used by downstream AST processors, especially code generators.
    *
    * @param loc
    *   The location of the entity kind option
    * @param args
    *   The argument to the option
    */
  case class EntityKind(loc: At, override val args: Seq[LiteralString]) extends EntityOption("kind")

  //////////////////////////////////////////////////////////////////// FUNCTION

  /** Base class of all function options
    *
    * @param name
    *   The name of the option
    */
  sealed abstract class FunctionOption(val name: String) extends OptionValue

  /** A function option to mark a function as being tail recursive
    * @param loc
    *   The location of the tail recursive option
    */
  case class TailRecursive(loc: At) extends FunctionOption("tail-recursive")

  //////////////////////////////////////////////////////////////////// CONTEXT

  /** Base trait for all options a Context can have.
    */
  sealed abstract class ContextOption(val name: String) extends OptionValue

  case class ContextPackageOption(loc: At, override val args: Seq[LiteralString]) extends ContextOption("package")

  /** A context's "wrapper" option. This option suggests the bounded context is to be used as a wrapper around an
    * external system and is therefore at the boundary of the context map
    *
    * @param loc
    *   The location of the wrapper option
    */
  case class WrapperOption(loc: At) extends ContextOption("wrapper")

  /** A context's "service" option. This option suggests the bounded context is intended to be a DDD service, similar to
    * a wrapper but without any persistent state and more of a stateless service aspect to its nature
    *
    * @param loc
    *   The location at which the option occurs
    */
  case class ServiceOption(loc: At) extends ContextOption("service")

  /** A context's "gateway" option that suggests the bounded context is intended to be an application gateway to the
    * model. Gateway's provide authentication and authorization access to external systems, usually user applications.
    *
    * @param loc
    *   The location of the gateway option
    */
  case class GatewayOption(loc: At) extends ContextOption("gateway")

  case class ContextTechnologyOption(loc: At, override val args: Seq[LiteralString]) extends ContextOption("technology")

  //////////////////////////////////////////////////////////////////// PROCESSOR

  sealed abstract class StreamletOption(val name: String) extends OptionValue

  case class StreamletTechnologyOption(loc: At, override val args: Seq[LiteralString])
      extends StreamletOption(Options.technology)

  //////////////////////////////////////////////////////////////////// PIPE

  sealed abstract class ConnectorOption(val name: String) extends OptionValue

  case class ConnectorPersistentOption(loc: At) extends ConnectorOption("package")

  case class ConnectorTechnologyOption(loc: At, override val args: Seq[LiteralString])
      extends ConnectorOption("technology")

  //////////////////////////////////////////////////////////////////// SAGA

  /** Base trait for all options applicable to a saga.
    */
  sealed abstract class SagaOption(val name: String) extends OptionValue

  /** A [[SagaOption]] that indicates sequential (serial) execution of the saga actions.
    *
    * @param loc
    *   The location of the sequential option
    */
  case class SequentialOption(loc: At) extends SagaOption("sequential")

  /** A [[SagaOption]] that indicates parallel execution of the saga actions.
    *
    * @param loc
    *   The location of the parallel option
    */
  case class ParallelOption(loc: At) extends SagaOption("parallel")

  case class SagaTechnologyOption(loc: At, override val args: Seq[LiteralString]) extends SagaOption("technology")

  ////////////////////////////////////////////////////////////////// APPLICATION

  sealed abstract class ApplicationOption(val name: String) extends OptionValue

  case class ApplicationTechnologyOption(loc: At, override val args: Seq[LiteralString] = Seq.empty[LiteralString])
      extends ApplicationOption("technology")

  ////////////////////////////////////////////////////////////////// DOMAIN

  /** Base trait for all options a Domain can have.
    */
  sealed abstract class DomainOption(val name: String) extends OptionValue

  /** A context's "wrapper" option. This option suggests the bounded context is to be used as a wrapper around an
    * external system and is therefore at the boundary of the context map
    *
    * @param loc
    *   The location of the wrapper option
    */
  case class DomainPackageOption(loc: At, override val args: Seq[LiteralString]) extends DomainOption("package")

  case class DomainExternalOption(loc: At) extends DomainOption("external")

  case class DomainTechnologyOption(loc: At, override val args: Seq[LiteralString]) extends DomainOption("technology")

  ////////////////////////////////////////////////////////////////// DOMAIN

  sealed abstract class EpicOption(val name: String) extends OptionValue

  case class EpicTechnologyOption(loc: At, override val args: Seq[LiteralString]) extends EpicOption("technology")

  case class EpicSynchronousOption(loc: At) extends EpicOption("synch")

  /** A term definition for the glossary */
  case class Term(
    loc: At,
    id: Identifier,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with VitalDefinitionDefinition {
    override def isEmpty: Boolean = description.isEmpty

    def format: String = s"${Keywords.term} ${id.format}"

    final val kind: String = "Term"
  }

  /** Added to definitions that support a list of term definitions */
  sealed trait WithTerms {
    def terms: Seq[Term]

    def hasTerms: Boolean = terms.nonEmpty
  }

  /** A value to record an inclusion of a file while parsing.
    *
    * @param loc
    *   The location of the include statement in the source
    * @param contents
    *   The Vital Definitions read from the file
    * @param source
    *   A string providing the source (path or URL) of the included source
    */
  case class Include[T <: Definition](
    loc: At = At(RiddlParserInput.empty),
    contents: Seq[T] = Seq.empty[T],
    source: Option[String] = None
  ) extends Definition
      with VitalDefinitionDefinition
      with RootDefinition {

    def id: Identifier = Identifier.empty

    def brief: Option[LiteralString] = Option.empty[LiteralString]

    def description: Option[Description] = None

    override def isRootContainer: Boolean = true

    def format: String = ""

    final val kind: String = "Include"
  }

  /** Added to definitions that support includes */
  sealed trait WithIncludes[T <: Definition] extends Container[T] {
    def includes: Seq[Include[T]]

    def contents: Seq[T] = {
      includes.flatMap(_.contents)
    }
  }

  /** A value that holds the author's information
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
    loc: At,
    id: Identifier,
    name: LiteralString,
    email: LiteralString,
    organization: Option[LiteralString] = None,
    title: Option[LiteralString] = None,
    url: Option[java.net.URL] = None,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with RootDefinition
      with DomainDefinition {
    override def isEmpty: Boolean = {
      name.isEmpty && email.isEmpty && organization.isEmpty && title.isEmpty
    }

    final val kind: String = "Author"

    def format: String = s"${Keywords.author} ${id.format}"
  }

  case class AuthorRef(loc: At, pathId: PathIdentifier) extends Reference[Author] {
    override def format: String = s"${Keywords.author} ${pathId.format}"

    def kind: String = ""
  }

  sealed trait WithAuthors extends Definition {
    def authors: Seq[AuthorRef]

    override def hasAuthors: Boolean = authors.nonEmpty
  }

  sealed trait VitalDefinition[OPT <: OptionValue, DEF <: Definition]
      extends Definition
      with WithOptions[OPT]
      with WithAuthors
      with WithIncludes[DEF]
      with WithTerms {

    import scala.language.implicitConversions

    /** Implicit conversion of boolean to Int for easier computation of statistics below
      *
      * @param b
      *   The boolean to convert to an Int
      * @return
      */
    implicit def bool2int(b: Boolean): Int = if b then 1 else 0

    override def isVital: Boolean = true
  }

  /** Base trait of any definition that is a container and contains types
    */
  sealed trait WithTypes extends Definition {
    def types: Seq[Type]

    override def hasTypes: Boolean = types.nonEmpty
  }

  /** Definition of a Processor. This is a base class for all Processor definitions (things that have inlets, outlets,
    * handlers, and take messages directly with a reference).
    */
  sealed trait Processor[OPT <: OptionValue, DEF <: Definition] extends VitalDefinition[OPT, DEF] with WithTypes {

    def types: Seq[Type]
    def constants: Seq[Constant]
    def functions: Seq[Function]
    def invariants: Seq[Invariant]
    def handlers: Seq[Handler]
    def inlets: Seq[Inlet]
    def outlets: Seq[Outlet]
  }

  /** The root of the containment hierarchy, corresponding roughly to a level about a file.
    *
    * @param contents
    *   The sequence top level definitions contained by this root container
    * @param inputs
    *   The inputs for this root scope
    */
  case class RootContainer(
    contents: Seq[RootDefinition] = Seq.empty[RootDefinition],
    inputs: Seq[RiddlParserInput] = Nil
  ) extends Definition {
    lazy val domains: Seq[Domain] = contents.filter(_.getClass == classOf[Domain]).asInstanceOf[Seq[Domain]]
    lazy val authors: Seq[Author] = contents.filter(_.getClass == classOf[Author]).asInstanceOf[Seq[Author]]

    override def isRootContainer: Boolean = true

    def loc: At = At.empty

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
      RootContainer(Seq.empty[RootDefinition], Seq.empty[RiddlParserInput])
  }

  /** Base trait for the four kinds of message references */
  sealed trait MessageRef extends Reference[Type] {
    def messageKind: AggregateUseCase

    override def format: String =
      s"${messageKind.kind.toLowerCase} ${pathId.format}"
  }

  object MessageRef {
    lazy val empty: MessageRef = new MessageRef {
      def messageKind: AggregateUseCase = RecordCase

      override def pathId: PathIdentifier = PathIdentifier.empty

      override def loc: At = At.empty
    }
  }

  /** A Reference to a command message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the event type
    */
  case class CommandRef(
    loc: At,
    override val id: Option[Identifier] = None,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = CommandCase
  }

  /** A Reference to an event message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the event type
    */
  case class EventRef(
    loc: At,
    override val id: Option[Identifier] = None,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = EventCase
  }

  /** A reference to a query message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the query type
    */
  case class QueryRef(
    loc: At,
    override val id: Option[Identifier] = None,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = QueryCase
  }

  /** A reference to a result message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the result type
    */
  case class ResultRef(
    loc: At,
    override val id: Option[Identifier] = None,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = ResultCase
  }

  /** A reference to a record message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the result type
    */
  case class RecordRef(
    loc: At,
    override val id: Option[Identifier] = None,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = RecordCase
    override def isEmpty: Boolean =
      super.isEmpty && loc.isEmpty && pathId.isEmpty
  }

  /** A definition that represents a constant value for reference in behaviors
    * @param loc
    *   The location in the source of the Constant
    * @param id
    *   The unique identifier of the Constant
    * @param typeEx
    *   The type expression goverining the range of values the constant can have
    * @param value
    *   The value of the constant
    * @param brief
    *   A brief descriptin of the constant
    * @param description
    *   A detailed description of the constant
    */
  case class Constant(
    loc: At,
    id: Identifier,
    typeEx: TypeExpression,
    value: LiteralString,
    brief: Option[LiteralString],
    description: Option[Description]
  ) extends LeafDefinition
      with ProcessorDefinition
      with DomainDefinition {
    override def kind: String = "Constant"

    /** Format the node to a string */
    override def format: String =
      s"${Keywords.const} ${id.format} is ${typeEx.format} = ${value.format}"
  }

  case class ConstantRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Field] {
    override def format: String = s"${Keywords.const} ${pathId.format}"
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
    loc: At,
    id: Identifier,
    typ: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Definition
      with ProcessorDefinition
      with ProjectorDefinition
      with FunctionDefinition
      with DomainDefinition {
    override def contents: Seq[TypeDefinition] = {
      typ match {
        case a: Aggregation                    => a.contents
        case a: AggregateUseCaseTypeExpression => a.contents
        case Enumeration(_, enumerators)       => enumerators
        case _                                 => Seq.empty[TypeDefinition]
      }
    }

    final val kind: String = {
      typ match {
        case AggregateUseCaseTypeExpression(_, useCase, _, _) => useCase.kind
        case _                                                => "Type"
      }
    }

    def format: String = ""
  }

  /** A reference to a type definition
    *
    * @param loc
    *   The location in the source where the reference to the type is made
    * @param pathId
    *   The path identifier of the reference type
    */
  case class TypeRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Type] {
    override def format: String = s"${Keywords.`type`} ${pathId.format}"
  }

  case class FieldRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Field] {
    override def format: String = s"${Keywords.field} ${pathId.format}"
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////// STATEMENTS

  sealed trait Statement extends RiddlValue {
    def kind: String = "Statement"
  }

  /** A statement whose behavior is specified as a text string allowing an arbitrary action to be specified handled by
    * RIDDL's syntax.
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param what
    *   The action to take (emitted as pseudo-code)
    */
  case class ArbitraryStatement(
    loc: At,
    what: LiteralString
  ) extends Statement {
    override def kind: String = "Arbitrary Statement"
    def format: String = what.format
  }

  /** An action that is intended to generate a runtime error in the generated application or otherwise indicate an error
    * condition
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param message
    *   The error message to report
    */
  case class ErrorStatement(
    loc: At,
    message: LiteralString
  ) extends Statement {
    override def kind: String = "Error Statement"
    def format: String = s"error ${message.format}"
  }

  case class SetStatement(
    loc: At,
    field: FieldRef,
    value: LiteralString
  ) extends Statement {
    override def kind: String = "Set Statement"
    def format: String = s"set ${field.format} to ${value.format}"
  }

  /** An action that returns a value from a function
    *
    * @param loc
    *   The location in the source of the publish action
    * @param value
    *   The value to be returned
    */
  case class ReturnStatement(
    loc: At,
    value: LiteralString
  ) extends Statement {
    override def kind: String = "Return Statement"
    def format: String = s"return ${value.format}"
  }

  /** An action that sends a message to an [[Inlet]] or [[Outlet]].
    *
    * @param loc
    *   The location in the source of the send action
    * @param msg
    *   The constructed message to be sent
    * @param portlet
    *   The inlet or outlet to which the message is sent
    */
  case class SendStatement(
    loc: At,
    msg: MessageRef,
    portlet: PortletRef[Portlet]
  ) extends Statement {
    override def kind: String = "Send Statement"
    def format: String = s"send ${msg.format} to ${portlet.format}"
  }

  /** A statement that replies in a handler to a query
    *
    * @param loc
    *   The location in the source of the publish action
    * @param message
    *   The message to be returned
    */
  case class ReplyStatement(
    loc: At,
    message: MessageRef
  ) extends Statement {
    override def kind: String = "Reply Statement"
    def format: String = s"reply ${message.format}"
  }

  /** An statement that morphs the state of an entity to a new structure
    *
    * @param loc
    *   The location of the morph action in the source
    * @param entity
    *   The entity to be affected
    * @param state
    *   The reference to the new state structure
    */
  case class MorphStatement(
    loc: At,
    entity: EntityRef,
    state: StateRef,
    value: MessageRef
  ) extends Statement {
    override def kind: String = "Morph Statement"
    def format: String = s"morph ${entity.format} to ${state.format} with ${value.format}"
  }

  /** An action that changes the behavior of an entity by making it use a new handler for its messages; named for the
    * "become" operation in Akka that does the same for an user.
    *
    * @param loc
    *   The location in the source of the become action
    * @param entity
    *   The entity whose behavior is to change
    * @param handler
    *   The reference to the new handler for the entity
    */
  case class BecomeStatement(
    loc: At,
    entity: EntityRef,
    handler: HandlerRef
  ) extends Statement {
    override def kind: String = "Become Statement"
    def format: String = s"become ${entity.format} to ${handler.format}"
  }

  /** An action that tells a message to an entity. This is very analogous to the tell operator in Akka. Unlike using an
    * Portlet, this implies a direct relationship between the telling entity and the told entity. This action is
    * considered useful in "high cohesion" scenarios. Use [[SendStatement]] to reduce the coupling between entities
    * because the relationship is managed by a [[Context]] 's [[Connector]] instead.
    *
    * @param loc
    *   The location of the tell action
    * @param msg
    *   A constructed message value to send to the entity, probably a command
    * @param processorRef
    *   The processor to which the message is directed
    */
  case class TellStatement(
    loc: At,
    msg: MessageRef,
    processorRef: ProcessorRef[Processor[?, ?]]
  ) extends Statement {
    override def kind: String = "Tell Statement"
    def format: String = s"tell ${msg.format} to ${processorRef.format}"
  }

  case class CallStatement(
    loc: At,
    func: FunctionRef
  ) extends Statement {
    override def kind: String = "Call Statement"
    def format: String = "scall ${func.format}"
  }

  case class ForEachStatement(
    loc: At,
    ref: PathIdentifier,
    do_ : Seq[Statement]
  ) extends Statement {
    override def kind: String = "Foreach Statement"
    def format: String = s"foreach ${ref.format} do \n" +
      do_.map(_.format).mkString("\n") + "end\n"
  }

  case class IfThenElseStatement(
    loc: At,
    cond: LiteralString,
    thens: Seq[Statement],
    elses: Seq[Statement]
  ) extends Statement {
    override def kind: String = "IfThenElse Statement"
    def format: String = s"if ${cond.format} then\n{\n${thens.map(_.format).mkString("  ", "\n  ", "\n}") +
        (if elses.nonEmpty then " else {\n" + elses.map(_.format).mkString("  ", "\n  ", "\n}\n")
         else "\n")}"
  }

  case class StopStatement(
    loc: At
  ) extends Statement {
    override def kind: String = "Stop Statement"
    def format: String = "scall ${func.format}"

  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////// ENTITIES

  /** A reference to an entity
    *
    * @param loc
    *   The location of the entity reference
    * @param pathId
    *   The path identifier of the referenced entity.
    */
  case class EntityRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Entity] {
    override def format: String = s"${Keywords.entity} ${pathId.format}"
  }

  /** A reference to a function.
    *
    * @param loc
    *   The location of the function reference.
    * @param pathId
    *   The path identifier of the referenced function.
    */
  case class FunctionRef(loc: At, pathId: PathIdentifier) extends Reference[Function] {
    override def format: String = s"${Keywords.function} ${pathId.format}"
  }

  /** A function definition which can be part of a bounded context or an entity.
    *
    * @param loc
    *   The location of the function definition
    * @param id
    *   The identifier that names the function
    * @param input
    *   An optional type expression that names and types the fields of the input of the function
    * @param output
    *   An optional type expression that names and types the fields of the output of the function
    * @param types
    *   The set of type definitions for use in the function
    * @param functions
    *   The set of function definitions for use in the function
    * @param statements
    *   The set of statements that define the behavior of this function
    * @param authors
    *   References to the authors that helped write this function
    * @param includes
    *   Inclusion of other files to complete this function definition
    * @param options
    *   The options for this function that might affect how it behaves
    * @param terms
    *   The definition of glossary terms related to this function
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the function.
    */
  case class Function(
    loc: At,
    id: Identifier,
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    types: Seq[Type] = Seq.empty[Type],
    functions: Seq[Function] = Seq.empty[Function],
    statements: Seq[Statement] = Seq.empty[Statement],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[FunctionDefinition]] = Seq
      .empty[Include[FunctionDefinition]],
    options: Seq[FunctionOption] = Seq.empty[FunctionOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[FunctionOption, FunctionDefinition]
      with WithTypes
      with AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with EntityDefinition
      with FunctionDefinition
      with ProjectorDefinition
      with RepositoryDefinition
      with SagaDefinition
      with StreamletDefinition {
    override lazy val contents: Seq[FunctionDefinition] = {
      super.contents ++ input.map(_.fields).getOrElse(Seq.empty[Field]) ++
        output.map(_.fields).getOrElse(Seq.empty[Field]) ++ types ++
        functions
    }

    override def isEmpty: Boolean = statements.isEmpty && input.isEmpty &&
      output.isEmpty

    final val kind: String = "Function"
  }

  /** An invariant expression that can be used in the definition of an entity. Invariants provide conditional
    * expressions that must be true at all times in the lifecycle of an entity.
    *
    * @param loc
    *   The location of the invariant definition
    * @param id
    *   The name of the invariant
    * @param condition
    *   The string representation of the condition that ought to be true
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the invariant.
    */
  case class Invariant(
    loc: At,
    id: Identifier,
    condition: Option[LiteralString] = Option.empty[LiteralString],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with ProcessorDefinition
      with StateDefinition {
    override def isEmpty: Boolean = condition.isEmpty

    def format: String = ""

    final val kind: String = "Invariant"
  }

  /** A sealed trait for the kinds of OnClause that can occur within a Handler definition.
    */
  sealed trait OnClause extends LeafDefinition with HandlerDefinition {
    def statements: Seq[Statement]
  }

  /** Defines the actions to be taken when a message does not match any of the OnMessageClauses. OnOtherClause
    * corresponds to the "other" case of an [[Handler]].
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param statements
    *   A set of examples that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnOtherClause(
    loc: At,
    statements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Other")

    override def isEmpty: Boolean = statements.isEmpty

    override def kind: String = "On Other"

    override def format: String = ""
  }

  /** Defines the actions to be taken when the component this OnClause occurs in is initialized.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param statements
    *   A set of statements that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnInitClause(
    loc: At,
    statements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Init")

    override def isEmpty: Boolean = statements.isEmpty

    override def kind: String = "On Init"

    override def format: String = ""
  }

  /** Defines the actions to be taken when a particular message is received by an entity. [[OnMessageClause]]s are used
    * in the definition of a [[Handler]] with one for each kind of message that handler deals with.
    *
    * @param loc
    *   The location of the "on" clause
    * @param msg
    *   A reference to the message type that is handled
    * @param from
    *   Optional message generating
    * @param statements
    *   A set of statements that define the behavior when the [[msg]] is received.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnMessageClause(
    loc: At,
    msg: MessageRef,
    from: Option[Reference[Definition]],
    statements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(msg.loc, s"On ${msg.format}")

    override def isEmpty: Boolean = statements.isEmpty

    def format: String = ""

    final val kind: String = "OnMessageClause"

    override def resolveNameTo(name: String): Option[Definition] = {
      if msg.id.getOrElse(Identifier.empty).value == name then Some(this) else None
    }
  }

  /** Defines the actions to be taken when the component this OnClause occurs in is initialized.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param statements
    *   A set of statements that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnTerminationClause(
    loc: At,
    statements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Term")

    override def isEmpty: Boolean = statements.isEmpty

    override def kind: String = "On Term"

    override def format: String = ""
  }

  /** A named handler of messages (commands, events, queries) that bundles together a set of [[OnMessageClause]]
    * definitions and by doing so defines the behavior of an entity. Note that entities may define multiple handlers and
    * switch between them to change how it responds to messages over time or in response to changing conditions
    *
    * @param loc
    *   The location of the handler definition
    * @param id
    *   The name of the handler.
    * @param clauses
    *   The set of [[OnMessageClause]] definitions that define how the entity responds to received messages.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the handler
    */
  case class Handler(
    loc: At,
    id: Identifier,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Container[HandlerDefinition]
      with AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with EntityDefinition
      with StateDefinition
      with RepositoryDefinition
      with StreamletDefinition
      with ProjectorDefinition {
    override def isEmpty: Boolean = clauses.isEmpty

    override def contents: Seq[HandlerDefinition] = clauses

    final val kind: String = "Handler"

    def format: String = s"${Keywords.handler} ${id.format}"
  }

  /** A reference to a Handler
    *
    * @param loc
    *   The location of the handler reference
    * @param pathId
    *   The path identifier of the referenced handler
    */
  case class HandlerRef(loc: At, pathId: PathIdentifier) extends Reference[Handler] {
    override def format: String = s"${Keywords.handler} ${pathId.format}"
  }

  /** Represents the state of an entity. The MorphAction can cause the state definition of an entity to change.
    *
    * @param loc
    *   The location of the state definition
    * @param id
    *   The name of the state definition
    * @param typ
    *   A reference to a type definition that provides the range of values that the state may assume.
    * @param handlers
    *   The handler definitions that may occur when this state is active
    * @param invariants
    *   Expressions of boolean logic that must always evaluate to true before and after an entity changes when this
    *   state is active.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the state.
    */
  case class State(
    loc: At,
    id: Identifier,
    typ: TypeRef,
    handlers: Seq[Handler] = Seq.empty[Handler],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends EntityDefinition {

    override def contents: Seq[StateDefinition] = handlers ++ invariants

    def format: String = s"${Keywords.state} ${id.format}"

    final val kind: String = "State"
  }

  /** A reference to an entity's state definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced state definition
    */
  case class StateRef(loc: At, pathId: PathIdentifier) extends Reference[State] {
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
    loc: At,
    id: Identifier,
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    states: Seq[State] = Seq.empty[State],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    handlers: Seq[Handler] = Seq.empty[Handler],
    functions: Seq[Function] = Seq.empty[Function],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    includes: Seq[Include[EntityDefinition]] = Seq
      .empty[Include[EntityDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[EntityOption, EntityDefinition]
      with ContextDefinition {

    override lazy val contents: Seq[EntityDefinition] = {
      super.contents ++ states ++ types ++ handlers ++ functions ++
        invariants ++ terms ++ inlets ++ outlets
    }

    final val kind: String = "Entity"

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty

  }

  sealed trait AdaptorDirection extends RiddlValue

  case class InboundAdaptor(loc: At) extends AdaptorDirection {
    def format: String = "from"
  }

  case class OutboundAdaptor(loc: At) extends AdaptorDirection {
    def format: String = "to"
  }

  /** Definition of an Adaptor. Adaptors are defined in Contexts to convert messages from another bounded context.
    * Adaptors translate incoming messages into corresponding messages using the ubiquitous language of the defining
    * bounded context. There should be one Adapter for each external Context
    *
    * @param loc
    *   Location in the parsing input
    * @param id
    *   Name of the adaptor
    * @param direction
    *   An indication of whether this is an inbound or outbound adaptor.
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
    loc: At,
    id: Identifier,
    direction: AdaptorDirection,
    context: ContextRef,
    handlers: Seq[Handler] = Seq.empty[Handler],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    functions: Seq[Function] = Seq.empty[Function],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    includes: Seq[Include[AdaptorDefinition]] = Seq
      .empty[Include[AdaptorDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[AdaptorOption] = Seq.empty[AdaptorOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[AdaptorOption, AdaptorDefinition]
      with ContextDefinition {
    override lazy val contents: Seq[AdaptorDefinition] = {
      super.contents ++ handlers ++ inlets ++ outlets ++ terms
    }
    final val kind: String = "Adaptor"

  }

  case class AdaptorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Adaptor] {
    override def format: String = s"${Keywords.adaptor} ${pathId.format}"
  }

  /** A RIDDL repository is an abstraction for anything that can retain information(e.g. messages for retrieval at a
    * later time. This might be a relational database, NoSQL database, data lake, API, or something not yet invented.
    * There is no specific technology implied other than the retention and retrieval of information. You should think of
    * repositories more like a message-oriented version of the Java Repository Pattern than any particular kind
    * ofdatabase.
    *
    * @see
    *   https://java-design-patterns.com/patterns/repository/#explanation
    * @param loc
    *   Location in the source of the Repository
    * @param id
    *   The unique identifier for this Repository
    * @param types
    *   The types, typically messages, that the Repository uses
    * @param handlers
    *   The handler for specifying how messages should be handled by the repository
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
    loc: At,
    id: Identifier,
    types: Seq[Type] = Seq.empty[Type],
    handlers: Seq[Handler] = Seq.empty[Handler],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    functions: Seq[Function] = Seq.empty[Function],
    constants: Seq[Constant] = Seq.empty[Constant],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    includes: Seq[Include[RepositoryDefinition]] = Seq
      .empty[Include[RepositoryDefinition]],
    options: Seq[RepositoryOption] = Seq.empty[RepositoryOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[RepositoryOption, RepositoryDefinition]
      with ContextDefinition {
    override def kind: String = "Repository"

    override lazy val contents: Seq[RepositoryDefinition] = {
      super.contents ++ types ++ handlers ++ inlets ++ outlets ++ terms ++ constants
    }
  }

  /** A reference to a repository definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class RepositoryRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Projector] {
    override def format: String = s"${Keywords.repository} ${pathId.format}"
  }

  /** Projectors get their name from Euclidean Geometry but are probably more analogous to a relational database view.
    * The concept is very simple in RIDDL: projectors gather data from entities and other sources, transform that data
    * into a specific record type, and support querying that data arbitrarily.
    *
    * @see
    *   https://en.wikipedia.org/wiki/View_(SQL)).
    * @see
    *   https://en.wikipedia.org/wiki/Projector_(mathematics)
    * @param loc
    *   Location in the source of the Projector
    * @param id
    *   The unique identifier for this Projector
    * @param authors
    *   The authors of this definition
    * @param options
    *   Options that can be used by the translators
    * @param types
    *   The type definitions necessary to construct the query results
    * @param handlers
    *   Specifies how to handle
    * @param terms
    *   Definitions of terms about this Projector
    * @param brief
    *   A brief description of this Projector
    * @param description
    *   A detailed description of this Projector
    */
  case class Projector(
    loc: At,
    id: Identifier,
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[ProjectorOption] = Seq.empty[ProjectorOption],
    includes: Seq[Include[ProjectorDefinition]] = Seq.empty[Include[ProjectorDefinition]],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    handlers: Seq[Handler] = Seq.empty[Handler],
    functions: Seq[Function] = Seq.empty[Function],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[ProjectorOption, ProjectorDefinition]
      with ContextDefinition
      with WithTypes {
    override lazy val contents: Seq[ProjectorDefinition] = {
      super.contents ++ handlers ++ invariants ++ terms
    }
    final val kind: String = "Projector"

  }

  /** A replicated value within a context. Integer, Map and Set values will use CRDTs
    * @param loc
    *   The location of
    * @param typeExp
    *   The type of the replica
    */
  case class Replica(
    loc: At,
    id: Identifier,
    typeExp: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with ContextDefinition {
    final val kind: String = "Replica"
    final val format: String = s"$kind ${id.format}"
  }

  /** A reference to an context's projector definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class ProjectorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Projector] {
    override def format: String = s"${Keywords.projector} ${pathId.format}"
  }

  /** A bounded context definition. Bounded contexts provide a definitional boundary on the language used to describe
    * some aspect of a system. They imply a tightly integrated ecosystem of one or more microservices that share a
    * common purpose. Context can be used to house entities, read side projectors, sagas, adaptations to other contexts,
    * apis, and etc.
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
    loc: At,
    id: Identifier,
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    entities: Seq[Entity] = Seq.empty[Entity],
    adaptors: Seq[Adaptor] = Seq.empty[Adaptor],
    sagas: Seq[Saga] = Seq.empty[Saga],
    streamlets: Seq[Streamlet] = Seq.empty[Streamlet],
    functions: Seq[Function] = Seq.empty[Function],
    terms: Seq[Term] = Seq.empty[Term],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    includes: Seq[Include[ContextDefinition]] = Seq.empty[Include[ContextDefinition]],
    handlers: Seq[Handler] = Seq.empty[Handler],
    projectors: Seq[Projector] = Seq.empty[Projector],
    repositories: Seq[Repository] = Seq.empty[Repository],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    connections: Seq[Connector] = Seq.empty[Connector],
    replicas: Seq[Replica] = Seq.empty[Replica],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[ContextOption, ContextDefinition]
      with DomainDefinition {
    override lazy val contents: Seq[ContextDefinition] = super.contents ++
      types ++ entities ++ adaptors ++ sagas ++ streamlets ++ functions ++
      terms ++ handlers ++ projectors ++ repositories ++ inlets ++
      outlets ++ connections

    final val kind: String = "Context"

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty

  }
  object Context {
    lazy val empty: Context = Context(At.empty, Identifier.empty)
  }

  /** A reference to a bounded context
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier for the referenced context
    */
  case class ContextRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Context] {
    override def format: String = s"context ${pathId.format}"
  }

  /** A sealed trait for Inlets and Outlets */
  sealed trait Portlet extends Definition

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
    loc: At,
    id: Identifier,
    type_ : Reference[Type],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Portlet
      with LeafDefinition
      with ProcessorDefinition
      with AlwaysEmpty {
    def format: String =
      s"${Keywords.inlet} ${id.format} is ${type_.format}"

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
    loc: At,
    id: Identifier,
    type_ : Reference[Type],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Portlet
      with LeafDefinition
      with ProcessorDefinition
      with AlwaysEmpty {
    def format: String = s"${Keywords.outlet} ${id.format} is ${type_.format}"

    final val kind: String = "Outlet"
  }

  case class Connector(
    loc: At,
    id: Identifier,
    options: Seq[ConnectorOption] = Seq.empty[ConnectorOption],
    flows: Option[TypeRef] = Option.empty[TypeRef],
    from: Option[OutletRef] = Option.empty[OutletRef],
    to: Option[InletRef] = Option.empty[InletRef],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = Option.empty[Description]
  ) extends LeafDefinition
      with ContextDefinition
      with WithOptions[ConnectorOption] {
    final override def isEmpty: Boolean = super.isEmpty && flows.isEmpty &&
      from.isEmpty && to.isEmpty

    final val kind: String = "Connector"
    override def format: String = s"${Keywords.connector}"
  }

  sealed trait StreamletShape extends RiddlValue {
    def keyword: String
  }

  case class Void(loc: At) extends StreamletShape {
    def format: String = Keywords.void

    def keyword: String = Keywords.void
  }

  case class Source(loc: At) extends StreamletShape {
    def format: String = Keywords.source

    def keyword: String = Keywords.source
  }

  case class Sink(loc: At) extends StreamletShape {
    def format: String = Keywords.sink

    def keyword: String = Keywords.sink
  }

  case class Flow(loc: At) extends StreamletShape {
    def format: String = Keywords.flow

    def keyword: String = Keywords.flow
  }

  case class Merge(loc: At) extends StreamletShape {
    def format: String = Keywords.merge

    def keyword: String = Keywords.merge
  }

  case class Split(loc: At) extends StreamletShape {
    def format: String = Keywords.split

    def keyword: String = Keywords.split
  }

  case class Router(loc: At) extends StreamletShape {
    def format: String = Keywords.router

    def keyword: String = Keywords.router
  }

  /** Definition of a Streamlet. A computing element for processing data from [[Inlet]]s to [[Outlet]]s. A processor's
    * processing is specified by free text statements in [[Handler]]s. Streamlets come in various shapes: Source, Sink,
    * Flow, Merge, Split, and Router depending on how many inlets and outlets they have
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
    * @param handlers
    *   Definitions of how the processor handles each event type
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the processor
    */
  case class Streamlet(
    loc: At,
    id: Identifier,
    shape: StreamletShape,
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    handlers: Seq[Handler] = Seq.empty[Handler],
    functions: Seq[Function] = Seq.empty[Function],
    constants: Seq[Constant] = Seq.empty[Constant],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    types: Seq[Type] = Seq.empty[Type],
    includes: Seq[Include[StreamletDefinition]] = Seq
      .empty[Include[StreamletDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[StreamletOption] = Seq.empty[StreamletOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[StreamletOption, StreamletDefinition]
      with ContextDefinition {
    override def contents: Seq[StreamletDefinition] = super.contents ++
      inlets ++ outlets ++ handlers ++ terms ++ constants

    final val kind: String = shape.getClass.getSimpleName

    shape match {
      case Source(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.isEmpty),
          s"Invalid Source Streamlet ins: ${outlets.size} == 1, ${inlets.size} == 0"
        )
      case Sink(_) =>
        require(
          isEmpty || (outlets.isEmpty && inlets.size == 1),
          "Invalid Sink Streamlet"
        )
      case Flow(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.size == 1),
          "Invalid Flow Streamlet"
        )
      case Merge(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.size >= 2),
          "Invalid Merge Streamlet"
        )
      case Split(_) =>
        require(
          isEmpty || (outlets.size >= 2 && inlets.size == 1),
          "Invalid Split Streamlet"
        )
      case Router(_) =>
        require(
          isEmpty || (outlets.size >= 2 && inlets.size >= 2),
          "Invalid Router Streamlet"
        )
      case Void(_) =>
        require(
          isEmpty || (outlets.isEmpty && inlets.isEmpty),
          "Invalid Void Stream"
        )
    }

  }

  /** A reference to an context's projector definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class StreamletRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Streamlet] {
    override def format: String = s"${Keywords.streamlet} ${pathId.format}"
  }

  /** Sealed base trait of references to [[Inlet]]s or [[Outlet]]s
    *
    * @tparam T
    *   The type of definition to which the references refers.
    */
  sealed trait PortletRef[+T <: Portlet] extends Reference[T]

  /** A reference to an [[Inlet]]
    *
    * @param loc
    *   The location of the inlet reference
    * @param pathId
    *   The path identifier of the referenced [[Inlet]]
    */
  case class InletRef(loc: At, pathId: PathIdentifier) extends PortletRef[Inlet] {
    override def format: String = s"${Keywords.inlet} ${pathId.format}"
  }

  /** A reference to an [[Outlet]]
    *
    * @param loc
    *   The location of the outlet reference
    * @param pathId
    *   The path identifier of the referenced [[Outlet]]
    */
  case class OutletRef(loc: At, pathId: PathIdentifier) extends PortletRef[Outlet] {
    override def format: String = s"${Keywords.outlet} ${pathId.format}"
  }

  /** The definition of one step in a saga with its undo step and example.
    *
    * @param loc
    *   The location of the saga action definition
    * @param id
    *   The name of the SagaAction
    * @param doStatements
    *   The command to be done.
    * @param undoStatements
    *   The command that undoes [[doStatements]]
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga action
    */
  case class SagaStep(
    loc: At,
    id: Identifier,
    doStatements: Seq[Statement] = Seq.empty[Statement],
    undoStatements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with SagaDefinition {
    def format: String = s"${Keywords.step} ${id.format}"

    final val kind: String = "SagaStep"
  }

  /** The definition of a Saga based on inputs, outputs, and the set of [[SagaStep]]s involved in the saga. Sagas define
    * a computing action based on a variety of related commands that must all succeed atomically or have their effects
    * undone.
    *
    * @param loc
    *   The location of the Saga definition
    * @param id
    *   The name of the saga
    * @param options
    *   The options of the saga
    * @param input
    *   A definition of the aggregate input values needed to invoke the saga, if any.
    * @param output
    *   A definition of the aggregate output values resulting from invoking the saga, if any.
    * @param sagaSteps
    *   The set of [[SagaStep]]s that comprise the saga.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga.
    */
  case class Saga(
    loc: At,
    id: Identifier,
    options: Seq[SagaOption] = Seq.empty[SagaOption],
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    sagaSteps: Seq[SagaStep] = Seq.empty[SagaStep],
    functions: Seq[Function] = Seq.empty[Function],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[SagaDefinition]] = Seq.empty[Include[SagaDefinition]],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[SagaOption, SagaDefinition]
      with ContextDefinition
      with DomainDefinition {
    override lazy val contents: Seq[SagaDefinition] = {
      super.contents ++ input.map(_.fields).getOrElse(Seq.empty[Field]) ++
        output.map(_.fields).getOrElse(Seq.empty[Field]) ++ sagaSteps ++ terms
    }
    final val kind: String = "Saga"

    override def isEmpty: Boolean = super.isEmpty && options.isEmpty &&
      input.isEmpty && output.isEmpty

  }

  case class SagaRef(loc: At, pathId: PathIdentifier) extends Reference[Saga] {
    def format: String = s"${Keywords.saga} ${pathId.format}"
  }

  /** An User (Role) who is the initiator of the user story. Users may be persons or machines
    *
    * @param loc
    *   The location of the user in the source
    * @param id
    *   The name (role) of the user
    * @param is_a
    *   What kind of thing the user is
    * @param brief
    *   A brief description of the user
    * @param description
    *   A longer description of the user and its role
    */
  case class User(
    loc: At,
    id: Identifier,
    is_a: LiteralString,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with DomainDefinition {
    def format: String = s"${Keywords.user} ${id.format} is ${is_a.format}"

    override def kind: String = "User"
  }

  /** A reference to an User using a path identifier
    *
    * @param loc
    *   THe location of the User in the source code
    * @param pathId
    *   The path identifier that locates the User
    */
  case class UserRef(loc: At, pathId: PathIdentifier) extends Reference[User] {
    def format: String = s"${Keywords.user} ${pathId.format}"
  }

  sealed trait Interaction extends UseCaseDefinition {

    /** Format the node to a string */
    override def format: String = s"$kind ${id.format}"
  }

  /** An interaction expression that specifies that each contained expression should be executed in parallel
    *
    * @param loc
    *   Location of the parallel group
    * @param contents
    *   The expressions to execute in parallel
    * @param brief
    *   A brief description of the parallel group
    */
  case class ParallelInteractions(
    loc: At,
    id: Identifier = Identifier.empty,
    contents: Seq[Interaction] = Seq.empty[Interaction],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Interaction {
    override def kind: String = "Parallel Interaction"
  }

  /** An interaction expression that specifies that each contained expression should be executed in strict sequential
    * order
    *
    * @param loc
    *   Location of the sequence
    * @param id
    *   Identifier for the interaction
    * @param contents
    *   The interactions to execute in sequence
    * @param brief
    *   A brief description of the sequence group
    * @param description
    *   A longer description of the sequence
    */
  case class SequentialInteractions(
    loc: At,
    id: Identifier = Identifier.empty,
    contents: Seq[Interaction] = Seq.empty[Interaction],
    brief: Option[LiteralString],
    description: Option[Description] = None
  ) extends Interaction {
    override def kind: String = "Sequential Interaction"
  }

  /** An interaction expression that specifies that its contents are optional
    *
    * @param loc
    *   The location of the optional group
    * @param contents
    *   The optional expressions
    * @param brief
    *   A brief description of the optional group
    */
  case class OptionalInteractions(
    loc: At,
    id: Identifier = Identifier.empty,
    contents: Seq[Interaction] = Seq.empty[Interaction],
    brief: Option[LiteralString],
    description: Option[Description] = None
  ) extends Interaction {
    override def kind: String = "Optional Interaction"
  }

  /** A very vague step just written as text */
  case class VagueInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    relationship: LiteralString,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Interaction {
    override def kind: String = "Vague Interaction"

    override def contents: Seq[Definition] = Seq.empty[Definition]
  }

  /** One abstract step in an Interaction between things. The set of case classes associated with this sealed trait
    * provide more type specificity to these three fields.
    */
  sealed trait GenericInteraction extends Interaction with LeafDefinition {
    def from: Reference[Definition]

    def relationship: LiteralString

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
    *   A brief description of the interaction step
    */
  case class ArbitraryInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    from: Reference[Definition],
    relationship: LiteralString,
    to: Reference[Definition],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Arbitrary Interaction"
  }

  case class SelfInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    from: Reference[Definition],
    relationship: LiteralString,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Self Interaction"
    override def to: Reference[Definition] = from
  }

  /** An interaction where an User receives output
    * @param loc
    *   The locaiton of the interaction in the source
    * @param from
    *   The output received
    * @param relationship
    *   THe name of the relationship
    * @param to
    *   THe user that receives the output
    * @param brief
    *   A brief description of this interaction
    */
  case class ShowOutputInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    from: OutputRef,
    relationship: LiteralString,
    to: UserRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Show Output Interaction"
  }

  /** A interaction where and User provides input
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   The user providing the input
    * @param relationship
    *   A description of the relationship in this interaction
    * @param to
    *   The input definition that receives the input
    * @param brief
    *   A description of this interaction step
    */
  case class TakeInputInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    from: UserRef,
    relationship: LiteralString,
    to: InputRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Take Input Interaction"
  }

  /** The definition of a Jacobsen Use Case RIDDL defines these epics by allowing a linkage between the user and RIDDL
    * applications or bounded contexts.
    * @param loc
    *   Where in the source this use case occurs
    * @param id
    *   The unique identifier for this use case
    * @param contents
    *   The interactions between users and system components that define the use case.
    * @param brief
    *   A brief description of this use case
    * @param description
    *   A longer description of this use case
    */
  case class UseCase(
    loc: At,
    id: Identifier,
    userStory: Option[UserStory] = None,
    contents: Seq[Interaction] = Seq.empty[Interaction],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends EpicDefinition
      with Container[Interaction] {
    override def kind: String = "UseCase"
    override def format: String = s"${Keywords.case_} ${id.format}"
  }

  /** An agile user story definition in the usual "As a {role} I want {capability} so that {benefit}" style.
    *
    * @param loc
    *   Location of the user story
    * @param user
    *   The user, or instigator, of the story.
    * @param capability
    *   The capability the user wishes to utilize
    * @param benefit
    *   The benefit of that utilization
    */
  case class UserStory(
    loc: At = At.empty,
    user: UserRef = UserRef(At.empty, PathIdentifier.empty),
    capability: LiteralString = LiteralString.empty,
    benefit: LiteralString = LiteralString.empty
  ) extends RiddlValue {
    def format: String = ""

    override def isEmpty: Boolean = false
  }

  /** The definition of an Epic that bundles multiple Jacobsen Use Cases into an overall story about user interactions
    * with the system. This define functionality from the perspective of users (men or machines) interactions with the
    * system that is part of their role.
    *
    * @param loc
    *   The location of the Epic definition
    * @param id
    *   The name of the Epic
    * @param userStory
    *   The [[UserStory]] (per agile and xP) that provides the overall big picture of this Epic
    * @param shownBy
    *   A list of URLs to visualizations or other materials related to the epic
    * @param cases
    *   A list of UseCase's that define the epic
    * @param brief
    *   A brief description (one sentence) for use in the glossary and summaries.
    * @param description
    *   An more detailed description of the Epic
    */
  case class Epic(
    loc: At,
    id: Identifier,
    userStory: Option[UserStory] = Option.empty[UserStory],
    shownBy: Seq[java.net.URL] = Seq.empty[java.net.URL],
    cases: Seq[UseCase] = Seq.empty[UseCase],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[EpicDefinition]] = Seq.empty[Include[EpicDefinition]],
    options: Seq[EpicOption] = Seq.empty[EpicOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[EpicOption, EpicDefinition]
      with DomainDefinition {
    override def contents: Seq[EpicDefinition] = {
      super.contents ++ cases ++ terms
    }

    override def isEmpty: Boolean = {
      contents.isEmpty && shownBy.isEmpty && userStory.isEmpty
    }

    final val kind: String = "Epic"

    override def format: String = s"${Keywords.epic} ${id.format}"
  }

  /** A reference to a Story definintion.
    * @param loc
    *   Location of the StoryRef
    * @param pathId
    *   The path id of the referenced Story
    */
  case class EpicRef(loc: At, pathId: PathIdentifier) extends Reference[Epic] {
    def format: String = s"${Keywords.epic} ${pathId.format}"
  }

  /** A group of GroupDefinition that can be treated as a whole. For example, a form, a button group, etc.
    * @param loc
    *   The location of the group
    * @param id
    *   The unique identifier of the group
    * @param elements
    *   The list of GroupDefinition
    * @param brief
    *   A brief description of the group
    * @param description
    *   A more detailed description of the group
    */
  case class Group(
    loc: At,
    alias: String,
    id: Identifier,
    elements: Seq[GroupDefinition] = Seq.empty[GroupDefinition],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends ApplicationDefinition
      with GroupDefinition {
    override def kind: String = "Group"
    override def isAppRelated: Boolean = true

    override lazy val contents: Seq[GroupDefinition] = { elements }

    /** Format the node to a string */
    override def format: String = s"group ${id.value}"
  }

  /** A Group contained within a group
   *
   * @param loc
   * Location of the contained group
   * @param id
   * The name of the group contained
   * @param group
   * The contained group as a reference to that group
   */
  case class ContainedGroup(
    loc: At,
    id: Identifier,
    group: GroupRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition with GroupDefinition {
    def kind: String = "ContainedGroup"

    def format: String = s"contains ${id.format} ${Readability.as} ${group.format}"
  }

  /** A Reference to a Group
    * @param loc
    *   The At locator of the group reference
    * @param pathId
    *   The path to the referenced group
    */
  case class GroupRef(loc: At, pathId: PathIdentifier) extends Reference[Group] {
    def format: String = s"${Keywords.group} ${pathId.format}"
  }

  /** A UI Element that presents some information to the user
    *
    * @param loc
    *   Location of the view in the source
    * @param id
    *   unique identifier oof the view
    * @param putOut
    *   A result reference for the data too be presented
    * @param outputs
    *   Any contained outputs
    * @param brief
    *   A brief description of the view
    * @param description
    *   A detailed description of the view
    */
  case class Output(
    loc: At,
    nounAlias: String,
    id: Identifier,
    verbAlias: String,
    putOut: TypeRef,
    outputs: Seq[OutputDefinition] = Seq.empty[OutputDefinition],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends ApplicationDefinition
      with OutputDefinition
      with GroupDefinition {
    override def kind: String = if nounAlias.nonEmpty then nounAlias else "output"
    override def isAppRelated: Boolean = true

    override lazy val contents: Seq[OutputDefinition] = outputs

    /** Format the node to a string */
    override def format: String = s"$kind $verbAlias ${putOut.format}"
  }

  /** A reference to an View using a path identifier
    *
    * @param loc
    *   The location of the ViewRef in the source code
    * @param pathId
    *   The path identifier that refers to the View
    */
  case class OutputRef(loc: At, pathId: PathIdentifier) extends Reference[Output] {
    def format: String = s"${Keywords.output} ${pathId.format}"
  }

  /** A Give is a UI Element to allow the user to 'give' some data to the application. It is analogous to a form in HTML
    *
    * @param loc
    *   Location of the Give
    * @param id
    *   Name of the give
    * @param putIn
    *   a Type reference of the type given by the user
    * @param brief
    *   A brief description of the Give
    * @param description
    *   a detailed description of the Give
    */
  case class Input(
    loc: At,
    nounAlias: String,
    id: Identifier,
    verbAlias: String,
    putIn: TypeRef,
    inputs: Seq[InputDefinition] = Seq.empty[InputDefinition],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends ApplicationDefinition
      with GroupDefinition
      with InputDefinition {
    override def kind: String = if nounAlias.nonEmpty then nounAlias else "input"
    override def isAppRelated: Boolean = true

    override lazy val contents: Seq[Definition] = inputs

    /** Format the node to a string */
    override def format: String = {
      s"$kind $verbAlias ${putIn.format}"
    }
  }

  /** A reference to an Input using a path identifier
    *
    * @param loc
    *   THe location of the GiveRef in the source code
    * @param pathId
    *   The path identifier that refers to the Give
    */
  case class InputRef(loc: At, pathId: PathIdentifier) extends Reference[Input] {
    def format: String = s"${Keywords.input} ${pathId.format}"
  }

  /** An application from which a person, robot, or other active agent (the user) will obtain information, or to which
    * that user will provided information.
    * @param loc
    *   The location of the application in the source
    * @param id
    *   The unique identifier for the application
    * @param options
    *   The options for the application
    * @param types
    *   Types that are needed for the communication with the user
    * @param groups
    *   A list of group definitions needed by the application
    * @param handlers
    *   The handlers for this application to process incoming messages
    * @param inlets
    *   Message inlets for the application
    * @param outlets
    *   Message outlets for the application
    * @param authors
    *   Author definitions for the application, for attribution of application components.
    * @param terms
    *   Definitions of terms useful in comprehending the application's purpose
    * @param includes
    *   Included source code
    * @param brief
    *   A brief description of the application
    * @param description
    *   A longer description of the application.
    */
  case class Application(
    loc: At,
    id: Identifier,
    options: Seq[ApplicationOption] = Seq.empty[ApplicationOption],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    groups: Seq[Group] = Seq.empty[Group],
    handlers: Seq[Handler] = Seq.empty[Handler],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    functions: Seq[Function] = Seq.empty[Function],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[ApplicationDefinition]] = Seq.empty,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Processor[ApplicationOption, ApplicationDefinition]
      with DomainDefinition {
    override def kind: String = "Application"
    override def isAppRelated: Boolean = true
    override lazy val contents: Seq[ApplicationDefinition] = {
      super.contents ++ types ++ groups ++ terms ++ includes
    }
  }

  /** A reference to an Application using a path identifier
    *
    * @param loc
    *   THe location of the ApplicationRef in the source code
    * @param pathId
    *   The path identifier that refers to the Application
    */
  case class ApplicationRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Application] {
    def format: String = s"${Keywords.application} ${pathId.format}"
  }

  /** The definition of a domain. Domains are the highest building block in RIDDL and may be nested inside each other to
    * form a hierarchy of domains. Generally, domains follow hierarchical organization structure but other taxonomies
    * and ontologies may be modelled with domains too.
    *
    * @param loc
    *   The location of the domain definition
    * @param id
    *   The name of the domain
    * @param options
    *   Options for the domain
    * @param types
    *   Type definitions with a domain (nearly global) scope, with applicability to many contexts or subdomains
    * @param contexts
    *   The contexts defined in the scope of the domain
    * @param users
    *   User definitions for use in epics
    * @param epics
    *   Story definitions for this domain
    * @param applications
    *   Application definitions for this domain
    * @param domains
    *   Nested sub-domains within this domain
    * @param terms
    *   Definition of terms pertaining to this domain that provide explanation of concepts from the domain.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the domain.
    */
  case class Domain(
    loc: At,
    id: Identifier,
    options: Seq[DomainOption] = Seq.empty[DomainOption],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    authorDefs: Seq[Author] = Seq.empty[Author],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    contexts: Seq[Context] = Seq.empty[Context],
    users: Seq[User] = Seq.empty[User],
    epics: Seq[Epic] = Seq.empty[Epic],
    sagas: Seq[Saga] = Seq.empty[Saga],
    applications: Seq[Application] = Seq.empty[Application],
    domains: Seq[Domain] = Seq.empty[Domain],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[DomainDefinition]] = Seq
      .empty[Include[DomainDefinition]],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[DomainOption, DomainDefinition]
      with RootDefinition
      with WithTypes
      with DomainDefinition {

    override lazy val contents: Seq[DomainDefinition] = {
      super.contents ++ domains ++ types ++ constants ++ contexts ++ users ++
        epics ++ applications ++ terms ++ authorDefs
    }
    final val kind: String = "Domain"

  }

  /** A reference to a domain definition
    *
    * @param loc
    *   The location at which the domain definition occurs
    * @param pathId
    *   The path identifier for the referenced domain.
    */
  case class DomainRef(loc: At, pathId: PathIdentifier) extends Reference[Domain] {
    override def format: String = s"${Keywords.domain} ${pathId.format}"
  }
}
