/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
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
      case (loc, RiddlOption.kind, args)              => EntityKind(loc, args)
      case (loc, RiddlOption.message_queue, _)        => EntityMessageQueue(loc)
      case (loc, RiddlOption.device, _)               => EntityIsDevice(loc)
      case (loc, RiddlOption.technology, args)        => EntityTechnologyOption(loc, args)
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
        briefly ~ description ~ endOfLineComment
    )./.map { case (loc, id, typRef, body, brief, description, comment) =>
      body match {
        case Some(defs) =>
          val groups = defs.groupBy(_.getClass)
          val handlers = mapTo[Handler](groups.get(classOf[Handler]))
          val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
          State(loc, id, typRef, handlers, invariants, brief, description, comment)
        case None =>
          State(loc, id, typRef, brief = brief, description, comment)
      }
    }
  }

  private def entityInclude[X: P]: P[Include[EntityDefinition]] = {
    include[EntityDefinition, X](entityDefinitions(_))
  }

  private def entityDefinitions[u: P]: P[Seq[EntityDefinition]] = {
    P(
      handler(StatementsSet.EntityStatements) | function | invariant |
        typeDef | state | entityInclude | inlet | outlet | term | constant
    )./.rep(1)
  }

  private def entityBody[u: P]: P[Seq[EntityDefinition]] = {
    P(
      undefined(Seq.empty[EntityDefinition])./ | entityDefinitions./
    )
  }

  def entity[u: P]: P[Entity] = {
    P(
      location ~ Keywords.entity ~/ identifier ~ authorRefs ~ is ~ open ~/
        entityOptions ~ entityBody ~ close ~ briefly ~ description ~ endOfLineComment
    ).map { case (loc, id, authors, options, entityDefs, brief, description, comment) =>
      val groups = entityDefs.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val constants = mapTo[Constant](groups.get(classOf[Constant]))
      val states = mapTo[State](groups.get(classOf[State]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
      val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
      val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include[EntityDefinition]](
        groups.get(
          classOf[Include[EntityDefinition]]
        )
      )
      Entity(
        loc,
        id,
        options,
        states,
        types,
        constants,
        handlers,
        functions,
        invariants,
        inlets,
        outlets,
        includes,
        authors,
        terms,
        brief,
        description,
        comment
      )
    }
  }
}
