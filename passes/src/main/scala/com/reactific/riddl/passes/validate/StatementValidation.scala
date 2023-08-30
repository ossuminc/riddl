/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At

import scala.collection.mutable

/** Unit Tests For ExampleValidationState */
trait StatementValidation extends TypeValidation {

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

  def checkStatements(
    statements: Seq[Statement],
    parents: Seq[Definition]
  ): this.type =
    // TODO: Write me
    this

  private def checkArgumentValues(
    arguments: ArgumentValues,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    arguments.args.values.foreach { (arg: Value) =>
      checkValue(arg, defn, parents)
    }
    this
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
            messages.addError(
              pid.loc,
              s"'${pid.format}' should reference a message type but is a ${errorDescription(te)} type instead."
            )
        }
      case _ =>
        messages.addError(
          pid.loc,
          s"'${pid.format}' was expected to be a message type but is ${article(defn.kind)} instead"
        )
    }
    this
  }

  private def checkFunctionCall(
    loc: At,
    pathId: PathIdentifier,
    args: ArgumentValues,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    checkArgumentValues(args, defn, parents)
    val maybeType: Option[Function] = checkPathRef[Function](pathId, defn, parents.toSeq)
    maybeType match {
      case None =>
        error(s"PathId ${pathId.format} does not reference a Function", pathId.loc)
      case Some(defn) =>
        defn match {
          case f: Function if f.input.nonEmpty =>
            val fid = f.id
            val fields = f.input.get.fields
            val paramNames = fields.map(_.id.value)
            val argNames = args.args.keys.map(_.value).toSeq
            val s1 = check(
              argNames.size == paramNames.size,
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
    }
    this
  }

  private def checkValues(
    values: Seq[Value],
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    values.foldLeft[this.type](this) { (st: this.type, expr) =>
      st.checkValue(expr, defn, parents)
    }
  }

  def checkValue(
    value: Value,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    value match {
      case FunctionCallValue(loc, funcRef, args) =>
        checkFunctionCall(loc, funcRef.pathId, args, defn, parents)
      case FunctionCallCondition(loc, funcRef, args) =>
        checkFunctionCall(loc, funcRef.pathId, args, defn, parents)
      case ComputedValue(loc, op, operands) =>
        check(
          op.nonEmpty,
          "Operator is empty in computed value",
          Error,
          loc
        ).checkValues(operands, defn, parents)
      case Comparison(loc, comp, arg1, arg2) =>
        checkValue(arg1, defn, parents)
          .checkValue(arg2, defn, parents)
          .check(
            arg1.valueType.isAssignmentCompatible(arg2.valueType),
            s"Incompatible expression types in ${comp.format} expression",
            Error,
            loc
          )
      case NotCondition(_, cond1) => checkValue(cond1, defn, parents)
      case condition: MultiCondition =>
        checkValues(condition.conditions, defn, parents)
      case _ => // not of interest
    }
    this
  }

  def validateStatement(
    statement: Statement,
    parents: Seq[Definition]
  ): this.type = {
    statement match {
      case SetStatement(_, pathId, value) =>
        checkPathRef[Field](pathId, statement, parents)
        checkValue(value, statement, parents)
        checkAssignmentCompatability(pathId, value, parents)
      case ReturnStatement(_, value) =>
        checkValue(value, statement, parents)
      case s @ SendAction(_, msg, outlet) =>
        checkMessageConstructor(msg, statement, parents)
        checkRef[Portlet](outlet, statement, parents)
        addSend(s, parents)
      case TellAction(_, msg, entityRef) =>
        checkMessageConstructor(msg, statement, parents)
        checkRef[Processor[?, ?]](entityRef, statement, parents)
      case FunctionCallStatement(_, funcId, args) =>
        checkPathRef[Function](funcId, statement, parents)
        checkArgumentValues(args, statement, parents)
      case BecomeStatement(loc, er, hr) =>
        checkRef[Entity](er, parents.head, parents.tail) match {
          case Some(entity) =>
            checkRef[Handler](hr, parents.head, parents.tail) match {
              case Some(handler) =>
                check(
                  entity.handlers.contains(handler),
                  s"Handler '${handler.id.format}' is not associated with Entity '${entity.id.format}",
                  Messages.Error,
                  handler.loc
                )
                check(
                  parents.exists(_.isInstanceOf[Entity]),
                  "Become statement is only allowed within an Entity's Handler",
                  Messages.Error,
                  loc
                )
              case None =>
                messages.error(s"Path Id'${hr.pathId} does not refer to a Handler", hr.loc)
            }
          case None =>
            messages.error(s"PathId '${er.pathId}' does not refer to an Entity'", er.loc)
        }
      case MorphStatement(_, entity, state, value) =>
        val maybeEntity = checkRef[Entity](entity, statement, parents)
        val maybeState = checkRef[State](state, statement, parents)
        checkValue(value, statement, parents)
        val maybeExprType = getValueType(value, parents)
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
                      if !this.isAssignmentCompatible(
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
      case ArbitraryStatement(loc, what) =>
        check(
          what.nonEmpty,
          "arbitrary statement is empty providing no behavior specification value",
          MissingWarning,
          loc
        )
      case _: ErrorStatement =>
    }
    this
  }

  private def checkAssignmentCompatability(
    path: PathIdentifier,
    value: Value,
    parents: Seq[Definition]
  ): this.type = {
    val pidType = getPathIdType(path, parents)
    val exprType = getValueType(value, parents)
    if !isAssignmentCompatible(pidType, exprType) then {
      messages.addError(
        path.loc,
        s"""Setting a value requires assignment compatibility, but field:
           |  ${path.format} (${pidType.map(_.format).getOrElse("<not found>")})
           |is not assignment compatible with expression:
           |  ${value.format} (${exprType
            .map(_.format)
            .getOrElse("<not found>")})
           |""".stripMargin
      )
    }
    this
  }
}
