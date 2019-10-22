package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._

/** Parsing rules for domains. */
trait DomainParser
    extends ChannelParser
    with ContextParser
    with InteractionParser
    with MessageParser
    with TypeParser {

  def domainDefinitions[_: P]: P[Definition] = {
    P(typeDef | anyMessageDef | channelDef | interactionDef | contextDef)
  }

  type DomainDefinitions = (
    Seq[TypeDef],
    Seq[ChannelDef],
    Seq[ContextDef],
    Seq[InteractionDef]
  )

  def domainContent[_: P]: P[DomainDefinitions] = {
    P(
      typeDef |
        channelDef |
        interactionDef |
        contextDef
    ).rep(0).map { seq =>
      val groups = seq.groupBy(_.getClass)
      val result = (
        mapTo[TypeDef](groups.get(classOf[TypeDef])),
        mapTo[ChannelDef](groups.get(classOf[ChannelDef])),
        mapTo[ContextDef](groups.get(classOf[ContextDef])),
        mapTo[InteractionDef](groups.get(classOf[InteractionDef]))
      )
      result
    }
  }

  def domainDef[_: P]: P[DomainDef] = {
    P(
      location ~ "domain" ~/ identifier ~
        ("is" ~ "subdomain" ~ "of" ~/ identifier).? ~ open ~/
        domainContent ~
        close ~ addendum
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
