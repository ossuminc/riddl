package com.yoppworks.ossum.riddl.language

import fastparse._
import ScalaWhitespace._
import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Terminals.Operators
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation

import scala.collection.immutable.ListMap

/** Parser rules for value expressions */
trait ExpressionParser extends CommonParser {

  def arguments[_: P]: P[ListMap[Identifier, Expression]] = {
    P(identifier ~ Punctuation.equals ~ expression).rep(0).map {
      s: Seq[(Identifier, Expression)] =>
        s.foldLeft(ListMap.empty[Identifier, Expression]) {
          case (b, (id, exp)) =>
            b + (id -> exp)
        }
    }
  }

  def functionCallExpression[_: P]: P[FunctionCallExpression] = {
    P(
      location ~ identifier ~ Punctuation.roundOpen ~
        arguments ~ Punctuation.roundClose
    ).map { tpl =>
      (FunctionCallExpression.apply _).tupled(tpl)
    }
  }

  def fieldExpression[_: P]: P[FieldExpression] = {
    P(location ~ pathIdentifier)
      .map(tpl => (FieldExpression.apply _).tupled(tpl))
  }

  def groupExpression[_: P]: P[GroupExpression] = {
    P(location ~ Punctuation.roundOpen ~/ expression ~ Punctuation.roundClose./)
      .map(tpl => (GroupExpression.apply _).tupled(tpl))
  }

  def operator[_: P]: P[String] = {
    P(
      Operators.plus.! | Operators.minus.! | Operators.times.! |
        Operators.div.! | Operators.mod.! |
        CharsWhile(x => x >= 'a' && x < 'z').!
    )
  }

  def argList[_: P]: P[Seq[Expression]] = {
    P(expression.rep(0, ","))
  }

  def mathExpression[_: P]: P[MathExpression] = {
    P(
      location ~ operator ~ Punctuation.roundOpen ~/ argList ~ Punctuation.roundClose /
    ).map(tpl => (MathExpression.apply _).tupled(tpl))
  }

  def expression[_: P]: P[Expression] = {
    mathExpression | functionCallExpression | groupExpression |
      literalInteger | literalDecimal | fieldExpression |
      (location ~ Punctuation.undefined).map(loc => UnknownExpression(loc))
  }
}
