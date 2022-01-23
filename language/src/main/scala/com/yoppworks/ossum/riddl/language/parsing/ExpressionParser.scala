package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Operators, Punctuation}
import fastparse.*
import fastparse.ScalaWhitespace.*

import scala.collection.immutable.ListMap

/** Parser rules for value expressions */
trait ExpressionParser extends CommonParser with ReferenceParser {

  def arguments[u: P]: P[ListMap[Identifier, Expression]] = {
    P(identifier ~ Punctuation.equals ~ expression).rep(0, ",").map {
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

  def fieldExpression[u: P]: P[FieldExpression] = {
    P(location ~ pathIdentifier).map(tpl => (FieldExpression.apply _).tupled(tpl))
  }

  def groupExpression[u: P]: P[GroupExpression] = {
    P(location ~ Punctuation.roundOpen ~/ expression ~ Punctuation.roundClose./)
      .map(tpl => (GroupExpression.apply _).tupled(tpl))
  }

  def operator[u: P]: P[String] = {
    P(
      Operators.plus.! | Operators.minus.! | Operators.times.! | Operators.div.! | Operators.mod.! |
        CharsWhile(x => x >= 'a' && x < 'z').!
    )
  }

  def mathExpression[u: P]: P[MathExpression] = {
    P(location ~ operator ~ argList).map(tpl => (MathExpression.apply _).tupled(tpl))
  }

  def unknownExpression[u: P]: P[UnknownExpression] = {
    P((location ~ Punctuation.undefined).map(loc => UnknownExpression(loc)))
  }

  def expression[u: P]: P[Expression] = {
    mathExpression | functionCallExpression | groupExpression | literalInteger | literalDecimal |
      fieldExpression | unknownExpression
  }
}
