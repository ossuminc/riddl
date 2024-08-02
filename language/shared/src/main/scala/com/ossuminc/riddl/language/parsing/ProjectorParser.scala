/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*

/** Unit Tests For FunctionParser */
private[parsing] trait ProjectorParser {
  this: FunctionParser
    & HandlerParser
    & ReferenceParser
    & StatementParser
    & StreamingParser
    & TypeParser =>

  private def projectorInclude[u: P]: P[Include[OccursInProjector]] = {
    include[u, OccursInProjector](projectorDefinitions(_))
  }

  private def updates[u: P]: P[RepositoryRef] = {
    P(
      location ~ Keywords.updates ~ repositoryRef
    ).map { case (_, ref) =>
      ref
    }
  }

  private def projectorDefinitions[u: P]: P[Seq[OccursInProjector]] = {
    P(
      updates | typeDef | term | projectorInclude | handler(StatementsSet.ProjectorStatements) |
        function | inlet | outlet | invariant | constant | authorRef | comment | option
    )./.rep(1)
  }

  private def projectorBody[u: P]: P[Seq[OccursInProjector]] = {
    P(
      undefined(Seq.empty[OccursInProjector]) | projectorDefinitions
    )
  }

  /** Parses projector definitions, e.g.
    *
    * {{{
    *   projector myView is {
    *     foo: Boolean
    *     bar: Integer
    *   }
    * }}}
    */
  def projector[u: P]: P[Projector] = {
    P(
      location ~ Keywords.projector ~/ identifier ~ is ~ open ~ projectorBody ~ close ~ briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      Projector(loc, id, contents, brief, description)
    }
  }
}
