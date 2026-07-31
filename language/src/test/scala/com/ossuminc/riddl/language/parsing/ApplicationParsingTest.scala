/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.*
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

abstract class ApplicationParsingTest(using PlatformContext) extends AbstractParsingTest {

  "Application Components" must {
    "support nested empty definitions that fail" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo {
          |context foo2 {
          |  group g1 is { ??? }
          |  group g2 is {
          |    group g3 is { ??? }
          |    input i1 acquires String is { ??? }
          |    output o1 displays String is { ??? }
          |  }
          |}
          |}""".stripMargin,
        td
      )
      parseDefinition[Domain](input) match {
        case Left(messages: Messages) =>
          fail(messages.format)
        case Right((dom: Domain, _)) =>
          succeed
      }
    }
    "supports using the prompt statement" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo {
          |context foo2 {
          |  command GoHome {???} with { briefly as "Directive to navigate to the home page" }
          |  handler foo3 is {
          |    on command GoHome {
          |      do "navigate to home page"
          |    }
          |  }
          |  group g2 is { ??? }
          |}
          |}""".stripMargin,
        td
      )
      parseDefinition[Domain](input) match {
        case Left(messages: Messages) =>
          fail(messages.format)
        case Right((dom: Domain, _)) =>
          succeed
      }
    }
    "support selection and entry input verbs (A44)" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo {
          |context foo2 {
          |  page picker is {
          |    picklist favColor selects String is { ??? }
          |    selector aChoice chooses String is { ??? }
          |    item pick3 picks String is { ??? }
          |    input amount enters String is { ??? }
          |    text given provides String is { ??? }
          |    button classic acquires String is { ??? }
          |  }
          |}
          |}""".stripMargin,
        td
      )
      parseDefinition[Domain](input) match {
        case Left(messages: Messages) =>
          fail(messages.format)
        case Right((dom: Domain, _)) =>
          val ctx = dom.contexts.head
          val group = ctx.groups.head
          val inputs = group.contents.toSeq.collect { case i: Input => i }
          inputs.map(_.verbAlias) must contain theSameElementsAs Seq(
            "selects",
            "chooses",
            "picks",
            "enters",
            "provides",
            "acquires"
          )
      }
    }
    "accepts the imperative `activate` alongside `activates`" in { (td: TestData) =>
      // `button Checkout activate Confirmation` is the reading authors reach for when the
      // input is a button, and it used to be a bare parse error at the verb. `activate` is the
      // ONE imperative in an otherwise third-person list -- deliberately, so the neighbours'
      // imperatives staying rejected is asserted below rather than left to assumption.
      val input = RiddlParserInput(
        """
          |domain foo {
          |context foo2 {
          |  page p is {
          |    button confirm activate String is { ??? }
          |    button legacy activates String is { ??? }
          |  }
          |}
          |}""".stripMargin,
        td
      )
      parseDefinition[Domain](input) match {
        case Left(messages: Messages) => fail(messages.format)
        case Right((dom: Domain, _)) =>
          val inputs = dom.contexts.head.groups.head.contents.toSeq.collect { case i: Input => i }
          inputs.map(_.verbAlias) must contain theSameElementsAs Seq("activate", "activates")
      }
    }

    "still rejects the imperative forms of the neighbouring verbs" in { (td: TestData) =>
      // Scope guard. `activate` was added alone; pairing the whole vocabulary would double what
      // a reader must recognise. If someone later adds `trigger` and friends, this fails and
      // they must decide deliberately rather than drift into it.
      Seq("trigger", "start", "submit", "select").foreach { verb =>
        val input = RiddlParserInput(
          s"""
             |domain foo {
             |context foo2 {
             |  page p is {
             |    button b $verb String is { ??? }
             |  }
             |}
             |}""".stripMargin,
          td
        )
        parseDefinition[Domain](input) match {
          case Left(_)  => succeed
          case Right(_) => fail(s"'$verb' parsed as an acquisition verb but should not")
        }
      }
      succeed
    }

    "supports 'shown by' in groups" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo {
          |  context ignore {
          |    group Mickey  is {
          |      shown by { https://pngimg.com/uploads/mickey_mouse/mickey_mouse_PNG54.png }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Domain](input) match
        case Left(messages: Messages) =>
          fail(messages.format)
        case Right((dom: Domain, _)) =>
          succeed
      end match
    }
  }
}
