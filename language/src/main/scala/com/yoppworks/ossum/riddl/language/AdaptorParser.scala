package com.yoppworks.ossum.riddl.language

import fastparse._
import ScalaWhitespace._
import AST.Adaptor
import Terminals.Keywords
import Terminals.Punctuation
import Terminals.Readability

/** Parser rules for Adaptors */
trait AdaptorParser extends CommonParser {

  def adaptorDef[_: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~/ domainRef.? ~/
        contextRef ~ is ~/
        open
        ~ close ~ addendum
    ).map { tpl =>
      (Adaptor.apply _).tupled(tpl)
    }
  }

}
