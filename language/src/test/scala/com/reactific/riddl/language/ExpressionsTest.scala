package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.ListMap

/** Unit Tests For Expressions */
class ExpressionsTest extends AnyWordSpec with Matchers {

  val int1 = IntegerValue(At.empty, BigInt(1))
  val int2 = IntegerValue(At.empty, BigInt(2))
  val litDec = DecimalValue(At.empty, BigDecimal(42.0))
  val aggCE = AggregateConstructionExpression(
    At.empty,
    PathIdentifier(At.empty, Seq("a", "b")),
    ArgList(ListMap(Identifier(At.empty, "arg") -> int1))
  )
  val arbitrary = ArbitraryOperator(
    At.empty,
    LiteralString(At.empty, "doit"),
    ArgList(ListMap(
      Identifier(At.empty, "arg") -> IntegerValue(At.empty, BigInt(1))
    ))
  )
  val arbitraryEx = ArbitraryExpression(LiteralString(At.empty, "act"))
  val arith = ArithmeticOperator(
    At.empty,
    "*",
    Seq(
      IntegerValue(At.empty, BigInt(1)),
      IntegerValue(At.empty, BigInt(1))
    )
  )
  val comp = Comparison(At.empty, lt, int1, int2)
  val not_ = NotCondition(At.empty, comp)
  val and = AndCondition(At.empty, Seq(comp, not_))
  val or = OrCondition(At.empty, Seq(comp, not_))
  val xor = XorCondition(At.empty, Seq(comp, not_))
  val entId =
    NewEntityIdOperator(At.empty, PathIdentifier(At.empty, Seq("a", "b")))
  val date = DateFunction(At.empty, "today")
  val false_ = False(At.empty)
  val true_ = True(At.empty)

  val func = FunctionCallExpression(
    At.empty,
    PathIdentifier(At.empty, Seq("a", "b")),
    ArgList(ListMap(
      Identifier(At.empty, "arg") -> IntegerValue(At.empty, BigInt(1))
    ))
  )
  val number = NumberFunction(At.empty, "pow")
  val string = StringFunction(At.empty, "temp")
  val ternary = Ternary(At.empty, True(At.empty), int1, int2)
  val timestamp = TimeStampFunction(At.empty, "now")
  val valueCond =
    ValueCondition(At.empty, PathIdentifier(At.empty, Seq("a", "b")))
  val valueEx = ValueOperator(At.empty, PathIdentifier(At.empty, Seq("a", "b")))
  val undef = UndefinedOperator(At.empty)
  val group = GroupExpression(At.empty, Seq(undef, arith))

  "Expressions" must {
    "format correctly" in {
      aggCE.format mustBe "a.b(arg=1)"
      arbitrary.format mustBe "\"doit\"(arg=1)"
      arbitraryEx.format mustBe "\"act\""
      arith.format mustBe "*(1,1)"
      comp.format mustBe "<(1,2)"
      date.format mustBe "today()"
      entId.format mustBe "new Id(a.b)"
      false_.format mustBe "false"
      func.format mustBe "a.b(arg=1)"
      group.format mustBe "(???, *(1,1))"
      litDec.format mustBe "42.0"
      number.format mustBe "pow()"
      string.format mustBe "temp()"
      ternary.format mustBe "if(true,1,2)"
      timestamp.format mustBe "now()"
      true_.format mustBe "true"
      valueCond.format mustBe "@a.b"
      valueEx.format mustBe "@a.b"
      undef.format mustBe "???"
      lt.format mustBe "<"
      gt.format mustBe ">"
      le.format mustBe "<="
      ge.format mustBe ">="
      AST.ne.format mustBe "!="
      AST.eq.format mustBe "=="
      not_.format mustBe "not(<(1,2))"
      and.format mustBe "and(<(1,2),not(<(1,2)))"
      or.format mustBe "or(<(1,2),not(<(1,2)))"
      xor.format mustBe "xor(<(1,2),not(<(1,2)))"
    }
    "must have the right value type" in {
      aggCE.expressionType mustBe Abstract(At.empty)
      and.expressionType mustBe Bool(At.empty)
      arbitrary.expressionType mustBe Abstract(At.empty)
      arith.expressionType mustBe Number(At.empty)
      date.expressionType mustBe Date(At.empty)
      entId.expressionType mustBe UniqueId(At.empty, entId.entityId)
      false_.expressionType mustBe Bool(At.empty)
      func.expressionType mustBe Abstract(At.empty)
      group.expressionType mustBe Number(At.empty)
      litDec.expressionType mustBe Decimal(At.empty, Long.MaxValue, Long
        .MaxValue)
      not_.expressionType mustBe Bool(At.empty)
      number.expressionType mustBe Number(At.empty)
      string.expressionType mustBe Strng(At.empty)
      ternary.expressionType mustBe Integer(At.empty)
      timestamp.expressionType mustBe TimeStamp(At.empty)
      true_.expressionType mustBe Bool(At.empty)
      valueCond.expressionType mustBe Bool(At.empty)
      valueEx.expressionType mustBe Abstract(At.empty)
      undef.expressionType mustBe Abstract(At.empty)
    }
  }
}
