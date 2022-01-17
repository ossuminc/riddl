package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for domains. */
trait DomainParser
    extends CommonParser
    with ContextParser
    with InteractionParser
    with StreamingParser
    with TypeParser {

  def domainInclude[X: P]: P[Seq[DomainDefinition]] = {
    include[DomainDefinition, X](domainContent(_))
  }

  def domainContent[u: P]: P[Seq[DomainDefinition]] = {
    P(
      typeDef.map(Seq(_)) | interaction.map(Seq(_)) | context.map(Seq(_)) | plant.map(Seq(_)) |
        domain.map(Seq(_)) | domainInclude | importDef.map(Seq(_))
    ).rep(0).map(_.flatten)
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~ is ~ open ~/
        (undefined(Seq.empty[DomainDefinition]) | domainContent) ~ close ~/ description
    ).map { case (loc, id, defs, description) =>
      val groups = defs.groupBy(_.getClass)
      val domains = mapTo[AST.Domain](groups.get(classOf[AST.Domain]))
      val types = mapTo[AST.Type](groups.get(classOf[AST.Type]))
      val contexts = mapTo[Context](groups.get(classOf[Context]))
      val interactions = mapTo[Interaction](groups.get(classOf[Interaction]))
      val plants = mapTo[Plant](groups.get(classOf[Plant]))
      Domain(loc, id, types, contexts, interactions, plants, domains, description)
    }
  }
}
