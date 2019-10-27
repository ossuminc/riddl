package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation
import com.yoppworks.ossum.riddl.language.Terminals.Readability

/** Parsing rules for domains. */
trait DomainParser
    extends TopicParser
    with ContextParser
    with InteractionParser
    with MessageParser
    with TypeParser {

  def domainDefinitions[_: P]: P[Definition] = {
    P(typeDef | anyMessageDef | topicDef | interactionDef | contextDef)
  }

  type DomainDefinitions = (
    Seq[TypeDef],
    Seq[TopicDef],
    Seq[ContextDef],
    Seq[InteractionDef]
  )

  def domainContent[_: P]: P[DomainDefinitions] = {
    P(
      typeDef |
        topicDef |
        interactionDef |
        contextDef
    ).rep(0).map { seq =>
      val groups = seq.groupBy(_.getClass)
      val result = (
        mapTo[TypeDef](groups.get(classOf[TypeDef])),
        mapTo[TopicDef](groups.get(classOf[TopicDef])),
        mapTo[ContextDef](groups.get(classOf[ContextDef])),
        mapTo[InteractionDef](groups.get(classOf[InteractionDef]))
      )
      result
    }
  }

  def domainDef[_: P]: P[DomainDef] = {
    P(
      location ~ Keywords.domain ~/ identifier ~
        (Readability.is ~ Keywords.subdomain ~ Readability.of ~/ identifier).? ~
        Punctuation.curlyOpen ~/
        domainContent ~
        Punctuation.curlyClose ~ addendum
    ).map {
      case (
          loc,
          id,
          subdomain,
          defs,
          addendum
          ) =>
        DomainDef(
          loc,
          id,
          subdomain,
          defs._1,
          defs._2,
          defs._3,
          defs._4,
          addendum
        )
    }
  }

  def root[_: P]: P[Seq[DomainDef]] = {
    P(Start ~ P(domainDef).rep(0) ~ End)
  }
}
