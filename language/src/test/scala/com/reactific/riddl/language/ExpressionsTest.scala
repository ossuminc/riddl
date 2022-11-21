package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.ListMap

/** Unit Tests For Expressions */
class ExpressionsTest extends AnyWordSpec with Matchers {

  val aggCE = AggregateConstructionExpression(
    At.empty,
    PathIdentifier(At.empty, Seq("a", "b")),
    ArgList(ListMap(
      Identifier(At.empty, "arg") -> LiteralInteger(At.empty, BigInt(1))
    ))
  )
  val arbitrary = ArbitraryOperator(At.empty, LiteralString(At.empty, "doit"),
    ArgList(ListMap(
      Identifier(At.empty, "arg") -> LiteralInteger(At.empty, BigInt(1))
    ))
  )
  val arith = ArithmeticOperator(
    At.empty,
    "*",
    Seq(
      LiteralInteger(At.empty, BigInt(1)),
      LiteralInteger(At.empty, BigInt(1))
    )
  )
  val entId =
    EntityIdExpression(At.empty, PathIdentifier(At.empty, Seq("a", "b")))

  val func = FunctionCallExpression(
    At.empty,
    PathIdentifier(At.empty, Seq("a", "b")),
    ArgList(ListMap(
      Identifier(At.empty, "arg") -> LiteralInteger(At.empty, BigInt(1))
    ))
  )
  val valueEx =
    ValueExpression(At.empty, PathIdentifier(At.empty, Seq("a", "b")))
  val undef = UndefinedExpression(At.empty)

  "Expressions" must {
    "format correctly" in {
      aggCE.format mustBe "a.b(arg=1)"
      arith.format mustBe "*(1,1)"
      entId.format mustBe "new Id(a.b)"
      func.format mustBe "a.b(arg=1)"
      valueEx.format mustBe "@a.b"
      undef.format mustBe "???"
    }
    "must have the right value type" in {
      aggCE.expressionType mustBe Abstract(At.empty)
      arith.expressionType mustBe Number(At.empty)
      entId.expressionType mustBe UniqueId(At.empty, entId.entityId)
      func.expressionType mustBe Abstract(At.empty)
      valueEx.expressionType mustBe Abstract(At.empty)
      undef.expressionType mustBe Abstract(At.empty)
    }
  }
}
