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
  this: HandlerParser
    with AdaptorParser
    with EntityParser
    with FunctionParser
    with ProjectorParser
    with ReferenceParser
    with RepositoryParser
    with SagaParser
    with StreamingParser
    with StatementParser
    with TypeParser =>

  private def contextOptions[X: P]: P[Seq[ContextOption]] = {
    options[X, ContextOption](RiddlOptions.contextOptions) {
      case (loc, RiddlOption.wrapper, _)       => ContextWrapperOption(loc)
      case (loc, RiddlOption.gateway, _)       => GatewayOption(loc)
      case (loc, RiddlOption.service, _)       => ServiceOption(loc)
      case (loc, RiddlOption.package_, args)   => ContextPackageOption(loc, args)
      case (loc, RiddlOption.technology, args) => ContextTechnologyOption(loc, args)
      case (loc, RiddlOption.css, args)        => ContextCssOption(loc, args)
      case (loc, RiddlOption.faicon, args)     => ContextIconOption(loc, args)
      case (loc, RiddlOption.kind, args)       => ContextKindOption(loc, args)
    }
  }

  private def contextInclude[X: P]: P[IncludeHolder[OccursInContext]] = {
    include[OccursInContext, X](contextDefinitions(_))
  }

  private def contextDefinition[u: P]: P[OccursInContext] = {
    P(
      typeDef | handler(StatementsSet.ContextStatements) | entity | authorRef |
        adaptor | function | saga | streamlet | projector | repository |
        inlet | outlet | connector | term | contextInclude | comment
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
      location ~ Keywords.context ~/ identifier ~ is ~ open ~
        contextOptions ~ contextBody ~ close ~ briefly ~ description
    ).map { case (loc, id, options, contents, brief, description) =>
      val mergedContent = mergeAsynchContent[OccursInContext](contents)
      Context(
        loc,
        id,
        options,
        mergedContent,
        brief,
        description
      )
    }
  }
}
