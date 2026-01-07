/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Adaptor
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

trait SharedAdaptorTest(using PlatformContext) extends AbstractValidatingTest {

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
        """domain ignore is {
          |  context Target is {???}
          |  context Foo is {
          |    type ItHappened = event { abc: String } with { described as "abc" }
          |    adaptor PaymentAdapter to context Target is {
          |      handler sendAMessage is {
          |        on event ItHappened {
          |          error "foo"
          |        } with { described as "?" }
          |      } with { explained as "?" }
          |    } with {
          |      explained as "?"
          |    }
          |  } with {
          |    explained as "?"
          |  }
          |} with {
          |  explained as "?"
          |}
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
          |  type ItWillHappen = command { abc: String } with { described as "abc" }
          |  command  LetsDoIt is { bcd: String with { described as "abc" } } with { described as "?" }
          |
          |  entity MyEntity is {
          |    sink phum is { inlet commands is command LetsDoIt }
          |  }
          |  connector only is {
          |    from outlet Foo.PaymentAdapter.foo.forMyEntity
          |    to inlet Foo.MyEntity.phum.commands
          |  }
          |  adaptor PaymentAdapter to context Target is {
          |    source foo is { outlet forMyEntity is command LetsDoIt }
          |    handler sendAMessage is {
          |      on command ItWillHappen  {
          |        send command Foo.LetsDoIt to outlet forMyEntity
          |      } with { described as "?" }
          |    } with { explained as "?" }
          |  } with { explained as "?" }
          | } with {
          |  explained as "?"
          | }
          |} with { explained as "?" }
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input) { (domain, _, messages) =>
        domain.isEmpty must be(false)
        domain.contexts(1).adaptors.head.id.value must be("PaymentAdapter")
      }
    }
  }
}
