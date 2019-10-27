package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Options
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation
import com.yoppworks.ossum.riddl.language.Terminals.Readability

/** Parsing rules for entity definitions  */
trait EntityParser
    extends CommonParser
    with TypeParser
    with FeatureParser
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

  def invariant[_: P]: P[InvariantDef] = {
    P(
      Keywords.invariant ~/ location ~ identifier ~ lines("") ~ addendum
    ).map(tpl => (InvariantDef.apply _).tupled(tpl))
  }

  def entityDef[_: P]: P[EntityDef] = {
    P(
      entityKind ~ location ~ Keywords.entity ~/ identifier ~
        Readability.as ~/ typeExpression ~ is ~ open ~/
        entityOptions ~
        (Keywords.consumes ~/ topicRef).rep(0) ~
        (Keywords.produces ~/ topicRef).rep(0) ~
        featureDef.rep(0) ~
        functionDef.rep(0) ~
        invariant.rep(0) ~
        close ~/
        addendum
    ).map { tpl =>
      (EntityDef.apply _).tupled(tpl)
    }
  }
}
