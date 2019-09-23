package com.yoppworks.ossum.riddl.parser

import AST._
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ParserTest */
class ParserTest extends WordSpec with MustMatchers {

  def runParser(
    input: String,
    expected: Seq[AST],
    extract: Seq[DomainDef] => Seq[AST]
  ): Unit =
    Parser.parseString(input) match {
      case Right(content) =>
        extract(content) mustBe expected
      case Left(msg) =>
        fail(msg)
    }

  "Parser" should {
    "allow an empty funky-name domain" in {
      val input =
        """domain 'foo-fah|roo' { }
          |""".stripMargin
      runParser(
        input,
        Seq[DomainDef](
          DomainDef(7, Seq.empty[String], "foo-fah|roo")
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
          DomainDef(7, Seq("this", "is", "a ", "sub"), "domain")
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
          DomainDef(7, Seq.empty[String], "foo"),
          DomainDef(22, Seq.empty[String], "bar")
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
            7,
            Seq.empty[String],
            "foo",
            contexts = Seq(ContextDef(index = 23, name = "bar"))
          )
        ),
        identity
      )
    }
    "allow options on context definitions" in {
      val input =
        """domain foo {
          |  function wrapper gateway context bar { }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          ContextDef(
            Seq(FunctionOption, WrapperOption, GatewayOption),
            48,
            "bar"
          )
        ),
        _.head.contexts
      )
    }
    "allow channel definitions in domains" in {
      val input =
        """domain foo {
          |  channel bar
          |}
          |""".stripMargin
      runParser(
        input,
        Seq[ChannelDef](
          ChannelDef(23, "bar")
        ),
        _.head.channels
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
            7,
            Seq.empty[String],
            "foo",
            Seq.empty[ChannelDef],
            contexts = Seq(
              ContextDef(
                index = 23,
                name = "bar",
                types = Seq[TypeDef](
                  TypeDef(
                    38,
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
          |    command DoThisThing = type SomeType yields event ThingWasDone
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          CommandDef(
            41,
            "DoThisThing",
            TypeRef("SomeType"),
            Seq(EventRef("ThingWasDone"))
          )
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.commands
        }
      )
    }
    "allow event definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    event ThingWasDone = type SomeType
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          EventDef(39, "ThingWasDone", TypeRef("SomeType"))
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.events
        }
      )
    }
    "allow query definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    query FindThisThing = String yields result SomeResult
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          QueryDef(
            39,
            "FindThisThing",
            String,
            Seq(ResultRef("SomeResult"))
          )
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.queries
        }
      )
    }
    "allow result definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    result ThisQueryResult = type SomeType
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          ResultDef(40, "ThisQueryResult", TypeRef("SomeType"))
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.results
        }
      )
    }
    "allow entity definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    persistent aggregate entity Hamburger = type SomeType
          |      consumes channel EntityChannel
          |      produces channel EntityChannel
          |      feature: AnAspect
          |        "This is some aspect of the entity"
          |        BACKGROUND: Given "Nobody loves me"
          |        EXAMPLE: "My Fate"
          |        GIVEN "everybody hates me"
          |        AND "I'm depressed"
          |        WHEN "I go fishing"
          |        THEN "I'll just eat worms"
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          EntityDef(
            33,
            Seq(EntityPersistent, EntityAggregate),
            "Hamburger",
            TypeRef("SomeType"),
            Some(ChannelRef("EntityChannel")),
            Some(ChannelRef("EntityChannel")),
            Seq(
              Feature(
                "AnAspect",
                "This is some aspect of the entity",
                Some(Background(Seq(Given("Nobody loves me")))),
                Seq(
                  Example(
                    "My Fate",
                    Seq(
                      Given("everybody hates me"),
                      Given("I'm depressed")
                    ),
                    Seq(When("I go fishing")),
                    Seq(Then("I'll just eat worms"))
                  )
                )
              )
            )
          )
        ), { x: Seq[DomainDef] =>
          x.head.contexts.head.entities
        }
      )
    }
    "allow adaptor definitions in contexts" in {
      val input =
        """domain foo.bar {
          |  context baz {
          |   adaptor fuzz for domain foo.fuzz context blogger
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          AdaptorDef(
            44,
            "fuzz",
            Some(DomainRef("foo.fuzz")),
            ContextRef("blogger")
          )
        ),
        _.head.contexts.head.translators
      )
    }
    "allow scenario definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    scenario dosomething {
          |      role SomeActor handles "Doing stuff" requires "Skills"
          |      external "perform command" from role SomeActor to entity
          |      myLittlePony with command DoAThing
          |      message "handle a thing" from entity myLittlePony to
          |        entity Unicorn with command HandleAThing
          |    }
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        Seq(
          ScenarioDef(
            42,
            List(),
            "dosomething",
            Seq(
              ActorRoleDef(
                67,
                "SomeActor",
                List("Doing stuff"),
                List("Skills")
              )
            ),
            Seq(
              ActorInteraction(
                "perform command",
                ActorRoleRef("SomeActor"),
                EntityRef("myLittlePony"),
                CommandRef("DoAThing")
              ),
              MessageInteraction(
                "handle a thing",
                EntityRef("myLittlePony"),
                EntityRef("Unicorn"),
                CommandRef("HandleAThing")
              )
            )
          )
        ),
        _.head.contexts.head.scenarios
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
          TypeDef(
            33,
            "enum",
            Enumeration(List("Apple", "Pear", "Peach", "Persimmon"))
          ),
        "type alt = select enum | stamp | url " ->
          TypeDef(
            33,
            "alt",
            Alternation(
              List(
                TypeRef("enum"),
                TypeRef("stamp"),
                TypeRef("url")
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
            33,
            "agg",
            Aggregation(
              Map(
                "key" → Number,
                "id" → Id,
                "time" → TimeStamp
              )
            )
          ),
        "type oneOrMore = type agg+" ->
          TypeDef(33, "oneOrMore", OneOrMore(TypeRef("agg"))),
        "type zeroOrMore = type agg*" ->
          TypeDef(33, "zeroOrMore", ZeroOrMore(TypeRef("agg"))),
        "type optional = type agg?" ->
          TypeDef(33, "optional", Optional(TypeRef("agg")))
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
