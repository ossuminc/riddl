package com.yoppworks.ossum.idddl.parser

import com.yoppworks.ossum.idddl.parser.AST._

import scala.collection.mutable

/** Validates an AST */
object Validation {

  case class ValidationError(index: Int, message: String)
  type ValidationErrors = Seq[ValidationError]
  val NoValidationErrors = Seq.empty[ValidationError]

  trait Validator extends (Def => ValidationErrors)

  case class ValidationContext(
    symbols: mutable.Map[Seq[String], mutable.Map[String,Def]]
  )

  def validate(definitions: Seq[Def]): ValidationErrors = {
    val context = ValidationContext(
      mutable.Map.empty[Seq[String], mutable.Map[String,Def]]
    )
    definitions.flatMap { definition =>
      validateInternal(definition)(context)
    }
  }

  protected def check(
    what: Def, message: String, predicate: Boolean = true
  ): Seq[ValidationError] = {
    if (!predicate) {
      Seq(ValidationError(what.index, message))
    } else {
      NoValidationErrors
    }
  }

  protected def validateInternal(definition: Def)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    definition match {
      case d: DomainDef => validateDomain(d)
      case c: ContextDef => validateContext(c)
      case t: TypeDef => validateType(t)
      case c: CommandDef => validateCommand(c)
      case e: EventDef => validateEvent(e)
      case q: QueryDef => validateQuery(q)
      case r: ResultDef => validateResult(r)
      case e: EntityDef => validateEntity(e)
      case c: ChannelDef => validateChannel(c)
      case _ =>
        Seq(ValidationError(definition.index, "Unknown Definition"))
    }
  }

  protected def validateDomain(d: DomainDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    val parent = d.name_path.parent
    context.symbols.get(parent) match {
      case Some(map) =>
        map.get(d.name_path.name) match {
          case Some(domainDef) =>
            check(d,s"Domain '${d.name_path}' already defined", predicate
              = false)
          case None =>
            NoValidationErrors
        }
      case None =>
        context.symbols.put(parent, mutable.Map(d.name_path.name -> d))
        NoValidationErrors
    }
  }

  protected def validateContext(d: ContextDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    validate(d.types)
    validate(d.channels)
    validate(d.commands)
    validate(d.events)
    validate(d.queries)
    validate(d.results)
    validate(d.entities)
  }

  protected def validateType(d: TypeDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    Seq.empty[ValidationError]
  }

  protected def validateCommand(d: CommandDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    Seq.empty[ValidationError]
  }

  protected def validateEvent(d: EventDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    Seq.empty[ValidationError]
  }

  protected def validateQuery(d: QueryDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    Seq.empty[ValidationError]
  }
  protected def validateResult(d: ResultDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    Seq.empty[ValidationError]
  }
  protected def validateEntity(d: EntityDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    Seq.empty[ValidationError]
  }
  protected def validateChannel(d: ChannelDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    Seq.empty[ValidationError]
  }
}
