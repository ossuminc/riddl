package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.CommonParser._
import com.yoppworks.ossum.riddl.parser.ContextParser.contextDef
import com.yoppworks.ossum.riddl.parser.InteractionParser.interactionDef
import com.yoppworks.ossum.riddl.parser.TypesParser.typeDef
import fastparse._
import ScalaWhitespace._

/** Parsing rules for domains. */
object DomainParser {

  def adaptorDef[_: P]: P[AdaptorDef] = {
    P(
      "adaptor" ~/ Index ~ identifier ~ "for" ~/ domainRef.? ~/ contextRef ~
        explanation
    ).map { tpl =>
      (AdaptorDef.apply _).tupled(tpl)
    }
  }

  def channelDef[_: P]: P[ChannelDef] = {
    P(
      "channel" ~ Index ~/ identifier ~ "{" ~
        "commands" ~ "{" ~/ identifier.map(CommandRef).rep(0, ",") ~ "}" ~/
        "events" ~ "{" ~/ identifier.map(EventRef).rep(0, ",") ~ "}" ~/
        "queries" ~ "{" ~/ identifier.map(QueryRef).rep(0, ",") ~ "}" ~/
        "results" ~ "{" ~/ identifier.map(ResultRef).rep(0, ",") ~ "}" ~/
        "}" ~/ explanation
    ).map { tpl ⇒
      (ChannelDef.apply _).tupled(tpl)
    }
  }

  def domainDef[_: P]: P[DomainDef] = {
    P(
      "domain" ~ Index ~/ identifier ~
        ("is" ~ "subdomain" ~ "of" ~/ identifier).? ~ "{" ~/
        typeDef.rep(0) ~
        channelDef.rep(0) ~
        interactionDef.rep(0) ~
        contextDef.rep(0) ~
        "}" ~ explanation
    ).map { tpl ⇒
      (DomainDef.apply _).tupled(tpl)
    }
  }

  def topLevelDomains[_: P]: P[Seq[DomainDef]] = {
    P(Start ~ P(domainDef).rep(0) ~ End)
  }
}
