/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.LiteralString
import fastparse.*
import fastparse.NoWhitespace.*

/** Parser rules that should not collect white space */
private[parsing] trait NoWhiteSpaceParsers extends ParsingContext with Terminals {

  def markdownLine[u: P]: P[LiteralString] = {
    P(
      location ~ Punctuation.verticalBar ~~
        CharsWhile(ch => ch != '\n' && ch != '\r').! ~~ ("\n" | "\r")
          .rep(min = 1, max = 2)
    ).map(tpl => (LiteralString.apply _).tupled(tpl))
  }

  private def hexDigit[u: P]: P[Unit] = P(CharIn("0-9a-fA-F").!)

  private def unicodeEscape[u: P]: P[Unit] =
    P("u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit).!

  def escape[u: P]: P[Unit] = P("\\" ~ (CharIn("\"/\\\\bfnrt") | unicodeEscape))

  private def stringChars(c: Char): Boolean = c != '\"' && c != '\\'

  private def strChars[u: P]: P[Unit] = P(CharsWhile(stringChars))

  def literalString[u: P]: P[LiteralString] = {
    P(
      location ~ Punctuation.quote ~/ (strChars | escape).rep.! ~
        Punctuation.quote
    )
  }.map { tpl => (LiteralString.apply _).tupled(tpl) }

}
