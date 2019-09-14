package com.yoppworks.ossum.idddl.parse

import org.scalatest._

/** Unit Tests For TypeSpec */
class TypeSpec extends WordSpec with MustMatchers {

  "Types" should {
    "support all language type constructs" in {
      import Node._
      String mustBe String
      Boolean mustBe Boolean
      Number mustBe Number
      Id mustBe Id
      Date mustBe Date
      Time mustBe Time
      TimeStamp mustBe TimeStamp
      URL mustBe URL
      Rename(String) mustBe Rename(String)
      Enumeration(Seq.empty[String]) mustBe Enumeration(Seq.empty[String])
      Alternation(Seq.empty[Type]) mustBe Alternation(Seq.empty[Type])
      Aggregation(Map.empty[String, Type]) mustBe Aggregation(
        Map.empty[String, Type]
      )
      Optional(String) mustBe Optional(String)
      Required(Number) mustBe Required(Number)
      Tuple(Seq.empty[Id.type]) mustBe Tuple(Seq.empty[Id.type])
      ZeroOrMore(Time) mustBe ZeroOrMore(Time)
      OneOrMore(URL) mustBe OneOrMore(URL)

    }
  }
}
