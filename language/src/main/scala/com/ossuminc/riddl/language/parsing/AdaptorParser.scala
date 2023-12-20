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

/** Parser rules for Adaptors */
private[parsing] trait AdaptorParser {
  this: HandlerParser
    with FunctionParser
    with StreamingParser
    with StatementParser
    with ReferenceParser
    with TypeParser
    with CommonParser =>

  private def adaptorOptions[u: P]: P[Seq[AdaptorOption]] = {
    options[u, AdaptorOption](RiddlOptions.adaptorOptions) {
      case (loc, RiddlOption.technology, args) => AdaptorTechnologyOption(loc, args)
      case (loc, RiddlOption.color, args)      => AdaptorColorOption(loc, args)
      case (loc, RiddlOption.kind, args)       => AdaptorKindOption(loc, args)
    }
  }

  private def adaptorInclude[u: P]: P[Include[AdaptorValue]] = {
    include[AdaptorValue, u](adaptorValues(_))
  }

  private def adaptorValues[u: P]: P[Seq[AdaptorValue]] = {
    P(
      (handler(StatementsSet.AdaptorStatements) | function | inlet |
        outlet | adaptorInclude | term | constant)./.rep(1)
    )
  }

  private def adaptorBody[u: P]: P[Seq[AdaptorValue]] = {
    undefined(Seq.empty[AdaptorValue])./ | adaptorValues./
  }

  private def adaptorDirection[u: P]: P[AdaptorDirection] = {
    P(location ~ (Readability.from.! | Readability.to.!)).map {
      case (loc, "from") => InboundAdaptor(loc)
      case (loc, "to")   => OutboundAdaptor(loc)
      case (loc, _) =>
        error("Impossible condition at $loc")
        InboundAdaptor(loc)
    }
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      location ~ Keywords.adaptor ~/ identifier ~ authorRefs ~
        adaptorDirection ~ contextRef ~ is ~ open ~ adaptorOptions ~
        adaptorBody ~ close ~ briefly ~ description ~ comments
    ).map {
      case (
            loc,
            id,
            authors,
            direction,
            context,
            options,
            defs,
            brief,
            description,
            comments
          ) =>
        Adaptor(
          loc,
          id,
          direction,
          context,
          defs ++ options ++ authors,
          brief,
          description,
          comments
        )
    }
  }
}
