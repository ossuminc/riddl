/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import com.ossuminc.riddl.language.AST
import sourcecode.Text.generate

/** Parser rules for Adaptors */
private[parsing] trait AdaptorParser {
  this: ProcessorParser =>

  import scala.concurrent.Future

  private def adaptorInclude[u: P]: P[Include[AdaptorContents]] = {
    include[u, AdaptorContents](adaptorDefinitions(_))
  }

  private def adaptorDefinitions[u: P]: P[Seq[AdaptorContents]] = {
    P(
      processorDefinitionContents(StatementsSet.AdaptorStatements) |
        handler(StatementsSet.AdaptorStatements)
    ).asInstanceOf[P[AdaptorContents]].rep(1)
  }

  private def adaptorBody[u: P]: P[Seq[AdaptorContents]] = {
    undefined(Seq.empty[AdaptorContents])./ | adaptorDefinitions./
  }

  private def adaptorDirection[u: P]: P[AdaptorDirection] = {
    P(location ~ (from.! | to.!)).map {
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
      checkForDuplicateIncludes(contents)
      Adaptor(loc, id, direction, cRef, contents, brief, description)
    }
  }
}
