package com.yoppworks.ossum.riddl.language

import java.net.URI
import java.util.regex.PatternSyntaxException

import com.yoppworks.ossum.riddl.language.AST._

import scala.reflect.ClassTag
import scala.reflect.classTag

/** Validates an AST */
object Validation {

  def validate[C <: Container](
    root: C,
    options: ValidationOptions = defaultOptions
  ): ValidationMessages = {
    val symTab = SymbolTable(root)
    val state = ValidationState(symTab, options)
    val folding = new ValidationFolding
    folding.foldLeft(root, root, state).msgs
  }

  sealed trait ValidationMessageKind {
    def isSevereError: Boolean = true
    def isError: Boolean = false
    def isWarning: Boolean = false
    def isMissing: Boolean = false
    def isStyle: Boolean = false
  }

  case object MissingWarning extends ValidationMessageKind {
    override def isWarning = true
    override def isMissing: Boolean = true
    override def toString: String = "Missing"
  }

  case object StyleWarning extends ValidationMessageKind {
    override def isWarning = true
    override def isStyle: Boolean = true
    override def toString: String = "Style"
  }

  case object Warning extends ValidationMessageKind {
    override def isWarning = true
    override def toString: String = "Warning"
  }

  case object Error extends ValidationMessageKind {
    override def isError = true
    override def toString: String = "Error"
  }

  case object SevereError extends ValidationMessageKind {
    override def isError = true
    override def isSevereError = true
    override def toString: String = "Severe"
  }

  case class ValidationMessage(
    loc: Location,
    message: String,
    kind: ValidationMessageKind = Error
  ) {

    def format(source: String): String = {
      s"$kind: $source$loc: $message"
    }
  }

  type ValidationMessages = List[ValidationMessage]

  val NoValidationMessages: List[ValidationMessage] =
    List.empty[ValidationMessage]

  object ValidationMessages {

    def apply(): ValidationMessages = {
      NoValidationMessages
    }

    def apply(msg: ValidationMessage): ValidationMessages = {
      apply() :+ msg
    }

    def apply(msgs: ValidationMessage*): ValidationMessages = {
      apply() ++ msgs
    }
  }

  trait ValidationOptions extends Riddl.Options

  val defaultOptions: ValidationOptions = new ValidationOptions {
    override def showTimes: Boolean = true
    override def showWarnings: Boolean = true
    override def showMissingWarnings: Boolean = true
    override def showStyleWarnings: Boolean = true
  }

