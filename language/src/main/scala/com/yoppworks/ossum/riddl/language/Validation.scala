package com.yoppworks.ossum.riddl.language

import java.net.URI
import java.util.regex.PatternSyntaxException
import com.yoppworks.ossum.riddl.language.AST.*

import scala.annotation.unused
import scala.reflect.ClassTag
import scala.reflect.classTag

/** Validates an AST */
object Validation {

  def validate[C <: Container](
    root: C,
    options: ValidationOptions = ValidationOptions.Default
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
    override def isWarning: Boolean = true
    override def isMissing: Boolean = true
    override def toString: String = "Missing"
  }

  case object StyleWarning extends ValidationMessageKind {
    override def isWarning: Boolean = true
    override def isStyle: Boolean = true
    override def toString: String = "Style"
  }

  case object Warning extends ValidationMessageKind {
    override def isWarning: Boolean = true
    override def toString: String = "Warning"
  }

  case object Error extends ValidationMessageKind {
    override def isError: Boolean = true
    override def toString: String = "Error"
  }

  case object SevereError extends ValidationMessageKind {
    override def isError: Boolean = true
    override def isSevereError: Boolean = true
    override def toString: String = "Severe"
  }

  case class ValidationMessage(
    loc: Location,
    message: String,
    kind: ValidationMessageKind = Error) {

    def format: String = { s"$kind: $loc: $message" }
  }

  type ValidationMessages = List[ValidationMessage]

  val NoValidationMessages: List[ValidationMessage] = List.empty[ValidationMessage]

  trait ValidationOptions extends Riddl.Options

  object ValidationOptions {

    private final case class ValidationOptionsImpl(
      showTimes: Boolean,
      showWarnings: Boolean,
      showMissingWarnings: Boolean,
      showStyleWarnings: Boolean)
        extends ValidationOptions

    def apply(
      showTimes: Boolean = false,
      showWarnings: Boolean = true,
      showMissingWarnings: Boolean = true,
      showStyleWarnings: Boolean = true
    ): ValidationOptions =
      ValidationOptionsImpl(showTimes, showWarnings, showMissingWarnings, showStyleWarnings)

    val Default: ValidationOptions = apply()
  }

