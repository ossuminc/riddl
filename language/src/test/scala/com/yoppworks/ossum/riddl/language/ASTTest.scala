package com.yoppworks.ossum.riddl.language

import org.scalatest._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers._

/** Unit Tests For Abstract Syntax Tree */
class ASTTest extends AnyWordSpec with must.Matchers {
  import AST._

  "Types" should {
    "support domain definitions" in {
      Domain((0, 0), Identifier((1, 1), "foo")) must be
      Domain((0, 0), Identifier((1, 1), "foo"), Seq.empty[Type])
    }
    "support all type constructs" in {
      TypeRef(0 -> 0, PathIdentifier(0 -> 0, Seq("Foo"))) mustBe TypeRef(
        0 -> 0,
        PathIdentifier(0 -> 0, Seq("Foo"))
      )

      Strng mustBe Strng
      Bool mustBe Bool
      Number mustBe Number
      Date mustBe Date
      Time mustBe Time
      TimeStamp mustBe TimeStamp
      URL mustBe URL
      Enumeration((0, 0), Seq.empty[Enumerator]) mustBe
        Enumeration((0, 0), Seq.empty[Enumerator])
      Alternation((0, 0), Seq.empty[TypeExpression]) mustBe
        Alternation((0, 0), Seq.empty[TypeExpression])
      Aggregation((0, 0), Seq.empty[Field]) mustBe
        Aggregation((0, 0), Seq.empty[Field])
      Optional((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("String")))) mustBe
        Optional((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("String"))))
      ZeroOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("Time")))) mustBe
        ZeroOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("Time"))))
      OneOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("URL")))) mustBe
        OneOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("URL"))))
    }
  }
}
