package com.yoppworks.ossum.riddl.language

/** Unit Tests For ParserTest */
class ParserTest extends ParsingTest {

  import AST._

  "Parser" should {
    "allow an empty funky-name domain" in {
      val input = "domain 'foo-fah|roo' { }"
      parseTopLevelDomain(input, _.containers.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            DomainDef(1 -> 1, Identifier(1 -> 8, "foo-fah|roo"), None)
      }
    }
    "allow a sub-domain" in {
      val input =
        """domain 'subdomain' as subdomain of 'parent' is { }
          |""".stripMargin
      parseTopLevelDomain(input, _.containers.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            DomainDef(
              1 -> 1,
              Identifier(1 -> 8, "subdomain"),
              Some(Identifier(1 -> 36, "parent"))
            )
      }
    }
    "allow multiple domains" in {
      val input =
        """domain foo { }
          |domain bar { }
          |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            RootContainer(
              Seq[DomainDef](
                DomainDef(1 -> 1, Identifier(1 -> 8, "foo"), None),
                DomainDef(2 -> 1, Identifier(2 -> 8, "bar"), None)
              )
            )
      }
    }
    "allow context definitions in domains" in {
      val input = "domain foo { context bar { } }"
      parseDomainDefinition[ContextDef](input, _.contexts.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            ContextDef(1 -> 14, id = Identifier(1 -> 22, "bar"))
      }
    }
    "allow options on context definitions" in {
      val input =
        "context bar { options (function wrapper gateway ) }"
      parseContextDefinition[ContextDef](input, identity) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            ContextDef(
              1 -> 1,
              Identifier(1 -> 9, "bar"),
              Seq(
                FunctionOption(1 -> 24),
                WrapperOption(1 -> 33),
                GatewayOption(1 -> 41)
              )
            )
      }
    }
    "allow topic definitions in domains" in {
      val input =
        """
          |domain foo {
          |
          |    topic bar { commands{} events{} queries{} results{} }
          |}
          |""".stripMargin
      parseDomainDefinition[TopicDef](input, _.topics.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            TopicDef(4 -> 5, Identifier(4 -> 11, "bar"))
      }
    }
    "allow type definitions in contexts" in {
      val input =
        """type Vikings = [
          |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
          |]""".stripMargin
      parseInContext(input, _.types.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            TypeDef(
              2 -> 1,
              Identifier(2 -> 6, "Vikings"),
              Enumeration(
                2 -> 16,
                Seq(
                  Enumerator(3 -> 3, Identifier(3 -> 3, "Ragnar"), None),
                  Enumerator(3 -> 10, Identifier(3 -> 10, "Lagertha"), None),
                  Enumerator(3 -> 19, Identifier(3 -> 19, "Bjorn"), None),
                  Enumerator(3 -> 25, Identifier(3 -> 25, "Floki"), None),
                  Enumerator(3 -> 31, Identifier(3 -> 31, "Rollo"), None),
                  Enumerator(3 -> 37, Identifier(3 -> 37, "Ivar"), None),
                  Enumerator(3 -> 42, Identifier(3 -> 42, "Aslaug"), None),
                  Enumerator(3 -> 49, Identifier(3 -> 49, "Ubbe"), None)
                )
              )
            )
      }
    }
    "allow command definitions in topics" in {
      val input =
        """domain bar { topic foo is { commands {
          |DoThisThing = SomeType yields event ThingWasDone
          |} } }
          |""".stripMargin
      parseDomainDefinition[CommandDef](input, _.topics.head.commands.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            CommandDef(
              2 -> 1,
              Identifier(2 -> 1, "DoThisThing"),
              TypeRef(2 -> 15, Identifier(2 -> 15, "SomeType")),
              Seq(EventRef(2 -> 31, Identifier(2 -> 37, "ThingWasDone")))
            )
      }
    }
    "allow event definitions in contexts" in {
      val input = """domain bar { topic foo is { events {
                    |ThingWasDone is SomeType
                    |} } }
                    |""".stripMargin
      parseDomainDefinition(input, _.topics.head.events.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            EventDef(
              2 -> 1,
              Identifier(2 -> 1, "ThingWasDone"),
              TypeRef(2 -> 17, Identifier(2 -> 17, "SomeType"))
            )
      }
    }
    "allow query definitions in contexts" in {
      val input = """domain bar { topic foo is { queries {
                    |FindThisThing = String yields result SomeResult
                    |} } }
                    |""".stripMargin
      parseDomainDefinition(input, _.topics.head.queries.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            QueryDef(
              2 -> 1,
              Identifier(2 -> 1, "FindThisThing"),
              Strng(2 -> 17),
              ResultRef(2 -> 31, Identifier(2 -> 38, "SomeResult"))
            )
      }
    }
    "allow result definitions in contexts" in {
      val input = """domain bar { topic foo is {
                    |result ThisQueryResult = SomeType
                    |} }
                    |""".stripMargin
      parseDomainDefinition(input, _.topics.head.results.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            ResultDef(
              2 -> 8,
              Identifier(2 -> 8, "ThisQueryResult"),
              TypeRef(2 -> 26, Identifier(2 -> 26, "SomeType"))
            )

      }
    }
    "allow entity definitions in contexts" in {
      val input: String =
        """entity Hamburger as SomeType is {
          |
          |  options ( persistent aggregate )
          |  consumes topic EntityChannel
          |  produces topic EntityChannel
          |  feature AnAspect {
          |    DESCRIPTION {
          |     |This is some aspect of the entity
          |    }
          |    BACKGROUND {
          |      Given "Nobody loves me"
          |    }
          |    EXAMPLE foo {
          |      "My Fate"
          |      GIVEN "everybody hates me"
          |      AND "I'm depressed"
          |      WHEN "I go fishing"
          |      THEN "I'll just eat worms"
          |    }
          |  }
          |}
          |""".stripMargin
      parseDefinition[EntityDef](input) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe EntityDef(
            SoftwareEntityKind(1 -> 1),
            1 -> 1,
            Identifier(1 -> 8, "Hamburger"),
            TypeRef(1 -> 21, Identifier(1 -> 21, "SomeType")),
            Seq(EntityPersistent(3 -> 13), EntityAggregate(3 -> 24)),
            Seq(TopicRef(4 -> 12, Identifier(4 -> 18, "EntityChannel"))),
            Seq(TopicRef(5 -> 12, Identifier(5 -> 18, "EntityChannel"))),
            Seq(
              FeatureDef(
                6 -> 3,
                Identifier(6 -> 11, "AnAspect"),
                Seq(
                  LiteralString(
                    8 -> 7,
                    "This is some aspect of the entity"
                  )
                ),
                Some(
                  Background(
                    10 -> 5,
                    Seq(
                      Given(
                        11 -> 7,
                        LiteralString(
                          11 -> 13,
                          "Nobody loves me"
                        )
                      )
                    )
                  )
                ),
                Seq(
                  ExampleDef(
                    13 -> 5,
                    Identifier(13 -> 13, "foo"),
                    LiteralString(14 -> 7, "My Fate"),
                    Seq(
                      Given(
                        15 -> 7,
                        LiteralString(15 -> 13, "everybody hates me")
                      ),
                      Given(16 -> 7, LiteralString(16 -> 11, "I'm depressed"))
                    ),
                    Seq(When(17 -> 7, LiteralString(17 -> 12, "I go fishing"))),
                    Seq(
                      Then(
                        18 -> 7,
                        LiteralString(18 -> 12, "I'll just eat worms")
                      )
                    )
                  )
                )
              )
            )
          )
      }
    }
    "allow adaptor definitions" in {
      val input =
        "adaptor fuzz for domain fuzzy context blogger {}"
      parseDefinition[AdaptorDef](input) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            AdaptorDef(
              1 -> 1,
              Identifier(1 -> 9, "fuzz"),
              Some(DomainRef(1 -> 18, Identifier(1 -> 25, "fuzzy"))),
              ContextRef(1 -> 31, Identifier(1 -> 39, "blogger"))
            )
      }
    }
    "allow interaction definitions" in {
      val input =
        """interaction dosomething {
          |  role SomeActor {
          |    option is human
          |    handles {
          |      |Doing stuff
          |    }
          |    requires {
          |      |Skills
          |    }
          |  }
          |  directive 'perform a command' option is async
          |    from role SomeActor
          |    to entity myLittlePony as command DoAThing
          |
          |  message 'handle a thing' option is async
          |    from entity myLittlePony
          |    to entity Unicorn as command HandleAThing
          |}
          |""".stripMargin
      parseDefinition[InteractionDef](input) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            InteractionDef(
              1 -> 1,
              Identifier(1 -> 13, "dosomething"),
              Seq(
                RoleDef(
                  2 -> 3,
                  Identifier(2 -> 8, "SomeActor"),
                  Seq(HumanOption(3 -> 15)),
                  List(LiteralString(5 -> 8, "Doing stuff")),
                  List(LiteralString(8 -> 8, "Skills"))
                )
              ),
              Seq(
                DirectiveActionDef(
                  11 -> 3,
                  Identifier(11 -> 13, "perform a command"),
                  Seq(AsynchOption(11 -> 43)),
                  RoleRef(12 -> 10, Identifier(12 -> 15, "SomeActor")),
                  EntityRef(13 -> 8, Identifier(13 -> 15, "myLittlePony")),
                  CommandRef(13 -> 31, Identifier(13 -> 39, "DoAThing")),
                  Seq.empty[Reaction]
                ),
                MessageActionDef(
                  15 -> 3,
                  Identifier(15 -> 11, "handle a thing"),
                  Seq(AsynchOption(15 -> 38)),
                  EntityRef(16 -> 10, Identifier(16 -> 17, "myLittlePony")),
                  EntityRef(17 -> 8, Identifier(17 -> 15, "Unicorn")),
                  CommandRef(17 -> 26, Identifier(17 -> 34, "HandleAThing")),
                  Seq.empty[Reaction]
                )
              )
            )
      }
    }
  }
}
