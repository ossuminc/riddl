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
    with HandlerParser
    with ReferenceParser
    with StatementParser
    with StreamingParser
    with TypeParser =>

  private def entityOptions[X: P]: P[Seq[EntityOption]] = {
    options[X, EntityOption](
      RiddlOptions.entityOptions
    ) {
      case (loc, RiddlOption.event_sourced, _)        => EntityEventSourced(loc)
      case (loc, RiddlOption.value, _)                => EntityValueOption(loc)
      case (loc, RiddlOption.aggregate, _)            => EntityIsAggregate(loc)
      case (loc, RiddlOption.transient, _)            => EntityTransient(loc)
      case (loc, RiddlOption.consistent, _)           => EntityIsConsistent(loc)
      case (loc, RiddlOption.available, _)            => EntityIsAvailable(loc)
      case (loc, RiddlOption.finite_state_machine, _) => EntityIsFiniteStateMachine(loc)
      case (loc, RiddlOption.kind, args)              => EntityKindOption(loc, args)
      case (loc, RiddlOption.message_queue, _)        => EntityMessageQueue(loc)
      case (loc, RiddlOption.technology, args)        => EntityTechnologyOption(loc, args)
      case (loc, RiddlOption.color, args)             => EntityColorOption(loc, args)
    }
  }

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
          State(loc, id, typRef, brief = brief, description)
      }
    }
  }

  private def entityInclude[X: P]: P[Include[OccursInEntity]] = {
    include[OccursInEntity, X](entityDefinitions(_))
  }

  private def entityDefinitions[u: P]: P[Seq[OccursInEntity]] = {
    P(
      handler(StatementsSet.EntityStatements) | function | invariant | state | entityInclude | inlet | outlet |
        typeDef | term | authorRef | comment | constant
    )./.rep(1)
  }

  private def entityBody[u: P]: P[Seq[OccursInEntity]] = {
    P(
      undefined(Seq.empty[OccursInEntity])./ | entityDefinitions./
    )
  }

  def entity[u: P]: P[Entity] = {
    P(
      location ~ Keywords.entity ~/ identifier ~ is ~ open ~/
        entityOptions ~ entityBody ~ close ~ briefly ~ description
    ).map { case (loc, id, options, contents, brief, description) =>
      Entity(loc, id, options, contents, brief, description)
    }
  }
}
