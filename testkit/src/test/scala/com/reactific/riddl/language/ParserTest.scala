package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.testkit.{ParsingTest, TestParser}

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
      val input = "domain 'foo-fah|roo' is { }"
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe
            Domain(1 -> 1, Identifier(1 -> 8, "foo-fah|roo"))
      }
    }
    "allow nested domains" in {
      val input = """domain foo is {
                    |domain bar is { }
                    |}
                    |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe RootContainer(Seq[Domain](Domain(
            1 -> 1,
            Identifier(1 -> 8, "foo"),
            domains = Seq(Domain(2 -> 1, Identifier(2 -> 8, "bar")))
          )))
      }
    }
    "allow multiple domains" in {
      val input = """domain foo is { }
                    |domain bar is { }
                    |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe RootContainer(Seq[Domain](
            Domain(1 -> 1, Identifier(1 -> 8, "foo")),
            Domain(2 -> 1, Identifier(2 -> 8, "bar"))
          ))
      }
    }
    "allow major definitions to be stubbed with ???" in {
      val input = """domain one is { ??? }
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
      val input = "domain foo is { context bar is { } }"
      parseDomainDefinition[Context](input, _.contexts.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe
            Context(1 -> 17, id = Identifier(1 -> 25, "bar"))
      }
    }
    "allow options on context definitions" in {
      val input = "context bar is { options (function, wrapper, gateway ) }"
      parseContextDefinition[Context](input, identity) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe Context(
            1 -> 1,
            Identifier(1 -> 9, "bar"),
            Seq(
              FunctionOption(1 -> 27),
              WrapperOption(1 -> 37),
              GatewayOption(1 -> 46)
            )
          )
      }
    }
    "allow type definitions in contexts" in {
      val input = """type Vikings = any of {
                    |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
                    |}""".stripMargin
      parseInContext(input, _.types.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe Type(
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
    "allow invariant definitions" in {
      val input: String =
        """invariant large is { "x is greater or equal to 10" }"""
      parseDefinition[Invariant](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe Invariant(
            1 -> 11,
            Identifier(1 -> 11, "large"),
            ArbitraryCondition(
              LiteralString(1 -> 22, "x is greater or equal to 10")
            ),
            None
          )
      }
    }
    "allow entity definitions in contexts" in {
      val input: String = """entity Hamburger is {
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
                            |""".stripMargin
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe Entity(
            1 -> 1,
            Identifier(1 -> 8, "Hamburger"),
            Seq(EntityTransient(2 -> 13), EntityAggregate(2 -> 24)),
            Seq(State(
              3 -> 3,
              Identifier(3 -> 9, "foo"),
              Aggregation(
                3 -> 16,
                Seq(Field(3 -> 18, Identifier(3 -> 18, "x"), Strng(3 -> 21)))
              ),
              None
            )),
            handlers = Seq(Handler(4 -> 11, Identifier(4 -> 11, "foo"))),
            functions = Seq(Function(
              5 -> 3,
              Identifier(5 -> 12, "AnAspect"),
              examples = Seq(Example(
                6 -> 5,
                Identifier(6 -> 13, "foo"),
                Seq(
                  GivenClause(
                    7 -> 7,
                    Seq(LiteralString(7 -> 13, "everybody hates me"))
                  ),
                  GivenClause(
                    8 -> 7,
                    Seq(LiteralString(8 -> 11, "I'm depressed"))
                  )
                ),
                Seq(WhenClause(
                  9 -> 7,
                  ArbitraryCondition(LiteralString(9 -> 12, "I go fishing"))
                )),
                Seq(ThenClause(
                  10 -> 7,
                  ArbitraryAction(
                    10 -> 12,
                    LiteralString(10 -> 12, "I'll just eat worms"),
                    None
                  )
                )),
                Seq(ButClause(
                  11 -> 7,
                  ArbitraryAction(
                    11 -> 12,
                    LiteralString(11 -> 12, "I'm happy"),
                    None
                  )
                ))
              ))
            ))
          )
      }
    }
    "allow adaptor definitions" in {
      val input = "adaptor fuzz for context foo.bar is { ??? }"
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe Adaptor(
            1 -> 1,
            Identifier(1 -> 9, "fuzz"),
            ContextRef(1 -> 18, PathIdentifier(1 -> 26, Seq("bar", "foo"))),
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
        case Right(content) => content must matchPattern {
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
      val input = """domain foo is { ??? } explained in file "foo.md"
                    |""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => content mustBe RootContainer(Seq[Domain](
          Domain(1 -> 1, Identifier(1 -> 8, "foo"),
            description = Some(FileDescription(1->36,Path.of("foo.md")))),
        ))
      }

    }
  }
}
