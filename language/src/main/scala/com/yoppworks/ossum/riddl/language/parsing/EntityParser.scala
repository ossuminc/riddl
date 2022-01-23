package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Options}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for entity definitions */
trait EntityParser
    extends HandlerParser with TypeParser with GherkinParser with FunctionParser {

  def entityOptions[X: P]: P[Seq[EntityOption]] = {
    options[X, EntityOption](
      StringIn(
        Options.eventSourced,
        Options.value,
        Options.aggregate,
        Options.persistent,
        Options.consistent,
        Options.available,
        Options.stateMachine,
        Options.kind
      ).!
    ) {
      case (loc, Options.eventSourced, _) => EntityEventSourced(loc)
      case (loc, Options.value, _) => EntityValueOption(loc)
      case (loc, Options.aggregate, _) => EntityAggregate(loc)
      case (loc, Options.persistent, _) => EntityPersistent(loc)
      case (loc, Options.consistent, _) => EntityConsistent(loc)
      case (loc, Options.available, _) => EntityAvailable(loc)
      case (loc, Options.stateMachine, _) => EntityFiniteStateMachine(loc)
      case (loc, Options.kind, args) => EntityKind(loc, args)
      case _ => throw new RuntimeException("Impossible case")
    }
  }

  /** Parses an invariant of an entity, i.e.
    *
    * {{{
    *   invariant large is { "x is greater or equal to 10" }
    * }}}
    */
  def invariant[u: P]: P[Invariant] = {
    P(
      Keywords.invariant ~/ location ~ identifier ~ is ~ open ~
        (undefined(Seq.empty[LiteralString]) | docBlock) ~ close ~ description
    ).map(tpl => (Invariant.apply _).tupled(tpl))
  }

  def state[u: P]: P[State] = {
    P(location ~ Keywords.state ~/ identifier ~ is ~ aggregation ~ description)
      .map(tpl => (State.apply _).tupled(tpl))
  }

  def entityDefinition[u: P]: P[EntityDefinition] = {
    P(handler | feature | function | invariant | typeDef | state)
  }

  type EntityBody = (Option[Seq[EntityOption]], Seq[EntityDefinition])

  def noEntityBody[u: P]: P[EntityBody] = {
    P(undefined(Option.empty[Seq[EntityOption]] -> Seq.empty[EntityDefinition]))
  }

  def entityBody[u: P]: P[EntityBody] = entityOptions.? ~ entityDefinition.rep

  def entity[u: P]: P[Entity] = {
    P(
      location ~ Keywords.entity ~/ identifier ~ is ~ open ~/
        (noEntityBody | entityBody) ~ close ~ description
    ).map { case (loc, id, (options, entityDefs), addendum) =>
      val groups = entityDefs.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val states = mapTo[State](groups.get(classOf[State]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
      Entity(
        loc,
        id,
        options.fold(Seq.empty[EntityOption])(identity),
        states,
        types,
        handlers,
        functions,
        invariants,
        addendum
      )
    }
  }
}
