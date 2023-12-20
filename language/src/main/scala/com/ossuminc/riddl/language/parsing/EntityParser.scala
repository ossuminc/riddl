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

  private def stateDefinitions[u: P]: P[Seq[StateDefinition]] = {
    P(handler(StatementsSet.EntityStatements) | invariant).rep(0)
  }

  private def stateBody[u: P]: P[Seq[StateDefinition]] = {
    P(undefined(Seq.empty[StateDefinition]) | stateDefinitions)
  }

  private def state[u: P]: P[State] = {
    P(
      location ~ Keywords.state ~ identifier ~/ Readability.of ~
        typeRef ~/ is ~ (open ~ stateBody ~ close).? ~/
        briefly ~ description ~ comments
    )./.map { case (loc, id, typRef, body, brief, description, comments) =>
      body match {
        case Some(defs) =>
          val groups = defs.groupBy(_.getClass)
          val handlers = mapTo[Handler](groups.get(classOf[Handler]))
          val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
          State(loc, id, typRef, handlers, invariants, brief, description, comments)
        case None =>
          State(loc, id, typRef, brief = brief, description, comments)
      }
    }
  }

  private def entityInclude[X: P]: P[Include[EntityValue]] = {
    include[EntityValue, X](entityValues(_))
  }

  private def entityValues[u: P]: P[Seq[EntityValue]] = {
    P(
      handler(StatementsSet.EntityStatements) | function | invariant |
        typeDef | state | entityInclude | inlet | outlet | term | constant
    )./.rep(1)
  }

  private def entityBody[u: P]: P[Seq[EntityValue]] = {
    P(
      undefined(Seq.empty[EntityValue])./ | entityValues./
    )
  }

  def entity[u: P]: P[Entity] = {
    P(
      location ~ Keywords.entity ~/ identifier ~ authorRefs ~ is ~ open ~/
        entityOptions ~ entityBody ~ close ~ briefly ~ description ~ comments
    ).map { case (loc, id, authors, options, entityDefs, brief, description, comments) =>
      Entity(
        loc,
        id,
        entityDefs ++ authors ++ options,
        brief,
        description,
        comments
      )
    }
  }
}
