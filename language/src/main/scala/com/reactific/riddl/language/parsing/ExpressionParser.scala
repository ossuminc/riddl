/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.{Operators, Punctuation}
import fastparse.*
import fastparse.ScalaWhitespace.*

import scala.collection.immutable.ListMap

/** Parser rules for value expressions */
trait ExpressionParser extends CommonParser with ReferenceParser {

  ////////////////////////////////////////// Conditions == Boolean Expression

  def condition[u: P]: P[Condition] = {
    P(terminalCondition | logicalConditions | functionCallExpression)
  }

  def terminalCondition[u: P]: P[Condition] = {
    P(trueCondition | falseCondition | conditionExpressions)
  }

  def trueCondition[u: P]: P[True] = {
    P(location ~ IgnoreCase("true")).map(True)./
  }

  def falseCondition[u: P]: P[False] = {
    P(location ~ IgnoreCase("false")).map(False)./
  }

  def conditionExpressions[u: P]: P[Condition & Expression] = {
    undefinedExpression | arbitraryExpression | valueExpression
  }

  def arbitraryExpression[u: P]: P[ArbitraryExpression] = {
    P(literalString).map(ls => ArbitraryExpression(ls))
  }

  def undefinedExpression[u: P]: P[UndefinedExpression] = {
    P(location ~ Punctuation.undefined).map(UndefinedExpression)
  }

  def valueExpression[u: P]: P[ValueExpression] = {
    P(location ~ Punctuation.at ~ pathIdentifier).map(tpl => (ValueExpression.apply _).tupled(tpl))
  }

  def logicalConditions[u: P]: P[Condition] = {
    P(
      orCondition | xorCondition | andCondition | notCondition |
        comparisonCondition
    )
  }

  def orCondition[u: P]: P[OrCondition] = {
    P(
      location ~ Operators.or ~ Punctuation.roundOpen ~/
        condition.rep(2, Punctuation.comma) ~ Punctuation.roundClose./
    ).map(t => (OrCondition.apply _).tupled(t))
  }

  def xorCondition[u: P]: P[XorCondition] = {
    P(
      location ~ Operators.xor ~ Punctuation.roundOpen ~/
        condition.rep(2, Punctuation.comma) ~ Punctuation.roundClose./
    ).map(tpl => (XorCondition.apply _).tupled(tpl))
  }

  def andCondition[u: P]: P[AndCondition] = {
    P(
      location ~ Operators.and ~ Punctuation.roundOpen ~/
        condition.rep(2, Punctuation.comma) ~ Punctuation.roundClose./
    ).map(t => (AndCondition.apply _).tupled(t))
  }

  def notCondition[u: P]: P[NotCondition] = {
    P(
      location ~ Operators.not ~ Punctuation.roundOpen ~/ condition ~
        Punctuation.roundClose./
    ).map(t => (NotCondition.apply _).tupled(t))
  }

  def comparisonCondition[u: P]: P[Comparison] = {
    P(
      location ~ comparator ~ Punctuation.roundOpen ~/ expression ~
        Punctuation.comma ~ expression ~ Punctuation.roundClose./
    ).map { x => (Comparison.apply _).tupled(x) }
  }


  def comparator[u: P]: P[Comparator] = {
    P(StringIn("<=", "!=", "==", ">=", "<", ">")).!./.map {
      case "==" => AST.eq
      case "!=" => AST.ne
      case "<" => AST.lt
      case "<=" => AST.le
      case ">" => AST.gt
      case ">=" => AST.ge
    }
  }

  ////////////////////////////////////////// Expressions == Any Type

  def aggregateConstruction[u: P]: P[AggregateConstructionExpression] = {
    P( location ~ Punctuation.exclamation ~/ typeRef ~ argList).map(tpl =>
      (AggregateConstructionExpression.apply _).tupled(tpl))
  }

  def terminalExpression[u: P]: P[Expression] = {
    P(terminalCondition | literalDecimal | literalInteger |
      aggregateConstruction | conditionExpressions )
  }

  def arguments[u: P]: P[ArgList] = {
    P(identifier ~ Punctuation.equals ~ expression)
      .rep(min = 0, Punctuation.comma).map { s: Seq[(Identifier, Expression)] =>
        s.foldLeft(ListMap.empty[Identifier, Expression]) {
          case (b, (id, exp)) => b + (id -> exp)
        }
      }.map { lm => ArgList(lm) }
  }

  def argList[u: P]: P[ArgList] = {
    P(Punctuation.roundOpen ~/ arguments ~ Punctuation.roundClose./)
  }

  def functionCallExpression[u: P]: P[FunctionCallExpression] = {
    P(location ~ pathIdentifier ~ argList)
      .map(tpl => (FunctionCallExpression.apply _).tupled(tpl))
  }

  def groupExpression[u: P]: P[GroupExpression] = {
    P(location ~ Punctuation.roundOpen ~/ expression ~ Punctuation.roundClose./)
      .map(tpl => (GroupExpression.apply _).tupled(tpl))
  }

  def operatorName[u: P]: P[String] = {
    CharPred(x => x >= 'a' && x <= 'z').! ~~ CharsWhile(x =>
      (x >= 'a' && x < 'z') ||
        (x >= 'A' && x <= 'Z') ||
        (x >= '0' && x <= '9')
    ).!
  }.map { case (x, y) => x + y }

  def arithmeticOperator[u: P]: P[ArithmeticOperator] = {
    P(
      location ~
        (Operators.plus.! | Operators.minus.! | Operators.times.! |
          Operators.div.! | Operators.mod.! | operatorName) ~
        Punctuation.roundOpen ~ expression.rep(0, Punctuation.comma) ~
        Punctuation.roundClose
    ).map { tpl => (ArithmeticOperator.apply _).tupled(tpl) }
  }

  def ternaryExpression[u: P]: P[Ternary] = {
    P(
      location ~ Operators.if_ ~ Punctuation.roundOpen ~ condition ~
        Punctuation.comma ~ expression ~ Punctuation.comma ~ expression ~
        Punctuation.roundClose./
    ).map(tpl => (Ternary.apply _).tupled(tpl))
  }

  def expression[u: P]: P[Expression] = {
    P(
      terminalExpression | ternaryExpression | arithmeticOperator |
        functionCallExpression | groupExpression
    )
  }

}
