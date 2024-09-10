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
  def byAs[u: P]: P[Unit] = Keywords.keywords(StringIn("by", "as"))

  def `for`[u: P]: P[Unit] = keyword("for")
  def from[u: P]: P[Unit] = keyword("from")
  def in[u: P]: P[Unit] = keyword("in")

  def is[u: P]: P[Unit] = {
    Keywords
      .keywords(
        StringIn("is", "are", ":", "=")
      )
      .?
  }

  def of[u: P]: P[Unit] = keyword("of")
  def so[u: P]: P[Unit] = keyword("so")
  def that[u: P]: P[Unit] = keyword("that")
  def to[u: P]: P[Unit] = keyword("to")
  def wants[u: P]: P[Unit] = keyword("wants")
  def `with`[u: P]: P[Unit] = keyword("with")

}
