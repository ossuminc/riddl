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
            At(rpi, 17, 96),
            Identifier(At(rpi, 22, 30), "Vikings"),
            Enumeration(
              At(rpi, 32, 96),
              Contents(
                Enumerator(At(rpi, 43, 50), Identifier(At(rpi, 43, 50), "Ragnar"), None),
                Enumerator(At(rpi, 50, 59), Identifier(At(rpi, 50, 59), "Lagertha"), None),
                Enumerator(At(rpi, 59, 65), Identifier(At(rpi, 59, 65), "Bjorn"), None),
                Enumerator(At(rpi, 65, 71), Identifier(At(rpi, 65, 71), "Floki"), None),
                Enumerator(At(rpi, 71, 77), Identifier(At(rpi, 71, 77), "Rollo"), None),
                Enumerator(At(rpi, 77, 82), Identifier(At(rpi, 77, 82), "Ivar"), None),
                Enumerator(At(rpi, 82, 89), Identifier(At(rpi, 82, 89), "Aslaug"), None),
                Enumerator(At(rpi, 89, 94), Identifier(At(rpi, 89, 94), "Ubbe"), None)
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
            At(rpi, 0, 161),
            Identifier(At(rpi, 7, 17), "Hamburger"),
            Contents(
              OptionValue(At(rpi, 24, 43), "transient", Seq.empty),
              OptionValue(At(rpi, 43, 62), "aggregate", Seq.empty),
              Type(
                At(rpi, 62, 90),
                Identifier(At(rpi, 67, 71), "Foo"),
                Aggregation(
                  At(rpi, 74, 90),
                  Contents(Field(At(rpi, 76, 86), Identifier(At(rpi, 76, 77), "x"), String_(At(rpi, 79, 86))))
                )
              ),
              State(
                At(rpi, 90, 131),
                Identifier(At(rpi, 96, 108), "BurgerState"),
                TypeRef(At(rpi, 111, 131), "type", PathIdentifier(At(rpi, 116, 131), List("BurgerStruct")))
              ),
              Handler(At(rpi, 131, 159), Identifier(At(rpi, 139, 153), "BurgerHandler"))
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
            At(rpi, 0, 44),
            Identifier(At(rpi, 8, 13), "fuzz"),
            InboundAdaptor(At(rpi, 13, 18)),
            ContextRef(
              At(rpi, 18, 34),
              PathIdentifier(At(rpi, 26, 34), Seq("foo", "bar"))
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
              val firstExpected =
                Field(At(rpi, 32, 43), Identifier(At(32, 34, rpi), "b"), Bool(At(36, 43, rpi)), Contents.empty)
              firstAggrContents.head must be(firstExpected)
              val secondExpected =
                Field(At(57, 68, rpi), Identifier(At(57, 59, rpi), "i"), Integer(At(61, 68, rpi)), Contents.empty)
              secondAggrContents.head must be(secondExpected)
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
          typ.typEx mustBe Replica(At(rpi, 49, 70), Integer(At(rpi, 60, 70)))
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
                LineComment(At(rpi, 0, 20), "Top Level Author"),
                Author(
                  At(rpi, 20, 77),
                  Identifier(At(rpi, 27, 32), "Reid"),
                  LiteralString(At(rpi, 43, 49), "Reid"),
                  LiteralString(At(rpi, 57, 73), "reid@ossum.biz")
                )
              )
            )
        }
      }
      Await.result(future, 10.seconds)
    }
  }
}
