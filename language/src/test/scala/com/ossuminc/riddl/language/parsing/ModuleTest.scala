/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.Module
import com.ossuminc.riddl.language.{toSeq, Messages}
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

abstract class ModuleTest(using PlatformContext) extends AbstractParsingTest {

  "Module" should {
    "be accepted at root scope" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |module foo is {
          |   // this is a comment
          |   domain blah is { ??? }
          |}
          |""".stripMargin,
        td
      )
      parseTopLevelDomains(input) match
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          root.modules must not be (empty)
          root.modules.head.id.value must be("foo")
          root.modules.head.domains must not be (empty)
          root.modules.head.domains.head.id.value must be("blah")

    }

    // S61-1: a Module is a FLAT collection of ANY top-level definition. No hierarchy is
    // enforced at its top level.
    "hold a flat mix of any top-level definition" in { (td: TestData) =>
      val input = RiddlParserInput(mixedModuleSource, td)
      parseTopLevelDomains(input) match
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          val module = root.modules.headOption.getOrElse(fail("no module parsed"))
          module.id.value must be("M")
          module.types.map(_.id.value) must contain("Amount")
          module.contexts.map(_.id.value) must contain("Ordering")
          module.functions.map(_.id.value) must contain("Compute")
          module.sagas.map(_.id.value) must contain("Checkout")
          module.domains.map(_.id.value) must contain("Retail")
          module.entities.map(_.id.value) must contain("Loose")
          module.adaptors.map(_.id.value) must contain("FromOrdering")
          module.projectors.map(_.id.value) must contain("Totals")
          module.repositories.map(_.id.value) must contain("Ledger")
          module.epics.map(_.id.value) must contain("Buying")
          module.users.map(_.id.value) must contain("Shopper")
          module.constants.map(_.id.value) must contain("Limit")
          module.invariants.map(_.id.value) must contain("Positive")
          module.authors.map(_.id.value) must contain("Reid")
          module.modules.map(_.id.value) must contain("Nested")
    }

    // Root is the file parse-root, not the reuse unit: it stays narrow.
    "not widen Root: a bare entity is still rejected at root scope" in { (td: TestData) =>
      val input = RiddlParserInput("entity Loose is { ??? }\n", td)
      parseTopLevelDomains(input) match
        case Left(_)  => succeed
        case Right(_) => fail("a bare entity must not parse at Root scope")
    }
  }

  "Nebula (deprecated)" should {

    "parse an anonymous bag of definitions as a Module, with exactly one deprecation" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """constant bar is String = "nothing"
            |type foo is Integer
            |entity foobar is { ??? }
            |""".stripMargin,
          td
        )
        val tp = TestParser(input)
        tp.parseNebula match
          case Left(messages) => fail(messages.format)
          case Right(module) =>
            module.contents.toSeq.size must be(3)
            module.id.value must be(Module.syntheticId)
            Module.isSynthetic(module) must be(true)
            module.types.map(_.id.value) must contain("foo")
            module.entities.map(_.id.value) must contain("foobar")
            val deprecations = tp.accumulatedMessages.filter(_.kind == Messages.Deprecation)
            deprecations.size must be(1)
            deprecations.head.message must include("deprecated")
    }
  }

  /** Mixed contents mirroring `language/input/module/mixed-module.riddl` (kept inline so the test
    * runs on every platform, including those without file I/O).
    */
  private val mixedModuleSource: String =
    """module M is {
      |  author Reid is { name is "Reid Spencer" email is "reid@ossuminc.com" }
      |  type Amount is Number
      |  constant Limit is Number = "100"
      |  user Shopper is "a person who buys things"
      |  invariant Positive is "the limit is positive"
      |  function Compute is { ??? }
      |  context Ordering is {
      |    event Placed is { when: TimeStamp }
      |  }
      |  entity Loose is { handler Anything is { ??? } }
      |  adaptor FromOrdering from context Ordering is { ??? }
      |  projector Totals is {
      |    record Snapshot is { total: Number }
      |    handler Updates is { ??? }
      |  }
      |  repository Ledger is { ??? }
      |  saga Checkout is {
      |    step ReserveStock is {
      |      do "reserve"
      |    } reverted by {
      |      do "release"
      |    }
      |    step ChargeCard is {
      |      do "charge"
      |    } reverted by {
      |      do "refund"
      |    }
      |  }
      |  epic Buying is {
      |    user Shopper wants to "buy something" so that "they own it"
      |    type Cart is String
      |  }
      |  domain Retail is { context Store is { ??? } }
      |  module Nested is { type Inner is String }
      |}
      |""".stripMargin
}