  case class ValidationState(
    symbolTable: SymbolTable,
    options: ValidationOptions = ValidationOptions.Default,
    msgs: ValidationMessages = NoValidationMessages)
      extends Folding.State[ValidationState] {
    def step(f: ValidationState => ValidationState): ValidationState = f(this)

    def parentOf(
      definition: Definition
    ): Container = { symbolTable.parentOf(definition).getOrElse(RootContainer.empty) }

    def isReportMissingWarnings: Boolean = options.showMissingWarnings

    def isReportStyleWarnings: Boolean = options.showStyleWarnings

    def lookup[T <: Definition: ClassTag](
      id: PathIdentifier
    ): List[T] = { symbolTable.lookup[T](id.value) }

    def lookup[T <: Definition: ClassTag](
      id: Seq[String]
    ): List[T] = { symbolTable.lookup[T](id) }

    def addIf(predicate: Boolean)(msg: ValidationMessage): ValidationState = {
      if (predicate) { add(msg) }
      else { this }
    }

    def add(
      msg: ValidationMessage
    ): ValidationState = {
      msg.kind match {
        case StyleWarning =>
          if (isReportStyleWarnings) { this.copy(msgs = msgs :+ msg) }
          else { this }
        case MissingWarning =>
          if (isReportMissingWarnings) { this.copy(msgs = msgs :+ msg) }
          else { this }

        case _ => this.copy(msgs = msgs :+ msg)
      }
    }

    def check(
      predicate: Boolean = true,
      message: => String,
      kind: ValidationMessageKind,
      loc: Location
    ): ValidationState = {
      if (!predicate) { add(ValidationMessage(loc, message, kind)) }
      else { this }
    }

    def checkIdentifierLength[T <: Definition](
      d: T,
      min: Int = 3
    ): ValidationState = {
      if (d.id.value.length < min) {
        add(ValidationMessage(
          d.id.loc,
          s"${d.kind} identifier '${d.id.value}' is too short. Identifiers should be at least $min characters.",
          StyleWarning
        ))
      } else { this }
    }

    def checkPattern(p: Pattern): ValidationState = {
      try {
        val compound = p.pattern.map(_.s).reduce(_ + _)
        java.util.regex.Pattern.compile(compound)
        this
      } catch { case x: PatternSyntaxException => add(ValidationMessage(p.loc, x.getMessage)) }
    }

    def checkEnumeration(
      definition: Definition,
      enumerators: Seq[Enumerator],
      desc: Option[Description]
    ): ValidationState = {
      enumerators.foldLeft(this) { case (state, enumerator) =>
        val id = enumerator.id
        var s = state.checkIdentifierLength(enumerator).check(
          id.value.head.isUpper,
          s"Enumerator '${id.value}' must start with upper case",
          StyleWarning,
          id.loc
        )
        s = enumerator.typeRef match {
          case Some(typeRef) => lookup[Type](typeRef.id) match {
              case Nil => s.check(
                  predicate = false,
                  message = s"Enumeration references a non-existent type: ${typeRef.id.value}",
                  Error,
                  typeRef.id.loc
                )
              case singleRef +: Nil => s.check(
                  singleRef.typ.isInstanceOf[Aggregation],
                  s"Enumeration references a non-aggregation type: ${typeRef.id.value}",
                  Error,
                  typeRef.id.loc
                )
              case head +: tail => s.check(
                  predicate = false,
                  message = s"Enumeration references an ambiguous type: ${typeRef.id.value}.  " +
                    s"Resolved references include: ${(head +: tail).map(_.id.value)}",
                  Error,
                  typeRef.id.loc
                )
              case List(_) => s.check(
                  predicate = false,
                  message = s"Enumeration references a non-existent type: ${typeRef.id.value}",
                  Error,
                  typeRef.id.loc
                )
              case _ => throw new IllegalStateException("Bad List[T] match")
            }
          case _ => s
        }
        s.checkDescription(definition, desc)
      }
    }

    def checkAlternation(
      definition: Definition,
      alternation: AST.Alternation
    ): ValidationState = {
      alternation.of.foldLeft(this) { case (state, typex) =>
        state.checkTypeExpression(typex, definition)
      }.checkDescription(definition, alternation.description)
    }

    def checkRangeType(
      definition: Definition,
      rt: RangeType
    ): ValidationState = {
      this.check(
        rt.min.n >= BigInt.long2bigInt(Long.MinValue),
        "Minimum value might be too small to store in a Long",
        Warning,
        rt.loc
      ).check(
        rt.max.n <= BigInt.long2bigInt(Long.MaxValue),
        "Maximum value might be too large to store in a Long",
        Warning,
        rt.loc
      ).checkDescription(definition, rt.description)
    }

    def checkAggregation(
      definition: Definition,
      agg: Aggregation
    ): ValidationState = {
      agg.fields.foldLeft(this) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          "Field names should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, field).checkDescription(field, field.description)
      }.checkDescription(definition, agg.description)
    }

    def checkMapping(
      definition: AST.Definition,
      mapping: AST.Mapping
    ): ValidationState = {
      this.checkTypeExpression(mapping.from, definition).checkTypeExpression(mapping.to, definition)
        .checkDescription(definition, mapping.description)
    }

    def checkTypeExpression(
      typ: TypeExpression,
      definition: Definition
    ): ValidationState = {
      typ match {
        case p @ Pattern(_, _, addendum) => checkPattern(p).checkDescription(definition, addendum)
        case UniqueId(_, entityName, addendum) => this.checkRef[Entity](entityName)
            .checkDescription(definition, addendum)
        case _: AST.PredefinedType                => this
        case AST.TypeRef(_, id: PathIdentifier)   => checkRef[Type](id)
        case Optional(_, typex: TypeExpression)   => checkTypeExpression(typex, definition)
        case OneOrMore(_, typex: TypeExpression)  => checkTypeExpression(typex, definition)
        case ZeroOrMore(_, typex: TypeExpression) => checkTypeExpression(typex, definition)
        case Enumeration(_, enumerators: Seq[Enumerator], desc) =>
          checkEnumeration(definition, enumerators, desc)
        case alt: Alternation => checkAlternation(definition, alt)
        case agg: Aggregation => checkAggregation(definition, agg)
        case mapping: Mapping => checkMapping(definition, mapping)
        case rt: RangeType    => checkRangeType(definition, rt)
        case ReferenceType(_, entity: EntityRef, addendum) => this.checkRef[Entity](entity)
            .checkDescription(definition, addendum)
      }
    }

