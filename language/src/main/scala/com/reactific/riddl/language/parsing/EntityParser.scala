/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import fastparse.*
import fastparse.ScalaWhitespace.*
import Terminals.*

/** Parsing rules for entity definitions */
private[parsing] trait EntityParser
    extends TypeParser
    with HandlerParser
    with StreamingParser {

  private def entityOptions[X: P]: P[Seq[EntityOption]] = {
    options[X, EntityOption](
      StringIn(
        Options.eventSourced,
        Options.value,
        Options.aggregate,
        Options.transient,
        Options.consistent,
        Options.available,
        Options.finiteStateMachine,
        Options.kind,
        Options.messageQueue,
        Options.device,
        Options.technology
      ).!
    ) {
      case (loc, Options.eventSourced, _) => EntityEventSourced(loc)
      case (loc, Options.value, _)        => EntityValueOption(loc)
      case (loc, Options.aggregate, _)    => EntityIsAggregate(loc)
      case (loc, Options.transient, _)    => EntityTransient(loc)
      case (loc, Options.consistent, _)   => EntityIsConsistent(loc)
      case (loc, Options.available, _)    => EntityIsAvailable(loc)
      case (loc, Options.`finiteStateMachine`, _) =>
        EntityIsFiniteStateMachine(loc)
      case (loc, Options.kind, args)       => EntityKind(loc, args)
      case (loc, Options.messageQueue, _)  => EntityMessageQueue(loc)
      case (loc, Options.device, _)        => EntityIsDevice(loc)
      case (loc, Options.technology, args) => EntityTechnologyOption(loc, args)
      case _ => throw new RuntimeException("Impossible case")
    }
  }

  type StateThings = (Aggregation, Seq[StateDefinition])

  private def stateDefinition[u: P]: P[StateThings] = {
    P(aggregation ~ (typeDef | handler | invariant).rep(0)).map {
      case (agg, stateDefs) => (agg, stateDefs)
    }
  }

  private def stateBody[u: P]: P[StateThings] = {
    P(
      undefined(
        (
          Aggregation(At.empty, Seq.empty[Field]),
          Seq.empty[StateDefinition]
        )
      ) | stateDefinition
    )
  }

  private def state[u: P]: P[State] = {
    P(
      location ~ Keywords.state ~/ identifier ~ is ~ open ~ stateBody ~ close ~
        briefly ~ description
    ).map { case (loc, id, (agg, things), brief, desc) =>
      val groups = things.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
      State(loc, id, agg, types, handlers, invariants, brief, desc)
    }
  }

  private def entityInclude[X: P]: P[Include[EntityDefinition]] = {
    include[EntityDefinition, X](entityDefinitions(_))
  }

  private def entityDefinitions[u: P]: P[Seq[EntityDefinition]] = {
    P(
      handler | function | invariant | typeDef | state | entityInclude |
        inlet | outlet | term
    ).rep
  }

  private type EntityBody = (Option[Seq[EntityOption]], Seq[EntityDefinition])

  private def noEntityBody[u: P]: P[EntityBody] = {
    P(undefined(Option.empty[Seq[EntityOption]] -> Seq.empty[EntityDefinition]))
  }

  private def entityBody[u: P]: P[EntityBody] = P(
    entityOptions.? ~ entityDefinitions
  )

  def entity[u: P]: P[Entity] = {
    P(
      location ~ Keywords.entity ~/ identifier ~ authorRefs ~ is ~ open ~/
        (noEntityBody | entityBody) ~ close ~ briefly ~ description
    ).map { case (loc, id, authorRefs, (options, entityDefs), briefly, desc) =>
      val groups = entityDefs.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val states = mapTo[State](groups.get(classOf[State]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
      val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
      val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
      val includes = mapTo[Include[EntityDefinition]](
        groups.get(
          classOf[Include[EntityDefinition]]
        )
      )
      val terms = mapTo[Term](groups.get(classOf[Term]))
      Entity(
        loc,
        id,
        options.fold(Seq.empty[EntityOption])(identity),
        states,
        types,
        handlers,
        functions,
        invariants,
        inlets,
        outlets,
        includes,
        authorRefs,
        terms,
        briefly,
        desc
      )
    }
  }
}
