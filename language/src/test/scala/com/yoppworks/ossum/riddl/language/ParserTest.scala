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
    "allow nested domains" in {
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
    "allow major definitions to be stubbed with ???" in {
      val input =
        """domain one { ??? }
          |domain two {
          |  topic one { ??? }
          |  interaction one { ??? }
          |  context one { ??? }
          |  context two {
          |    interaction two { ??? }
          |    entity one { ??? }
          |    entity two {
          |      state is {}
          |      feature one { ??? }
          |      consumer one for topic foo is { ??? }
          |      function one { ??? }
          |      invariant one { ??? }
          |    }
          |    adaptor one { ??? }
          |  }
          |}
          |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content.contents must not be empty
          succeed
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
          |DoThisThing is {} yields event ThingWasDone
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
              Aggregation(2 -> 16, ListMap.empty[Identifier, TypeExpression]),
              Seq(
                EventRef(2 -> 26, PathIdentifier(2 -> 32, Seq("ThingWasDone")))
              )
            )
      }
    }
    "allow event definitions in contexts" in {
      val input = """domain bar { topic foo is { events {
                    |ThingWasDone is {}
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
              Aggregation(2 -> 17, ListMap.empty[Identifier, TypeExpression])
            )
      }
    }
    "allow query definitions in topics" in {
      val input = """domain bar { topic foo is { queries {
                    |FindThisThing = {} yields result SomeResult
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
              Aggregation(2 -> 17, ListMap.empty[Identifier, TypeExpression]),
              ResultRef(2 -> 27, PathIdentifier(2 -> 34, Seq("SomeResult")))
            )
      }
    }
    "allow result definitions in topics" in {
      val input = """domain bar { topic foo is {
                    |result ThisQueryResult = {}
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
              Aggregation(2 -> 26, ListMap.empty[Identifier, TypeExpression])
            )

      }
    }
    "allow entity definitions in contexts" in {
      val input: String =
        """entity Hamburger is {
          |  options ( persistent aggregate )
          |  state { x: String }
          |  consumer foo of topic EntityChannel {}
          |  feature AnAspect {
          |    BACKGROUND {
          |      Given "Nobody loves me"
          |    }
          |    EXAMPLE foo {
          |      GIVEN "everybody hates me"
          |      AND "I'm depressed"
          |      WHEN "I go fishing"
          |      THEN "I'll just eat worms"
          |      ELSE "I'm happy"
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
                TopicRef(4 -> 19, PathIdentifier(4 -> 25, Seq("EntityChannel")))
              )
            ),
            features = Seq(
              Feature(
                5 -> 3,
                Identifier(5 -> 11, "AnAspect"),
                Some(
                  Background(
                    6 -> 5,
                    Seq(
                      Given(
                        7 -> 7,
                        Seq(
                          LiteralString(
                            7 -> 13,
                            "Nobody loves me"
                          )
                        )
                      )
                    )
                  )
                ),
                Seq(
                  Example(
                    9 -> 5,
                    Identifier(9 -> 13, "foo"),
                    Seq(
                      Given(
                        10 -> 7,
                        Seq(LiteralString(10 -> 13, "everybody hates me"))
                      ),
                      Given(
                        11 -> 7,
                        Seq(LiteralString(11 -> 11, "I'm depressed"))
                      )
                    ),
                    Seq(
                      When(
                        12 -> 7,
                        Seq(LiteralString(12 -> 12, "I go fishing"))
                      )
                    ),
                    Seq(
                      Then(
                        13 -> 7,
                        Seq(LiteralString(13 -> 12, "I'll just eat worms"))
                      )
                    ),
                    Seq(
                      Else(
                        14 -> 7,
                        Seq(LiteralString(14 -> 12, "I'm happy"))
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
        "adaptor fuzz  { ??? }"
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content mustBe
            Adaptor(
              1 -> 1,
              Identifier(1 -> 9, "fuzz")
            )
      }
    }
    "allow interaction definitions" in {
      val input =
        """interaction dosomething {
          |  message 'perform a command' option is async
          |    from entity Unicorn
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
                MessageAction(
                  2 -> 3,
                  Identifier(2 -> 11, "perform a command"),
                  Seq(AsynchOption(2 -> 41)),
                  EntityRef(3 -> 10, PathIdentifier(3 -> 17, Seq("Unicorn"))),
                  EntityRef(
                    4 -> 8,
                    PathIdentifier(4 -> 15, Seq("myLittlePony"))
                  ),
                  CommandRef(
                    4 -> 31,
                    PathIdentifier(4 -> 39, Seq("DoAThing"))
                  ),
                  Seq.empty[Reaction]
                ),
                MessageAction(
                  6 -> 3,
                  Identifier(6 -> 11, "handle a thing"),
                  Seq(AsynchOption(6 -> 38)),
                  EntityRef(
                    7 -> 10,
                    PathIdentifier(7 -> 17, Seq("myLittlePony"))
                  ),
                  EntityRef(8 -> 8, PathIdentifier(8 -> 15, Seq("Unicorn"))),
                  CommandRef(
                    8 -> 26,
                    PathIdentifier(8 -> 34, Seq("HandleAThing"))
                  ),
                  Seq.empty[Reaction]
                )
              )
            )
      }
    }
  }
}
