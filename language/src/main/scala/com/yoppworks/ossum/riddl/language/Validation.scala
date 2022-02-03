package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

import java.util.regex.PatternSyntaxException
import scala.annotation.unused
import scala.collection.mutable.ListBuffer
import scala.reflect.{ClassTag, classTag}
import scala.util.matching.Regex

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
    result.messages.sortBy(_.loc)
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
    options: ValidationOptions = ValidationOptions.Default)
      extends Folding.State[ValidationState] {

    private val msgs: ListBuffer[ValidationMessage] = ListBuffer.empty[ValidationMessage]

    def messages: ValidationMessages = msgs.toList

    def step(f: ValidationState => ValidationState): ValidationState = f(this)

    def parentOf(
      definition: Definition
    ): Container[Definition] = {symbolTable.parentOf(definition).getOrElse(RootContainer.empty)}

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
            msgs += msg
            this
          } else {
            this
          }
        case MissingWarning =>
          if (isReportMissingWarnings) {
            msgs += msg
            this
          }
          else {
            this
          }
        case _ =>
          msgs += msg
          this
      }
    }

    private val vowels: Regex = "[aAeEiIoOuU]".r
    def article(thing: String): String = {
      val article = if (vowels.matches(thing.substring(0,1))) "an" else "a"
      s"$article $thing"
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
          s"Enumerator '${id.format}' must start with upper case",
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
      val s1: ValidationState = mt.fields.headOption match {
        case Some(sender) =>
          this.check(sender.id.value == "sender",
            "The first field of a message type must be the implicit 'sender'",
            SevereError, mt.loc)
            .step { state =>
              sender.typeEx match {
                case ReferenceType(loc, entityRef) =>
                  state.check(entityRef.id.isEmpty,
                    "The implicit 'sender' must not have a path in its entity reference",
                    SevereError, loc)
                case other: TypeExpression =>
                  state.addError(mt.loc, s"The implicit 'sender must be Reference type, not $other")
              }
            }
        case None =>
          this
      }
      mt.fields.tail.foldLeft(s1) { case (state, field) =>
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
      (
        checkMulti: (DT, SymbolTable#LookupResult[DT]) => ValidationState
      ): ValidationState = {
      symbolTable.lookupSymbol[DT](symbol) match {
        case Nil => checkEmpty()
        case (d, _) :: Nil => checkSingle(d.asInstanceOf[DT]) // FIXME:
        case (d, _) :: tail => checkMulti(d.asInstanceOf[DT], tail) // FIXME:
      }
    }

    def checkMessageRef(ref: MessageRef, kind: MessageKind): ValidationState = {
      symbolTable.lookupSymbol[Type](ref.id.value) match {
        case Nil =>
          add(ValidationMessage(
            ref.id.loc,
            s"'${ref.id.format}' is not defined but should be ${article(kind.kind)} type",
            Error
          ))
        case (d, _) :: Nil => d match {
          case Type(_, _, typ, _) => typ match {
            case MessageType(_, mk, _) => check(
              mk == kind,
              s"'${ref.id.format}' should be ${article(kind.kind)} type" +
                s" but is ${article(mk.kind)} type instead",
              Error,
              ref.id.loc
            )
            case te: TypeExpression => add(ValidationMessage(
              ref.id.loc,
              s"'${ref.id.format}' should reference ${article(kind.kind)} type but is a ${
                AST.kind(te)
                } type instead",
                Error
              ))
            }
            case _ => add(ValidationMessage(
              ref.id.loc,
              s"'${ref.id.format}' was expected to be ${article(kind.kind)} type but is ${
                article(AST.kind(d))} instead",
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
          s"'${id.format}' was expected to be ${article(foundClass.getSimpleName)} but is " +
            s"${article(defClass.getSimpleName)} instead",
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
      else if (!d.isImplicit) {
        add(ValidationMessage(
          id.loc,
          s"""'${id.format}' is not uniquely defined.
             |Definitions are:
             |${formatDefinitions(definitions)}""".stripMargin,
          Error
        ))
      } else {
        this
      }
    }

    def checkPathRef[T <: Definition: ClassTag](
      id: PathIdentifier
    )(validator: SingleMatchValidationFunction = defaultSingleMatchValidationFunction
    ): ValidationState = {
      if (id.value.nonEmpty) {
        val tc = classTag[T].runtimeClass
        symbolTable.lookupSymbol[T](id.value) match {
          case Nil =>
            add(ValidationMessage(
              id.loc,
              s"'${id.format}' is not defined but should be ${article(tc.getSimpleName)}",
              Error
            ))
          // Single match, defer to validation function
          case (d, optT) :: Nil => validator(this, tc, id, d.getClass, d, optT)
          // Too many matches / non-unique
          case (d, optT) :: tail => handleMultipleResultsCase[T](id, d, optT, tail)
        }
      } else { this }
    }

    def checkRef[T <: Definition : ClassTag](reference: Reference[T]): ValidationState = {
      checkPathRef[T](reference.id)()
    }

    private def formatDefinitions[T <: Definition](list: List[T]): String = {
      list.map { dfntn => "  " + dfntn.id.value + " (" + dfntn.loc + ")" }.mkString("\n")
    }

    def checkSequence[A](
      elements: Seq[A]
    )(
      fold: (ValidationState, A) => ValidationState
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

    def checkAction(action: Action): ValidationState = {
      action match {
        case SetAction(_, path, value, _) =>
          this
            .checkPathRef[Field](path)()
            .checkExpression(value)
        case PublishAction(_, msg, pipeRef, _) =>
          this
            .checkMessageConstructor(msg)
            .checkRef[Pipe](pipeRef)
        case BecomeAction(_, entity, handler, _) =>
          this
            .checkRef[Entity](entity)
            .checkRef[Handler](handler)
        case MorphAction(_, entity, entityState, _) =>
          this
            .checkRef[Entity](entity)
            .checkRef[State](entityState)
        case TellAction(_, msg, entity, _) =>
          this
            .checkRef[Entity](entity)
            .checkMessageConstructor(msg)
        case AskAction(_, entity, msg, _) =>
          this
            .checkRef[Entity](entity)
            .checkMessageConstructor(msg)
        case CompoundAction(loc, actions, _) =>
          val vs = this
            .check(actions.nonEmpty, "Compound action is empty", MissingWarning, loc)
          actions.foldLeft(vs) { (s, action) => s.checkAction(action) }

        case ArbitraryAction(loc, what, _) =>
          this.check(what.nonEmpty,
            "arbitrary action is empty so specifies nothing", MissingWarning, loc)
      }
    }

    def checkExample(example: Example): ValidationState = {
      if (example.nonEmpty) {
        example.givens.foldLeft(this) { (state, givenClause) =>
          val s = state.checkNonEmpty(givenClause.scenario, "Givens", example, MissingWarning)
          givenClause.scenario.foldLeft(s) { (state, ls) =>
            state.checkNonEmptyValue(ls, "Given String", example, MissingWarning)
          }
        }.step { state: ValidationState =>
          example.whens.foldLeft(state) { (state, whenClause) =>
            state.checkExpression(whenClause.condition)
          }
        }
          .checkNonEmpty(example.thens, "Thens", example, required = true)
          .step { state: ValidationState =>
            example.thens.foldLeft(state) { (state, thenClause) =>
              state.checkAction(thenClause.action)
            }
          }.step { state: ValidationState =>
          example.buts.foldLeft(state) { (state, butClause) =>
            state.checkAction(butClause.action)
          }
        }
          .checkDescription(example)
      } else this
    }

    def checkExamples(examples: Seq[Example]): ValidationState = {
      examples.foldLeft(this) { (next, example) => next.checkExample(example) }
    }

    def checkFunctionCall(loc: Location, pathId: PathIdentifier, args: ArgList): ValidationState = {
      checkPathRef[Function](pathId) { (state, foundClass, id, defClass, defn, optN) =>
        val s = defaultSingleMatchValidationFunction(state, foundClass, id, defClass, defn, optN)
        defn match {
          case Function(_, fid, Some(Aggregation(_, fields)), _, _, _, _) =>
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

    def checkExpression(expression: Expression): ValidationState = {
      expression match {
        case ValueCondition(_, path) =>
          checkPathRef[Field](path)()
        case GroupExpression(_, expr) =>
          checkExpression(expr)
        case FunctionCallExpression(loc, pathId, arguments) =>
          checkFunctionCall(loc, pathId, arguments)
        case ArithmeticOperator(loc, op, operands) =>
          val s1 = check(op.nonEmpty, "Operator is empty in abstract binary operator", Error, loc)
          operands.foldLeft(s1) { (st, operand) => st.checkExpression(operand) }
        case Comparison(_, _, arg1, arg2) =>
          checkExpression(arg1).checkExpression(arg2)
        case _: Expression =>
          this // everything else doesn't need validation
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
                unset.filterNot(_.isImplicit).foldLeft(this) { (next, field) =>
                  next.add(ValidationMessage(messageConstructor.msg.loc,
                    s"Field '${field.id.format}' was not set in message constructor"))
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
                s"'${id.format}' should reference a message type but is a ${AST.kind(te)} type " +
                  s"instead",
                Error
              ))
          }
          case _ =>
            add(ValidationMessage(
              id.loc,
              s"'${id.format}' was expected to be a message type but is ${
                article(AST.kind(d))} instead",
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

    def closeContext(
      state: Validation.ValidationState,
      container: Domain,
      context: AST.Context
    ): ValidationState = {state.checkDescription(context)}

    def openStory(state: ValidationState, container: Domain, story: Story):
    ValidationState = {
      state.checkContainer(container, story)
        .checkNonEmptyValue(story.role, "role", story, MissingWarning)
        .checkNonEmptyValue(story.capability, "capability", story, MissingWarning)
        .checkNonEmptyValue(story.benefit, "benefit", story, MissingWarning)
        .checkExamples(story.examples)
    }

    override def closeStory(state: ValidationState, container: Domain, story: Story)
    : ValidationState = {
      state.checkDescription(story)
    }

    def openEntity(
      state: Validation.ValidationState,
      container: Context,
      entity: AST.Entity
    ): ValidationState = {
      state.checkContainer(container, entity)
        .checkOptions[EntityOption](entity.options, entity.loc)
        .checkSequence(entity.states) { (next, state) =>
          next.checkTypeExpression(state.typeEx, state)
        }.addIf(entity.handlers.isEmpty && !entity.isEmpty) {
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
      onClause.examples.foldLeft(result) { (state, example) =>
        state.checkExample(example)
      }
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

    override def doStoryExample(state: ValidationState, container: Story, example: Example)
    : ValidationState = {
      state.checkDefinition(container, example).checkExample(example)
    }

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
        .checkExpression(invariant.expression)
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

    def openFunction[TCD <: Container[Definition]](
      state: ValidationState,
      container: TCD,
      function: Function
    ): ValidationState = {
      state.checkDefinition(container, function)
        .checkTypeExpression(function.input.getOrElse(Nothing(function.loc)), function)
        .checkTypeExpression(function.output.getOrElse(Nothing(function.loc)), function)
        .checkExamples(function.examples)
    }

    def closeFunction[TCD <: Container[Definition]](
      state: ValidationState,
      container: TCD,
      function: Function
    ): ValidationState = {
      state.checkDescription(function)
    }

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
