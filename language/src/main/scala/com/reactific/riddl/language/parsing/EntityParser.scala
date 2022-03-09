package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.{Keywords, Options}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for entity definitions */
trait EntityParser extends TypeParser with GherkinParser with FunctionParser {

  def entityOptions[X: P]: P[Seq[EntityOption]] = {
    options[X, EntityOption](
      StringIn(
        Options.eventSourced,
        Options.value,
        Options.aggregate,
        Options.transient,
        Options.consistent,
        Options.available,
        Options.stateMachine,
        Options.kind,
        Options.messageQueue
      ).!
    ) {
      case (loc, Options.eventSourced, _) => EntityEventSourced(loc)
      case (loc, Options.value, _)        => EntityValueOption(loc)
      case (loc, Options.aggregate, _)    => EntityAggregate(loc)
      case (loc, Options.transient, _)    => EntityTransient(loc)
      case (loc, Options.consistent, _)   => EntityConsistent(loc)
      case (loc, Options.available, _)    => EntityAvailable(loc)
      case (loc, Options.stateMachine, _) => EntityFiniteStateMachine(loc)
      case (loc, Options.kind, args)      => EntityKind(loc, args)
      case (loc, Options.messageQueue, _) => EntityMessageQueue(loc)
      case _                              =>
        throw new RuntimeException("Impossible case")
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
      Keywords.invariant ~/ location ~ identifier ~ is ~ open ~ condition ~ close ~ briefly ~
        description
    ).map(tpl => (Invariant.apply _).tupled(tpl))
  }

  def state[u: P]: P[State] = {
    P(location ~ Keywords.state ~/ identifier ~ is ~ aggregation ~ briefly ~ description)
      .map(tpl => (State.apply _).tupled(tpl))
  }

  def onClause[u: P]: P[OnClause] = {
    Keywords.on ~/ location ~ messageRef ~ open ~
      ((location ~ exampleBody).map { case (l, (g, w, t, b)) =>
        Seq(Example(l, Identifier(l, ""), g, w, t, b))
      } | examples | undefined(Seq.empty[Example])) ~ close ~ briefly ~ description
  }.map(t => (OnClause.apply _).tupled(t))

  def handler[u: P]: P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ is ~
        ((open ~ undefined(Seq.empty[OnClause]) ~ close) | optionalNestedContent(onClause)) ~
        briefly ~ description
    ).map(t => (Handler.apply _).tupled(t))
  }

  def entityInclude[X: P]: P[Include] = {
    include[EntityDefinition, X](entityDefinitions(_))
  }

  def entityDefinitions[u: P]: P[Seq[EntityDefinition]] = {
    P(handler | function | invariant | typeDef | state | entityInclude).rep
  }

  type EntityBody = (Option[Seq[EntityOption]], Seq[EntityDefinition])

  def noEntityBody[u: P]: P[EntityBody] = {
    P(undefined(Option.empty[Seq[EntityOption]] -> Seq.empty[EntityDefinition]))
  }

  def entityBody[u: P]: P[EntityBody] = P(entityOptions.? ~ entityDefinitions)

  def entity[u: P]: P[Entity] = {
    P(
      location ~ Keywords.entity ~/ identifier ~ is ~ open ~/ (noEntityBody | entityBody) ~ close ~
        briefly ~ description
    ).map { case (loc, id, (options, entityDefs), briefly, description) =>
      val groups = entityDefs.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val states = mapTo[State](groups.get(classOf[State]))
      val handlers = mapTo[Handler](groups.get(classOf[Handler]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val invariants = mapTo[Invariant](groups.get(classOf[Invariant]))
      val includes = mapTo[Include](groups.get(classOf[Include]))
      Entity(
        loc,
        id,
        options.fold(Seq.empty[EntityOption])(identity),
        states,
        types,
        handlers,
        functions,
        invariants,
        includes,
        briefly,
        description
      )
    }
  }
}
