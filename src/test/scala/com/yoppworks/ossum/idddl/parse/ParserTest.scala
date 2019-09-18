package com.yoppworks.ossum.idddl.parse

import com.yoppworks.ossum.idddl.parse.AST._
import fastparse.Parsed._
import org.scalactic.PrettyMethods._
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

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
      case failure @ Failure(_, index, _) ⇒
        val annotated_input =
          input.substring(0, index) + "^" + input.substring(index)
        val trace = failure.trace()
        val msg =
          s"""Parse of '$annotated_input' failed at position $index"
             |${trace.longAggregateMsg}
             |expected: ${expected.pretty}
             |              """.stripMargin
        fail(msg)
    }

  }
  "Parser" should {
    "allow an empty funky-name domain" in {
      val input =
        """domain 'foo-fah|roo' { }
          |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(DomainPath(Seq("foo-fah|roo")), Seq.empty[ContextDef])
        ),
        identity
      )
    }
    "allow a sub-domain" in {
      val input =
        """domain this.is.'a '.sub.domain { }
          |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(
            DomainPath(Seq("this", "is", "a ", "sub", "domain")),
            Seq.empty[ContextDef]
          )
        ),
        identity
      )
    }
    "allow multiple domains" in {
      val input =
        """domain foo { }
          |domain bar { }
          |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(DomainPath(Seq("foo")), Seq.empty[ContextDef]),
          DomainDef(DomainPath(Seq("bar")), Seq.empty[ContextDef])
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
            DomainPath(Seq("foo")),
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
          |    type Vikings = any [
          |      Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
          |    ]
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(
            DomainPath(Seq("foo")),
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
    "allow command definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    command DoThisThing: SomeType yields ThingWasDone
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          CommandDef("DoThisThing", NamedType("SomeType"), Seq("ThingWasDone"))
        ), { x: Seq[DomainDef] =>
          x.head.children.head.children
        }
      )
    }
    "allow event definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    event ThingWasDone: SomeType
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          EventDef("ThingWasDone", NamedType("SomeType"))
        ), { x: Seq[DomainDef] =>
          x.head.children.head.children
        }
      )
    }
    "allow query definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    query FindThisThing: SomeType yields SomeResult
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          QueryDef("FindThisThing", NamedType("SomeType"), Seq("SomeResult"))
        ), { x: Seq[DomainDef] =>
          x.head.children.head.children
        }
      )
    }
    "allow result definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    result ThisQueryResult: SomeType
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          ResultDef("ThisQueryResult", NamedType("SomeType"))
        ), { x: Seq[DomainDef] =>
          x.head.children.head.children
        }
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
        """type agg = combine {
          |  key: Number,
          |  id: Id,
          |  time: TimeStamp
          |}
          |""".stripMargin ->
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
          val input = "domain test { context foo { " + statement + " } }"
          runParser(
            input,
            List(expected),
            (x: Seq[DomainDef]) => x.head.children.head.children
          )
      }
    }
  }
}
