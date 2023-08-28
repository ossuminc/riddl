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
private[parsing] trait ConditionParser extends ReferenceParser with CommonParser {

  def value[u: P]: P[Value] = {
    P(
      decimalValue | integerValue | booleanValue | computedValue | constantValue | fieldValue |
        arbitraryValue | functionCallValue
    )
  }

  private def arbitraryValue[u: P]: P[ArbitraryValue] = {
    P(location ~ literalString).map(tpl => (ArbitraryValue.apply _).tupled(tpl))
  }

  def booleanValue[u: P]: P[BooleanValue] = {
    P(location ~ StringIn(Keywords.true_, Keywords.false_).!).map { (t: (At, String)) =>
      {
        val loc = t._1
        t._2 match {
          case Keywords.true_  => BooleanValue(loc, true)
          case Keywords.false_ => BooleanValue(loc, false)
          case x: String =>
            error(s"Invalid boolean value: $x")
            BooleanValue(loc, false)
        }
      }
    }
  }

  private def fieldValue[u: P]: P[FieldValue] = {
    P(location ~ fieldRef)
      .map { case (loc, ref) => FieldValue(loc, ref.pathId) }
  }

  private def constantValue[u: P]: P[ConstantValue] = {
    P(location ~ constantRef)
      .map { case (loc, ref) => ConstantValue(loc, ref.pathId) }
  }
  private def arithmeticOperator[u: P]: P[String] = {
    P(StringIn("+", "-", "*", "/", "%").!)
  }

  private def namedOperator[u: P]: P[String] = {
    (CharPred(x => x >= 'a' && x <= 'z').! ~~ CharsWhile(x =>
      (x >= 'a' && x < 'z') ||
        (x >= 'A' && x <= 'Z') ||
        (x >= '0' && x <= '9') || (x == '_')
    )).!
  }

  private def operatorName[u: P]: P[String] = {
    P(
      arithmeticOperator.! | namedOperator
    )
  }

  def operatorValues[u: P]: P[Seq[Value]] = {
    Punctuation.roundOpen ~
      value.rep(0, ",", 64) ~
      Punctuation.roundClose./
  }

  def computedValue[u: P]: P[ComputedValue] = {
    P(
      location ~ operatorName ~ operatorValues
    ).map { tpl => (ComputedValue.apply _).tupled(tpl) }
  }

  private def parameters[u: P]: P[Seq[(Identifier, Value)]] = {
    P(
      undefined[u, Seq[(Identifier, Value)]](
        Seq.empty[(Identifier, Value)]
      ) |
        (identifier ~ Punctuation.equalsSign ~ value)
          .rep(min = 0, Punctuation.comma)
    )
  }

  def parameterValues[u: P]: P[ParameterValues] = {
    P(location ~ Punctuation.roundOpen ~/ parameters ~ Punctuation.roundClose./)
      .map { case (loc, args) =>
        val mapping = Map.from(args)
        ParameterValues(loc, mapping)
      }
  }

  private def functionCallValue[u: P]: P[FunctionCallValue] = {
    P(
      location ~ functionRef ~ parameterValues
    ).map { tpl => (FunctionCallValue.apply _).tupled(tpl) }
  }
  
  def condition[u: P]: P[Condition] = {
    P(terminalCondition | logicalConditions | functionCallCondition )
  }

  private def terminalCondition[u: P]: P[Condition] = {
    P(trueCondition | falseCondition | arbitraryCondition)
  }

  def trueCondition[u: P]: P[True] = {
    P(location ~ IgnoreCase("true")).map((loc) => True(loc))./
  }

  def falseCondition[u: P]: P[False] = {
    P(location ~ IgnoreCase("false")).map(loc => False(loc))./
  }

  private def arbitraryCondition[u: P]: P[ArbitraryCondition] = {
    P(literalString).map(ls => ArbitraryCondition(ls.loc, ls))
  }

  private def functionCallCondition[u: P]: P[FunctionCallCondition] = {
    P(location ~ functionRef ~ parameterValues)
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

}
