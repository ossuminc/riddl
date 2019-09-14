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
      Enumeration(Seq.empty[String]) mustBe Enumeration(Seq.empty[String])
      Alternation(Seq.empty[String]) mustBe Alternation(Seq.empty[String])
      Aggregation(Map.empty[String, String]) mustBe Aggregation(
        Map.empty[String, String]
      )
      Optional(String) mustBe Optional(String)
      Required(Number) mustBe Required(Number)
      Tuple(Seq.empty[Id.type]) mustBe Tuple(Seq.empty[Id.type])
      ZeroOrMore(Time) mustBe ZeroOrMore(Time)
      OneOrMore(URL) mustBe OneOrMore(URL)

    }
  }
}
