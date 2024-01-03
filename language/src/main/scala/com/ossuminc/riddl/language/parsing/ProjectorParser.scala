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

  private def projectionOptions[u: P]: P[Seq[ProjectorOption]] = {
    options[u, ProjectorOption](StringIn(RiddlOption.technology,RiddlOption.color,RiddlOption.kind).!) {
      case (loc, RiddlOption.technology, args) => ProjectorTechnologyOption(loc, args)
      case (loc, RiddlOption.color, args) => ProjectorColorOption(loc, args)
      case (loc, RiddlOption.kind, args) => ProjectorKindOption(loc, args)
    }
  }

  private def projectionInclude[u: P]: P[IncludeHolder[OccursInProjector]] = {
    include[OccursInProjector, u](projectionDefinitions(_))
  }

  private def projectionDefinitions[u: P]: P[Seq[OccursInProjector]] = {
    P(
      typeDef | term | projectionInclude | handler(StatementsSet.ProjectorStatements) |
        function | inlet | outlet | invariant | constant | typeDef | authorRef | comment
    )./.rep(1)
  }

  private def projectionBody[u: P]: P[Seq[OccursInProjector]] = {
    P(
      undefined(Seq.empty[OccursInProjector]) | projectionDefinitions
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
      location ~ Keywords.projector ~/ identifier ~ is ~ open ~
        projectionOptions ~ projectionBody ~ close ~ briefly ~ description
    ).map { case (loc, id, options, contents, brief, description) =>
      Projector(loc, id, options, contents, brief, description)
    }
  }
}
