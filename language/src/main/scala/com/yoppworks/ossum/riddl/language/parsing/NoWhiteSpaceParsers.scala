package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.LiteralString
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation
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

  def literalString[u: P]: P[LiteralString] = {
    P(location ~ Punctuation.quote ~~ CharsWhile(_ != '"', 0).! ~~ Punctuation.quote).map { tpl =>
      (LiteralString.apply _).tupled(tpl)
    }
  }
}