    def checkMessageRef(ref: MessageReference): ValidationState = {
      ref match {
        case CommandRef(_, id) => checkRef[Command](id)
        case EventRef(_, id)   => checkRef[Event](id)
        case QueryRef(_, id)   => checkRef[Query](id)
        case ResultRef(_, id)  => checkRef[Result](id)
      }
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference
    ): ValidationState = { checkRef[T](reference.id) }

    def checkRef[T <: Definition: ClassTag](
      id: PathIdentifier
    ): ValidationState = {
      if (id.value.nonEmpty) {
        val tc = classTag[T].runtimeClass
        symbolTable.lookupSymbol[T](id.value) match {
          case Nil => add(ValidationMessage(
              id.loc,
              s"'${id.format}' is not defined but should be a ${tc.getSimpleName}",
              Error
            ))
          // Type mismatch or subtype
          case (d, None) :: Nil => check(
              tc.isAssignableFrom(d.getClass),
              s"'${id.format}' was expected to be ${tc.getSimpleName}" +
                s" but is ${d.getClass.getSimpleName} instead",
              Error,
              id.loc
            )
          // Type match
          case (_, Some(t)) :: Nil =>
            assert(t.getClass == tc)
            this
          // Too many matches / non-unique
          case (d, optT) :: tail =>
            // Handle domain, context, entity same name
            val definitions = (d :: tail.map { case (d, _) => d })
            val types = (optT :: tail.map { case (_, t) => t }) collect { case Some(tpe) => tpe }
            val exactlyOneMatch = types.count(_.getClass == tc) == 1
            val allDifferent = definitions.map(_.kind).distinct.size == definitions.size
            if (exactlyOneMatch && allDifferent) { this }
            else {
              add(ValidationMessage(
                id.loc,
                s"""'${id.value}' is not uniquely defined.
                   |Definitions are:
                   |${formatDefinitions(definitions)}""".stripMargin,
                Error
              ))
            }
        }
      } else { this }
    }

    private def formatDefinitions[T <: Definition](list: List[T]): String = {
      list.map { dfntn => "  " + dfntn.id.value + " (" + dfntn.loc + ")" }.mkString("\n")
    }

    def checkSequence[A](
      elements: Seq[A]
    )(fold: (ValidationState, A) => ValidationState
    ): ValidationState = elements.foldLeft(this) { case (next, element) => fold(next, element) }

    def checkNonEmpty(
      list: Seq[?],
      name: String,
      thing: Definition
    ): ValidationState = {
      check(list.nonEmpty, s"$name in ${thing.identify} should not be empty", Error, thing.loc)
    }

    def checkOptions[T](options: Seq[T], loc: Location): ValidationState = {
      check(options.size == options.distinct.size, "Options should not be repeated", Error, loc)
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
      val matches = result.lookup[Definition](path)
      if (matches.isEmpty) {
        result = result.add(ValidationMessage(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!",
          SevereError
        ))
      } else if (matches.size >= 2) {
        matches.groupBy(result.symbolTable.parentOf(_)).get(Some(container)) match {
          case Some(head :: tail) if tail.nonEmpty =>
            result = result.add(ValidationMessage(
              head.id.loc,
              s"${definition.identify} is defined multiple times; other " +
                s"definitions are:\n  " + matches.map(x => x.identify + " " + x.loc)
                  .mkString("\n  "),
              Error
            ))
          case _ =>
        }
      }
      result
    }

