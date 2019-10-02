package com.yoppworks.ossum.riddl.parser

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

  sealed trait AST

  case class Identifier(value: String) extends AST

  case class LiteralString(s: String) extends AST
  case class LiteralInteger(n: BigInt) extends AST
  case class LiteralDecimal(d: BigDecimal) extends AST
  case class PathIdentifier(value: Seq[String]) extends AST

  case class Link(url: LiteralString) extends AST

  case class Explanation(
    purpose: LiteralString,
    details: Option[LiteralString] = None,
    links: Seq[Link] = Seq.empty[Link]
  ) extends AST

  sealed trait Ref extends AST {
    def id: Identifier
  }

  sealed trait Def extends AST {
    def index: Int
    def id: Identifier
    def explanation: Option[Explanation]
  }

  sealed trait Type extends AST
  sealed trait TypeDefinition extends Type

  sealed trait TypeExpression extends Type {
    def id: Identifier
  }
  case class TypeRef(id: Identifier) extends Ref with TypeExpression
  case class PredefinedType(id: Identifier) extends TypeExpression

  val Strng = PredefinedType(Identifier("String"))
  val Boolean = PredefinedType(Identifier("Boolean"))
  val Number = PredefinedType(Identifier("Number")) /* eventually turn this
  into:
  case object Integer extends PredefinedType("Integer")
  case object Decimal extends PredefinedType("Decimal")
   */
  val Id = PredefinedType(Identifier("Id"))
  val Date = PredefinedType(Identifier("Date"))
  val Time = PredefinedType(Identifier("Time"))
  val TimeStamp = PredefinedType(Identifier("TimeStamp"))
  val URL = PredefinedType(Identifier("URL"))

  case class Optional(id: Identifier) extends TypeExpression
  case class ZeroOrMore(id: Identifier) extends TypeExpression
  case class OneOrMore(id: Identifier) extends TypeExpression

  case class Enumeration(of: Seq[Identifier]) extends TypeDefinition
  case class Alternation(of: Seq[TypeExpression]) extends TypeDefinition
  case class Aggregation(of: Map[Identifier, TypeExpression])
      extends TypeDefinition

  case class TypeDef(
    index: Int,
    id: Identifier,
    typ: Type,
    explanation: Option[Explanation] = None
  ) extends Def

  case class ChannelRef(id: Identifier) extends Ref
  case class ChannelDef(
    index: Int,
    id: Identifier,
    commands: Seq[CommandRef] = Seq.empty[CommandRef],
    events: Seq[EventRef] = Seq.empty[EventRef],
    queries: Seq[QueryRef] = Seq.empty[QueryRef],
    results: Seq[ResultRef] = Seq.empty[ResultRef],
    explanation: Option[Explanation] = None
  ) extends Def

  sealed trait MessageRef extends Ref
  sealed trait MessageDef extends Def

  case class CommandRef(id: Identifier) extends MessageRef
  case class CommandDef(
    index: Int,
    id: Identifier,
    typ: TypeExpression,
    events: EventRefs,
    explanation: Option[Explanation] = None
  ) extends MessageDef

  case class EventRef(id: Identifier) extends MessageRef
  type EventRefs = Seq[EventRef]
  case class EventDef(
    index: Int,
    id: Identifier,
    typ: TypeExpression,
    explanation: Option[Explanation] = None
  ) extends MessageDef

  case class QueryRef(id: Identifier) extends MessageRef
  case class QueryDef(
    index: Int,
    id: Identifier,
    typ: TypeExpression,
    result: ResultRef,
    explanation: Option[Explanation] = None
  ) extends MessageDef

  case class ResultRef(id: Identifier) extends MessageRef
  case class ResultDef(
    index: Int,
    id: Identifier,
    typ: TypeExpression,
    explanation: Option[Explanation] = None
  ) extends MessageDef

  sealed trait EntityOption
  case object EntityAggregate extends EntityOption
  case object EntityPersistent extends EntityOption
  case object EntityConsistent extends EntityOption
  case object EntityAvailable extends EntityOption
  case object EntityDevice extends EntityOption

  case class EntityRef(id: Identifier) extends Ref

  case class Given(fact: LiteralString)
  case class When(situation: LiteralString)
  case class Then(result: LiteralString)
  case class Background(givens: Seq[Given] = Seq.empty[Given])
  case class Example(
    description: LiteralString,
    givens: Seq[Given] = Seq.empty[Given],
    whens: Seq[When] = Seq.empty[When],
    thens: Seq[Then] = Seq.empty[Then]
  )
  case class FeatureDef(
    index: Int,
    id: Identifier,
    description: LiteralString,
    background: Option[Background] = None,
    examples: Seq[Example] = Seq.empty[Example],
    explanation: Option[Explanation] = None
  ) extends Def

  case class InvariantDef(
    index: Int,
    id: Identifier,
    expression: LiteralString,
    explanation: Option[Explanation] = None
  ) extends Def

  /** Definition of an Entity
    *
    * @param options The options for the entity
    * @param index The index location in the input
    * @param id The name of the entity
    * @param typ The type of the entity's value
    * @param consumes A reference to the channel from which the entity consumes
    * @param produces A reference to the channel to which the entity produces
    */
  case class EntityDef(
    index: Int,
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    id: Identifier,
    typ: TypeExpression,
    consumes: Option[ChannelRef] = None,
    produces: Option[ChannelRef] = None,
    features: Seq[FeatureDef] = Seq.empty[FeatureDef],
    invariants: Seq[InvariantDef] = Seq.empty[InvariantDef],
    explanation: Option[Explanation] = None
  ) extends Def

  trait TranslationRule extends Def {
    def channel: String
  }

  case class MessageTranslationRule(
    index: Int,
    id: Identifier,
    channel: String,
    input: String,
    output: String,
    rule: String,
    explanation: Option[Explanation] = None
  ) extends TranslationRule

  /** Definition of an Adaptor
    * Adaptors are defined in Contexts to convert messaging from one Context to
    * another. Adaptors translate incoming events from other Contexts into
    * commands or events that its owning context can understand. There should be
    * one Adaptor for each external Context
    *
    * @param index Location in the parsing input
    * @param id Name of the adaptor
    */
  case class AdaptorDef(
    index: Int,
    id: Identifier,
    targetDomain: Option[DomainRef] = None,
    targetContext: ContextRef,
    explanation: Option[Explanation] = None
    // Details TBD
  ) extends Def

  sealed trait ContextOption
  case object WrapperOption extends ContextOption
  case object FunctionOption extends ContextOption
  case object GatewayOption extends ContextOption

  case class ContextRef(id: Identifier) extends Ref

  case class ContextDef(
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    index: Int,
    id: Identifier,
    types: Seq[TypeDef] = Seq.empty[TypeDef],
    commands: Seq[CommandDef] = Seq.empty[CommandDef],
    events: Seq[EventDef] = Seq.empty[EventDef],
    queries: Seq[QueryDef] = Seq.empty[QueryDef],
    results: Seq[ResultDef] = Seq.empty[ResultDef],
    entities: Seq[EntityDef] = Seq.empty[EntityDef],
    adaptors: Seq[AdaptorDef] = Seq.empty[AdaptorDef],
    interactions: Seq[InteractionDef] = Seq.empty[InteractionDef],
    explanation: Option[Explanation] = None
  ) extends Def

  /** Definition of an Interaction
    * Interactions define an exemplary interaction between the system being
    * designed and other actors. The basic ideas of an Interaction are much
    * like UML Sequence Diagram.
    *
    * @param index Where in the input the Scenario is defined
    * @param id The name of the scenario
    * @param roles The roles defined for the interaction
    * @param actions The actions that constitute the interaction
    */
  case class InteractionDef(
    index: Int,
    id: Identifier,
    roles: Seq[RoleDef] = Seq.empty[RoleDef],
    actions: Seq[ActionDef] = Seq.empty[ActionDef],
    explanation: Option[Explanation] = None
  ) extends Def

  sealed trait RoleOption
  case object HumanOption extends RoleOption
  case object DeviceOption extends RoleOption

  case class RoleDef(
    options: Seq[RoleOption] = Seq.empty[RoleOption],
    index: Int,
    id: Identifier,
    responsibilities: Seq[LiteralString] = Seq.empty[LiteralString],
    capacities: Seq[LiteralString] = Seq.empty[LiteralString],
    explanation: Option[Explanation] = None
  ) extends Def

  case class RoleRef(id: Identifier) extends Ref

  sealed trait ActionDef extends Def

  type Actions = Seq[ActionDef]

  sealed trait MessageOption
  case object SynchOption extends MessageOption
  case object AsynchOption extends MessageOption
  case object ReplyOption extends MessageOption

  /** An Interaction based on entity messaging
    * @param options Options for the message
    * @param index Where the message is located in the input
    * @param id The displayable text that describes the interaction
    * @param sender A reference to the entity sending the message
    * @param receiver A reference to the entity receiving the message
    * @param message A reference to the kind of message sent & received
    */
  case class MessageActionDef(
    options: Seq[MessageOption] = Seq.empty[MessageOption],
    index: Int,
    id: Identifier,
    sender: EntityRef,
    receiver: EntityRef,
    message: MessageRef,
    explanation: Option[Explanation] = None
  ) extends ActionDef

  case class DirectiveActionDef(
    options: Seq[MessageOption] = Seq.empty[MessageOption],
    index: Int,
    id: Identifier,
    role: RoleRef,
    entity: EntityRef,
    message: MessageRef,
    explanation: Option[Explanation] = None
  ) extends ActionDef

  case class ProcessingActionDef(
    index: Int,
    id: Identifier,
    entity: EntityRef,
    description: LiteralString,
    explanation: Option[Explanation] = None
  ) extends ActionDef

  case class DeletionActionDef(
    index: Int,
    id: Identifier,
    entity: EntityRef,
    explanation: Option[Explanation] = None
  ) extends ActionDef

  case class CreationActionDef(
    index: Int,
    id: Identifier,
    entity: EntityRef,
    explanation: Option[Explanation] = None
  ) extends ActionDef

  case class DomainRef(id: Identifier) extends Ref

  case class DomainDef(
    index: Int,
    id: Identifier,
    subdomain: Option[Identifier] = None,
    types: Seq[TypeDef] = Seq.empty[TypeDef],
    channels: Seq[ChannelDef] = Seq.empty[ChannelDef],
    interactions: Seq[InteractionDef] = Seq.empty[InteractionDef],
    contexts: Seq[ContextDef] = Seq.empty[ContextDef],
    explanation: Option[Explanation] = None
  ) extends Def

  type Domains = Seq[DomainDef]
}
