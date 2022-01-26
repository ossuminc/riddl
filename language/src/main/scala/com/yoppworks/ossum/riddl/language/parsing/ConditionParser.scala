package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Operators, Punctuation}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For ConditionParser */
trait ConditionParser extends ExpressionParser {

  def trueCondition[u: P]: P[True] = {
    P(location ~ IgnoreCase("true")).map(True)./
  }

  def falseCondition[u: P]: P[False] = {
    P(location ~ IgnoreCase("false")).map(False)./
  }

  def comparator[u: P]: P[Comparator] = {
    P("<=" | "!=" | "==" | ">=" | "<" | ">" | "le" | "ne" | "eq" | "ge" | "lt" | "gt").!./.map {
      case "==" | "eq" => AST.eq
      case "!=" | "ne" => AST.ge
      case "<" | "lt" => AST.lt
      case "<=" | "le" => AST.le
      case ">" | "gt" => AST.gt
      case ">=" | "ge" => AST.ge
    }
  }

  def comparisonCondition[u: P]: P[Comparison] = {
    P(
      location ~ comparator ~ Punctuation.roundOpen ~/ condition ~ Punctuation.comma ~ condition ~
        Punctuation.roundClose./
    ).map { x => (Comparison.apply _).tupled(x) }
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

  def arbitraryCondition[u: P]: P[ArbitraryCondition] = {
    P(literalString).map(ls => ArbitraryCondition(ls))
  }

  def groupedCondition[u: P]: P[Condition] = {
    P(Punctuation.roundOpen ~/ condition ~ Punctuation.roundClose./)
  }

  def callCondition[u: P]: P[FunctionCallCondition] = {
    P(location ~ pathIdentifier ~ argList).map(tpl => (FunctionCallCondition.apply _).tupled(tpl))
  }

  def terminalExpressions[u: P]: P[Condition] = {
    P(trueCondition | falseCondition | arbitraryCondition)
  }

  def logicalExpressions[u: P]: P[Condition] = {
    P(orCondition | andCondition | notCondition | comparisonCondition | callCondition)
  }

  def referenceCondition[u: P]: P[ReferenceCondition] = {
    P(location ~ pathIdentifier).map { case (loc, pid) => ReferenceCondition(loc, pid) }
  }

  def undefinedCondition[u: P]: P[UndefinedCondition] = {
    P(location ~ Punctuation.undefined).map(UndefinedCondition)
  }

  def condition[u: P]: P[Condition] = {
    P(terminalExpressions | groupedCondition | logicalExpressions | referenceCondition |
      undefinedCondition)
  }
}
