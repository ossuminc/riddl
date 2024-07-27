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

/** Parsing rules for entity definitions */
private[parsing] trait EntityParser {
  this: FunctionParser
    & HandlerParser
    & ReferenceParser
    & StatementParser
    & StreamingParser
    & TypeParser =>

  private def stateDefinitions[u: P]: P[Seq[OccursInState]] = {
    P(handler(StatementsSet.EntityStatements) | invariant).rep(0)
  }

  private def stateBody[u: P]: P[Seq[OccursInState]] = {
    P(undefined(Seq.empty[OccursInState]) | stateDefinitions)
  }

  private def state[u: P]: P[State] = {
    P(
      location ~ Keywords.state ~ identifier ~/ Readability.of ~
        typeRef ~/ is ~ (open ~ stateBody ~ close).? ~/
        briefly ~ description
    )./.map { case (loc, id, typRef, body, brief, description) =>
      body match {
        case Some(defs) =>
          val groups = defs.groupBy(_.getClass)
          val handlers = mapTo[Handler](groups.get(classOf[Handler]))
          val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
          State(loc, id, typRef, handlers, invariants, brief, description)
        case None =>
          State(loc, id, typRef, brief = brief, description = description)
      }
    }
  }

  private def entityInclude[u: P]: P[IncludeHolder[OccursInEntity]] = {
    include[u, OccursInEntity](entityDefinitions(_))
  }

  private def entityDefinitions[u: P]: P[Seq[OccursInEntity]] = {
    P(
      handler(StatementsSet.EntityStatements) | function | invariant | state | entityInclude | inlet | outlet |
        typeDef | term | authorRef | comment | constant | option
    )./.rep(1)
  }

  private def entityBody[u: P]: P[Seq[OccursInEntity]] = {
    P(
      undefined(Seq.empty[OccursInEntity])./ | entityDefinitions./
    )
  }

  def entity[u: P]: P[Entity] = {
    P(
      location ~ Keywords.entity ~/ identifier ~ is ~ open ~/ entityBody ~ close ~ briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      val mergedContent = mergeAsynchContent[OccursInEntity](contents)
      Entity(loc, id, mergedContent, brief, description)
    }
  }
}
