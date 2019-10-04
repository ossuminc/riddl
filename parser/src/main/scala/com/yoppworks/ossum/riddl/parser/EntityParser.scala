package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._
import CommonParser._
import TypesParser._
import com.yoppworks.ossum.riddl.parser.FeatureParser.feature

/** Parsing rules for entity definitions  */
object EntityParser {

  def entityOption[_: P]: P[EntityOption] = {
    P(StringIn("device", "aggregate", "persistent", "consistent", "available")).!.map {
      case "device" ⇒ EntityDevice
      case "aggregate"  => EntityAggregate
      case "persistent" => EntityPersistent
      case "consistent" => EntityConsistent
      case "available"  => EntityAvailable
    }
  }

  def invariant[_: P]: P[InvariantDef] = {
    P(
      "invariant" ~/ Index ~ identifier ~ "=" ~ literalString ~ explanation
    ).map(tpl ⇒ (InvariantDef.apply _).tupled(tpl))
  }

  def entityDef[_: P]: P[EntityDef] = {
    P(
      Index ~
        "entity" ~/ identifier.log("id") ~
        is.log("is") ~/ typeExpression.log("type") ~ "{" ~
        ("options" ~/ is ~ entityOption.rep(1)).? ~
        ("consumes" ~/ channelRef).? ~
        ("produces" ~/ channelRef).? ~
        feature.rep(0) ~
        invariant.rep(0) ~
        "}" ~/
        explanation
    ).log("entity").map { tpl ⇒
      (EntityDef.apply _).tupled(tpl)
    }
  }

}
