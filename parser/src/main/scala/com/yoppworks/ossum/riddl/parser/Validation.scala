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
  case object MissingWarning extends ValidationMessageKind
  case object StyleWarning extends ValidationMessageKind
  case object Warning extends ValidationMessageKind
  case object Error extends ValidationMessageKind

  case class ValidationMessage(
    loc: Location,
    message: String,
    kind: ValidationMessageKind = Error
  )

  type ValidationMessages = mutable.ListBuffer[ValidationMessage]

  def NoValidationState: mutable.ListBuffer[ValidationMessage] =
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
  case object ReportMissingWarnings extends ValidationOptions

  /** Warn when recommended stylistic violations occur */
  case object ReportStyleWarnings extends ValidationOptions

  val defaultOptions: Seq[ValidationOptions] = Seq(
    ReportMissingWarnings,
    ReportStyleWarnings
  )

  def validate(
    domains: Seq[DomainDef],
    options: Seq[ValidationOptions] = defaultOptions
  ): Seq[ValidationMessage] = {
    val state = ValidationState(options)
    domains.flatMap { domain =>
      DomainValidator(domain, state).traverse.msgs
    }
  }

  case class ValidationState(
    options: Seq[ValidationOptions] = defaultOptions,
    msgs: ValidationMessages = NoValidationState,
    symbols: mutable.Map[String, mutable.ListBuffer[Definition]] =
      mutable.Map.empty[String, mutable.ListBuffer[Definition]]
  ) {

    def isReportMissingWarnings: Boolean =
      options.contains(ReportMissingWarnings)

    def isReportStyleWarnings: Boolean =
      options.contains(ReportStyleWarnings)

    def add(msg: ValidationMessage): Unit = {
      msg.kind match {
        case StyleWarning if isReportStyleWarnings =>
          msgs.append(msg)
        case MissingWarning if isReportMissingWarnings =>
          msgs.append(msg)
        case _ =>
          msgs.append(msg)
      }
    }

    def put(dfntn: Definition): Unit = {
      symbols.get(dfntn.id.value) match {
        case Some(list) =>
          symbols.update(dfntn.id.value, list :+ dfntn)
        case None =>
          symbols.put(dfntn.id.value, mutable.ListBuffer(dfntn))

      }
    }

    def get[D <: Definition: ClassTag](id: Identifier): Option[D] = {
      val clazz = classTag[D].runtimeClass
      symbols.get(id.value) match {
        case Some(list) =>
          list
            .find { d =>
              clazz.isAssignableFrom(d.getClass)
            }
            .map(_.asInstanceOf[D])
        case None =>
          None
      }
    }

    put(Strng)
    put(Number)
    put(Id)
    put(Bool)
    put(Time)
    put(Date)
    put(TimeStamp)
    put(URL)

  }

  abstract class ValidatorBase[D <: Definition: ClassTag](definition: D)
      extends DefTraveler[ValidationState, D] {
    def payload: ValidationState

    get[D](definition.id) match {
      case Some(dfntn) =>
        payload.add(
          ValidationMessage(
            dfntn.id.loc,
            s"Attempt to define '${dfntn.id.value}' twice; previous " +
              s"definition at ${dfntn.loc}",
            Error
          )
        )
      case None =>
        put(definition)
    }

    def put(dfntn: Definition): Unit = {
      payload.put(dfntn)
    }

    def get[T <: Definition: ClassTag](
      id: Identifier
    ): Option[T] = {
      payload.get[T](id)
    }

    protected def visitExplanation(
      maybeExplanation: Option[Explanation]
    ): Unit = {
      check(
        maybeExplanation.isEmpty,
        "Definitions should have explanations",
        MissingWarning
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

    protected def check(
      predicate: Boolean = true,
      message: String,
      kind: ValidationMessageKind,
      loc: Location = definition.loc
    ): Unit = {
      if (!predicate) {
        payload.add(ValidationMessage(loc, message, kind))
      }
    }

    def checkRef[T <: Definition: ClassTag](id: Identifier): Unit = {
      get[T](id) match {
        case Some(d) =>
          val tc = classTag[T].runtimeClass
          check(
            tc.isAssignableFrom(d.getClass),
            s"Identifier ${id.value} was expected to be ${tc.getName}" +
              s" but is ${d.getClass.getName}",
            Error,
            id.loc
          )
        case None =>
          payload.add(
            ValidationMessage(
              id.loc,
              s"Type ${id.value} is not defined. " +
                "Types must be defined before they are used.",
              Error
            )
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
        typeDef.id.value.head.isUpper,
        s"Type name ${typeDef.id.value} must start with a capital letter",
        StyleWarning
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
                id.value.forall(_.isLower),
                "Field name should be all lowercase",
                StyleWarning,
                typEx.loc
              )
          }
        case Alternation(_, of) =>
          of.foreach { typEx: TypeExpression =>
            checkRef[TypeDefinition](typEx.id)
          }
        case Enumeration(_, of) =>
          if (payload.isReportStyleWarnings) {
            of.foreach { id =>
              check(
                id.value.head.isUpper,
                s"Enumerator '${id.value}' must start with lower case",
                StyleWarning
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
