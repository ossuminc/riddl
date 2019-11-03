package com.yoppworks.ossum.riddl.language

import fastparse._
import NoWhitespace._
import com.yoppworks.ossum.riddl.language.AST.LiteralString
import com.yoppworks.ossum.riddl.language.AST.Location
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation

/** Parser rules that should not collect white space */
trait NoWhiteSpaceParsers extends ParsingContext {

  final val specialLineChars: String =
    "~`!@#$%^&*()_-+=[]\"':;<>,.?/"

  def markdownPredicate(c: Char): Boolean = {
    c.isLetterOrDigit | c.isSpaceChar | specialLineChars.contains(c)
  }

  def markdownLine[_: P]: P[LiteralString] = {
    P(
      Punctuation.verticalBar ~ location ~~ CharPred(markdownPredicate).rep.!
        ~~ ("\n" | "\r").rep(1)
    ).map(tpl => (LiteralString.apply _).tupled(tpl))
  }

  def literalString[_: P]: P[LiteralString] = {
    P(
      location ~ Punctuation.quote ~~/
        CharsWhile(_ != '"', 0).! ~~
        Punctuation.quote
    ).map { tpl =>
      (LiteralString.apply _).tupled(tpl)
    }
  }

}
