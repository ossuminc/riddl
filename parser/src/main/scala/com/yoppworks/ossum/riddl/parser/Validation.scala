package com.yoppworks.ossum.riddl.parser

import AST._
import com.yoppworks.ossum.riddl.parser.Traversal.DefTraveler
import com.yoppworks.ossum.riddl.parser.Traversal.FeatureTraveler

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.reflect._

/** Validates an AST */
object Validation {

  sealed trait ValidationMessageKind
  case object Warning extends ValidationMessageKind
  case object Error extends ValidationMessageKind

  case class ValidationMessage(
    loc: Location,
    message: String,
    kind: ValidationMessageKind
  )

  type ValidationMessages = mutable.ListBuffer[ValidationMessage]

  val NoValidationState: mutable.ListBuffer[ValidationMessage] =
    mutable.ListBuffer.empty[ValidationMessage]

  object ValidationMessages {

    def apply(): ValidationMessages = {
      new mutable.ListBuffer[ValidationMessage]
    }

    def apply(msg: ValidationMessage): ValidationMessages = {
      apply().append(msg)
    }

    def apply(msg: ValidationMessage*): ValidationMessages = {
      apply().appendAll(msg)
    }
  }

  sealed trait ValidationOptions

  /** Warn when recommended features are left unused */
  case object WarnOnMissing extends ValidationOptions

  /** Warn when recommended stylistic violations occur */
  case object WarnOnStyle extends ValidationOptions

  val defaultOptions: Seq[ValidationOptions] = Seq(
    WarnOnMissing,
    WarnOnStyle
  )

  def validate(
    domains: Seq[DomainDef],
    options: Seq[ValidationOptions] = defaultOptions
  ): Seq[ValidationMessage] = {
    val state = ValidationState(options)
    domains.flatMap { domain =>
      DomainValidator(domain, state).payload.msgs
    }
  }

  case class ValidationState(
    options: Seq[ValidationOptions] = defaultOptions,
    msgs: ValidationMessages = NoValidationState,
    symbols: mutable.Map[(Identifier, Class[_]), Definition] =
      mutable.Map.empty[(Identifier, Class[_]), Definition]
  ) {

    def ++(msg: ValidationMessage): ValidationState = {
      msgs.append(msg)
      this
    }
    symbols.put((Strng.id, classOf[TypeDefinition]), Strng)
    symbols.put((Number.id, classOf[TypeDefinition]), Number)
    symbols.put((Id.id, classOf[TypeDefinition]), Id)
    symbols.put((Bool.id, classOf[TypeDefinition]), Bool)
    symbols.put((Time.id, classOf[TypeDefinition]), Time)
    symbols.put((Date.id, classOf[TypeDefinition]), Date)
    symbols.put((TimeStamp.id, classOf[TypeDefinition]), TimeStamp)
    symbols.put((URL.id, classOf[TypeDefinition]), URL)
  }

  abstract class ValidatorBase[D <: Definition](definition: D)
      extends DefTraveler[ValidationState, D] {
    def payload: ValidationState

    payload.symbols.get(definition.id -> classOf[TypeDefinition]) match {
      case Some(definition) =>
        payload ++ ValidationMessage(
          definition.id.loc,
          s"Attempt to define type '${definition.id.value}' twice; previous " +
            s"definition at ${definition.loc}",
          Error
        )
      case None =>
        payload.symbols.put(
          definition.id -> classOf[TypeDefinition],
          definition
        )
    }

    def get[T <: Definition: ClassTag](
      id: Identifier
    ): Option[T] = {
      payload.symbols.get(id -> classTag[T].runtimeClass).map(_.asInstanceOf[T])
    }

    protected def check(
      predicate: Boolean = true,
      message: String,
      kind: ValidationMessageKind,
      loc: Location = definition.loc
    ): Option[ValidationMessage] = {
      if (!predicate) {
        Some(ValidationMessage(loc, message, kind))
      } else {
        None
      }
    }

    def isWarnOnMissing: Boolean = payload.options.contains(WarnOnMissing)

    def isWarnOnStyle: Boolean = payload.options.contains(WarnOnStyle)

    protected def visitExplanation(
      maybeExplanation: Option[Explanation]
    ): Unit = {
      check(
        maybeExplanation.isEmpty && isWarnOnMissing,
        "Definitions should have explanations",
        Warning
      )
    }

    protected def visitSeeAlso(
      maybeSeeAlso: Option[SeeAlso]
    ): Unit = {
      maybeSeeAlso.foreach { _ =>
      }
    }

    def close(): Unit = {}

    protected def terminus(): ValidationState = {
      payload
    }

    def checkRef[T <: Definition: ClassTag](id: Identifier): Unit = {
      get[T](id) match {
        case Some(d) =>
          val tc = classTag[T].runtimeClass
          check(
            d.getClass == tc,
            s"Identifier ${id.value} was expected to be ${tc.getName}" +
              s" but is ${d.getClass.getName}",
            Error,
            id.loc
          )
        case None =>
          payload ++ ValidationMessage(
            id.loc,
            s"Type ${id.value} is not defined. " +
              "Types must be defined before they are used.",
            Error
          )

      }
    }
  }

