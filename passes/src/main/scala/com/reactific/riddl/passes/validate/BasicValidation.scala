/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.{AST, Messages}
import com.reactific.riddl.passes.resolve.ResolutionOutput
import com.reactific.riddl.passes.symbols.SymbolsOutput

import scala.annotation.tailrec
import scala.reflect.{ClassTag, classTag}
import scala.util.matching.Regex

/** Unit Tests For BasicValidationState */
trait BasicValidation {

  def symbols: SymbolsOutput
  def resolution: ResolutionOutput
  def messages: Messages.Accumulator

  def parentOf(definition: Definition): Container[Definition] = {
    symbols.parentOf(definition).getOrElse(RootContainer.empty)
  }

  def lookup[T <: Definition : ClassTag](id: Seq[String]): List[T] = {
    symbols.lookup[T](id)
  }

  def pathIdToDefinition(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Option[Definition] = {
    resolution.refMap.definitionOf(pid, parents.head)
  }

  @inline def resolvePath[T <: Definition](
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Option[T] = {
    pathIdToDefinition(pid, parents).map(_.asInstanceOf[T])
  }

  def resolvePidRelativeTo[T <: Definition](
    pid: PathIdentifier,
    relativeTo: Definition
  ): Option[T] = {
    val parents = relativeTo +: symbols.parentsOf(relativeTo)
    resolvePath[T](pid, parents)
  }

  def checkPathRef[T <: Definition : ClassTag](
    pid: PathIdentifier,
    container: Definition,
    parents: Seq[Definition]
  ): Option[T] = {
    val tc = classTag[T].runtimeClass
    if pid.value.isEmpty then {
      val message =
        s"An empty path cannot be resolved to ${article(tc.getSimpleName)}"
      messages.addError(pid.loc, message)
      Option.empty[T]
    } else {
      val pars = if parents.head != container then container +: parents else parents
      resolvePath[T](pid, pars)
    }
  }

  def checkRef[T <: Definition : ClassTag](
    reference: Reference[T],
    definition: Definition,
    parents: Seq[Definition],
  ): Option[T] = {
    checkPathRef[T](reference.pathId, definition, parents)
  }

  def checkRefAndExamine[T <: Definition : ClassTag](
    reference: Reference[T],
    defn: Definition,
    parents: Seq[Definition]
  )(examiner: T => Unit): this.type = {
    checkPathRef[T](reference.pathId, defn, parents).map { (resolved: T) => examiner(resolved) }
    this
  }

  def checkMaybeRef[T <: Definition : ClassTag](
    reference: Option[Reference[T]],
    definition: Definition,
    parents: Seq[Definition]
  ): Option[T] = {
    reference.flatMap { ref =>
      checkPathRef[T](ref.pathId, definition, parents)
    }
  }

  def checkMessageRef(
    ref: MessageRef,
    topDef: Definition,
    parents: Seq[Definition],
    kind: AggregateUseCase
  ): this.type = {
    if ref.isEmpty then {
      messages.addError(ref.pathId.loc, s"${ref.identify} is empty")
      this
    } else {
      checkRefAndExamine[Type](ref, topDef, parents) { (definition: Definition) =>
        definition match {
          case Type(_, _, typ, _, _) =>
            typ match {
              case AggregateUseCaseTypeExpression(_, mk, _) =>
                check(
                  mk == kind,
                  s"'${ref.identify} should be ${article(kind.kind)} type" +
                    s" but is ${article(mk.kind)} type instead",
                  Error,
                  ref.pathId.loc
                )
              case te: TypeExpression =>
                messages.addError(
                  ref.pathId.loc,
                  s"'${ref.identify} should reference ${article(kind.kind)} but is a ${
                    AST.errorDescription(te)
                  } type instead"
                )
            }
          case _ =>
            messages.addError(
              ref.pathId.loc,
              s"${ref.identify} was expected to be ${
                article(kind.kind)} type but is ${article(definition.kind)} instead"
            )
        }
      }
    }
  }

  @tailrec final def getPathIdType(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Option[TypeExpression] = {
    if pid.value.isEmpty then {
      None
    } else {
      val maybeDef: Option[Definition] = resolvePath(pid, parents)
      val candidate: Option[TypeExpression] = maybeDef.headOption match {
        case None => None
        case Some(f: Function) => f.output
        case Some(t: Type) => Some(t.typ)
        case Some(f: Field) => Some(f.typeEx)
        case Some(s: State) =>
          Some(AliasedTypeExpression(s.typ.loc, s.typ.pathId))
        case Some(Inlet(_, _, typ, _, _)) =>
          Some(AliasedTypeExpression(typ.loc, typ.pathId))
        case Some(Outlet(_, _, typ, _, _)) =>
          Some(AliasedTypeExpression(typ.loc, typ.pathId))
        case Some(connector: Connector) =>
          connector.flows
            .map(typeRef => AliasedTypeExpression(typeRef.loc, typeRef.pathId))
            .orElse(Option.empty[TypeExpression])
        case Some(streamlet: Streamlet) if streamlet.outlets.size == 1 =>
          resolvePath[Type](
            streamlet.outlets.head.type_.pathId,
            parents.toSeq
          )
            .map(_.typ)
        case Some(_) => Option.empty[TypeExpression]
      }
      candidate match {
        case Some(AliasedTypeExpression(_, pid)) =>
          getPathIdType(pid, maybeDef.toSeq)
        case Some(other: TypeExpression) => Some(other)
        case None => None
      }
    }
  }

  private val vowels: Regex = "[aAeEiIoOuU]".r

  def article(thing: String): String = {
    val article = if vowels.matches(thing.substring(0, 1)) then "an" else "a"
    s"$article $thing"
  }

  def check(
    predicate: Boolean = true,
    message: => String,
    kind: KindOfMessage,
    loc: At
  ): this.type = {
    if !predicate then
      messages.add(Message(loc, message, kind))
    this
  }

  def checkWhen(predicate: Boolean)(checker: () => Unit): this.type = {
    if predicate then checker()
    this
  }

  def checkSequence[A](elements: Seq[A])(check: (A) => Unit): this.type = {
    elements.foreach(check(_))
    this
  }

  def checkOverloads(): this.type = {
    symbols.foreachOverloadedSymbol { (defs: Seq[Seq[Definition]]) =>
      this.checkSequence(defs) { defs2 =>
        val first = defs2.head
        if defs2.sizeIs == 2 then {
          val last = defs2.last
          messages.addStyle(
            last.loc,
            s"${last.identify} overloads ${first.identifyWithLoc}"
          )
        } else {
          if defs2.sizeIs > 2 then {
            val tail = defs2.tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
            messages.addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
          }
        }
      }
    }
    this
  }


  def checkIdentifierLength[T <: Definition](d: T, min: Int = 3): this.type = {
    if d.id.value.nonEmpty && d.id.value.length < min then {
      messages.addStyle(
        d.id.loc,
        s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min"
      )
    }
    this
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false
  ): this.type = {
    check(
      value.nonEmpty,
      message =
        s"$name in ${thing.identify} ${if required then "must" else "should"} not be empty",
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
  ): this.type = {
    check(
      list.nonEmpty,
      s"$name in ${thing.identify} ${if required then "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }
}
