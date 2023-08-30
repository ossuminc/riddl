/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import fastparse.*
import fastparse.ScalaWhitespace.*

import Terminals.*

/** Parser rules for value expressions */
private[parsing] trait ConditionParser extends ValueParser with ReferenceParser with CommonParser {

  def condition[u: P]: P[Condition] = {
    P(terminalCondition | logicalConditions | functionCallCondition)
  }

  private def terminalCondition[u: P]: P[Condition] = {
    P(trueCondition | falseCondition | arbitraryCondition)
  }

  def trueCondition[u: P]: P[True] = {
    P(location ~ IgnoreCase("true")).map(loc => True(loc))./
  }

  def falseCondition[u: P]: P[False] = {
    P(location ~ IgnoreCase("false")).map(loc => False(loc))./
  }

  private def arbitraryCondition[u: P]: P[ArbitraryCondition] = {
    P(literalString).map(ls => ArbitraryCondition(ls.loc, ls))
  }

  private def functionCallCondition[u: P]: P[FunctionCallCondition] = {
    P(location ~ functionRef ~ argumentValues)
      .map(tpl => (FunctionCallCondition.apply _).tupled(tpl))
  }

  private def logicalConditions[u: P]: P[Condition] = {
    P(
      orCondition | xorCondition | andCondition | notCondition |
        comparisonCondition
    )
  }

  private def orCondition[u: P]: P[OrCondition] = {
    P(
      location ~ Operators.or ~ Punctuation.roundOpen ~/
        condition.rep(2, Punctuation.comma) ~ Punctuation.roundClose./
    ).map(t => (OrCondition.apply _).tupled(t))
  }

  private def xorCondition[u: P]: P[XorCondition] = {
    P(
      location ~ Operators.xor ~ Punctuation.roundOpen ~
        condition.rep(2, Punctuation.comma) ~ Punctuation.roundClose./
    ).map(tpl => (XorCondition.apply _).tupled(tpl))
  }

  private def andCondition[u: P]: P[AndCondition] = {
    P(
      location ~ Operators.and ~ Punctuation.roundOpen ~/
        condition.rep(2, Punctuation.comma) ~ Punctuation.roundClose./
    ).map(t => (AndCondition.apply _).tupled(t))
  }

  private def notCondition[u: P]: P[NotCondition] = {
    P(
      location ~ Operators.not ~ Punctuation.roundOpen ~/ condition ~
        Punctuation.roundClose./
    ).map(t => (NotCondition.apply _).tupled(t))
  }

  private def comparisonCondition[u: P]: P[Comparison] = {
    P(
      location ~ comparator ~ Punctuation.roundOpen ~/ value ~
        Punctuation.comma ~ value ~ Punctuation.roundClose./
    ).map { x => (Comparison.apply _).tupled(x) }
  }

  private def comparator[u: P]: P[Comparator] = {
    P(StringIn("<=", "!=", "==", ">=", "<", ">")).!./.map {
      case "==" => AST.eq
      case "!=" => AST.ne
      case "<"  => AST.lt
      case "<=" => AST.le
      case ">"  => AST.gt
      case ">=" => AST.ge
    }
  }

  def invariant[u: P]: P[Invariant] = {
    P(
      Keywords.invariant ~/ location ~ identifier ~ is ~ open ~
        (undefined[u, Option[Condition]](Option.empty[Condition]) | condition.?) ~ close ~ briefly ~ description
    ).map { case (loc, id, cond, brief, desc) =>
      Invariant(loc, id, cond, brief, desc)
    }
  }

  def constantValue[u: P]: P[Value] = {
    P(
      trueCondition | falseCondition | decimalValue |
        integerValue | stringValue | computedValue
    )
  }

  def constant[u: P]: P[Constant] = {
    P(
      location ~ Keywords.const ~ identifier ~ is ~ typeExpression ~
        Punctuation.equalsSign ~ constantValue ~
        briefly ~ description
    ).map { tpl => (Constant.apply _).tupled(tpl) }
  }



}
