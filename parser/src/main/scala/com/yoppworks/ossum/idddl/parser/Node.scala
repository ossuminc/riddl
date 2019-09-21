package com.yoppworks.ossum.idddl.parser

sealed trait Node

object Node {

  sealed trait Type extends Node
  sealed trait CardinalizedType extends Type

  type DefRef = String

  sealed trait Def extends Node {
    def index: Int
    def name: DefRef
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

  case class ChannelDef(
    index: Int,
    name: String,
    commands: Seq[CommandDef],
    queries: Seq[QueryDef],
    events: Seq[EventDef],
    results: Seq[ResultDef]
  ) extends Def

  case class CommandDef(
    index: Int,
    name: DefRef,
    typ: Type,
    events: Seq[String]
  ) extends MessageDef

  case class EventDef(index: Int, name: DefRef, typ: Type) extends MessageDef
  case class QueryDef(index: Int, name: DefRef, typ: Type, results: Seq[DefRef])
      extends MessageDef
  case class ResultDef(index: Int, name: DefRef, typ: Type) extends MessageDef

  sealed trait EntityOption
  case object EntityAggregate extends EntityOption
  case object EntityPersistent extends EntityOption
  case object EntityConsistent extends EntityOption
  case object EntityAvailable extends EntityOption

  case class EntityDef(
    index: Int,
    name: DefRef,
    options: Seq[EntityOption],
    typ: Type,
    consumes: DefRef, // reference to the channel from which it consumes
    produces: DefRef // reference to the channel to which it produces
  ) extends Def

  case class TypeDef(index: Int, name: String, typ: Type) extends Def

  case class TranslationRule(
    name: String,
    channel: ChannelDef
  )

  case class AdaptorDef(
    index: Int,
    name: String,
    inputChannel: Option[String],
    inputRules: Map[String, TranslationRule],
    outputChannel: Option[String]
  )
  case class ContextDef(
    index: Int,
    name: String,
    types: Seq[TypeDef] = Seq.empty[TypeDef],
    commands: Seq[CommandDef] = Seq.empty[CommandDef],
    events: Seq[EventDef] = Seq.empty[EventDef],
    queries: Seq[QueryDef] = Seq.empty[QueryDef],
    results: Seq[ResultDef] = Seq.empty[ResultDef],
    entities: Seq[EntityDef] = Seq.empty[EntityDef],
    translators: Seq[AdaptorDef] = Seq.empty[AdaptorDef]
  ) extends Def

  case class DomainPath(path: Seq[String]) {
    require(path.nonEmpty, "Too few path name elements")
    def parent: Seq[String] = path.dropRight(1)
    def name: String = path.last
    override def toString: String =
      path.mkString(".")
  }

  case class DomainDef(
    index: Int,
    name: String,
    parentDomain: Option[DomainDef],
    name_path: DomainPath,
    channels: Seq[ChannelDef] = Seq.empty[ChannelDef],
    contexts: Seq[ContextDef] = Seq.empty[ContextDef]
  ) extends Def
}
