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
    val s1 = folding.foldLeft(root, root, state)
    val result = checkOverloads(symTab, s1)
    result.msgs.sortBy(_.loc)
  }

  def checkOverloads(symbolTable: SymbolTable, state: ValidationState): ValidationState = {
    symbolTable.foreachOverloadedSymbol { defs: Seq[Seq[Definition]] =>
      defs.foldLeft(state) { (s, defs) =>
        if (defs.sizeIs == 2) {
          val first = defs.head
          val last = defs.last
          state
            .addStyle(last.loc, s"${last.identify} overloads ${first.identify} at ${first.id.loc}")
        } else {
          val first = defs.head
          val tail = defs.tail.map(d => d.identify + " at " + d.loc).mkString(s",\n    ")
          s.addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
        }
      }
    }
  }

  sealed trait ValidationMessageKind {
    def isSevereError: Boolean = true

    def isError: Boolean = false

    def isWarning: Boolean = false

    def isMissing: Boolean = false

    def isStyle: Boolean = false
  }

  final case object MissingWarning extends ValidationMessageKind {
    override def isWarning: Boolean = true

    override def isMissing: Boolean = true

    override def toString: String = "Missing"
  }

  final case object StyleWarning extends ValidationMessageKind {
    override def isWarning: Boolean = true

    override def isStyle: Boolean = true

    override def toString: String = "Style"
  }

  final case object Warning extends ValidationMessageKind {
    override def isWarning: Boolean = true

    override def toString: String = "Warning"
  }

  final case object Error extends ValidationMessageKind {
    override def isError: Boolean = true

    override def toString: String = "Error"
  }

  final case object SevereError extends ValidationMessageKind {
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

    def lookup[T <: Definition : ClassTag](
      id: Seq[String]
    ): List[T] = {symbolTable.lookup[T](id)}

    def addIf(predicate: Boolean)(msg: ValidationMessage): ValidationState = {
      if (predicate) add(msg) else this
    }

    def addStyle(loc: Location, msg: String): ValidationState = {
      add(ValidationMessage(loc, msg, StyleWarning))
    }

    def addWarning(loc: Location, msg: String): ValidationState = {
      add(ValidationMessage(loc, msg, Warning))
    }

    def addError(loc: Location, msg: String): ValidationState = {
      add(ValidationMessage(loc, msg, Error))
    }

    def add(
      msg: ValidationMessage
    ): ValidationState = {
      msg.kind match {
        case StyleWarning =>
          if (isReportStyleWarnings) {
            this.copy(msgs = msgs :+ msg)
          } else {
            this
          }
        case MissingWarning =>
          if (isReportMissingWarnings) {
            this.copy(msgs = msgs :+ msg)
          }
          else {
            this
          }
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
          s"${AST.kind(d)} identifier '${d.id.value}' is too short. The minimum length is $min",
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
      @unused
      definition: Definition,
      enumerators: Seq[Enumerator]
    ): ValidationState = {
      enumerators.foldLeft(this) { case (state, enumerator) =>
        val id = enumerator.id
        state.checkIdentifierLength(enumerator).check(
          id.value.head.isUpper,
          s"Enumerator '${id.value}' must start with upper case",
          StyleWarning,
          id.loc
        ).checkDescription(enumerator)
      }
    }

    def checkAlternation[TD <: Definition](
      definition: TD,
      alternation: AST.Alternation
    ): ValidationState = {
      alternation.of.foldLeft(this) { case (state, typex) =>
        state.checkTypeExpression(typex, definition)
      }
    }

    def checkRangeType(
      @unused
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
      )
    }

    def checkAggregation(
      @unused
      definition: Definition,
      agg: Aggregation
    ): ValidationState = {
      agg.fields.foldLeft(this) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          "Field names in aggregates should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, field).checkDescription(field)
      }
    }

    def checkMessageType(
      @unused
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
        ).checkTypeExpression(field.typeEx, field).checkDescription(field)
      }
    }

    def checkMapping[TD <: Definition](
      definition: TD,
      mapping: AST.Mapping
    ): ValidationState = {
      this.checkTypeExpression(mapping.from, definition).checkTypeExpression(mapping.to, definition)
    }

    def checkTypeExpression[TD <: Definition](
      typ: TypeExpression,
      definition: TD
    ): ValidationState = {
      typ match {
        case p@Pattern(_, _) => checkPattern(p)
        case UniqueId(_, entityName) => this.checkPathRef[Entity](entityName)()
        case _: AST.PredefinedType => this
        case AST.TypeRef(_, id: PathIdentifier) => checkPathRef[Type](id)()
        case Optional(_, typex: TypeExpression) => checkTypeExpression(typex, definition)
        case OneOrMore(_, typex: TypeExpression) => checkTypeExpression(typex, definition)
        case ZeroOrMore(_, typex: TypeExpression) => checkTypeExpression(typex, definition)
        case Enumeration(_, enumerators) => checkEnumeration(definition, enumerators)
        case alt: Alternation => checkAlternation(definition, alt)
        case agg: Aggregation => checkAggregation(definition, agg)
        case mt: MessageType => checkMessageType(definition, mt)
        case mapping: Mapping => checkMapping(definition, mapping)
        case rt: RangeType => checkRangeType(definition, rt)
        case ReferenceType(_, entity: EntityRef) => this.checkRef[Entity](entity)
      }
    }

    def checkSymbolLookup[DT <: Definition : ClassTag](symbol: Seq[String])
                                                      (checkEmpty: () => ValidationState)
                                                      (checkSingle: DT => ValidationState)
                                                      (checkMulti: (DT, SymbolTable#LookupResult[DT]) => ValidationState
                                                      ): ValidationState = {
      symbolTable.lookupSymbol[DT](symbol) match {
        case Nil => checkEmpty()
        case (d, _) :: Nil => checkSingle(d.asInstanceOf[DT]) // FIXME:
        case (d, _) :: tail => checkMulti(d.asInstanceOf[DT], tail) // FIXME:
      }
    }

    def checkMessageRef(ref: MessageRef, kind: MessageKind): ValidationState = {
      symbolTable.lookupSymbol[Type](ref.id.value) match {
        case Nil => add(ValidationMessage(
          ref.id.loc,
          s"'${ref.id.format}' is not defined but should be a ${kind.kind} type",
          Error
        ))
        case (d, _) :: Nil => d match {
          case Type(_, _, typ, _) => typ match {
              case MessageType(_, mk, _) => check(
                mk == kind,
                s"'${ref.id.format}' was expected to be a ${kind.kind} type" +
                  s" but is a ${mk.kind} type instead",
                Error,
                ref.id.loc
              )
              case te: TypeExpression => add(ValidationMessage(
                ref.id.loc,
                s"'${ref.id.format}' should reference a ${kind.kind} type but is a ${AST.kind(te)
                } type instead",
                Error
              ))
            }
            case _ => add(ValidationMessage(
              ref.id.loc,
              s"'${ref.id.format}' was expected to be a ${kind.kind} type but is a ${AST.kind(d)}" +
                s"  instead",
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
      val definitions = d :: tail.map { case (definition, _) => definition }
      val types = (optT :: tail.map { case (_, t) => t }) collect { case Some(tpe) => tpe }
      val exactlyOneMatch = types.count(_.getClass == tc) == 1
      val allDifferent = definitions.map(AST.kind).distinct.sizeIs == definitions.size
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

    def checkNonEmptyValue(
                            value: RiddlValue,
                            name: String,
                            thing: Definition,
                            kind: ValidationMessageKind = Error,
                            required: Boolean = false
                          ): ValidationState = {
      check(
        value.nonEmpty,
        message = s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
        kind,
        thing.loc
      )
    }

    def checkNonEmpty(
                       list: Seq[?],
                       name: String,
                       thing: Definition,
                       kind: ValidationMessageKind = Error,
                       required: Boolean = false
                     ): ValidationState = {
      check(
        list.nonEmpty,
        message = s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
        kind,
        thing.loc
      )
    }

    def checkOptions[T](options: Seq[T], loc: Location): ValidationState = {
      check(options.sizeIs == options.distinct.size, "Options should not be repeated", Error, loc)
    }

    def checkDefinition[TCD <: Container[Definition], TD <: Definition](
      container: TCD,
      definition: TD
    ): ValidationState = {
      var result = this.check(
        definition.id.nonEmpty,
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
      } else if (matches.sizeIs >= 2) {
        matches.groupBy(result.symbolTable.parentOf(_)).get(Option(container)) match {
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

    def checkDescription[TD <: DescribedValue](id: String, value: TD): ValidationState = {
      val description: Option[Description] = value.description
      if (description.isEmpty && value.nonEmpty) {
        this.check(
          predicate = false,
          s"$id should have a description",
          MissingWarning,
          value.loc
        )
      } else if (description.nonEmpty) {
        val desc = description.get
        this.check(
          desc.nonEmpty,
          s"For $id, description is declared but empty",
          MissingWarning,
          desc.loc
        )
      } else this
    }

    def checkDescription[TD <: Definition](
                                            definition: TD
                                          ): ValidationState = {
      checkDescription(definition.identify, definition)
    }

    def checkContainer[P <: Container[Definition], T <: Container[Definition]](
                                                                                parent: P,
                                                                                container: T
    ): ValidationState = {
      this.checkDefinition(parent, container).check(
        container.nonEmpty,
        s"${container.identify} in ${parent.identify} must have content.",
        MissingWarning,
        container.loc
      )
    }

    def checkExample(example: Example): ValidationState = {
      if (example.nonEmpty) {
        this.checkNonEmpty(example.givens, "Givens", example)
          .checkNonEmpty(example.whens, "Whens", example)
          .checkNonEmpty(example.thens, "Thens", example, required = true).checkDescription(example)
      }
      this
    }

    def checkExamples(examples: Seq[Example]): ValidationState = {
      examples.foldLeft(this) { (next, example) => next.checkExample(example) }
    }

    def checkFunctionCall(loc: Location, pathId: PathIdentifier, args: ArgList): ValidationState = {
      checkPathRef[Function](pathId) { (state, foundClass, id, defClass, defn, optN) => {
        val s = defaultSingleMatchValidationFunction(state, foundClass, id, defClass, defn, optN)
        defn match {
          case Function(_, fid, Some(Aggregation(_, fields)), _, _, _) =>
            val paramNames = fields.map(_.id.value)
            val argNames = args.args.keys.map(_.value).toSeq
            val s1 = s.check(argNames.size == paramNames.size,
              s"Wrong number of arguments for ${
                fid.format
              }. Expected ${paramNames.size}, got ${argNames.size}", Error, loc)
            val missing = paramNames.filterNot(argNames.contains(_))
            val unexpected = argNames.filterNot(paramNames.contains(_))
            val s2 = s1.check(missing.isEmpty,
              s"Missing arguments: ${missing.mkString(", ")}", Error, loc)
            s2.check(unexpected.isEmpty,
              s"Arguments do not correspond to parameters; ${unexpected.mkString(",")}",
              Error, loc)
          case _ =>
            s
        }
      }
      }
    }

    def checkCondition(@unused condition: Condition): ValidationState = {
      condition match {
        case FunctionCallCondition(loc, pathId, args) =>
          checkFunctionCall(loc, pathId, args)
        case ReferenceCondition(_, ref) =>
          checkPathRef[Field](ref)()
        case _ =>
          this
      }
    }

    def checkExpression(@unused expression: Expression): ValidationState = {
      expression match {
        case FieldExpression(_, path) =>
          checkPathRef[Field](path)()
        case GroupExpression(_, expr) =>
          checkExpression(expr)
        case FunctionCallExpression(loc, pathId, arguments) =>
          checkFunctionCall(loc, pathId, arguments)
        case Plus(_, op1, op2) =>
          checkExpression(op1).checkExpression(op2)
        case Minus(_, op1, op2) =>
          checkExpression(op1).checkExpression(op2)
        case Multiply(_, op1, op2) =>
          checkExpression(op1).checkExpression(op2)
        case Divide(_, op1, op2) =>
          checkExpression(op1).checkExpression(op2)
        case Modulus(_, op1, op2) =>
          checkExpression(op1).checkExpression(op2)
        case AbstractBinary(loc, op, op1, op2) =>
          check(op.nonEmpty, "Operator is empty in abstract binary operator", Error, loc)
            .checkExpression(op1).checkExpression(op2)
        case _ =>
          this
      }
    }

    def getFieldType(path: PathIdentifier): Option[TypeExpression] = {
      val results = symbolTable.lookup[Definition](path.value)
      if (results.size == 1) {
        results.head match {
          case f: Field =>
            Option(f.typeEx)
          case t: Type =>
            Option(t.typ)
          case _ =>
            None
        }
      } else {
        None
      }
    }

    def checkMessageConstructor(messageConstructor: MessageConstructor): ValidationState = {
      val id = messageConstructor.msg.id
      checkSymbolLookup(id.value) { () =>
        add(ValidationMessage(
          id.loc,
          s"'${id.format}' is not defined but should be a message type",
          Error
        ))
      } { d: Definition =>
        d match {
          case Type(_, _, typ, _) => typ match {
            case mt: MessageType =>
              val names = messageConstructor.args.args.keys.map(_.value).toSeq
              val unset = mt.fields.filterNot { fName => names.contains(fName.id.value) }
              if (unset.nonEmpty) {
                unset.foldLeft(this) { (next, field) =>
                  next.add(ValidationMessage(field.loc,
                    s"Field '${field.id.format}' was not set in message constructor'"))
                }
              } else {
                mt.fields.foldLeft(this) { (next, _: Field) =>
                  // val fromType = messageConstructor.args.args
                  next
                }
                // mt.fields.zip(names)
              }
            case te: TypeExpression =>
              add(ValidationMessage(
                id.loc,
                s"'${id.format}' should reference a message type but is a ${AST.kind(te)} type instead",
                Error
              ))
          }
          case _ =>
            add(ValidationMessage(
              id.loc,
              s"'${id.format}' was expected to be a message type but is a ${AST.kind(d)} instead",
              Error
            ))
        }
      } { (d: Definition, tail) =>
        add(ValidationMessage(id.loc, s"ambiguous assignment from ${d.identify} which as ${
          tail
            .size
        } other definitions"))
      }
    }
  }

  class ValidationFolding extends Folding.Folding[ValidationState] {

    override def openRootDomain(
      state: ValidationState,
      container: RootContainer,
      domain: Domain
    ): ValidationState = {state.checkContainer(container, domain)}

    override def closeRootDomain(
      state: ValidationState,
      container: RootContainer,
      domain: Domain
    ): ValidationState = {state.checkDescription(domain)}

    override def openDomain(
      state: ValidationState,
      container: Domain,
      domain: Domain
    ): ValidationState = {state.checkContainer(container, domain)}

    override def closeDomain(
      state: ValidationState,
      container: Domain,
      domain: Domain
    ): ValidationState = {state.checkDescription(domain)}

    override def openContext(
      state: Validation.ValidationState,
      container: Domain,
      context: AST.Context
    ): ValidationState = {
      state.checkContainer(container, context)
        .checkOptions[ContextOption](context.options, context.loc)
    }

    override def closeContext(
      state: Validation.ValidationState,
      container: Domain,
      context: AST.Context
    ): ValidationState = {state.checkDescription(context)}

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
      }.addIf(entity.hasOption[EntityFiniteStateMachine] && entity.states.sizeIs < 2) {
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
        checkOnClauseAction(state, onClause, action)
      }
    }

    def checkOnClauseAction(
                             state: ValidationState,
                             @unused parent: OnClause,
                             action: Action
                           ): ValidationState = {
      val newState = action match {
        case SetAction(_, path, value, _) =>
          state
            .checkPathRef[Field](path)()
            .checkExpression(value)
        case PublishAction(_, msg, pipeRef, _) =>
          state
            .checkMessageConstructor(msg)
            .checkRef[Pipe](pipeRef)
        case BecomeAction(_, entity, handler, _) =>
          state
            .checkRef[Entity](entity)
            .checkRef[Handler](handler)
        case MorphAction(_, entity, entityState, _) =>
          state
            .checkRef[Entity](entity)
            .checkRef[State](entityState)
        case TellAction(_, entity, msg, _) =>
          state
            .checkRef[Entity](entity)
            .checkMessageConstructor(msg)
        case AskAction(_, entity, msg, _) =>
          state
            .checkRef[Entity](entity)
            .checkMessageConstructor(msg)
        case ArbitraryAction(loc, what, _) =>
          state.check(what.nonEmpty,
            "arbitrary action is empty so specifies nothing", MissingWarning, loc)
        case WhenAction(_, condition, th, el, _) =>
          el.foldLeft(th.foldLeft(state.checkCondition(condition)) { (s, action) =>
            checkOnClauseAction(s, parent, action)
          }) { (s, action) =>
            checkOnClauseAction(s, parent, action)
          }
      }
      newState.checkDescription[Action](AST.kind(action), action)
    }

    override def closeEntity(
      state: Validation.ValidationState,
      container: Context,
      entity: AST.Entity
    ): ValidationState = {state.checkDescription(entity)}

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
    ): ValidationState = {state.checkDescription(interaction)}

    override def openFeature(
      state: ValidationState,
      container: Context,
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
      container: Context,
      feature: Feature
    ): ValidationState = {state.checkDescription(feature)}

    override def openAdaptor(
      state: ValidationState,
      container: Context,
      adaptor: Adaptor
    ): ValidationState = { state.checkDefinition(container, adaptor) }

    override def closeAdaptor(
      state: ValidationState,
      container: Context,
      adaptor: Adaptor
    ): ValidationState = {state.checkDescription(adaptor)}

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
      ).checkTypeExpression(typeDef.typ, container).checkDescription(typeDef)
    }

    override def doAction(
      state: ValidationState,
      container: Interaction,
      action: ActionDefinition
    ): ValidationState = {
      state.checkDefinition(container, action).checkDescription(action)
      // FIXME: do some validation of action
    }

    override def doFeatureExample(
      state: ValidationState,
      feature: Feature,
      example: Example
    ): ValidationState = {state.checkDefinition(feature, example).checkExample(example)}

    override def doFunctionExample(
      state: ValidationState,
      function: Function,
      example: Example
    ): ValidationState = {state.checkDefinition(function, example).checkExample(example)}

    override def doProcessorExample(
      state: ValidationState,
      processor: Processor,
      example: Example
    ): ValidationState = {state.checkDefinition(processor, example).checkExample(example)}

    override def doInvariant(
      state: ValidationState,
      container: Entity,
      invariant: Invariant
    ): ValidationState = {
      state.checkDefinition(container, invariant)
        .checkNonEmptyValue(invariant.expression, "Condition", invariant, MissingWarning)
        .checkCondition(invariant.expression)
        .checkDescription(invariant)
    }

    override def doAdaptation(
      state: ValidationState,
      container: Adaptor,
      adaptation: Adaptation
    ): ValidationState = {
      adaptation match {
        case Adaptation(_, _, event, command, examples, _) =>
          state.checkDefinition(container, adaptation)
            .checkRef[Type](event).checkRef[Type](command).checkExamples(examples)
            .checkDescription(adaptation)
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
      s: AST.State
    ): ValidationState = state.checkContainer(container, s)

    override def closeState(
      state: ValidationState,
      container: Entity,
      s: AST.State
    ): ValidationState = {state.checkDescription(s)}

    override def openSaga(
      state: ValidationState,
      container: Context,
      saga: Saga
    ): ValidationState = state.checkDefinition(container, saga)

    override def closeSaga(
      state: ValidationState,
      container: Context,
      saga: Saga
    ): ValidationState = state.checkDescription(container)

    override def doStateField(
      state: ValidationState,
      container: State,
      field: Field
    ): ValidationState = state.checkDescription(field)

    override def doPipe(
      state: ValidationState,
      container: Plant,
      pipe: Pipe
    ): ValidationState = {state.checkDefinition(container, pipe).checkDescription(pipe)}

    override def doJoint(
      state: ValidationState,
      container: Plant,
      joint: Joint
    ): ValidationState = state.checkDefinition(container, joint).checkDescription(joint)

    override def doSagaAction(
      state: ValidationState,
      saga: Saga,
      action: SagaAction
    ): ValidationState = state.checkDefinition(saga, action).checkDescription(action)

    override def openProcessor(
      state: ValidationState,
      container: Plant,
      processor: Processor
    ): ValidationState = state.checkDefinition(container, processor)

    override def closeProcessor(
      state: ValidationState,
      container: Plant,
      processor: Processor
    ): ValidationState = state.checkDescription(processor)

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
    ): ValidationState = state.checkDescription(function)

    override def doInlet(
      state: ValidationState,
      container: Processor,
      inlet: Inlet
    ): ValidationState = state.checkDefinition(container, inlet).checkDescription(inlet)

    override def doOutlet(
      state: ValidationState,
      container: Processor,
      outlet: Outlet
    ): ValidationState = state.checkDefinition(container, outlet).checkDescription(outlet)
  }
}
