package com.yoppworks.ossum.riddl.parser

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
            DomainDef((1, 1), Identifier((1, 8), "foo-fah|roo"), None)
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
              (1, 1),
              Identifier((1, 8), "subdomain"),
              Some(Identifier((1, 36), "parent"))
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
              DomainDef((1, 1), Identifier((1, 8), "foo"), None),
              DomainDef((2, 1), Identifier((2, 8), "bar"), None)
            )
      }
    }
    "allow context definitions in domains" in {
      val input = "domain foo { context bar { } }"
      parseDomainDefinition[ContextDef](input, _.contexts.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            ContextDef((1, 14), id = Identifier((1, 22), "bar"))
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
              (1, 1),
              Identifier((1, 9), "bar"),
              Seq(
                FunctionOption((1, 24)),
                WrapperOption((1, 33)),
                GatewayOption((1, 41))
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
            ChannelDef((4, 5), Identifier((4, 13), "bar"))
      }
    }
    "allow type definitions in contexts" in {
      val input =
        """type Vikings = any [
          |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
          |]""".stripMargin
      parseInContext(input, _.types.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            TypeDef(
              (2, 1),
              Identifier((2, 6), "Vikings"),
              Enumeration(
                (2, 16),
                Seq(
                  Identifier((3, 3), "Ragnar"),
                  Identifier((3, 10), "Lagertha"),
                  Identifier((3, 19), "Bjorn"),
                  Identifier((3, 25), "Floki"),
                  Identifier((3, 31), "Rollo"),
                  Identifier((3, 37), "Ivar"),
                  Identifier((3, 42), "Aslaug"),
                  Identifier((3, 49), "Ubbe")
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
              (2, 1),
              Identifier((2, 9), "DoThisThing"),
              TypeRef((2, 23), Identifier((2, 23), "SomeType")),
              Seq(EventRef((2, 45), Identifier((2, 45), "ThingWasDone")))
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
              (2, 1),
              Identifier((2, 7), "ThingWasDone"),
              TypeRef((2, 22), Identifier((2, 22), "SomeType"))
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
              (2, 1),
              Identifier((2, 7), "FindThisThing"),
              TypeRef((2, 23), Identifier((2, 23), "String")),
              ResultRef((2, 37), Identifier((2, 44), "SomeResult"))
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
              (2, 1),
              Identifier((2, 8), "ThisQueryResult"),
              TypeRef((2, 26), Identifier((2, 26), "SomeType"))
            )

      }
    }
    "allow entity definitions in contexts" in {
      val input =
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
      parseInContext(input, _.entities.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe EntityDef(
            (2, 1),
            Identifier((2, 8), "Hamburger"),
            TypeRef((2, 21), Identifier((2, 21), "SomeType")),
            Seq(EntityPersistent(3, 13), EntityAggregate(3, 24)),
            Some(ChannelRef((4, 12), Identifier((4, 20), "EntityChannel"))),
            Some(ChannelRef((5, 12), Identifier((5, 20), "EntityChannel"))),
            Seq(
              FeatureDef(
                (6, 3),
                Identifier((6, 11), "AnAspect"),
                Seq("This is some aspect of the entity"),
                Some(
                  Background(
                    (8, 5),
                    Seq(
                      Given(
                        (9, 7),
                        LiteralString(
                          (9, 13),
                          "Nobody loves me"
                        )
                      )
                    )
                  )
                ),
                Seq(
                  ExampleDef(
                    (11, 5),
                    Identifier((11, 13), "foo"),
                    LiteralString((12, 7), "My Fate"),
                    Seq(
                      Given(
                        (13, 7),
                        LiteralString((13, 13), "everybody hates me")
                      ),
                      Given((14, 7), LiteralString((14, 11), "I'm depressed"))
                    ),
                    Seq(When((15, 7), LiteralString((15, 12), "I go fishing"))),
                    Seq(
                      Then(
                        (16, 7),
                        LiteralString((16, 12), "I'll just eat worms")
                      )
                    )
                  )
                )
              )
            )
          )
      }
    }
    "allow adaptor definitions in contexts" in {
      val input =
        "adaptor fuzz for domain fuzzy context blogger"
      parseInContext[AdaptorDef](input, _.adaptors.head) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            AdaptorDef(
              (2, 1),
              Identifier((2, 9), "fuzz"),
              Some(DomainRef((2, 18), Identifier((2, 25), "fuzzy"))),
              ContextRef((2, 31), Identifier((2, 39), "blogger"))
            )
      }
    }
    "allow interaction definitions in contexts" in {
      val input =
        """interaction dosomething {
          |  role SomeActor {
          |    option is human
          |    handles "Doing stuff"
          |    requires "Skills" }
          |  directive 'perform command' option is asynch from role SomeActor to
          |  entity myLittlePony with command DoAThing
          |  processing doSomeProcessing for entity myLittlePony
          |  as "This does some stuff"
          |  message 'handle a thing' option is asynch from entity myLittlePony to
          |    entity Unicorn with command HandleAThing
          |}
          |""".stripMargin
      parseDefinition[InteractionDef](input) match {
        case Left(msg) => fail(msg)
        case Right(content) =>
          content mustBe
            InteractionDef(
              (1, 1),
              Identifier((1, 13), "dosomething"),
              Seq(
                RoleDef(
                  (2, 3),
                  Identifier((2, 8), "SomeActor"),
                  Seq(HumanOption((3, 15))),
                  List(LiteralString((4, 13), "Doing stuff")),
                  List(LiteralString((5, 14), "Skills"))
                )
              ),
              Seq(
                DirectiveActionDef(
                  (6, 3),
                  Identifier((6, 13), "perform command"),
                  Seq(AsynchOption((6, 41))),
                  RoleRef((6, 53), Identifier((6, 58), "SomeActor")),
                  EntityRef((7, 3), Identifier((7, 10), "myLittlePony")),
                  CommandRef((7, 28), Identifier((7, 36), "DoAThing"))
                ),
                ProcessingActionDef(
                  (8, 3),
                  Identifier((8, 14), "doSomeProcessing"),
                  EntityRef((8, 35), Identifier((8, 42), "myLittlePony")),
                  LiteralString((9, 6), "This does some stuff")
                ),
                MessageActionDef(
                  (10, 3),
                  Identifier((10, 11), "handle a thing"),
                  Seq(AsynchOption((10, 38))),
                  EntityRef((10, 50), Identifier((10, 57), "myLittlePony")),
                  EntityRef((11, 5), Identifier((11, 12), "Unicorn")),
                  CommandRef((11, 25), Identifier((11, 33), "HandleAThing"))
                )
              )
            )
      }
    }
  }
}
