/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.resolve.ResolutionOutput
import com.ossuminc.riddl.passes.symbols.SymbolsOutput
import com.ossuminc.riddl.utils.PlatformContext

import scala.reflect.{ClassTag, classTag}
import scala.util.matching.Regex

/** Validation infrastructure needed for all kinds of definition validation */
trait BasicValidation(using pc: PlatformContext) {

  def symbols: SymbolsOutput
  def resolution: ResolutionOutput
  protected def messages: Messages.Accumulator

  def parentOf(definition: Definition): Branch[?] = {
    symbols.parentOf(definition).getOrElse(Root.empty)
  }

  def parentsOf(definition: Definition): Parents = {
    symbols.parentsOf(definition)
  }

  def lookup[T <: Definition: ClassTag](id: Seq[String]): List[T] = {
    symbols.lookup[T](id)
  }

  def pathIdToDefinition(
    pid: PathIdentifier,
    parents: Parents
  ): Option[Definition] = {
    if pid.value.length == 1 then
      // Let's try the symbol table
      symbols.lookup[Definition](pid.value.reverse).headOption
    else
      parents.headOption.flatMap { (head: Branch[?]) =>
        resolution.refMap.definitionOf[Definition](pid, head)
      }
  }

  @inline
  def resolvePath[T <: Definition](
    pid: PathIdentifier,
    parents: Parents
  ): Option[T] = {
    pathIdToDefinition(pid, parents).map(_.asInstanceOf[T])
  }

  def checkPathRef[T <: Definition: ClassTag](
    pid: PathIdentifier,
    parents: Parents
  ): Option[T] = {
    if pid.value.isEmpty then
      val tc = classTag[T].runtimeClass
      val message =
        s"An empty path cannot be resolved to ${article(tc.getSimpleName)}"
      messages.addError(pid.loc, message)
      Option.empty[T]
    else resolvePath[T](pid, parents)
  }

  def checkRef[T <: Definition: ClassTag](
    reference: Reference[T],
    parents: Parents
  ): Option[T] = {
    checkPathRef[T](reference.pathId, parents)
  }

  def checkRefAndExamine[T <: Definition: ClassTag](
    reference: Reference[T],
    parents: Parents
  )(examiner: T => Unit): this.type = {
    checkPathRef[T](reference.pathId, parents).foreach { (resolved: T) =>
      examiner(resolved)
    }
    this
  }

  private def checkMaybeRef[T <: Definition: ClassTag](
    reference: Option[Reference[T]],
    parents: Parents
  ): Option[T] = {
    reference.flatMap { ref =>
      checkPathRef[T](ref.pathId, parents)
    }
  }

  def checkTypeRef(
    ref: TypeRef,
    parents: Parents
  ): Option[Type] = {
    checkRef[Type](ref, parents)
  }

