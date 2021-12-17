package com.yoppworks.ossum.riddl.generation.hugo

final case class HugoDescription(brief: String, details: List[String], citations: List[String])

final case class HugoField(
  name: String,
  fieldType: HugoType,
  description: Option[HugoDescription] = None)

sealed trait HugoRepr {
  type Self
  def name: String
  def fullName: String
  def description: Option[HugoDescription]
  def hasDescription: Boolean = description.nonEmpty
}

sealed trait RiddlNoDescription extends HugoRepr {
  final val description = None
  override final val hasDescription = false
}

sealed trait RiddlContainer extends HugoRepr {
  def namespace: Namespace
  def contents: Iterable[HugoRepr] = namespace.allChildren
    .collect { case Namespace(Some(repr), _, _) => repr }
  def types: Iterable[HugoType] = contents.collect { case ht: HugoType => ht }
}

sealed trait RiddlSelfTyped[S <: HugoRepr] extends HugoRepr {
  final type Self = S
}

sealed trait NameFromFullName {
  def fullName: String
  final lazy val name: String = fullName.split('.').last
}

final case class HugoRoot(namespace: Namespace)
    extends HugoRepr with RiddlContainer with RiddlNoDescription {
  type Self = HugoRoot
  def name: String = namespace.name
  def fullName: String = "_root_"
}

final case class HugoDomain(
  name: String,
  namespace: Namespace,
  description: Option[HugoDescription])
    extends HugoRepr with RiddlContainer {
  type Self = HugoDomain
  def fullName: String = namespace.fullName
  def contexts: Iterable[HugoContext] = contents.collect { case hc: HugoContext => hc }
  def domains: Iterable[HugoDomain] = contents.collect { case hd: HugoDomain => hd }
}

final case class HugoContext(
  name: String,
  namespace: Namespace,
  description: Option[HugoDescription])
    extends HugoRepr with RiddlContainer {
  type Self = HugoContext
  def fullName: String = namespace.fullName
  def entities: Iterable[HugoEntity] = contents.collect { case he: HugoEntity => he }
}

sealed trait HugoType extends HugoRepr
sealed trait MustResolve extends HugoType
object HugoType {
  sealed trait RiddlType[Self <: HugoType]
      extends HugoType with RiddlNoDescription with RiddlSelfTyped[Self] with NameFromFullName

  sealed trait RiddlDescType[Self <: HugoType]
      extends HugoType with RiddlSelfTyped[Self] with NameFromFullName

  sealed abstract class PredefinedType private[hugo] (kind: String)
      extends HugoType with RiddlNoDescription with RiddlSelfTyped[PredefinedType] {
    protected def this() = this("")
    val name = if (kind.isEmpty) getClass.getSimpleName.stripSuffix("$") else kind
    def fullName: String = name
  }

  object PredefinedType {
    def unapply(predefinedType: PredefinedType): Option[String] = Some(predefinedType.name)

    /* All `Predefined` types, which are still `HugoType`s */
    case object Text extends PredefinedType("String")
    case object Bool extends PredefinedType("Boolean")
    case object Number extends PredefinedType
    case object Integer extends PredefinedType
    case object Decimal extends PredefinedType
    case object Real extends PredefinedType
    case object Date extends PredefinedType
    case object Time extends PredefinedType
    case object DateTime extends PredefinedType
    case object Timestamp extends PredefinedType
    case object Duration extends PredefinedType
    case object URL extends PredefinedType
    case object LatLong extends PredefinedType
    case object UUID extends PredefinedType
    case object Bottom extends PredefinedType("Nothing")
  }

  case class UnhandledType(fullName: String) extends RiddlType[UnhandledType]

  case class ReferenceType(
    fullName: String,
    ref: TypeReference,
    description: Option[HugoDescription])
      extends MustResolve with RiddlDescType[ReferenceType]

  case class TypeReference(namespace: Namespace, fullName: String)
      extends MustResolve with RiddlNoDescription with RiddlSelfTyped[TypeReference] {
    def name: String = s"TypeReference($fullName)"
  }
  object TypeReference {
    def apply(ns: Namespace, nameParts: Seq[String]): TypeReference = {
      assert(nameParts.nonEmpty, "nameParts must not be empty")
      TypeReference(ns, nameParts.mkString("."))
    }
  }

  final case class EntityRef(fullName: String, entity: HugoEntity) extends RiddlType[EntityRef]

  final case class Alias(fullName: String, aliasForType: HugoType) extends RiddlType[Alias]

  final case class Optional(fullName: String, innerType: HugoType) extends RiddlType[Optional]

  final case class Collection(fullName: String, contains: HugoType, canBeEmpty: Boolean)
      extends RiddlType[Collection]

  object Enumeration {
    sealed trait EnumOption {
      def name: String
    }

    case class EnumOptionNamed(name: String) extends EnumOption
    case class EnumOptionValue(name: String, value: Int) extends EnumOption
    case class EnumOptionTyped(name: String, subtype: HugoType) extends EnumOption
  }

  final case class Enumeration(
    fullName: String,
    of: Seq[Enumeration.EnumOption],
    description: Option[HugoDescription])
      extends RiddlDescType[Enumeration]

  final case class Variant(
    fullName: String,
    of: Seq[HugoType],
    description: Option[HugoDescription])
      extends RiddlDescType[Variant]

  final case class Mapping(
    fullName: String,
    from: HugoType,
    to: HugoType,
    description: Option[HugoDescription])
      extends RiddlDescType[Alias]

  final case class Range(fullName: String, min: Int, max: Int, description: Option[HugoDescription])
      extends RiddlDescType[Range]

  final case class RegexPattern(
    fullName: String,
    pattern: Seq[String],
    description: Option[HugoDescription])
      extends RiddlDescType[RegexPattern]

  final case class UniqueId(
    fullName: String,
    entity: EntityRef,
    description: Option[HugoDescription])
      extends RiddlDescType[UniqueId]

  final case class Record(
    fullName: String,
    fields: Set[HugoField],
    description: Option[HugoDescription])
      extends RiddlDescType[Record]
}

case class HugoEntity(
  name: String,
  namespace: Namespace,
  options: HugoEntity.Options,
  states: Set[HugoEntity.State],
  handlers: Set[HugoEntity.Handler],
  functions: Set[HugoEntity.Function],
  invariants: Set[HugoEntity.Invariant],
  description: Option[HugoDescription])
    extends HugoRepr with RiddlContainer {
  def fullName: String = namespace.fullName
}

object HugoEntity {

  type Options = EntityOption.ValueSet
  object EntityOption extends Enumeration {
    val none = ValueSet.empty
    val EventSourced, ValueType, Aggregate, Persistent, Consistent, Available, FiniteStateMachine =
      Value
  }

  case class State(fullName: String, fields: Set[HugoField]) extends NameFromFullName

  case class Handler(fullName: String, clauses: Seq[OnClause]) extends NameFromFullName

  sealed trait OnClause {
    def onType: HugoType
  }
  object OnClause {
    case class Command(onType: HugoType) extends OnClause
    case class Event(onType: HugoType) extends OnClause
    case class Query(onType: HugoType) extends OnClause
    case class Action(onType: HugoType) extends OnClause
  }

  case class Function(fullName: String, inputs: Set[HugoField], output: HugoType)
      extends NameFromFullName

  case class Invariant(fullName: String, expression: List[String]) extends NameFromFullName
}
