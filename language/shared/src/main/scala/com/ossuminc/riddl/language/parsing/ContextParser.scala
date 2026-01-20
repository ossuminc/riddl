/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{map => _, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Parsing rules for Context definitions */
private[parsing] trait ContextParser {
  this: ProcessorParser & AdaptorParser & EntityParser & ProjectorParser & RepositoryParser & SagaParser &
    StreamingParser & GroupParser =>

  private def contextInclude[u: P]: P[Include[ContextContents]] = {
    include[u, ContextContents](contextDefinitions(_))
  }

  private def contextDefinition[u: P]: P[ContextContents] = {
    P(
      // GROUP 1: Most common - core DDD entities (40-50%)
      entity | repository |
      // GROUP 2: Common - types and functions (15-20%)
      processorDefinitionContents(StatementsSet.ContextStatements) |
      // GROUP 3: Common - integration components (15-20%)
      adaptor | projector |
      // GROUP 4: Moderate - orchestration and streaming (10-15%)
      saga | streamlet |
      // GROUP 5: Less common - UI, connectivity, includes (5-10%)
      group | connector | contextInclude | comment
    ).asInstanceOf[P[ContextContents]]
  }

  private def contextDefinitions[u: P]: P[Seq[ContextContents]] = {
    contextDefinition./.rep(1)
  }

  private def contextBody[u: P]: P[Seq[ContextContents]] = {
    P(
      undefined(Seq.empty[ContextContents])./ | contextDefinitions./
    )
  }

  def context[u: P]: P[Context] = {
    P(
      Index ~ Keywords.context ~/ identifier ~ is ~ open ~ contextBody ~ close ~ withMetaData ~ Index
    )./.map { case (start, id, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Context(at(start, end), id, contents.toContents, descriptives.toContents)
    }
  }
}
