package com.yoppworks.ossum.idddl.parser

import com.yoppworks.ossum.idddl.parser.AST._

/** Validates an AST */
object Validator {

  case class ValidationError(index: Int, message: String)
  type ValidationErrors = Seq[ValidationError]
  def validate(definition: Def): Option[ValidationErrors] = {
    definition match {
      case d: DomainDef => validateDomain(d)
      case c: ContextDef => validateContext(c)
      case t: TypeDef => validateType(t)
      case _ =>
        Some(Seq(ValidationError(definition.index, "Unknown Definition")))
    }
  }

  def validateDomain(d: DomainDef): Option[ValidationErrors] = {
    None
  }

  def validateContext(d: ContextDef): Option[ValidationErrors] = {
    None
  }

  def validateType(d: TypeDef): Option[ValidationErrors] = {
    None
  }

}
