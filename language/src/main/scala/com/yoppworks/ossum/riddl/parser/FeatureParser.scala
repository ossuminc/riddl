package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._
import fastparse._
import ScalaWhitespace._

/** Parsing rules for entity feature definitions */
trait FeatureParser extends CommonParser {

  def givens[_: P]: P[Seq[Given]] = {
    P(
      (location ~ IgnoreCase("given") ~/ literalString).map(
        tpl => (Given.apply _).tupled(tpl)
      ) ~
        (location ~ IgnoreCase("and") ~/ literalString)
          .map(
            tpl => (Given.apply _).tupled(tpl)
          )
          .rep(0)
    ).map {
      case (initial, remainder) => initial +: remainder
    }
  }

  def whens[_: P]: P[Seq[When]] = {
    P(
      (location ~ IgnoreCase("when") ~/ literalString).map(
        tpl => (When.apply _).tupled(tpl)
      ) ~
        (location ~ IgnoreCase("and") ~/ literalString)
          .map(
            tpl => (When.apply _).tupled(tpl)
          )
          .rep(0)
    ).map {
      case (initial, remainder) => initial +: remainder
    }
  }

  def thens[_: P]: P[Seq[Then]] = {
    P(
      (location ~ IgnoreCase("then") ~/ literalString).map(
        tpl => (Then.apply _).tupled(tpl)
      ) ~
        (location ~ IgnoreCase("and") ~/ literalString)
          .map(
            tpl => (Then.apply _).tupled(tpl)
          )
          .rep(0)
    ).map {
      case (initial, remainder) => initial +: remainder
    }
  }

  def example[_: P]: P[ExampleDef] = {
    P(
      location ~ IgnoreCase("example") ~/ identifier ~ open ~/ literalString ~
        givens ~
        whens ~ thens ~
        close ~ addendum
    ).map { tpl =>
      (ExampleDef.apply _).tupled(tpl)
    }
  }

  def background[_: P]: P[Background] = {
    P(location ~ IgnoreCase("background") ~/ open ~/ givens)
      .map(tpl => (Background.apply _).tupled(tpl)) ~ close
  }

  def description[_: P]: P[Seq[LiteralString]] = {
    P(IgnoreCase("description") ~/ literalStrings(""))
  }

  def featureDef[_: P]: P[FeatureDef] = {
    P(
      location ~ IgnoreCase("feature") ~/ identifier ~ open ~
        description ~ background.? ~ example.rep(1) ~
        close ~/ addendum
    ).map { tpl =>
      (FeatureDef.apply _).tupled(tpl)
    }
  }
}
