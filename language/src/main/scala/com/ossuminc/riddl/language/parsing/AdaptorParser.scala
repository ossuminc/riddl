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
import com.ossuminc.riddl.language.AST

/** Parser rules for Adaptors */
private[parsing] trait AdaptorParser {
  this: HandlerParser & FunctionParser & StreamingParser & StatementParser & ReferenceParser & TypeParser &
    CommonParser =>

  private def adaptorOption[u: P]: P[AdaptorOption] = {
    option[u, AdaptorOption](RiddlOptions.adaptorOptions) {
      case (loc, RiddlOption.technology, args) => AdaptorTechnologyOption(loc, args)
      case (loc, RiddlOption.css, args)        => AdaptorCssOption(loc, args)
      case (loc, RiddlOption.faicon, args)     => AdaptorIconOption(loc, args)
      case (loc, RiddlOption.kind, args)       => AdaptorKindOption(loc, args)
    }
  }

  private def adaptorInclude[u: P]: P[IncludeHolder[OccursInAdaptor]] = {
    include[u, OccursInAdaptor](adaptorDefinitions(_))
  }

  private def adaptorDefinitions[u: P]: P[Seq[OccursInAdaptor]] = {
    P(
      (handler(StatementsSet.AdaptorStatements) | function | inlet | adaptorOption |
        outlet | adaptorInclude | term | constant | authorRef | comment)./.rep(1)
    )
  }

  private def adaptorBody[u: P]: P[Seq[OccursInAdaptor]] = {
    undefined(Seq.empty[OccursInAdaptor])./ | adaptorDefinitions./
  }

  private def adaptorDirection[u: P]: P[AdaptorDirection] = {
    P(location ~ (Readability.from.! | Readability.to.!)).map {
      case (loc, "from") => InboundAdaptor(loc)
      case (loc, "to")   => OutboundAdaptor(loc)
      case (loc, str) =>
        error(s"Impossible condition at $loc $str")
        InboundAdaptor(loc)
    }
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~
        adaptorDirection ~ contextRef ~ is ~ open ~ adaptorBody ~ close ~ briefly ~ description
    ).map { case (loc, id, direction, cRef, contents, brief, description) =>
      val mergedContent = mergeAsynchContent[OccursInAdaptor](contents)
      Adaptor(loc, id, direction, cRef, mergedContent, brief, description)
    }
  }
}
