package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.{Adaptor, Example}
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
trait AdaptorParser extends FeatureParser {

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~ contextRef ~ is ~ open ~
        (undefined(Seq.empty[Example]) | examples) ~ close ~ description
    ).map { tpl => (Adaptor.apply _).tupled(tpl) }
  }

}
