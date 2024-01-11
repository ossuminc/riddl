/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import Readability.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

import scala.concurrent.{Await, Future}

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
    with CommonParser
    with ParsingContext =>

  private def domainOptions[X: P]: P[Seq[DomainOption]] = {
    options[X, DomainOption](RiddlOptions.domainOptions) {
      case (loc, RiddlOption.external, args)   => DomainExternalOption(loc, args)
      case (loc, RiddlOption.package_, args)   => DomainPackageOption(loc, args)
      case (loc, RiddlOption.technology, args) => DomainTechnologyOption(loc, args)
      case (loc, RiddlOption.color, args)      => DomainColorOption(loc, args)
      case (loc, RiddlOption.kind, args)       => DomainKindOption(loc, args)
    }
  }

  private def domainInclude[u: P]: P[IncludeHolder[OccursInDomain]] = {
    include[OccursInDomain, u](domainDefinitions(_))
  }

  private def user[u: P]: P[User] = {
    P(
      location ~ Keywords.user ~ identifier ~/ is ~ literalString ~/ briefly ~/
        description
    ).map { case (loc, id, is_a, brief, description) =>
      User(loc, id, is_a, brief, description)
    }
  }

  private def domainDefinitions[u: P]: P[Seq[OccursInDomain]] = {
    P(
      author | authorRef | typeDef | context | user | epic | saga | domain | term |
        constant | application | importDef | domainInclude | comment
    )./.rep(1)
  }

  private def domainBody[u: P]: P[Seq[OccursInDomain]] = {
    undefined(Seq.empty[OccursInDomain]) | domainDefinitions
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~/ is ~ open ~/
        domainOptions ~/ domainBody ~ close ~/
        briefly ~ description
    ).map { case (loc, id, options, contents, brief, description) =>
      val mergedContent = mergeAsynchContent[OccursInDomain](contents)
      Domain(loc, id, options, mergedContent, brief, description)
    }
  }
}
