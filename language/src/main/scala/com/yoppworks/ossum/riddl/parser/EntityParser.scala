package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._

/** Parsing rules for entity definitions  */
trait EntityParser extends CommonParser with TypeParser with FeatureParser {

  def entityOptions[X: P]: P[Seq[EntityOption]] = {
    options[X, EntityOption](
      StringIn(
        "device",
        "aggregate",
        "persistent",
        "consistent",
        "available"
      ).!
    ) {
      case (loc, "device")     => EntityDevice(loc)
      case (loc, "aggregate")  => EntityAggregate(loc)
      case (loc, "persistent") => EntityPersistent(loc)
      case (loc, "consistent") => EntityConsistent(loc)
      case (loc, "available")  => EntityAvailable(loc)
      case _                   => throw new RuntimeException("Impossible case")
    }
  }

  def invariant[_: P]: P[InvariantDef] = {
    P(
      "invariant" ~/ location ~ identifier ~ "=" ~ literalString ~ addendum
    ).map(tpl => (InvariantDef.apply _).tupled(tpl))
  }

  def entityDef[_: P]: P[EntityDef] = {
    P(
      location ~ "entity" ~/ identifier ~ is ~/ typeExpression ~ "{" ~
        entityOptions ~
        ("consumes" ~/ channelRef).? ~
        ("produces" ~/ channelRef).? ~
        featureDef.rep(0) ~
        invariant.rep(0) ~
        "}" ~/ addendum
    ).map { tpl =>
      (EntityDef.apply _).tupled(tpl)
    }
  }

}