  def checkMessageRef(
    ref: MessageRef,
    parents: Parents,
    kinds: Seq[AggregateUseCase]
  ): this.type = {
    if ref.isEmpty then {
      messages.addError(ref.pathId.loc, s"${ref.identify} is empty")
      this
    } else {
      checkRefAndExamine[Type](ref, parents) { (definition: Definition) =>
        definition match {
          case Type(_, _, typ, _) =>
            typ match {
              case AggregateUseCaseTypeExpression(_, mk, _) =>
                check(
                  kinds.contains(mk),
                  s"'${ref.identify} should be one of these message types: ${kinds.mkString(",")}" +
                    s" but is ${article(mk.useCase)} type instead",
                  Error,
                  ref.pathId.loc
                )
              case te: TypeExpression =>
                messages.addError(
                  ref.pathId.loc,
                  s"'${ref.identify} should reference one of these types: ${kinds.mkString(",")} but is a ${errorDescription(te)} type " + s"instead"
                )
            }
          case _ =>
            messages.addError(
              ref.pathId.loc,
              s"${ref.identify} was expected to be one of these types; ${kinds.mkString(",")}, but is ${article(definition.kind)} instead"
            )
        }
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
    if !predicate then messages.add(Message(loc, message, kind))
    this
  }

  def checkSequence[A](elements: Seq[A])(check: A => Unit): this.type = {
    elements.foreach(check(_))
    this
  }

  def checkOverloads(): this.type = {
    def reportNonDistinctTypes(
      typeNames: Seq[String],
      locations: Seq[String],
      defList: Seq[Definition]
    ): Unit =
      val distinct = typeNames.distinct
      val typeLoc = typeNames
        .zip(locations)
        .map { (name, loc) => s"$name at $loc" }
        .mkString(",\n  ")
      if distinct.size > 1 then
        val message = defList.head.identify + " is overloaded with " +
          distinct.size.toString + " distinct field types:\n  " + typeLoc
        messages.addError(defList.head.errorLoc, message)
      end if

    end reportNonDistinctTypes

    symbols.foreachOverloadedSymbol { (defs: Seq[Seq[Definition]]) =>
      this.checkSequence(defs) { defList =>
        val map = defList.groupBy(_.kind)
        if map.size > 1 then
          val tailStr: String = defList.tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
          messages.addError(
            defList.head.errorLoc,
            s"${defList.head.identify} is overloaded with ${map.size} kinds:\n  $tailStr"
          )
        else if map.size == 1 then
          map.head._1 match {
            case name: String if name == Field.getClass.getSimpleName =>
              val fields = map.head._2.asInstanceOf[Seq[Field]]
              val types = fields.map(field => errorDescription(field.typeEx))
              val locations = fields.map(_.loc.format)
              reportNonDistinctTypes(types, locations, defList)
            case name: String if name == Type.getClass.getSimpleName =>
              val typeDefs = map.head._2.asInstanceOf[Seq[Type]]
              val types = typeDefs.map(type_ => errorDescription(type_.typEx))
              val locations = typeDefs.map(_.loc.format)
              reportNonDistinctTypes(types, locations, defList)
            case x: String =>
          }
        end if
      }
    }
    this
  }

  def checkIdentifierLength[T <: WithIdentifier](d: T, min: Int = 3): this.type = {
    check(
      d.id.nonEmpty | d.isAnonymous,
      "Identifiers must not be empty",
      Error,
      d.errorLoc
    )
    if !d.isAnonymous && d.id.value.length < min then {
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
      message = s"$name in ${thing.identify} ${if required then "must" else "should"} not be empty",
      kind,
      thing.errorLoc
    )
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    loc: At,
    kind: KindOfMessage,
    required: Boolean
  ): this.type = {
    check(
      value.nonEmpty,
      message =
        s"$name in ${thing.identify} at $loc ${if required then "must" else "should"} not be empty",
      kind,
      thing.errorLoc
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
      thing.errorLoc
    )
  }

  def checkNonEmpty(
    list: Seq[?],
    name: String,
    thing: Definition,
    loc: At,
    kind: KindOfMessage,
    required: Boolean
  ): this.type = {
    check(
      list.nonEmpty,
      s"$name in ${thing.identify} at $loc ${if required then "must" else "should"} not be empty",
      kind,
      loc
    )
  }

  def checkCrossContextReference(
    ref: PathIdentifier,
    definition: Definition,
    container: Definition
  ): Unit = {
    symbols.contextOf(definition) match {
      case Some(definitionContext) =>
        symbols.contextOf(container) match {
          case Some(containerContext) =>
            if definitionContext != containerContext then
              val formatted = ref.format
              messages.add(
                style(
                  s"Path Identifier $formatted at ${ref.loc.format} references ${definition.identify} in " +
                    s"${definitionContext.identify} but occurs in ${container.identify} in ${containerContext.identify}." +
                    " Cross-context references are ill-advised as they lead to model confusion and violate " +
                    "the 'bounded' aspect of bounded contexts",
                  ref.loc.extend(formatted.length)
                )
              )
            else ()
          case None => ()
        }
      case None => ()
    }
  }
}
