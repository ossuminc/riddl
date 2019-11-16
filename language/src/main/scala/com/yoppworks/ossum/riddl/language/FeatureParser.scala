package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._
import Terminals.Keywords
import Terminals.Readability

/** Parsing rules for entity feature definitions */
trait FeatureParser extends CommonParser {

  def givens[_: P]: P[Seq[Given]] = {
    P(
      (location ~ IgnoreCase("given") ~/ docBlock).map(
        tpl => (Given.apply _).tupled(tpl)
      ) ~
        (location ~ IgnoreCase("and") ~/ docBlock)
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
      (location ~ IgnoreCase(Keywords.when) ~/ docBlock).map(
        tpl => (When.apply _).tupled(tpl)
      ) ~
        (location ~ IgnoreCase("and") ~/ docBlock)
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
      (location ~ IgnoreCase(Keywords.then_) ~/ docBlock).map(
        tpl => (Then.apply _).tupled(tpl)
      ) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock)
          .map(
            tpl => (Then.apply _).tupled(tpl)
          )
          .rep(0)
    ).map {
      case (initial, remainder) => initial +: remainder
    }
  }

  def elses[_: P]: P[Seq[Else]] = {
    P(
      (location ~ IgnoreCase(Keywords.else_) ~/ docBlock).map(
        tpl => (Else.apply _).tupled(tpl)
      ) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock)
          .map(
            tpl => (Else.apply _).tupled(tpl)
          )
          .rep(0)
    ).?.map {
      case Some((initial, remainder)) => initial +: remainder
      case None                       => Seq.empty[Else]
    }
  }

  def implicitExample[_: P]: P[Example] = {
    P(location ~ givens ~ whens ~ thens ~ elses).map {
      case (loc, g, w, t, e) =>
        Example(loc, Identifier(loc, "implicit"), g, w, t, e, None)
    }
  }

  def namedExample[_: P]: P[Example] = {
    P(
      location ~ IgnoreCase(Keywords.example) ~/ identifier ~ open ~/
        givens ~
        whens ~ thens ~ elses ~
        close ~ description
    ).map { tpl =>
      (Example.apply _).tupled(tpl)
    }
  }

  def examples[_: P]: P[Seq[Example]] = {
    implicitExample.map(Seq(_)) |
      namedExample.rep(1)
  }

  def background[_: P]: P[Background] = {
    P(
      location ~ IgnoreCase(Keywords.background) ~/ open ~/
        givens
    ).map(tpl => (Background.apply _).tupled(tpl)) ~ close
  }

  def feature[_: P]: P[Feature] = {
    P(
      location ~
        IgnoreCase(Keywords.feature) ~/
        identifier ~ is ~ open ~
        (undefined.map(_ => (None, Seq.empty[Example])) |
          (background.? ~ examples)) ~
        close ~ description
    ).map {
      case (loc, id, (bkgrnd, examples), desc) =>
        Feature(loc, id, bkgrnd, examples, desc)
    }
  }
}
