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

import java.util.regex.PatternSyntaxException
import scala.annotation.unused
import scala.collection.mutable.ListBuffer
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal
import scala.util.matching.Regex

/** Validates an AST */
object Validation {

  def validate(
    root: ParentDefOf[Definition],
    commonOptions: CommonOptions = CommonOptions()
  ): ValidationMessages = {
    val symTab = SymbolTable(root)
    val state = ValidationState(symTab, commonOptions)
    val result = try {
      val folder = new ValidationFolder
      val s1 = Folding.foldAround(state, root, folder)
      checkOverloads(symTab, s1)
    } catch {
      case NonFatal(xcptn) =>
      state.add(ValidationMessage(0->0,
          s"Exception Occurred: $xcptn", SevereError
        ))
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

  sealed trait ValidationMessageKind {
    def isSevereError: Boolean = false

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

    override def compare(that: ValidationMessage): Int = this.loc
      .compare(that.loc)
  }

  type ValidationMessages = List[ValidationMessage]

  val NoValidationMessages: List[ValidationMessage] = List
    .empty[ValidationMessage]

  case class ValidationState(
    symbolTable: SymbolTable,
    commonOptions: CommonOptions = CommonOptions())
      extends Folding.State[ValidationState] {

    private val msgs: ListBuffer[ValidationMessage] = ListBuffer
      .empty[ValidationMessage]

    def messages: ValidationMessages = msgs.toList

    def step(f: ValidationState => ValidationState): ValidationState = f(this)

    def parentOf(
      definition: Definition
    ): Container[Definition] = {
      symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
    }

    def isReportMissingWarnings: Boolean = commonOptions.showMissingWarnings

    def isReportStyleWarnings: Boolean = commonOptions.showStyleWarnings

    def lookup[T <: Definition: ClassTag](
      id: Seq[String]
    ): List[T] = { symbolTable.lookup[T](id) }

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
          } else { this }
        case MissingWarning =>
          if (isReportMissingWarnings) {
            msgs += msg
            this
          } else { this }
        case _ =>
          msgs += msg
          this
      }
    }

