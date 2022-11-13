/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*

class ContextValidationTest extends ValidatingTest {

  "Context" should {
    "allow options" in {
      val input =
        """options (wrapper, service, gateway, package("foo"), technology("http"))"""
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe (empty)
          context.options.size mustBe 5
          context.options must contain(WrapperOption((2, 11, rpi)))
          context.options must contain(GatewayOption((2, 29, rpi)))
          context.options must contain(ServiceOption((2, 20, rpi)))
          context.options must contain(ContextPackageOption(
            (2, 38, rpi),
            Seq(LiteralString((2, 46, rpi), "foo"))
          ))
          context.options must contain(ContextTechnologyOption(
            (2, 54, rpi),
            Seq(LiteralString((2, 65, rpi), "http"))
          ))
      }
    }
    "allow types" in {
      val input = """type C is Current
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe (empty)
          context.types.size mustBe (1)
          context.types.head mustBe
            (Type(
              (2, 2, rpi),
              Identifier((2, 7, rpi), "C"),
              Current((2, 12, rpi))
            ))
      }
    }
    "allow functions" in {
      val input = """function bar is {
                    |  requires { i: Integer }
                    |  returns { o: Integer }
                    |}
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          info(errors.format)
          errors must be(empty)
          context.functions.size mustBe (1)
          val expected = Function(
            (2, 2, rpi),
            Identifier((2, 11, rpi), "bar"),
            input = Some(Aggregation(
              (3, 12, rpi),
              Seq(Field(
                (3, 14, rpi),
                Identifier((3, 14, rpi), "i"),
                Integer((3, 17, rpi))
              ))
            )),
            output = Some(Aggregation(
              (4, 11, rpi),
              Seq(Field(
                (4, 13, rpi),
                Identifier((4, 13, rpi), "o"),
                Integer((4, 16, rpi))
              ))
            ))
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
          info(errors.format)
          errors must be(empty)
          val expected = Entity((2, 2, rpi), Identifier((2, 9, rpi), "bar"))
          context.entities.size mustBe (1)
          context.entities.head mustBe expected
      }

    }
    "allow terms" in {
      val input = """term bar is briefly "imaginary line in court room"
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          info(errors.format)
          errors must be(empty)
          val expected = Term(
            (2, 2, rpi),
            Identifier((2, 7, rpi), "bar"),
            Some(LiteralString((2, 22, rpi), "imaginary line in court room"))
          )
          context.terms.size mustBe (1)
          context.terms.head mustBe expected
      }
    }
    "allow processors" in {
      val input = """source foo is { ??? }
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          info(errors.format)
          errors must be(empty)
          val expected = Processor(
            (2, 2, rpi),
            Identifier((2, 9, rpi), "foo"),
            Source((2, 2, rpi))
          )
          context.processors.size mustBe (1)
          context.processors.head mustBe expected
      }
    }
    "allow projections" in {
      val input = """projection foo is { ??? }
                    |""".stripMargin
      parseAndValidateContext(input) {
        case (context: Context, rpi, msgs: Messages) =>
          val errors = msgs.justErrors
          info(errors.format)
          errors must be(empty)
          val expected = Projection(
            (2, 2, rpi),
            Identifier((2, 13, rpi), "foo")
          )
          context.projections.size mustBe (1)
          context.projections.head mustBe expected
      }
    }
    "allow includes" in { pending } // TODO: write this case
  }
}