  case class TypeValidator(
    typeDef: TypeDef,
    payload: ValidationState
  ) extends ValidatorBase[TypeDef](typeDef)
      with Traversal.TypeTraveler[ValidationState] {

    protected def open(): Unit = {
      check(
        typeDef.id.value.charAt(0).isUpper && isWarnOnStyle,
        "Type names must start with a capital letter",
        Warning
      )
    }

    def visitType(ty: Type): Unit = {
      ty match {
        case _: PredefinedType =>
        case Optional(_: Location, typeName: Identifier) =>
          checkRef[TypeDefinition](typeName)
        case ZeroOrMore(_: Location, typeName: Identifier) =>
          checkRef[TypeDefinition](typeName)
        case OneOrMore(_: Location, typeName: Identifier) =>
          checkRef[TypeDefinition](typeName)
        case TypeRef(_, typeName) =>
          checkRef[TypeDefinition](typeName)
        case Aggregation(_, of) =>
          of.foreach {
            case (id: Identifier, typEx: TypeExpression) =>
              checkRef[TypeDefinition](typEx.id)
              check(
                isWarnOnStyle && id.value.forall(_.isLower),
                "Field name should be all lowercase",
                Warning,
                typEx.loc
              )
          }
        case Alternation(_, of) =>
          of.foreach { typEx: TypeExpression =>
            checkRef[TypeDefinition](typEx.id)
          }
        case Enumeration(_, of) =>
          if (isWarnOnStyle) {
            of.foreach { id =>
              check(
                id.value.head.isUpper,
                s"Enumerator '${id.value}' must start with lower case",
                Warning
              )
            }
          }
      }
    }
  }

  case class DomainValidator(
    domain: DomainDef,
    payload: ValidationState = ValidationState()
  ) extends ValidatorBase[DomainDef](domain)
      with Traversal.DomainTraveler[ValidationState] {

    def open(): Unit = {}

    def visitType(
      typ: TypeDef
    ): Traversal.TypeTraveler[ValidationState] = {
      TypeValidator(typ, payload)
    }

    def visitChannel(
      channel: ChannelDef
    ): Traversal.ChannelTraveler[ValidationState] = {
      ChannelValidator(channel, payload)
    }

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[ValidationState] = {
      InteractionValidator(i, payload)
    }

    def visitContext(
      context: ContextDef
    ): Traversal.ContextTraveler[ValidationState] = {
      ContextValidator(context, payload)
    }
  }

  case class ChannelValidator(
    channel: ChannelDef,
    payload: ValidationState
  ) extends ValidatorBase[ChannelDef](channel)
      with Traversal.ChannelTraveler[ValidationState] {

    def open(): Unit = {}

    def visitCommands(commands: Seq[CommandRef]): Unit = {}

    def visitEvents(events: Seq[EventRef]): Unit = {}

    def visitQueries(queries: Seq[QueryRef]): Unit = {}

    def visitResults(results: Seq[ResultRef]): Unit = {}
  }

  case class ContextValidator(
    context: ContextDef,
    payload: ValidationState
  ) extends ValidatorBase[ContextDef](context)
      with Traversal.ContextTraveler[ValidationState] {

    def open(): Unit = {}

    def visitType(t: TypeDef): Traversal.TypeTraveler[ValidationState] = {
      TypeValidator(t, payload)
    }

    def visitCommand(command: CommandDef): Unit = {}

    def visitEvent(event: EventDef): Unit = {}

    def visitQuery(query: QueryDef): Unit = {}

    def visitResult(result: ResultDef): Unit = {}

    def visitAdaptor(
      a: AdaptorDef
    ): Traversal.AdaptorTraveler[ValidationState] =
      AdaptorValidator(a, payload)

    def visitInteraction(
      i: InteractionDef
    ): Traversal.InteractionTraveler[ValidationState] =
      InteractionValidator(i, payload)

    def visitEntity(
      e: EntityDef
    ): Traversal.EntityTraveler[ValidationState] = {
      EntityValidator(e, payload)
    }
  }

  case class EntityValidator(
    entity: EntityDef,
    payload: ValidationState
  ) extends ValidatorBase[EntityDef](entity)
      with Traversal.EntityTraveler[ValidationState] {

    def open(): Unit = {}

    def visitProducer(c: ChannelRef): Unit = {}

    def visitConsumer(c: ChannelRef): Unit = {}

    def visitInvariant(i: InvariantDef): Unit = {}

    def visitFeature(f: FeatureDef): FeatureTraveler[ValidationState] = {
      FeatureValidator(f, payload)
    }
  }

  case class FeatureValidator(
    feature: FeatureDef,
    payload: ValidationState
  ) extends ValidatorBase[FeatureDef](feature)
      with Traversal.FeatureTraveler[ValidationState] {

    def open(): Unit = {}

    def visitBackground(background: Background): Unit = {}
    def visitExample(example: ExampleDef): Unit = {}
  }

  case class InteractionValidator(
    interaction: InteractionDef,
    payload: ValidationState
  ) extends ValidatorBase[InteractionDef](interaction)
      with Traversal.InteractionTraveler[ValidationState] {

    def open(): Unit = {}

    def visitRole(role: RoleDef): Unit = {}

    def visitAction(action: ActionDef): Unit = {}
  }

  case class AdaptorValidator(
    adaptor: AdaptorDef,
    payload: ValidationState
  ) extends ValidatorBase[AdaptorDef](adaptor)
      with Traversal.AdaptorTraveler[ValidationState] {

    def open(): Unit = {}

  }
}
