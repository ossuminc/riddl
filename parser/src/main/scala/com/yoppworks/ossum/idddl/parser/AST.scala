package com.yoppworks.ossum.idddl.parser

sealed trait AST

/** Abstract Syntax Tree
  * This object defines the model for processing IDDL and producing a
  * raw AST from it. This raw AST has no referential integrity, it just
  * results from applying the parsing rules to the input. The RawAST models
  * produced from parsing are syntactically correct but have no semantic
  * validation. The Transformation passes convert RawAST model to AST model
  * which is referentially and semantically consistent (or the user gets an
  * error).
  */
object AST {

  sealed trait Type extends AST

  sealed trait Def extends AST {
    def index: Int
    def name: String
  }

  sealed trait Ref extends AST {
    def name: String
  }

  case object String extends Type
  case object Boolean extends Type
  case object Number extends Type
  case object Id extends Type
  case object Date extends Type
  case object Time extends Type
  case object TimeStamp extends Type
  case object URL extends Type

  case class Enumeration(of: Seq[String]) extends Type
  case class Alternation(of: Seq[Type]) extends Type
  case class Aggregation(of: Map[String, Type]) extends Type

  case class Optional(element: Type) extends Type
  case class ZeroOrMore(of: Type) extends Type
  case class OneOrMore(of: Type) extends Type

  case class TypeRef(name: String) extends Ref with Type
  case class TypeDef(index: Int, name: String, typ: Type) extends Def

  case class ChannelRef(name: String) extends Ref
  case class ChannelDef(
    index: Int,
    name: String,
    commands: Seq[String] = Seq.empty[String],
    queries: Seq[String] = Seq.empty[String],
    events: Seq[String] = Seq.empty[String],
    results: Seq[String] = Seq.empty[String]
  ) extends Def

  sealed trait MessageRef extends Ref
  sealed trait MessageDef extends Def

  case class CommandRef(name: String) extends MessageRef
  case class CommandDef(
    index: Int,
    name: String,
    typ: Type,
    events: EventRefs
  ) extends MessageDef

  case class EventRef(name: String) extends MessageRef
  type EventRefs = Seq[EventRef]
  case class EventDef(index: Int, name: String, typ: Type) extends MessageDef

  case class QueryRef(name: String) extends MessageRef
  case class QueryDef(
    index: Int,
    name: String,
    typ: Type,
    results: Seq[ResultRef]
  ) extends MessageDef

  case class ResultRef(name: String) extends MessageRef
  type ResultRefs = Seq[ResultRef]
  case class ResultDef(index: Int, name: String, typ: Type) extends MessageDef

  sealed trait EntityOption
  case object EntityAggregate extends EntityOption
  case object EntityPersistent extends EntityOption
  case object EntityConsistent extends EntityOption
  case object EntityAvailable extends EntityOption

  case class EntityRef(name: String) extends Ref

  /** Definition of an Entity
    *
    * @param options The options for the entity
    * @param index The index location in the input
    * @param name The name of the entity
    * @param typ The type of the entity's value
    * @param consumes A reference to the channel from which the entity consumes
    * @param produces A reference to the channel to which the entity produces
    */
  case class EntityDef(
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    index: Int,
    name: String,
    typ: Type,
    consumes: Option[ChannelRef] = None,
    produces: Option[ChannelRef] = None // reference to the channel to which it
    // produces
  ) extends Def

  trait TranslationRule {
    def name: String
    def channel: String
  }

  case class MessageTranslationRule(
    name: String,
    channel: String,
    input: String,
    output: String,
    rule: String
  )

  /** Definition of an Adaptor
    * Adaptors are defined in Contexts to convert messaging from one Context to
    * another. Adaptors translate incoming events from other Contexts into
    * commands or events that its owning context can understand. There should be
    * one Adaptor for each external Context
    *
    * @param index Location in the parsing input
    * @param name Name of the adaptor
    */
  case class AdaptorDef(
    index: Int,
    name: String,
    targetDomain: Option[DomainRef] = None,
    targetContext: ContextRef
    // Details TBD
  ) extends Def

  sealed trait ContextOption
  case object DeviceOption extends ContextOption
  case object ServiceOption extends ContextOption
  case object FunctionOption extends ContextOption
  case object GatewayOption extends ContextOption

  case class ContextRef(name: String) extends Ref

  case class ContextDef(
    options: Seq[ContextOption] = Seq.empty[ContextOption],
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

  /** Definition of a Scenario
    * Scenarios define an exemplary interaction between the system being designed
    * and its users. The basic ideas of a Scenario are much like a UML Sequence
    * Diagram.
    *
    * @param index Where in the input the Scenario is defined
    * @param prefix The path identifier's prefix
    * @param name The name of the scenario
    */
  case class ScenarioDef(
    index: Int,
    prefix: Seq[String],
    name: String,
    actors: Seq[ActorRoleDef],
    interactions: Seq[Interaction]
  ) extends Def

  sealed trait Interaction {
    def label: String
  }
  type Interactions = Seq[Interaction]

  /** An Interaction based on entity messaging
    *
    * @param label The displayable text that describes the interaction
    * @param sender A reference to the entity sending the message
    * @param receiver A reference to the entity receiving the message
    * @param message A reference to the kind of message sent & received
    */
  case class MessageInteraction(
    label: String,
    sender: EntityRef,
    receiver: EntityRef,
    message: MessageRef
  ) extends Interaction

  case class ActorRoleDef(
    index: Int,
    name: String,
    responsibilities: Seq[String] = Seq.empty[String],
    capacities: Seq[String] = Seq.empty[String]
  ) extends Def

  case class ActorRoleRef(name: String) extends Ref

  case class ActorInteraction(
    label: String,
    actor: ActorRoleRef,
    entity: EntityRef
  ) extends Interaction

  case class DomainRef(name: String) extends Ref

  case class DomainDef(
    index: Int,
    prefix: Seq[String],
    name: String,
    channels: Seq[ChannelDef] = Seq.empty[ChannelDef],
    contexts: Seq[ContextDef] = Seq.empty[ContextDef],
    scenarios: Seq[ScenarioDef] = Seq.empty[ScenarioDef]
  ) extends Def

}
