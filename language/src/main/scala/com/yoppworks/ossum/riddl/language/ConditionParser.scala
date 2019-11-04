package com.yoppworks.ossum.riddl.language

import AST._
import Terminals.Punctuation
import Terminals._
import fastparse._
import ScalaWhitespace._

/** Unit Tests For ConditionParser */
trait ConditionParser extends CommonParser {

  def referenceCondition[_: P]: P[ReferenceCondition] = {
    P(location ~ pathIdentifier).map {
      case (loc, pid) => ReferenceCondition(loc, pid)
    }
  }

  def trueCondition[_: P]: P[True] = {
    P(location ~ IgnoreCase("true")).map(True)./
  }

  def falseCondition[_: P]: P[False] = {
    P(location ~ IgnoreCase("false")).map(False)./
  }

  def emptyCondition[_: P]: P[Empty] = {
    P(location ~ "empty" ~/ pathIdentifier).map {
      case (loc, pid) =>
        Empty(loc, pid)
    }
  }

  def notCondition[_: P]: P[NotCondition] = {
    P(location ~ Operators.not ~/ condition)
      .map(t => (NotCondition.apply _).tupled(t))
  }

  def orCondition[_: P]: P[OrCondition] = {
    P(location ~ condition ~ Operators.or ~/ condition)
      .map(t => (OrCondition.apply _).tupled(t))
  }

  def andCondition[_: P]: P[AndCondition] = {
    P(location ~ condition ~ Operators.and ~/ condition)
      .map(t => (AndCondition.apply _).tupled(t))
  }

  def grouping[_: P]: P[Condition] = {
    P(Punctuation.roundOpen ~/ condition ~ Punctuation.roundClose./)
  }

  def logicalExpressions[_: P]: P[Condition] = {
    P(orCondition | andCondition | notCondition)
  }

  def miscellaneous[_: P]: P[Miscellaneous] = {
    P(
      location ~ "misc" ~/
        Punctuation.roundOpen ~/ docBlock ~ Punctuation.roundClose./
    ).map(x => Miscellaneous(x._1, x._2))
  }

  def terminalExpressions[_: P]: P[Condition] = {
    P(
      miscellaneous | trueCondition | falseCondition | emptyCondition |
        referenceCondition
    )
  }

  def condition[_: P]: P[Condition] = {
    P(terminalExpressions | grouping | logicalExpressions)
  }
}
