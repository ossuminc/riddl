/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Unit Tests For FunctionParser */
private[parsing] trait ProjectorParser {
  this: ProcessorParser & StreamingParser =>

  private def projectorInclude[u: P]: P[Include[ProjectorContents]] = {
    include[u, ProjectorContents](projectorDefinitions(_))
  }

  private def updates[u: P]: P[RepositoryRef] =
    P( Keywords.updates ~ repositoryRef )

  private def projectorDefinitions[u: P]: P[Seq[ProjectorContents]] = {
    P(
      processorDefinitionContents(StatementsSet.ProjectorStatements) | updates | projectorInclude
    ).asInstanceOf[P[ProjectorContents]]./.rep(1)
  }

  private def projectorBody[u: P]: P[Seq[ProjectorContents]] = {
    P(
      undefined(Seq.empty[ProjectorContents]) | projectorDefinitions
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
      Index ~ Keywords.projector ~/ identifier ~ is ~ open ~ projectorBody ~ close ~ withMetaData ~ Index
    )./.map { case (start, id, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Projector(at(start,end), id, contents.toContents, descriptives.toContents)
    }
  }
}
