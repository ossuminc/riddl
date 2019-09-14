package com.yoppworks.ossum.idddl.parse

import com.yoppworks.ossum.idddl.parse.Node._
import fastparse.Parsed
import fastparse.Parsed.Success
import fastparse.Parsed.Failure
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ParserTest */
class ParserTest extends WordSpec with MustMatchers {

  "Parser" should {
    "allow type definitions" in {
      val inputs =
        """{
          |type str = String
          |type num = Number
          |type boo = Boolean
          |type ident  = Id
          |type dat = Date
          |type tim = Time
          |type stamp = TimeStamp
          |type url = URL
          |type enum = any [Apple Pear Peach Persimmon]
          |type alt = select enum | stamp | url
          |type agg = combine key : Number, id: Id, time: TimeStamp
          |}
          |""".stripMargin
      val expected = List[Def](
        TypeDef("str", String),
        TypeDef("num", Number),
        TypeDef("boo", Boolean),
        TypeDef("ident", Id),
        TypeDef("dat", Date),
        TypeDef("tim", Time),
        TypeDef("stamp", TimeStamp),
        TypeDef("url", URL),
        TypeDef(
          "enum",
          Enumeration(List("Apple", "Pear", "Peach", "Persimmon"))
        ),
        TypeDef("alt", Alternation(List("enum", "stamp", "url"))),
        TypeDef(
          "agg",
          Aggregation(Map("key" → "Number", "id" → "Id", "time" → "TimeStamp"))
        )
      )
      fastparse.parse(inputs, Parser.parse(_)) match {
        case Success(content, index) ⇒
          content.toList mustBe expected
          index mustBe inputs.length - 1
          succeed
        case Failure(label, index, extra) ⇒
          val stack = extra.stack.toMap.map {
            case (str: String, int: Int) ⇒ s"  $str : $int"
          }
          val annotated_input = inputs.substring(0, index) + "^" + inputs
            .substring(index)
          val msg = s"""Parse of '$annotated_input' failed at position $index"
                       |in Parser ${extra.originalParser}
                       |at:
                       |${stack.mkString("\n")}
                       |""".stripMargin
          fail(msg)
      }
    }
  }
}
