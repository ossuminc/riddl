package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.ListMap

/** Unit Tests For Expressions */
class ValuesTest extends AnyWordSpec with Matchers {

  val int1 = IntegerValue(At.empty, BigInt(1))
  val int2 = IntegerValue(At.empty, BigInt(2))
  val litDec = DecimalValue(At.empty, BigDecimal(42.0))
  val argValues = Seq[Value](
    IntegerValue(At.empty, BigInt(1)),
    IntegerValue(At.empty, BigInt(1))
  )
  val arith = ComputedValue(
    At.empty,
    "*",
    argValues
  )
  val comp = Comparison(At.empty, lt, int1, int2)
  val not_ = NotCondition(At.empty, comp)
  val and = AndCondition(At.empty, Seq(comp, not_))
  val or = OrCondition(At.empty, Seq(comp, not_))
  val xor = XorCondition(At.empty, Seq(comp, not_))
  val date = ComputedValue(At.empty, "today", Seq.empty[Value])
  val false_ = False(At.empty)
  val true_ = True(At.empty)

  val callValue = FunctionCallValue(
    At.empty,
    FunctionRef(At.empty, PathIdentifier(At.empty, Seq("a", "b"))),
    ParameterValues(
      At.empty,
      Map.from(
        Seq(Identifier(At.empty, "arg") -> IntegerValue(At.empty, BigInt(1)))
      )
    )
  )

  val valueCond =
    ValueCondition(At.empty, PathIdentifier(At.empty, Seq("a", "b")))
  val fieldValue = FieldValue(At.empty, PathIdentifier(At.empty, Seq("a", "b")))

  "Values" must {
    "format correctly" in {
      arith.format mustBe "*(1, 1)"
      comp.format mustBe "<(1, 2)"
      date.format mustBe "today()"
      false_.format mustBe "false"
      callValue.format mustBe "function a.b(arg=1)"
      litDec.format mustBe "42.0"
      true_.format mustBe "true"
      valueCond.format mustBe "@a.b"
      fieldValue.format mustBe "@a.b"
      lt.format mustBe "<"
      gt.format mustBe ">"
      le.format mustBe "<="
      ge.format mustBe ">="
      AST.ne.format mustBe "!="
      AST.eq.format mustBe "=="
      not_.format mustBe "not(<(1, 2))"
      and.format mustBe "and(<(1, 2), not(<(1, 2)))"
      or.format mustBe "or(<(1, 2), not(<(1, 2)))"
      xor.format mustBe "xor(<(1, 2), not(<(1, 2)))"
    }
  }
}
