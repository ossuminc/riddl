package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for feature definitions This is based on Cucumber's Gherkin language.
  *
  * @see
  *   https://cucumber.io/docs/gherkin/reference/
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
      (location ~ (IgnoreCase(Keywords.else_) | IgnoreCase(Keywords.but)) ~/ docBlock)
        .map(tpl => (But.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock).map(tpl => (But.apply _).tupled(tpl))
          .rep(0)
    ).?.map {
      case Some((initial, remainder)) => initial +: remainder
      case None                       => Seq.empty[But]
    }
  }

  def exampleBody[u: P]: P[(Seq[Given], Seq[When], Seq[Then], Seq[But])] = {
    P(
      (givens.?.map(_.getOrElse(Seq.empty[Given])) ~ whens.?.map(_.getOrElse(Seq.empty[When])) ~
        thens ~ buts.?.map(_.getOrElse(Seq.empty[But]))) |
        undefined((Seq.empty[Given], Seq.empty[When], Seq.empty[Then], Seq.empty[But]))
    )
  }

  def example[u: P]: P[Example] = {
    P(
      location ~ (IgnoreCase(Keywords.example) | IgnoreCase(Keywords.scenario)) ~/ identifier ~
        is.? ~ open ~/ exampleBody ~ close ~ description
    ).map { case (loc, id, (g, w, t, e), desc) => Example(loc, id, g, w, t, e, desc) }
  }

  def examples[u: P]: P[Seq[Example]] = { P(example.rep(0)) }

  def background[u: P]: P[Background] = {
    P(location ~ IgnoreCase(Keywords.background) ~/ open ~/ givens ~ close)
      .map(tpl => (Background.apply _).tupled(tpl))
  }

  def feature[u: P]: P[Feature] = {
    P(
      location ~ IgnoreCase(Keywords.feature) ~/ identifier ~ is ~ open ~
        (undefined((None, Seq.empty[Example])) | (background.? ~ examples)) ~ close ~ description
    ).map { case (loc, id, (background, examples), desc) =>
      Feature(loc, id, background, examples, desc)
    }
  }
}
