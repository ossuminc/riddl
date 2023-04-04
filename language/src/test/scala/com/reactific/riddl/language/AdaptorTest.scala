/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Adaptor
import com.reactific.riddl.language.passes.validation.ValidatingTest

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
        messages.isOnlyIgnorable mustBe true
      }
    }

    "allow wrapper adaptations" in {
      val input =
        """domain ignore is {
          | context Target is {???}
          | context Foo is {
          |  type ItWillHappen = command { abc: String described as "abc" } described as "?"
          |  command  LetsDoIt is { bcd: String described as "abc" } described as "?"
          |  entity MyEntity is { inlet commands is command LetsDoIt }
          |    connector only is {
          |      from outlet Foo.PaymentAdapter.forMyEntity
          |      to inlet MyEntity.commands
          |    }
          |  adaptor PaymentAdapter to context Target is {
          |    outlet forMyEntity is command LetsDoIt
          |    handler sendAMessage is {
          |      on command ItWillHappen  {
          |        example one is {
          |          then send command LetsDoIt(bcd="foo") to outlet forMyEntity
          |        } described as "eno"
          |      } described as "?"
          |    } explained as "?"
          |  } explained as "?"
          | } explained as "?"
          |} explained as "?"
          |""".stripMargin
      parseAndValidateDomain(input, shouldFailOnErrors = false) { (_, _, messages) =>
        messages.isOnlyWarnings mustBe true
      }
    }

  }
}
