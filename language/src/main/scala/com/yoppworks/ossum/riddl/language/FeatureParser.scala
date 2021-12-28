package com.yoppworks.ossum.riddl.language

import AST.*
import fastparse.*
import ScalaWhitespace.*
import Terminals.Keywords
import Terminals.Readability

/** Parsing rules for feature definitions This is based on Cucumber's Gherkin language.
 *
 * @see
 * https://cucumber.io/docs/gherkin/reference/
 */
trait FeatureParser extends CommonParser {

  def givens[u: P]: P[Seq[Given]] = {
    P(
      (location ~ IgnoreCase(Keywords.given_) ~/ docBlock).map(tpl => (Given.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock).map(tpl => (Given.apply _).tupled(tpl))
          .rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def whens[u: P]: P[Seq[When]] = {
    P(
      (location ~ IgnoreCase(Keywords.when) ~/ docBlock).map(tpl => (When.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock).map(tpl => (When.apply _).tupled(tpl))
          .rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def thens[u: P]: P[Seq[Then]] = {
    P(
      (location ~ IgnoreCase(Keywords.then_) ~/ docBlock).map(tpl => (Then.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock).map(tpl => (Then.apply _).tupled(tpl))
          .rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def buts[u: P]: P[Seq[But]] = {
    P(
      (location ~ IgnoreCase(Keywords.but) ~/ docBlock).map(tpl => (But.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock).map(tpl => (But.apply _).tupled(tpl))
          .rep(0)
    ).?.map {
      case Some((initial, remainder)) => initial +: remainder
      case None => Seq.empty[But]
    }
  }

  def exampleBody[u: P]: P[(Seq[Given], Seq[When], Seq[Then], Seq[But])] = {
    P(
      (givens ~ whens ~ thens ~ buts) |
        undefined((Seq.empty[Given], Seq.empty[When], Seq.empty[Then], Seq.empty[But]))
    )
  }

  def namedExample[u: P]: P[Example] = {
    P(
      location ~ (IgnoreCase(Keywords.example) | IgnoreCase(Keywords.scenario)) ~/ identifier ~
        open ~/ exampleBody ~ close ~ description
    ).map { case (loc, id, (g, w, t, e), desc) => Example(loc, id, g, w, t, e, desc) }
  }

  def examples[u: P]: P[Seq[Example]] = {namedExample.rep(1) }

  def background[u: P]: P[Background] = {
    P(location ~ IgnoreCase(Keywords.background) ~/ open ~/ givens ~ close)
      .map(tpl => (Background.apply _).tupled(tpl))
  }

  def feature[u: P]: P[Feature] = {
    P(
      location ~ IgnoreCase(Keywords.feature) ~/ identifier ~ is ~ open ~
        (undefined((None, Seq.empty[Example])) | (background.? ~ examples)) ~ close ~ description
    ).map { case (loc, id, (bkgrnd, examples), desc) => Feature(loc, id, bkgrnd, examples, desc) }
  }
}
