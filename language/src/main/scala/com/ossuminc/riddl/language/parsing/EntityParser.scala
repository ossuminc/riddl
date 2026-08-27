/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Parsing rules for entity definitions */
private[parsing] trait EntityParser {
  this: ProcessorParser & StreamingParser =>

  private def stateContent[u: P]: P[StateContents] =
    P(handler(StatementsSet.EntityStatements) | invariant | comment).asInstanceOf[P[StateContents]]

  private def stateContents[u: P]: P[Seq[StateContents]] =
    stateContent.rep(1)

  private def stateBody[u: P]: P[Seq[StateContents]] =
    P(is ~ open ~ (undefined(Seq.empty[StateContents]) | stateContents) ~ close)

  /** What introduces a state's record reference.
    *
    * `of` is the canonical 2.0 spelling — `state X of record R is { … }`. `is` was also accepted,
    * and since `is` is itself optional so was nothing at all, which left one keyword doing two jobs
    * in a single production: `stateBody` already uses `is` to introduce the BODY, exactly as every
    * other definition in the language does.
    *
    * Returns the location of the offending text when the spelling was NOT `of`, so the caller can
    * deprecate it. The `is` alternative still succeeds on empty input, so this accepts precisely
    * what it accepted before — nothing that parsed stops parsing.
    */
  private def stateRecordIntro[u: P]: P[Option[At]] =
    P(
      of.map(_ => Option.empty[At]) |
        (Index ~ is ~ Index).map { case (start, end) => Some(at(start, end)) }
    )

  def state[u: P]: P[State] = {
    P(
      Index ~ Keywords.maybeInitial ~ Keywords.state ~ identifier ~/ stateRecordIntro ~ recordRef ~/
        stateBody.? ~ withMetaData ~ Index
    )./.map { case (start, isInitial, id, notOf, typRef, body, descriptives, end) =>
      notOf.foreach { loc =>
        deprecation(
          loc,
          s"Use `of` to introduce a state's record reference: `state ${id.value} of " +
            s"${typRef.format} is { … }`. Introducing it with `is` (or with nothing) is deprecated " +
            "and will be removed in a future major version, because `is` introduces a definition's " +
            "BODY everywhere else in the language.",
          code = Option(RuleId.StateIsRecord),
          autoFixable = true
        )
      }
      State(
        at(start, end),
        id,
        typRef,
        // A state's first handler is its initial (live) one unless another is marked `initial`.
        markFirstHandlerInitial(body.getOrElse(Seq.empty)).toContents,
        descriptives.toContents,
        isInitial = isInitial
      )
    }
  }

  /** If no [[Handler]] in these contents is marked `initial`, mark the first (declaration order).
    */
  private def markFirstHandlerInitial(contents: Seq[StateContents]): Seq[StateContents] = {
    val handlers = contents.collect { case h: Handler => h }
    if handlers.isEmpty || handlers.exists(_.isInitial) then contents
    else {
      var done = false
      contents.map {
        case h: Handler if !done => done = true; h.copy(isInitial = true)
        case other               => other
      }
    }
  }

  /** Every `T` in `contents`, descending through `Include`/`BASTImport` wrappers.
    *
    * The wrappers' contents are already populated at this point (an include is parsed eagerly), and
    * this must agree with `entity.states` / `entity.handlers`, which use `filterThroughWrappers`.
    * When it did not, DEFAULTING counted an entity's states literally while VALIDATION counted them
    * through includes — so an entity with one inline state and one included state was defaulted as
    * single-state (auto-marking an entity-scope handler `initial`) and then validated as
    * multi-state. With the duplicate-`initial` guard removed, that surfaced as riddlc auto-marking
    * one handler and then reporting the author's OWN explicitly-marked handler as the duplicate.
    */
  private def throughWrappers[T <: RiddlValue: scala.reflect.ClassTag](
    contents: Seq[EntityContents]
  ): Seq[T] =
    val theClass = scala.reflect.classTag[T].runtimeClass
    def loop(items: Seq[RiddlValue]): Seq[T] = items.flatMap {
      case inc: Include[?]                            => loop(inc.contents.toSeq)
      case bi: BASTImport                             => loop(bi.contents.toSeq)
      case x if theClass.isAssignableFrom(x.getClass) => Seq(x.asInstanceOf[T])
      case _                                          => Seq.empty
    }
    loop(contents)
  end throughWrappers

  /** Supply the historical "first-declared is initial" default for an entity: mark the first
    * [[State]] if none is marked, and — only when the entity has a single state — mark the first
    * entity-scope [[Handler]] if none is marked. Refactor-safety comes from the explicit `initial`
    * keyword; this only preserves prior semantics for unmarked models.
    *
    * COUNTING sees through includes (so it agrees with validation); MARKING still only rewrites
    * this entity's own direct contents, since an included fragment is shared and must not be
    * rewritten on behalf of one includer.
    */
  private def defaultEntityInitials(contents: Seq[EntityContents]): Seq[EntityContents] = {
    val states = throughWrappers[State](contents)
    val withState =
      if states.isEmpty || states.exists(_.isInitial) then contents
      else {
        var done = false
        contents.map {
          case s: State if !done => done = true; s.copy(isInitial = true)
          case other             => other
        }
      }
    val handlers = throughWrappers[Handler](withState)
    val singleState = throughWrappers[State](withState).sizeIs == 1
    if singleState && handlers.nonEmpty && !handlers.exists(_.isInitial) then {
      var done = false
      withState.map {
        case h: Handler if !done => done = true; h.copy(isInitial = true)
        case other               => other
      }
    } else withState
  }

  private def entityInclude[u: P]: P[Include[EntityContents]] = {
    include[u, EntityContents]((p: P[?]) => entityDefinitions(using p.asInstanceOf[P[u]]))
  }

  private def entityDefinitions[u: P]: P[Seq[EntityContents]] = {
    P(
      processorDefinitionContents(StatementsSet.EntityStatements) | state | entityInclude
    ).asInstanceOf[P[EntityContents]]./.rep(1)
  }

  private def entityBody[u: P]: P[Seq[EntityContents]] = {
    P(
      undefined(Seq.empty[EntityContents])./ | entityDefinitions./
    )
  }

  /** Zero or more intention keywords immediately before `entity`, in ANY order.
    *
    * Consumes nothing unless one of the words is present; because the enclosing `entity` rule cuts
    * only after `Keywords.entity`, a prefix not actually followed by `entity` backtracks cleanly,
    * so no lookahead is needed. Same shape as `ContextParser.intentionPrefix`.
    *
    * Keywords are tried longest-first so `persistent` can never be matched as a prefix of a longer
    * word, and the result is sorted canonically -- ordering is a writing convenience, never a
    * structural difference.
    */
  private def entityIntentionPrefix[u: P]: P[Seq[EntityIntention]] =
    // Literals, not EntityIntention.keywords: StringIn is a macro and takes only constants.
    // EntityIntentionKeywordsTest pins the two lists together so they cannot drift.
    P(
      StringIn(
        "event-sourced",
        "consistent",
        "persistent",
        "aggregate",
        "available",
        "transient"
      ).!.rep(0)
    ).map(kws => EntityIntention.canonical(kws.flatMap(EntityIntention.fromKeyword)))

  /** The options these intentions replaced, mapped to their keyword. */
  private val deprecatedIntentionOptions: Map[String, EntityIntention] = Map(
    "event-sourced" -> EntityIntention.EventSourced,
    "value" -> EntityIntention.Persistent, // renamed: `value` said it less clearly
    "transient" -> EntityIntention.Transient,
    "aggregate" -> EntityIntention.Aggregate,
    "consistent" -> EntityIntention.Consistent,
    "available" -> EntityIntention.Available
  )

  /** Consume a deprecated `option` into the intention it became, dropping it from the metadata.
    *
    * Consuming rather than keeping it is what makes a round trip converge: the keyword prefix
    * already says it, so emitting both would duplicate it. If that leaves the `with { … }` block
    * empty, the block simply is not emitted, and the entity gets the ordinary "should have
    * metadata" nudge that any entity without metadata gets -- which after migration it genuinely
    * is. Same bargain as `prompt` -> `do`.
    */
  private def intentionsFromDeprecatedOptions(
    meta: Seq[MetaData]
  ): (Seq[MetaData], Seq[EntityIntention]) = {
    val found = meta.collect {
      case ov: OptionValue if deprecatedIntentionOptions.contains(ov.name) => ov
    }
    found.foreach { ov =>
      val intention = deprecatedIntentionOptions(ov.name)
      deprecation(
        ov.loc,
        s"'option ${ov.name}' is deprecated; write '${intention.keyword}' before 'entity' instead",
        code = Option(RuleId.EntityOptionToIntention),
        autoFixable = false
      )
    }
    val remaining = meta.filterNot(m => found.exists(_ eq m))
    remaining -> found.map(ov => deprecatedIntentionOptions(ov.name))
  }

  def entity[u: P]: P[Entity] = {
    P(
      Index ~ entityIntentionPrefix ~ Keywords.entity ~/ identifier ~ asShape ~ is ~ open ~/
        entityBody ~ close ~ withMetaData ~ Index
    )./ map { case (start, intentions, id, ascribed, contents, meta, end) =>
      checkForDuplicateIncludes(contents)
      val (remainingMeta, fromOptions) = intentionsFromDeprecatedOptions(meta)
      Entity(
        at(start, end),
        id,
        defaultEntityInitials(contents).toContents,
        ascribedShape = ascribed,
        intentions = EntityIntention.canonical(intentions ++ fromOptions),
        metadata = remainingMeta.toContents
      )
    }
  }
}
