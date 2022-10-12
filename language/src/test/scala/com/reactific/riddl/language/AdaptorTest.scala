/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Adaptor

/** Unit Tests For ConsumerTest */
class AdaptorTest extends ValidatingTest {

  "Adaptors" should {
    "handle undefined body" in {
      val input = """adaptor PaymentAdapter for context Foo is {
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
        """domain ignore is { context Foo is {
          |type ItHappened = event { abc: String described as "abc" } described as "?"
          |type LetsDoIt = command { bcd: String described as "abc" } described as "?"
          |adaptor PaymentAdapter for context Foo is {
          |  adapt sendAMessage is {
          |    from event ItHappened to command LetsDoIt as {
          |      example one is { then error "foo" } described as "eno"
          |    }
          |  } explained as "?"
          |} explained as "?"
          |} explained as "?"
          |} explained as "?"
          |""".stripMargin
      parseAndValidateDomain(input) { (_, _, messages) =>
        messages mustBe empty
      }
    }

    "allow wrapper adaptations" in {
      val input =
        """domain ignore is { context Foo is {
          |type ItWillHappen = command { abc: String described as "abc" } described as "?"
          |type LetsDoIt = command { bcd: String described as "abc" } described as "?"
          |adaptor PaymentAdapter for context Foo is {
          |  adapt sendAMessage is {
          |    from command ItWillHappen to command LetsDoIt as {
          |      example one is { then error "foo" } described as "eno"
          |    }
          |  } explained as "?"
          |} explained as "?"
          |} explained as "?"
          |} explained as "?"
          |""".stripMargin
      parseAndValidateDomain(input) { (_, _, messages) =>
        messages mustBe empty
      }
    }

  }
}
