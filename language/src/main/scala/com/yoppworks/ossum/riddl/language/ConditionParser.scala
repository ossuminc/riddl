package com.yoppworks.ossum.riddl.language

import AST._
import Terminals.Punctuation
import Terminals._
import fastparse._
import ScalaWhitespace._

/** Unit Tests For ConditionParser */
trait ConditionParser extends ExpressionParser {

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

  def comparator[_: P]: P[String] = {
    ("<=" | "!=" | "==" | ">=" | "<" | ">").!./
  }

  def comparisonCondition[_: P]: P[Comparison] = {
    P(
      location ~ comparator ~ Punctuation.roundOpen ~/ expression ~
        Punctuation.comma ~ expression ~ Punctuation.roundClose./
    ).map { x =>
      (Comparison.apply _).tupled(x)
    }
  }

  def expressionCondition[_: P]: P[ExpressionCondition] = {
    P(
      location ~ identifier ~ argList ~ description
    ).map(tpl => (ExpressionCondition.apply _).tupled(tpl))
  }

  def notCondition[_: P]: P[NotCondition] = {
    P(
      location ~ Operators.not ~ Punctuation.roundOpen ~/ condition ~
        Punctuation.roundClose./
    ).map(t => (NotCondition.apply _).tupled(t))
  }

  def orCondition[_: P]: P[OrCondition] = {
    P(
      location ~ Operators.or ~ Punctuation.roundOpen ~/ condition ~
        Punctuation.comma ~ condition ~ Punctuation.roundClose./
    ).map(t => (OrCondition.apply _).tupled(t))
  }

  def andCondition[_: P]: P[AndCondition] = {
    P(
      location ~ Operators.and ~ Punctuation.roundOpen ~/ condition ~
        Punctuation.comma ~ condition ~ Punctuation.roundClose./
    ).map(t => (AndCondition.apply _).tupled(t))
  }

  def grouping[_: P]: P[Condition] = {
    P(Punctuation.roundOpen ~/ condition ~ Punctuation.roundClose./)
  }

  def logicalExpressions[_: P]: P[Condition] = {
    P(
      orCondition | andCondition | notCondition | comparisonCondition |
        expressionCondition
    )
  }

  def terminalExpressions[_: P]: P[Condition] = {
    P(
      trueCondition | falseCondition
    )
  }

  def condition[_: P]: P[Condition] = {
    P(
      terminalExpressions | grouping | logicalExpressions | referenceCondition
    )
  }
}
