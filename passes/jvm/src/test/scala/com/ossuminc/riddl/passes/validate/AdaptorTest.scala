/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Adaptor
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import org.scalatest.TestData

/** Unit Tests For AdaptorTest */
class AdaptorTest extends ValidatingTest {

  "Adaptors" should {
    "handle undefined body" in { (td: TestData) =>
      val input = RiddlParserInput(
        """adaptor PaymentAdapter from context Foo is {
          |  ???
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(_) => succeed
      }
    }

    "allow message actions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain ignore is { context Target is {???}  context Foo is {
          |type ItHappened = event { abc: String described as "abc" } described as "?"
          |adaptor PaymentAdapter to context Target is {
          |  handler sendAMessage is {
          |    on event ItHappened {
          |      error "foo"
          |    } described as "?"
          |  } explained as "?"
          |} explained as "?"
          |} explained as "?"
          |} explained as "?"
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input) { (_, _, messages) =>
        messages.isOnlyIgnorable mustBe true
      }
    }

    "allow wrapper adaptations" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain ignore is {
          | context Target is {???}
          | context Foo is {
          |  type ItWillHappen = command { abc: String described as "abc" } described as "?"
          |  command  LetsDoIt is { bcd: String described as "abc" } described as "?"
          |  entity MyEntity is {
          |    inlet commands is command LetsDoIt
          |  }
          |  connector only is {
          |    from outlet Foo.PaymentAdapter.forMyEntity
          |    to inlet Foo.MyEntity.commands
          |  }
          |  adaptor PaymentAdapter to context Target is {
          |    outlet forMyEntity is command LetsDoIt
          |    handler sendAMessage is {
          |      on command ItWillHappen  {
          |        send command Foo.LetsDoIt to outlet forMyEntity
          |      } described as "?"
          |    } explained as "?"
          |  } explained as "?"
          | } explained as "?"
          |} explained as "?"
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input) { (_, _, messages) =>
        succeed
      }
    }
  }
}
