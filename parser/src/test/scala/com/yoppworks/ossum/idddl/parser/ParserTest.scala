package com.yoppworks.ossum.idddl.parser

import AST._
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ParserTest */
class ParserTest extends WordSpec with MustMatchers {

  def runParser(
    input: String,
    expected: Seq[AST],
    extract: Seq[DomainDef] => Seq[AST]
  ): Unit = {
    Parser.parseString(input) match {
      case Right(content) =>
        extract(content).toList mustBe expected
      case Left(msg) =>
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
          DomainDef(7, DomainPath(Seq("foo-fah|roo")))
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
          DomainDef(7,
            DomainPath(Seq("this", "is", "a ", "sub", "domain")),
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
          DomainDef(7, DomainPath(Seq("foo"))),
          DomainDef(22,DomainPath(Seq("bar")))
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
          DomainDef(7,
            DomainPath(Seq("foo")),
            contexts = Seq(ContextDef(23, "bar"))
          )
        ),
        identity
      )
    }
    "allow channel definitions in domains" in {
      val input =
        """domain foo {
          |  channel bar flows String
          |}
          |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(7,
            DomainPath(Seq("foo")),
            channels=Seq(ChannelDef(23, "bar", String))
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
          DomainDef(7,
            DomainPath(Seq("foo")),
            contexts = Seq(
              ContextDef(23,
                "bar",
                types = Seq[TypeDef](
                  TypeDef(38,
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
          CommandDef(41, "DoThisThing", NamedType("SomeType"),
            Seq("ThingWasDone"))
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.commands
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
          EventDef(39, "ThingWasDone", NamedType("SomeType"))
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.events
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
          QueryDef(39, "FindThisThing", NamedType("SomeType"), Seq
          ("SomeResult"))
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.queries
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
          ResultDef(40, "ThisQueryResult", NamedType("SomeType"))
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.results
        }
      )
    }
    "allow entity definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    persistent aggregate entity Hamburger: SomeType
          |      consumes [ACommand, AQuery]
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          EntityDef(61,
            "Hamburger",
            Seq(EntityPersistent, EntityAggregate),
            NamedType("SomeType"),
            Seq("ACommand", "AQuery"),
            Seq.empty[String]
          )
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.entities
        }
      )
    }
    "allow all the kinds of type definitions" in {
      val cases: List[(String, TypeDef)] = List(
        "type str = String" → TypeDef(33, "str", String),
        "type num = Number" → TypeDef(33, "num", Number),
        "type boo = Boolean" → TypeDef(33, "boo", Boolean),
        "type ident  = Id" -> TypeDef(33, "ident", Id),
        "type dat = Date" -> TypeDef(33, "dat", Date),
        "type tim = Time" -> TypeDef(33, "tim", Time),
        "type stamp = TimeStamp" -> TypeDef(33, "stamp", TimeStamp),
        "type url = URL" -> TypeDef(33, "url", URL),
        "type FirstName = String" -> TypeDef(33, "FirstName", String),
        "type enum = any [ Apple Pear Peach Persimmon ]" ->
          TypeDef(33,
            "enum",
            Enumeration(List("Apple", "Pear", "Peach", "Persimmon"))
          ),
        "type alt = select enum | stamp | url " ->
          TypeDef(33,
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
          TypeDef(33,
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
          TypeDef(33, "oneOrMore", OneOrMore(NamedType("agg"))),
        "type zeroOrMore = agg*" ->
          TypeDef(33, "zeroOrMore", ZeroOrMore(NamedType("agg"))),
        "type optional = agg?" ->
          TypeDef(33, "optional", Optional(NamedType("agg")))
      )
      cases.foreach {
        case (statement, expected) ⇒
          val input = "domain test { context foo { " + statement + " } }"
          runParser(
            input,
            List(expected),
            (x: Seq[DomainDef]) => x.head.contexts.head.types
          )
      }
    }
  }
}
