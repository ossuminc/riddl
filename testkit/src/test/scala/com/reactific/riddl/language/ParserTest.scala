package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.testkit.ParsingTest
import com.reactific.riddl.language.testkit.TestParser

import java.nio.file.Path

/** Unit Tests For ParserTest */
class ParserTest extends ParsingTest {

  "ParserContext" must {
    "throw on underflow" in {
      val riddlParserInput = RiddlParserInput("")
      val testParser = TestParser(riddlParserInput)
      testParser
    }

  }

  "Parser" must {
    "report bad syntax" in {
      val input = "Flerkins are evil but cute"
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors must not be empty
          errors.head.msg mustBe "Expected one of (end-of-input | \"domain\")"
        case Right(_) => fail("Invalid syntax should make an error")
      }
    }
    "handle missing }" in {
      val input = "domain foo is { author is { ??? }\n"
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors must not be empty
          errors.head.msg.contains("Expected one of (") must be(true)
        case Right(_) => fail("Missing closing brace should make an error")
      }
    }
    "allow an empty funky-name domain" in {
      val input = RiddlParserInput("domain 'foo-fah|roo' is { }")
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) => content mustBe
            Domain((1, 1, rpi), Identifier((1, 8, rpi), "foo-fah|roo"))
      }
    }
    "allow nested domains" in {
      val input = RiddlParserInput("""domain foo is {
                                     |domain bar is { }
                                     |}
                                     |""".stripMargin)
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe RootContainer(Seq[Domain](Domain(
            (1, 1, input),
            Identifier((1, 8, input), "foo"),
            domains =
              Seq(Domain((2, 1, input), Identifier((2, 8, input), "bar")))
          )))
      }
    }
    "allow multiple domains" in {
      val input = RiddlParserInput("""domain foo is { }
                                     |domain bar is { }
                                     |""".stripMargin)
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe RootContainer(Seq[Domain](
            Domain((1, 1, input), Identifier((1, 8, input), "foo")),
            Domain((2, 1, input), Identifier((2, 8, input), "bar"))
          ))
      }
    }
    "allow major definitions to be stubbed with ???" in {
      val input = RiddlParserInput(
        """domain one is { ??? }
          |domain two is {
          |  plant one is { ??? }
          |  plant two is {
          |    pipe a is { ??? }
          |    multi b is { ??? }
          |  }
          |  context one is { ??? }
          |  context two is {
          |    function foo is { ??? }
          |    term expialidocious is described by { ??? }
          |    entity one is { ??? }
          |    entity two is {
          |      state entityState is { ??? }
          |      handler one  is { ??? }
          |      function one is { ??? }
          |      invariant one is { ??? }
          |    }
          |    adaptor one for context over.consumption is { ??? }
          |  }
          |}
          |""".stripMargin
      )
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
      val input = RiddlParserInput("domain foo is { context bar is { } }")
      parseDomainDefinition[Context](input, _.contexts.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) => content mustBe
            Context((1, 17, rpi), id = Identifier((1, 25, rpi), "bar"))
      }
    }
    "allow options on context definitions" in {
      val input = RiddlParserInput(
        "context bar is { options (function, wrapper, gateway ) }"
      )
      parseContextDefinition[Context](input, identity) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) => content mustBe Context(
            (1, 1, rpi),
            Identifier((1, 9, rpi), "bar"),
            Seq(
              FunctionOption((1, 27, rpi)),
              WrapperOption((1, 37, rpi)),
              GatewayOption((1, 46, rpi))
            )
          )
      }
    }
    "allow type definitions in contexts" in {
      val rpi =
        RiddlParserInput("""type Vikings = any of {
                           |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
                           |}""".stripMargin)
      parseInContext(rpi, _.types.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          val expected = Type(
            (2, 1, rpi),
            Identifier((2, 6, rpi), "Vikings"),
            Enumeration(
              (2, 16, rpi),
              Seq(
                Enumerator(
                  (3, 3, rpi),
                  Identifier((3, 3, rpi), "Ragnar"),
                  None
                ),
                Enumerator(
                  (3, 10, rpi),
                  Identifier((3, 10, rpi), "Lagertha"),
                  None
                ),
                Enumerator(
                  (3, 19, rpi),
                  Identifier((3, 19, rpi), "Bjorn"),
                  None
                ),
                Enumerator(
                  (3, 25, rpi),
                  Identifier((3, 25, rpi), "Floki"),
                  None
                ),
                Enumerator(
                  (3, 31, rpi),
                  Identifier((3, 31, rpi), "Rollo"),
                  None
                ),
                Enumerator(
                  (3, 37, rpi),
                  Identifier((3, 37, rpi), "Ivar"),
                  None
                ),
                Enumerator(
                  (3, 42, rpi),
                  Identifier((3, 42, rpi), "Aslaug"),
                  None
                ),
                Enumerator((3, 49, rpi), Identifier((3, 49, rpi), "Ubbe"), None)
              )
            )
          )
          content mustBe expected
      }
    }
    "allow invariant definitions" in {
      val input = RiddlParserInput(
        """invariant large is { "x is greater or equal to 10" }"""
      )
      parseDefinition[Invariant](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) => content mustBe Invariant(
            (1, 11, rpi),
            Identifier((1, 11, rpi), "large"),
            ArbitraryExpression(
              LiteralString((1, 22, rpi), "x is greater or equal to 10")
            ),
            None
          )
      }
    }
    "allow entity definitions in contexts" in {
      val input = RiddlParserInput("""entity Hamburger is {
                                     |  options ( transient, aggregate )
                                     |  state foo is { x: String }
                                     |  handler foo is {}
                                     |  function AnAspect is {
                                     |    EXAMPLE foo {
                                     |      GIVEN "everybody hates me"
                                     |      AND "I'm depressed"
                                     |      WHEN "I go fishing"
                                     |      THEN "I'll just eat worms"
                                     |      ELSE "I'm happy"
                                     |    }
                                     |  }
                                     |}
                                     |""".stripMargin)
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) => content mustBe Entity(
            (1, 1, rpi),
            Identifier((1, 8, rpi), "Hamburger"),
            Seq(EntityTransient((2, 13, rpi)), EntityAggregate((2, 24, rpi))),
            Seq(State(
              (3, 3, rpi),
              Identifier((3, 9, rpi), "foo"),
              Aggregation(
                (3, 16, rpi),
                Seq(Field(
                  (3, 18, rpi),
                  Identifier((3, 18, rpi), "x"),
                  Strng((3, 21, rpi))
                ))
              ),
              None
            )),
            handlers =
              Seq(Handler((4, 11, rpi), Identifier((4, 11, rpi), "foo"))),
            functions = Seq(Function(
              (5, 3, rpi),
              Identifier((5, 12, rpi), "AnAspect"),
              examples = Seq(Example(
                (6, 5, rpi),
                Identifier((6, 13, rpi), "foo"),
                Seq(
                  GivenClause(
                    (7, 7, rpi),
                    Seq(LiteralString((7, 13, rpi), "everybody hates me"))
                  ),
                  GivenClause(
                    (8, 7, rpi),
                    Seq(LiteralString((8, 11, rpi), "I'm depressed"))
                  )
                ),
                Seq(WhenClause(
                  (9, 7, rpi),
                  ArbitraryExpression(
                    LiteralString((9, 12, rpi), "I go fishing")
                  )
                )),
                Seq(ThenClause(
                  (10, 7, rpi),
                  ArbitraryAction(
                    (10, 12, rpi),
                    LiteralString((10, 12, rpi), "I'll just eat worms"),
                    None
                  )
                )),
                Seq(ButClause(
                  (11, 7, rpi),
                  ArbitraryAction(
                    (11, 12, rpi),
                    LiteralString((11, 12, rpi), "I'm happy"),
                    None
                  )
                ))
              ))
            ))
          )
      }
    }
    "allow adaptor definitions" in {
      val input =
        RiddlParserInput("adaptor fuzz for context foo.bar is { ??? }")
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) => content mustBe Adaptor(
            (1, 1, rpi),
            Identifier((1, 9, rpi), "fuzz"),
            ContextRef(
              (1, 18, rpi),
              PathIdentifier((1, 26, rpi), Seq("bar", "foo"))
            ),
            Seq.empty[Adaptation]
          )
      }
    }

    "allow functions" in {
      val input = """
                    |function foo is {
                    |  requires {b:Boolean}
                    |  yields {i:Integer}
                    |}
                    |""".stripMargin

      parseDefinition[Function](RiddlParserInput(input)) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((function, _)) => function must matchPattern {
            case Function(
                  _,
                  Identifier(_, "foo"),
                  Some(
                    Aggregation(
                      _,
                      Seq(Field(_, Identifier(_, "b"), Bool(_), _, _))
                    )
                  ),
                  Some(
                    Aggregation(
                      _,
                      Seq(Field(_, Identifier(_, "i"), Integer(_), _, _))
                    )
                  ),
                  _,
                  None,
                  None
                ) =>
          }
      }
    }
    "allow descriptions from files" in {
      val input =
        RiddlParserInput("""domain foo is { ??? } explained in file "foo.md"
                           |""".stripMargin)
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(rc) => rc mustBe RootContainer(Seq[Domain](Domain(
            (1, 1, input),
            Identifier((1, 8, input), "foo"),
            description =
              Some(FileDescription((1, 36, input), Path.of("foo.md")))
          )))
      }

    }
  }
}
