package com.yoppworks.ossum.riddl.language

import fastparse._
import ScalaWhitespace._
import AST.Adaptor
import AST.Example
import Terminals.Keywords
import Terminals.Readability

/** Parser rules for Adaptors */
trait AdaptorParser extends FeatureParser {

  def adaptor[_: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~ contextRef ~ is ~ open ~
        (namedExample.rep(1) | undefined.map(_ => Seq.empty[Example])) ~ close ~ description
    ).map { tpl => (Adaptor.apply _).tupled(tpl) }
  }

}
