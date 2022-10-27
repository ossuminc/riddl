/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Adaptor
import com.reactific.riddl.language.Messages.MissingWarning

/** Unit Tests For ConsumerTest */
class AdaptorTest extends ValidatingTest {

  "Adaptors" should {
    "handle undefined body" in {
      val input = """adaptor PaymentAdapter from context Foo is {
                    |  ???
                    |}
                    |""".stripMargin
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(_) => succeed
      }
    }

    "allow message actions" in {
      val input =
        """domain ignore is { context Target is {???}  context Foo is {
          |type ItHappened = event { abc: String described as "abc" } described as "?"
          |adaptor PaymentAdapter to context Target is {
          |  handler sendAMessage is {
          |    on event ItHappened {
          |      example one is { then error "foo" } described as "eno"
          |    } described as "?"
          |  } explained as "?"
          |} explained as "?"
          |} explained as "?"
          |} explained as "?"
          |""".stripMargin
      parseAndValidateDomain(input) { (_, _, messages) =>
        messages.filterNot(_.kind == MissingWarning) mustBe empty
      }
    }

    "allow wrapper adaptations" in {
      val input =
        """domain ignore is { context Target is {???}  context Foo is {
          |type ItWillHappen = command { abc: String described as "abc" } described as "?"
          |type LetsDoIt = command { bcd: String described as "abc" } described as "?"
          |entity MyEntity is { ??? }
          |adaptor PaymentAdapter to context Target is {
          |  handler sendAMessage is {
          |    on command ItWillHappen  {
          |      example one is { then tell command LetsDoIt(bcd="foo") to
          |        entity MyEntity
          |      } described as "eno"
          |    } described as "?"
          |  } explained as "?"
          |} explained as "?"
          |} explained as "?"
          |} explained as "?"
          |""".stripMargin
      parseAndValidateDomain(input) { (_, _, messages) =>
        messages.filterNot(_.kind == Messages.MissingWarning) mustBe empty
      }
    }

  }
}
