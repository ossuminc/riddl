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
import com.ossuminc.riddl.utils.PlatformContext

/** Parser rules for Adaptors */
private[parsing] trait AdaptorParser(using io: PlatformContext) {
  this: ProcessorParser =>

  import scala.concurrent.Future

  private def adaptorInclude[u: P]: P[Include[AdaptorContents]] = {
    include[u, AdaptorContents](adaptorContents(_))
  }

  private def adaptorContents[u: P]: P[Seq[AdaptorContents]] = {
    P(
      processorDefinitionContents(StatementsSet.AdaptorStatements) |
        handler(StatementsSet.AdaptorStatements) | adaptorInclude
    ).asInstanceOf[P[AdaptorContents]].rep(1)
  }

  private def adaptorBody[u: P]: P[Seq[AdaptorContents]] = {
    undefined(Seq.empty[AdaptorContents])./ | adaptorContents./
  }

  private def adaptorDirection[u: P]: P[AdaptorDirection] = {
    P(Index ~ (from.! | to.!) ~ Index).map { case (start, str, end) =>
      val loc = at(start, end)
      str match
        case "from" => InboundAdaptor(loc)
        case "to"   => OutboundAdaptor(loc)
        case str: String =>
          error(s"Impossible condition at $loc $str")
          InboundAdaptor(loc)
      end match
    }
  }

  def adaptor[u: P]: P[Adaptor] = {
    P(
      Index ~ Keywords.adaptor ~/ identifier ~
        adaptorDirection ~ contextRef ~ is ~ open ~ adaptorBody ~
        close ~ withMetaData ~ Index
    )./ map { case (start, id, direction, cRef, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Adaptor(at(start, end), id, direction, cRef, contents.toContents, descriptives.toContents)
    }
  }
}
