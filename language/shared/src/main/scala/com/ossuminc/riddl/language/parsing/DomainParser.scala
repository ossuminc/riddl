/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.{map => _, *}
import com.ossuminc.riddl.utils.Await
import fastparse.*
import fastparse.MultiLineWhitespace.*

import scala.concurrent.Future

/** Parsing rules for domains. */
private[parsing] trait DomainParser {
  this: VitalDefinitionParser & ContextParser & EpicParser & SagaParser & StreamingParser =>

  def user[u: P]: P[User] = {
    P(
      Index ~ Keywords.user ~ identifier ~/ is ~ literalString ~/ withMetaData ~ Index
    )./.map { case (start, id, is_a, descriptives, end) =>
      User(at(start, end), id, is_a, descriptives.toContents)
    }
  }

  private def domainInclude[u: P]: P[Include[DomainContents]] = {
    include[u, DomainContents](domainDefinitions(_))
  }

  private def domainDefinitions[u: P]: P[Seq[DomainContents]] = {
    P(
      vitalDefinitionContents |
        author | context | domain | user | epic | saga | importDef | bastImport | domainInclude | comment
    ).asInstanceOf[P[DomainContents]]./.rep(1)
  }

  private def domainBody[u: P]: P[Seq[DomainContents]] = {
    undefined(Seq.empty[DomainContents]) | domainDefinitions
  }

  def domain[u: P]: P[Domain] = {
    P(
      Index ~ Keywords.domain ~/ identifier ~/ is ~ open ~/ domainBody ~ close ~ withMetaData ~ Index
    )./.map { case (start, id, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Domain(at(start, end), id, contents.toContents, descriptives.toContents)
    }
  }
}
