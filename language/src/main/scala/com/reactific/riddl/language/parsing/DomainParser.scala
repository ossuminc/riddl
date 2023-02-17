/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.AST
import Terminals.*

import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for domains. */
private[parsing] trait DomainParser
    extends ApplicationParser
    with ContextParser
    with StoryParser
    with StreamingParser
    with TypeParser {

  private def domainOptions[X: P]: P[Seq[DomainOption]] = {
    options[X, DomainOption](
      StringIn(Options.package_, Options.external, Options.technology).!
    ) {
      case (loc, Options.external, _)      => DomainExternalOption(loc)
      case (loc, Options.package_, args)   => DomainPackageOption(loc, args)
      case (loc, Options.technology, args) => DomainTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  private def domainInclude[X: P]: P[Include[DomainDefinition]] = {
    include[DomainDefinition, X](domainContent(_))
  }

  private def domainContent[u: P]: P[Seq[DomainDefinition]] = {
    P(
      (author | typeDef | context | actor | story | domain | term |
        application | importDef | domainInclude).rep(0)
    )
  }

  private def actor[u: P]: P[Actor] = {
    P(
      location ~ Keywords.actor ~ identifier ~/ is ~ literalString ~ briefly ~
        description
    ).map { case (loc, id, is_a, brief, description) =>
      Actor(loc, id, is_a, brief, description)
    }
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~ authorRefs ~ is ~ open ~/
        domainOptions ~
        (undefined(Seq.empty[DomainDefinition]) | domainContent) ~ close ~/
        briefly ~ description
    ).map { case (loc, id, authorRefs, options, defs, briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val authors = mapTo[AST.Author](groups.get(classOf[AST.Author]))
      val subdomains = mapTo[AST.Domain](groups.get(classOf[AST.Domain]))
      val types = mapTo[AST.Type](groups.get(classOf[AST.Type]))
      val contexts = mapTo[Context](groups.get(classOf[Context]))
      val stories = mapTo[Story](groups.get(classOf[Story]))
      val apps = mapTo[Application](groups.get(classOf[Application]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val actors = mapTo[Actor](groups.get(classOf[Actor]))
      val includes = mapTo[Include[DomainDefinition]](
        groups.get(
          classOf[Include[DomainDefinition]]
        )
      )
      Domain(
        loc,
        id,
        options,
        authorRefs,
        authors,
        types,
        contexts,
        actors,
        stories,
        apps,
        subdomains,
        terms,
        includes,
        briefly,
        description
      )
    }
  }
}
