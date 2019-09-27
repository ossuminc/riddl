package com.yoppworks.ossum.riddl.parser

import fastparse.P
import fastparse.Parsed

/** Unit Tests For ParserTest */
class ParserTest extends ParsingTest {

  import AST._
  import TopLevelParser._

  "Parser" should {
    "allow an empty funky-name domain" in {
      val input =
        """domain 'foo-fah|roo' { }
          |""".stripMargin
      runParser(
        input,
        DomainDef(7, Identifier("foo-fah|roo"), None),
        _.head
      )
    }
    "allow a sub-domain" in {
      val input =
        """domain 'subdomain' is subdomain of 'parent' { }
          |""".stripMargin
      runParser(
        input,
        DomainDef(
          7,
          Identifier("subdomain"),
          Some(Identifier("parent"))
        ),
        _.head
      )
    }
    "allow multiple domains" in {
      val input =
        """domain foo { }
          |domain bar { }
          |""".stripMargin
      runParser[DomainDef, DomainDef](
        input,
        Seq[DomainDef](
          DomainDef(7, Identifier("foo"), None),
          DomainDef(22, Identifier("bar"), None)
        ),
        topLevelDomains(_),
        _
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
        DomainDef(
          7,
          Identifier("foo"),
          None,
          contexts = Seq(ContextDef(index = 23, id = Identifier("bar")))
        ),
        _.head
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
        ContextDef(
          Seq(FunctionOption, WrapperOption, GatewayOption),
          48,
          Identifier("bar")
        ),
        _.head.contexts.head
      )
    }
    "allow channel definitions in domains" in {
      val input =
        """domain foo {
          |  channel bar { commands{} events{} queries{} results{} }
          |}
          |""".stripMargin
      runParser(
        input,
        ChannelDef(23, Identifier("bar")),
        _.head.channels.head
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
        DomainDef(
          7,
          Identifier("foo"),
          None,
          contexts = Seq(
            ContextDef(
              index = 23,
              id = Identifier("bar"),
              types = Seq[TypeDef](
                TypeDef(
                  38,
                  Identifier("Vikings"),
                  Enumeration(
                    Seq(
                      Identifier("Ragnar"),
                      Identifier("Lagertha"),
                      Identifier("Bjorn"),
                      Identifier("Floki"),
                      Identifier("Rollo"),
                      Identifier("Ivar"),
                      Identifier("Aslaug"),
                      Identifier("Ubbe")
                    )
                  )
                )
              )
            )
          )
        ),
        _.head
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
        CommandDef(
          41,
          Identifier("DoThisThing"),
          TypeRef(Identifier("SomeType")),
          Seq(EventRef(Identifier("ThingWasDone")))
        ),
        _.head.contexts.head.commands.head
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
        EventDef(
          39,
          Identifier("ThingWasDone"),
          TypeRef(Identifier("SomeType"))
        ),
        _.head.contexts.head.events.head
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
        QueryDef(
          39,
          Identifier("FindThisThing"),
          Strng,
          ResultRef(Identifier("SomeResult"))
        ),
        _.head.contexts.head.queries.head
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
        ResultDef(
          40,
          Identifier("ThisQueryResult"),
          TypeRef(Identifier("SomeType"))
        ),
        _.head.contexts.head.results.head
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
        EntityDef(
          33,
          Seq(EntityPersistent, EntityAggregate),
          Identifier("Hamburger"),
          TypeRef(Identifier("SomeType")),
          Some(ChannelRef(Identifier("EntityChannel"))),
          Some(ChannelRef(Identifier("EntityChannel"))),
          Seq(
            FeatureDef(
              176,
              Identifier("AnAspect"),
              LiteralString("This is some aspect of the entity"),
              Some(Background(Seq(Given(LiteralString("Nobody loves me"))))),
              Seq(
                Example(
                  LiteralString("My Fate"),
                  Seq(
                    Given(LiteralString("everybody hates me")),
                    Given(LiteralString("I'm depressed"))
                  ),
                  Seq(When(LiteralString("I go fishing"))),
                  Seq(Then(LiteralString("I'll just eat worms")))
                )
              )
            )
          )
        ),
        _.head.contexts.head.entities.head
      )
    }
    "allow adaptor definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |   adaptor fuzz for domain fuzzy context blogger
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        AdaptorDef(
          40,
          Identifier("fuzz"),
          Some(DomainRef(Identifier("fuzzy"))),
          ContextRef(Identifier("blogger"))
        ),
        _.head.contexts.head.adaptors.head
      )
    }
    "allow interaction definitions in contexts" in {
      val input =
        """domain foo {
          |  context bar {
          |    interaction dosomething {
          |      human role SomeActor handles "Doing stuff" requires "Skills"
          |      asynch directive 'perform command' from role SomeActor to
          |      entity myLittlePony with command DoAThing
          |      processing doSomeProcessing for entity myLittlePony
          |      as "This does some stuff"
          |      asynch message 'handle a thing' from entity myLittlePony to
          |        entity Unicorn with command HandleAThing
          |    }
          |  }
          |}
          |""".stripMargin
      runParser(
        input,
        InteractionDef(
          45,
          Identifier("dosomething"),
          Seq(
            RoleDef(
              Seq(HumanOption),
              76,
              Identifier("SomeActor"),
              List(LiteralString("Doing stuff")),
              List(LiteralString("Skills"))
            )
          ),
          Seq(
            DirectiveActionDef(
              Seq(AsynchOption),
              149,
              Identifier("perform command"),
              RoleRef(Identifier("SomeActor")),
              EntityRef(Identifier("myLittlePony")),
              CommandRef(Identifier("DoAThing"))
            ),
            ProcessingActionDef(
              255,
              Identifier("doSomeProcessing"),
              EntityRef(Identifier("myLittlePony")),
              LiteralString("This does some stuff")
            ),
            MessageActionDef(
              Seq(AsynchOption),
              349,
              Identifier("handle a thing"),
              EntityRef(Identifier("myLittlePony")),
              EntityRef(Identifier("Unicorn")),
              CommandRef(Identifier("HandleAThing"))
            )
          )
        ),
        _.head.contexts.head.interactions.head
      )
    }
  }
}
