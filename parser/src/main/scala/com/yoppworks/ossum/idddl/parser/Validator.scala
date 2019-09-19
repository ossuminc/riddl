package com.yoppworks.ossum.idddl.parser

import com.yoppworks.ossum.idddl.parser.AST._

import scala.collection.mutable

/** Validates an AST */
object Validator {

  case class ValidationError(index: Int, message: String)
  type ValidationErrors = Seq[ValidationError]
  val NoValidationErrors = Seq.empty[ValidationError]

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
    validate(d.children)
  }

  protected def validateType(d: TypeDef)(
    implicit context: ValidationContext
  ): ValidationErrors = {
    Seq.empty[ValidationError]
  }

}
