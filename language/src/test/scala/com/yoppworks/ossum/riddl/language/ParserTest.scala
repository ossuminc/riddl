package com.yoppworks.ossum.riddl.language

/** Unit Tests For ParserTest */
class ParserTest extends ParsingTest {

  import AST._

  "Parser" should {
    "allow an empty funky-name domain" in {
      val input = "domain 'foo-fah|roo' { }"
      parseTopLevelDomain(input, _.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            DomainDef(1 -> 1, Identifier(1 -> 8, "foo-fah|roo"), None)
      }
    }
    "allow a sub-domain" in {
      val input =
        """domain 'subdomain' is subdomain of 'parent' { }
          |""".stripMargin
      parseTopLevelDomain(input, _.head) match {
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
            Seq[DomainDef](
              DomainDef(1 -> 1, Identifier(1 -> 8, "foo"), None),
              DomainDef(2 -> 1, Identifier(2 -> 8, "bar"), None)
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
        "context bar { options {function wrapper gateway } }"
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
    "allow channel definitions in contexts" in {
      val input =
        """
          |domain foo {
          |  context fum {
          |    channel bar { commands{} events{} queries{} results{} }
          |  }
          |}
          |""".stripMargin
      parseDomainDefinition[ChannelDef](input, _.contexts.head.channels.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            ChannelDef(4 -> 5, Identifier(4 -> 13, "bar"))
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
    "allow command definitions in contexts" in {
      val input =
        "command DoThisThing = SomeType yields event ThingWasDone"
      parseInContext[CommandDef](input, _.commands.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            CommandDef(
              2 -> 1,
              Identifier(2 -> 9, "DoThisThing"),
              TypeRef(2 -> 23, Identifier(2 -> 23, "SomeType")),
              Seq(EventRef(2 -> 39, Identifier(2 -> 45, "ThingWasDone")))
            )
      }
    }
    "allow event definitions in contexts" in {
      val input = "event ThingWasDone = SomeType"
      parseInContext(input, _.events.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            EventDef(
              2 -> 1,
              Identifier(2 -> 7, "ThingWasDone"),
              TypeRef(2 -> 22, Identifier(2 -> 22, "SomeType"))
            )
      }
    }
    "allow query definitions in contexts" in {
      val input =
        "query FindThisThing = String yields result SomeResult"
      parseInContext(input, _.queries.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            QueryDef(
              2 -> 1,
              Identifier(2 -> 7, "FindThisThing"),
              Strng(2 -> 23),
              ResultRef(2 -> 37, Identifier(2 -> 44, "SomeResult"))
            )
      }
    }
    "allow result definitions in contexts" in {
      val input =
        "result ThisQueryResult = SomeType"
      parseInContext(input, _.results.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            ResultDef(
              2 -> 1,
              Identifier(2 -> 8, "ThisQueryResult"),
              TypeRef(2 -> 26, Identifier(2 -> 26, "SomeType"))
            )

      }
    }
    "allow entity definitions in contexts" in {
      val input: String =
        """entity Hamburger is SomeType {
          |  options { persistent aggregate }
          |  consumes channel EntityChannel
          |  produces channel EntityChannel
          |  feature AnAspect {
          |    DESCRIPTION { "This is some aspect of the entity" }
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
            Seq(EntityPersistent(2 -> 13), EntityAggregate(2 -> 24)),
            Seq(ChannelRef(3 -> 12, Identifier(3 -> 20, "EntityChannel"))),
            Seq(ChannelRef(4 -> 12, Identifier(4 -> 20, "EntityChannel"))),
            Seq(
              FeatureDef(
                5 -> 3,
                Identifier(5 -> 11, "AnAspect"),
                Seq(
                  LiteralString(
                    6 -> 19,
                    "This is some aspect of the entity"
                  )
                ),
                Some(
                  Background(
                    7 -> 5,
                    Seq(
                      Given(
                        8 -> 7,
                        LiteralString(
                          8 -> 13,
                          "Nobody loves me"
                        )
                      )
                    )
                  )
                ),
                Seq(
                  ExampleDef(
                    10 -> 5,
                    Identifier(10 -> 13, "foo"),
                    LiteralString(11 -> 7, "My Fate"),
                    Seq(
                      Given(
                        12 -> 7,
                        LiteralString(12 -> 13, "everybody hates me")
                      ),
                      Given(13 -> 7, LiteralString(13 -> 11, "I'm depressed"))
                    ),
                    Seq(When(14 -> 7, LiteralString(14 -> 12, "I go fishing"))),
                    Seq(
                      Then(
                        15 -> 7,
                        LiteralString(15 -> 12, "I'll just eat worms")
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
          |    handles { "Doing stuff" }
          |    requires { "Skills" }
          |  }
          |  directive 'perform a command' option is asynch
          |    from role SomeActor
          |    to entity myLittlePony as command DoAThing
          |
          |  message 'handle a thing' option is asynch
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
                  List(LiteralString(4 -> 15, "Doing stuff")),
                  List(LiteralString(5 -> 16, "Skills"))
                )
              ),
              Seq(
                DirectiveActionDef(
                  7 -> 3,
                  Identifier(7 -> 13, "perform a command"),
                  Seq(AsynchOption(7 -> 43)),
                  RoleRef(8 -> 10, Identifier(8 -> 15, "SomeActor")),
                  EntityRef(9 -> 8, Identifier(9 -> 15, "myLittlePony")),
                  CommandRef(9 -> 31, Identifier(9 -> 39, "DoAThing")),
                  Seq.empty[Reaction]
                ),
                MessageActionDef(
                  11 -> 3,
                  Identifier(11 -> 11, "handle a thing"),
                  Seq(AsynchOption(11 -> 38)),
                  EntityRef(12 -> 10, Identifier(12 -> 17, "myLittlePony")),
                  EntityRef(13 -> 8, Identifier(13 -> 15, "Unicorn")),
                  CommandRef(13 -> 26, Identifier(13 -> 34, "HandleAThing")),
                  Seq.empty[Reaction]
                )
              )
            )
      }
    }
  }
}
