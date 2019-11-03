package com.yoppworks.ossum.riddl.language

import java.net.URI
import java.util.regex.PatternSyntaxException

import com.yoppworks.ossum.riddl.language.AST._

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.reflect.classTag

/** Validates an AST */
object Validation {

  def validate[C <: Container](
    root: C,
    options: Seq[ValidationOptions] = defaultOptions
  ): Seq[ValidationMessage] = {
    val symTab = SymbolTable(root)
    val state = ValidationState(symTab, options)
    val folding = new ValidationFolding
    folding.foldLeft(root, root, state).msgs
  }

  sealed trait ValidationMessageKind {
    def isError: Boolean = false

    def isWarning: Boolean = false
  }

  case object MissingWarning extends ValidationMessageKind {
    override def isWarning = true

    override def toString: String = "Missing"
  }

  case object StyleWarning extends ValidationMessageKind {
    override def isWarning = true

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
      List[ValidationMessage]()
    }

    def apply(msg: ValidationMessage): ValidationMessages = {
      apply() :+ msg
    }

    def apply(msgs: ValidationMessage*): ValidationMessages = {
      apply() ++ msgs
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

  case class ValidationState(
    symbolTable: SymbolTable,
    options: Seq[ValidationOptions] = defaultOptions,
    msgs: ValidationMessages = NoValidationMessages
  ) extends Folding.State[ValidationState] {
    def step(f: ValidationState => ValidationState): ValidationState = f(this)

    def parentOf(
      definition: Definition
    ): Container = {
      symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
    }

    def isReportMissingWarnings: Boolean =
      options.contains(ReportMissingWarnings)

    def isReportStyleWarnings: Boolean =
      options.contains(ReportStyleWarnings)

    def lookup[T <: Definition: ClassTag](
      id: Identifier,
      within: Container
    ): List[T] = {
      symbolTable.lookup[T](id, within)
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

    def checkIdentifier(id: Identifier): ValidationState = {
      if (id.value.length <= 2) {
        add(
          ValidationMessage(
            id.loc,
            "Identifier is too short. Identifiers must be at least 3 characters",
            StyleWarning
          )
        )
      }
      this
    }

    def checkTypeExpression(
      typ: TypeExpression,
      definition: Container
    ): ValidationState = {
      typ match {
        case Pattern(loc, pattern, addendum) =>
          try {
            val compound = pattern.map(_.s).reduce(_ + _)
            java.util.regex.Pattern.compile(compound)
          } catch {
            case x: PatternSyntaxException =>
              add(ValidationMessage(loc, x.getMessage))
          }
          this.checkDescription(definition, addendum)
        case UniqueId(_, entityName, addendum) =>
          this
            .checkIdentifier(entityName)
            .checkRef[Entity](entityName, definition)
            .checkDescription(definition, addendum)
        case _: AST.PredefinedType =>
          this
        case AST.TypeRef(_, id: Identifier) =>
          checkRef[Type](id, definition)
        case Optional(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case OneOrMore(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case ZeroOrMore(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case Enumeration(_, enumerators: Seq[Enumerator], addendum) =>
          enumerators.foldLeft(this) {
            case (state, enumerator) =>
              val id = enumerator.id
              val s = state
                .checkIdentifier(id)
                .check(
                  id.value.head.isUpper,
                  s"Enumerator '${id.value}' must start with lower case",
                  StyleWarning,
                  id.loc
                )
              if (enumerator.value.nonEmpty) {
                s.checkTypeExpression(enumerator.value.get, definition)
                  .checkDescription(definition, addendum)
              } else {
                s.checkDescription(definition, addendum)
              }
          }
        case Alternation(_, of, addendum) =>
          of.foldLeft(this) {
              case (state, typex) =>
                state.checkTypeExpression(typex, definition)
            }
            .checkDescription(definition, addendum)
        case Aggregation(
            loc,
            of: immutable.ListMap[Identifier, TypeExpression],
            addendum
            ) =>
          of.foldLeft(this) {
              case (state, (id, typex)) =>
                state
                  .checkIdentifier(id)
                  .check(
                    id.value.head.isLower,
                    "Field names should start with a lower case letter",
                    StyleWarning,
                    loc
                  )
                  .checkTypeExpression(typex, definition)
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
          this
            .checkRef[Entity](entity, definition)
            .checkDescription(definition, addendum)
      }
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference,
      within: Container
    ): ValidationState = {
      checkRef[T](reference.id, within)
    }

    def checkRef[T <: Definition: ClassTag](
      id: Identifier,
      within: Container
    ): ValidationState = {
      if (id.value.nonEmpty) {
        val tc = classTag[T].runtimeClass
        symbolTable.lookup[T](id, within) match {
          case Nil =>
            add(
              ValidationMessage(
                id.loc,
                s"'${id.value}' is not defined but should be a ${tc.getSimpleName}",
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
      result = result.check(
        definition.id.value.length > 2,
        s"${definition.identify} has a name that is too short.",
        StyleWarning,
        definition.loc
      )
      val matches =
        result.lookup[Definition](definition.id, container)
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
                s"${head.identify} is defined multiple times; other " +
                  s"definitions are:\n  " +
                  matches.map(x => x.identify + " " + x.loc).mkString("\n  "),
                Error
              )
            )
          case _ =>
        }
      }
      result.checkDescription(definition, definition.description)
    }

    def checkDescription(
      definition: Definition,
      description: Option[Description]
    ): ValidationState = {
      if (description.isEmpty) {
        this.check(
          predicate = false,
          s"Definition '${definition.id.value}' should have a description",
          MissingWarning,
          definition.loc
        )
      } else {
        val desc = description.get
        val brief = desc.brief
        val result: ValidationState = this
          .check(
            brief.s.nonEmpty,
            s"Brief description should not be empty",
            MissingWarning,
            brief.loc
          )
          .check(
            desc.details.nonEmpty,
            "Detailed description should not be empty",
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
      state
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
      state
    }

    override def openEntity(
      state: Validation.ValidationState,
      container: Container,
      entity: AST.Entity
    ): ValidationState = {
      var result = state
        .checkDefinition(container, entity)
        .checkTypeExpression(entity.state, entity)
        .checkOptions[EntityOption](entity.options, entity.loc)
      result = entity.consumers.foldLeft(result) { (s, consumer) =>
        s.checkRef[Topic](consumer.topic, entity)
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
            s"Entity `${entity.id.value}` has only empty topic consumers",
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
      state
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
      channel: Topic
    ): ValidationState = {
      state
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
      state
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
      state
    }

    override def openAdaptor(
      state: ValidationState,
      container: Container,
      adaptor: Adaptor
    ): ValidationState = {
      val result =
        state
          .checkDefinition(container, adaptor)
          .checkRef[Context](adaptor.targetContext, adaptor)
      adaptor.targetDomain.foldLeft(result) {
        case (s, domain) => s.checkRef[Domain](domain, adaptor)
      }
    }

    override def closeAdaptor(
      state: ValidationState,
      container: Container,
      adaptor: Adaptor
    ): ValidationState = {
      state
    }

    override def doCommand(
      state: ValidationState,
      container: Container,
      command: Command
    ): ValidationState = {
      val result =
        state
          .checkDefinition(container, command)
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
            st.checkRef[Event](eventRef, container)
        }
      }
    }

    override def doEvent(
      state: ValidationState,
      container: Container,
      event: Event
    ): ValidationState = {
      state.checkTypeExpression(event.typ, container)
    }

    override def doQuery(
      state: ValidationState,
      container: Container,
      query: Query
    ): ValidationState = {
      state
        .checkDefinition(container, query)
        .checkTypeExpression(query.typ, container)
        .checkRef[Result](query.result.id, container)
    }

    override def doResult(
      state: ValidationState,
      container: Container,
      result: Result
    ): ValidationState = {
      state.checkTypeExpression(result.typ, container)
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
          s"Type name ${typeDef.id.value} should start with a capital letter",
          StyleWarning,
          typeDef.loc
        )
        .checkTypeExpression(typeDef.typ, container)
    }

    override def doPredefinedType(
      result: ValidationState,
      container: Container,
      predef: PredefinedType
    ): ValidationState = {
      result
    }

    override def doAction(
      state: ValidationState,
      container: Container,
      action: ActionDefinition
    ): ValidationState = {
      val newState = state.checkDefinition(container, action)
      action match {
        case ma: MessageAction =>
          ma.reactions.foldLeft(
            newState
              .checkRef[Entity](ma.receiver, container)
              .checkRef[Entity](ma.sender, container)
              .checkRef[MessageDefinition](ma.message, container)
          ) {
            case (s, reaction) =>
              s.checkRef(reaction.entity, container)
          }
        case da: DirectiveAction =>
          da.reactions.foldLeft(
            newState
              .checkRef[Entity](da.entity, container)
              .checkRef[MessageDefinition](da.message, container)
              .checkRef[Role](da.role, container)
          ) {
            case (s, reaction) => s.checkRef(reaction.entity, container)
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
    }

    override def doFunction(
      state: ValidationState,
      container: Container,
      function: Action
    ): ValidationState = {
      state
    }

    override def doInvariant(
      state: ValidationState,
      container: Container,
      invariant: Invariant
    ): ValidationState = {
      state.checkDefinition(container, invariant)
    }

    override def doRole(
      state: ValidationState,
      container: Container,
      role: Role
    ): ValidationState = {
      state
        .checkDefinition(container, role)
        .checkOptions[RoleOption](role.options, role.loc)
        .checkNonEmpty(role.responsibilities, "Responsibilities", role)
        .checkNonEmpty(role.capacities, "Capacities", role)
    }

    override def doTranslationRule(
      state: ValidationState,
      container: Container,
      rule: TranslationRule
    ): ValidationState = {
      state.checkDefinition(container, rule)
    }
  }
}