    private val vowels: Regex = "[aAeEiIoOuU]".r
    def article(thing: String): String = {
      val article = if (vowels.matches(thing.substring(0, 1))) "an" else "a"
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
      } catch {
        case x: PatternSyntaxException =>
          add(ValidationMessage(p.loc, x.getMessage))
      }
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
        ).checkTypeExpression(field.typeEx, field).checkDescription(field)
      }
    }

    def checkMapping[TD <: Definition](
      definition: TD,
      mapping: AST.Mapping
    ): ValidationState = {
      this.checkTypeExpression(mapping.from, definition)
        .checkTypeExpression(mapping.to, definition)
    }

    def checkTypeExpression[TD <: Definition](
      typ: TypeExpression,
      definition: TD
    ): ValidationState = {
      typ match {
        case p @ Pattern(_, _)       => checkPattern(p)
        case UniqueId(_, entityName) => this.checkPathRef[Entity](entityName)()
        case _: AST.PredefinedType   => this
        case AST.TypeRef(_, id: PathIdentifier) => checkPathRef[Type](id)()
        case Optional(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case OneOrMore(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case ZeroOrMore(_, typex: TypeExpression) =>
          checkTypeExpression(typex, definition)
        case Enumeration(_, enumerators) =>
          checkEnumeration(definition, enumerators)
        case alt: Alternation => checkAlternation(definition, alt)
        case agg: Aggregation => checkAggregation(definition, agg)
        case mt: MessageType  => checkMessageType(definition, mt)
        case mapping: Mapping => checkMapping(definition, mapping)
        case rt: RangeType    => checkRangeType(definition, rt)
        case ReferenceType(_, entity: EntityRef) => this
            .checkRef[Entity](entity)
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
      symbolTable.lookupSymbol[Type](ref.id.value) match {
        case Nil => add(ValidationMessage(
            ref.id.loc,
            s"${ref.identify} is not defined but should be ${article(kind.kind)} type",
            Error
          ))
        case (d, _) :: Nil => d match {
            case Type(_, _, typ, _, _) => typ match {
                case MessageType(_, mk, _) => check(
                    mk == kind,
                    s"'${ref.identify} should be ${article(kind.kind)} type" +
                      s" but is ${article(mk.kind)} type instead",
                    Error,
                    ref.id.loc
                  )
                case te: TypeExpression => add(ValidationMessage(
                    ref.id.loc,
                    s"'${ref.identify} should reference ${article(kind.kind)} type but is a ${AST
                      .kind(te)} type instead",
                    Error
                  ))
              }
            case _ => add(ValidationMessage(
                ref.id.loc,
                s"${ref.identify} was expected to be ${article(kind.kind)} type but is ${article(AST.kind(d))} instead",
                Error
              ))
          }
        case (d, optT) :: tail =>
          handleMultipleResultsCase[Type](ref.id, d, optT, tail)
        case _ => this
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

    private def handleMultipleResultsCase[T <: Definition: ClassTag](
      id: PathIdentifier,
      d: Definition,
      optT: Option[T],
      tail: List[(Definition, Option[T])]
    ): ValidationState = {
      // Handle domain, context, entity same name
      val tc = classTag[T].runtimeClass
      val definitions = d :: tail.map { case (definition, _) => definition }
      val types = (optT :: tail.map { case (_, t) => t }) collect {
        case Some(tpe) => tpe
      }
      val exactlyOneMatch = types.count(_.getClass == tc) == 1
      val allDifferent = definitions.map(AST.kind).distinct.sizeIs ==
        definitions.size
      if (exactlyOneMatch && allDifferent) { this }
      else if (!d.isImplicit) {
        add(ValidationMessage(
          id.loc,
          s"""'${id.format}' is not uniquely defined.
             |Definitions are:
             |${formatDefinitions(definitions)}""".stripMargin,
          Error
        ))
      } else { this }
    }

    def checkPathRef[T <: Definition: ClassTag](
      id: PathIdentifier
    )(validator: SingleMatchValidationFunction =
        defaultSingleMatchValidationFunction
    ): ValidationState = {
      if (id.value.nonEmpty) {
        val tc = classTag[T].runtimeClass
        symbolTable.lookupSymbol[T](id.value) match {
          case Nil => add(ValidationMessage(
              id.loc,
              s"'${id.format}' is not defined but should be ${article(tc.getSimpleName)}",
              Error
            ))
          // Single match, defer to validation function
          case (d, optT) :: Nil => validator(this, tc, id, d.getClass, d, optT)
          // Too many matches / non-unique
          case (d, optT) :: tail =>
            handleMultipleResultsCase[T](id, d, optT, tail)
        }
      } else { this }
    }

    def checkRef[T <: Definition: ClassTag](
      reference: Reference[T]
    ): ValidationState = { checkPathRef[T](reference.id)() }

    def checkRef[T <: Definition: ClassTag](
      reference: Option[Reference[T]]
    ): ValidationState = {
      if (reference.nonEmpty) { checkPathRef[T](reference.get.id)() }
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
      kind: ValidationMessageKind = Error,
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
      kind: ValidationMessageKind = Error,
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
      val matches = result.lookup[Definition](path)
      if (matches.isEmpty) {
        result = result.add(ValidationMessage(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!",
          SevereError
        ))
      } else if (matches.sizeIs >= 2) {
        val parentGroups = matches.groupBy(result.symbolTable.parentOf(_))
        parentGroups.get(Option(parent)) match {
          case Some(head :: tail) if tail.nonEmpty =>
            result = result.add(ValidationMessage(
              head.id.loc,
              s"${definition.identify} has same name as other definitions in ${parent.identifyWithLoc}:  " +
                tail.map(x => x.identifyWithLoc).mkString(",  "),
              Warning
            ))
          case Some(head :: tail) if tail.isEmpty =>
            result = result.add(ValidationMessage(
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

    def checkAction(action: Action): ValidationState = {
      action match {
        case SetAction(_, path, value, _) => this.checkPathRef[Field](path)()
            .checkExpression(value)
        case PublishAction(_, msg, pipeRef, _) => this
            .checkMessageConstructor(msg).checkRef[Pipe](pipeRef)
        case FunctionCallAction(loc, funcId, args, _) =>
          val ref = FunctionRef(loc, funcId)
          this.checkRef[Function](ref).checkArgList(args, funcId)
        case BecomeAction(_, entity, handler, _) => this
            .checkRef[Entity](entity).checkRef[Handler](handler)
        case MorphAction(_, entity, entityState, _) => this
            .checkRef[Entity](entity).checkRef[State](entityState)
        case TellAction(_, msg, entity, _) => this.checkRef[Entity](entity)
            .checkMessageConstructor(msg)
        case AskAction(_, entity, msg, _) => this.checkRef[Entity](entity)
            .checkMessageConstructor(msg)
        case ReplyAction(_, msg, _) => this.checkMessageConstructor(msg)
        case CompoundAction(loc, actions, _) =>
          val vs = this.check(
            actions.nonEmpty,
            "Compound action is empty",
            MissingWarning,
            loc
          )
          actions.foldLeft(vs) { (s, action) => s.checkAction(action) }

        case ArbitraryAction(loc, what, _) => this.check(
            what.nonEmpty,
            "arbitrary action is empty so specifies nothing",
            MissingWarning,
            loc
          )
      }
    }

    def checkActions(actions: Seq[Action]): ValidationState = {
      actions.foldLeft(this)((s, a) => s.checkAction(a))
    }

    def checkExample(example: Example): ValidationState = {
      if (example.nonEmpty) {
        example.givens.foldLeft(this) { (state, givenClause) =>
          val s = state.checkNonEmpty(
            givenClause.scenario,
            "Givens",
            example,
            MissingWarning
          )
          givenClause.scenario.foldLeft(s) { (state, ls) =>
            state
              .checkNonEmptyValue(ls, "Given String", example, MissingWarning)
          }
        }.step { state: ValidationState =>
          example.whens.foldLeft(state) { (state, whenClause) =>
            state.checkExpression(whenClause.condition)
          }
        }.checkNonEmpty(example.thens, "Thens", example, required = true).step {
          state: ValidationState =>
            example.thens.foldLeft(state) { (state, thenClause) =>
              state.checkAction(thenClause.action)
            }
        }.step { state: ValidationState =>
          example.buts.foldLeft(state) { (state, butClause) =>
            state.checkAction(butClause.action)
          }
        }.checkDescription(example)
      } else this
    }

    def checkExamples(examples: Seq[Example]): ValidationState = {
      examples.foldLeft(this) { (next, example) => next.checkExample(example) }
    }

    def checkFunctionCall(
      loc: Location,
      pathId: PathIdentifier,
      args: ArgList
    ): ValidationState = {
      checkPathRef[Function](pathId) {
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
                  .format}. Expected ${paramNames.size}, got ${argNames.size}",
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

    def checkExpression(expression: Expression): ValidationState = {
      expression match {
        case ValueCondition(_, path)  => checkPathRef[Field](path)()
        case GroupExpression(_, expr) => checkExpression(expr)
        case FunctionCallExpression(loc, pathId, arguments) =>
          checkFunctionCall(loc, pathId, arguments)
        case ArithmeticOperator(loc, op, operands) =>
          val s1 = check(
            op.nonEmpty,
            "Operator is empty in abstract binary operator",
            Error,
            loc
          )
          operands.foldLeft(s1) { (st, operand) => st.checkExpression(operand) }
        case Comparison(_, _, arg1, arg2) => checkExpression(arg1)
            .checkExpression(arg2)
        case _: Expression => this // everything else doesn't need validation
      }
    }

    def getFieldType(path: PathIdentifier): Option[TypeExpression] = {
      val results = symbolTable.lookup[Definition](path.value)
      if (results.size == 1) {
        results.head match {
          case f: Field => Option(f.typeEx)
          case t: Type  => Option(t.typ)
          case _        => None
        }
      } else { None }
    }

    def checkArgList(
      @unused
      args: ArgList,
      @unused
      function: PathIdentifier
    ): ValidationState = this // TODO Validate ArgLists

    def checkMessageConstructor(
      messageConstructor: MessageConstructor
    ): ValidationState = {
      val id = messageConstructor.msg.id
      checkSymbolLookup(id.value) { () =>
        add(ValidationMessage(
          id.loc,
          s"'${id.format}' is not defined but should be a message type",
          Error
        ))
      } { d: Definition =>
        d match {
          case Type(_, _, typ, _, _) => typ match {
              case mt: MessageType =>
                val names = messageConstructor.args.args.keys.map(_.value).toSeq
                val unset = mt.fields.filterNot { fName =>
                  names.contains(fName.id.value)
                }
                if (unset.nonEmpty) {
                  unset.filterNot(_.isImplicit).foldLeft(this) {
                    (next, field) =>
                      next.add(ValidationMessage(
                        messageConstructor.msg.loc,
                        s"${field.identify} was not set in message constructor"
                      ))
                  }
                } else {
                  mt.fields.foldLeft(this) { (next, _: Field) =>
                    // val fromType = messageConstructor.args.args
                    next
                  }
                  // mt.fields.zip(names)
                }
              case te: TypeExpression => add(ValidationMessage(
                  id.loc,
                  s"'${id.format}' should reference a message type but is a ${AST
                    .kind(te)} type instead.",
                  Error
                ))
            }
          case _ => add(ValidationMessage(
              id.loc,
              s"'${id.format}' was expected to be a message type but is ${article(AST.kind(d))} instead",
              Error
            ))
        }
      } { (d: Definition, tail) =>
        add(ValidationMessage(
          id.loc,
          s"ambiguous assignment from ${d.identify} which has ${tail.size} other definitions"
        ))
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
      container match {
        case typ: Type          => openType(state, parents.head, typ)
        case function: Function => openFunction(state, parents.head, function)
        case entity: Entity     => openEntity(state, parents.head, entity)
        case onClause: OnClause => state
            .checkMessageRef(onClause.msg, onClause.msg.messageKind)
        case processor: Processor =>
          openProcessor(state, parents.head, processor)
        case story: Story       => openStory(state, parents.head, story)
        case sagaStep: SagaStep => openSagaStep(state, parents.head, sagaStep)
        case context: Context   => openContext(state, parents.head, context)
        case include: Include   => openInclude(state, include)
        case adaptation: AdaptorDefinition =>
          openAdaptation(state, parents.head, adaptation)
        case _: RootContainer =>
          // we don't validate root containers
          state
        case domain: Domain =>
          val parent = parents.headOption.getOrElse(RootContainer(Seq(domain)))
          state.checkParentDefOf(parent, container)
        case container: ParentDefOf[Definition] =>
          // Adaptor, Plant, State, Saga, Handler
          state.checkParentDefOf(parents.head, container)
      }
    }

    def doDefinition(
      state: ValidationState,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): ValidationState = {
      definition match {
        case example: Example => state.checkDefinition(parents.head, example)
            .checkExample(example)
        case _: Field             => state // handled by Type/State
        case invariant: Invariant => doInvariant(state, parents.head, invariant)
        case definition: Definition => // generic case
          // Pipe, Joint, Inlet, Outlet, Term
          state.checkDefinition(parents.head, definition)
            .checkDescription(definition)
      }
    }

    def closeContainer(
      state: ValidationState,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): ValidationState = {
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
          next.checkTypeExpression(state.typeEx, state)
        }.addIf(entity.handlers.isEmpty && !entity.isEmpty) {
          ValidationMessage(
            entity.loc,
            s"${entity.identify} must define a handler"
          )
        }.addIf(
          entity.handlers.nonEmpty && entity.handlers.forall(_.clauses.isEmpty)
        ) {
          ValidationMessage(
            entity.loc,
            s"${entity.identify} has only empty handlers",
            MissingWarning
          )
        }.addIf(
          entity.hasOption[EntityFiniteStateMachine] && entity.states.sizeIs < 2
        ) {
          ValidationMessage(
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
      ).checkTypeExpression(typeDef.typ, container)
    }

    def doInvariant(
      state: ValidationState,
      container: ParentDefOf[Definition],
      invariant: Invariant
    ): ValidationState = {
      state.checkDefinition(container, invariant).checkNonEmptyValue(
        invariant.expression,
        "Condition",
        invariant,
        MissingWarning
      ).checkExpression(invariant.expression).checkDescription(invariant)
    }

    def openAdaptation(
      state: ValidationState,
      container: ParentDefOf[Definition],
      adaptation: AdaptorDefinition
    ): ValidationState = {
      adaptation match {
        case eaa: EventActionA8n => state.checkDefinition(container, adaptation)
            .checkRef[Type](eaa.messageRef).checkActions(eaa.actions)
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
      state.checkDefinition(container, function).checkTypeExpression(
        function.input.getOrElse(Nothing(function.loc)),
        function
      ).checkTypeExpression(
        function.output.getOrElse(Nothing(function.loc)),
        function
      ).checkExamples(function.examples)
    }

  }
}
