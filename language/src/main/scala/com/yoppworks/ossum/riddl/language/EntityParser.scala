package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Options

/** Parsing rules for entity definitions  */
trait EntityParser
    extends CommonParser
    with TypeParser
    with FeatureParser
    with TopicParser
    with ActionParser
    with ConsumerParser {

  def entityOptions[X: P]: P[Seq[EntityOption]] = {
    options[X, EntityOption](
      StringIn(
        Options.aggregate,
        Options.persistent,
        Options.consistent,
        Options.available
      ).!
    ) {
      case (loc, Options.aggregate)  => EntityAggregate(loc)
      case (loc, Options.persistent) => EntityPersistent(loc)
      case (loc, Options.consistent) => EntityConsistent(loc)
      case (loc, Options.available)  => EntityAvailable(loc)
      case _                         => throw new RuntimeException("Impossible case")
    }
  }

  def entityKind[X: P]: P[EntityKind] = {
    P(location ~ (Options.device | Options.software | Options.person).!.?).map {
      case (loc, Some(Options.device))   => DeviceEntityKind(loc)
      case (loc, Some(Options.person))   => PersonEntityKind(loc)
      case (loc, Some(Options.software)) => SoftwareEntityKind(loc)
      case (loc, None)                   => SoftwareEntityKind(loc)
      case _                             => throw new RuntimeException("Impossible case")
    }
  }

  def invariant[_: P]: P[Invariant] = {
    P(
      Keywords.invariant ~/ location ~ identifier ~ is ~ open ~
        (undefined.map(_ => Seq.empty[LiteralString]) |
          docBlock("")) ~
        close ~ description
    ).map(tpl => (Invariant.apply _).tupled(tpl))
  }

  def state[_: P]: P[Aggregation] = {
    P(Keywords.state ~/ is ~ aggregation)
  }

  def entityDefinition[_: P]: P[EntityDefinition] = {
    P(
      consumer | feature | action | invariant
    )
  }

  def entity[_: P]: P[Entity] = {
    P(
      entityKind ~ location ~ Keywords.entity ~/ identifier ~ is ~ open ~/
        ((location ~ undefined).map { loc: Location =>
          (
            Seq.empty[EntityOption],
            Aggregation(loc),
            Seq.empty[EntityDefinition]
          )
        } | (
          entityOptions ~
            state ~
            entityDefinition.rep
        )) ~ close ~ description
    ).map {
      case (kind, loc, id, (options, state, entityDefs), addendum) =>
        val groups = entityDefs.groupBy(_.getClass)
        val consumers = mapTo[Consumer](groups.get(classOf[Consumer]))
        val features = mapTo[Feature](groups.get(classOf[Feature]))
        val actions = mapTo[Function](groups.get(classOf[Function]))
        val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
        Entity(
          kind,
          loc,
          id,
          state,
          options,
          consumers,
          features,
          actions,
          invariants,
          addendum
        )
    }
  }
}
