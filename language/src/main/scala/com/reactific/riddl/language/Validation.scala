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

import scala.collection.mutable

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
    root: ParentDefOf[Definition],
    commonOptions: CommonOptions = CommonOptions())
      extends Folding.MessagesState[ValidationState] {

    var parents: Seq[ParentDefOf[Definition]] = Seq.empty[ParentDefOf[Definition]]
    var definition: Definition = root

    def captureContext(cont: Definition, pars: Seq[ParentDefOf[Definition]]): Unit = {
      definition = cont
      parents = pars
    }

    def step(f: ValidationState => ValidationState): ValidationState = f(this)

    /*
    def parentOf(
      definition: Definition
    ): Container[Definition] = {
      symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
    }
     */

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
      val s1: ValidationState = mt.fields.headOption match {
        case Some(sender) => this.check(
            sender.id.value == "sender",
            "The first field of a message type must be the implicit 'sender'",
            SevereError,
            mt.loc
          ).step { state =>
            sender.typeEx match {
              case ReferenceType(loc, entityRef) => state.check(
                  entityRef.id.isEmpty,
                  "The implicit 'sender' must not have a path in its entity reference",
                  SevereError,
                  loc
                )
              case other: TypeExpression => state.addError(
                  mt.loc,
                  s"The implicit 'sender must be Reference type, not $other"
                )
            }
          }
        case None => this
      }
      mt.fields.tail.foldLeft(s1) { case (state, field) =>
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
        case agg: Aggregation                   => checkAggregation(agg)
        case mt: MessageType                    => checkMessageType(mt)
        case alt: Alternation                   => checkAlternation(alt)
        case mapping: Mapping                   => checkMapping(mapping)
        case rt: RangeType                      => checkRangeType(rt)
        case p: Pattern                         => checkPattern(p)
        case TypeRef(_, id: PathIdentifier)     => checkPathRef[Type](id)()
        case Enumeration(_, enumerators)        => checkEnumeration(enumerators)
        case Optional(_, tye: TypeExpression)   => checkTypeExpression(tye)
        case OneOrMore(_, tye: TypeExpression)  => checkTypeExpression(tye)
        case ZeroOrMore(_, tye: TypeExpression) => checkTypeExpression(tye)
        case UniqueId(_, entityName)            =>
          this.checkPathRef[Entity](entityName)()
        case ReferenceType(_, entity: EntityRef) => this
            .checkRef[Entity](entity)
        case _: PredefinedType                  => this
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

    def checkMessageRef(ref: MessageRef, kind: MessageKind): ValidationState = {
      if (ref.isEmpty) {
        addError(ref.id.loc, s"${ref.identify} is empty")
      } else{
        checkPathRef(ref.id) { (state, _, _, _, defn, _) =>
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
                    article(kind.kind)} type but is a ${
                    AST.kind(te)} type instead")
            }
            case _ =>
              state.addError(
                ref.id.loc,
                s"${ref.identify} was expected to be ${article(kind.kind)} type but is ${
                  article(AST.kind(defn))} instead")
          }
        }
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

    /** Resolve a PathIdentifier If the path is already resolved or it has no
      * empty components then we can resolve it from the map or the symbol
      * table.
      *
      * @param pid
      *   The path to consider
      * @return
      *   Either an error or a definition
      */
    def resolvePath(
      pid: PathIdentifier,
    ): Option[Definition] = {
      val result = if (definition.id.value == "sender" && pid.value.isEmpty) {
        None
      } else {
        val stack = mutable.Stack.empty[ParentDefOf[Definition]]
        stack.pushAll(parents.reverse)
        pid.value.foldLeft(stack.headOption.asInstanceOf[Option[Definition]]) { (r, n) =>
          if (r.isEmpty) {
            None // propagate error condition
          } else if (n.isEmpty) {
            if (stack.nonEmpty) {
              val valueFound = stack.pop()
              Some(valueFound)
            } else {
              None // no way to pop an item of the stack so signal error
            }
          } else {
            r match {
              case None =>
                None // propagate error condition
              case Some(p) if p.isContainer =>
                val contents = p.asInstanceOf[Container[Definition]].contents
                contents.find(_.id.value == n) match {
                  case Some(q) if q.isContainer =>
                    // found the named item, put it on the stack and make it
                    // the latest result (r)
                    stack.push(q.asInstanceOf[ParentDefOf[Definition]])
                    Some(q)
                  case Some(_) =>
                    // Found an item but its not a container.
                    // This is an error because we were supposed to resolve
                    // the name
                  None
                  case None =>
                    None // propagate the error condition
                }
              case Some(_) => None // no container to resolve name into
            }
          }
        }
      }
      result
    }
    def checkPathRef[T <: Definition: ClassTag](
      pid: PathIdentifier,
    )(validator: SingleMatchValidationFunction =
      defaultSingleMatchValidationFunction
    ): ValidationState = {
      val tc = classTag[T].runtimeClass
      def notResolved(): Unit = {
        addError(
          pid.loc,
          s"Path '${pid.format}' was not resolved, in ${definition.kind} '" +
            definition.id.value +
            s"' but should be ${article(tc.getSimpleName)}"
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
        if (resolvePath(pid).isEmpty) {
          notResolved()
        }
        this
      }
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference[T]
    ): ValidationState = {
      checkPathRef[T](reference.id)() }

    def checkRef[T <: Definition: ClassTag](
      reference: Option[Reference[T]]
    ): ValidationState = {
      if (reference.nonEmpty) {
        checkPathRef[T](reference.get.id)() }
      else { this }
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
      value: TD
    ): ValidationState = {
      val description: Option[Description] = value.description
      if (description.isEmpty) {
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
        s"${container.identify} in ${parent.identify} must have content.",
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
      if (example.nonEmpty) {
        val Example(_, _, givens, whens, thens, buts, _, _) = example
        givens.foldLeft(this) { (state, givenClause) =>
            givenClause.scenario.foldLeft(state) { (state, ls) =>
            state
              .checkNonEmptyValue(ls, "Given Scenario", example, MissingWarning)
          }.checkNonEmpty(
            givenClause.scenario,
            "Givens",
            example,
            MissingWarning
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
      } else this
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
            case Function(_, fid, Some(Aggregation(_, fields)), _, _, _, _) =>
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
        case AggregateConstructionExpression(_, TypeRef(_,pid), args) =>
          checkPathRef(pid)()
            .checkArgList(args)
        case EntityIdExpression(_, EntityRef(_, pid)) =>
          checkPathRef(pid)()
        case FunctionCallAction(_, pid, args, _) =>
          checkPathRef(pid)()
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
      checkPathRef(id) { (state, _, id, _, defn, _) =>
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
      state.captureContext(container, parents)
      container match {
        case typ: Type          => openType(state, parents.head, typ)
        case function: Function => openFunction(state, parents.head, function)
        case onClause: OnClause =>
          if (!onClause.msg.isEmpty)
            state.checkMessageRef(onClause.msg, onClause.msg.messageKind)
          else
            state
        case st: State =>
          state.checkDefinition(parents.head, st)
            .checkTypeExpression(st.typeEx)
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
        .checkSequence(entity.states) { (next, state) =>
          next.checkTypeExpression(state.typeEx)
        }.addIf(entity.handlers.isEmpty && !entity.isEmpty) {
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
          entity.hasOption[EntityFiniteStateMachine] && entity.states.sizeIs < 2
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
      state.checkDefinition(container, typeDef).check(
        typeDef.id.value.head.isUpper,
        s"${typeDef.identify} should start with a capital letter",
        StyleWarning,
        typeDef.loc
      ).checkTypeExpression(typeDef.typ)
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
            s.checkRef[Entity](n.entity)
          }
          processor.outlets.foldLeft(s3) { (s, n) =>
            s.checkRef[Entity](n.entity)
          }
        }.step { s5 => s5.checkExamples(processor.examples) }
    }

    def openFunction[TCD <: ParentDefOf[Definition]](
      state: ValidationState,
      container: TCD,
      function: Function
    ): ValidationState = {
      state
        .checkDefinition(container, function)
        .checkTypeExpression(
          function.input.getOrElse(Nothing(function.loc))
      ).checkTypeExpression(
        function.output.getOrElse(Nothing(function.loc)),
      ).checkExamples(function.examples)
    }

    def doDefinition(
      state: ValidationState,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): ValidationState = {
      // Capture current parse state
      state.captureContext(definition, parents)
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
            .checkPathRef(cca.messageRef.id)().checkPathRef(cca.command.id)()
            .checkDescription(cca)
        case eca: EventCommandA8n => s1.checkDefinition(parent, eca)
            .checkPathRef(eca.messageRef.id)().checkPathRef(eca.command.id)()
            .checkDescription(eca)
        case eaa: EventActionA8n => s1.checkDefinition(parent, eaa)
            .checkPathRef(eaa.messageRef.id)().checkActions(eaa.actions)
            .checkExamples(eaa.examples).checkDescription(eaa)
        case field: Field =>
          s1.checkDefinition(parent, field)
            .checkTypeExpression(field.typeEx).checkDescription(field)
        case h: Handler =>
          val s2 = s1.checkDefinition(parent, h)
          h.applicability.fold(s2) { pid => s1.checkPathRef(pid)() }
            .checkDescription(h)
        case i: Inlet =>
          val s2 = s1.checkDefinition(parent, i).checkPathRef(i.type_.id)()
          i.entity.fold(s2) { er => s2.checkPathRef(er.id)() }
            .checkDescription(i)
        case ij: InletJoint => s1.checkDefinition(parent, ij)
            .checkPathRef(ij.pipe.id)().checkPathRef(ij.inletRef.id)()
            .checkDescription(ij)
        case o: Outlet =>
          val s2 = s1.checkDefinition(parent, o).checkPathRef(o.type_.id)()
          o.entity.fold(s2) { er => s2.checkPathRef(er.id)() }
            .checkDescription(o)
        case oj: OutletJoint => s1.checkDefinition(parent, oj)
            .checkPathRef(oj.pipe.id)().checkPathRef(oj.outletRef.id)()
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
          s.implementedBy.foldLeft(s2) { (st, pid) => st.checkPathRef(pid)() }
            .checkDescription(s)
        case p: Pipe =>
          val s2 = s1.checkDefinition(parent, p)
          p.transmitType.fold(s2) { typeRef => s1.checkPathRef(typeRef.id)() }
            .checkDescription(p)
        case oc: OnClause => s1.checkDefinition(parent, oc)
            .checkMessageRef(oc.msg, oc.msg.messageKind)
            .checkExamples(oc.examples).checkDescription(oc)
        case ai: AuthorInfo =>
          s1.checkNonEmptyValue(ai.name,"name", ai, Error, required=true)
            .checkNonEmptyValue(ai.email, "email", ai, Error, required=true)
            .checkDescription(ai)
        case t: Term =>
          s1.checkDefinition(parent, t)
            .checkDescription(t)
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
      state.captureContext(container, parents)
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
