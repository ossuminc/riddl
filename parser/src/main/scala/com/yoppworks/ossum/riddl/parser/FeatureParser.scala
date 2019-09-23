package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._

import CommonParser._

/** Unit Tests For FeatureParser */
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
      IgnoreCase("example") ~ ":" ~/ literalString ~ givens ~ whens ~ thens
    ).map { tpl ⇒
      (Example.apply _).tupled(tpl)
    }
  }

  def background[_: P]: P[Background] = {
    P(IgnoreCase("background") ~ ":" ~/ givens).map(Background)
  }

  def feature[_: P]: P[Feature] = {
    P(
      IgnoreCase("feature") ~ ":" ~/ identifier ~ literalString ~
        background.? ~ example.rep(1)
    ).map { tpl ⇒
      (Feature.apply _).tupled(tpl)
    }
  }
}
