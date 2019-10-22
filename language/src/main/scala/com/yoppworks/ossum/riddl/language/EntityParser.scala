package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._

/** Parsing rules for entity definitions  */
trait EntityParser
    extends CommonParser
    with TypeParser
    with FeatureParser
    with FunctionParser {

  def entityOptions[X: P]: P[Seq[EntityOption]] = {
    options[X, EntityOption](
      StringIn(
        "aggregate",
        "persistent",
        "consistent",
        "available"
      ).!
    ) {
      case (loc, "aggregate")  => EntityAggregate(loc)
      case (loc, "persistent") => EntityPersistent(loc)
      case (loc, "consistent") => EntityConsistent(loc)
      case (loc, "available")  => EntityAvailable(loc)
      case _                   => throw new RuntimeException("Impossible case")
    }
  }

  def entityKind[X: P]: P[EntityKind] = {
    P(location ~ ("device" | "software" | "person").!.?).map {
      case (loc, Some("device"))   => DeviceEntityKind(loc)
      case (loc, Some("person"))   => PersonEntityKind(loc)
      case (loc, Some("software")) => SoftwareEntityKind(loc)
      case (loc, None)             => SoftwareEntityKind(loc)
      case _                       => throw new RuntimeException("Impossible case")
    }
  }

  def invariant[_: P]: P[InvariantDef] = {
    P(
      "invariant" ~/ location ~ identifier ~ literalStrings("") ~ addendum
    ).map(tpl => (InvariantDef.apply _).tupled(tpl))
  }

  def entityDef[_: P]: P[EntityDef] = {
    P(
      entityKind ~ location ~ "entity" ~/ identifier ~ is ~/ typeExpression ~
        open ~
        entityOptions ~
        ("consumes" ~/ channelRef).rep(0) ~
        ("produces" ~/ channelRef).rep(0) ~
        featureDef.rep(0) ~
        functionDef.rep(0) ~
        invariant.rep(0) ~
        close ~/ addendum
    ).map { tpl =>
      (EntityDef.apply _).tupled(tpl)
    }
  }
}
