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
    with HandlerParser
    with ReferenceParser
    with StatementParser
    with StreamingParser
    with TypeParser =>

  private def projectorOption[u: P]: P[ProjectorOption] = {
    option[u, ProjectorOption](RiddlOptions.projectorOptions) {
      case (loc, RiddlOption.technology, args) => ProjectorTechnologyOption(loc, args)
      case (loc, RiddlOption.css, args)        => ProjectorCssOption(loc, args)
      case (loc, RiddlOption.faicon, args)     => ProjectorIconOption(loc, args)
      case (loc, RiddlOption.kind, args)       => ProjectorKindOption(loc, args)
    }
  }

  private def projectorInclude[u: P]: P[IncludeHolder[OccursInProjector]] = {
    include[OccursInProjector, u](projectorDefinitions(_))
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
        function | inlet | outlet | invariant | constant | authorRef | comment | projectorOption
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
      val mergedContent = mergeAsynchContent[OccursInProjector](contents)

      Projector(loc, id, mergedContent, brief, description)
    }
  }
}
