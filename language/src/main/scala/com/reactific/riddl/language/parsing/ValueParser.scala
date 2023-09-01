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
private[parsing] trait ValueParser extends TypeParser with ReferenceParser with CommonParser {

  def value[u: P]: P[Value] = {
    P(
      decimalValue | integerValue | booleanValue | computedValue | constantValue | fieldValue |
        arbitraryValue | functionCallValue | messageValue
    )
  }

  private def arbitraryValue[u: P]: P[ArbitraryValue] = {
    P(location ~ literalString).map(tpl => (ArbitraryValue.apply _).tupled(tpl))
  }

  private def booleanValue[u: P]: P[BooleanValue] = {
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

  private def operatorValues[u: P]: P[Seq[Value]] = {
    Punctuation.roundOpen ~
      value.rep(0, ",", 64) ~
      Punctuation.roundClose./
  }

  def computedValue[u: P]: P[ComputedValue] = {
    P(
      location ~ operatorName ~ operatorValues ~ (Readability.as ~ typeExpression).?
    ).map { case (loc, opName, opValues, maybeTe) =>
      val te = maybeTe.getOrElse(Abstract(loc))
      ComputedValue(loc, opName, opValues)
    }
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

  def argumentValues[u: P]: P[ArgumentValues] = {
    P(location ~ Punctuation.roundOpen ~/ parameters ~ Punctuation.roundClose./)
      .map { case (loc, args) =>
        val mapping = Map.from(args)
        ArgumentValues(loc, mapping)
      }
  }

  private def functionCallValue[u: P]: P[FunctionCallValue] = {
    P(
      location ~ functionRef ~ argumentValues
    ).map { tpl => (FunctionCallValue.apply _).tupled(tpl) }
  }

  private def messageValue[u:P]: P[MessageValue] = {
    P(
      location ~ messageRef ~ argumentValues
    ).map { tpl => (MessageValue.apply _).tupled(tpl) }
  }
}
