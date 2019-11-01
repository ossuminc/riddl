package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Options
import com.yoppworks.ossum.riddl.language.Terminals.Readability

/** Parsing rules for entity definitions  */
trait EntityParser
    extends CommonParser
    with TypeParser
    with FeatureParser
    with TopicParser
    with FunctionParser {

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
      Keywords.invariant ~/ location ~ identifier ~ is ~ docBlock("") ~ addendum
    ).map(tpl => (Invariant.apply _).tupled(tpl))
  }

  def state[_: P]: P[Aggregation] = {
    P(Keywords.state ~/ is ~ aggregationType)
  }

  def setAction[_: P]: P[SetAction] = {
    (Keywords.set ~/ location ~ pathIdentifier ~ Terminals.Readability.to ~
      pathIdentifier).map { t =>
      (SetAction.apply _).tupled(t)
    }
  }

  def appendAction[_: P]: P[AppendAction] = {
    (Keywords.append ~/ location ~ pathIdentifier ~ Terminals.Readability.to ~
      identifier).map { t =>
      (AppendAction.apply _).tupled(t)
    }
  }

  def sendAction[_: P]: P[SendAction] = {
    (Keywords.send ~/ location ~ messageRef ~ Terminals.Readability.to ~
      topicRef).map { t =>
      (SendAction.apply _).tupled(t)
    }
  }

  def removeAction[_: P]: P[RemoveAction] = {
    (Keywords.remove ~/ location ~ pathIdentifier ~ Readability.from ~
      pathIdentifier).map { t =>
      (RemoveAction.apply _).tupled(t)
    }
  }

  def onClauseAction[_: P]: P[OnClauseAction] = {
    P(setAction | appendAction | sendAction | removeAction)
  }

  def onClause[_: P]: P[OnClause] = {
    Keywords.on ~/ location ~ messageRef ~ open ~ onClauseAction.rep ~ close
  }.map(t => (OnClause.apply _).tupled(t))

  def consumer[_: P]: P[Consumer] = {
    P(
      Keywords.consumer ~/ location ~ identifier ~ Readability.for_ ~ topicRef ~
        optionalNestedContent(onClause) ~ addendum
    ).map(t => (Consumer.apply _).tupled(t))
  }

  def entityDefinition[_: P]: P[EntityDefinition] = {
    P(
      consumer |
        featureDef |
        functionDef |
        invariant
    )
  }

  def entityDef[_: P]: P[Entity] = {
    P(
      entityKind ~ location ~ Keywords.entity ~/ identifier ~ is ~ open ~/
        entityOptions ~
        state ~
        entityDefinition.rep ~
        close ~/
        addendum
    ).map {
      case (kind, loc, id, options, state, entityDefs, addendum) =>
        val groups = entityDefs.groupBy(_.getClass)
        val consumers = mapTo[Consumer](groups.get(classOf[Consumer]))
        val features = mapTo[Feature](groups.get(classOf[Feature]))
        val functions = mapTo[Function](groups.get(classOf[Function]))
        val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
        Entity(
          kind,
          loc,
          id,
          state,
          options,
          consumers,
          features,
          functions,
          invariants,
          addendum
        )
    }
  }
}
