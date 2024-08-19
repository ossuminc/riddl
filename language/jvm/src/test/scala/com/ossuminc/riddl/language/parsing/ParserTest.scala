/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.AST

import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.TestData

/** Unit Tests For Parsing */
class ParserTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput._

  "ParserContext" must {
    "throw on underflow" in { (td: TestData) =>
      val riddlParserInput = RiddlParserInput("", td)
      val testParser = TestParser(riddlParserInput)
      testParser
    }
  }

  "Parser" must {
    "report bad syntax" in { (td: TestData) =>
      val input = RiddlParserInput("Flerkins are evil but cute", td)
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors must not be empty
          val head = errors.head.message
          head must include("Expected one of")
          head must include("\"author\"")
          head must include("\"domain\"")
        case Right(_) => fail("Invalid syntax should make an error")
      }
    }
    "ensure keywords are distinct" in { (td: TestData) =>
      val input = RiddlParserInput("domainfoois { author nobody is { ??? } } \n", td)
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors must not be empty
          errors.head.message must include("white space after keyword")
        case Right(_) => fail("'domainfoois' should be flagged as needing whitespace after a keyword")
      }
    }
    "handle missing }" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { author nobody is { ??? } \n", td)
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors must not be empty
          if errors.head.message.startsWith("Expected one of (") && errors.head.message.contains("context") then succeed
          else fail(errors.format)
        case Right(_) => fail("Missing closing brace should make an error")
      }
    }
    "allow an empty funky-name domain" in { (td: TestData) =>
      val input = RiddlParserInput("domain 'foo-fah|roo' is { ??? }", td)
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe
            Domain((1, 1, rpi), Identifier((1, 8, rpi), "foo-fah|roo"))
      }
    }
    "allow nested domains" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
                                     |domain bar is { ??? }
                                     |}
                                     |""".stripMargin,
        td
      )
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content.contents mustBe Seq[Domain](
            Domain(
              (1, 1, input),
              Identifier((1, 8, input), "foo"),
              contents = Seq(Domain((2, 1, input), Identifier((2, 8, input), "bar")))
            )
          )
      }
    }
    "allow multiple domains" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is { ??? }
                                     |domain bar is { ??? }
                                     |""".stripMargin,
        td
      )
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content.contents mustBe Seq[Domain](
            Domain((1, 1, input), Identifier((1, 8, input), "foo")),
            Domain((2, 1, input), Identifier((2, 8, input), "bar"))
          )
      }
    }
    "allow major definitions to be stubbed with ???" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain one is { ??? }
          |domain two is {
          |  context one is {
          |    router b is { ??? }
          |  }
          |  context two is {
          |    function foo is { ??? }
          |    term expialidocious is described by { ??? }
          |    entity one is { ??? }
          |    entity two is {
          |      type twoState is { foo: Integer }
          |      state entityState of twoState
          |      handler one  is { ??? }
          |      function one is { ??? }
          |      invariant one is "???"
          |    }
          |    adaptor one from context over.consumption is { ??? }
          |  }
          |}
          |""".stripMargin,
        td
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
    "allow context definitions in domains" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { context bar is { ??? } }", td)
      parseDomainDefinition[Context](input, _.contexts.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe
            Context((1, 17, rpi), id = Identifier((1, 25, rpi), "bar"))
      }
    }
    "allow options on context definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context bar is {
          |  option service
          |  option wrapper
          |  option gateway
          |}
          |""".stripMargin,
        td
      )
      parseContextDefinition[Context](input, identity) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe Context(
            (1, 1, rpi),
            Identifier((1, 9, rpi), "bar"),
            Seq(
              OptionValue((2, 10, rpi), "service", Seq.empty),
              OptionValue((3, 10, rpi), "wrapper", Seq.empty),
              OptionValue((4, 10, rpi), "gateway", Seq.empty)
            )
          )
      }
    }
    "allow type definitions in contexts" in {  (td: TestData) =>
      val rpi = RiddlParserInput(
      """type Vikings = any of {
        |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
        |}""".stripMargin, td)
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
    "allow invariant definitions" in { (td: TestData) =>
      val input = RiddlParserInput("""invariant large is "x is greater or equal to 10" """, td)
      parseDefinition[Invariant](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe Invariant(
            (1, 11, rpi),
            Identifier((1, 11, rpi), "large"),
            Option(LiteralString((1, 20, rpi), "x is greater or equal to 10")),
            None,
            None
          )
      }
    }
    "allow entity definitions" in { (td: TestData) =>
      val input = RiddlParserInput("""entity Hamburger is {
         |  option transient
         |  option aggregate
         |  type Foo is { x: String }
         |  state BurgerState of type BurgerStruct
         |  handler BurgerHandler is {}
         |}
         |""".stripMargin,
        td
      )
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          val expected = Entity(
            (1, 1, rpi),
            Identifier((1, 8, rpi), "Hamburger"),
            Seq(
              OptionValue((2, 10, rpi), "transient", Seq.empty),
              OptionValue((3, 10, rpi), "aggregate", Seq.empty),
              Type(
                (4, 3, rpi),
                Identifier((4, 8, rpi), "Foo"),
                Aggregation(
                  (4, 15, rpi),
                  List(Field((4, 17, rpi), Identifier((4, 17, rpi), "x"), String_((4, 20, rpi))))
                )
              ),
              State(
                (5, 3, rpi),
                Identifier((5, 9, rpi), "BurgerState"),
                TypeRef((5, 24, rpi), "type", PathIdentifier((5, 29, rpi), List("BurgerStruct")))
              ),
              Handler((6, 11, rpi), Identifier((6, 11, rpi), "BurgerHandler"))
            )
          )
          content mustBe expected
      }
    }
    "allow adaptor definitions" in { (td: TestData) =>
      val input = RiddlParserInput("adaptor fuzz from context foo.bar is { ??? }", td)
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe Adaptor(
            (1, 1, rpi),
            Identifier((1, 9, rpi), "fuzz"),
            InboundAdaptor((1, 14, rpi)),
            ContextRef(
              (1, 19, rpi),
              PathIdentifier((1, 27, rpi), Seq("foo", "bar"))
            ),
            Seq.empty
          )
      }
    }

    "allow functions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |function foo is {
          |  requires { b : Boolean}
          |  returns { i : Integer}
          |  body { ??? }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Function](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((function, _)) =>
          function must matchPattern {
            case Function(
                  _,
                  Identifier(_, "foo"),
                  Some(Aggregation(_, Seq(Field(_, Identifier(_, "b"), Bool(_), _, _)))),
                  Some(Aggregation(_, Seq(Field(_, Identifier(_, "i"), Integer(_), _, _)))),
                  _,
                  _
                ) =>
          }
      }
    }
    "support Replica types in Contexts" in {  (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  context bar is {
          |    type crdt is replica of Integer
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Domain](input) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, rpi)) =>
          val typ = domain.contexts.head.types.head
          typ.typEx mustBe Replica((3, 18, rpi), Integer((3, 29, rpi)))
      }
    }
    "parse from a complex file" in { (td: TestData) =>
      val rpi = RiddlParserInput.fromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"),td)
      parseTopLevelDomains(rpi) match {
        case Left(errors) =>
          fail(errors.format)
        case Right(root) =>
          /*
          // Top Level Author
          author Reid is { name: "Reid" email: "reid@ossum.biz" }

         // A top level domain
        domain Everything is {
          // How to mark a definition with the author that created it
          by author Reid

          type SomeType is String // <-- that's a type

          /* This is another way to do a comment, just like C/C++ */
          command DoAThing is { thingField: Integer }
           */
          root.contents.startsWith(
            Seq(
              LineComment((1, 1, rpi), "Top Level Author"),
              Author(
                (2, 1, rpi),
                Identifier((2, 8, rpi), "Reid"),
                LiteralString((2, 23, rpi), "Reid"),
                LiteralString((2, 37, rpi), "reid@ossum.biz")
              )
            )
          )
      }
    }
  }
}
