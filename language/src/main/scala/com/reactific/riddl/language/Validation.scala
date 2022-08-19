/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Folding.Folder
import Messages.*
import com.reactific.riddl.language.parsing.RiddlParserInput

import java.util.regex.PatternSyntaxException
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal
import scala.util.matching.Regex
import org.apache.commons.lang3.exception.ExceptionUtils

/** Validates an AST */
object Validation {

  def validate(
    root: ParentDefOf[Definition],
    commonOptions: CommonOptions = CommonOptions()
  ): Messages.Messages = {
    val symTab = SymbolTable(root)
    val state = ValidationState(symTab, root, commonOptions)
    val result = try {
      val folder = new ValidationFolder
      val s1 = Folding.foldAround(state, root, folder)
      checkOverloads(symTab, s1)
    } catch {
      case NonFatal(xcptn) =>
        val message =
          ExceptionUtils.getRootCauseStackTrace(xcptn).mkString("\n")
        state.add(Message(Location.empty, message, SevereError))
    }
    result.messages.sortBy(_.loc)
  }

  def checkOverloads(
    symbolTable: SymbolTable,
    state: ValidationState
  ): ValidationState = {
    symbolTable.foreachOverloadedSymbol { defs: Seq[Seq[Definition]] =>
      defs.foldLeft(state) { (s, defs2) =>
        if (defs2.sizeIs == 2) {
          val first = defs2.head
          val last = defs2.last
          s.addStyle(
            last.loc,
            s"${last.identify} overloads ${first.identifyWithLoc}"
          )
        } else if (defs2.sizeIs > 2) {
          val first = defs2.head
          val tail = defs2.tail.map(d => d.identifyWithLoc).mkString(s",\n    ")
          s.addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
        } else { s }
      }
    }
  }


