/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.LiteralString
import fastparse.*
import fastparse.NoWhitespace.*

/** Parser rules that should not collect white space */
private[parsing] trait NoWhiteSpaceParsers { 
  this: ParsingContext =>

  def toEndOfLine[u: P]: P[String] = {
    P(
      CharsWhile(ch => ch != '\n' && ch != '\r').!
    )
  }

  def until[u: P](first: Char, second: Char): P[String] = {
    var firstFound = false
    var secondFound = false
    P(
      CharsWhile {
        case _: Char if firstFound && secondFound => false
        case ch: Char if firstFound && ch == second =>
          secondFound = true
          true
        case ch: Char if ch == first =>
          firstFound = true
          true
        case _ =>
          firstFound = false
          secondFound = false
          true
      }.!
    )
  }

  def until3[u: P](first: Char, second: Char, third: Char): P[String] = {
    var firstFound = false
    var secondFound = false
    var thirdFound = true
    P(
      CharsWhile {
        case _: Char if firstFound && secondFound && thirdFound =>
          false
        case ch: Char if firstFound && secondFound && ch == third =>
          thirdFound = true
          true
        case ch: Char if firstFound && ch == second =>
          secondFound = true
          true
        case ch: Char if ch == first =>
          firstFound = true
          true
        case _ =>
          firstFound = false
          secondFound = false
          thirdFound = false
          true
      }.!.map(_.dropRight(3))
    )
  }

  def markdownLine[u: P]: P[LiteralString] = {
    P(
      location ~ Punctuation.verticalBar ~~ toEndOfLine
    ).map(tpl => LiteralString.apply.tupled(tpl))
  }

  // \\	The backslash character
  // \0n	The character with octal value 0n (0 <= n <= 7)
  // \0nn	The character with octal value 0nn (0 <= n <= 7)
  // \0mnn	The character with octal value 0mnn (0 <= m <= 3, 0 <= n <= 7)
  // \xhh	The character with hexadecimal value 0xhh
  // \uhhhh	The character with hexadecimal value 0xhhhh
  // \x{h...h}	The character with hexadecimal value 0xh...h (Character.MIN_CODE_POINT  <= 0xh...h <=  Character.MAX_CODE_POINT)
  // \t	The tab character ('\u0009')
  // \n	The newline (line feed) character ('\u000A')
  // \r	The carriage-return character ('\u000D')
  // \f	The form-feed character ('\u000C')
  // \a	The alert (bell) character ('\u0007')
  // \e	The escape character ('\u001B')
  // \cx	The control character corresponding to x

  private val backslash: String = "\\"
  private final val zero: String = "0"

  private def hexDigit[u: P]: P[String] = CharIn("0-9a-fA-F").!./
  private def hexEscape[u: P]: P[String] = P(backslash ~ "x" ~ hexDigit.rep(min = 2, max=8,sep="")).!./
  private def unicodeEscape[u: P]: P[Unit] = P(backslash ~ "u" ~ hexDigit.rep(min = 4, max = 4, sep = "")).!./

  private final val escape_chars = "\\\\\\\"aefnrt"
  private def shortcut[u: P]: P[String] = P("\\" ~ CharIn(escape_chars)).!
  def escape[u: P]: P[String] = P(shortcut | hexEscape | unicodeEscape).!./

  private def stringChars(c: Char): Boolean = c != '\"' && c != '\\'

  private def strChars[u: P]: P[String] = P(CharsWhile(stringChars)).!./

  def literalString[u: P]: P[LiteralString] = {
    P(
      location ~ Punctuation.quote ~/ (strChars | escape).rep.! ~
        Punctuation.quote
    )
  }.map { tpl => LiteralString.apply.tupled(tpl) }
}
