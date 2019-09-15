package com.yoppworks.ossum.idddl.parse

import com.yoppworks.ossum.idddl.parse.AST._
import fastparse.Parsed._
import org.scalatest.MustMatchers
import org.scalatest.WordSpec
import org.scalactic.PrettyMethods._

/** Unit Tests For ParserTest */
class ParserTest extends WordSpec with MustMatchers {

  "Parser" should {
    "allow type definitions" in {
      val cases: List[(String, TypeDef)] = List(
        "type str = String" → TypeDef("str", String),
        "type num = Number" → TypeDef("num", Number),
        "type boo = Boolean" → TypeDef("boo", Boolean),
        "type ident  = Id" -> TypeDef("ident", Id),
        "type dat = Date" -> TypeDef("dat", Date),
        "type tim = Time" -> TypeDef("tim", Time),
        "type stamp = TimeStamp" -> TypeDef("stamp", TimeStamp),
        "type url = URL" -> TypeDef("url", URL),
        "type FirstName = String" -> TypeDef("FirstName", String),
        "type enum = any [ Apple Pear Peach Persimmon ]" ->
          TypeDef(
            "enum",
            Enumeration(List("Apple", "Pear", "Peach", "Persimmon"))
          ),
        "type alt = select enum | stamp | url " ->
          TypeDef(
            "alt",
            Alternation(
              List(
                NamedType("enum"),
                NamedType("stamp"),
                NamedType("url")
              )
            )
          ),
        "type agg = combine key : Number, id: Id, time: TimeStamp" ->
          TypeDef(
            "agg",
            Aggregation(
              Map(
                "key" → Number,
                "id" → Id,
                "time" → TimeStamp
              )
            )
          ),
        "type oneOrMore = agg+" ->
          TypeDef("oneOrMore", OneOrMore(NamedType("agg"))),
        "type zeroOrMore = agg*" ->
          TypeDef("zeroOrMore", ZeroOrMore(NamedType("agg"))),
        "type optional = agg?" ->
          TypeDef("optional", Optional(NamedType("agg")))
      )
      cases.foreach {
        case (statement, expected) ⇒
          val input = "{ " + statement + " }"
          fastparse.parse(input, Parser.parse(_)) match {
            case Success(content, index) ⇒
              content.toList mustBe List(expected)
              index mustBe input.length
              succeed
            case failure @ Failure(_, index, extra) ⇒
              val stack = extra.stack.toMap.map {
                case (str: String, int: Int) ⇒ s"  $str : $int"
              }
              val annotated_input =
                input.substring(0, index) + "^" + input.substring(index)
              val msg =
                s"""Parse of '$annotated_input' failed at position $index"
                   |${failure.trace().longAggregateMsg}
         
                   |
              """.stripMargin
              fail(msg)
          }
      }
    }
  }
}
