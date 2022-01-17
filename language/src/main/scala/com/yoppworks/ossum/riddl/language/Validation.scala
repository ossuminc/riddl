package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

import java.util.regex.PatternSyntaxException
import scala.annotation.unused
import scala.reflect.{ClassTag, classTag}

/** Validates an AST */
object Validation {

  def validate[C <: Container[Definition]](
    root: C,
    options: ValidationOptions = ValidationOptions.Default
  ): ValidationMessages = {
    val symTab = SymbolTable(root)
    val state = ValidationState(symTab, options)
    val folding = new ValidationFolding
    folding.foldLeft(root, root, state).msgs.sortBy(_.loc)
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
    kind: ValidationMessageKind = Error)
      extends Ordered[ValidationMessage] {

    def format: String = { s"$kind: $loc: $message" }

    override def compare(that: ValidationMessage): Int = this.loc.compare(that.loc)
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
    ): Container[Definition] = { symbolTable.parentOf(definition).getOrElse(RootContainer.empty) }

    def isReportMissingWarnings: Boolean = options.showMissingWarnings

    def isReportStyleWarnings: Boolean = options.showStyleWarnings

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
        val s = state.checkIdentifierLength(enumerator).check(
          id.value.head.isUpper,
          s"Enumerator '${id.value}' must start with upper case",
          StyleWarning,
          id.loc
        )
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
          "Field names in aggregates should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, field).checkDescription(field, field.description)
      }.checkDescription(definition, agg.description)
    }

    def checkMessageType(
      definition: Definition,
      mt: MessageType
    ): ValidationState = {
      val kind = mt.messageKind.kind
      mt.fields.foldLeft(this) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          s"Field names in $kind messages should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, field).checkDescription(field, field.description)
      }.checkDescription(definition, mt.description)
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
        case UniqueId(_, entityName, addendum) => this.checkPathRef[Entity](entityName)()
            .checkDescription(definition, addendum)
        case _: AST.PredefinedType                => this
        case AST.TypeRef(_, id: PathIdentifier)   => checkPathRef[Type](id)()
        case Optional(_, typex: TypeExpression)   => checkTypeExpression(typex, definition)
        case OneOrMore(_, typex: TypeExpression)  => checkTypeExpression(typex, definition)
        case ZeroOrMore(_, typex: TypeExpression) => checkTypeExpression(typex, definition)
        case Enumeration(_, enumerators: Seq[Enumerator], desc) =>
          checkEnumeration(definition, enumerators, desc)
        case alt: Alternation => checkAlternation(definition, alt)
        case agg: Aggregation => checkAggregation(definition, agg)
        case mt: MessageType  => checkMessageType(definition, mt)
        case mapping: Mapping => checkMapping(definition, mapping)
        case rt: RangeType    => checkRangeType(definition, rt)
        case ReferenceType(_, entity: EntityRef, addendum) => this.checkRef[Entity](entity)
            .checkDescription(definition, addendum)
      }
    }

    def checkMessageRef(ref: MessageRef, kind: MessageKind): ValidationState = {
      symbolTable.lookupSymbol[Type](ref.id.value) match {
        case Nil => add(ValidationMessage(
            ref.id.loc,
            s"'${ref.id.format}' is not defined but should be a ${kind.kind} Type",
            Error
          ))
        case (d, _) :: Nil => d match {
            case Type(_, _, typ, _) => typ match {
                case MessageType(loc, mk, _, _) => check(
                    mk == kind,
                    s"'${ref.id.format}' was expected to be a ${kind.kind} Type" +
                      s" but is a ${mk.kind} Type instead",
                    Error,
                    loc
                  )
                case te: TypeExpression => add(ValidationMessage(
                    te.loc,
                    s"'${ref.id.format}' should reference a ${kind.kind} Type but is a ${te.kind} instead",
                    Error
                  ))
              }
            case _ => add(ValidationMessage(
                d.loc,
                s"'${ref.id.format}' was expected to be a ${kind.kind} Type but is a ${d.kind} Type instead",
                Error
              ))
          }
        case (d, optT) :: tail => handleMultipleResultsCase[Type](ref.id, d, optT, tail)
        case _                 => this
      }
    }

    private type SingleMatchValidationFunction = (
      ValidationState,
      Class[?],
      PathIdentifier,
      Class[? <: Definition],
      Definition,
      Option[Definition]
    ) => ValidationState

    private val defaultSingleMatchValidationFunction: SingleMatchValidationFunction =
      (state, foundClass, id, defClass, _, _) => {
        state.check(
          foundClass.isAssignableFrom(defClass),
          s"'${id.format}' was expected to be a ${foundClass.getSimpleName} but is a ${defClass.getSimpleName} instead",
          Error,
          id.loc
        )
      }

    private def handleMultipleResultsCase[T <: Definition: ClassTag](
      id: PathIdentifier,
      d: Definition,
      optT: Option[T],
      tail: List[(Definition, Option[T])]
    ): ValidationState = {
      // Handle domain, context, entity same name
      val tc = classTag[T].runtimeClass
      val definitions = d :: tail.map { case (d, _) => d }
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

    def checkPathRef[T <: Definition: ClassTag](
      id: PathIdentifier
    )(validator: SingleMatchValidationFunction = defaultSingleMatchValidationFunction
    ): ValidationState = {
      if (id.value.nonEmpty) {
        val tc = classTag[T].runtimeClass
        symbolTable.lookupSymbol[T](id.value) match {
          case Nil => add(ValidationMessage(
              id.loc,
              s"'${id.format}' is not defined but should be a ${tc.getSimpleName}",
              Error
            ))
          // Single match, defer to validation function
          case (d, optT) :: Nil => validator(this, tc, id, d.getClass, d, optT)
          // Too many matches / non-unique
          case (d, optT) :: tail => handleMultipleResultsCase[T](id, d, optT, tail)
        }
      } else { this }
    }

    def checkRef[T <: Definition: ClassTag](reference: Reference): ValidationState = {
      checkPathRef[T](reference.id)()
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
      thing: Definition,
      kind: ValidationMessageKind = Error
    ): ValidationState = {
      check(list.nonEmpty, s"$name in ${thing.identify} should not be empty", kind, thing.loc)
    }

    def checkOptions[T](options: Seq[T], loc: Location): ValidationState = {
      check(options.size == options.distinct.size, "Options should not be repeated", Error, loc)
    }

    def checkDefinition(
      container: Container[Definition],
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
        this.check(
          desc.lines.nonEmpty && desc.lines.exists(_.s.nonEmpty),
          s"For ${definition.identify}, description is declared but empty",
          MissingWarning,
          desc.loc
        )
      }
    }

    def checkContainer(
      parent: Container[Definition],
      container: Container[Definition]
    ): ValidationState = {
      this.checkDefinition(parent, container).check(
        container.contents.nonEmpty,
        s"Container${container.identify} in ${parent.identify} must have content.",
        MissingWarning,
        container.loc
      )
    }
  }

  class ValidationFolding extends Folding.Folding[ValidationState] {

    override def openDomain(
      state: ValidationState,
      container: Container[Domain],
      domain: Domain
    ): ValidationState = {
      state.checkContainer(container, domain)
        .checkNonEmpty(domain.contents, "contents", domain, MissingWarning)
    }

    override def closeDomain(
      state: ValidationState,
      container: Container[Domain],
      domain: Domain
    ): ValidationState = { state.checkDescription(domain, domain.description) }

    override def openContext(
      state: Validation.ValidationState,
      container: Domain,
      context: AST.Context
    ): ValidationState = {
      val result = state.checkContainer(container, context)
        .checkOptions[ContextOption](context.options, context.loc)
      if (context.entities.isEmpty) {
        result.add(
          ValidationMessage(context.loc, "Contexts that define no entities are not valid", Error)
        )
      } else { result }
    }

    override def closeContext(
      state: Validation.ValidationState,
      container: Domain,
      context: AST.Context
    ): ValidationState = { state.checkDescription(context, context.description) }

    override def openEntity(
      state: Validation.ValidationState,
      container: Context,
      entity: AST.Entity
    ): ValidationState = {
      state.checkContainer(container, entity).checkOptions[EntityOption](entity.options, entity.loc)
        .checkSequence(entity.states) { (next, state) =>
          next.checkTypeExpression(state.typeEx, state)
        }.addIf(entity.handlers.isEmpty) {
          ValidationMessage(entity.loc, s"Entity '${entity.id.format}' must define a handler")
        }.addIf(entity.handlers.nonEmpty && entity.handlers.forall(_.clauses.isEmpty)) {
          ValidationMessage(
            entity.loc,
            s"Entity '${entity.id.format}' has only empty handlers",
            MissingWarning
          )
        }.addIf(entity.hasOption[EntityFiniteStateMachine] && entity.states.size < 2) {
          ValidationMessage(
            entity.loc,
            s"Entity '${entity.id.format}' is declared as a finite-state-machine, but does not " +
              s"have at least two states",
            Error
          )
        }
    }

    override def doHandler(
      state: ValidationState,
      container: Entity,
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
      val result = state.checkMessageRef(onClause.msg, onClause.msg.messageKind)
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
          state.checkPathRef[Field](path)()
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
      container: Context,
      entity: AST.Entity
    ): ValidationState = { state.checkDescription(entity, entity.description) }

    override def openInteraction(
      state: ValidationState,
      container: Container[Interaction],
      interaction: Interaction
    ): ValidationState = {
      state.checkDefinition(container, interaction)
        .checkNonEmpty(interaction.actions, "Actions", interaction)
    }

    override def closeInteraction(
      state: ValidationState,
      container: Container[Interaction],
      interaction: Interaction
    ): ValidationState = { state.checkDescription(interaction, interaction.description) }

    override def openFeature(
      state: ValidationState,
      container: Container[Feature],
      feature: Feature
    ): ValidationState = {
      val state2 = state.checkDefinition(container, feature)
      feature.contents.foldLeft(state2) { case (s, example) =>
        s.checkNonEmpty(example.givens, "Given", feature)
        s.checkNonEmpty(example.thens, "Then", feature)
      }
    }

    override def closeFeature(
      state: ValidationState,
      container: Container[Feature],
      feature: Feature
    ): ValidationState = { state.checkDescription(feature, feature.description) }

    override def openAdaptor(
      state: ValidationState,
      container: Context,
      adaptor: Adaptor
    ): ValidationState = { state.checkDefinition(container, adaptor) }

    override def closeAdaptor(
      state: ValidationState,
      container: Context,
      adaptor: Adaptor
    ): ValidationState = { state.checkDescription(adaptor, adaptor.description) }

    override def doType(
      state: ValidationState,
      container: Container[Definition],
      typeDef: Type
    ): ValidationState = {
      state.checkDefinition(container, typeDef).check(
        typeDef.id.value.head.isUpper,
        s"${typeDef.identify} should start with a capital letter",
        StyleWarning,
        typeDef.loc
      ).checkTypeExpression(typeDef.typ, container).checkDescription(typeDef, typeDef.description)
    }

    override def doAction(
      state: ValidationState,
      container: Interaction,
      action: ActionDefinition
    ): ValidationState = {
      state.checkDefinition(container, action).checkDescription(action, action.description)
      // FIXME: do some validation of action
    }

    override def doExample(
      state: ValidationState,
      container: Container[Example],
      example: Example
    ): ValidationState = {
      state.checkDefinition(container, example).checkNonEmpty(example.givens, "Givens", example)
        .checkNonEmpty(example.whens, "Whens", example)
        .checkNonEmpty(example.thens, "Thens", example)
        .checkDescription(example, example.description)
    }

    override def doInvariant(
      state: ValidationState,
      container: Entity,
      invariant: Invariant
    ): ValidationState = {
      state.checkDefinition(container, invariant)
        .checkNonEmpty(invariant.expression, "Expression", invariant)
        .checkDescription(invariant, invariant.description)
    }

    override def doAdaptation(
      state: ValidationState,
      container: Adaptor,
      adaptation: Adaptation
    ): ValidationState = {
      adaptation match {
        case Adaptation(_, _, event, command, _, description) => state
            .checkDefinition(container, adaptation).checkRef(event).checkRef(command)
            .checkDescription(adaptation, description)
        case _ =>
          require(requirement = false, "Unknown adaptation")
          state
      }
    }

    override def openPlant(
      state: ValidationState,
      container: Domain,
      plant: Plant
    ): ValidationState = state

    def closePlant(
      state: ValidationState,
      container: Domain,
      plant: Plant
    ): ValidationState = state

    override def openState(
      state: ValidationState,
      container: Entity,
      s: State
    ): ValidationState = state

    override def closeState(
      state: ValidationState,
      container: Entity,
      s: State
    ): ValidationState = state

    override def openSaga(
      state: ValidationState,
      container: Container[Saga],
      saga: Saga
    ): ValidationState = state

    override def closeSaga(
      state: ValidationState,
      container: Container[Saga],
      saga: Saga
    ): ValidationState = state

    override def doStateField(
      state: ValidationState,
      container: Container[Field],
      field: Field
    ): ValidationState = state

    override def doPipe(state: ValidationState, container: Plant, pipe: Pipe): ValidationState =
      state

    override def doJoint(
      state: ValidationState,
      container: Plant,
      joint: Joint
    ): ValidationState = state

    override def doSagaAction(
      state: ValidationState,
      saga: Saga,
      action: SagaAction
    ): ValidationState = state

    override def openProcessor(
      state: ValidationState,
      container: Plant,
      processor: Processor
    ): ValidationState = state

    override def closeProcessor(
      state: ValidationState,
      container: Plant,
      processor: Processor
    ): ValidationState = state

    override def openFunction(
      state: ValidationState,
      container: Entity,
      function: Function
    ): ValidationState = {
      state.checkDefinition(container, function)
        .checkTypeExpression(function.input.getOrElse(Nothing(function.loc)), function)
        .checkTypeExpression(function.output.getOrElse(Nothing(function.loc)), function)
    }

    override def closeFunction(
      state: ValidationState,
      container: Entity,
      function: Function
    ): ValidationState = state.checkDescription(function, function.description)

    override def doInlet(
      state: ValidationState,
      container: Processor,
      inlet: Inlet
    ): ValidationState = state

    override def doOutlet(
      state: ValidationState,
      container: Processor,
      outlet: Outlet
    ): ValidationState = state
  }
}
