/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.LiteralString
import com.ossuminc.riddl.utils.URL
import fastparse.*
import fastparse.NoWhitespace.*

/** Parser rules that should not collect white space */
private[parsing] trait NoWhiteSpaceParsers {
  this: ParsingContext =>

  def toEndOfLine[u: P]: P[String] = {
    P(
      CharsWhile(ch => ch != '\n' && ch != '\r', 0).!
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
      Index ~ Punctuation.verticalBar ~~ toEndOfLine ~/ Index
    ).map { case (start, line, end) => LiteralString(at(start, end), line) }
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
  private def hexEscape[u: P]: P[String] = P(backslash ~ "x" ~ hexDigit.rep(min = 2, max = 8, sep = "")).!./
  private def unicodeEscape[u: P]: P[Unit] = P(backslash ~ "u" ~ hexDigit.rep(min = 4, max = 4, sep = "")).!./

  private final val escape_chars = "\\\\\\\"aefnrt"
  private def shortcut[u: P]: P[String] = P("\\" ~ CharIn(escape_chars)).!
  
  def escape[u: P]: P[String] = P(shortcut | hexEscape | unicodeEscape).!./

  def stringChars(c: Char): Boolean = c != '\"' && c != '\\'

  def strChars[u: P]: P[String] = P(CharsWhile(stringChars)).!./

  def literalString[u: P]: P[LiteralString] = {
    P(
      Index ~ Punctuation.quote ~/ (strChars | escape).rep.! ~ Punctuation.quote ~ Index
    )
  }.map { case (off1, litStr, off2) => LiteralString(at(off1, off2), litStr) }

  private def hostString[u: P]: P[String] = {
    P(CharsWhile { ch => ch.isLetterOrDigit || ch == '-' }.rep(1, ".", 32)).!
  }

  private def portNum[u: P]: P[String] = {
    P(Index ~~ CharsWhileIn("0-9").rep(min = 1, max = 5).! ~~ Index).map { (i1, numStr: String, i2) =>
      val num = numStr.toInt
      if num > 0 && num < 65535 then
        numStr
      else
        error(at(i1,i2), s"Invalid port number: $numStr. Must be in range 0 <= port < 65536")
        "0"
      end if
    }
  }

  private final val validURLChars = "-_.~!$&'()*+,;="
  private def urlPath[u: P]: P[String] = {
    P(
      CharsWhile(ch => ch.isLetterOrDigit || validURLChars.contains(ch))
    ).repX(1,"/").!
  }

  def httpUrl[u: P]: P[URL] = {
    P(
      StringIn("http","https") ~~ "://" ~~ hostString ~~ (":" ~~ portNum).? ~~ "/" ~~ urlPath.?
    ).!.map(URL)
  }


}
