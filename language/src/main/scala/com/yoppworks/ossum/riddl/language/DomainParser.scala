package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._
import Terminals.Keywords
import Terminals.Readability

/** Parsing rules for domains. */
trait DomainParser
    extends CommonParser
    with TopicParser
    with ContextParser
    with InteractionParser
    with TypeParser {

  def domainDefinitions[_: P]: P[Definition] = {
    P(typeDef | topicDef | interactionDef | contextDef)
  }

  def domainInclude[_: P]: P[Seq[DomainDefinition]] = {
    P(
      Keywords.include ~/ literalString
    ).map { str =>
      doInclude(str, Seq.empty[DomainDefinition])(domainContent(_))
    }
  }

  def domainContent[_: P]: P[Seq[DomainDefinition]] = {
    P(
      typeDef.map(Seq(_)) |
        topicDef.map(Seq(_)) |
        interactionDef.map(Seq(_)) |
        contextDef.map(Seq(_)) |
        domainInclude
    ).rep(0).map(_.flatten)
  }

  def domainDef[_: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~
        (Readability.as ~ Keywords.subdomain ~ Readability.of ~/ identifier).? ~
        is ~
        open ~/
        domainContent ~
        close ~ addendum
    ).map {
      case (loc, id, subdomain, defs, addendum) =>
        val groups = defs.groupBy(_.getClass)
        val types = mapTo[AST.Type](groups.get(classOf[AST.Type]))
        val topics = mapTo[Topic](groups.get(classOf[Topic]))
        val contexts = mapTo[Context](groups.get(classOf[Context]))
        val interactions =
          mapTo[Interaction](groups.get(classOf[Interaction]))
        Domain(
          loc,
          id,
          subdomain,
          types,
          topics,
          contexts,
          interactions,
          addendum
        )
    }
  }
}
