package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.{Keywords, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for feature definitions This is based on Cucumber's Gherkin language.
  *
  * @see
  *   https://cucumber.io/docs/gherkin/reference/
  */
trait GherkinParser extends ActionParser {

  def givens[u: P]: P[Seq[GivenClause]] = {
    P(
      (location ~ IgnoreCase(Keywords.given_) ~/ docBlock)
        .map(tpl => (GivenClause.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ docBlock)
          .map(tpl => (GivenClause.apply _).tupled(tpl)).rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def whens[u: P]: P[Seq[WhenClause]] = {
    P(
      (location ~ IgnoreCase(Keywords.when) ~/ condition)
        .map(tpl => (WhenClause.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ condition)
          .map(tpl => (WhenClause.apply _).tupled(tpl)).rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def thens[u: P]: P[Seq[ThenClause]] = {
    P(
      (location ~ IgnoreCase(Keywords.then_) ~/ anyAction)
        .map(tpl => (ThenClause.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ anyAction)
          .map(tpl => (ThenClause.apply _).tupled(tpl)).rep(0)
    ).map { case (initial, remainder) => initial +: remainder }
  }

  def buts[u: P]: P[Seq[ButClause]] = {
    P(
      (location ~ (IgnoreCase(Keywords.else_) | IgnoreCase(Keywords.but)) ~/ anyAction)
        .map(tpl => (ButClause.apply _).tupled(tpl)) ~
        (location ~ IgnoreCase(Readability.and) ~/ anyAction)
          .map(tpl => (ButClause.apply _).tupled(tpl)).rep(0)
    ).?.map {
      case Some((initial, remainder)) => initial +: remainder
      case None                       => Seq.empty[ButClause]
    }
  }

  def exampleBody[u: P]: P[(Seq[GivenClause], Seq[WhenClause], Seq[ThenClause], Seq[ButClause])] = {
    P(
      (givens.?.map(_.getOrElse(Seq.empty[GivenClause])) ~
        whens.?.map(_.getOrElse(Seq.empty[WhenClause])) ~ thens ~
        buts.?.map(_.getOrElse(Seq.empty[ButClause]))) | undefined(
        (Seq.empty[GivenClause], Seq.empty[WhenClause], Seq.empty[ThenClause], Seq.empty[ButClause])
      )
    )
  }

  def example[u: P]: P[Example] = {
    P(
      location ~ (IgnoreCase(Keywords.example) | IgnoreCase(Keywords.scenario)) ~/ identifier ~
        is.? ~ open ~/ exampleBody ~ close ~ briefly ~ description
    ).map { case (loc, id, (g, w, t, e), brief, desc) => Example(loc, id, g, w, t, e, brief, desc) }
  }

  def testedWithExamples[u: P]: P[Seq[Example]] = {
    P(("tested" ~ ("with" | "by")).? ~ examples).?.map {
      case Some(examples) => examples
      case None           => Seq.empty[Example]
    }
  }

  def examples[u: P]: P[Seq[Example]] = { P(example.rep(0)) }

}
