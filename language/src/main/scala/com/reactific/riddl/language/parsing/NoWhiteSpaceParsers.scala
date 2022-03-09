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
