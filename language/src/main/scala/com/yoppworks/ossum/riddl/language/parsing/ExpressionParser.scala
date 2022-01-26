package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Operators, Punctuation}
import fastparse.*
import fastparse.ScalaWhitespace.*

import scala.collection.immutable.ListMap

/** Parser rules for value expressions */
trait ExpressionParser extends CommonParser with ReferenceParser {

  def arguments[u: P]: P[ListMap[Identifier, Expression]] = {
    P(identifier ~ Punctuation.equals ~ expression).rep(min = 0, Punctuation.comma).map {
      s: Seq[(Identifier, Expression)] =>
        s.foldLeft(ListMap.empty[Identifier, Expression]) { case (b, (id, exp)) =>
          b + (id -> exp)
        }
    }
  }

  def argList[u: P]: P[ArgList] = {
    P(
      Punctuation.roundOpen ~/ arguments ~ Punctuation.roundClose./
    ).map { lm => ArgList(lm) }
  }

  def functionCallExpression[u: P]: P[FunctionCallExpression] = {
    P(location ~ pathIdentifier ~ argList).map {
      tpl => (FunctionCallExpression.apply _).tupled(tpl)
    }
  }

  def valueExpression[u: P]: P[ValueExpression] = {
    P(location ~ pathIdentifier).map(tpl => (ValueExpression.apply _).tupled(tpl))
  }

  def groupExpression[u: P]: P[GroupExpression] = {
    P(location ~ Punctuation.roundOpen ~/ expression ~ Punctuation.roundClose./)
      .map(tpl => (GroupExpression.apply _).tupled(tpl))
  }

  def operatorName[u: P]: P[String] = {
    CharPred(x => x >= 'a' && x <= 'z').! ~~
      CharsWhile(x => (x >= 'a' && x < 'z') || (x >= 'A' && x <= 'Z') || (x >= '0' && x <= '9')).!
  }.map { case (x, y) => x + y }

  def arithmeticOperator[u: P]: P[ArithmeticOperator] = {
    P(
      location ~ (Operators.plus.! | Operators.minus.! | Operators.times.! |
        Operators.div.! | Operators.mod.! | operatorName) ~
        Punctuation.roundOpen ~ expression.rep(0, Punctuation.comma) ~ Punctuation.roundClose
    ).map { tpl => (ArithmeticOperator.apply _).tupled(tpl) }
  }

  def arbitraryExpression[u: P]: P[ArbitraryExpression] = {
    P(location ~ literalString).map(tpl => (ArbitraryExpression.apply _).tupled(tpl))
  }


  def expression[u: P]: P[Expression] = {
    arithmeticOperator | functionCallExpression | valueExpression | arbitraryExpression
      | groupExpression | literalDecimal | literalInteger
  }
}