  case class ValidationState(
    symbolTable: SymbolTable,
    root: ParentDefOf[Definition] = RootContainer.empty,
    commonOptions: CommonOptions = CommonOptions()
  ) extends Folding.MessagesState[ValidationState]
    with Folding.PathResolutionState[ValidationState]
  {

    def parentOf(
      definition: Definition
    ): Container[Definition] = {
      symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
    }

    def lookup[T <: Definition: ClassTag](
      id: Seq[String]
    ): List[T] = { symbolTable.lookup[T](id) }

    def addIf(predicate: Boolean)(msg: Message): ValidationState = {
      if (predicate) add(msg) else this
    }

    private val vowels: Regex = "[aAeEiIoOuU]".r
    def article(thing: String): String = {
      val article = if (vowels.matches(thing.substring(0, 1))) "an" else "a"
      s"$article $thing"
    }

    def check(
      predicate: Boolean = true,
      message: => String,
      kind: KindOfMessage,
      loc: Location
    ): ValidationState = {
      if (!predicate) { add(Message(loc, message, kind)) }
      else { this }
    }

    def checkIdentifierLength[T <: Definition](
      d: T,
      min: Int = 3
    ): ValidationState = {
      if (d.id.value.nonEmpty && d.id.value.length < min) {
        add(Message(
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
      } catch {
        case x: PatternSyntaxException =>
          add(Message(p.loc, x.getMessage))
      }
    }

    def checkEnumeration(
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

    def checkAlternation(
      alternation: AST.Alternation
    ): ValidationState = {
      alternation.of.foldLeft(this) { case (state, typex) =>
        state.checkTypeExpression(typex)
      }
    }

    def checkRangeType(
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
      agg: Aggregation,
    ): ValidationState = {
      agg.fields.foldLeft(this) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          "Field names in aggregates should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx).checkDescription(field)
      }
    }

    def checkMessageType(
      mt: MessageType
    ): ValidationState = {
      val kind = mt.messageKind.kind
      mt.fields.foldLeft(this) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          s"Field names in $kind messages should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx).checkDescription(field)
      }
    }

    def checkMapping(
      mapping: AST.Mapping
    ): ValidationState = {
      this.checkTypeExpression(mapping.from)
        .checkTypeExpression(mapping.to)
    }

    def checkTypeExpression[TD <: Definition](
      typ: TypeExpression,
    ): ValidationState = {
      typ match {
        case agg: Aggregation              => checkAggregation(agg)
        case mt: MessageType               => checkMessageType(mt)
        case TypeRef(_, id: PathIdentifier) => checkPathRef[Type](id)()
        case alt: Alternation              => checkAlternation(alt)
        case mapping: Mapping              => checkMapping(mapping)
        case rt: RangeType                 => checkRangeType(rt)
        case p: Pattern                    => checkPattern(p)
        case Enumeration(_, enumerators)   => checkEnumeration(enumerators)
        case Optional(_, tye )             => checkTypeExpression(tye)
        case OneOrMore(_, tye )            => checkTypeExpression(tye)
        case ZeroOrMore(_, tye )           => checkTypeExpression(tye)
        case SpecificRange(_, typex: TypeExpression, min, max) =>
          checkTypeExpression(typex, definition)
          check(min >= 0,
            "Minimum cardinality must be non-negative", Error,
            typ.loc)
          check(max >= 0,
            "Maximum cardinality must be non-negative", Error,
            typ.loc)
          check(min < max,
            "Minimum cardinality must be less than maximum cardinality",
            Error,
            typ.loc
          )
        case UniqueId(_, entityName)       => checkPathRef[Entity](entityName)()
        case ReferenceType(_, entity)      => checkRef[Entity](entity)
        case _: PredefinedType              => this // nothing needed
        case _: TypeRef                    => this // handled elsewhere
        case x =>
          require(requirement=false, s"Failed to match definition $x")
          this
      }
    }

    def checkSymbolLookup[DT <: Definition: ClassTag](
      symbol: Seq[String]
    )(checkEmpty: () => ValidationState
    )(checkSingle: DT => ValidationState
    )(checkMulti: (DT, SymbolTable#LookupResult[DT]) => ValidationState
    ): ValidationState = {
      symbolTable.lookupSymbol[DT](symbol) match {
        case Nil            => checkEmpty()
        case (d, _) :: Nil  => checkSingle(d.asInstanceOf[DT])
        case (d, _) :: tail => checkMulti(d.asInstanceOf[DT], tail)
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

    private val defaultSingleMatchValidationFunction
      : SingleMatchValidationFunction =
      (state, foundClass, id, defClass, _, _) => {
        state.check(
          foundClass.isAssignableFrom(defClass),
          s"'${id.format}' was expected to be ${article(foundClass.getSimpleName)} but is " +
            s"${article(defClass.getSimpleName)} instead",
          Error,
          id.loc
        )
      }

    private def handleMultipleResultsCase[T <: Definition](
      id: PathIdentifier,
      list: List[(Definition, Option[T])]
    ): ValidationState = {
      // Handle domain, context, entity same name
      require(list.size > 1) // must be so or caller logic isn't right
      // val tc = classTag[T].runtimeClass
      val definitions = list.map { case (definition, _) => definition }
      val allDifferent =
        definitions.map(AST.kind).distinct.sizeIs == definitions.size
      if (allDifferent) { this }
      else if (!definitions.head.isImplicit) {
        add(Message(
          id.loc,
          s"""Path reference '${id.format}' is ambiguous. Definitions are:
             |${formatDefinitions(definitions)}""".stripMargin,
          Error
        ))
      } else { this }
    }


    def checkPathRef[T <: Definition: ClassTag](
      pid: PathIdentifier, kind: Option[String] = None
    )(validator: SingleMatchValidationFunction =
      defaultSingleMatchValidationFunction
    ): ValidationState = {
      val tc = classTag[T].runtimeClass
      def notResolved(): Unit = {
        val message = s"Path '${pid.format}' was not resolved," +
          s" in ${definition.kind} '${definition.id.value}'"
        val referTo = if (kind.nonEmpty) kind.get else tc.getSimpleName

        addError(
          pid.loc, message + {
            if (referTo.nonEmpty)
              s", but should refer to ${article(referTo)}"
            else ""
          }
        )
      }
      if (pid.value.nonEmpty && pid.value.forall(_.nonEmpty)) {
        symbolTable.lookupSymbol[T](pid.value) match {
          case Nil =>
            notResolved()
            this
          case (d, optT) :: Nil =>
            // Single match, defer to validation function
            validator(this, tc, pid, d.getClass, d, optT)
          case (d, optT) :: tail =>
            // Too many matches / non-unique / ambiguous
            val list = (d, optT) :: tail
            handleMultipleResultsCase[T](pid, list)
        }
      } else {
        val resolution = resolvePath(pid)
        resolution match {
          case None => notResolved()
          case Some(x) if x.getClass != tc =>
            val message = s"Path '${pid.format}' resolved to ${article(
              x.getClass.getSimpleName)} but ${article(
              tc.getSimpleName)} was expected"
            addError(pid.loc, message)
          case _ => // class matched, we're good!
        }
        this
      }
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference[T], kind: Option[String] = None
    ): ValidationState = {
      checkPathRef[T](reference.id, kind)() }

    def checkMaybeRef[T <: Definition: ClassTag](
      reference: Option[Reference[T]], kind: Option[String] = None
    ): ValidationState = {
      if (reference.nonEmpty) {
        checkPathRef[T](reference.get.id, kind)() }
      else { this }
    }

    def checkMessageRef(ref: MessageRef, kind: MessageKind): ValidationState = {
      if (ref.isEmpty) {
        addError(ref.id.loc, s"${ref.identify} is empty")
      } else {
        checkPathRef[Type](ref.id, Some(kind.kind)) { (state, _, _, _, defn, _) =>
          defn match {
            case Type(_, _, typ, _, _) => typ match {
              case MessageType(_, mk, _) =>
                state.check(
                  mk == kind,
                  s"'${ref.identify} should be ${article(kind.kind)} type" +
                    s" but is ${article(mk.kind)} type instead",
                  Error,
                  ref.id.loc
                )
              case te: TypeExpression =>
                state.addError(
                  ref.id.loc,
                  s"'${ref.identify} should reference ${
                    article(kind.kind)
                  } but is a ${
                    AST.kind(te)
                  } type instead")
            }
            case _ =>
              state.addError(
                ref.id.loc,
                s"${ref.identify} was expected to be ${article(kind.kind)} type but is ${
                  article(AST.kind(defn))
                } instead")
          }
        }
      }
    }

    private def formatDefinitions[T <: Definition](list: List[T]): String = {
      list.map { dfntn => "  " + dfntn.id.value + " (" + dfntn.loc + ")" }
        .mkString("\n")
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
        message =
          s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
        kind,
        thing.loc
      )
    }

    def checkOptions[T <: OptionValue](
      options: Seq[T],
      loc: Location
    ): ValidationState = {
      check(
        options.sizeIs == options.distinct.size,
        "Options should not be repeated",
        Error,
        loc
      )
    }

    def checkDefinition[TCD <: ParentDefOf[Definition], TD <: Definition](
      parent: TCD,
      definition: TD
    ): ValidationState = {
      var result = this.check(
        definition.id.nonEmpty | definition.isImplicit,
        "Definitions may not have empty names",
        Error,
        definition.loc
      )
      result = result.checkIdentifierLength(definition)
      val path = symbolTable.pathOf(definition)
      if (!definition.id.isEmpty) {
        val matches = result.lookup[Definition](path)
        if (matches.isEmpty) {
          result = result.add(Message(
            definition.id.loc,
            s"'${definition.id.value}' evaded inclusion in symbol table!",
            SevereError
          ))
        } else if (matches.sizeIs >= 2) {
          val parentGroups = matches.groupBy(result.symbolTable.parentOf(_))
          parentGroups.get(Option(parent)) match {
            case Some(head :: tail) if tail.nonEmpty =>
              result = result.add(Message(
                head.id.loc,
                s"${definition.identify} has same name as other definitions in ${parent.identifyWithLoc}:  " +
                  tail.map(x => x.identifyWithLoc).mkString(",  "),
                Warning
              ))
            case Some(head :: tail) if tail.isEmpty =>
              result = result.add(Message(
                head.id.loc,
                s"${definition.identify} has same name as other definitions: " +
                  matches.filterNot(_ == definition).map(x => x.identifyWithLoc)
                    .mkString(",  "),
                StyleWarning
              ))
            case _ =>
            // ignore
          }
        }
      }
      result
    }

    def checkDescription[TD <: DescribedValue](
      id: String,
      value: TD,
    ): ValidationState = {
      val description: Option[Description] = value.description
      val shouldCheck: Boolean =
        (value.isInstanceOf[Definition] && value.nonEmpty) ||
          value.isInstanceOf[Type]
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

    def checkParentDefOf[
      P <: ParentDefOf[Definition],
      T <: ParentDefOf[Definition]
    ](parent: P,
      container: T
    ): ValidationState = {
      this.checkDefinition(parent, container).check(
        container.nonEmpty,
        s"${container.identify} in ${parent.identify} should have content.",
        MissingWarning,
        container.loc
      )
    }
/*
    def checkContainer[C <: Container[Definition]](
      container: C
    ): ValidationState = {
      this.check(
        container.nonEmpty,
        s"${AST.kind(container)} must have content.",
        MissingWarning,
        container.loc
      )
    }
*/
    def checkAction(
      action: Action
    ): ValidationState = {
      action match {
        case _: ErrorAction => this
        case SetAction(_, path, value, _) =>
          this
            .checkPathRef[Field](path)()
            .checkExpression(value)
        case ReturnAction(_, expr, _) =>
          this.checkExpression(expr)
        case YieldAction(_, msg, _) =>
          this.checkMessageConstructor(msg)
        case PublishAction(_, msg, pipeRef, _) =>
          this
            .checkMessageConstructor(msg)
            .checkRef[Pipe](pipeRef)
        case FunctionCallAction(_, funcId, args, _) =>
          this
            .checkPathRef[Function](funcId)()
            .checkArgList(args)
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
        case ReplyAction(_, msg, _) => this.checkMessageConstructor(msg)
        case CompoundAction(loc, actions, _) =>
          val vs = this.check(
            actions.nonEmpty,
            "Compound action is empty",
            MissingWarning,
            loc
          )
          actions.foldLeft(vs) { (s, action) =>
            s.checkAction(action)
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
    ): ValidationState = {
      actions.foldLeft(this)((s, action) => s.checkAction(action))
    }

    def checkExample(
      example: Example,
    ): ValidationState = {
      stepIf(example.nonEmpty) { st =>
        val Example(_, _, givens, whens, thens, buts, _, _) = example
        givens.foldLeft(st) { (state, givenClause) =>
            givenClause.scenario.foldLeft(state) { (state, ls) =>
            state
              .checkNonEmptyValue(ls, "Given Scenario", example, MissingWarning)
          }.checkNonEmpty(
              givenClause.scenario, "Givens", example, MissingWarning
            )
        }.step { state: ValidationState =>
          whens.foldLeft(state) { (st, whenClause) =>
            st.checkExpression(whenClause.condition)
          }
        }.checkNonEmpty(thens, "Thens", example, required = true).step {
          st: ValidationState =>
            st.checkActions(thens.map(_.action))
        }.step { st: ValidationState =>
          st.checkActions(buts.map(_.action))
        }.checkDescription(example)
      }
    }

    def checkExamples(
      examples: Seq[Example],
    ): ValidationState = {
      examples.foldLeft(this) { (next, example) =>
        next.checkExample(example)
      }
    }

    def checkFunctionCall(
      loc: Location,
      pathId: PathIdentifier,
      args: ArgList
    ): ValidationState = {
      checkArgList(args)
      .checkPathRef[Function](pathId) {
        (state, foundClass, id, defClass, defn, optN) =>
          val s = defaultSingleMatchValidationFunction(
            state,
            foundClass,
            id,
            defClass,
            defn,
            optN
          )
          defn match {
            case Function(_, fid, Some(Aggregation(_, fields)), _, _, _, _, _, _) =>
              val paramNames = fields.map(_.id.value)
              val argNames = args.args.keys.map(_.value).toSeq
              val s1 = s.check(
                argNames.size == paramNames.size,
                s"Wrong number of arguments for ${fid
                  .format}. Expected ${paramNames.size}, but got ${argNames.size}",
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
            case _ => s
          }
      }
    }

    def checkExpressions(
      expressions: Seq[Expression]
    ): ValidationState = {
      expressions.foldLeft(this) { (st, expr) =>
        st.checkExpression(expr)
      }
    }

    def checkExpression(
      expression: Expression,
    ): ValidationState =
      expression match {
        case ValueExpression(_, path)  =>
          checkPathRef[Field](path)()
        case GroupExpression(_, expr) =>
          checkExpression(expr)
        case FunctionCallExpression(loc, pathId, arguments) =>
          checkFunctionCall(loc, pathId, arguments)
        case ArithmeticOperator(loc, op, operands) =>
          check(
            op.nonEmpty,
            "Operator is empty in abstract binary operator",
            Error,
            loc
          ).checkExpressions(operands)
        case Comparison(_, _, arg1, arg2) =>
          checkExpression(arg1)
            .checkExpression(arg2)
        case AggregateConstructionExpression(_, typeRef, args) =>
          checkRef[Type](typeRef)
            .checkArgList(args)
        case EntityIdExpression(_, entityRef) =>
          checkRef[Entity](entityRef)
        case FunctionCallAction(_, pid, args, _) =>
          checkPathRef[Function](pid)()
            .checkArgList(args)
        case Ternary(_, condition, expr1, expr2) =>
          checkExpression(condition)
            .checkExpression(expr1)
            .checkExpression(expr2)
        case NotCondition(_, cond1) =>
          checkExpression(cond1)
        case condition: MultiCondition =>
          checkExpressions(condition.conditions)
        case _ =>
          this // not of interest
    }

    def checkArgList(
      arguments: ArgList,
    ): ValidationState = {
      arguments.args.values.foldLeft(this) { (st, arg) =>
        st.checkExpression(arg)
      }
    }

    def checkMessageConstructor(
      messageConstructor: MessageConstructor
    ): ValidationState = {
      val id = messageConstructor.msg.id
      val kind = messageConstructor.msg.messageKind.kind
      checkPathRef[Type](id,Some(kind)) { (state, _, id, _, defn, _) =>
        defn match {
          case Type(_, _, typ, _, _) => typ match {
              case mt: MessageType =>
                val names = messageConstructor.args.args.keys.map(_.value).toSeq
                val unset = mt.fields.filterNot { fName =>
                  names.contains(fName.id.value)
                }
                if (unset.nonEmpty) {
                  unset.filterNot(_.isImplicit).foldLeft(state) {
                    (next, field) =>
                      next.addError(
                        field.loc,
                        s"${field.identify} was not set in message constructor"
                      )
                  }
                } else { state }
              case te: TypeExpression => state.addError(
                  id.loc,
                  s"'${id.format}' should reference a message type but is a ${AST.kind(te)} type instead."
                )
            }
          case _ => addError(
              id.loc,
              s"'${id.format}' was expected to be a message type but is ${article(AST.kind(defn))} instead"
            )
        }
      }
    }

    def checkProcessorKind(proc: Processor): ValidationState = {
      val ins = proc.inlets.size
      val outs = proc.outlets.size
      proc.shape match {
        case AST.Source(loc) =>
          if (ins != 0 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and no Inlets but has $outs and $ins "
            )
          } else { this }
        case AST.Flow(loc) =>
          if (ins != 1 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Sink(loc) =>
          if (ins != 1 || outs != 0) {
            this.addError(
              loc,
              s"${proc.identify} should have no Outlets and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Merge(loc) =>
          if (ins < 2 || outs != 1) {
            this.addError(
              loc,
              s"${proc.identify} should have 1 Outlet and >1 Inlets but has $outs and $ins"
            )
          } else { this }
        case AST.Split(loc) =>
          if (ins != 1 || outs < 2) {
            this.addError(
              loc,
              s"${proc.identify} should have >1 Outlets and 1 Inlet but has $outs and $ins"
            )
          } else { this }
        case AST.Multi(loc) =>
          if (ins < 2 || outs < 2) {
            this.addError(
              loc,
              s"${proc.identify} should have >1 Outlets and >1 Inlets but has $outs and $ins"
            )
          } else { this }
      }
    }

  }

  class ValidationFolder extends Folder[ValidationState] {


    def openContainer(
      state: ValidationState,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): ValidationState = {
      state.captureHierarchy(container, parents)
      container match {
        case typ: Type          => openType(state, parents.head, typ)
        case function: Function =>
          state.checkDefinition(container, function)
        case onClause: OnClause =>
          if (!onClause.msg.isEmpty)
            state.checkMessageRef(onClause.msg, onClause.msg.messageKind)
          else
            state
        case st: State =>
          state.checkDefinition(parents.head, st)
        case processor: Processor =>
          openProcessor(state, parents.head, processor)
        case story: Story       => openStory(state, parents.head, story)
        case sagaStep: SagaStep => openSagaStep(state, parents.head, sagaStep)
        case entity: Entity     => openEntity(state, parents.head, entity)
        case context: Context   => openContext(state, parents.head, context)
        case include: Include   => openInclude(state, include)
        case adaptation: AdaptorDefinition =>
          openAdaptation(state, parents.head, adaptation)
        case domain: Domain =>
          val parent = parents.headOption.getOrElse(
            RootContainer(Seq(domain),Seq.empty[RiddlParserInput]))
          state.checkParentDefOf(parent, container)
        case _: RootContainer =>
          // we don't validate root containers
          state
        case container: ParentDefOf[Definition] =>
          // Adaptor, Plant, State, Saga, Handler
          state.checkParentDefOf(parents.head, container)
      }
    }


    def openContext(
      state: Validation.ValidationState,
      container: ParentDefOf[Definition],
      context: AST.Context
    ): ValidationState = {
      state.checkParentDefOf(container, context)
        .checkOptions[ContextOption](context.options, context.loc)
    }

    def openStory(
      state: ValidationState,
      container: ParentDefOf[Definition],
      story: Story
    ): ValidationState = {
      state.checkParentDefOf(container, story)
        .checkNonEmptyValue(story.role, "role", story, MissingWarning)
        .checkNonEmptyValue(
          story.capability,
          "capability",
          story,
          MissingWarning
        ).checkNonEmptyValue(story.benefit, "benefit", story, MissingWarning)
        .checkExamples(story.examples)
    }

    def openEntity(
      state: Validation.ValidationState,
      container: ParentDefOf[Definition],
      entity: AST.Entity
    ): ValidationState = {
      state.checkParentDefOf(container, entity)
        .checkOptions[EntityOption](entity.options, entity.loc)
        .addIf(entity.handlers.isEmpty && !entity.isEmpty) {
          Message(
            entity.loc,
            s"${entity.identify} must define a handler"
          )
        }.addIf(
          entity.handlers.nonEmpty && entity.handlers.forall(_.clauses.isEmpty)
        ) {
          Message(
            entity.loc,
            s"${entity.identify} has only empty handlers",
            MissingWarning
          )
        }.addIf(
          entity.hasOption[EntityIsFiniteStateMachine] && entity.states.sizeIs < 2
        ) {
          Message(
            entity.loc,
            s"${entity.identify} is declared as an fsm, but doesn't have at least two states",
            Error
          )
        }
    }

    def openInclude(
      state: ValidationState,
      include: Include
    ): ValidationState = {
      state.check(
        include.nonEmpty,
        "Include has no included content",
        Error,
        include.loc
      ).check(
        include.path.nonEmpty,
        "Include has no path provided",
        Error,
        include.loc
      ).step { s =>
        if (include.path.nonEmpty) {
          s.check(
            include.path.get.toString.nonEmpty,
            "Include path provided is empty",
            Error,
            include.loc
          )
        } else { s }
      }
    }

    def openType(
      state: ValidationState,
      container: ParentDefOf[Definition],
      typeDef: Type
    ): ValidationState = {
      state.checkDefinition(container, typeDef)
        .check(
        typeDef.id.value.head.isUpper,
        s"${typeDef.identify} should start with a capital letter",
        StyleWarning,
        typeDef.loc
      ).stepIf(!typeDef.typ.isInstanceOf[AggregateTypeExpression]) { st =>
        st.checkTypeExpression(typeDef.typ)
      }
    }

    def openAdaptation(
      state: ValidationState,
      container: ParentDefOf[Definition],
      adaptation: AdaptorDefinition
    ): ValidationState = {
      adaptation match {
        case eaa: EventActionA8n => state.checkDefinition(container, adaptation)
            .checkRef[Type](eaa.messageRef).checkActions(eaa.actions)
        case cca: CommandCommandA8n =>
          state
            .checkDefinition(container, adaptation)
            .checkRef[Type](cca.messageRef).checkRef[Type](cca.command)
        case eca: EventCommandA8n => state
            .checkDefinition(container, adaptation)
            .checkRef[Type](eca.messageRef).checkRef[Type](eca.command)
        case _ =>
          require(requirement = false, "Unknown adaptation")
          state
      }
    }

    def openSagaStep(
      state: ValidationState,
      saga: ParentDefOf[Definition],
      step: SagaStep
    ): ValidationState = {
      state.checkDefinition(saga, step).checkDescription(step).check(
        step.doAction.getClass == step.undoAction.getClass,
        "The primary action and revert action must be the same shape",
        Error,
        step.doAction.loc
      )
    }

    def openProcessor(
      state: ValidationState,
      container: ParentDefOf[Definition],
      processor: Processor
    ): ValidationState = {
      state.checkDefinition(container, processor).checkProcessorKind(processor)
        .step { s2 =>
          val s3 = processor.inlets.foldLeft(s2) { (s, n) =>
            s.checkMaybeRef[Entity](n.entity)
          }
          processor.outlets.foldLeft(s3) { (s, n) =>
            s.checkMaybeRef[Entity](n.entity)
          }
        }.step { s5 => s5.checkExamples(processor.examples) }
    }


    def doDefinition(
      state: ValidationState,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): ValidationState = {
      // Capture current parse state
      state.captureHierarchy(definition, parents)
      // check the obvious stuff for all definitions
      val s1 = state.checkDefinition(parents.head, definition)
      val parent = parents.head
      definition match {
        case example: Example => s1.checkDefinition(parent, example)
            .checkExample(example).checkDescription(example)
        case invariant: Invariant => s1.checkDefinition(parent, invariant)
            .checkNonEmptyValue(
              invariant.expression,
              "Condition",
              invariant,
              MissingWarning
            ).checkExpression(invariant.expression).checkDescription(invariant)
        case cca: CommandCommandA8n => s1.checkDefinition(parents.head, cca)
            .checkPathRef[Command](cca.messageRef.id)()
            .checkPathRef[Command](cca.command.id)()
            .checkDescription(cca)
        case eca: EventCommandA8n => s1.checkDefinition(parent, eca)
            .checkPathRef[Event](eca.messageRef.id)()
            .checkPathRef[Command](eca.command.id)()
            .checkDescription(eca)
        case eaa: EventActionA8n => s1.checkDefinition(parent, eaa)
            .checkPathRef[Event](eaa.messageRef.id)()
            .checkActions(eaa.actions)
            .checkExamples(eaa.examples).checkDescription(eaa)
        case field: Field =>
          s1.checkDefinition(parent, field)
            .addIf(field.id.value.matches("^[^a-z].*"))(
              Message(field.id.loc,
                "Field names should begin with a lower case letter",
                StyleWarning
              )
            )
            .checkTypeExpression(field.typeEx)
            .checkDescription(field)
        case h: Handler =>
          val s2 = s1.checkDefinition(parent, h)
          h.applicability.fold(s2) { pid =>
            s1.checkPathRef[Entity & Projection](pid)() }
            .checkDescription(h)
        case inlet @ Inlet(_,_,typeRef,entityRef,_,_) =>
          val s2 = s1.checkDefinition(parent, inlet)
            .checkRef[Type](typeRef)
          entityRef.fold(s2) { er =>
            s2.checkRef[Entity](er)}.checkDescription(inlet)
        case ij: InletJoint =>
          s1.checkDefinition(parent, ij)
            .checkPathRef[Pipe](ij.pipe.id)()
            .checkPathRef[Inlet](ij.inletRef.id)()
            .checkDescription(ij)
        case o: Outlet =>
          val s2 =
            s1.checkDefinition(parent, o)
              .checkRef[Type](o.type_)
          o.entity.fold(s2) { er =>
            s2.checkRef[Entity](er) }.checkDescription(o)
        case oj: OutletJoint =>
          s1.checkDefinition(parent, oj)
            .checkPathRef[Pipe](oj.pipe.id)()
            .checkPathRef[Outlet](oj.outletRef.id)()
            .checkDescription(oj)
        case s: Story =>
          val s2 = s1.checkDefinition(parent, s)
            .checkNonEmptyValue(s.role, "role", s, Error, required = true)
            .checkNonEmptyValue(s.benefit, "benefit", s, Error, required = true)
            .checkNonEmptyValue(
              s.capability,
              "capability",
              s,
              Error,
              required = true
            ).checkExamples(s.examples)
            .checkNonEmpty(s.shownBy, "shownBy", s, StyleWarning)
          s.implementedBy.foldLeft(s2) { (st, pid) =>
            st.checkRef[Domain](pid) }
            .checkDescription(s)
        case p: Pipe =>
          val s2 = s1.checkDefinition(parent, p)
          p.transmitType.fold(s2) { typeRef =>
            s1.checkPathRef[Type](typeRef.id)() }
            .checkDescription(p)
        case oc: OnClause =>
          s1.checkDefinition(parent, oc)
            .checkMessageRef(oc.msg, oc.msg.messageKind)
            .checkExamples(oc.examples).checkDescription(oc)
        case ai: AuthorInfo =>
          s1.checkNonEmptyValue(ai.name,"name", ai, Error, required=true)
            .checkNonEmptyValue(ai.email, "email", ai, Error, required=true)
            .checkDescription(ai)
        case t: Term =>
          s1.checkDefinition(parent, t)
            .checkDescription(t)
        case _: Enumerator   => s1 // handled in checkEnumeration
        case _: Container[?] => s1 // handled elsewhere
        case x  =>
          require(requirement=false, s"Failed to match definition $x")
          s1
      }
    }

    def closeContainer(
      state: ValidationState,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): ValidationState = {
      state.captureHierarchy(container, parents)
      container match {
        case _: Include       => state // nothing to validate
        case _: RootContainer =>
          // we don't validate root containers
          state
        case parent: ParentDefOf[Definition] =>
          // RootContainer, Domain, Context, Entity, Story, Adaptor, Plant,
          // State, Saga, Processor, Function, Handler, OnClause, Type,
          // Adaptation
          state.checkDescription(parent)
      }
    }

  }
}
