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
trait GherkinParser extends CommonParser {

  def givens[u: P]: P[Seq[GherkinClause]] = {
    P(
      (location ~ IgnoreCase(Keywords.given_) ~/ docBlock)
        .map(tpl => (GherkinClause.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock)
          .map(tpl => (GherkinClause.apply _).tupled(tpl)).rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def whens[u: P]: P[Seq[GherkinClause]] = {
    P(
      (location ~ IgnoreCase(Keywords.when) ~/ docBlock)
        .map(tpl => (GherkinClause.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock)
          .map(tpl => (GherkinClause.apply _).tupled(tpl)).rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def thens[u: P]: P[Seq[GherkinClause]] = {
    P(
      (location ~ IgnoreCase(Keywords.then_) ~/ docBlock)
        .map(tpl => (GherkinClause.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock)
          .map(tpl => (GherkinClause.apply _).tupled(tpl)).rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def buts[u: P]: P[Seq[GherkinClause]] = {
    P(
      (location ~ (IgnoreCase(Keywords.else_) | IgnoreCase(Keywords.but)) ~/ docBlock)
        .map(tpl => (GherkinClause.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock)
          .map(tpl => (GherkinClause.apply _).tupled(tpl)).rep(0)
    ).?.map {
      case Some((initial, remainder)) => initial +: remainder
      case None                       => Seq.empty[GherkinClause]
    }
  }

  def exampleBody[
    u: P
  ]: P[(Seq[GherkinClause], Seq[GherkinClause], Seq[GherkinClause], Seq[GherkinClause])] = {
    P(
      (givens.?.map(_.getOrElse(Seq.empty[GherkinClause])) ~
        whens.?.map(_.getOrElse(Seq.empty[GherkinClause])) ~ thens ~
        buts.?.map(_.getOrElse(Seq.empty[GherkinClause]))) | undefined((
        Seq.empty[GherkinClause],
        Seq.empty[GherkinClause],
        Seq.empty[GherkinClause],
        Seq.empty[GherkinClause]
      ))
    )
  }

  def example[u: P]: P[Example] = {
    P(
      location ~ (IgnoreCase(Keywords.example) | IgnoreCase(Keywords.scenario)) ~/ identifier ~
        is.? ~ open ~/ exampleBody ~ close ~ description
    ).map { case (loc, id, (g, w, t, e), desc) => Example(loc, id, g, w, t, e, desc) }
  }

  def examples[u: P]: P[Seq[Example]] = { P(example.rep(0)) }

  def feature[u: P]: P[Feature] = {
    P(
      location ~ IgnoreCase(Keywords.feature) ~/ identifier ~ is ~ open ~
        (undefined(Seq.empty[Example]) | examples) ~ close ~ description
    ).map { case (loc, id, examples, desc) => Feature(loc, id, examples, desc) }
  }
}
