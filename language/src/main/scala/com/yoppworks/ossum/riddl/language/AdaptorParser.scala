package com.yoppworks.ossum.riddl.language

import fastparse._
import ScalaWhitespace._
import AST.Adaptor
import AST.AdaptorMapping
import Terminals.Keywords
import Terminals.Punctuation
import Terminals.Readability

/** Parser rules for Adaptors */
trait AdaptorParser extends CommonParser {

  def adaptor[_: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~
        contextRef ~ is ~ open ~ undefined.map(_ => Seq.empty[AdaptorMapping]) ~
        close ~ description
    ).map { tpl => (Adaptor.apply _).tupled(tpl) }
  }

}
