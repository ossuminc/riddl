package com.yoppworks.ossum.riddl.parser

import org.scalatest._

import scala.collection.immutable.ListMap

/** Unit Tests For TypeSpec */
class ASTTest extends WordSpec with MustMatchers {
  import AST._

  "Types" should {
    "support domain definitions" in {
      DomainDef(0, Identifier("foo"), None) must be
      DomainDef(0, Identifier("foo"), None, Seq.empty[TypeDef])
    }
    "support all type constructs" in {
      PredefinedType(Identifier("String")) mustBe Strng
      PredefinedType(Identifier("Boolean")) mustBe Boolean
      PredefinedType(Identifier("Number")) mustBe Number
      PredefinedType(Identifier("Id")) mustBe Id
      PredefinedType(Identifier("Date")) mustBe Date
      PredefinedType(Identifier("Time")) mustBe Time
      PredefinedType(Identifier("TimeStamp")) mustBe TimeStamp
      PredefinedType(Identifier("URL")) mustBe URL
      Enumeration(Seq.empty[Identifier]) mustBe
        Enumeration(Seq.empty[Identifier])
      Alternation(Seq.empty[TypeRef]) mustBe Alternation(Seq.empty[TypeRef])
      Aggregation(ListMap.empty[Identifier, TypeExpression]) mustBe
        Aggregation(ListMap.empty[Identifier, TypeExpression])
      Optional(Identifier("String")) mustBe Optional(Strng.id)
      ZeroOrMore(Identifier("Time")) mustBe ZeroOrMore(Time.id)
      OneOrMore(Identifier("URL")) mustBe OneOrMore(URL.id)
    }
  }
}
