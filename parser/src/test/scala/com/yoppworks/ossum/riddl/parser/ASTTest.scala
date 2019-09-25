package com.yoppworks.ossum.riddl.parser

import org.scalatest._

/** Unit Tests For TypeSpec */
class ASTTest extends WordSpec with MustMatchers {
  import AST._

  "Types" should {
    "support domain definitions" in {
      DomainDef(0, Identifier("foo"), None) must be
      DomainDef(0, Identifier("foo"), None, Seq.empty[TypeDef])
    }
    "support all type constructs" in {
      String mustBe String
      Boolean mustBe Boolean
      Number mustBe Number
      Id mustBe Id
      Date mustBe Date
      Time mustBe Time
      TimeStamp mustBe TimeStamp
      URL mustBe URL
      Enumeration(Seq.empty[Identifier]) mustBe
        Enumeration(Seq.empty[Identifier])
      Alternation(Seq.empty[Type]) mustBe Alternation(Seq.empty[Type])
      Aggregation(Map.empty[Identifier, Type]) mustBe
        Aggregation(Map.empty[Identifier, Type])
      Optional(String) mustBe Optional(String)
      ZeroOrMore(Time) mustBe ZeroOrMore(Time)
      OneOrMore(URL) mustBe OneOrMore(URL)
    }
  }
}
