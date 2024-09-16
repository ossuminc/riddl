/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

import scala.concurrent.{Await, Future}

/** Parsing rules for domains. */
private[parsing] trait DomainParser {
  this: VitalDefinitionParser & ApplicationParser & ContextParser & EpicParser & SagaParser & StreamingParser =>

  def user[u: P]: P[User] = {
    P(
      location ~ Keywords.user ~ identifier ~/ is ~ literalString ~/ withMetaData
    )./.map { case (loc, id, is_a, descriptives) =>
      User(loc, id, is_a, descriptives.toContents)
    }
  }

  private def domainInclude[u: P]: P[Include[DomainContents]] = {
    include[u, DomainContents](domainDefinitions(_))
  }

  private def domainDefinitions[u: P]: P[Seq[DomainContents]] = {
    P(
      vitalDefinitionContents |
        author | context | domain | user | application | epic | saga | importDef | domainInclude | comment
    ).asInstanceOf[P[DomainContents]]./.rep(1)
  }

  private def domainBody[u: P]: P[Seq[DomainContents]] = {
    undefined(Seq.empty[DomainContents]) | domainDefinitions
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~/ is ~ open ~/ domainBody ~ close ~ withMetaData
    )./.map { case (loc, id, contents, descriptives) =>
      checkForDuplicateIncludes(contents)
      Domain(loc, id, contents.toContents, descriptives.toContents)
    }
  }
}
