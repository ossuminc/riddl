/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import fastparse.*
import MultiLineWhitespace.*
import com.ossuminc.riddl.language.parsing.Keywords.keyword

trait Readability {

  def and[u: P]: P[Unit] = keyword("and")

  def are[u: P]: P[Unit] = keyword("are")

  def as[u: P]: P[Unit] = keyword("as")

  def at[u: P]: P[Unit] = keyword("at")

  def by[u: P]: P[Unit] = keyword("by")

  def `for`[u: P]: P[Unit] = keyword("for")

  def from[u: P]: P[Unit] = keyword("from")

  def in[u: P]: P[Unit] = keyword("in")

  def of[u: P]: P[Unit] = keyword("of")

  def so[u: P]: P[Unit] = keyword("so")

  def that[u: P]: P[Unit] = keyword("that")

  def to[u: P]: P[Unit] = keyword("to")

  def wants[u: P]: P[Unit] = keyword("wants")

  def `with`[u: P]: P[Unit] = keyword("with")

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
