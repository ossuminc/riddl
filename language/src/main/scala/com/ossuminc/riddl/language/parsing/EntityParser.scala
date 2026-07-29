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
            "BODY everywhere else in the language."
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

  def entity[u: P]: P[Entity] = {
    P(
      Index ~ Keywords.entity ~/ identifier ~ asShape ~ is ~ open ~/ entityBody ~ close ~
        withMetaData ~ Index
    )./ map { case (start, id, ascribed, contents, meta, end) =>
      checkForDuplicateIncludes(contents)
      Entity(
        at(start, end),
        id,
        defaultEntityInitials(contents).toContents,
        ascribedShape = ascribed,
        metadata = meta.toContents
      )
    }
  }
}
