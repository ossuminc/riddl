package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Options

/** Parsing rules for entity definitions */
trait EntityParser
    extends CommonParser
    with TypeParser
    with FeatureParser
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
        Options.available
      ).!
    ) {
      case (loc, Options.eventSourced) => EntityAggregate(loc)
      case (loc, Options.value)        => EntityValueOption(loc)
      case (loc, Options.aggregate)    => EntityAggregate(loc)
      case (loc, Options.persistent)   => EntityPersistent(loc)
      case (loc, Options.consistent)   => EntityConsistent(loc)
      case (loc, Options.available)    => EntityAvailable(loc)
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
  def invariant[_: P]: P[Invariant] = {
    P(
      Keywords.invariant ~/ location ~ identifier ~ is ~ open ~
        (undefined.map(_ => Seq.empty[LiteralString]) | docBlock) ~ close ~ description
    ).map(tpl => (Invariant.apply _).tupled(tpl))
  }

  def state[_: P]: P[State] = {
    P(location ~ Keywords.state ~/ identifier ~ is ~ typeExpression ~ description)
      .map(tpl => (State.apply _).tupled(tpl))
  }

  def entityDefinition[_: P]: P[EntityDefinition] = {
    P(handler | feature | function | invariant | typeDef | state)
  }

  def entity[_: P]: P[Entity] = {
    P(
      entityKind ~ location ~ Keywords.entity ~/ identifier ~ is ~ open ~/
        ((location ~ undefined).map { loc: Location =>
          (Seq.empty[EntityOption], Seq.empty[EntityDefinition])
        } | (entityOptions ~ entityDefinition.rep)) ~ close ~ description
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
        options,
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
