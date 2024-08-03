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

/** Parsing rules for Context definitions */
private[parsing] trait ContextParser {
  this: HandlerParser & AdaptorParser & EntityParser & FunctionParser & ProjectorParser & ReferenceParser &
    RepositoryParser & SagaParser & StreamingParser & StatementParser & TypeParser =>

  private def contextInclude[u: P]: P[IncludeHolder[OccursInContext]] = {
    include[u, OccursInContext](contextDefinitions(_))
  }

  private def contextDefinition[u: P]: P[OccursInContext] = {
    P(
      typeDef | handler(StatementsSet.ContextStatements) | entity | authorRef |
        adaptor | function | saga | streamlet | projector | repository |
        inlet | outlet | connector | term | contextInclude | comment | option
    )
  }

  private def contextDefinitions[u: P]: P[Seq[OccursInContext]] = {
    contextDefinition./.rep(1)
  }

  private def contextBody[u: P]: P[Seq[OccursInContext]] = {
    P(
      undefined(Seq.empty[OccursInContext])./ | contextDefinitions./
    )
  }

  def context[u: P]: P[Context] = {
    P(
      location ~ Keywords.context ~/ identifier ~ is ~ open ~ contextBody ~ close ~ briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      Context(
        loc,
        id,
        contents,
        brief,
        description
      )
    }
  }
}
