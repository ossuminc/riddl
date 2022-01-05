package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.Operators
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For ConditionParser */
trait ConditionParser extends ExpressionParser {

  def referenceCondition[u: P]: P[ReferenceCondition] = {
    P(location ~ pathIdentifier).map { case (loc, pid) => ReferenceCondition(loc, pid) }
  }

  def trueCondition[u: P]: P[True] = { P(location ~ IgnoreCase("true")).map(True)./ }

  def falseCondition[u: P]: P[False] = { P(location ~ IgnoreCase("false")).map(False)./ }

  def comparator[u: P]: P[String] = { ("<=" | "!=" | "==" | ">=" | "<" | ">").!./ }

  def comparisonCondition[u: P]: P[Comparison] = {
    P(
      location ~ comparator ~ Punctuation.roundOpen ~/ expression ~ Punctuation.comma ~ expression ~
        Punctuation.roundClose./
    ).map { x => (Comparison.apply _).tupled(x) }
  }

  def expressionCondition[u: P]: P[ExpressionCondition] = {
    P(location ~ identifier ~ argList ~ description)
      .map(tpl => (ExpressionCondition.apply _).tupled(tpl))
  }

  def notCondition[u: P]: P[NotCondition] = {
    P(location ~ Operators.not ~ Punctuation.roundOpen ~/ condition ~ Punctuation.roundClose./)
      .map(t => (NotCondition.apply _).tupled(t))
  }

  def orCondition[u: P]: P[OrCondition] = {
    P(
      location ~ Operators.or ~ Punctuation.roundOpen ~/ condition ~ Punctuation.comma ~ condition ~
        Punctuation.roundClose./
    ).map(t => (OrCondition.apply _).tupled(t))
  }

  def andCondition[u: P]: P[AndCondition] = {
    P(
      location ~ Operators.and ~ Punctuation.roundOpen ~/ condition ~ Punctuation.comma ~
        condition ~ Punctuation.roundClose./
    ).map(t => (AndCondition.apply _).tupled(t))
  }

  def grouping[u: P]: P[Condition] = {
    P(Punctuation.roundOpen ~/ condition ~ Punctuation.roundClose./)
  }

  def logicalExpressions[u: P]: P[Condition] = {
    P(orCondition | andCondition | notCondition | comparisonCondition | expressionCondition)
  }

  def terminalExpressions[u: P]: P[Condition] = { P(trueCondition | falseCondition) }

  def condition[u: P]: P[Condition] = {
    P(terminalExpressions | grouping | logicalExpressions | referenceCondition)
  }
}
