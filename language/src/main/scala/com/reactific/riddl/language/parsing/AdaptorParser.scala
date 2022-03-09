package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser rules for Adaptors */
trait AdaptorParser
    extends ReferenceParser with GherkinParser with ActionParser {

  def eventCommandAdaptation[u: P]: P[EventCommandA8n] = {
    P(
      location ~ Keywords.adapt ~/ identifier ~ is ~ open ~ Readability.from ~
        eventRef ~ Readability.to ~ commandRef ~ Readability.as ~/ open ~
        (undefined(Seq.empty[Example]) | examples) ~ close ~ close ~ briefly ~
        description
    ).map { tpl => (EventCommandA8n.apply _).tupled(tpl) }
  }

  def eventActionAdaptation[u: P]: P[EventActionA8n] = {
    P(
      location ~ Keywords.adapt ~/ identifier ~ is ~ open ~ Readability.from ~
        eventRef ~ Readability.to ~ open ~ actionList ~ close ~/
        Readability.as ~/ open ~
        (undefined(Seq.empty[Example]) | examples) ~ close ~ close ~ briefly ~
        description
    ).map { tpl => (EventActionA8n.apply _).tupled(tpl) }
  }

  def adaptorInclude[u: P]: P[Include] = {
    include[AdaptorDefinition, u](adaptorDefinitions(_))
  }

  def adaptorDefinitions[u: P]: P[Seq[AdaptorDefinition]] = {
    P(
      undefined(Seq.empty[AdaptorDefinition]) |
        (eventCommandAdaptation | eventActionAdaptation | adaptorInclude).rep(1)
    )
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ Readability.for_ ~
        contextRef ~ is ~ open ~ adaptorDefinitions ~ close ~ briefly ~
        description
    ).map { case (loc, id, cref, defs, briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val includes = mapTo[Include](groups.get(classOf[Include]))
      val adaptations: Seq[Adaptation] =
        defs.filter(_.isInstanceOf[Adaptation]).map(_.asInstanceOf[Adaptation])
      Adaptor(loc, id, cref, adaptations, includes, briefly, description)
    }
  }
}
