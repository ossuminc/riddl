package com.yoppworks.ossum.idddl.parser

sealed trait AST

object AST {

  sealed trait Type extends AST
  sealed trait CardinalizedType extends Type

  sealed trait Def extends AST {
    def index: Int
  }

  case object String extends Type
  case object Boolean extends Type
  case object Number extends Type
  case object Id extends Type
  case object Date extends Type
  case object Time extends Type
  case object TimeStamp extends Type
  case object URL extends Type
  case class NamedType(typeName: String) extends Type
  case class Enumeration(of: Seq[String]) extends Type
  case class Alternation(of: Seq[Type]) extends Type
  case class Aggregation(of: Map[String, Type]) extends Type

  case class Optional(element: Type) extends CardinalizedType
  case class ZeroOrMore(of: Type) extends CardinalizedType
  case class OneOrMore(of: Type) extends CardinalizedType

  sealed trait MessageDef extends Def
  case class CommandDef(index: Int,
                        name: String,
                        typ: Type,
                        events: Seq[String])
      extends MessageDef
  case class EventDef(index: Int, name: String, typ: Type)
      extends MessageDef
  case class QueryDef(index: Int,
                      name: String,
                      typ: Type,
                      results: Seq[String])
      extends MessageDef
  case class ResultDef(index: Int, name: String, typ: Type)
      extends MessageDef

  case class ObjectDef(index: Int, name: String, typ: Type) extends Def

  sealed trait EntityOption
  case object EntityAggregate extends EntityOption
  case object EntityPersistent extends EntityOption

  case class EntityDef(
                        index: Int,
                        name: String,
                        options: Seq[EntityOption],
                        typ: Type,
                        consumes: Seq[String],
                        produces: Seq[String]
  ) extends Def

  case class DomainPath(path: Seq[String])
  case class DomainDef(
    index: Int, name_path: DomainPath, children: Seq[ContextDef]
  ) extends Def
  case class ContextDef(index: Int, name: String, children: Seq[Def])
      extends Def
  case class TypeDef(index: Int, name: String, typ: Type) extends Def

}
