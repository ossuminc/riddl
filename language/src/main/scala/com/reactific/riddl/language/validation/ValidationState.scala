package com.reactific.riddl.language.validation
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.*
import com.reactific.riddl.language.ast.At

import java.util.regex.PatternSyntaxException
import scala.annotation.tailrec
import scala.annotation.unused
import scala.collection.mutable
import scala.math.abs
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.matching.Regex

case class ValidationState(
  symbolTable: SymbolTable,
  root: Definition = RootContainer.empty,
  commonOptions: CommonOptions = CommonOptions())
    extends Folding.PathResolutionState[ValidationState] {

  val uses: mutable.HashMap[Definition, Seq[Definition]] = mutable.HashMap
    .empty[Definition, Seq[Definition]]
  val usedBy: mutable.HashMap[Definition, Seq[Definition]] = mutable.HashMap
    .empty[Definition, Seq[Definition]]

  private var entities: Seq[Entity] = Seq.empty[Entity]
  def addEntity(entity: Entity): ValidationState = {
    entities = entities :+ entity
    this
  }
  private var types: Seq[Type] = Seq.empty[Type]
  def addType(ty: Type): ValidationState = {
    types = types :+ ty
    this
  }

  private var functions: Seq[Function] = Seq.empty[Function]
  def addFunction(fun: Function): ValidationState = {
    functions = functions :+ fun
    this
  }

  private var inlets: Seq[Inlet] = Seq.empty[Inlet]
  def getInlets: Seq[Inlet] = inlets
  def addInlet(in: Inlet): ValidationState = {
    inlets = inlets :+ in
    this
  }

  private var outlets: Seq[Outlet] = Seq.empty[Outlet]
  def getOutlets: Seq[Outlet] = outlets
  def addOutlet(out: Outlet): ValidationState = {
    outlets = outlets :+ out
    this
  }

  private var pipes: Seq[Pipe] = Seq.empty[Pipe]
  def getPipes: Seq[Pipe] = pipes
  def addPipe(out: Pipe): ValidationState = {
    pipes = pipes :+ out
    this
  }

  private var processors: Seq[Processor] = Seq.empty[Processor]
  def getProcessors: Seq[Processor] = processors
  def addProcessor(proc: Processor): ValidationState = {
    processors = processors :+ proc
    this
  }

  def associateUsage(user: Definition, use: Definition): Unit = {
    val used = uses.getOrElse(user, Seq.empty[Definition])
    val new_used = used :+ use
    uses.update(user, new_used)

    val usages = usedBy.getOrElse(use, Seq.empty[Definition])
    val new_usages = usages :+ user
    usedBy.update(use, new_usages)
  }

  def checkUnused(): ValidationState = {
    if (commonOptions.showUnusedWarnings) {
      def hasUsages(definition: Definition): Boolean = {
        val result = usedBy.get(definition) match {
          case None        => false
          case Some(users) => users.nonEmpty
        }
        result
      }
      for { e <- entities } {
        check(hasUsages(e), s"${e.identify} is unused", Warning, e.loc)
      }
      for { t <- types } {
        check(hasUsages(t), s"${t.identify} is unused", Warning, t.loc)
      }
      for { f <- functions } {
        check(hasUsages(f), s"${f.identify} is unused", Warning, f.loc)
      }
    }
    this
  }

  def checkOverloads(
    symbolTable: SymbolTable
  ): ValidationState = {
    symbolTable.foreachOverloadedSymbol { defs: Seq[Seq[Definition]] =>
      this.checkSequence(defs) { (s, defs2) =>
        if (defs2.sizeIs == 2) {
          val first = defs2.head
          val last = defs2.last
          s.addStyle(
            last.loc,
            s"${last.identify} overloads ${first.identifyWithLoc}"
          )
        } else if (defs2.sizeIs > 2) {
          val first = defs2.head
          val tail = defs2.tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
          s.addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
        } else { s }
      }
    }
  }

  def parentOf(
    definition: Definition
  ): Container[Definition] = {
    symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
  }

  def lookup[T <: Definition: ClassTag](
    id: Seq[String]
  ): List[T] = { symbolTable.lookup[T](id) }

  def addIf(predicate: Boolean)(msg: => Message): ValidationState = {
    if (predicate) add(msg) else this
  }

  val vowels: Regex = "[aAeEiIoOuU]".r

  def article(thing: String): String = {
    val article = if (vowels.matches(thing.substring(0, 1))) "an" else "a"
    s"$article $thing"
  }

  def check(
    predicate: Boolean = true,
    message: => String,
    kind: KindOfMessage,
    loc: At
  ): ValidationState = {
    if (!predicate) { add(Message(loc, message, kind)) }
    else { this }
  }

  def checkThat(
    predicate: Boolean
  )(f: ValidationState => ValidationState
  ): ValidationState = {
    if (predicate) { f(this) }
    else { this }
  }

  private def checkIdentifierLength[T <: Definition](
    d: T,
    min: Int = 3
  ): ValidationState = {
    if (d.id.value.nonEmpty && d.id.value.length < min) {
      addStyle(
        d.id.loc,
        s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min"
      )
    } else { this }
  }

  private def checkPattern(p: Pattern): ValidationState = {
    try {
      val compound = p.pattern.map(_.s).reduce(_ + _)
      java.util.regex.Pattern.compile(compound)
      this
    } catch {
      case x: PatternSyntaxException => add(Message(p.loc, x.getMessage))
    }
  }

  private def checkEnumeration(
    enumerators: Seq[Enumerator]
  ): ValidationState = {
    checkSequence(enumerators) { case (state, enumerator) =>
      val id = enumerator.id
      state.checkIdentifierLength(enumerator).check(
        id.value.head.isUpper,
        s"Enumerator '${id.format}' must start with upper case",
        StyleWarning,
        id.loc
      ).checkDescription(enumerator)
    }
  }

  private def checkAlternation(
    alternation: AST.Alternation,
    typeDef: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    checkSequence(alternation.of) { case (state, typex) =>
      state.checkTypeExpression(typex, typeDef, parents)
    }
  }

  private def checkRangeType(
    rt: RangeType
  ): ValidationState = {
    this.check(
      rt.min >= BigInt.long2bigInt(Long.MinValue),
      "Minimum value might be too small to store in a Long",
      Warning,
      rt.loc
    ).check(
      rt.max <= BigInt.long2bigInt(Long.MaxValue),
      "Maximum value might be too large to store in a Long",
      Warning,
      rt.loc
    )
  }

  private def checkAggregation(
    agg: Aggregation
  ): ValidationState = {
    checkSequence(agg.fields) { case (state, field) =>
      state.checkIdentifierLength(field).check(
        field.id.value.head.isLower,
        "Field names in aggregates should start with a lower case letter",
        StyleWarning,
        field.loc
      ).checkDescription(field)
    }
  }

  private def checkAggregateUseCase(
    mt: AggregateUseCaseTypeExpression,
    typeDef: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    checkSequence(mt.fields) { case (state, field) =>
      state.checkIdentifierLength(field).check(
        field.id.value.head.isLower,
        s"Field names in ${mt.usecase.kind} should start with a lower case letter",
        StyleWarning,
        field.loc
      ).checkTypeExpression(field.typeEx, typeDef, parents)
        .checkDescription(field)
    }
  }

  private def checkMapping(
    mapping: AST.Mapping,
    typeDef: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    this.checkTypeExpression(mapping.from, typeDef, parents)
      .checkTypeExpression(mapping.to, typeDef, parents)
  }

  def checkTypeExpression[TD <: Definition](
    typ: TypeExpression,
    defn: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    typ match {
      case AliasedTypeExpression(_, id: PathIdentifier) =>
        checkPathRef[Type](id, defn, parents)()()
      case mt: AggregateUseCaseTypeExpression =>
        checkAggregateUseCase(mt, defn, parents)
      case agg: Aggregation            => checkAggregation(agg)
      case alt: Alternation            => checkAlternation(alt, defn, parents)
      case mapping: Mapping            => checkMapping(mapping, defn, parents)
      case rt: RangeType               => checkRangeType(rt)
      case p: Pattern                  => checkPattern(p)
      case Enumeration(_, enumerators) => checkEnumeration(enumerators)
      case Optional(_, tye)   => checkTypeExpression(tye, defn, parents)
      case OneOrMore(_, tye)  => checkTypeExpression(tye, defn, parents)
      case ZeroOrMore(_, tye) => checkTypeExpression(tye, defn, parents)
      case SpecificRange(_, typex: TypeExpression, min, max) =>
        checkTypeExpression(typex, defn, parents)
        check(
          min >= 0,
          "Minimum cardinality must be non-negative",
          Error,
          typ.loc
        )
        check(
          max >= 0,
          "Maximum cardinality must be non-negative",
          Error,
          typ.loc
        )
        check(
          min < max,
          "Minimum cardinality must be less than maximum cardinality",
          Error,
          typ.loc
        )
      case UniqueId(_, pid) => checkPathRef[Entity](pid, defn, parents)()()
      case EntityReferenceTypeExpression(_, pid) =>
        checkPathRef[Entity](pid, defn, parents)()()
      case _: PredefinedType => this // nothing needed
      case _: TypeRef        => this // handled elsewhere
      case x =>
        require(requirement = false, s"Failed to match definition $x")
        this
    }
  }

  type SingleMatchValidationFunction = (
    /* state:*/ ValidationState,
    /* expectedClass:*/ Class[?],
    /* pathIdSought:*/ PathIdentifier,
    /* foundClass*/ Class[? <: Definition],
    /* definitionFound*/ Definition
  ) => ValidationState

  type MultiMatchValidationFunction = (
    /* state:*/ ValidationState,
    /* pid: */ PathIdentifier,
    /* list: */ List[(Definition, Seq[Definition])]
  ) => Seq[Definition]

  val nullSingleMatchingValidationFunction: SingleMatchValidationFunction =
    (state, _, _, _, _) => { state }

  def defaultSingleMatchValidationFunction(
    state: ValidationState,
    expectedClass: Class[?],
    pid: PathIdentifier,
    foundClass: Class[? <: Definition],
    @unused definitionFound: Definition
  ): ValidationState = {
    state.check(
      expectedClass.isAssignableFrom(foundClass),
      s"'${pid.format}' was expected to be ${article(expectedClass.getSimpleName)} but is " +
        s"${article(foundClass.getSimpleName)}.",
      Error,
      pid.loc
    )
  }

  def defaultMultiMatchValidationFunction[T <: Definition: ClassTag](
    state: ValidationState,
    pid: PathIdentifier,
    list: List[(Definition, Seq[Definition])]
  ): Seq[Definition] = {
    // Extract all the definitions that were found
    val definitions = list.map(_._1)
    val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
      definitions.size
    val expectedClass = classTag[T].runtimeClass
    if (allDifferent || definitions.head.isImplicit) {
      // pick the one that is the right type or the first one
      list.find(_._1.getClass == expectedClass) match {
        case Some((defn, parents)) => defn +: parents
        case None                  => list.head._1 +: list.head._2
      }
    } else {
      state.addError(
        pid.loc,
        s"""Path reference '${pid.format}' is ambiguous. Definitions are:
           |${formatDefinitions(list)}""".stripMargin
      )
      Seq.empty[Definition]
    }
  }

  def formatDefinitions[T <: Definition](
    list: List[(T, SymbolTable#Parents)]
  ): String = {
    list.map { case (definition, parents) =>
      "  " + parents.reverse.map(_.id.value).mkString(".") + "." +
        definition.id.value + " (" + definition.loc + ")"
    }.mkString("\n")
  }

  def notResolved[T <: Definition: ClassTag](
    pid: PathIdentifier,
    container: Definition,
    kind: Option[String]
  ): Unit = {
    val tc = classTag[T].runtimeClass
    val message = s"Path '${pid.format}' was not resolved," +
      s" in ${container.identify}"
    val referTo = if (kind.nonEmpty) kind.get else tc.getSimpleName
    addError(
      pid.loc,
      message + {
        if (referTo.nonEmpty) s", but should refer to ${article(referTo)}"
        else ""
      }
    )
  }

  def checkPathRef[T <: Definition: ClassTag](
    pid: PathIdentifier,
    container: Definition,
    parents: Seq[Definition],
    kind: Option[String] = None
  )(single: SingleMatchValidationFunction = defaultSingleMatchValidationFunction
  )(multi: MultiMatchValidationFunction = defaultMultiMatchValidationFunction
  ): ValidationState = {
    val tc = classTag[T].runtimeClass
    if (pid.value.isEmpty) {
      val message =
        s"An empty path cannot be resolved to ${article(tc.getSimpleName)}"
      addError(pid.loc, message)
    } else {
      val pars =
        if (parents.head != container) container +: parents else parents
      val result = resolvePath(pid, pars) { definitions =>
        if (definitions.nonEmpty) {
          val d = definitions.head
          associateUsage(container, d)
          single(this, tc, pid, d.getClass, d)
          definitions
        } else { definitions } // signal not resolved below
      } { list => multi(this, pid, list) }
      if (result.isEmpty) { notResolved(pid, container, kind) }
    }
    this
  }

  def checkRef[T <: Definition: ClassTag](
    reference: Reference[T],
    defn: Definition,
    parents: Seq[Definition],
    kind: Option[String] = None
  ): ValidationState = {
    checkPathRef[T](reference.pathId, defn, parents, kind)()()
  }

  def checkMessageRef(
    ref: MessageRef,
    topDef: Definition,
    parents: Seq[Definition],
    kind: AggregateUseCase
  ): ValidationState = {
    if (ref.isEmpty) { addError(ref.pathId.loc, s"${ref.identify} is empty") }
    else {
      checkPathRef[Type](ref.pathId, topDef, parents, Some(kind.kind)) {
        (state, _, _, _, defn) =>
          defn match {
            case Type(_, _, typ, _, _) => typ match {
                case AggregateUseCaseTypeExpression(_, mk, _) => state.check(
                    mk == kind,
                    s"'${ref.identify} should be ${article(kind.kind)} type" +
                      s" but is ${article(mk.kind)} type instead",
                    Error,
                    ref.pathId.loc
                  )
                case te: TypeExpression => state.addError(
                    ref.pathId.loc,
                    s"'${ref.identify} should reference ${article(kind.kind)} but is a ${AST
                        .errorDescription(te)} type instead"
                  )
              }
            case _ => state.addError(
                ref.pathId.loc,
                s"${ref.identify} was expected to be ${article(kind.kind)} type but is ${article(defn.kind)} instead"
              )
          }
      }(defaultMultiMatchValidationFunction)
    }
  }

  def checkOption[A <: RiddlValue](
    opt: Option[A],
    name: String,
    thing: Definition
  )(folder: (ValidationState, A) => ValidationState
  ): ValidationState = {
    opt match {
      case None =>
        addMissing(thing.loc, s"$name in ${thing.identify} should not be empty")
      case Some(x) =>
        val s1 = checkNonEmptyValue(x, "Condition", thing, MissingWarning)
        folder(s1, x)
    }
  }

  def checkSequence[A](
    elements: Seq[A]
  )(fold: (ValidationState, A) => ValidationState
  ): ValidationState = elements.foldLeft(this) { case (next, element) =>
    fold(next, element)
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false
  ): ValidationState = {
    check(
      value.nonEmpty,
      message =
        s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }

  def checkNonEmpty(
    list: Seq[?],
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false
  ): ValidationState = {
    check(
      list.nonEmpty,
      s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }

  def checkOptions[T <: OptionValue](
    options: Seq[T],
    loc: At
  ): ValidationState = {
    check(
      options.sizeIs == options.distinct.size,
      "Options should not be repeated",
      Error,
      loc
    )
  }

  def checkUniqueContent(definition: Definition): ValidationState = {
    val allNames = definition.contents.map(_.id.value)
    val uniqueNames = allNames.toSet
    if (allNames.size != uniqueNames.size) {
      val duplicateNames = allNames.toSet.removedAll(uniqueNames)
      addError(
        definition.loc,
        s"${definition.identify} has duplicate content names:\n${duplicateNames
            .mkString("  ", ",\n  ", "\n")}"
      )
    } else { this }
  }

  def checkDefinition(
    parents: Seq[Definition],
    definition: Definition
  ): ValidationState = {
    var result = this.check(
      definition.id.nonEmpty | definition.isImplicit,
      "Definitions may not have empty names",
      Error,
      definition.loc
    ).checkIdentifierLength(definition).checkUniqueContent(definition).check(
      !definition.isVital || definition.hasAuthors,
      "Vital definitions should have an author reference",
      MissingWarning,
      definition.loc
    ).stepIf(definition.isVital) { vs: ValidationState =>
      definition.asInstanceOf[WithAuthors].authors
        .foldLeft(vs) { case (vs, authorRef) =>
          pathIdToDefinition(authorRef.pathId, parents) match {
            case None => vs
                .addError(authorRef.loc, s"${authorRef.format} is not defined")
            case _ => vs
          }
        }
    }

    val path = symbolTable.pathOf(definition)
    if (!definition.id.isEmpty) {
      val matches = result.lookup[Definition](path)
      if (matches.isEmpty) {
        result = result.addSevere(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!"
        )
      } else if (matches.sizeIs >= 2) {
        val parentGroups = matches.groupBy(result.symbolTable.parentOf(_))
        parentGroups.get(parents.headOption) match {
          case Some(head :: tail) if tail.nonEmpty =>
            result = result.addWarning(
              head.id.loc,
              s"${definition.identify} has same name as other definitions " +
                s"in ${head.identifyWithLoc}:  " +
                tail.map(x => x.identifyWithLoc).mkString(",  ")
            )
          case Some(head :: tail) if tail.isEmpty =>
            result = result.addStyle(
              head.id.loc,
              s"${definition.identify} has same name as other definitions: " +
                matches.filterNot(_ == definition).map(x => x.identifyWithLoc)
                  .mkString(",  ")
            )
          case _ =>
          // ignore
        }
      }
    }
    result
  }

  def checkContainer(
    parents: Seq[Definition],
    container: Definition
  ): ValidationState = {
    val parent: Definition = parents.headOption.getOrElse(RootContainer.empty)
    checkDefinition(parents, container).check(
      container.nonEmpty || container.isInstanceOf[Field],
      s"${container.identify} in ${parent.identify} should have content",
      MissingWarning,
      container.loc
    )
  }

  def checkDescription[TD <: DescribedValue](
    id: String,
    value: TD
  ): ValidationState = {
    val description: Option[Description] = value.description
    val shouldCheck: Boolean = {
      value.isInstanceOf[Type] |
        (value.isInstanceOf[Definition] && value.nonEmpty)
    }
    if (description.isEmpty && shouldCheck) {
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
        s"For $id, description at ${desc.loc} is declared but empty",
        MissingWarning,
        desc.loc
      )
    } else this
  }

  def checkDescription[TD <: Definition](
    definition: TD
  ): ValidationState = { checkDescription(definition.identify, definition) }

  def checkAction(
    action: Action,
    defn: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    action match {
      case _: ErrorAction => this
      case SetAction(_, path, value, _) => this
          .checkPathRef[Field](path, defn, parents)()()
          .checkExpression(value, defn, parents)
          .checkAssignmentCompatability(path, value, parents)
      case AppendAction(_, value, path, _) => this
          .checkExpression(value, defn, parents)
          .checkPathRef[Field](path, defn, parents)()()
      case ReturnAction(_, expr, _) => this.checkExpression(expr, defn, parents)
      case YieldAction(_, msg, _) => this
          .checkMessageConstructor(msg, defn, parents)
      case PublishAction(_, msg, pipeRef, _) => this
          .checkMessageConstructor(msg, defn, parents)
          .checkRef[Pipe](pipeRef, defn, parents)
      case FunctionCallAction(_, funcId, args, _) => this
          .checkPathRef[Function](funcId, defn, parents)()()
          .checkArgList(args, defn, parents)
      case BecomeAction(_, entity, handler, _) => this
          .checkRef[Entity](entity, defn, parents)
          .checkRef[Handler](handler, defn, parents)
      case MorphAction(_, entity, entityState, _) => this
          .checkRef[Entity](entity, defn, parents)
          .checkRef[State](entityState, defn, parents)
      case TellAction(_, msg, entity, _) => this
          .checkRef[Definition](entity, defn, parents)
          .checkMessageConstructor(msg, defn, parents)
      case AskAction(_, entity, msg, _) => this
          .checkRef[Entity](entity, defn, parents)
          .checkMessageConstructor(msg, defn, parents)
      case ReplyAction(_, msg, _) => checkMessageConstructor(msg, defn, parents)
      case CompoundAction(loc, actions, _) =>
        check(actions.nonEmpty, "Compound action is empty", MissingWarning, loc)
          .checkSequence(actions) { (s, action) =>
            s.checkAction(action, defn, parents)
          }
      case ArbitraryAction(loc, what, _) => this.check(
          what.nonEmpty,
          "arbitrary action is empty so specifies nothing",
          MissingWarning,
          loc
        )
    }
  }

  def checkActions(
    actions: Seq[Action],
    defn: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    checkSequence(actions)((s, action) => s.checkAction(action, defn, parents))
  }

  def checkExample(
    example: Example,
    parents: Seq[Definition]
  ): ValidationState = {
    val Example(_, _, givens, whens, thens, buts, _, _) = example
    checkSequence(givens) { (state, givenClause) =>
      state.checkSequence(givenClause.scenario) { (state, ls) =>
        state.checkNonEmptyValue(ls, "Given Scenario", example, MissingWarning)
      }.checkNonEmpty(givenClause.scenario, "Givens", example, MissingWarning)
    }.checkSequence(whens) { (st, when) =>
      st.checkExpression(when.condition, example, parents)
    }.checkThat(example.id.nonEmpty) { st =>
      st.checkNonEmpty(thens, "Thens", example, required = true)
    }.checkActions(thens.map(_.action), example, parents)
      .checkActions(buts.map(_.action), example, parents)
      .checkDescription(example)
  }

  def checkExamples(
    examples: Seq[Example],
    parents: Seq[Definition]
  ): ValidationState = {
    examples.foldLeft(this) { (next, example) =>
      next.checkExample(example, parents)
    }
  }

  def checkFunctionCall(
    loc: At,
    pathId: PathIdentifier,
    args: ArgList,
    defn: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    checkArgList(args, defn, parents).checkPathRef[Function](
      pathId,
      defn,
      parents
    ) { (state, foundClass, id, defClass, defn) =>
      defaultSingleMatchValidationFunction(
        state,
        foundClass,
        id,
        defClass,
        defn
      )
      defn match {
        case f: Function if f.input.nonEmpty =>
          val fid = f.id
          val fields = f.input.get.fields
          val paramNames = fields.map(_.id.value)
          val argNames = args.args.keys.map(_.value).toSeq
          val s1 = state.check(
            argNames.size == paramNames.size,
            s"Wrong number of arguments for ${fid.format}. Expected ${paramNames
                .size}, but got ${argNames.size}",
            Error,
            loc
          )
          val missing = paramNames.filterNot(argNames.contains(_))
          val unexpected = argNames.filterNot(paramNames.contains(_))
          val s2 = s1.check(
            missing.isEmpty,
            s"Missing arguments: ${missing.mkString(", ")}",
            Error,
            loc
          )
          s2.check(
            unexpected.isEmpty,
            s"Arguments do not correspond to parameters; ${unexpected.mkString(",")}",
            Error,
            loc
          )
        case _ => state
      }
    }()
  }

  def checkExpressions(
    expressions: Seq[Expression],
    defn: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    expressions.foldLeft(this) { (st, expr) =>
      st.checkExpression(expr, defn, parents)
    }
  }

  def checkExpression(
    expression: Expression,
    defn: Definition,
    parents: Seq[Definition]
  ): ValidationState = expression match {
    case ValueOperator(_, path) => checkPathRef[Field](path, defn, parents)(
        nullSingleMatchingValidationFunction
      )()
    case GroupExpression(_, expressions) => checkSequence(expressions) {
        (st, expr) => st.checkExpression(expr, defn, parents)
      }
    case FunctionCallExpression(loc, pathId, arguments) =>
      checkFunctionCall(loc, pathId, arguments, defn, parents)
    case ArithmeticOperator(loc, op, operands) => check(
        op.nonEmpty,
        "Operator is empty in abstract binary operator",
        Error,
        loc
      ).checkExpressions(operands, defn, parents)
    case Comparison(loc, comp, arg1, arg2) =>
      checkExpression(arg1, defn, parents).checkExpression(arg2, defn, parents)
        .check(
          arg1.expressionType.isAssignmentCompatible(arg2.expressionType),
          s"Incompatible expression types in ${comp.format} expression",
          Messages.Error,
          loc
        )
    case AggregateConstructionExpression(_, pid, args) =>
      checkPathRef[Type](pid, defn, parents)()()
        .checkArgList(args, defn, parents)
    case NewEntityIdOperator(_, entityRef) =>
      checkPathRef[Entity](entityRef, defn, parents)()()
    case Ternary(loc, condition, expr1, expr2) =>
      checkExpression(condition, defn, parents)
        .checkExpression(expr1, defn, parents)
        .checkExpression(expr2, defn, parents).check(
          expr1.expressionType.isAssignmentCompatible(expr2.expressionType),
          "Incompatible expression types in Ternary expression",
          Messages.Error,
          loc
        )

    case NotCondition(_, cond1) => checkExpression(cond1, defn, parents)
    case condition: MultiCondition =>
      checkExpressions(condition.conditions, defn, parents)
    case _ => this // not of interest
  }

  @tailrec private final def getPathIdType(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Option[TypeExpression] = {
    if (pid.value.isEmpty) { None }
    else {
      val newParents: Seq[Definition] = resolvePath(pid, parents)()()
      val candidate: Option[TypeExpression] = newParents.headOption match {
        case None              => None
        case Some(f: Function) => f.output
        case Some(t: Type)     => Some(t.typ)
        case Some(f: Field)    => Some(f.typeEx)
        case Some(s: State)    => Some(s.aggregation)
        case Some(Pipe(_, _, tt, _, _, _, _)) =>
          val te = tt.map(x => AliasedTypeExpression(x.loc, x.pathId))
          Some(te.getOrElse(Abstract(pid.loc)))
        case Some(Inlet(_, _, typ, _, _)) =>
          Some(AliasedTypeExpression(typ.loc, typ.pathId))
        case Some(Outlet(_, _, typ, _, _)) =>
          Some(AliasedTypeExpression(typ.loc, typ.pathId))
        case Some(_) => Option.empty[TypeExpression]
      }
      candidate match {
        case Some(AliasedTypeExpression(_, pid)) =>
          getPathIdType(pid, newParents)
        case Some(other: TypeExpression) => Some(other)
        case None                        => None
      }
    }
  }

  def isAssignmentCompatible(
    typeEx1: Option[TypeExpression],
    typeEx2: Option[TypeExpression]
  ): Boolean = {
    typeEx1 match {
      case None => false
      case Some(ty1) => typeEx2 match {
          case None      => false
          case Some(ty2) => ty1.isAssignmentCompatible(ty2)
        }
    }
  }

  private def getExpressionType(
    expr: Expression,
    parents: Seq[Definition]
  ): Option[TypeExpression] = {
    expr match {
      case NewEntityIdOperator(loc, pid)      => Some(UniqueId(loc, pid))
      case ValueOperator(_, path)             => getPathIdType(path, parents)
      case FunctionCallExpression(_, name, _) => getPathIdType(name, parents)
      case GroupExpression(loc, expressions)  =>
        // the type of a group is the last expression but it could be empty
        expressions.lastOption match {
          case None       => Some(Abstract(loc))
          case Some(expr) => getExpressionType(expr, parents)
        }
      case AggregateConstructionExpression(_, pid, _) =>
        getPathIdType(pid, parents)
      case Ternary(loc, _, expr1, expr2) =>
        val expr1Ty = getExpressionType(expr1, parents)
        val expr2Ty = getExpressionType(expr2, parents)
        if (isAssignmentCompatible(expr1Ty, expr2Ty)) { expr1Ty }
        else {
          addError(
            loc,
            s"""Ternary expressions must be assignment compatible but:
               |  ${expr1.format} and
               |  ${expr2.format}
               |are incompatible
               |""".stripMargin
          )
          None
        }
      case e: Expression => Some(e.expressionType)
    }
  }

  def checkAssignmentCompatability(
    path: PathIdentifier,
    expr: Expression,
    parents: Seq[Definition]
  ): ValidationState = {
    val pidType = getPathIdType(path, parents)
    val exprType = getExpressionType(expr, parents)
    if (!isAssignmentCompatible(pidType, exprType)) {
      addError(
        path.loc,
        s"""Setting a value requires assignment compatibility, but field:
           |  ${path.format} (${pidType.map(_.format).getOrElse("<not found>")})
           |is not assignment compatible with expression:
           |  ${expr.format} (${exprType.map(_.format).getOrElse("<not found>")})
           |""".stripMargin
      )
    } else { this }
  }

  def checkArgList(
    arguments: ArgList,
    defn: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    arguments.args.values.foldLeft(this) { (st, arg) =>
      st.checkExpression(arg, defn, parents)
    }
  }

  def checkMessageConstructor(
    messageConstructor: MessageConstructor,
    defn: Definition,
    parents: Seq[Definition]
  ): ValidationState = {
    val id = messageConstructor.msg.pathId
    val kind = messageConstructor.msg.messageKind.kind
    checkPathRef[Type](id, defn, parents, Some(kind)) {
      (state, _, id, _, defn) =>
        defn match {
          case Type(_, _, typ, _, _) => typ match {
              case mt: AggregateUseCaseTypeExpression =>
                val names = messageConstructor.args.args.keys.map(_.value).toSeq
                val unset = mt.fields.filterNot { fName =>
                  names.contains(fName.id.value)
                }
                if (unset.nonEmpty) {
                  unset.filterNot(_.isImplicit).foldLeft(state) {
                    (next, field) =>
                      next.addError(
                        messageConstructor.loc,
                        s"${field.identify} was not set in message constructor"
                      )
                  }
                } else { state }
              case te: TypeExpression => state.addError(
                  id.loc,
                  s"'${id.format}' should reference a message type but is a ${AST.errorDescription(te)} type instead."
                )
            }
          case _ => addError(
              id.loc,
              s"'${id.format}' was expected to be a message type but is ${article(defn.kind)} instead"
            )
        }
    }(defaultMultiMatchValidationFunction)
  }

  def checkProcessorShape(proc: Processor): ValidationState = {
    val ins = proc.inlets.size
    val outs = proc.outlets.size
    def generateError(
      proc: Processor,
      req_ins: Int,
      req_outs: Int
    ): ValidationState = {
      def sOutlet(n: Int): String = {
        if (n == 1) s"1 outlet"
        else if (n < 0) { s"at least ${abs(n)} outlets" }
        else s"$n outlets"
      }
      def sInlet(n: Int): String = {
        if (n == 1) s"1 inlet"
        else if (n < 0) { s"at least ${abs(n)} outlets" }
        else s"$n inlets"
      }
      this.addError(
        proc.loc,
        s"${proc.identify} should have " + sOutlet(req_outs) + " and " +
          sInlet(req_ins) + s" but it has " + sOutlet(outs) + " and " +
          sInlet(ins)
      )
    }

    if (!proc.isEmpty) {
      proc.shape match {
        case _: AST.Source =>
          if (ins != 0 || outs != 1) { generateError(proc, 0, 1) }
          else { this }
        case _: AST.Flow =>
          if (ins != 1 || outs != 1) { generateError(proc, 1, 1) }
          else { this }
        case _: AST.Sink =>
          if (ins != 1 || outs != 0) { generateError(proc, 1, 0) }
          else { this }
        case _: AST.Merge =>
          if (ins < 2 || outs != 1) { generateError(proc, -2, 1) }
          else { this }
        case _: AST.Split =>
          if (ins != 1 || outs < 2) { generateError(proc, 1, -2) }
          else { this }
        case _: AST.Router =>
          if (ins < 2 || outs < 2) { generateError(proc, -2, -2) }
          else { this }
        case _: AST.Multi =>
          if (ins < 2 || outs < 2) { generateError(proc, -2, -2) }
          else { this }
        case _: AST.Void =>
          if (ins > 0 || outs > 0) { generateError(proc, 0, 0) }
          else { this }
      }
    } else { this }
  }

  def areSameType(
    tr1: TypeRef,
    tr2: TypeRef,
    parents: Seq[Definition]
  ): Boolean = {
    val pid1 = tr1.pathId
    val pid2 = tr2.pathId
    val typeDef1 = resolvePathIdentifier[Type](pid1, parents)
    val typeDef2 = resolvePathIdentifier[Type](pid2, parents)
    typeDef1.nonEmpty && typeDef2.nonEmpty && (typeDef1.get == typeDef2.get)
  }
}
