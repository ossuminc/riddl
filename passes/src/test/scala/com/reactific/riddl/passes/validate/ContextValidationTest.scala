/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.TopLevelParser

import java.nio.file.Path

class ContextValidationTest extends ValidatingTest {

  "Context" should {
    "allow options" in {
      val input =
        """options (wrapper, service, gateway, package("foo"), technology("http")) ??? """
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe empty
          context.options.size mustBe 5
          context.options must contain(WrapperOption((2, 11, rpi)))
          context.options must contain(GatewayOption((2, 29, rpi)))
          context.options must contain(ServiceOption((2, 20, rpi)))
          context.options must contain(
            ContextPackageOption(
              (2, 38, rpi),
              Seq(LiteralString((2, 46, rpi), "foo"))
            )
          )
          context.options must contain(
            ContextTechnologyOption(
              (2, 54, rpi),
              Seq(LiteralString((2, 65, rpi), "http"))
            )
          )
      }
    }
    "allow types" in {
      val input = """type C is Current
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe empty
          context.types.size mustBe 1
          context.types.head mustBe Type(
            (2, 2, rpi),
            Identifier((2, 7, rpi), "C"),
            Current((2, 12, rpi))
          )
      }
    }
    "allow functions" in {
      val input = """function bar is {
                    |  requires { i: Integer }
                    |  returns { o: Integer }
                    |  body { ??? }
                    |}
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          // info(errors.format)
          errors mustBe empty
          context.functions.size mustBe 1
          val expected = Function(
            (2, 2, rpi),
            Identifier((2, 11, rpi), "bar"),
            input = Some(
              Aggregation(
                (3, 12, rpi),
                Seq(
                  Field(
                    (3, 14, rpi),
                    Identifier((3, 14, rpi), "i"),
                    Integer((3, 17, rpi))
                  )
                )
              )
            ),
            output = Some(
              Aggregation(
                (4, 11, rpi),
                Seq(
                  Field(
                    (4, 13, rpi),
                    Identifier((4, 13, rpi), "o"),
                    Integer((4, 16, rpi))
                  )
                )
              )
            )
          )
          context.functions.head mustBe expected
      }
    }
    "allow entities" in {
      val input = """entity bar is { ??? }
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          // info(errors.format)
          errors must be(empty)
          val expected = Entity((2, 2, rpi), Identifier((2, 9, rpi), "bar"))
          context.entities.size mustBe 1
          context.entities.head mustBe expected
      }

    }
    "allow terms" in {
      val input = """term bar is briefly "imaginary line in court room"
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          // info(errors.format)
          errors must be(empty)
          val expected = Term(
            (2, 2, rpi),
            Identifier((2, 7, rpi), "bar"),
            Some(LiteralString((2, 22, rpi), "imaginary line in court room"))
          )
          context.terms.size mustBe 1
          context.terms.head mustBe expected
      }
    }
    "allow processors" in {
      val input = """source foo is { ??? }
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          // info(errors.format)
          errors must be(empty)
          val expected = Streamlet(
            (2, 2, rpi),
            Identifier((2, 9, rpi), "foo"),
            Source((2, 2, rpi))
          )
          context.streamlets.size mustBe 1
          context.streamlets.head mustBe expected
      }
    }
    "allow projectors" in {
      val input = """projector foo is {
                    |  record one is { ??? }
                    |  handler one is { ??? }
                    |}
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          // info(errors.format)
          errors must be(empty)
          context.projectors.size mustBe 1
          val actual = context.projectors.head
          val expected =
            Projector(
              (2, 2, rpi),
              Identifier((2, 12, rpi), "foo"),
              List(),
              List(),
              List(),
              List(
                Type(
                  (3, 3, rpi),
                  Identifier((3, 10, rpi), "one"),
                  AggregateUseCaseTypeExpression(
                    (3, 17, rpi),
                    RecordCase,
                    List()
                  ),
                  None,
                  None
                )
              ),
              List.empty[Constant],
              List.empty[Inlet],
              List.empty[Outlet],
              List(
                Handler(
                  (4, 11, rpi),
                  Identifier((4, 11, rpi), "one"),
                  List(),
                  List(),
                  None,
                  None
                )
              ),
              List(),
              List(),
              List(),
              None,
              None
            )
          actual mustBe expected
      }
    }

    "allow includes" in {
      val name = "language/src/test/input/context/context-with-include.riddl"
      val path = Path.of(name)
      TopLevelParser.parse(path) match {
        case Left(errors) => fail(errors.format)
        case Right(root) =>
          root mustNot be(empty)
          root.contents mustNot be(empty)
          val d = root.domains.head
          d.contexts mustNot be(empty)
          val c = d.contexts.head
          c.includes mustNot be(empty)
          val inc = c.includes.head
          inc.contents.head match {
            case t: Term => t.format contains "foo"
            case _       => fail("test case should have term 'foo'")
          }
      }
    }
  }
}
