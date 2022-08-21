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
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.Location
import com.reactific.riddl.language.parsing.RiddlParserInput

import java.util.regex.PatternSyntaxException
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal
import scala.util.matching.Regex
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.annotation.tailrec

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
      state.checkSequence(defs) { (s, defs2) =>
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
  ) extends Folding.PathResolutionState[ValidationState] {

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

    def checkIf(
      predicate: Boolean = true,
    )(f: ValidationState => ValidationState) : ValidationState = {
      if (predicate) { f(this) }
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

    def checkAlternation(
      alternation: AST.Alternation,
      typeDef: Definition
    ): ValidationState = {
      checkSequence(alternation.of) { case (state, typex) =>
        state.checkTypeExpression(typex, typeDef)
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
      typeDef: Definition
    ): ValidationState = {
      checkSequence(agg.fields) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          "Field names in aggregates should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, typeDef).checkDescription(field)
      }
    }

    def checkMessageType(
      mt: MessageType,
      typeDef: Definition
    ): ValidationState = {
      val kind = mt.messageKind.kind
      checkSequence(mt.fields) { case (state, field) =>
        state.checkIdentifierLength(field).check(
          field.id.value.head.isLower,
          s"Field names in $kind messages should start with a lower case letter",
          StyleWarning,
          field.loc
        ).checkTypeExpression(field.typeEx, typeDef).checkDescription(field)
      }
    }

    def checkMapping(
      mapping: AST.Mapping,
      typeDef: Definition
    ): ValidationState = {
      this.checkTypeExpression(mapping.from, typeDef)
        .checkTypeExpression(mapping.to, typeDef)
    }

    def checkTypeExpression[TD <: Definition](
      typ: TypeExpression,
      defn: Definition
    ): ValidationState = {
      typ match {
        case agg: Aggregation              => checkAggregation(agg,defn)
        case mt: MessageType               => checkMessageType(mt,defn)
        case TypeRef(_, id: PathIdentifier) => checkPathRef[Type](id,defn)()
        case alt: Alternation              => checkAlternation(alt, defn)
        case mapping: Mapping              => checkMapping(mapping, defn)
        case rt: RangeType                 => checkRangeType(rt)
        case p: Pattern                    => checkPattern(p)
        case Enumeration(_, enumerators)   => checkEnumeration(enumerators)
        case Optional(_, tye )             => checkTypeExpression(tye, defn)
        case OneOrMore(_, tye )            => checkTypeExpression(tye, defn)
        case ZeroOrMore(_, tye )           => checkTypeExpression(tye, defn)
        case SpecificRange(_, typex: TypeExpression, min, max) =>
          checkTypeExpression(typex, defn)
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
        case UniqueId(_, entityRef)        => checkRef[Entity](entityRef, defn)
        case ReferenceType(_, entity)      => checkRef[Entity](entity, defn)
        case _: PredefinedType              => this // nothing needed
        case _: TypeRef                    => this // handled elsewhere
        case x =>
          require(requirement=false, s"Failed to match definition $x")
          this
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

    private def formatDefinitions[T <: Definition](list: List[T]): String = {
      list.map { dfntn => "  " + dfntn.id.value + " (" + dfntn.loc + ")" }
        .mkString("\n")
    }

    def resolvePathFromSymTab[T <: Definition: ClassTag](
      pid: PathIdentifier
    ) : Seq[Definition] = {
      val symTabCompatibleNameSearch = pid.value.reverse
      val list = symbolTable.lookupParentage(symTabCompatibleNameSearch)
      list match {
        case Nil => // nothing found
          Seq.empty[Definition]
        case (d, parents) :: Nil => // list.size == 1
          val expectedClass = classTag[T].runtimeClass
          val actualClass = d.getClass
          defaultSingleMatchValidationFunction(this,expectedClass,pid,actualClass,d,None)
          d +: parents
        case list => // list.size > 1
          // Extract all the definitions that were found
          val definitions = list.map(_._1)
          val allDifferent =
            definitions.map(AST.kind).distinct.sizeIs == definitions.size
          val expectedClass = classTag[T].runtimeClass
          if (allDifferent || definitions.head.isImplicit) {
            // pick the one that is the right type or the first one
            list.find(_._1.getClass == expectedClass) match {
              case Some((defn,parents)) => defn +: parents
              case None => list.head._1 +: list.head._2
            }
          } else {
            addError(
              pid.loc,
              s"""Path reference '${pid.format}' is ambiguous. Definitions are:
                 |${formatDefinitions(definitions)}""".stripMargin
            )
            Seq.empty[Definition]
          }
      }
    }

    def checkPathRef[T <: Definition: ClassTag](
      pid: PathIdentifier, container: Definition, kind: Option[String] = None
    )(validator: SingleMatchValidationFunction =
      defaultSingleMatchValidationFunction
    ): ValidationState = {
      val tc = classTag[T].runtimeClass
      def notResolved(): Unit = {
        val message = s"Path '${pid.format}' was not resolved," +
          s" in ${container.kind} '${container.id.value}'"
        val referTo = if (kind.nonEmpty) kind.get else tc.getSimpleName

        addError(
          pid.loc, message + {
            if (referTo.nonEmpty)
              s", but should refer to ${article(referTo)}"
            else ""
          }
        )
      }
      if (pid.value.isEmpty) {
        val message = s"An empty path cannot be resolved to ${
          article(tc.getSimpleName)} was expected"
        addError(pid.loc, message)
      } else if ( pid.value.exists(_.isEmpty)) {
        val resolution = resolvePath(pid)
        resolution.headOption match {
          case None =>
            notResolved()
            this
          /* FIXME: Can't know dynamic type at compile time!
          case Some(x) if x.getClass != tc =>
            val message = s"Path '${pid.format}' resolved to ${
              article(
                x.getClass.getSimpleName)
            } but ${
              article(
                tc.getSimpleName)
            } was expected"
            addError(pid.loc, message)
           */
          case Some(d) => // class matched, we're good!
            validator(this, tc,pid,d.getClass,d,None)
        }
      } else {
        val result = resolvePathFromSymTab[T](pid)
        if (result.isEmpty) {
          notResolved()
          this
        } else {
          validator(this, tc, pid, result.head.getClass, result.head, None)
        }
      }
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference[T], defn: Definition, kind: Option[String] = None
    ): ValidationState = {
      checkPathRef[T](reference.id, defn, kind)() }

    def checkMessageRef(ref: MessageRef, defn: Definition, kind: MessageKind): ValidationState = {
      if (ref.isEmpty) {
        addError(ref.id.loc, s"${ref.identify} is empty")
      } else {
        checkPathRef[Type](ref.id, defn, Some(kind.kind)) { (state, _, _, _, defn, _) =>
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

    def checkOption[A <: RiddlValue](
      opt: Option[A],
      name: String,
      thing: Definition
    )(folder: (ValidationState, A) => ValidationState ): ValidationState = {
      opt match {
        case None => addMissing(
            thing.loc,
            s"$name in ${thing.identify} should not be empty"
          )
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
        s"$name in ${thing.identify} ${
          if (required) "must" else "should"} not be empty",
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

    def checkDefinition(
      parent: Definition,
      definition: Definition
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
          parentGroups.get(Option(parent.asInstanceOf[ParentDefOf[Definition]])) match {
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
      action: Action,
      defn: Definition
    ): ValidationState = {
      action match {
        case _: ErrorAction => this
        case SetAction(_, path, value, _) =>
          this
            .checkPathRef[Field](path, defn)()
            .checkExpression(value, defn)
            .checkAssignmentCompatability(path, value)
        case AppendAction(_, value, path, _) =>
          this
            .checkExpression(value, defn)
            .checkPathRef[Field](path, defn)()
        case ReturnAction(_, expr, _) =>
          this.checkExpression(expr, defn)
        case YieldAction(_, msg, _) =>
          this.checkMessageConstructor(msg, defn)
        case PublishAction(_, msg, pipeRef, _) =>
          this
            .checkMessageConstructor(msg, defn)
            .checkRef[Pipe](pipeRef, defn)
        case FunctionCallAction(_, funcId, args, _) =>
          this
            .checkPathRef[Function](funcId, defn)()
            .checkArgList(args, defn)
        case BecomeAction(_, entity, handler, _) =>
          this
            .checkRef[Entity](entity, defn)
            .checkRef[Handler](handler, defn)
        case MorphAction(_, entity, entityState, _) =>
          this
            .checkRef[Entity](entity, defn)
            .checkRef[State](entityState, defn)
        case TellAction(_, msg, entity, _) =>
          this
            .checkRef[Entity](entity, defn)
            .checkMessageConstructor(msg, defn)
        case AskAction(_, entity, msg, _) =>
          this
            .checkRef[Entity](entity, defn)
            .checkMessageConstructor(msg, defn)
        case ReplyAction(_, msg, _) =>
          checkMessageConstructor(msg, defn)
        case CompoundAction(loc, actions, _) =>
          check(
            actions.nonEmpty,
            "Compound action is empty",
            MissingWarning,
            loc
          ).checkSequence(actions){ (s, action) =>
            s.checkAction(action, defn)
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
      defn: Definition
    ): ValidationState = {
      checkSequence(actions)((s, action) => s.checkAction(action, defn))
    }

    def checkExample(
      example: Example,
    ): ValidationState = {
      stepIf(example.nonEmpty) { st =>
        val Example(_, _, givens, whens, thens, buts, _, _) = example
        st.checkSequence(givens) { (state, givenClause) =>
          state.checkSequence(givenClause.scenario){ (state, ls) =>
            state
              .checkNonEmptyValue(ls, "Given Scenario", example, MissingWarning)
          }.checkNonEmpty(
              givenClause.scenario, "Givens", example, MissingWarning
            )
        }
        .checkSequence(whens){ (st, when) =>
          st.checkExpression(when.condition, example)
        }
        .checkNonEmpty(thens, "Thens", example, required = true)
        .checkActions(thens.map(_.action),example)
        .checkActions(buts.map(_.action),example)
        .checkDescription(example)
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
      args: ArgList,
      defn: Definition
    ): ValidationState = {
      checkArgList(args, defn)
      .checkPathRef[Function](pathId,defn) {
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
            case _ => s
          }
      }
    }

    def checkExpressions(
      expressions: Seq[Expression],
      defn: Definition
    ): ValidationState = {
      expressions.foldLeft(this) { (st, expr) =>
        st.checkExpression(expr, defn)
      }
    }

    def checkExpression(
      expression: Expression,
      defn: Definition
    ): ValidationState =
      expression match {
        case ValueExpression(_, path)  =>
          checkPathRef[Field](path,defn)()
        case GroupExpression(_, expressions) =>
          checkSequence(expressions) {
            (st, expr) => st.checkExpression(expr, defn)
          }
        case FunctionCallExpression(loc, pathId, arguments) =>
          checkFunctionCall(loc, pathId, arguments, defn)
        case ArithmeticOperator(loc, op, operands) =>
          check(
            op.nonEmpty,
            "Operator is empty in abstract binary operator",
            Error,
            loc
          ).checkExpressions(operands, defn)
        case Comparison(_, _, arg1, arg2) =>
          checkExpression(arg1, defn)
            .checkExpression(arg2, defn)
        case AggregateConstructionExpression(_, typeRef, args) =>
          checkRef[Type](typeRef, defn)
            .checkArgList(args, defn)
        case EntityIdExpression(_, entityRef) =>
          checkRef[Entity](entityRef, defn)
        case Ternary(_, condition, expr1, expr2) =>
          checkExpression(condition, defn)
            .checkExpression(expr1, defn)
            .checkExpression(expr2, defn)
        case NotCondition(_, cond1) =>
          checkExpression(cond1, defn)
        case condition: MultiCondition =>
          checkExpressions(condition.conditions, defn)
        case _ =>
          this // not of interest
    }


    @tailrec private def getPathIdType(
      id: PathIdentifier,
      parents: Seq[Definition] = parents
    ): Option[TypeExpression] = {
      if (id.value.isEmpty) {
        None
      } else {
        val newParents: Seq[Definition] =
          if (id.value.exists(_.isEmpty)) {
            resolvePath(id, parents)
          } else {
            resolvePathFromSymTab[Definition](id)
          }
        val candidate = newParents.headOption match {
          case None => None
          case Some(f: Function) => f.output
          case Some(t: Type) => Some(t.typ)
          case Some(f: Field) => Some(f.typeEx)
          case Some(s: State) => Some(s.typeEx)
          case Some(p: Pipe) => Some(p.transmitType.getOrElse(Abstract(id.loc)))
          case Some(in: Inlet) => Some(in.type_)
          case Some(out: Outlet) => Some(out.type_)
          case Some(_) => None
        }
        candidate match {
          case Some(TypeRef(_, pid)) =>
            getPathIdType(pid, newParents)
          case Some(other: TypeExpression) => Some(other)
          case None => None
        }
      }
    }

    def isAssignmentCompatible(
      typeEx1: Option[TypeExpression],
      typeEx2: Option[TypeExpression]
    ): Boolean = {
      typeEx1 match {
        case None => false
        case Some(ty1) =>
          typeEx2 match {
            case None => false
            case Some(ty2) =>
              ty1.isAssignmentCompatible(ty2)
          }
      }
    }

    def getExpressionType(expr: Expression): Option[TypeExpression] = {
      expr match {
        case ne: NumericExpression =>
          ne match {
            case LiteralInteger(loc, _) => Some(Integer(loc))
            case LiteralDecimal(loc, _) => Some(Decimal(loc))
            case ArithmeticOperator(loc, _, _) => Some(Number(loc))
          }
        case cond: Condition => Some(Bool(cond.loc))
        case EntityIdExpression(loc, entityId) => Some(UniqueId(loc,entityId))
        case ValueExpression(_, path) => getPathIdType(path)
        case ae: ArbitraryExpression => Some(Abstract(ae.loc))
        case ao: ArbitraryOperator => Some(Abstract(ao.loc))
        case FunctionCallExpression(_, name, _) => getPathIdType(name)
        case GroupExpression(loc, expressions) =>
          // the type of a group is the last expression but it could be empty
          expressions.lastOption match {
            case None => Some(Abstract(loc))
            case Some(expr) => getExpressionType(expr)
          }
        case UndefinedExpression(loc) => Some(Abstract(loc))
        case AggregateConstructionExpression(_, typRef, _) =>
          getPathIdType(typRef.id)
        case Ternary(loc, _, expr1, expr2) =>
          val expr1Ty = getExpressionType(expr1)
          val expr2Ty = getExpressionType(expr2)
          if (isAssignmentCompatible(expr1Ty, expr2Ty)) {
            expr1Ty
          } else {
            addError(loc,
            s"""Ternary expressions must be assignment compatible but:
                 |  ${expr1.format} and
                 |  ${expr2.format}
                 |are incompatible
                 |""".stripMargin
            )
            None
          }
      }
    }

    def checkAssignmentCompatability(
      path: PathIdentifier,
      expr: Expression
    ): ValidationState = {
      val pidType = getPathIdType(path, parents)
      val exprType = getExpressionType(expr)
      if (!isAssignmentCompatible(pidType, exprType)) {
        addError(path.loc,
          s"""Setting a value requires assignment compatibility, but field:
             |  ${path.format} (${pidType.map(_.format).getOrElse("<not found>")})
             |  is not assignment compatible with expression:
             |  ${expr.format} (${exprType.map(_.format).getOrElse("<not found>")})
             |""".stripMargin)
      } else { this }
    }

    def checkArgList(
      arguments: ArgList,
      defn: Definition
    ): ValidationState = {
      arguments.args.values.foldLeft(this) { (st, arg) =>
        st.checkExpression(arg, defn)
      }
    }

    def checkMessageConstructor(
      messageConstructor: MessageConstructor,
      defn: Definition
    ): ValidationState = {
      val id = messageConstructor.msg.id
      val kind = messageConstructor.msg.messageKind.kind
      checkPathRef[Type](id,defn, Some(kind)) { (state, _, id, _, defn, _) =>
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
      state.captureHierarchy(parents)
      container match {
        case typ: Type          => openType(state, parents, typ)
        case function: Function =>
          state.checkDefinition(container, function)
        case onClause: OnClause =>
          state.checkIf(onClause.msg.nonEmpty) { st =>
            st.checkMessageRef(onClause.msg, container, onClause.msg.messageKind)
          }
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
      parents: Seq[Definition],
      typeDef: Type
    ): ValidationState = {
      state.checkDefinition(parents.head, typeDef)
        .check(
        typeDef.id.value.head.isUpper,
        s"${typeDef.identify} should start with a capital letter",
        StyleWarning,
        typeDef.loc
      ).checkIf(!typeDef.typ.isContainer) { vs =>
        vs.captureHierarchy(typeDef +: parents)
        vs.checkTypeExpression(typeDef.typ, typeDef)
        vs.captureHierarchy(parents)
      }
    }

    def openAdaptation(
      state: ValidationState,
      container: ParentDefOf[Definition],
      adaptation: AdaptorDefinition
    ): ValidationState = {
      state.checkDefinition(container, adaptation)
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
      state
        .checkDefinition(container, processor)
        .checkProcessorKind(processor)
    }


    def doDefinition(
      state: ValidationState,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): ValidationState = {
      //
      val parent = parents.head
      // basic validation applicable to all definitions
      val s1 = state.checkDefinition(parent, definition)
      // Capture current parse state including now the definition as the
      // top element of the parent stack
      state.captureHierarchy(definition +: parents)
      definition match {
        case example: Example => s1.checkDefinition(parent, example)
            .checkExample(example).checkDescription(example)
        case invariant: Invariant => s1.checkDefinition(parent, invariant)
            .checkOption(
              invariant.expression,
              "condition",
              invariant
            ) { (st, expr) =>st.checkExpression(expr, definition) }
            .checkDescription(invariant)
        case cca: CommandCommandA8n => s1.checkDefinition(parents.head, cca)
            .checkPathRef[Command](cca.messageRef.id, definition)()
            .checkPathRef[Command](cca.command.id, definition)()
            .checkDescription(cca)
        case eca: EventCommandA8n => s1.checkDefinition(parent, eca)
            .checkPathRef[Event](eca.messageRef.id, definition)()
            .checkPathRef[Command](eca.command.id, definition)()
        case eaa: EventActionA8n => s1.checkDefinition(parent, eaa)
            .checkPathRef[Event](eaa.messageRef.id, definition)()
            .checkActions(eaa.actions, definition)
        case field: Field =>
          s1.addIf(field.id.value.matches("^[^a-z].*"))(
              Message(field.id.loc,
                "Field names should begin with a lower case letter",
                StyleWarning
              )
            )
            .checkTypeExpression(field.typeEx, definition)
            .checkDescription(field)
        case h: Handler =>
          s1.checkOption(h.applicability, "applicability", h) { (st, pid) =>
            st.checkPathRef[Entity & Projection](pid.id, definition)()
          }.checkDescription(h)
        case inlet @ Inlet(_,_,typeRef,entityRef,_,_) =>
          s1.checkRef[Type](typeRef, definition)
            .checkOption(
              entityRef, "entity reference", inlet
            ) { (st,er) => st.checkRef[Entity](er, definition) }
            .checkDescription(inlet)
        case ij: InletJoint =>
          s1.checkPathRef[Pipe](ij.pipe.id, definition)()
            .checkPathRef[Inlet](ij.inletRef.id, definition)()
            .checkDescription(ij)
        case outlet @ Outlet(_,_,typeRef,entityRef, _,_) =>
          s1.checkRef[Type](typeRef, definition)
            .checkOption(
              entityRef, "entity reference", outlet
            ) { (st,er) => st.checkRef[Entity](er, definition) }
            .checkDescription(outlet)
        case oj: OutletJoint =>
          s1.checkPathRef[Pipe](oj.pipe.id, definition)()
            .checkPathRef[Outlet](oj.outletRef.id, definition)()
            .checkDescription(oj)
        case s: Story =>
          s1.checkNonEmptyValue(s.role, "role", s, Error, required = true)
            .checkNonEmptyValue(s.benefit, "benefit", s, Error, required = true)
            .checkNonEmptyValue(
              s.capability,
              "capability",
              s,
              Error,
              required = true
            ).checkNonEmpty(s.shownBy, "shownBy", s, StyleWarning)
            .checkSequence(s.implementedBy) { (st, pid) =>
              st.checkRef[Domain](pid, definition)
            }.checkDescription(s)
        case p: Pipe =>
          s1.checkDefinition(parent, p)
            .checkOption(p.transmitType, "transmit type", p) { (st, typeRef) =>
              st.checkPathRef[Type](typeRef.id, p)()
            }.checkDescription(p)
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
      state.captureHierarchy(parents)
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
