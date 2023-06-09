/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At

import scala.collection.mutable

/** Unit Tests For ExampleValidationState */
trait ExampleValidation extends TypeValidation {

  protected val sends: mutable.HashMap[SendAction, Seq[Definition]] =
    mutable.HashMap
      .empty[SendAction, Seq[Definition]]

  private def addSend(
    send: SendAction,
    parents: Seq[Definition]
  ): this.type = {
    sends.put(send, parents)
    this
  }

  def checkExamples(
    examples: Seq[Example],
    parents: Seq[Definition]
  ): this.type = {
    examples.foldLeft[this.type](this) { (next, example) =>
      next.checkExample(example, parents)
    }
  }

  def checkExample(
    example: Example,
    parents: Seq[Definition]
  ): this.type = {
    val Example(_, _, givens, whens, thens, buts, _, _) = example

    checkSequence(givens) { (givenClause: GivenClause) =>
      checkSequence(givenClause.scenario) { ls =>
        checkNonEmptyValue(ls, "Given Scenario", example, MissingWarning)
      }.checkNonEmpty(givenClause.scenario, "Givens", example, MissingWarning)
    }
    checkSequence(whens) { (when: WhenClause) =>
      checkExpression(when.condition, example, parents)
    }
    if example.id.nonEmpty then {
      checkNonEmpty(thens, "Thens", example, required = true)
    }
    checkActions(thens.map(_.action), example, parents)
    checkActions(buts.map(_.action), example, parents)
    checkDescription(example)
  }

  private def checkArgList(
    arguments: ArgList,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    arguments.args.values.foldLeft[this.type](this) { (st: this.type, arg) =>
      st.checkExpression(arg, defn, parents)
    }
  }

  private def checkMessageConstructor(
    messageConstructor: MessageConstructor,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    val pid = messageConstructor.msg.pathId
    resolvePath[Type](pid, parents) match {
      case Some(typ) =>
        typ.typ match {
          case mt: AggregateUseCaseTypeExpression =>
            val names = messageConstructor.args.args.keys.map(_.value).toSeq
            val unset = mt.fields.filterNot { fName =>
              names.contains(fName.id.value)
            }
            if unset.nonEmpty then {
              unset.filterNot(_.isImplicit).foreach { field =>
                messages.addError(
                  messageConstructor.loc,
                  s"${field.identify} was not set in message constructor"
                )
              }
            }
          case te: TypeExpression =>
            messages.addError(pid.loc,
              s"'${pid.format}' should reference a message type but is a ${errorDescription(te)} type instead."
            )
        }
      case _ =>
        messages.addError(pid.loc,
          s"'${pid.format}' was expected to be a message type but is ${article(defn.kind)} instead"
        )
    }
    this
  }

