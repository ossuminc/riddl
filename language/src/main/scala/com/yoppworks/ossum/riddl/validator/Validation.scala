package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST.ActionDef
import com.yoppworks.ossum.riddl.parser.AST.AdaptorDef
import com.yoppworks.ossum.riddl.parser.AST.Aggregation
import com.yoppworks.ossum.riddl.parser.AST.Alternation
import com.yoppworks.ossum.riddl.parser.AST.Background
import com.yoppworks.ossum.riddl.parser.AST.Bool
import com.yoppworks.ossum.riddl.parser.AST.ChannelDef
import com.yoppworks.ossum.riddl.parser.AST.ChannelRef
import com.yoppworks.ossum.riddl.parser.AST.CommandDef
import com.yoppworks.ossum.riddl.parser.AST.CommandRef
import com.yoppworks.ossum.riddl.parser.AST.ContextDef
import com.yoppworks.ossum.riddl.parser.AST.Date
import com.yoppworks.ossum.riddl.parser.AST.Definition
import com.yoppworks.ossum.riddl.parser.AST.DomainDef
import com.yoppworks.ossum.riddl.parser.AST.EntityDef
import com.yoppworks.ossum.riddl.parser.AST.Enumeration
import com.yoppworks.ossum.riddl.parser.AST.EventDef
import com.yoppworks.ossum.riddl.parser.AST.EventRef
import com.yoppworks.ossum.riddl.parser.AST.ExampleDef
import com.yoppworks.ossum.riddl.parser.AST.Explanation
import com.yoppworks.ossum.riddl.parser.AST.FeatureDef
import com.yoppworks.ossum.riddl.parser.AST.Id
import com.yoppworks.ossum.riddl.parser.AST.Identifier
import com.yoppworks.ossum.riddl.parser.AST.InteractionDef
import com.yoppworks.ossum.riddl.parser.AST.InvariantDef
import com.yoppworks.ossum.riddl.parser.AST.Location
import com.yoppworks.ossum.riddl.parser.AST.Number
import com.yoppworks.ossum.riddl.parser.AST.OneOrMore
import com.yoppworks.ossum.riddl.parser.AST.Optional
import com.yoppworks.ossum.riddl.parser.AST.PredefinedType
import com.yoppworks.ossum.riddl.parser.AST.QueryDef
import com.yoppworks.ossum.riddl.parser.AST.QueryRef
import com.yoppworks.ossum.riddl.parser.AST.ResultDef
import com.yoppworks.ossum.riddl.parser.AST.ResultRef
import com.yoppworks.ossum.riddl.parser.AST.RoleDef
import com.yoppworks.ossum.riddl.parser.AST.SeeAlso
import com.yoppworks.ossum.riddl.parser.AST.Strng
import com.yoppworks.ossum.riddl.parser.AST.Time
import com.yoppworks.ossum.riddl.parser.AST.TimeStamp
import com.yoppworks.ossum.riddl.parser.AST.Type
import com.yoppworks.ossum.riddl.parser.AST.TypeDef
import com.yoppworks.ossum.riddl.parser.AST.TypeDefinition
import com.yoppworks.ossum.riddl.parser.AST.TypeExpression
import com.yoppworks.ossum.riddl.parser.AST.TypeRef
import com.yoppworks.ossum.riddl.parser.AST.URL
import com.yoppworks.ossum.riddl.parser.AST.ZeroOrMore
import com.yoppworks.ossum.riddl.parser.Traversal
import com.yoppworks.ossum.riddl.parser.Traversal.DefTraveler
import com.yoppworks.ossum.riddl.parser.Traversal.FeatureTraveler

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.reflect.classTag

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

}
