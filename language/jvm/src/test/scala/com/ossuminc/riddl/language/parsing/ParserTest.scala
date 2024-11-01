/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.{Await, JVMPlatformContext, PathUtils, PlatformContext}
import com.ossuminc.riddl.utils.{pc, ec}

import java.nio.file.Path
import org.scalatest.TestData
import scala.concurrent.duration.DurationInt

/** Unit Tests For Parsing */
class ParserTest extends ParsingTest with org.scalatest.Inside {

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
            Domain(At(rpi, 0, 31), Identifier(At(rpi, 7, 21), "foo-fah|roo"))
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
              contents = Contents(Domain((2, 1, input), Identifier((2, 8, input), "bar")))
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
          |    entity one is { ??? }
          |    entity two is {
          |      type twoState is { foo: Integer }
          |      state entityState of twoState
          |      handler one  is { ??? }
          |      function one is { ??? }
          |      invariant one is "???"
          |    }
          |    adaptor one from context over.consumption is { ??? }
          |  } with {
          |   term expialidocious is "supercalifragilistic" with { ??? }
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
            Context(At(rpi, 16, 39), id = Identifier(At(rpi, 24, 28), "bar"))
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
          content must be(
            Context(
              At(rpi, 0, 70),
              Identifier(At(rpi, 8, 12), "bar"),
              Contents(
                OptionValue(At(rpi, 19, 36), "service", Seq.empty),
                OptionValue(At(rpi, 36, 53), "wrapper", Seq.empty),
                OptionValue(At(rpi, 53, 68), "gateway", Seq.empty)
              )
            )
          )
      }
    }
    "allow type definitions in contexts" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """type Vikings = any of {
        |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
        |}""".stripMargin,
        td
      )
      parseInContext(rpi, _.types.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          val expected = Type(
            At(rpi, 2, 1),
            Identifier(At(rpi, 2, 6), "Vikings"),
            Enumeration(
              At(rpi, 2, 16),
              Contents(
                Enumerator(
                  At(rpi, 3, 3),
                  Identifier(At(rpi, 3, 3), "Ragnar"),
                  None
                ),
                Enumerator(
                  At(rpi, 3, 10),
                  Identifier(At(rpi, 3, 10), "Lagertha"),
                  None
                ),
                Enumerator(
                  At(rpi, 3, 19),
                  Identifier(At(rpi, 3, 19), "Bjorn"),
                  None
                ),
                Enumerator(
                  At(rpi, 3, 25),
                  Identifier(At(rpi, 3, 25), "Floki"),
                  None
                ),
                Enumerator(
                  At(rpi, 3, 31),
                  Identifier(At(rpi, 3, 31), "Rollo"),
                  None
                ),
                Enumerator(
                  At(rpi, 3, 37),
                  Identifier(At(rpi, 3, 37), "Ivar"),
                  None
                ),
                Enumerator(
                  At(rpi, 3, 42),
                  Identifier(At(rpi, 3, 42), "Aslaug"),
                  None
                ),
                Enumerator(At(rpi, 3, 49), Identifier(At(rpi, 3, 49), "Ubbe"), None)
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
            At(rpi, 0, 49),
            Identifier(At(rpi, 10, 16), "large"),
            Option(LiteralString(At(rpi, 19, 48), "x is greater or equal to 10"))
          )
      }
    }
    "allow entity definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """entity Hamburger is {
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
            At(rpi, 1, 1),
            Identifier(At(rpi, 1, 8), "Hamburger"),
            Contents(
              OptionValue(At(rpi, 2, 10), "transient", Seq.empty),
              OptionValue(At(rpi, 3, 10), "aggregate", Seq.empty),
              Type(
                At(rpi, 4, 3),
                Identifier(At(rpi, 4, 8), "Foo"),
                Aggregation(
                  At(rpi, 4, 15),
                  Contents(Field(At(rpi, 4, 17), Identifier(At(rpi, 4, 17), "x"), String_(At(rpi, 4, 20))))
                )
              ),
              State(
                At(rpi, 5, 3),
                Identifier(At(rpi, 5, 9), "BurgerState"),
                TypeRef(At(rpi, 5, 24), "type", PathIdentifier(At(rpi, 5, 29), List("BurgerStruct")))
              ),
              Handler(At(rpi, 6, 11), Identifier(At(rpi, 6, 11), "BurgerHandler"))
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
            At(rpi, 1, 1),
            Identifier(At(rpi, 1, 9), "fuzz"),
            InboundAdaptor(At(rpi, 1, 14)),
            ContextRef(
              At(rpi, 1, 19),
              PathIdentifier(At(rpi, 1, 27), Seq("foo", "bar"))
            ),
            Contents.empty
          )
      }
    }

    "allow functions" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """
          |function foo is {
          |  requires { b : Boolean}
          |  returns { i : Integer}
          |  ???
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Function](rpi) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((function, _)) =>
          inside(function) {
            case Function(
                  _,
                  Identifier(_, "foo"),
                  Some(Aggregation(_, firstAggrContents)),
                  Some(Aggregation(_, secondAggrContents)),
                  _,
                  _
                ) =>
              firstAggrContents must be(
                Contents(Field(At(rpi, 3, 14), Identifier(At(3, 14, rpi), "b"), Bool(At(3, 18, rpi)), Contents.empty))
              )
              secondAggrContents must be(
                Contents(
                  Field(At(4, 13, rpi), Identifier(At(4, 13, rpi), "i"), Integer(At(4, 17, rpi)), Contents.empty)
                )
              )
          }
      }
    }
    "handle a comment" in { (td: TestData) =>
      val input: RiddlParserInput = RiddlParserInput(
        """/* this is a comment */""".stripMargin,
        td
      )
      parseInContext[InlineComment](input, _.contents.filter[InlineComment].head) match
        case Left(messages) =>
          fail(messages.format)
        case Right(comment, _) =>
          comment.lines.head must be("this is a comment ")
    }
    "support Replica types in Contexts" in { (td: TestData) =>
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
          typ.typEx mustBe Replica(At(rpi, 3, 18), Integer(At(rpi, 3, 29)))
      }
    }
    "parse from a complex file" in { (td: TestData) =>
      val url = PathUtils.urlFromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"))
      val future = RiddlParserInput.fromURL(url, td).map { rpi =>
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
                LineComment(At(rpi, 1, 1), "Top Level Author"),
                Author(
                  At(rpi, 2, 1),
                  Identifier(At(rpi, 2, 8), "Reid"),
                  LiteralString(At(rpi, 2, 23), "Reid"),
                  LiteralString(At(rpi, 2, 37), "reid@ossum.biz")
                )
              )
            )
        }
      }
      Await.result(future, 10.seconds)
    }
  }
}
