package com.yoppworks.ossum.riddl.generation.hugo

trait Nameable {
  def name: String
  def fullName: String
}

trait NameableFromFullName extends Nameable {
  def fullName: String
  final lazy val name: String = fullName.split('.').last
}

final case class HugoDescription(brief: String, details: List[String], citations: List[String])

final case class HugoField(
  fullName: String,
  fieldType: RiddlType,
  description: Option[HugoDescription] = None)
    extends NameableFromFullName

sealed trait RiddlType {
  def fullName: String
}
sealed trait MustResolve extends RiddlType

object RiddlType {

  sealed abstract class PredefinedType private[hugo] (kind: String) extends RiddlType {
    protected def this() = this("")
    val fullName = if (kind.isEmpty) getClass.getSimpleName.stripSuffix("$") else kind
    override def toString: String = fullName
  }

  object PredefinedType {
    def unapply(predefinedType: PredefinedType): Option[String] = Some(predefinedType.fullName)

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

  case class UnhandledType(fullName: String) extends RiddlType

  case class EntityReference(
    namespace: String,
    fullName: String)
      extends RiddlType with MustResolve {
    override def toString: String = s"EntityReference($namespace, $fullName)"
  }

  object EntityReference {
    def apply(ns: String, nameParts: Seq[String]): EntityReference = {
      assert(nameParts.nonEmpty, "nameParts must not be empty")
      EntityReference(ns, nameParts.mkString("."))
    }
  }

  case class TypeReference(
    namespace: String,
    fullName: String)
      extends RiddlType with MustResolve {
    override def toString: String = s"TypeReference($namespace, $fullName)"
  }

  object TypeReference {
    def apply(ns: String, nameParts: Seq[String]): TypeReference = {
      assert(nameParts.nonEmpty, "nameParts must not be empty")
      TypeReference(ns, nameParts.mkString("."))
    }
  }

  sealed abstract class RiddlTypeNamed(val fullName: String) extends RiddlType {
    override def toString: String = fullName
  }

  final case class EntityRef(entity: HugoEntity)
      extends RiddlTypeNamed(s"EntityRef(${entity.fullName})")

  final case class TypeRef(hugoType: HugoType)
      extends RiddlTypeNamed(s"TypeRef(${hugoType.fullName})")

  final case class Alias(aliasForType: RiddlType)
      extends RiddlTypeNamed(s"TypeAlias($aliasForType)")

  final case class Optional(innerType: RiddlType) extends RiddlTypeNamed(s"Optional($innerType)")

  final case class Collection(contains: RiddlType, canBeEmpty: Boolean)
      extends RiddlTypeNamed(if (canBeEmpty) { s"Collection($contains)" }
      else { s"NonEmptyCollection($contains)" })

  object Enumeration {
    sealed trait EnumOption {
      def name: String
    }

    case class EnumOptionNamed(name: String) extends EnumOption
    case class EnumOptionValue(name: String, value: Int) extends EnumOption
    case class EnumOptionTyped(name: String, subtype: RiddlType) extends EnumOption
  }

  final case class Enumeration(of: Seq[Enumeration.EnumOption])
      extends RiddlTypeNamed(s"Enumeration(${of.map(_.name).mkString(",")})")

  final case class Variant(of: Seq[RiddlType])
      extends RiddlTypeNamed(s"Variant(${of.map(_.toString).mkString(",")})")

  final case class Mapping(
    from: RiddlType,
    to: RiddlType)
      extends RiddlTypeNamed(s"Mapping(from: $from, to: $to)")

  final case class Range(min: Int, max: Int) extends RiddlTypeNamed(s"Range(from: $min, to: $max)")

  final case class RegexPattern(pattern: Seq[String])
      extends RiddlTypeNamed(s"RegexPattern($pattern)")

  final case class UniqueId(appliesTo: RiddlType)
      extends RiddlTypeNamed(s"UniqueId(appliesTo: $appliesTo)")

  final case class Record(fields: Set[HugoField])
      extends RiddlTypeNamed(
        s"Record(${fields.map(f => s"${f.name}: ${f.fieldType}").mkString(", ")})"
      )
}