  case class ValidationState(
    symbolTable: SymbolTable,
    options: ValidationOptions = defaultOptions,
    msgs: ValidationMessages = NoValidationMessages
  ) extends Folding.State[ValidationState] {
    def step(f: ValidationState => ValidationState): ValidationState = f(this)

    def parentOf(
      definition: Definition
    ): Container = {
      symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
    }

    def isReportMissingWarnings: Boolean =
      options.showMissingWarnings

    def isReportStyleWarnings: Boolean =
      options.showStyleWarnings

    def lookup[T <: Definition: ClassTag](
      id: PathIdentifier
    ): List[T] = {
      symbolTable.lookup[T](id.value)
    }

    def lookup[T <: Definition: ClassTag](
      id: Seq[String]
    ): List[T] = {
      symbolTable.lookup[T](id)
    }

    def add(msg: ValidationMessage): ValidationState = {
      msg.kind match {
        case StyleWarning =>
          if (isReportStyleWarnings)
            this.copy(msgs = msgs :+ msg)
          else
            this
        case MissingWarning =>
          if (isReportMissingWarnings)
            this.copy(msgs = msgs :+ msg)
          else this

        case _ =>
          this.copy(msgs = msgs :+ msg)
      }
    }

    def check(
      predicate: Boolean = true,
      message: => String,
      kind: ValidationMessageKind,
      loc: Location
    ): ValidationState = {
      if (!predicate) {
        add(ValidationMessage(loc, message, kind))
      } else {
        this
      }
    }

    def checkIdentifierLength[T <: Definition](
      d: T,
      min: Int = 3
    ): ValidationState = {
      if (d.id.value.length < min) {
        add(
          ValidationMessage(
            d.id.loc,
            s"${d.kind} identifier '${d.id.value}' is too short. Identifiers should be at least $min characters.",
            StyleWarning
          )
        )
      } else {
        this
      }
    }

    def checkPattern(p: Pattern): ValidationState = {
      try {
        val compound = p.pattern.map(_.s).reduce(_ + _)
        java.util.regex.Pattern.compile(compound)
        this
      } catch {
        case x: PatternSyntaxException =>
          add(ValidationMessage(p.loc, x.getMessage))
      }
    }

    def checkTypeExpression(
      typ: TypeExpression,
      definition: Definition
    ): ValidationState = {
      typ match {
        case p @ Pattern(_, _, addendum) =>
          checkPattern(p).checkDescription(definition, addendum)
        case UniqueId(_, entityName, addendum) =>
          this
            .checkRef[Entity](entityName)
            .checkDescription(definition, addendum)
        case _: AST.PredefinedType =>
          this
        case AST.TypeRef(_, id: PathIdentifier) =>
          checkRef[Type](id)
        case Optional(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case OneOrMore(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case ZeroOrMore(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case Enumeration(_, enumerators: Seq[Enumerator], desc) =>
          enumerators.foldLeft(this) {
            case (state, enumerator) =>
              val id = enumerator.id
              val s = state
                .checkIdentifierLength(enumerator)
                .check(
                  id.value.head.isUpper,
                  s"Enumerator '${id.value}' must start with lower case",
                  StyleWarning,
                  id.loc
                )
              if (enumerator.value.nonEmpty) {
                s.checkTypeExpression(enumerator.value.get, definition)
                  .checkDescription(definition, desc)
              } else {
                s.checkDescription(definition, desc)
              }
          }
        case Alternation(_, of, addendum) =>
          of.foldLeft(this) {
              case (state, typex) =>
                state.checkTypeExpression(typex, definition)
            }
            .checkDescription(definition, addendum)
        case Aggregation(loc, of: Seq[Field], addendum) =>
          of.foldLeft(this) {
              case (state, field) =>
                state
                  .checkIdentifierLength(field)
                  .check(
                    field.id.value.head.isLower,
                    "Field names should start with a lower case letter",
                    StyleWarning,
                    loc
                  )
                  .checkTypeExpression(field.typeEx, field)
                  .checkDescription(field, field.description)
            }
            .checkDescription(definition, addendum)
        case Mapping(_, from, to, addendum) =>
          this
            .checkTypeExpression(from, definition)
            .checkTypeExpression(to, definition)
            .checkDescription(definition, addendum)
        case RangeType(loc, min, max, addendum) =>
          this
            .check(
              min.n >= BigInt.long2bigInt(Long.MinValue),
              "Minimum value might be too small to store in a Long",
              Warning,
              loc
            )
            .check(
              max.n <= BigInt.long2bigInt(Long.MaxValue),
              "Maximum value might be too large to store in a Long",
              Warning,
              loc
            )
            .checkDescription(definition, addendum)
        case ReferenceType(_, entity: EntityRef, addendum) =>
          this.checkRef[Entity](entity).checkDescription(definition, addendum)
      }
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference
    ): ValidationState = {
      checkRef[T](reference.id)
    }

    def checkRef[T <: Definition: ClassTag](
      id: PathIdentifier
    ): ValidationState = {
      if (id.value.nonEmpty) {
        val tc = classTag[T].runtimeClass
        symbolTable.lookup[T](id.value) match {
          case Nil =>
            add(
              ValidationMessage(
                id.loc,
                s"'${id.value.mkString(".")}' is not defined but should be a ${tc.getSimpleName}",
                Error
              )
            )
          case d :: Nil =>
            check(
              tc.isAssignableFrom(d.getClass),
              s"'${id.value}' was expected to be ${tc.getSimpleName}" +
                s" but is ${d.getClass.getSimpleName} instead",
              Error,
              id.loc
            )
          case _ :: tail =>
            add(
              ValidationMessage(
                id.loc,
                s"""'${id.value}' is not uniquely defined. Other
                   |definitions are:
                   |${tail.map(_.loc.toString).mkString("  \n")}",
                   |""".stripMargin,
                Error
              )
            )
        }
      } else {
        this
      }
    }

    def checkNonEmpty(
      list: Seq[_],
      name: String,
      thing: Definition
    ): ValidationState = {
      check(
        list.nonEmpty,
        s"$name in ${thing.identify} should not be empty",
        Error,
        thing.loc
      )
    }

    def checkLiteralString(
      litStr: LiteralString,
      name: String,
      thing: Definition
    ): ValidationState = {
      check(
        litStr.s.nonEmpty,
        s"$name in ${thing.identify} should not be empty",
        MissingWarning,
        thing.loc
      )
    }

    def checkOptions[T](options: Seq[T], loc: Location): ValidationState = {
      check(
        options.size == options.distinct.size,
        "Options should not be repeated",
        Error,
        loc
      )
    }

    def checkDefinition(
      container: Container,
      definition: Definition
    ): ValidationState = {
      var result = this.check(
        definition.id.value.nonEmpty,
        "Definitions may not have empty names",
        Error,
        definition.loc
      )
      result = result.checkIdentifierLength(definition)
      val path = symbolTable.pathOf(definition)
      val matches =
        result.lookup[Definition](path)
      if (matches.isEmpty) {
        result = result.add(
          ValidationMessage(
            definition.id.loc,
            s"'${definition.id.value}' evaded inclusion in symbol table!",
            SevereError
          )
        )
      } else if (matches.size >= 2) {
        matches
          .groupBy(result.symbolTable.parentOf(_))
          .get(Some(container)) match {
          case Some(head :: tail) if tail.nonEmpty =>
            result = result.add(
              ValidationMessage(
                head.id.loc,
                s"${definition.identify} is defined multiple times; other " +
                  s"definitions are:\n  " +
                  matches.map(x => x.identify + " " + x.loc).mkString("\n  "),
                Error
              )
            )
          case _ =>
        }
      }
      result
    }

    def checkDescription(
      definition: Definition,
      description: Option[Description]
    ): ValidationState = {
      if (definition.description.isEmpty) {
        this.check(
          predicate = false,
          s"${definition.identify} should have a description",
          MissingWarning,
          definition.loc
        )
      } else {
        val desc = definition.description.get
        val brief = desc.brief
        val result: ValidationState = this
          .check(
            brief.nonEmpty,
            s"For ${definition.identify}, brief description should not be empty",
            MissingWarning,
            desc.loc
          )
          .check(
            desc.details.nonEmpty,
            s"For ${definition.identify}, detailed description should not be empty",
            MissingWarning,
            desc.loc
          )
        desc.citations.foldLeft(result) {
          case (next: ValidationState, citation: LiteralString) =>
            val uriMsg = try {
              new URI(citation.s); ""
            } catch {
              case x: Exception =>
                x.getMessage
            }
            next
              .check(uriMsg.isEmpty, uriMsg, Error, citation.loc)
              .check(
                citation.s.nonEmpty,
                "Citations should not be empty",
                MissingWarning,
                citation.loc
              )
        }
      }
    }
  }

  class ValidationFolding extends Folding.Folding[ValidationState] {

    override def openDomain(
      state: ValidationState,
      container: Container,
      domain: Domain
    ): ValidationState = {
      state.checkDefinition(container, domain)
    }

    override def closeDomain(
      state: ValidationState,
      container: Container,
      domain: Domain
    ): ValidationState = {
      state.checkDescription(domain, domain.description)
    }

    override def openContext(
      state: Validation.ValidationState,
      container: Container,
      context: AST.Context
    ): ValidationState = {
      val result =
        state
          .checkDefinition(container, context)
          .checkOptions[ContextOption](context.options, context.loc)
      if (context.entities.isEmpty) {
        result.add(
          ValidationMessage(
            context.loc,
            "Contexts that define no entities are not valid",
            Error
          )
        )
      } else {
        result
      }
    }

    override def closeContext(
      state: Validation.ValidationState,
      container: Container,
      context: AST.Context
    ): ValidationState = {
      state.checkDescription(context, context.description)
    }

    override def openEntity(
      state: Validation.ValidationState,
      container: Container,
      entity: AST.Entity
    ): ValidationState = {
      var result = state
        .checkDefinition(container, entity)
        .checkOptions[EntityOption](entity.options, entity.loc)
      result = entity.states.foldLeft(result) { (next, state) =>
        next.checkTypeExpression(state.typeEx, state)
      }
      result = entity.consumers.foldLeft(result) { (s, consumer) =>
        s.checkRef[Topic](consumer.topic)
      }

      result = entity.types.foldLeft(result) { (s, typ) =>
        s.checkDefinition(entity, typ).checkTypeExpression(typ.typ, typ)
      }

      // TODO: invariant?

      if (entity.consumers.isEmpty) {
        result = result.add(
          ValidationMessage(entity.loc, "An entity must consume a topic")
        )
      } else if (!entity.consumers.exists(_.clauses.nonEmpty)) {
        result = result.add(
          ValidationMessage(
            entity.loc,
            s"Entity '${entity.id.value}' has only empty topic consumers",
            MissingWarning
          )
        )
      }
      result
    }

    override def closeEntity(
      state: Validation.ValidationState,
      container: Container,
      entity: AST.Entity
    ): ValidationState = {
      state.checkDescription(entity, entity.description)
    }

    override def openTopic(
      state: ValidationState,
      container: Container,
      topic: Topic
    ): ValidationState = {
      state
        .checkDefinition(container, topic)
        .check(
          topic.results.size + topic.queries.size + topic.commands.size +
            topic.events.size > 0,
          s"${topic.identify} does not define any messages",
          MissingWarning,
          topic.loc
        )
    }

    override def closeTopic(
      state: ValidationState,
      container: Container,
      topic: Topic
    ): ValidationState = {
      state.checkDescription(topic, topic.description)
    }

    override def openInteraction(
      state: ValidationState,
      container: Container,
      interaction: Interaction
    ): ValidationState = {
      state
        .checkDefinition(container, interaction)
        .checkNonEmpty(interaction.actions, "Actions", interaction)
    }

    override def closeInteraction(
      state: ValidationState,
      container: Container,
      interaction: Interaction
    ): ValidationState = {
      state.checkDescription(interaction, interaction.description)
    }

    override def openFeature(
      state: ValidationState,
      container: Container,
      feature: Feature
    ): ValidationState = {
      val state2 = state.checkDefinition(container, feature)
      feature.background.foldLeft(state2) {
        case (s, bg) => s.checkNonEmpty(bg.givens, "Background", feature)
      }

    }

    override def closeFeature(
      state: ValidationState,
      container: Container,
      feature: Feature
    ): ValidationState = {
      state.checkDescription(feature, feature.description)
    }

    override def openAdaptor(
      state: ValidationState,
      container: Container,
      adaptor: Adaptor
    ): ValidationState = {
      state.checkDefinition(container, adaptor)
    }

    override def closeAdaptor(
      state: ValidationState,
      container: Container,
      adaptor: Adaptor
    ): ValidationState = {
      state.checkDescription(adaptor, adaptor.description)
    }

    override def doCommand(
      state: ValidationState,
      container: Container,
      command: Command
    ): ValidationState = {
      val result =
        state
          .checkDefinition(container, command)
          .checkDescription(command, command.description)
          .checkTypeExpression(command.typ, container)
      if (command.events.isEmpty) {
        result.add(
          ValidationMessage(
            command.loc,
            "Commands must always yield at least one event"
          )
        )
      } else {
        command.events.foldLeft(result) {
          case (st, eventRef) =>
            st.checkRef[Event](eventRef)
        }
      }
    }

    override def doEvent(
      state: ValidationState,
      container: Container,
      event: Event
    ): ValidationState = {
      state
        .checkTypeExpression(event.typ, container)
        .checkDescription(event, event.description)
    }

    override def doQuery(
      state: ValidationState,
      container: Container,
      query: Query
    ): ValidationState = {
      state
        .checkDefinition(container, query)
        .checkTypeExpression(query.typ, container)
        .checkRef[Result](query.result.id)
        .checkDescription(query, query.description)
    }

    override def doResult(
      state: ValidationState,
      container: Container,
      result: Result
    ): ValidationState = {
      state
        .checkTypeExpression(result.typ, container)
        .checkDescription(result, result.description)
    }

    override def doType(
      state: ValidationState,
      container: Container,
      typeDef: Type
    ): ValidationState = {
      state
        .checkDefinition(container, typeDef)
        .check(
          typeDef.id.value.head.isUpper,
          s"${typeDef.identify} should start with a capital letter",
          StyleWarning,
          typeDef.loc
        )
        .checkTypeExpression(typeDef.typ, container)
        .checkDescription(typeDef, typeDef.description)
    }

    override def doPredefinedType(
      state: ValidationState,
      container: Container,
      predef: PredefinedType
    ): ValidationState = {
      state
    }

    override def doAction(
      state: ValidationState,
      container: Container,
      action: ActionDefinition
    ): ValidationState = {
      val newState = state
        .checkDefinition(container, action)
        .checkDescription(action, action.description)
      action match {
        case ma: MessageAction =>
          ma.reactions.foldLeft(
            newState
              .checkRef[Entity](ma.receiver)
              .checkRef[Entity](ma.sender)
              .checkRef[MessageDefinition](ma.message)
          ) {
            case (s, reaction) =>
              s.checkRef(reaction.entity)
          }
      }
    }

    override def doExample(
      state: ValidationState,
      container: Container,
      example: Example
    ): ValidationState = {
      state
        .checkDefinition(container, example)
        .checkNonEmpty(example.givens, "Givens", example)
        .checkNonEmpty(example.whens, "Whens", example)
        .checkNonEmpty(example.thens, "Thens", example)
        .checkDescription(example, example.description)
    }

    override def doFunction(
      state: ValidationState,
      container: Container,
      function: Function
    ): ValidationState = {
      state
        .checkDefinition(container, function)
        .checkTypeExpression(
          function.input.getOrElse(Nothing(function.loc)),
          function
        )
        .checkTypeExpression(function.output, function)
        .checkDescription(function, function.description)
    }

    override def doInvariant(
      state: ValidationState,
      container: Container,
      invariant: Invariant
    ): ValidationState = {
      state
        .checkDefinition(container, invariant)
        .checkNonEmpty(invariant.expression, "Expression", invariant)
        .checkDescription(invariant, invariant.description)
    }

    override def doTranslationRule(
      state: ValidationState,
      container: Container,
      rule: TranslationRule
    ): ValidationState = {
      state
        .checkDefinition(container, rule)
        .checkDescription(rule, rule.description)
    }
  }
}
