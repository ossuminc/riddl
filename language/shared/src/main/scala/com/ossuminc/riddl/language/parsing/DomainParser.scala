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
  this: ApplicationParser & ContextParser & EpicParser & ReferenceParser & SagaParser & StreamingParser &
    StatementParser & TypeParser & CommonParser & ParsingContext =>

  private def user[u: P]: P[User] = {
    P(
      location ~ Keywords.user ~ identifier ~/ is ~ literalString ~/ briefly ~/
        description
    ).map { case (loc, id, is_a, brief, description) =>
      User(loc, id, is_a, brief, description)
    }
  }

  private def domainInclude[u: P]: P[Include[DomainContents]] = {
    include[u, DomainContents](domainDefinitions(_))
  }

  private def domainDefinitions[u: P]: P[Seq[DomainContents]] = {
    P(
      author | authorRef | typeDef | context | user | epic | saga | domain | term |
        constant | application | importDef | domainInclude | comment | option
    ).asInstanceOf[P[DomainContents]]./.rep(1)
  }

  private def domainBody[u: P]: P[Seq[DomainContents]] = {
    undefined(Seq.empty[DomainContents]) | domainDefinitions
  }

  def domain[u: P]: P[Domain] = {
    P(
      location ~ Keywords.domain ~/ identifier ~/ is ~ open ~/ domainBody ~ close ~/
        briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      checkForDuplicateIncludes(contents)
      Domain(loc, id, contents, brief, description)
    }
  }
}
