package com.yoppworks.ossum.riddl.language

import fastparse._
import ScalaWhitespace._
import AST.AdaptorDef
import Terminals.Keywords
import Terminals.Punctuation
import Terminals.Readability

/** Parser rules for Adaptors */
trait AdaptorParser extends CommonParser {

  def adaptorDef[_: P]: P[AdaptorDef] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~/ domainRef.? ~/
        contextRef ~ is ~/
        open
        ~ close ~ addendum
    ).map { tpl =>
      (AdaptorDef.apply _).tupled(tpl)
    }
  }

}
