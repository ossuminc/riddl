package com.yoppworks.ossum.idddl.parser

import com.yoppworks.ossum.idddl.parser.AST._

import scala.collection.mutable

/** Validates an AST */
object Validation {

  case class ValidationError(index: Int, message: String)

  type ValidationErrors = Seq[ValidationError]
  val NoValidationErrors = Seq.empty[ValidationError]

  trait Validator {
    def validateDomain(d: DomainDef): ValidationErrors

    def validateContext(context: ContextDef, domain: DomainDef)
    : ValidationErrors

    def validateChannel(channel: ChannelDef, domain: DomainDef)
    : ValidationErrors

    def validateType(typ: TypeDef, context: ContextDef): ValidationErrors

    def validateCommand(command: CommandDef, context: ContextDef)
    : ValidationErrors

    def validateEvent(event: EventDef, context: ContextDef): ValidationErrors

    def validateQuery(query: QueryDef, context: ContextDef): ValidationErrors

    def validateResult(result: ResultDef, context: ContextDef): ValidationErrors

    def validateEntity(entity: EntityDef, context: ContextDef): ValidationErrors

    protected val symbols: mutable.Map[Seq[String], mutable.Map[String, Def]] =
      mutable.Map[Seq[String], mutable.Map[String, Def]]()

    protected def check(
      what: Def, message: String, predicate: Boolean = true
    ): Seq[ValidationError] = {
      if (!predicate) {
        Seq(ValidationError(what.index, message))
      } else {
        NoValidationErrors
      }
    }
  }

  def validate(
    domains: Seq[DomainDef],
    validator: DefaultValidator = new DefaultValidator
  ) : ValidationErrors = {
    domains.flatMap( validator.validateDomain )
  }

  class DefaultValidator extends Validator {

    def validateDomain(d: DomainDef): ValidationErrors = {
      val parent = d.name_path.parent
      val domainErrors = symbols.get(parent) match {
        case Some(map) =>
          map.get(d.name_path.name) match {
            case Some(domainDef) =>
              check(d, s"Domain '${d.name_path}' already defined", predicate
                = false)
            case None =>
              NoValidationErrors
          }
        case None =>
          symbols.put(parent, mutable.Map(d.name_path.name -> d))
          NoValidationErrors
      }
      domainErrors ++
        d.channels.flatMap(chan => validateChannel(chan, d)) ++
        d.contexts.flatMap(c => validateContext(c,d))
    }

    def validateChannel(channel: ChannelDef, d: DomainDef): ValidationErrors = {
      Seq.empty[ValidationError]
    }

    def validateContext(context: ContextDef, domain: DomainDef): ValidationErrors = {
      context.types.flatMap(typ => validateType(typ,context)) ++
      context.commands.flatMap(command => validateCommand(command, context))
      context.events.flatMap(event => validateEvent(event, context))
      context.queries.flatMap(qry => validateQuery(qry, context))
      context.results.flatMap(result => validateResult(result, context))
      context.entities.flatMap(entity => validateEntity(entity, context))
    }

    def validateType(d: TypeDef, context: ContextDef): ValidationErrors = {
      Seq.empty[ValidationError]
    }

    def validateCommand(d: CommandDef, context: ContextDef): ValidationErrors = {
      Seq.empty[ValidationError]
    }

    def validateEvent(event: EventDef, context: ContextDef): ValidationErrors
    = {
      Seq.empty[ValidationError]
    }

    def validateQuery(qry: QueryDef, context: ContextDef): ValidationErrors = {
      Seq.empty[ValidationError]
    }

    def validateResult(result: ResultDef, context: ContextDef): ValidationErrors
    = {
      Seq.empty[ValidationError]
    }

    def validateEntity(entity: EntityDef, context: ContextDef): ValidationErrors = {
      Seq.empty[ValidationError]
    }
  }
}
