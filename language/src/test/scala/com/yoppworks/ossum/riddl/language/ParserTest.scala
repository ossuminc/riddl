package com.yoppworks.ossum.riddl.language

import RiddlParserInput._
import org.scalatest.Assertion

/** Unit Tests For ParserTest */
class ParserTest extends ParsingTest {

  import AST._

  def getOrElseFail[R](
    e: Either[Seq[ParserError], R]
  )(f: R => Assertion): Assertion = e match {
    case Left(errors) =>
      val msg = errors.iterator.map(_.format).mkString
      fail(msg)
    case Right(r) =>
      f(r)
  }

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
      getOrElseFail(parseTopLevelDomain(input, _.contents.head)) { content =>
        content mustBe Domain(1 -> 1, Identifier(1 -> 8, "foo-fah|roo"))
      }
    }
    "allow nested domains" in {
      val input =
        """domain foo {
          |domain bar { }
          |}
          |""".stripMargin
      getOrElseFail(parseTopLevelDomains(input)) { content =>
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
      getOrElseFail(parseTopLevelDomains(input)) { content =>
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
          |    adaptor one for context over.consumption { ??? }
          |  }
          |}
          |""".stripMargin
      getOrElseFail(parseTopLevelDomains(input)) { content =>
        content.contents must not be empty
        succeed
      }
    }
    "allow context definitions in domains" in {
      val input = "domain foo { context bar { } }"
      getOrElseFail(parseDomainDefinition[Context](input, _.contexts.head)) {
        content =>
          content mustBe Context(1 -> 14, id = Identifier(1 -> 22, "bar"))
      }
    }
    "allow options on context definitions" in {
      val input =
        "context bar { options (function wrapper gateway ) }"
      getOrElseFail(parseContextDefinition[Context](input, identity)) {
        content =>
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
      getOrElseFail(parseDomainDefinition[Topic](input, _.topics.head)) {
        content =>
          content mustBe Topic(4 -> 5, Identifier(4 -> 11, "bar"))
      }
    }
    "allow type definitions in contexts" in {
      val input =
        """type Vikings = any of {
          |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
          |}""".stripMargin
      getOrElseFail(parseInContext(input, _.types.head)) { content =>
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
      getOrElseFail(
        parseDomainDefinition[Command](input, _.topics.head.commands.head)
      ) { content =>
        content mustBe
          Command(
            2 -> 1,
            Identifier(2 -> 1, "DoThisThing"),
            Aggregation(2 -> 16, Seq.empty[Field]),
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
      getOrElseFail(parseDomainDefinition(input, _.topics.head.events.head)) {
        content =>
          content mustBe
            Event(
              2 -> 1,
              Identifier(2 -> 1, "ThingWasDone"),
              Aggregation(2 -> 17, Seq.empty[Field])
            )
      }
    }
    "allow query definitions in topics" in {
      val input = """domain bar { topic foo is { queries {
                    |FindThisThing = {} yields result SomeResult
                    |} } }
                    |""".stripMargin
      getOrElseFail(parseDomainDefinition(input, _.topics.head.queries.head)) {
        content =>
          content mustBe
            Query(
              2 -> 1,
              Identifier(2 -> 1, "FindThisThing"),
              Aggregation(2 -> 17, Seq.empty[Field]),
              ResultRef(2 -> 27, PathIdentifier(2 -> 34, Seq("SomeResult")))
            )
      }
    }
    "allow result definitions in topics" in {
      val input = """domain bar { topic foo is {
                    |result ThisQueryResult = {}
                    |} }
                    |""".stripMargin
      getOrElseFail(parseDomainDefinition(input, _.topics.head.results.head)) {
        content =>
          content mustBe
            Result(
              2 -> 8,
              Identifier(2 -> 8, "ThisQueryResult"),
              Aggregation(2 -> 26, Seq.empty[Field])
            )

      }
    }
    "allow entity definitions in contexts" in {
      val input: String =
        """entity Hamburger is {
          |  options ( persistent aggregate )
          |  state foo is { x: String }
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
      getOrElseFail(parseDefinition[Entity](input)) { content =>
        content mustBe Entity(
          SoftwareEntityKind(1 -> 1),
          1 -> 1,
          Identifier(1 -> 8, "Hamburger"),
          Seq(EntityPersistent(2 -> 13), EntityAggregate(2 -> 24)),
          Seq(
            State(
              3 -> 3,
              Identifier(3 -> 9, "foo"),
              Aggregation(
                3 -> 16,
                Seq(Field(3 -> 18, Identifier(3 -> 18, "x"), Strng(3 -> 21)))
              ),
              None
            )
          ),
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
        "adaptor fuzz for context foo.bar { ??? }"
      getOrElseFail(parseDefinition[Adaptor](input)) { content =>
        content mustBe
          Adaptor(
            1 -> 1,
            Identifier(1 -> 9, "fuzz"),
            ContextRef(1 -> 18, PathIdentifier(1 -> 26, Seq("bar", "foo"))),
            Seq.empty[AdaptorMapping]
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
      getOrElseFail(parseDefinition[Interaction](input)) { content =>
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

  "allow human roles" in {
    val parser = TestParser("option is human")
    getOrElseFail(parser.expect(parser.roleOptions(_))) { roleOptions =>
      roleOptions mustBe List(AST.HumanOption(Location(1, 11)))
    }
  }
  "allow device roles" in {
    val parser = TestParser("option is device")
    getOrElseFail(parser.expect(parser.roleOptions(_))) { roleOptions =>
      roleOptions mustBe List(AST.DeviceOption(Location(1, 11)))
    }
  }
}