    def checkDescription(
      definition: Definition,
      @unused
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
        val result: ValidationState = this.check(
          brief.nonEmpty,
          s"For ${definition.identify}, brief description should not be empty",
          MissingWarning,
          desc.loc
        ).check(
          desc.details.nonEmpty,
          s"For ${definition.identify}, detailed description should not be empty",
          MissingWarning,
          desc.loc
        )
        desc.citations.foldLeft(result) { case (next: ValidationState, citation: LiteralString) =>
          val uriMsg =
            try { new URI(citation.s); "" }
            catch { case x: Exception => x.getMessage }
          next.check(uriMsg.isEmpty, uriMsg, Error, citation.loc).check(
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
    ): ValidationState = { state.checkDefinition(container, domain) }

    override def closeDomain(
      state: ValidationState,
      container: Container,
      domain: Domain
    ): ValidationState = { state.checkDescription(domain, domain.description) }

    override def openContext(
      state: Validation.ValidationState,
      container: Container,
      context: AST.Context
    ): ValidationState = {
      val result = state.checkDefinition(container, context)
        .checkOptions[ContextOption](context.options, context.loc)
      if (context.entities.isEmpty) {
        result.add(
          ValidationMessage(context.loc, "Contexts that define no entities are not valid", Error)
        )
      } else { result }
    }

    override def closeContext(
      state: Validation.ValidationState,
      container: Container,
      context: AST.Context
    ): ValidationState = { state.checkDescription(context, context.description) }

    override def openEntity(
      state: Validation.ValidationState,
      container: Container,
      entity: AST.Entity
    ): ValidationState = {
      state.checkDefinition(container, entity).checkOptions[EntityOption](
        entity.options,
        entity.loc
      ).checkSequence(entity.states) { (next, state) =>
        next.checkTypeExpression(state.typeEx, state)
      }.addIf(entity.handlers.isEmpty) {
        ValidationMessage(entity.loc, s"Entity '${entity.id.value}' must define a handler")
      }.addIf(entity.handlers.nonEmpty && entity.handlers.forall(_.clauses.isEmpty)) {
        ValidationMessage(
          entity.loc,
          s"Entity '${entity.id.value}' has only empty handlers",
          MissingWarning
        )
      }.addIf(entity.hasOption[EntityFiniteStateMachine] && entity.states.size < 2) {
        ValidationMessage(
          entity.loc,
          s"Entity '${entity.id.value}' is declared as a finite-state-machine, but does not " +
            s"have at least two states",
          Error
        )
      }
    }

    override def doHandler(
      state: ValidationState,
      container: Container,
      handler: Handler
    ): ValidationState = {
      handler.clauses.foldLeft(state) { (state, onClause) => doOnClause(state, handler, onClause) }
    }

    def doOnClause(
      state: ValidationState,
      @unused
      parent: Handler,
      onClause: OnClause
    ): ValidationState = {
      val result = state.checkMessageRef(onClause.msg)
      onClause.actions.foldLeft(result) { (state, action) =>
        doClauseStatement(state, onClause, action)
      }
      // TODO: validate the following:
      // referential integrity of state fields
      // etc
    }

    // TODO: WIP
    def doClauseStatement(
      state: ValidationState,
      @unused
      parent: OnClause,
      clauseStatement: OnClauseStatement
    ): ValidationState = {
      clauseStatement match {
        case SetStatement(_, path, _, _) =>
          // TODO:  not working (state fields are not captured as references)
          state.checkRef[Field](path)
        // validate state changes
        case RemoveStatement(_, _, _, _)  => state
        case AppendStatement(_, _, _, _)  => state
        case SendStatement(_, _, _, _)    => state
        case ExecuteStatement(_, _, _)    => state
        case PublishStatement(_, _, _, _) => state
        case WhenStatement(_, _, _, _)    => state
      }
    }

    override def closeEntity(
      state: Validation.ValidationState,
      container: Container,
      entity: AST.Entity
    ): ValidationState = { state.checkDescription(entity, entity.description) }

    override def openTopic(
      state: ValidationState,
      container: Container,
      topic: Topic
    ): ValidationState = {
      state.checkDefinition(container, topic).check(
        topic.results.size + topic.queries.size + topic.commands.size + topic.events.size > 0,
        s"${topic.identify} does not define any messages",
        MissingWarning,
        topic.loc
      )
    }

    override def closeTopic(
      state: ValidationState,
      container: Container,
      topic: Topic
    ): ValidationState = { state.checkDescription(topic, topic.description) }

    override def openInteraction(
      state: ValidationState,
      container: Container,
      interaction: Interaction
    ): ValidationState = {
      state.checkDefinition(container, interaction)
        .checkNonEmpty(interaction.actions, "Actions", interaction)
    }

    override def closeInteraction(
      state: ValidationState,
      container: Container,
      interaction: Interaction
    ): ValidationState = { state.checkDescription(interaction, interaction.description) }

    override def openFeature(
      state: ValidationState,
      container: Container,
      feature: Feature
    ): ValidationState = {
      val state2 = state.checkDefinition(container, feature)
      feature.background.foldLeft(state2) { case (s, bg) =>
        s.checkNonEmpty(bg.givens, "Background", feature)
      }
    }

    override def closeFeature(
      state: ValidationState,
      container: Container,
      feature: Feature
    ): ValidationState = { state.checkDescription(feature, feature.description) }

    override def openAdaptor(
      state: ValidationState,
      container: Container,
      adaptor: Adaptor
    ): ValidationState = { state.checkDefinition(container, adaptor) }

    override def closeAdaptor(
      state: ValidationState,
      container: Container,
      adaptor: Adaptor
    ): ValidationState = { state.checkDescription(adaptor, adaptor.description) }

    override def openMessage(
      state: ValidationState,
      container: Container,
      message: MessageDefinition
    ): ValidationState = {
      val result = state.checkDefinition(container, message)
        .checkDescription(message, message.description).checkTypeExpression(message.typ, container)
      super.openMessage(result, container, message)
    }

    override def closeMessage(
      state: ValidationState,
      container: Container,
      message: MessageDefinition
    ): ValidationState = {
      val result = state.checkDescription(message, message.description)
      super.openMessage(result, container, message)
    }

    override def openCommand(
      state: ValidationState,
      container: Container,
      command: Command
    ): ValidationState = {
      if (command.events.isEmpty) {
        state.add(ValidationMessage(command.loc, "Commands must always yield at least one event"))
      } else {
        command.events.foldLeft(state) { case (st, eventRef) => st.checkRef[Event](eventRef) }
      }
    }

    override def openEvent(
      state: ValidationState,
      container: Container,
      event: Event
    ): ValidationState = { state }

    override def openQuery(
      state: ValidationState,
      container: Container,
      query: Query
    ): ValidationState = { state.checkRef[Result](query.result.id) }

    override def openResult(
      state: ValidationState,
      container: Container,
      result: Result
    ): ValidationState = { state }

    override def doType(
      state: ValidationState,
      container: Container,
      typeDef: Type
    ): ValidationState = {
      state.checkDefinition(container, typeDef).check(
        typeDef.id.value.head.isUpper,
        s"${typeDef.identify} should start with a capital letter",
        StyleWarning,
        typeDef.loc
      ).checkTypeExpression(typeDef.typ, container).checkDescription(typeDef, typeDef.description)
    }

    override def doPredefinedType(
      state: ValidationState,
      container: Container,
      predef: PredefinedType
    ): ValidationState = { state }

    override def doAction(
      state: ValidationState,
      container: Container,
      action: ActionDefinition
    ): ValidationState = {
      val newState = state.checkDefinition(container, action)
        .checkDescription(action, action.description)
      action match {
        case ma: MessageAction => ma.reactions.foldLeft(
            newState.checkRef[Entity](ma.receiver).checkRef[Entity](ma.sender)
              .checkRef[MessageDefinition](ma.message)
          ) { case (s, reaction) => s.checkRef(reaction.entity) }
      }
    }

    override def doExample(
      state: ValidationState,
      container: Container,
      example: Example
    ): ValidationState = {
      state.checkDefinition(container, example).checkNonEmpty(example.givens, "Givens", example)
        .checkNonEmpty(example.whens, "Whens", example)
        .checkNonEmpty(example.thens, "Thens", example)
        .checkDescription(example, example.description)
    }

    override def doFunction(
      state: ValidationState,
      container: Container,
      function: Function
    ): ValidationState = {
      state.checkDefinition(container, function)
        .checkTypeExpression(function.input.getOrElse(Nothing(function.loc)), function)
        .checkTypeExpression(function.output, function)
        .checkDescription(function, function.description)
    }

    override def doInvariant(
      state: ValidationState,
      container: Container,
      invariant: Invariant
    ): ValidationState = {
      state.checkDefinition(container, invariant)
        .checkNonEmpty(invariant.expression, "Expression", invariant)
        .checkDescription(invariant, invariant.description)
    }

    override def doTranslationRule(
      state: ValidationState,
      container: Container,
      rule: TranslationRule
    ): ValidationState = {
      state.checkDefinition(container, rule).checkDescription(rule, rule.description)
    }
  }
}
