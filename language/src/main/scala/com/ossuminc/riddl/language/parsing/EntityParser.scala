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
          code = Option(Messages.DeprecationCode.StateIsRecord),
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

  /** Supply the historical "first-declared is initial" default for an entity: mark the first
    * [[State]] if none is marked, and — only when the entity has a single state — mark the first
    * entity-scope [[Handler]] if none is marked. Refactor-safety comes from the explicit `initial`
    * keyword; this only preserves prior semantics for unmarked models. States/handlers nested in
    * includes are not reached here (they are resolved later).
    */
  private def defaultEntityInitials(contents: Seq[EntityContents]): Seq[EntityContents] = {
    val states = contents.collect { case s: State => s }
    val withState =
      if states.isEmpty || states.exists(_.isInitial) then contents
      else {
        var done = false
        contents.map {
          case s: State if !done => done = true; s.copy(isInitial = true)
          case other             => other
        }
      }
    val handlers = withState.collect { case h: Handler => h }
    val singleState = withState.collect { case s: State => s }.sizeIs == 1
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

  /** Map a deprecated `option` to the intention it became.
    *
    * The option is LEFT in the metadata. Removing it here was the first attempt and it emptied the
    * `with { … }` block of any entity whose only metadata was that option -- which then drew
    * "Metadata in Entity 'X' should not be empty", scolding the author for content the parser had
    * just deleted. PrettifyPass skips these options instead, so a round trip still converges on the
    * keyword spelling with no duplication, and nothing complains in between.
    */
  private def intentionsFromDeprecatedOptions(meta: Seq[MetaData]): Seq[EntityIntention] = {
    val found = meta.collect {
      case ov: OptionValue if deprecatedIntentionOptions.contains(ov.name) => ov
    }
    found.foreach { ov =>
      val intention = deprecatedIntentionOptions(ov.name)
      deprecation(
        ov.loc,
        s"'option ${ov.name}' is deprecated; write '${intention.keyword}' before 'entity' instead",
        code = Option(Messages.DeprecationCode.EntityOptionToIntention),
        autoFixable = false
      )
    }
    found.map(ov => deprecatedIntentionOptions(ov.name))
  }

  def entity[u: P]: P[Entity] = {
    P(
      Index ~ entityIntentionPrefix ~ Keywords.entity ~/ identifier ~ asShape ~ is ~ open ~/
        entityBody ~ close ~ withMetaData ~ Index
    )./ map { case (start, intentions, id, ascribed, contents, meta, end) =>
      checkForDuplicateIncludes(contents)
      val fromOptions = intentionsFromDeprecatedOptions(meta)
      Entity(
        at(start, end),
        id,
        defaultEntityInitials(contents).toContents,
        ascribedShape = ascribed,
        intentions = EntityIntention.canonical(intentions ++ fromOptions),
        metadata = meta.toContents
      )
    }
  }
}
