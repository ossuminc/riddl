package com.yoppworks.ossum.riddl.parser

import scala.collection.immutable.ListMap

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

  /** The root trait of all things RIDDL AST */
  sealed trait RiddlNode

  /** A location of an item in the input  */
  case class Location(line: Int = 0, col: Int = 0) {
    override def toString: String = {
      s"${line + 1}:${col + 1}"
    }
  }

  object Location {
    implicit def apply(pair: (Int, Int)): Location = {
      Location(pair._1, pair._2)
    }
  }

  /**  */
  sealed trait RiddlValue extends RiddlNode {
    def loc: Location
  }

  case class Identifier(loc: Location, value: String) extends RiddlValue
  case class RiddlOption(loc: Location, name: String) extends RiddlValue
  case class LiteralString(loc: Location, s: String) extends RiddlValue
  case class LiteralInteger(loc: Location, n: BigInt) extends RiddlValue
  case class LiteralDecimal(loc: Location, d: BigDecimal) extends RiddlValue
  case class PathIdentifier(loc: Location, value: Seq[String])
      extends RiddlValue

  case class Explanation(
    loc: Location,
    markdown: Seq[String] = Seq.empty[String]
  ) extends RiddlValue

  case class SeeAlso(
    loc: Location,
    citations: Seq[String] = Seq.empty[String]
  ) extends RiddlValue

  case class Addendum(
    loc: Location,
    explanation: Option[Explanation],
    seeALso: Option[SeeAlso]
  ) extends RiddlValue

  sealed trait Reference extends RiddlValue {
    def id: Identifier
  }

  sealed trait Definition extends RiddlValue {
    def id: Identifier
    def addendum: Option[Addendum]
  }

  sealed trait Type extends RiddlValue

  sealed trait TypeExpression extends Type {
    def id: Identifier
  }
  case class TypeRef(loc: Location, id: Identifier)
      extends Reference
      with TypeExpression

  abstract class PredefinedType(name: String) extends TypeExpression {
    def id: Identifier = Identifier(loc, name)
  }

  case class Strng(loc: Location) extends PredefinedType("String")
  case class Bool(loc: Location) extends PredefinedType("Boolean")
  case class Number(loc: Location) extends PredefinedType("Number") /* eventually turn this
  into:
  case object Integer extends PredefinedType("Integer")
  case object Decimal extends PredefinedType("Decimal")
   */
  case class Id(loc: Location) extends PredefinedType("Id")
  case class Date(loc: Location) extends PredefinedType("Date")
  case class Time(loc: Location) extends PredefinedType("Time")
  case class TimeStamp(loc: Location) extends PredefinedType("TimeStamp")
  case class URL(loc: Location) extends PredefinedType("URL")

  case class Optional(loc: Location, id: Identifier) extends TypeExpression
  case class ZeroOrMore(loc: Location, id: Identifier) extends TypeExpression
  case class OneOrMore(loc: Location, id: Identifier) extends TypeExpression

  sealed trait TypeDefinition extends Type
  case class Enumeration(loc: Location, of: Seq[Identifier])
      extends TypeDefinition
  case class Alternation(loc: Location, of: Seq[TypeExpression])
      extends TypeDefinition
  case class Aggregation(loc: Location, of: ListMap[Identifier, TypeExpression])
      extends TypeDefinition

  case class TypeDef(
    loc: Location,
    id: Identifier,
    typ: Type,
    addendum: Option[Addendum] = None
  ) extends Definition

  case class ChannelRef(loc: Location, id: Identifier) extends Reference
  case class ChannelDef(
    loc: Location,
    id: Identifier,
    commands: Seq[CommandRef] = Seq.empty[CommandRef],
    events: Seq[EventRef] = Seq.empty[EventRef],
    queries: Seq[QueryRef] = Seq.empty[QueryRef],
    results: Seq[ResultRef] = Seq.empty[ResultRef],
    addendum: Option[Addendum] = None
  ) extends Definition

  sealed trait MessageReference extends Reference
  sealed trait MessageDefinition extends Definition

  case class CommandRef(loc: Location, id: Identifier) extends MessageReference
  case class CommandDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    events: EventRefs,
    addendum: Option[Addendum] = None
  ) extends MessageDefinition

  case class EventRef(loc: Location, id: Identifier) extends MessageReference
  type EventRefs = Seq[EventRef]
  case class EventDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    addendum: Option[Addendum] = None
  ) extends MessageDefinition

  case class QueryRef(loc: Location, id: Identifier) extends MessageReference
  case class QueryDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    result: ResultRef,
    addendum: Option[Addendum] = None
  ) extends MessageDefinition

  case class ResultRef(loc: Location, id: Identifier) extends MessageReference
  case class ResultDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    addendum: Option[Addendum] = None
  ) extends MessageDefinition

  sealed trait EntityValue extends RiddlValue

  abstract class EntityOption(name: String) extends EntityValue {
    def id: Identifier = Identifier(loc, name)
  }

  case class EntityAggregate(loc: Location) extends EntityOption("aggregate")
  case class EntityPersistent(loc: Location) extends EntityOption("persistent")
  case class EntityConsistent(loc: Location) extends EntityOption("consistent")
  case class EntityAvailable(loc: Location) extends EntityOption("available")
  case class EntityDevice(loc: Location) extends EntityOption("device")

  case class EntityRef(loc: Location, id: Identifier) extends Reference

  sealed trait FeatureValue extends RiddlValue
  case class Given(loc: Location, fact: LiteralString) extends FeatureValue
  case class When(loc: Location, situation: LiteralString) extends FeatureValue
  case class Then(loc: Location, result: LiteralString) extends FeatureValue
  case class Background(loc: Location, givens: Seq[Given] = Seq.empty[Given])
      extends FeatureValue
  case class ExampleDef(
    loc: Location,
    id: Identifier,
    description: LiteralString,
    givens: Seq[Given] = Seq.empty[Given],
    whens: Seq[When] = Seq.empty[When],
    thens: Seq[Then] = Seq.empty[Then],
    addendum: Option[Addendum] = None
  ) extends Definition

  case class FeatureDef(
    loc: Location,
    id: Identifier,
    description: Seq[String],
    background: Option[Background] = None,
    examples: Seq[ExampleDef] = Seq.empty[ExampleDef],
    addendum: Option[Addendum] = None
  ) extends Definition

  case class InvariantDef(
    loc: Location,
    id: Identifier,
    expression: LiteralString,
    addendum: Option[Addendum] = None
  ) extends Definition

  /** Definition of an Entity
    *
    * @param options The options for the entity
    * @param loc The location in the input
    * @param id The name of the entity
    * @param typ The type of the entity's value
    * @param consumes A reference to the channel from which the entity consumes
    * @param produces A reference to the channel to which the entity produces
    */
  case class EntityDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    consumes: Option[ChannelRef] = None,
    produces: Option[ChannelRef] = None,
    features: Seq[FeatureDef] = Seq.empty[FeatureDef],
    invariants: Seq[InvariantDef] = Seq.empty[InvariantDef],
    addendum: Option[Addendum] = None
  ) extends Definition

  trait TranslationRule extends Definition {
    def channel: String
  }

  case class MessageTranslationRule(
    loc: Location,
    id: Identifier,
    channel: String,
    input: String,
    output: String,
    rule: String,
    addendum: Option[Addendum] = None
  ) extends TranslationRule

  /** Definition of an Adaptor
    * Adaptors are defined in Contexts to convert messaging from one Context to
    * another. Adaptors translate incoming events from other Contexts into
    * commands or events that its owning context can understand. There should be
    * one Adaptor for each external Context
    *
    * @param loc Location in the parsing input
    * @param id Name of the adaptor
    */
  case class AdaptorDef(
    loc: Location,
    id: Identifier,
    targetDomain: Option[DomainRef] = None,
    targetContext: ContextRef,
    addendum: Option[Addendum] = None
    // Details TBD
  ) extends Definition

  sealed trait ContextOption extends RiddlValue
  case class WrapperOption(loc: Location) extends ContextOption
  case class FunctionOption(loc: Location) extends ContextOption
  case class GatewayOption(loc: Location) extends ContextOption

  case class ContextRef(loc: Location, id: Identifier) extends Reference

  case class ContextDef(
    loc: Location,
    id: Identifier,
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    types: Seq[TypeDef] = Seq.empty[TypeDef],
    commands: Seq[CommandDef] = Seq.empty[CommandDef],
    events: Seq[EventDef] = Seq.empty[EventDef],
    queries: Seq[QueryDef] = Seq.empty[QueryDef],
    results: Seq[ResultDef] = Seq.empty[ResultDef],
    entities: Seq[EntityDef] = Seq.empty[EntityDef],
    adaptors: Seq[AdaptorDef] = Seq.empty[AdaptorDef],
    interactions: Seq[InteractionDef] = Seq.empty[InteractionDef],
    addendum: Option[Addendum] = None
  ) extends Definition

  /** Definition of an Interaction
    * Interactions define an exemplary interaction between the system being
    * designed and other actors. The basic ideas of an Interaction are much
    * like UML Sequence Diagram.
    *
    * @param loc Where in the input the Scenario is defined
    * @param id The name of the scenario
    * @param roles The roles defined for the interaction
    * @param actions The actions that constitute the interaction
    */
  case class InteractionDef(
    loc: Location,
    id: Identifier,
    roles: Seq[RoleDef] = Seq.empty[RoleDef],
    actions: Seq[ActionDef] = Seq.empty[ActionDef],
    addendum: Option[Addendum] = None
  ) extends Definition

  sealed trait RoleOption extends RiddlValue
  case class HumanOption(loc: Location) extends RoleOption
  case class DeviceOption(loc: Location) extends RoleOption

  case class RoleDef(
    loc: Location,
    id: Identifier,
    options: Seq[RoleOption] = Seq.empty[RoleOption],
    responsibilities: Seq[LiteralString] = Seq.empty[LiteralString],
    capacities: Seq[LiteralString] = Seq.empty[LiteralString],
    addendum: Option[Addendum] = None
  ) extends Definition

  case class RoleRef(loc: Location, id: Identifier) extends Reference

  sealed trait ActionDef extends Definition

  type Actions = Seq[ActionDef]

  sealed trait MessageOption extends RiddlValue
  case class SynchOption(loc: Location) extends MessageOption
  case class AsynchOption(loc: Location) extends MessageOption
  case class ReplyOption(loc: Location) extends MessageOption

  /** An Interaction based on entity messaging
    * @param options Options for the message
    * @param loc Where the message is located in the input
    * @param id The displayable text that describes the interaction
    * @param sender A reference to the entity sending the message
    * @param receiver A reference to the entity receiving the message
    * @param message A reference to the kind of message sent & received
    */
  case class MessageActionDef(
    loc: Location,
    id: Identifier,
    options: Seq[MessageOption] = Seq.empty[MessageOption],
    sender: EntityRef,
    receiver: EntityRef,
    message: MessageReference,
    addendum: Option[Addendum] = None
  ) extends ActionDef

  case class DirectiveActionDef(
    loc: Location,
    id: Identifier,
    options: Seq[MessageOption] = Seq.empty[MessageOption],
    role: RoleRef,
    entity: EntityRef,
    message: MessageReference,
    addendum: Option[Addendum] = None
  ) extends ActionDef

  case class ProcessingActionDef(
    loc: Location,
    id: Identifier,
    entity: EntityRef,
    description: LiteralString,
    addendum: Option[Addendum] = None
  ) extends ActionDef

  case class DeletionActionDef(
    loc: Location,
    id: Identifier,
    entity: EntityRef,
    addendum: Option[Addendum] = None
  ) extends ActionDef

  case class CreationActionDef(
    loc: Location,
    id: Identifier,
    entity: EntityRef,
    addendum: Option[Addendum] = None
  ) extends ActionDef

  case class DomainRef(loc: Location, id: Identifier) extends Reference

  case class DomainDef(
    loc: Location,
    id: Identifier,
    subdomain: Option[Identifier] = None,
    types: Seq[TypeDef] = Seq.empty[TypeDef],
    channels: Seq[ChannelDef] = Seq.empty[ChannelDef],
    interactions: Seq[InteractionDef] = Seq.empty[InteractionDef],
    contexts: Seq[ContextDef] = Seq.empty[ContextDef],
    addendum: Option[Addendum] = None
  ) extends Definition

  type Domains = Seq[DomainDef]
}
