package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._
import Terminals.Keywords

/** Parsing rules for domains. */
trait DomainParser
    extends CommonParser
    with TopicParser
    with ContextParser
    with InteractionParser
    with TypeParser {

  def domainInclude[X: P]: P[Seq[DomainDefinition]] = {
    include[DomainDefinition, X](domainContent(_))
  }

  def domainContent[_: P]: P[Seq[DomainDefinition]] = {
    P(
      typeDef.map(Seq(_)) | topic.map(Seq(_)) | interaction.map(Seq(_)) | context.map(Seq(_)) |
        domain.map(Seq(_)) | domainInclude
    ).rep(0).map(_.flatten)
  }

  def domain[_: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~ is ~ open ~/
        (undefined.map(_ => Seq.empty[DomainDefinition]) | domainContent) ~ close ~/ description
    ).map { case (loc, id, defs, description) =>
      val groups = defs.groupBy(_.getClass)
      val domains = mapTo[AST.Domain](groups.get(classOf[AST.Domain]))
      val types = mapTo[AST.Type](groups.get(classOf[AST.Type]))
      val topics = mapTo[Topic](groups.get(classOf[Topic]))
      val contexts = mapTo[Context](groups.get(classOf[Context]))
      val interactions = mapTo[Interaction](groups.get(classOf[Interaction]))
      Domain(loc, id, types, topics, contexts, interactions, domains, description)
    }
  }
}
