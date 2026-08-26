/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.RuleId
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

  /** Resolve a path to a definition of the EXPECTED kind, or to nothing.
    *
    * **This used to `asInstanceOf[T]` with no `ClassTag`, and that is a defect worth remembering.**
    * `T` erases, so the cast always "succeeded" here and handed back a definition of the WRONG kind
    * typed as `T`. Nothing failed at this line; the `ClassCastException` surfaced far away, at
    * whichever caller first touched a `T`-specific member — and only for callers that touch one, so
    * the same mistyped value crashed one model and passed silently through another. That made the
    * failure depend on which check ran first, which is exactly what riddl-generator reported: a
    * clean diagnosis followed by a stack trace that swallowed every remaining error in the model.
    *
    * The type test is `isInstance`-based (via the `ClassTag`), so a legitimate SUBTYPE still
    * resolves — `resolvePath[Processor[?]]` on an `Entity` is a match, as it must be.
    *
    * **Returning `None` loses no diagnostic.** A path that resolves to the wrong kind is already
    * reported by `ResolutionPass` ("… resolved to Field 'x' …, but a Processor was expected"),
    * which runs BEFORE validation. This function's job is to hand validation a value it can trust,
    * and the honest answer for a mismatch is that there isn't one.
    */
  @inline
  def resolvePath[T <: Definition: ClassTag](
    pid: PathIdentifier,
    parents: Parents
  ): Option[T] = {
    pathIdToDefinition(pid, parents).collect { case definition: T => definition }
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
          s"e.g. 'EnclosingScope.${tc.getSimpleName}Name'.",
        ruleId = Some(RuleId.EmptyPath)
      )
      Option.empty[T]
    else resolvePath[T](pid, parents)
  }

  def checkRef[T <: Definition: ClassTag](
    reference: Reference[T],
    parents: Parents
  ): Option[T] = {
    val resolved = checkPathRef[T](reference.pathId, parents)
    // Every `TypeRef` in the model funnels through here, whether its caller went via
    // `checkTypeRef` or called `checkRef[Type]` directly (invariant `requires` does). Putting the
    // truthfulness check at this one point covers both without threading it through call sites,
    // and cannot double-report: one call, one check. `MessageRef` is an `AggregateRef`, NOT a
    // `TypeRef`, so on-clauses are untouched -- `checkMessageRef` already owns their kind rule.
    reference match
      case tr: TypeRef =>
        resolved.foreach {
          case t: Type => checkTypeRefKeyword(tr, t)
          case _       => ()
        }
      case _ => ()
    resolved
  }

  /** A reference's prefix must name what the target was DECLARED as (Reid, 2026-08-24).
    *
    * *"I required that kind-of-thing prefix for all references SPECIFICALLY to avoid ambiguity and
    * to aid comprehension of the model when read. Using `type` undoes that requirement."*
    *
    * **Keyed off the DECLARATION, never off what the reference carries.** An alternation declared
    * `type OrderEvent is one of { ... }` genuinely IS a type even though every member is an event,
    * so `is type OrderEvent` stays correct. Asking what the reference *carries* would redden all
    * 230 such references in reactive-bbq alone and be wrong about every one; asking what the target
    * was *declared* flags only the references that point straight at a message.
    *
    * **An omitted prefix is indistinguishable from `type`**, because `TypeRef.keyword` defaults to
    * `"type"` -- there is no "was it written?" bit in the AST. Reid ruled that the bare form is
    * therefore held to the same standard: the prefix is required and must be truthful. The message
    * below says "names it as a type" rather than "is prefixed type", because the latter is a lie to
    * an author who wrote no prefix at all.
    */
  private def checkTypeRefKeyword(ref: TypeRef, target: Type): Unit = {
    val declared: String = target.typEx match
      case auc: AggregateUseCaseTypeExpression => auc.usecase.useCase.toLowerCase
      case _                                   => "type"
    val written = ref.keyword.toLowerCase
    if written != declared then
      val name = ref.pathId.format
      messages.addError(
        ref.loc,
        s"'$name' is declared ${article(declared)}, but this reference names it as " +
          s"${article(written)}",
        suggestion = s"Write '$declared $name'. A reference's prefix must name what the target " +
          "was declared as; an omitted prefix means 'type', which is correct only when the " +
          "target really is a 'type'.",
        ruleId = Some(RuleId.WrongKeyword)
      )
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
          s"Name a message type here, e.g. '${kinds.headOption.map(_.useCase).getOrElse("command")} DoSomething'.",
        ruleId = Some(RuleId.MessageRefEmpty)
      )
      this
    } else {
      checkRefAndExamine[Type](ref, parents) { (definition: Definition) =>
        definition match {
          case Type(_, _, typ, _) =>
            typ match {
              case AggregateUseCaseTypeExpression(_, mk, _, _) =>
                check(
                  kinds.contains(mk),
                  s"'${ref.identify} should be one of these message types: ${kinds.mkString(",")}" +
                    s" but is ${article(mk.useCase)} type instead",
                  Error,
                  ref.pathId.loc,
                  suggestion =
                    s"Reference a type declared as one of: ${kinds.map(_.useCase).mkString(", ")}; " +
                      s"or redeclare the target type with one of those aggregate use cases.",
                  ruleId = Some(RuleId.MessageRefWrongKind)
                )
              case te: TypeExpression =>
                messages.addError(
                  ref.pathId.loc,
                  s"'${ref.identify} should reference one of these types: ${kinds.mkString(",")} but is a ${errorDescription(te)} type " + s"instead",
                  suggestion =
                    s"Point the reference at a type declared as one of: ${kinds.map(_.useCase).mkString(", ")} " +
                      s"(e.g. 'type X = ${kinds.headOption.map(_.useCase).getOrElse("command")} { ??? }').",
                  ruleId = Some(RuleId.MessageRefWrongKind)
                )
            }
          case _ =>
            messages.addError(
              ref.pathId.loc,
              s"${ref.identify} was expected to be one of these types; ${kinds.mkString(",")}, but is ${article(definition.kind)} instead",
              suggestion =
                s"Reference a message type (${kinds.map(_.useCase).mkString(", ")}) rather than ${article(definition.kind)}.",
              ruleId = Some(RuleId.MessageRefNotAMessage)
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

  /** `ruleId` is REQUIRED here for the same reason it is on the `add*` helpers: a diagnostic must
    * name the rule it belongs to, or a consumer cannot filter, suppress or fix it without matching
    * prose. This helper was MISSED when the ids were first threaded -- it builds a `Message`
    * directly rather than going through `Accumulator.add*` -- and 68 diagnostics were emitted with
    * a null rule as a result. Found by running `validate --json` and seeing the null, not by
    * reading the code.
    */
  def check(
    predicate: Boolean = true,
    message: => String,
    kind: KindOfMessage,
    loc: At,
    suggestion: => String = "",
    ruleId: Option[RuleId]
  ): this.type = {
    if !predicate then
      messages.add(Message(loc, message, kind, suggestion = suggestion, ruleId = ruleId))
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
            "Give the same-named fields a single consistent type, or rename them so each name maps to one type.",
          ruleId = Some(RuleId.FieldOverloadedTypes)
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
              "Rename one of the definitions so the same name does not refer to different kinds of definition.",
            ruleId = Some(RuleId.TypeOverloaded)
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
                  s"Rename or merge the duplicate $name definitions so each name is unique within its scope.",
                ruleId = Some(RuleId.OverloadedDefinitions)
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
      suggestion = s"Give this ${d.kind} a name of at least $min characters.",
      ruleId = Some(RuleId.IdentifierEmpty)
    )
    if !d.isAnonymous && d.id.value.length < min then {
      messages.addStyle(
        d.id.loc,
        s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min",
        suggestion =
          s"Rename '${d.id.value}' to a more descriptive identifier of at least $min characters.",
        ruleId = Some(RuleId.NameTooShort)
      )
    }
    this
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false,
    ruleId: Option[RuleId] = Some(RuleId.EmptyContent)
  ): this.type = {
    check(
      value.nonEmpty,
      message = s"$name in ${thing.identify} ${if required then "must" else "should"} not be empty",
      kind,
      thing.errorLoc,
      suggestion =
        s"Provide a value for '$name' in ${thing.identify}, or remove the empty declaration.",
      ruleId = ruleId
    )
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    loc: At,
    kind: KindOfMessage,
    required: Boolean,
    // NOT defaulted: Scala forbids two overloads that both carry default arguments, and the
    // no-`loc` overload is the one that has them. Callers of this form name their rule.
    ruleId: Option[RuleId]
  ): this.type = {
    check(
      value.nonEmpty,
      message =
        s"$name in ${thing.identify} at $loc ${if required then "must" else "should"} not be empty",
      kind,
      thing.errorLoc,
      suggestion =
        s"Provide a value for '$name' in ${thing.identify}, or remove the empty declaration.",
      ruleId = ruleId
    )
  }

  def checkNonEmpty(
    list: Seq[?],
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false,
    ruleId: Option[RuleId] = Some(RuleId.EmptyContent)
  ): this.type = {
    check(
      list.nonEmpty,
      s"$name in ${thing.identify} ${if required then "must" else "should"} not be empty",
      kind,
      thing.errorLoc,
      suggestion = s"Add at least one $name to ${thing.identify}, or remove the empty declaration.",
      ruleId = ruleId
    )
  }

  def checkNonEmpty(
    list: Seq[?],
    name: String,
    thing: Definition,
    loc: At,
    kind: KindOfMessage,
    required: Boolean,
    // NOT defaulted: Scala forbids two overloads that both carry default arguments, and the
    // no-`loc` overload is the one that has them. Callers of this form name their rule.
    ruleId: Option[RuleId]
  ): this.type = {
    check(
      list.nonEmpty,
      s"$name in ${thing.identify} at $loc ${if required then "must" else "should"} not be empty",
      kind,
      loc,
      suggestion = s"Add at least one $name to ${thing.identify}, or remove the empty declaration.",
      ruleId = ruleId
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
                      "instead of referencing across the context boundary directly.",
                  ruleId = Some(RuleId.CrossContextReference)
                )
              )
            else ()
          case None => ()
        }
      case None => ()
    }
  }
}
