package com.yoppworks.ossum.idddl.parse

import com.yoppworks.ossum.idddl.parse.AST._
import fastparse.Parsed._
import org.scalatest.MustMatchers
import org.scalatest.WordSpec
import org.scalactic.PrettyMethods._

/** Unit Tests For ParserTest */
class ParserTest extends WordSpec with MustMatchers {

  def runParser(
    input: String,
    expected: Seq[AST],
    extract: Seq[DomainDef] => Seq[AST]
  ): Unit = {
    fastparse.parse(input, Parser.parse(_)) match {
      case Success(content, index) ⇒
        extract(content).toList mustBe expected
        index must be <= input.length
      case failure @ Failure(_, index, extra) ⇒
        val stack = extra.stack.toMap.map {
          case (str: String, int: Int) ⇒ s"  $str : $int"
        }
        val annotated_input =
          input.substring(0, index) + "^" + input.substring(index)
        val msg =
          s"""Parse of '$annotated_input' failed at position $index"
             |${failure.trace().longAggregateMsg}
             |expected: ${expected.pretty}
              """.stripMargin
        fail(msg)
    }

  }
  "Parser" should {
    "allow multiple domains to be defined" in {
      val input = """domain foo { }
                    |domain bar { }
                    |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(DomainPath(Seq.empty[String], "foo"), Seq.empty[Def]),
          DomainDef(DomainPath(Seq.empty[String], "bar"), Seq.empty[Def])
        ),
        identity
      )
    }
    "allow context definitions in domains" in {
      val input =
        """domain foo {
          |  context bar { }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(
            DomainPath(Seq.empty[String], "foo"),
            Seq(ContextDef("bar", Seq.empty[Def]))
          )
        ),
        identity
      )
    }
    "allow type definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    type Vikings = any [Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe ]
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(
            DomainPath(Seq.empty[String], "foo"),
            Seq(
              ContextDef(
                "bar",
                Seq[TypeDef](
                  TypeDef(
                    "Vikings",
                    Enumeration(
                      Seq(
                        "Ragnar",
                        "Lagertha",
                        "Bjorn",
                        "Floki",
                        "Rollo",
                        "Ivar",
                        "Aslaug",
                        "Ubbe"
                      )
                    )
                  )
                )
              )
            )
          )
        ),
        identity
      )
    }
    "allow all the kinds of type definitions" in {
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
          val input = "domain test { " + statement + " }"
          runParser(
            input,
            List(expected),
            (x: Seq[DomainDef]) => x.head.children
          )
      }
    }
  }
}
