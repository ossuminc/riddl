/*
 * Copyright 2019-2026 Ossum Inc.
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
      messages.addError(
        pid.loc,
        message,
        suggestion = s"Provide a non-empty path that names ${article(tc.getSimpleName)}, " +
          s"e.g. 'EnclosingScope.${tc.getSimpleName}Name'."
      )
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
      messages.addError(
        ref.pathId.loc,
        s"${ref.identify} is empty",
        suggestion =
          s"Name a message type here, e.g. '${kinds.headOption.map(_.useCase).getOrElse("command")} DoSomething'."
      )
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
                  ref.pathId.loc,
                  suggestion =
                    s"Reference a type declared as one of: ${kinds.map(_.useCase).mkString(", ")}; " +
                      s"or redeclare the target type with one of those aggregate use cases."
                )
              case te: TypeExpression =>
                messages.addError(
                  ref.pathId.loc,
                  s"'${ref.identify} should reference one of these types: ${kinds.mkString(",")} but is a ${errorDescription(te)} type " + s"instead",
                  suggestion =
                    s"Point the reference at a type declared as one of: ${kinds.map(_.useCase).mkString(", ")} " +
                      s"(e.g. 'type X = ${kinds.headOption.map(_.useCase).getOrElse("command")} { ??? }')."
                )
            }
          case _ =>
            messages.addError(
              ref.pathId.loc,
              s"${ref.identify} was expected to be one of these types; ${kinds.mkString(",")}, but is ${article(definition.kind)} instead",
              suggestion =
                s"Reference a message type (${kinds.map(_.useCase).mkString(", ")}) rather than ${article(definition.kind)}."
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
    loc: At,
    suggestion: => String = ""
  ): this.type = {
    if !predicate then messages.add(Message(loc, message, kind, suggestion = suggestion))
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
        messages.addWarning(
          defList.head.loc,
          message,
          suggestion =
            "Give the same-named fields a single consistent type, or rename them so each name maps to one type."
        )
      end if

    end reportNonDistinctTypes

    symbols.foreachOverloadedSymbol { (defs: Seq[Seq[Definition]]) =>
      this.checkSequence(defs) { defList =>
        val map =
          defList
            .filterNot {
              case g: Group  => true
              case i: Input  => true
              case o: Output => true
              case _         => false
            }
            .groupBy(_.kind)
        if map.size > 1 then
          val tailStr: String = defList.map(d => d.identifyWithLoc).mkString(s",\n  ")
          messages.addWarning(
            defList.head.errorLoc,
            s"${defList.head.identify} is overloaded with ${map.size} kinds:\n  $tailStr",
            suggestion =
              "Rename one of the definitions so the same name does not refer to different kinds of definition."
          )
        else if map.size == 1 then
          map.head._1 match {
            case name: String if name == Field.getClass.getSimpleName =>
              // Fields are fully scoped by their containing type,
              // so same-named fields in different records within
              // the same context are never ambiguous
              ()
            case name: String if name == Type.getClass.getSimpleName =>
              val typeDefs = map.head._2.asInstanceOf[Seq[Type]]
              val types = typeDefs.map(type_ => errorDescription(type_.typEx))
              val locations = typeDefs.map(_.loc.format)
              reportNonDistinctTypes(types, locations, defList)
            case name: String if name == "Domain" || name == "Context" =>
              val definitions = map.head._2
              val typeLoc = definitions
                .map { defn => s"${defn.identify} at ${defn.loc.format}" }
                .mkString(",\n  ")
              val message = defList.head.identify + " is overloaded with " +
                definitions.size.toString + s" distinct $name definitions:\n  " + typeLoc
              messages.addError(
                defList.head.errorLoc,
                message,
                suggestion =
                  s"Rename or merge the duplicate $name definitions so each name is unique within its scope."
              )
            case _ => ()
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
      d.errorLoc,
      suggestion = s"Give this ${d.kind} a name of at least $min characters."
    )
    if !d.isAnonymous && d.id.value.length < min then {
      messages.addStyle(
        d.id.loc,
        s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min",
        suggestion =
          s"Rename '${d.id.value}' to a more descriptive identifier of at least $min characters."
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
      thing.errorLoc,
      suggestion =
        s"Provide a value for '$name' in ${thing.identify}, or remove the empty declaration."
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
      thing.errorLoc,
      suggestion =
        s"Provide a value for '$name' in ${thing.identify}, or remove the empty declaration."
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
      thing.errorLoc,
      suggestion = s"Add at least one $name to ${thing.identify}, or remove the empty declaration."
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
      loc,
      suggestion = s"Add at least one $name to ${thing.identify}, or remove the empty declaration."
    )
  }

  def checkCrossContextReference(
    ref: PathIdentifier,
    definition: Definition,
    container: Definition,
    parents: Parents
  ): Unit = {
    // Adaptors exist to handle cross-context communication, so
    // cross-context references within them are expected and valid.
    if parents.exists(_.isInstanceOf[Adaptor]) then return
    symbols.contextOf(definition) match {
      case Some(definitionContext) =>
        symbols.contextOf(container) match {
          case Some(containerContext) =>
            if definitionContext != containerContext then
              val formatted = ref.format
              messages.add(
                warning(
                  s"Path Identifier $formatted at ${ref.loc.format} references ${definition.identify} in " +
                    s"${definitionContext.identify} but occurs in ${container.identify} in ${containerContext.identify}." +
                    " Cross-context references violate the 'bounded' aspect of bounded contexts and lead to" +
                    " model confusion. Instead, use an Adaptor to translate message types between contexts" +
                    " or a Streamlet pipeline (Source/Sink/Flow) to decouple the communication",
                  ref.loc.extend(formatted.length),
                  suggestion =
                    s"Add an Adaptor in ${containerContext.identify} to translate messages from " +
                      s"${definitionContext.identify}, or connect the contexts with a Streamlet (Source/Sink/Flow) " +
                      "instead of referencing across the context boundary directly."
                )
              )
            else ()
          case None => ()
        }
      case None => ()
    }
  }
}
