package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._
import Terminals.Keywords
import Terminals.Punctuation
import Terminals.Readability

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
      (location ~ IgnoreCase(Keywords.when) ~/ literalString).map(
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
      (location ~ IgnoreCase(Readability.then_) ~/ literalString).map(
        tpl => (Then.apply _).tupled(tpl)
      ) ~
        (location ~ IgnoreCase(Readability.and) ~/ literalString)
          .map(
            tpl => (Then.apply _).tupled(tpl)
          )
          .rep(0)
    ).map {
      case (initial, remainder) => initial +: remainder
    }
  }

  def example[_: P]: P[Example] = {
    P(
      location ~ IgnoreCase(Keywords.example) ~/ identifier ~ open ~/ literalString ~
        givens ~
        whens ~ thens ~
        close ~ addendum
    ).map { tpl =>
      (Example.apply _).tupled(tpl)
    }
  }

  def background[_: P]: P[Background] = {
    P(
      location ~ IgnoreCase(Keywords.background) ~/ open ~/
        givens
    ).map(tpl => (Background.apply _).tupled(tpl)) ~ close
  }

  def description[_: P]: P[Seq[LiteralString]] = {
    P(IgnoreCase(Keywords.description) ~/ docBlock)
  }

  def featureDef[_: P]: P[Feature] = {
    P(
      location ~
        IgnoreCase(Keywords.feature) ~/
        identifier ~ is ~
        open ~
        description ~ background.? ~ example.rep(1) ~
        close ~
        addendum
    ).map { tpl =>
      (Feature.apply _).tupled(tpl)
    }
  }
}
