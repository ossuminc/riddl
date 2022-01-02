package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.{Adaptation, Adaptor, EventAdaptation}
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
trait AdaptorParser extends FeatureParser {

  def adaptation[u: P]: P[Adaptation] = {
    P(
      location ~ Keywords.adapt ~/ identifier ~ is ~ open ~ Readability.from ~ eventRef ~
        Readability.to ~ commandRef ~ Readability.as ~/ open ~
        (undefined(None) | example.map(Some(_))) ~ close
    ).map { case (location, identifier, event, command, example) =>
      EventAdaptation(location, identifier, event, command, example)
    }
  }

  def adaptations[u: P]: P[Seq[Adaptation]] = {
    P(undefined(Seq.empty[Adaptation]) | adaptation.rep(1))
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~ contextRef ~ is ~ open ~
        adaptations ~ close ~ description
    ).map { tpl => (Adaptor.apply _).tupled(tpl) }
  }

}
