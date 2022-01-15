package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Options
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for entity definitions */
trait EntityParser
    extends CommonParser
    with TypeParser
    with GherkinParser
    with TopicParser
    with FunctionParser
    with HandlerParser {

  def entityOptions[X: P]: P[Seq[EntityOption]] = {
    options[X, EntityOption](
      StringIn(
        Options.eventSourced,
        Options.value,
        Options.aggregate,
        Options.persistent,
        Options.consistent,
        Options.available,
        Options.stateMachine
      ).!
    ) {
      case (loc, Options.eventSourced) => EntityEventSourced(loc)
      case (loc, Options.value)        => EntityValueOption(loc)
      case (loc, Options.aggregate)    => EntityAggregate(loc)
      case (loc, Options.persistent)   => EntityPersistent(loc)
      case (loc, Options.consistent)   => EntityConsistent(loc)
      case (loc, Options.available)    => EntityAvailable(loc)
      case (loc, Options.stateMachine) => EntityFiniteStateMachine(loc)
      case _                           => throw new RuntimeException("Impossible case")
    }
  }

  def entityKind[X: P]: P[EntityKind] = {
    P(location ~ (Options.device | Options.concept | Options.actor).!.?).map {
      case (loc, Some(Options.device))  => DeviceEntityKind(loc)
      case (loc, Some(Options.actor))   => ActorEntityKind(loc)
      case (loc, Some(Options.concept)) => ConceptEntityKind(loc)
      case (loc, None)                  => ConceptEntityKind(loc)
      case _                            => throw new RuntimeException("Impossible case")
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
    P(location ~ Keywords.state ~/ identifier ~ is ~ typeExpression ~ description)
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
      entityKind ~ location ~ Keywords.entity ~/ identifier ~ is ~ open ~/
        (noEntityBody | entityBody) ~ close ~ description
    ).map { case (kind, loc, id, (options, entityDefs), addendum) =>
      val groups = entityDefs.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val states = mapTo[State](groups.get(classOf[State]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val features = mapTo[Feature](groups.get(classOf[Feature]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
      Entity(
        kind,
        loc,
        id,
        options.fold(Seq.empty[EntityOption])(identity),
        states,
        types,
        handlers,
        features,
        functions,
        invariants,
        addendum
      )
    }
  }
}
