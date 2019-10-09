package com.yoppworks.ossum.riddl.parser

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
      Strng((0, 0)) mustBe Strng((0, 0))
      Bool((0, 0)) mustBe Bool((0, 0))
      Number((0, 0)) mustBe Number((0, 0))
      Id((0, 0)) mustBe Id((0, 0))
      Date((0, 0)) mustBe Date((0, 0))
      Time((0, 0)) mustBe Time((0, 0))
      TimeStamp((0, 0)) mustBe TimeStamp((0, 0))
      URL((0, 0)) mustBe URL((0, 0))
      Enumeration((0, 0), Seq.empty[Identifier]) mustBe
        Enumeration((0, 0), Seq.empty[Identifier])
      Alternation((0, 0), Seq.empty[TypeRef]) mustBe
        Alternation((0, 0), Seq.empty[TypeRef])
      Aggregation((0, 0), ListMap.empty[Identifier, TypeExpression]) mustBe
        Aggregation((0, 0), ListMap.empty[Identifier, TypeExpression])
      Optional((0, 0), Identifier((0, 0), "String")) mustBe
        Optional((0, 0), Identifier((0, 0), "String"))
      ZeroOrMore((0, 0), Identifier((0, 0), "Time")) mustBe
        ZeroOrMore((0, 0), Identifier((0, 0), "Time"))
      OneOrMore((0, 0), Identifier((0, 0), "URL")) mustBe
        OneOrMore((0, 0), Identifier((0, 0), "URL"))
    }
  }
}
