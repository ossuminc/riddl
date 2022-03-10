/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.LiteralString
import com.reactific.riddl.language.Terminals.Punctuation
import fastparse.*
import fastparse.NoWhitespace.*

/** Parser rules that should not collect white space */
trait NoWhiteSpaceParsers extends ParsingContext {

  def markdownLine[u: P]: P[LiteralString] = {
    P(
      location ~ Punctuation.verticalBar ~~ CharsWhile(ch => ch != '\n' && ch != '\r').! ~~
        ("\n" | "\r").rep(min = 1, max = 2)
    ).map(tpl => (LiteralString.apply _).tupled(tpl))
  }

  def hexDigit[u: P]: P[Unit] = P(CharIn("0-9a-fA-F").!)

  def unicodeEscape[u: P]: P[Unit] = P("u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit).!

  def escape[u: P]: P[Unit] = P("\\" ~ (CharIn("\"/\\\\bfnrt") | unicodeEscape))

  def stringChars(c: Char): Boolean = c != '\"' && c != '\\'

  def strChars[u: P]: P[Unit] = P(CharsWhile(stringChars))

  def literalString[u: P]: P[LiteralString] = {
    P(location ~ Punctuation.quote ~/ (strChars | escape).rep.! ~ Punctuation.quote)
  }.map { tpl => (LiteralString.apply _).tupled(tpl) }

}
