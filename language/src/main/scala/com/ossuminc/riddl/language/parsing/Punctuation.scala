/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing
import fastparse.*

object Punctuation {
  final val asterisk = "*"
  final val atSign = "@"
  final val codeQuote = "```"
  final val comma = ","
  final val colon = ":"
  final val curlyOpen = "{"
  final val curlyClose = "}"
  final val dot = "."
  final val equalsSign = "="
  final val exclamation = "!"
  final val plus = "+"
  final val question = "?"
  final val quote = "\""
  final val roundOpen = "("
  final val roundClose = ")"
  final val squareOpen = "["
  final val squareClose = "]"
  final val undefinedMark = "???"
  final val verticalBar = "|"

  // NOTE: Keep this link in synch with the list in TokenParser
  def allPunctuation: Seq[String] = Seq(
    asterisk,
    atSign,
    comma,
    colon,
    curlyOpen,
    curlyClose,
    dot,
    equalsSign,
    exclamation,
    plus,
    question,
    quote,
    roundOpen,
    roundClose,
    squareOpen,
    squareClose,
    undefinedMark,
    verticalBar
  )

  def anyPunctuation[u: P]: P[Unit] = {
    P(
      StringIn(
        asterisk,
        atSign,
        codeQuote,
        comma,
        colon,
        curlyOpen,
        curlyClose,
        dot,
        equalsSign,
        exclamation,
        plus,
        question,
        quote,
        roundOpen,
        roundClose,
        squareOpen,
        squareClose,
        verticalBar,
        undefinedMark
      )
    )
  }

  /** The TOKENIZER's punctuation set. `!` is guarded by a negative lookahead rather than listed
    * inside the `StringIn`, mirroring the parser's own `"!" ~~ !"="` guard: `!=` is a comparison
    * OPERATOR, not a negation, and this set deliberately contains no comparison operators at all
    * (there is no `<` or `>` here either), so tokenizing `!=` as punctuation would be inconsistent
    * as well as misleading to an editor.
    *
    * `!` belongs here at all only because of the 2026-08-14 ruling that made it synonymous with
    * `not` EVERYWHERE. Before that it was a narrow special case, and its absence cost nothing;
    * after it, `TokenParser.otherToken` swallowed `!isValid then do "no" end` -- the whole rest of
    * the input -- into a single `Token.Other`, leaving riddl-idea-plugin and synapify unable to
    * highlight any of it.
    */
  def tokenPunctuation[u: P]: P[Unit] = {
    P(
      (exclamation ~~ !"=") | StringIn(
        asterisk,
        atSign,
        comma,
        colon,
        curlyOpen,
        curlyClose,
        dot,
        equalsSign,
        plus,
        question,
        roundOpen,
        roundClose,
        squareOpen,
        squareClose,
        undefinedMark
      )
    )
  }
}
