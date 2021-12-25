package com.yoppworks.ossum.riddl.language

import fastparse.*
import ScalaWhitespace.*
import AST.Adaptor
import AST.AdaptorMapping
import Terminals.Keywords
import Terminals.Readability

/** Parser rules for Adaptors */
trait AdaptorParser extends CommonParser {

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~ contextRef ~ is ~ open ~
        undefined(Seq.empty[AdaptorMapping]) ~ close ~ description
    ).map { tpl => (Adaptor.apply _).tupled(tpl) }
  }

}
