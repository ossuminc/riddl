/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Parsing rules for Context definitions */
private[parsing] trait ContextParser {
  this: ProcessorParser & AdaptorParser & EntityParser & ProjectorParser & RepositoryParser & SagaParser &
    StreamingParser =>

  private def contextInclude[u: P]: P[Include[ContextContents]] = {
    include[u, ContextContents](contextDefinitions(_))
  }

  private def contextDefinition[u: P]: P[ContextContents] = {
    P(
      processorDefinitionContents(StatementsSet.ContextStatements) |
        entity | adaptor | saga | streamlet | projector | repository | connector | contextInclude | comment
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
      location ~ Keywords.context ~/ identifier ~ is ~ open ~ contextBody ~ close ~ withDescriptives
    )./.map { case (loc, id, contents, descriptives) =>
      checkForDuplicateIncludes(contents)
      Context(loc, id, contents, descriptives)
    }
  }
}
