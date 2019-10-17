package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal.DefTraveler

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

  def validateDomain(
    domain: DomainDef,
    options: Seq[ValidationOptions] = defaultOptions
  ): Seq[ValidationMessage] = {
    val state = ValidationState(options)
    DomainValidator(domain, state).traverse.msgs.toSeq
  }

  def validateContext(
    domain: ContextDef,
    options: Seq[ValidationOptions] = defaultOptions
  ): Seq[ValidationMessage] = {
    val state = ValidationState(options)
    ContextValidator(domain, state).traverse.msgs.toSeq
  }

  def validateEntity(
    entity: EntityDef,
    options: Seq[ValidationOptions] = defaultOptions
  ): Seq[ValidationMessage] = {
    val state = ValidationState(options)
    EntityValidator(entity, state).traverse.msgs.toSeq
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
        case StyleWarning =>
          if (isReportStyleWarnings) msgs.append(msg)
        case MissingWarning =>
          if (isReportMissingWarnings) msgs.append(msg)
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

    def put(dfntn: Definition): Unit = {
      payload.put(dfntn)
    }

    def get[T <: Definition: ClassTag](
      id: Identifier
    ): Option[T] = {
      payload.get[T](id)
    }

    def open(): Unit = {
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
    }

    def close(): Unit = {}

    override def visitAddendum(add: Option[Addendum]): Unit = {
      if (add.isEmpty) {
        check(
          predicate = false,
          s"Definition '${definition.id.value}' " +
            "should have explanations or references",
          MissingWarning
        )
      } else {
        super.visitAddendum(add)
      }
    }

    protected def visitExplanation(
      exp: Explanation
    ): Unit = {
      check(
        exp.markdown.nonEmpty,
        "Explanations should not be empty",
        MissingWarning
      )
    }

    protected def visitSeeAlso(
      seeAlso: SeeAlso
    ): Unit = {
      check(
        seeAlso.citations.nonEmpty,
        "SeeAlso references should not be empty",
        MissingWarning
      )
    }

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

    protected def checkTypeExpression(typ: TypeExpression): Unit = {
      typ match {
        case Optional(_, id) =>
          checkRef[TypeDef](id)
        case OneOrMore(_, id) =>
          checkRef[TypeDef](id)
        case ZeroOrMore(_, id) =>
          checkRef[TypeDef](id)
        case TypeRef(_, id) =>
          checkRef[TypeDef](id)
      }
    }

    protected def checkRef[T <: Definition: ClassTag](id: Identifier): Unit = {
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
