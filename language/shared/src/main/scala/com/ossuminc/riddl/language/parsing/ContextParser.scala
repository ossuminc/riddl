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

  private def contextInclude[u: P]: P[Include[ContextContents]] = {
    include[u, ContextContents](contextDefinitions(_))
  }

  private def contextDefinition[u: P]: P[ContextContents] = {
    P(
      typeDef | handler(StatementsSet.ContextStatements) | entity | authorRef |
        adaptor | function | saga | streamlet | projector | repository |
        inlet | outlet | connector | term | contextInclude | comment | option
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
      location ~ Keywords.context ~/ identifier ~ is ~ open ~ contextBody ~ close ~ briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      // mergeFutureContent[OccursInContext](contents) { (contents: Seq[OccursInContext]) =>
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
