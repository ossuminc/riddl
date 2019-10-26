package com.yoppworks.ossum.riddl.language

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
      s"($line:$col)"
    }
  }

  object Location {
    val empty: Location = Location()

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
    seeAlso: Option[SeeAlso]
  ) extends RiddlValue

  sealed trait Reference extends RiddlValue {
    def id: Identifier
  }

  sealed trait Definition extends RiddlValue {
    def id: Identifier
    def addendum: Option[Addendum]
    def kind: String
    def identify: String = s"$kind '${id.value}'"
  }

  sealed trait Container extends Definition

  case class RootContainer(content: Seq[Container]) extends Container {
    def id: Identifier = Identifier((0, 0), "root")
    def addendum: Option[Addendum] = None
    def kind: String = "RootContainer"
    def loc: Location = (0, 0)
  }

  object RootContainer {
    val empty: RootContainer = RootContainer(Seq.empty[Container])
  }

  sealed trait TypeExpression extends RiddlValue

  case class TypeRef(loc: Location, id: Identifier)
      extends Reference
      with TypeExpression

  case class Optional(loc: Location, texp: TypeExpression)
      extends TypeExpression
  case class ZeroOrMore(loc: Location, texp: TypeExpression)
      extends TypeExpression
  case class OneOrMore(loc: Location, texp: TypeExpression)
      extends TypeExpression

  case class Enumeration(loc: Location, of: Seq[Identifier])
      extends TypeExpression
  case class Alternation(loc: Location, of: Seq[TypeExpression])
      extends TypeExpression
  case class Aggregation(loc: Location, of: ListMap[Identifier, TypeExpression])
      extends TypeExpression
  case class Mapping(loc: Location, from: TypeExpression, to: TypeExpression)
      extends TypeExpression
  case class RangeType(loc: Location, min: LiteralInteger, max: LiteralInteger)
      extends TypeExpression
  case class ReferenceType(
    loc: Location,
    entity: EntityRef
  ) extends TypeExpression

  sealed trait TypeDefinition extends Definition

  abstract class PredefinedType(locattion: Location, name: String)
      extends TypeExpression {
    def loc: Location
    def id: Identifier = Identifier(loc, name)
    def addendum: Option[Addendum] = None
    def kind: String = name
  }

  case class Strng(loc: Location) extends PredefinedType(loc, "String")
  case class Bool(loc: Location) extends PredefinedType(loc, "Boolean")
  case class Number(loc: Location) extends PredefinedType(loc, "Number")
  case class Integer(loc: Location) extends PredefinedType(loc, "Integer")
  case class Decimal(loc: Location) extends PredefinedType(loc, "Decimal")
  case class Date(loc: Location) extends PredefinedType(loc, "Date")
  case class Time(loc: Location) extends PredefinedType(loc, "Time")
  case class DateTime(loc: Location) extends PredefinedType(loc, "DateTime")
  case class TimeStamp(loc: Location) extends PredefinedType(loc, "TimeStamp")
  case class URL(loc: Location) extends PredefinedType(loc, "URL")
  case class Pattern(loc: Location, pattern: LiteralString)
      extends PredefinedType(loc, "Pattern")
  case class UniqueId(loc: Location, entityName: Identifier)
      extends PredefinedType(loc, "Id")

  case class TypeDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    addendum: Option[Addendum] = None
  ) extends TypeDefinition {
    def kind: String = "Type"
  }

  case class ChannelRef(
    loc: Location,
    id: Identifier
  ) extends Reference
  case class ChannelDef(
    loc: Location,
    id: Identifier,
    commands: Seq[CommandRef] = Seq.empty[CommandRef],
    events: Seq[EventRef] = Seq.empty[EventRef],
    queries: Seq[QueryRef] = Seq.empty[QueryRef],
    results: Seq[ResultRef] = Seq.empty[ResultRef],
    addendum: Option[Addendum] = None
  ) extends Definition {
    def kind: String = "Channel"
  }

  sealed trait MessageReference extends Reference
  sealed trait MessageDefinition extends Definition

  case class CommandRef(
    loc: Location,
    id: Identifier
  ) extends MessageReference
  case class CommandDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    events: EventRefs,
    addendum: Option[Addendum] = None
  ) extends MessageDefinition {
    def kind: String = "Command"
  }

  case class EventRef(
    loc: Location,
    id: Identifier
  ) extends MessageReference
  type EventRefs = Seq[EventRef]
  case class EventDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    addendum: Option[Addendum] = None
  ) extends MessageDefinition {
    def kind: String = "Event"
  }

  case class QueryRef(
    loc: Location,
    id: Identifier
  ) extends MessageReference
  case class QueryDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    result: ResultRef,
    addendum: Option[Addendum] = None
  ) extends MessageDefinition {
    def kind: String = "Query"
  }

  case class ResultRef(
    loc: Location,
    id: Identifier
  ) extends MessageReference
  case class ResultDef(
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    addendum: Option[Addendum] = None
  ) extends MessageDefinition {
    def kind: String = "Result"
  }

  sealed trait EntityValue extends RiddlValue

  sealed abstract class EntityOption(name: String) extends EntityValue {
    def id: Identifier = Identifier(loc, name)
  }

  case class EntityAggregate(loc: Location) extends EntityOption("aggregate")
  case class EntityPersistent(loc: Location) extends EntityOption("persistent")
  case class EntityConsistent(loc: Location) extends EntityOption("consistent")
  case class EntityAvailable(loc: Location) extends EntityOption("available")

  sealed abstract class EntityKind(name: String) extends EntityValue {
    def id: Identifier = Identifier(loc, name)
  }
  case class SoftwareEntityKind(loc: Location) extends EntityKind("software")
  case class DeviceEntityKind(loc: Location) extends EntityKind("device")
  case class PersonEntityKind(loc: Location) extends EntityKind("person")

  case class EntityRef(
    loc: Location,
    id: Identifier
  ) extends Reference

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
  ) extends Definition {
    def kind: String = "Example"
  }

  case class FeatureRef(
    loc: Location,
    id: Identifier
  ) extends Reference
  case class FeatureDef(
    loc: Location,
    id: Identifier,
    description: Seq[LiteralString],
    background: Option[Background] = None,
    examples: Seq[ExampleDef] = Seq.empty[ExampleDef],
    addendum: Option[Addendum] = None
  ) extends Container {
    def kind: String = "Feature"
  }

  case class FunctionRef(
    loc: Location,
    id: Identifier
  ) extends Reference
  case class FunctionDef(
    loc: Location,
    id: Identifier,
    inputs: Seq[TypeExpression] = Seq.empty[TypeRef],
    outputs: Seq[TypeExpression] = Seq.empty[TypeRef],
    description: Seq[String] = Seq.empty[String],
    addendum: Option[Addendum] = None
  ) extends Definition {
    def kind: String = "Function"
  }

  case class InvariantRef(
    loc: Location,
    id: Identifier
  ) extends Reference
  case class InvariantDef(
    loc: Location,
    id: Identifier,
    expression: Seq[LiteralString],
    addendum: Option[Addendum] = None
  ) extends Definition {
    def kind: String = "Invariant"
  }

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
    entityKind: EntityKind,
    loc: Location,
    id: Identifier,
    typ: TypeExpression,
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    consumes: Seq[ChannelRef] = Seq.empty[ChannelRef],
    produces: Seq[ChannelRef] = Seq.empty[ChannelRef],
    features: Seq[FeatureDef] = Seq.empty[FeatureDef],
    functions: Seq[FunctionDef] = Seq.empty[FunctionDef],
    invariants: Seq[InvariantDef] = Seq.empty[InvariantDef],
    addendum: Option[Addendum] = None
  ) extends Container {
    def kind: String = "Entity"
  }

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
  ) extends TranslationRule {
    def kind: String = "Message Translation Rule"
  }

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
  ) extends Container {
    def kind: String = "Adaptor"
  }

  sealed trait ContextOption extends RiddlValue
  case class WrapperOption(loc: Location) extends ContextOption
  case class FunctionOption(loc: Location) extends ContextOption
  case class GatewayOption(loc: Location) extends ContextOption

  case class ContextRef(
    loc: Location,
    id: Identifier
  ) extends Reference

  case class ContextDef(
    loc: Location,
    id: Identifier,
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    types: Seq[TypeDef] = Seq.empty[TypeDef],
    commands: Seq[CommandDef] = Seq.empty[CommandDef],
    events: Seq[EventDef] = Seq.empty[EventDef],
    queries: Seq[QueryDef] = Seq.empty[QueryDef],
    results: Seq[ResultDef] = Seq.empty[ResultDef],
    channels: Seq[ChannelDef] = Seq.empty[ChannelDef],
    entities: Seq[EntityDef] = Seq.empty[EntityDef],
    adaptors: Seq[AdaptorDef] = Seq.empty[AdaptorDef],
    interactions: Seq[InteractionDef] = Seq.empty[InteractionDef],
    addendum: Option[Addendum] = None
  ) extends Container {
    def kind: String = "Context"
  }

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
  ) extends Container {
    def kind: String = "Interaction"
  }

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
  ) extends Definition {
    def kind: String = "Role"
  }

  case class RoleRef(
    loc: Location,
    id: Identifier
  ) extends Reference

  sealed trait ActionDef extends Definition {
    def reactions: Seq[Reaction]
  }

  /** Used to capture reactions to actions. Actions include reactions in
    * their definition to model the precipitating reactions to the action.
    */
  case class Reaction(
    loc: Location,
    id: Identifier,
    entity: EntityRef,
    function: FunctionRef,
    arguments: Seq[LiteralString],
    addendum: Option[Addendum] = None
  )

  type Actions = Seq[ActionDef]

  sealed trait MessageOption extends RiddlValue
  case class SynchOption(loc: Location) extends MessageOption
  case class AsynchOption(loc: Location) extends MessageOption
  case class ReplyOption(loc: Location) extends MessageOption

  /** An Interaction based on entity messaging between two entities in the
    * system.
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
    reactions: Seq[Reaction],
    addendum: Option[Addendum] = None
  ) extends ActionDef {
    def kind: String = "Message Action"
  }

  /** A directive from an actor (Role) towards some entity. You can think of
    * this like external input, coming from outside the system.
    */
  case class DirectiveActionDef(
    loc: Location,
    id: Identifier,
    options: Seq[MessageOption] = Seq.empty[MessageOption],
    role: RoleRef,
    entity: EntityRef,
    message: MessageReference,
    reactions: Seq[Reaction],
    addendum: Option[Addendum] = None
  ) extends ActionDef {
    def kind: String = "Directive Action"
  }

  case class ReactionRef(
    loc: Location,
    id: Identifier
  ) extends Reference

  case class DomainRef(
    loc: Location,
    id: Identifier
  ) extends Reference

  case class DomainDef(
    loc: Location,
    id: Identifier,
    subdomain: Option[Identifier] = None,
    types: Seq[TypeDef] = Seq.empty[TypeDef],
    channels: Seq[ChannelDef] = Seq.empty[ChannelDef],
    contexts: Seq[ContextDef] = Seq.empty[ContextDef],
    interactions: Seq[InteractionDef] = Seq.empty[InteractionDef],
    addendum: Option[Addendum] = None
  ) extends Container {
    def kind: String = "Domain"
  }
}
