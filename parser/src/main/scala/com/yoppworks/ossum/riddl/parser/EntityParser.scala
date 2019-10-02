package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._
import CommonParser._
import TypesParser._
import com.yoppworks.ossum.riddl.parser.FeatureParser.feature

/** Parsing rules for entity definitions  */
object EntityParser {

  def commandDef[_: P]: P[CommandDef] = {
    P(
      "command" ~ Index ~/ identifier ~ "=" ~ typeExpression ~ "yields" ~
        eventRefs ~ explanation
    ).map { tpl ⇒
      (CommandDef.apply _).tupled(tpl)
    }
  }

  def eventRefs[_: P]: P[EventRefs] = {
    P("event" ~~ "s".? ~/ identifier.rep(1, P(","))).map(_.map(EventRef))
  }

  def eventDef[_: P]: P[EventDef] = {
    P(
      "event" ~ Index ~/ identifier ~ "=" ~ typeExpression ~ explanation
    ).map { tpl ⇒
      (EventDef.apply _).tupled(tpl)
    }
  }

  def queryDef[_: P]: P[QueryDef] = {
    P(
      "query" ~ Index ~/ identifier ~ "=" ~ typeExpression ~ "yields" ~
        resultRef ~ explanation
    ).map { tpl ⇒
      (QueryDef.apply _).tupled(tpl)
    }
  }

  def resultDef[_: P]: P[ResultDef] = {
    P(
      "result" ~ Index ~/ identifier ~ "=" ~ typeExpression ~ explanation
    ).map { tpl ⇒
      (ResultDef.apply _).tupled(tpl)
    }
  }

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
        entityOption.rep(0) ~ "entity" ~/ identifier ~ "=" ~
        typeExpression ~
        ("consumes" ~ channelRef).? ~
        ("produces" ~/ channelRef).? ~
        feature.rep(0) ~
        invariant.rep(0) ~
        explanation
    ).map { tpl ⇒
      (EntityDef.apply _).tupled(tpl)
    }
  }

}
