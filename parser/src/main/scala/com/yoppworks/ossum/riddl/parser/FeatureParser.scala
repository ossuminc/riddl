package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._

import CommonParser._

/** Parsing rules for entity feature definitions */
object FeatureParser {

  def givens[_: P]: P[Seq[Given]] = {
    P(
      IgnoreCase("given") ~/ literalString ~
        P(IgnoreCase("and") ~/ literalString).rep(0)
    ).map {
      case (initial, remainder) ⇒ Given(initial) +: remainder.map(Given)
    }
  }

  def whens[_: P]: P[Seq[When]] = {
    P(
      IgnoreCase("when") ~/ literalString ~
        P(IgnoreCase("and") ~/ literalString).rep(0)
    ).map {
      case (initial, remainder) ⇒ When(initial) +: remainder.map(When)
    }
  }

  def thens[_: P]: P[Seq[Then]] = {
    P(
      IgnoreCase("then") ~/ literalString ~
        P(IgnoreCase("and") ~ literalString).rep(0)
    ).map {
      case (initial, remainder) ⇒ Then(initial) +: remainder.map(Then)
    }
  }

  def example[_: P]: P[Example] = {
    P(
      IgnoreCase("example") ~ "{" ~/ literalString ~ givens ~ whens ~ thens ~
        "}"
    ).map { tpl ⇒
      (Example.apply _).tupled(tpl)
    }
  }

  def background[_: P]: P[Background] = {
    P(IgnoreCase("background") ~ "{" ~/ givens).map(Background) ~ "}"
  }

  def description[_: P]: P[Seq[String]] = {
    P(IgnoreCase("description") ~/ lines)
  }

  def feature[_: P]: P[FeatureDef] = {
    P(
      IgnoreCase("feature") ~/ Index ~ identifier ~ "{" ~
        description ~ background.? ~ example.rep(1) ~
        "}" ~ explanation
    ).map { tpl ⇒
      (FeatureDef.apply _).tupled(tpl)
    }
  }
}
