package com.yoppworks.ossum.riddl.language

import AST.*
import fastparse.*
import ScalaWhitespace.*
import Terminals.Keywords
import Terminals.Readability

/** Parsing rules for entity feature definitions */
trait FeatureParser extends CommonParser {

  def givens[u: P]: P[Seq[Given]] = {
    P(
      (location ~ IgnoreCase("given") ~/ docBlock).map(tpl => (Given.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase("and") ~/ docBlock).map(tpl => (Given.apply _).tupled(tpl)).rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def whens[u: P]: P[Seq[When]] = {
    P(
      (location ~ IgnoreCase(Keywords.when) ~/ docBlock).map(tpl => (When.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase("and") ~/ docBlock).map(tpl => (When.apply _).tupled(tpl)).rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def thens[u: P]: P[Seq[Then]] = {
    P(
      (location ~ IgnoreCase(Keywords.then_) ~/ docBlock).map(tpl => (Then.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock).map(tpl => (Then.apply _).tupled(tpl))
          .rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def elses[u: P]: P[Seq[Else]] = {
    P(
      (location ~ IgnoreCase(Keywords.else_) ~/ docBlock).map(tpl => (Else.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock).map(tpl => (Else.apply _).tupled(tpl))
          .rep(0)
    ).?.map {
      case Some((initial, remainder)) => initial +: remainder
      case None                       => Seq.empty[Else]
    }
  }

  def exampleBody[u: P]: P[(Seq[Given], Seq[When], Seq[Then], Seq[Else])] = {
    P(
      (givens ~ whens ~ thens ~ elses) |
        undefined((Seq.empty[Given], Seq.empty[When], Seq.empty[Then], Seq.empty[Else]))
    )
  }

  def implicitExample[u: P]: P[Example] = {
    P(location ~ exampleBody).map { case (loc, (g, w, t, e)) =>
      Example(loc, Identifier(loc, "implicit"), g, w, t, e, None)
    }
  }

  def namedExample[u: P]: P[Example] = {
    P(
      location ~ IgnoreCase(Keywords.example) ~/ identifier ~ open ~/ exampleBody ~ close ~
        description
    ).map { case (loc, id, (g, w, t, e), desc) => Example(loc, id, g, w, t, e, desc) }
  }

  def examples[u: P]: P[Seq[Example]] = { implicitExample.map(Seq(_)) | namedExample.rep(1) }

  def background[u: P]: P[Background] = {
    P(location ~ IgnoreCase(Keywords.background) ~/ open ~/ givens)
      .map(tpl => (Background.apply _).tupled(tpl)) ~ close
  }

  def feature[u: P]: P[Feature] = {
    P(
      location ~ IgnoreCase(Keywords.feature) ~/ identifier ~ is ~ open ~
        (undefined((None, Seq.empty[Example])) | (background.? ~ examples)) ~ close ~ description
    ).map { case (loc, id, (bkgrnd, examples), desc) => Feature(loc, id, bkgrnd, examples, desc) }
  }
}
