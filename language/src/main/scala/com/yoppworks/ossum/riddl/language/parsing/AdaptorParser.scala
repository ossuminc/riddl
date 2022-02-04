package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.{Adaptation, Adaptor, Example}
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
trait AdaptorParser extends ReferenceParser with GherkinParser {

  def adaptation[u: P]: P[Adaptation] = {
    P(
      location ~ Keywords.adapt ~/ identifier ~ is ~ open ~ Readability.from ~ eventRef ~
        Readability.to ~ commandRef ~ Readability.as ~/ open ~
        (undefined(Seq.empty[Example]) | examples) ~
        close ~ close ~ briefly ~ description
    ).map { tpl => (Adaptation.apply _).tupled(tpl) }
  }

  def adaptations[u: P]: P[Seq[Adaptation]] = {
    P(undefined(Seq.empty[Adaptation]) | adaptation.rep(1))
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~ contextRef ~ is ~ open ~
        adaptations ~ close ~ briefly ~ description
    ).map { tpl => (Adaptor.apply _).tupled(tpl) }
  }

}
