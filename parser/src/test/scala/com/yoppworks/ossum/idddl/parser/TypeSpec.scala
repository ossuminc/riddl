package com.yoppworks.ossum.idddl.parser

import org.scalatest._

/** Unit Tests For TypeSpec */
class TypeSpec extends WordSpec with MustMatchers {

  "Types" should {
    "support all language type constructs" in {
      import AST._
      String mustBe String
      Boolean mustBe Boolean
      Number mustBe Number
      Id mustBe Id
      Date mustBe Date
      Time mustBe Time
      TimeStamp mustBe TimeStamp
      URL mustBe URL
      Enumeration(Seq.empty[String]) mustBe Enumeration(Seq.empty[String])
      Alternation(Seq.empty[Type]) mustBe Alternation(Seq.empty[Type])
      Aggregation(Map.empty[String, Type]) mustBe Aggregation(
        Map.empty[String, Type]
      )
      Optional(String) mustBe Optional(String)
      ZeroOrMore(Time) mustBe ZeroOrMore(Time)
      OneOrMore(URL) mustBe OneOrMore(URL)

    }
  }
}
