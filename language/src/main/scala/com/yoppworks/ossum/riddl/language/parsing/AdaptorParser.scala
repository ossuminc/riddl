package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.{Adaptation, Adaptor,
  AdaptorDefinition, Example, Include }
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
trait AdaptorParser extends ReferenceParser with GherkinParser {

  def adaptation[u: P]: P[Adaptation] = {
    P(
      location ~ Keywords.adapt ~/ identifier ~ is ~ open ~ Readability.from ~ eventRef ~
        Readability.to ~ commandRef ~ Readability.as ~/ open ~
        (undefined(Seq.empty[Example]) | examples) ~ close ~ close ~ briefly ~ description
    ).map { tpl => (Adaptation.apply _).tupled(tpl) }
  }

  def adaptorInclude[u: P]: P[Include] = {
    include[AdaptorDefinition, u](adaptorDefinitions(_))
  }

  def adaptorDefinitions[u: P]: P[Seq[AdaptorDefinition]] = {
    P(
      undefined(Seq.empty[AdaptorDefinition]) |
        ( adaptation | adaptorInclude).rep(1)
    )
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~ contextRef ~ is ~ open ~
        adaptorDefinitions ~ close ~ briefly ~ description
    ).map { case(loc, id, cref, defs, briefly, description ) =>
      val groups = defs.groupBy(_.getClass)
      val includes = mapTo[Include](groups.get(classOf[Include]))
      val adaptations = mapTo[Adaptation](groups.get(classOf[Adaptation]))
      Adaptor(
        loc, id, cref, adaptations, includes, briefly, description
      )
    }
  }
}