  private def checkFunctionCall(
    loc: At,
    pathId: PathIdentifier,
    args: ArgList,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    checkArgList(args, defn, parents)
      .checkPathRef[Function](pathId, defn, parents.toSeq)
    defn match {
      case f: Function if f.input.nonEmpty =>
        val fid = f.id
        val fields = f.input.get.fields
        val paramNames = fields.map(_.id.value)
        val argNames = args.args.keys.map(_.value).toSeq
        val s1 = check(argNames.size == paramNames.size,
          s"Wrong number of arguments for ${fid.format}. Expected ${paramNames.size}, but got ${argNames.size}",
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
      case _ =>
    }
    this
  }

  private def checkExpressions(
    expressions: Seq[Expression],
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    expressions.foldLeft[this.type](this) { (st: this.type, expr) =>
      st.checkExpression(expr, defn, parents)
    }
  }

  def checkExpression(
    expression: Expression,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    expression match {
      case ValueOperator(_, path) =>
        checkPathRef[Field](path, defn, parents)
      case GroupExpression(_, expressions) =>
        checkSequence(expressions) { expr =>
          checkExpression(expr, defn, parents)
        }
      case FunctionCallExpression(loc, pathId, arguments) =>
        checkFunctionCall(loc, pathId, arguments, defn, parents)
      case ArithmeticOperator(loc, op, operands) =>
        check(
          op.nonEmpty,
          "Operator is empty in abstract binary operator",
          Error,
          loc
        ).checkExpressions(operands, defn, parents)
      case Comparison(loc, comp, arg1, arg2) =>
        checkExpression(arg1, defn, parents)
          .checkExpression(arg2, defn, parents)
          .check(
            arg1.expressionType.isAssignmentCompatible(arg2.expressionType),
            s"Incompatible expression types in ${comp.format} expression",
            Error,
            loc
          )
      case AggregateConstructionExpression(_, pid, args) =>
        checkPathRef[Type](pid, defn, parents)
        checkArgList(args, defn, parents)
      case NewEntityIdOperator(_, entityRef) =>
        checkPathRef[Entity](entityRef, defn, parents)
      case Ternary(loc, condition, expr1, expr2) =>
        checkExpression(condition, defn, parents)
          .checkExpression(expr1, defn, parents)
          .checkExpression(expr2, defn, parents)
          .check(
            expr1.expressionType.isAssignmentCompatible(expr2.expressionType),
            "Incompatible expression types in Ternary expression",
            Error,
            loc
          )

      case NotCondition(_, cond1) => checkExpression(cond1, defn, parents)
      case condition: MultiCondition =>
        checkExpressions(condition.conditions, defn, parents)
      case _ => // not of interest
    }
    this
  }

  private def checkActions(
    actions: Seq[Action],
    example: Example,
    parents: Seq[Definition]
  ): this.type = {
    checkSequence(actions) { action =>
      checkAction(action, example, example +: parents)
    }
    this
  }

  private def checkAction(
    action: Action,
    defn: Example,
    parents: Seq[Definition]
  ): this.type = {
    action match {
      case _: ErrorAction =>
      case AssignAction(_, path, value) =>
        checkPathRef[Field](path, defn, parents)
        checkExpression(value, defn, parents)
        checkAssignmentCompatability(path, value, parents)
      case AppendAction(_, value, path) =>
         checkExpression(value, defn, parents)
         checkPathRef[Field](path, defn, parents)
      case ReturnAction(_, value) =>
        checkExpression(value, defn, parents)
      case s @ SendAction(_, msg, outlet) =>
        checkMessageConstructor(msg, defn, parents)
        checkRef[Portlet](outlet, defn, parents)
        addSend(s, parents)
      case TellAction(_, msg, entityRef) =>
        checkMessageConstructor(msg, defn, parents)
        checkRef[Processor[?, ?]](entityRef, defn, parents)
      case FunctionCallAction(_, funcId, args) =>
        checkPathRef[Function](funcId, defn, parents)
        checkArgList(args, defn, parents)
      case BecomeAction(_, entity, handler) =>
        checkRef[Entity](entity, defn, parents)
        checkRef[Handler](handler, defn, parents)
      case MorphAction(_, entity, state, value) =>
        val maybeEntity = checkRef[Entity](entity, defn, parents)
        val maybeState = checkRef[State](state, defn, parents)
        checkExpression(value, defn, parents)
        val maybeExprType = getExpressionType(value, parents)
        if maybeExprType.isEmpty then {
          messages.addError(
            value.loc,
            s"Unable to determine type of expression ${value.format}"
          )
        } else {
          val exprType = maybeExprType.get
          maybeEntity.flatMap { entity =>
            maybeState
              .map { resolvedState =>
                if !entity.states.contains(resolvedState) then {
                  messages.addError(
                    state.loc,
                    s"${entity.identify} does not contain ${resolvedState.identify}"
                  )
                } else {
                  val pid = resolvedState.typ.pathId
                  val maybeType = resolvePidRelativeTo[Type](pid, resolvedState)
                    maybeType match {
                      case Some(typ) =>
                        if
                          !this.isAssignmentCompatible(
                            Some(typ.typ),
                            Some(exprType)
                          )
                        then {
                          messages.addError(
                            value.loc,
                            s"Morph value of type ${exprType.format} " +
                              s"cannot be assigned to ${resolvedState.identify} value of type ${typ.identify}"
                          )
                        }
                      case None =>
                    }
                  }
                }
            }
          }

      case CompoundAction(loc, actions) =>
        check(actions.nonEmpty, "Compound action is empty", MissingWarning, loc)
          .checkSequence(actions) { action =>
            checkAction(action, defn, parents)
          }
      case ArbitraryAction(loc, what) =>
        check(
          what.nonEmpty,
          "arbitrary action is empty providing no behavior specification value",
          MissingWarning,
          loc
        )
    }
    this
  }

  private def checkAssignmentCompatability(
    path: PathIdentifier,
    expr: Expression,
    parents: Seq[Definition]
  ): this.type = {
    val pidType = getPathIdType(path, parents)
    val exprType = getExpressionType(expr, parents)
    if !isAssignmentCompatible(pidType, exprType) then {
      messages.addError(
        path.loc,
        s"""Setting a value requires assignment compatibility, but field:
           |  ${path.format} (${pidType.map(_.format).getOrElse("<not found>")})
           |is not assignment compatible with expression:
           |  ${expr.format} (${exprType
            .map(_.format)
            .getOrElse("<not found>")})
           |""".stripMargin
      )
    }
    this
  }
}
