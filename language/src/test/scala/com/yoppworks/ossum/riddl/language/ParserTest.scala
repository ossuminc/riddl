package com.yoppworks.ossum.riddl.language

import scala.collection.immutable.ListMap

import RiddlParserInput._

/** Unit Tests For ParserTest */
class ParserTest extends ParsingTest {

  import AST._

  "Parser" should {
    "report bad syntax" in {
      val input = "Flerkins are evil but cute"
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors.map(_.format).foreach(println(_))
          errors must not be empty
        case Right(content) =>
          fail("Invalid syntax should make an error")
      }
    }
    "allow an empty funky-name domain" in {
      val input = "domain 'foo-fah|roo' { }"
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Domain(1 -> 1, Identifier(1 -> 8, "foo-fah|roo"))
      }
    }
    "allow nesed domains" in {
      val input =
        """domain foo {
          |domain bar { }
          |}
          |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            RootContainer(
              Seq[Domain](
                Domain(
                  1 -> 1,
                  Identifier(1 -> 8, "foo"),
                  domains = Seq(Domain(2 -> 1, Identifier(2 -> 8, "bar")))
                )
              )
            )
      }
    }
    "allow multiple domains" in {
      val input =
        """domain foo { }
          |domain bar { }
          |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            RootContainer(
              Seq[Domain](
                Domain(1 -> 1, Identifier(1 -> 8, "foo")),
                Domain(2 -> 1, Identifier(2 -> 8, "bar"))
              )
            )
      }
    }
    "allow context definitions in domains" in {
      val input = "domain foo { context bar { } }"
      parseDomainDefinition[Context](input, _.contexts.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Context(1 -> 14, id = Identifier(1 -> 22, "bar"))
      }
    }
    "allow options on context definitions" in {
      val input =
        "context bar { options (function wrapper gateway ) }"
      parseContextDefinition[Context](input, identity) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Context(
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
      parseDomainDefinition[Topic](input, _.topics.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Topic(4 -> 5, Identifier(4 -> 11, "bar"))
      }
    }
    "allow type definitions in contexts" in {
      val input =
        """type Vikings = any of {
          |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
          |}""".stripMargin
      parseInContext(input, _.types.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Type(
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
      parseDomainDefinition[Command](input, _.topics.head.commands.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Command(
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
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Event(
              2 -> 1,
              Identifier(2 -> 1, "ThingWasDone"),
              TypeRef(2 -> 17, Identifier(2 -> 17, "SomeType"))
            )
      }
    }
    "allow query definitions in topics" in {
      val input = """domain bar { topic foo is { queries {
                    |FindThisThing = String yields result SomeResult
                    |} } }
                    |""".stripMargin
      parseDomainDefinition(input, _.topics.head.queries.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Query(
              2 -> 1,
              Identifier(2 -> 1, "FindThisThing"),
              Strng(2 -> 17),
              ResultRef(2 -> 31, Identifier(2 -> 38, "SomeResult"))
            )
      }
    }
    "allow result definitions in topics" in {
      val input = """domain bar { topic foo is {
                    |result ThisQueryResult = SomeType
                    |} }
                    |""".stripMargin
      parseDomainDefinition(input, _.topics.head.results.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Result(
              2 -> 8,
              Identifier(2 -> 8, "ThisQueryResult"),
              TypeRef(2 -> 26, Identifier(2 -> 26, "SomeType"))
            )

      }
    }
    "allow entity definitions in contexts" in {
      val input: String =
        """entity Hamburger is {
          |  options ( persistent aggregate )
          |  state { x: String }
          |  consumer foo for topic EntityChannel
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
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe Entity(
            SoftwareEntityKind(1 -> 1),
            1 -> 1,
            Identifier(1 -> 8, "Hamburger"),
            Aggregation(
              3 -> 9,
              ListMap(Identifier(3 -> 11, "x") -> Strng(3 -> 14))
            ),
            Seq(EntityPersistent(2 -> 13), EntityAggregate(2 -> 24)),
            consumers = Seq(
              Consumer(
                4 -> 12,
                Identifier(4 -> 12, "foo"),
                TopicRef(4 -> 20, Identifier(4 -> 26, "EntityChannel"))
              )
            ),
            features = Seq(
              Feature(
                5 -> 3,
                Identifier(5 -> 11, "AnAspect"),
                Seq(
                  LiteralString(
                    7 -> 7,
                    "This is some aspect of the entity"
                  )
                ),
                Some(
                  Background(
                    9 -> 5,
                    Seq(
                      Given(
                        10 -> 7,
                        LiteralString(
                          10 -> 13,
                          "Nobody loves me"
                        )
                      )
                    )
                  )
                ),
                Seq(
                  Example(
                    12 -> 5,
                    Identifier(12 -> 13, "foo"),
                    LiteralString(13 -> 7, "My Fate"),
                    Seq(
                      Given(
                        14 -> 7,
                        LiteralString(14 -> 13, "everybody hates me")
                      ),
                      Given(15 -> 7, LiteralString(15 -> 11, "I'm depressed"))
                    ),
                    Seq(When(16 -> 7, LiteralString(16 -> 12, "I go fishing"))),
                    Seq(
                      Then(
                        17 -> 7,
                        LiteralString(17 -> 12, "I'll just eat worms")
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
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Adaptor(
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
      parseDefinition[Interaction](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Interaction(
              1 -> 1,
              Identifier(1 -> 13, "dosomething"),
              Seq(
                Role(
                  2 -> 3,
                  Identifier(2 -> 8, "SomeActor"),
                  Seq(HumanOption(3 -> 15)),
                  List(LiteralString(5 -> 8, "Doing stuff")),
                  List(LiteralString(8 -> 8, "Skills"))
                )
              ),
              Seq(
                DirectiveAction(
                  11 -> 3,
                  Identifier(11 -> 13, "perform a command"),
                  Seq(AsynchOption(11 -> 43)),
                  RoleRef(12 -> 10, Identifier(12 -> 15, "SomeActor")),
                  EntityRef(13 -> 8, Identifier(13 -> 15, "myLittlePony")),
                  CommandRef(13 -> 31, Identifier(13 -> 39, "DoAThing")),
                  Seq.empty[Reaction]
                ),
                MessageAction(
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
