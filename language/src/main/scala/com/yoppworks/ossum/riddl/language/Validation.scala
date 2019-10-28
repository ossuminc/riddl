package com.yoppworks.ossum.riddl.language

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
        case Pattern(loc, pattern) =>
          try {
            java.util.regex.Pattern.compile(pattern.s)
          } catch {
            case x: PatternSyntaxException =>
              add(ValidationMessage(loc, x.getMessage))
          }
          this
        case UniqueId(loc, entityName) =>
          this
            .checkIdentifier(entityName)
            .checkRef[EntityDef](entityName, definition)
        case _: AST.PredefinedType =>
          this
        case AST.TypeRef(_, id: Identifier) =>
          checkRef[TypeDefinition](id, definition)
        case Optional(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case OneOrMore(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case ZeroOrMore(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case Enumeration(_, enumerators: Seq[Enumerator]) =>
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
              } else {
                s
              }
          }
        case Alternation(_, of) =>
          of.foldLeft(this) {
            case (state, typex) =>
              state.checkTypeExpression(typex, definition)
          }
        case Aggregation(
            loc,
            of: immutable.ListMap[Identifier, TypeExpression]
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
        case Mapping(_, from, to) =>
          this
            .checkTypeExpression(from, definition)
            .checkTypeExpression(to, definition)
        case RangeType(loc, min, max) =>
          this
            .check(
              min.n >= BigInt.long2bigInt(Long.MinValue),
              "Minimum value might be too small to store in a Long",
              Warning,
              loc
            )
            .check(
              max.n <= BigInt.long2bigInt(Long.MaxValue),
              "Maximum value might be too small to store in a Long",
              Warning,
              loc
            )
        case ReferenceType(_, entity: EntityRef) =>
          this.checkRef[EntityDef](entity, definition)
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
      result.checkAddendum(definition, definition.addendum)
    }

    def checkAddendum(
      definition: Definition,
      add: Option[Addendum]
    ): ValidationState = {
      var result = this
      if (add.isEmpty) {
        result = result.check(
          predicate = false,
          s"Definition '${definition.id.value}' " +
            "should have explanations or references",
          MissingWarning,
          definition.loc
        )
      } else {
        val explanation = add.get.explanation
        if (explanation.nonEmpty)
          result = result.checkExplanation(explanation.get)
        val seeAlso = add.get.seeAlso
        if (seeAlso.nonEmpty)
          result = result.checkSeeAlso(seeAlso.get)
      }
      result
    }

    def checkExplanation(
      exp: Explanation
    ): ValidationState = {
      this.check(
        exp.markdown.nonEmpty,
        "Explanations should not be empty",
        MissingWarning,
        exp.loc
      )
    }

    def checkSeeAlso(
      seeAlso: SeeAlso
    ): ValidationState = {
      this.check(
        seeAlso.citations.nonEmpty,
        "SeeAlso references should not be empty",
        MissingWarning,
        seeAlso.loc
      )
    }
  }

  class ValidationFolding extends Folding.Folding[ValidationState] {

    override def openDomain(
      state: ValidationState,
      container: Container,
      domain: DomainDef
    ): ValidationState = {
      domain.subdomain.foldLeft(
        state.checkDefinition(container, domain)
      ) {
        case (st, id) =>
          st.checkRef[DomainDef](id, state.parentOf(domain))
      }
    }

    override def closeDomain(
      state: ValidationState,
      container: Container,
      domain: DomainDef
    ): ValidationState = {
      state
    }

    override def openContext(
      state: Validation.ValidationState,
      container: Container,
      context: AST.ContextDef
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
      context: AST.ContextDef
    ): ValidationState = {
      state
    }

    override def openEntity(
      state: Validation.ValidationState,
      container: Container,
      entity: AST.EntityDef
    ): ValidationState = {
      val result1 = state
        .checkDefinition(container, entity)
        .checkTypeExpression(entity.typ, entity)
        .checkOptions[EntityOption](entity.options, entity.loc)
      val result2: ValidationState =
        entity.produces.foldLeft(result1) { (s, chan) =>
          val persistentClass = EntityPersistent((0, 0)).getClass
          val lookupResult = s
            .checkRef[TopicDef](chan, entity)
            .symbolTable
            .lookup[TopicDef](chan, entity)
          lookupResult match {
            case chan :: Nil =>
              s.check(
                !(chan.events.isEmpty &&
                  entity.options.exists(_.getClass == persistentClass)),
                s"Persistent ${entity.identify} requires a topic " +
                  "that produces events but " +
                  s"${chan.identify} does not define any.",
                Error,
                chan.loc
              )
            case _ =>
              s
          }
        }
      var result = entity.consumes.foldLeft(result2) { (s, chan) =>
        s.checkRef[TopicDef](chan, entity)
      }

      // TODO: invariant?

      if (entity.consumes.isEmpty) {
        result = result.add(
          ValidationMessage(entity.loc, "An entity must consume a topic")
        )
      }
      if (entity.produces.isEmpty &&
          entity.options.exists(_.getClass == classOf[EntityPersistent])) {
        result = result.add(
          ValidationMessage(
            entity.loc,
            "An entity that produces no events on a topic cannot be persistent"
          )
        )
      }
      result
    }

    override def closeEntity(
      state: Validation.ValidationState,
      container: Container,
      entity: AST.EntityDef
    ): ValidationState = {
      state
    }

    override def openTopic(
      state: ValidationState,
      container: Container,
      topic: TopicDef
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
      channel: TopicDef
    ): ValidationState = {
      state
    }

    override def openInteraction(
      state: ValidationState,
      container: Container,
      interaction: InteractionDef
    ): ValidationState = {
      state
        .checkDefinition(container, interaction)
        .checkNonEmpty(interaction.actions, "Actions", interaction)
    }

    override def closeInteraction(
      state: ValidationState,
      container: Container,
      interaction: InteractionDef
    ): ValidationState = {
      state
    }

    override def openFeature(
      state: ValidationState,
      container: Container,
      feature: FeatureDef
    ): ValidationState = {
      val state2 = state
        .checkDefinition(container, feature)
        .checkNonEmpty(feature.description, "Description", feature)
      feature.background.foldLeft(state2) {
        case (s, bg) => s.checkNonEmpty(bg.givens, "Background", feature)
      }

    }

    override def closeFeature(
      state: ValidationState,
      container: Container,
      feature: FeatureDef
    ): ValidationState = {
      state
    }

    override def openAdaptor(
      state: ValidationState,
      container: Container,
      adaptor: AdaptorDef
    ): ValidationState = {
      val result =
        state
          .checkDefinition(container, adaptor)
          .checkRef[ContextDef](adaptor.targetContext, adaptor)
      adaptor.targetDomain.foldLeft(result) {
        case (s, domain) => s.checkRef[DomainDef](domain, adaptor)
      }
    }

    override def closeAdaptor(
      state: ValidationState,
      container: Container,
      adaptor: AdaptorDef
    ): ValidationState = {
      state
    }

    override def doCommand(
      state: ValidationState,
      container: Container,
      command: CommandDef
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
            st.checkRef[EventDef](eventRef, container)
        }
      }
    }

    override def doEvent(
      state: ValidationState,
      container: Container,
      event: EventDef
    ): ValidationState = {
      state.checkTypeExpression(event.typ, container)
    }

    override def doQuery(
      state: ValidationState,
      container: Container,
      query: QueryDef
    ): ValidationState = {
      state
        .checkDefinition(container, query)
        .checkTypeExpression(query.typ, container)
        .checkRef[ResultDef](query.result.id, container)
    }

    override def doResult(
      state: ValidationState,
      container: Container,
      result: ResultDef
    ): ValidationState = {
      state.checkTypeExpression(result.typ, container)
    }

    override def doType(
      state: ValidationState,
      container: Container,
      typeDef: TypeDef
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
      action: ActionDef
    ): ValidationState = {
      val newState = state.checkDefinition(container, action)
      action match {
        case ma: MessageActionDef =>
          ma.reactions.foldLeft(
            newState
              .checkRef[EntityDef](ma.receiver, container)
              .checkRef[EntityDef](ma.sender, container)
              .checkRef[MessageDefinition](ma.message, container)
          ) {
            case (s, reaction) =>
              s.checkRef(reaction.entity, container)
          }
        case da: DirectiveActionDef =>
          da.reactions.foldLeft(
            newState
              .checkRef[EntityDef](da.entity, container)
              .checkRef[MessageDefinition](da.message, container)
              .checkRef[RoleDef](da.role, container)
          ) {
            case (s, reaction) => s.checkRef(reaction.entity, container)
          }
      }
    }

    override def doExample(
      state: ValidationState,
      container: Container,
      example: ExampleDef
    ): ValidationState = {
      state
        .checkDefinition(container, example)
        .checkLiteralString(example.description, "Description", example)
        .checkNonEmpty(example.givens, "Givens", example)
        .checkNonEmpty(example.whens, "Whens", example)
        .checkNonEmpty(example.thens, "Thens", example)
    }

    override def doFunction(
      state: ValidationState,
      container: Container,
      function: FunctionDef
    ): ValidationState = {
      state
    }

    override def doInvariant(
      state: ValidationState,
      container: Container,
      invariant: InvariantDef
    ): ValidationState = {
      state.checkDefinition(container, invariant)
    }

    override def doRole(
      state: ValidationState,
      container: Container,
      role: RoleDef
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
