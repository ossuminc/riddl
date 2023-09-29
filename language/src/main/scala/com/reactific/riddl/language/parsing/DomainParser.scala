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
private[parsing] trait DomainParser {
  this: ApplicationParser
    with ContextParser
    with EpicParser
    with ReferenceParser
    with SagaParser
    with StreamingParser
    with StatementParser
    with TypeParser
    with CommonParser =>

  private def domainOptions[X: P]: P[Seq[DomainOption]] = {
    options[X, DomainOption](StringIn(Options.package_, Options.technology).!) {
      case (loc, Options.package_, args)   => DomainPackageOption(loc, args)
      case (loc, Options.technology, args) => DomainTechnologyOption(loc, args)
    }
  }

  private def domainInclude[u: P]: P[Include[DomainDefinition]] = {
    include[DomainDefinition, u](domainDefinitions(_))
  }

  private def user[u: P]: P[User] = {
    P(
      location ~ Keywords.user ~ identifier ~/ is ~ literalString ~ briefly ~
        description
    ).map { case (loc, id, is_a, brief, description) =>
      User(loc, id, is_a, brief, description)
    }
  }

  private def domainDefinitions[u: P]: P[Seq[DomainDefinition]] = {
    P(
      author | typeDef | context | user | epic | saga | domain | term |
        constant | application | importDef | domainInclude
    )./.rep(1)
  }

  private def domainBody[u: P]: P[Seq[DomainDefinition]] = {
    undefined(Seq.empty[DomainDefinition]) | domainDefinitions
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~ authorRefs ~ is ~ open ~/
        domainOptions ~ domainBody ~ close ~/
        briefly ~ description
    ).map { case (loc, id, authorRefs, options, defs, briefly, description) =>
      val groups = defs.groupBy(_.getClass)
      val authors = mapTo[Author](groups.get(classOf[Author]))
      val subdomains = mapTo[Domain](groups.get(classOf[Domain]))
      val types = mapTo[Type](groups.get(classOf[Type]))
      val consts = mapTo[Constant](groups.get(classOf[Constant]))
      val contexts = mapTo[Context](groups.get(classOf[Context]))
      val epics = mapTo[Epic](groups.get(classOf[Epic]))
      val sagas = mapTo[Saga](groups.get(classOf[Saga]))
      val apps = mapTo[Application](groups.get(classOf[Application]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val users = mapTo[User](groups.get(classOf[User]))
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
        consts,
        contexts,
        users,
        epics,
        sagas,
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
