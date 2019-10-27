package com.yoppworks.ossum.riddl.language

import org.scalatest._

import scala.collection.immutable.ListMap

/** Unit Tests For TypeSpec */
class ASTTest extends WordSpec with MustMatchers {
  import AST._

  "Types" should {
    "support domain definitions" in {
      DomainDef((0, 0), Identifier((1, 1), "foo"), None) must be
      DomainDef((0, 0), Identifier((1, 1), "foo"), None, Seq.empty[TypeDef])
    }
    "support all type constructs" in {
      TypeRef((0, 0), Identifier((0, 0), "Foo")) mustBe TypeRef(
        (0, 0),
        Identifier((0, 0), "Foo")
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
      Aggregation((0, 0), ListMap.empty[Identifier, TypeExpression]) mustBe
        Aggregation((0, 0), ListMap.empty[Identifier, TypeExpression])
      Optional((0, 0), TypeRef((0, 0), Identifier((0, 0), "String"))) mustBe
        Optional((0, 0), TypeRef((0, 0), Identifier((0, 0), "String")))
      ZeroOrMore((0, 0), TypeRef((0, 0), Identifier((0, 0), "Time"))) mustBe
        ZeroOrMore((0, 0), TypeRef((0, 0), Identifier((0, 0), "Time")))
      OneOrMore((0, 0), TypeRef((0, 0), Identifier((0, 0), "URL"))) mustBe
        OneOrMore((0, 0), TypeRef((0, 0), Identifier((0, 0), "URL")))
    }
  }
}
