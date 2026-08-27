/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import fastparse.*
import MultiLineWhitespace.*
import com.ossuminc.riddl.language.parsing.Keywords.keyword

trait Readability {

  /** A readability word (`to`, `as`, `by`, …) — English filler that makes a statement read like a
    * sentence but carries no AST content of its own.
    *
    * MUST have the same word-boundary guarantee as [[Keywords.keyword]], and until 2026-08-15 it
    * did not: `readable(key)` was a bare `P(key)`, so `to` matched as a PREFIX of any longer
    * identifier. `boundMessageValue` (`StatementParser.scala`) guards its bare-path arm with `!to`
    * expecting that guard to mean "does not START WITH the word `to`, followed by a boundary" — but
    * without a boundary here, `!to` actually meant "does not start with the two characters `t`
    * `o`", so `tell tourCompleted to …` failed the guard on `tourCompleted` itself and died with
    * `Expected one of (command | event | query | result)`, a message that names the wrong problem
    * entirely. Reported by riddl-models: 4 of 10,298 corpus-wide `ValueRef` migration sites hit
    * this (`TourCompleted`, `ToleranceEvaluated`, `TouchpointRecorded` x2).
    *
    * Fixed at the SHAPE, not the one call site: every readability word gets the boundary, because a
    * readability word matching the prefix of a longer identifier is wrong everywhere it appears,
    * not just after `!to`. Confirmed empirically to be a tightening only, not a behavior change, by
    * the full test suite and the riddl-models corpus (both green before and after) -- see
    * `docs/superpowers/plans/2026-08-15-three-task-fixes.md` Fix B.
    */
  def readable[u: P](key: String): P[Unit] = {
    P(key ~~ &(Keywords.isNotKeywordChar))
  }

  def and[u: P]: P[Unit] = readable("and")

  def are[u: P]: P[Unit] = readable("are")

  def as[u: P]: P[Unit] = readable("as")

  def at[u: P]: P[Unit] = readable("at")

  def by[u: P]: P[Unit] = readable("by")

  def `for`[u: P]: P[Unit] = readable("for")

  def from[u: P]: P[Unit] = readable("from")

  def in[u: P]: P[Unit] = readable("in")

  def of[u: P]: P[Unit] = readable("of")

  def so[u: P]: P[Unit] = readable("so")

  def that[u: P]: P[Unit] = readable("that")

  def to[u: P]: P[Unit] = readable("to")

  def wants[u: P]: P[Unit] = readable("wants")

  /** The user-story obligation verb. Widened from the lone `wants` to accept the RFC-2119 / MoSCoW
    * modal synonyms. This is a pure vocabulary alias: the verb word is discarded (like `wants`
    * always has been) and is NOT captured in the `UserStory` AST, so all seven verbs parse to an
    * equivalent `UserStory`.
    */
  def userStoryVerb[u: P]: P[Unit] = Keywords.keywords(
    StringIn("wants", "must", "shall", "should", "may", "will", "can")
  )

  def `with`[u: P]: P[Unit] = readable("with")

  def anyReadability[u: P]: P[Unit] = {
    P(
      Keywords.keywords(
        StringIn(
          ReadabilityWords.and,
          ReadabilityWords.are,
          ReadabilityWords.as,
          ReadabilityWords.at,
          ReadabilityWords.by,
          ReadabilityWords.`for`,
          ReadabilityWords.from,
          ReadabilityWords.in,
          ReadabilityWords.is,
          ReadabilityWords.of,
          ReadabilityWords.so,
          ReadabilityWords.that,
          ReadabilityWords.to,
          ReadabilityWords.wants,
          ReadabilityWords.with_
        )
      )
    )
  }
}

object ReadabilityWords {
  final val and = "and"
  final val are = "are"
  final val as = "as"
  final val at = "at"
  final val by = "by"
  final val `for` = "for"
  final val from = "from"
  final val in = "in"
  final val is = "is"
  final val of = "of"
  final val so = "so"
  final val that = "that"
  final val to = "to"
  final val wants = "wants"
  final val with_ = "with"

  // NOTE: Keep this list in synch with the list in TokenParser
  def allReadability: Seq[String] = Seq(
    and,
    are,
    as,
    at,
    by,
    `for`,
    from,
    in,
    is,
    of,
    so,
    that,
    to,
    wants,
    with_
  )
}
