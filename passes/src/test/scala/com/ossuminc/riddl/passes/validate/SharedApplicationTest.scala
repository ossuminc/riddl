/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, Messages, *}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import org.scalatest.TestData

trait SharedApplicationTest extends AbstractValidatingTest {

  "Application" should {
    "parse a simple case " in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain foo is {
          |  application context Test is {
          |    result Title { content: String }
          |    command Name { content: String }
          |    group Together is {
          |      output One presents result Title with { described by "Show a blank page with title" }
          |      input Two acquires command Name with { briefly "yield  a Name" }
          |    } with {
          |     description as "Show a title, collect a Name"
          |    }
          |  } with {
          |    description as "A very simple app just for testing"
          |    option is technology("react.js")
          |  }
          |} with {
          |  described by "Just a parsing convenience"
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(rpi) {
        case (
              domain: Domain,
              _: RiddlParserInput,
              messages: Messages.Messages
            ) =>
          domain.contexts mustNot be(empty)
          domain.contexts.head.types.size mustBe (2)
          val group = domain.contexts.head.groups.head
          val outputs: Seq[Output] = group.contents.filter[Output]
          outputs must not be (empty)
          outputs.head.brief must be(empty)
          outputs.head.descriptions must not be (empty)
          messages.hasErrors mustBe false
      }
    }

    // A44: selection-verb semantics for Inputs

    val selectionWarning = "a selection verb"

    def appWith(input: String): RiddlParserInput =
      RiddlParserInput(
        s"""domain foo is {
           |  application context Shopping is {
           |    type Color is any of { Red, Green, Blue }
           |    type Palette is any of { Warm, Cool }
           |    type Choice is one of { type Color, type Palette }
           |    type Amount is Integer
           |    page picker is {
           |      $input
           |    }
           |  }
           |}
           |""".stripMargin,
        "A44 selection verbs"
      )

    "not warn when a selection verb acquires an enumeration" in { (td: TestData) =>
      parseAndValidateDomain(appWith("picklist favColor selects type Color")) {
        case (_, _, messages: Messages.Messages) =>
          messages.hasErrors mustBe false
          messages.filter(_.message.contains(selectionWarning)) mustBe empty
      }
    }

    "not warn when a selection verb acquires an alternation" in { (td: TestData) =>
      parseAndValidateDomain(appWith("selector aChoice chooses type Choice")) {
        case (_, _, messages: Messages.Messages) =>
          messages.hasErrors mustBe false
          messages.filter(_.message.contains(selectionWarning)) mustBe empty
      }
    }

    "emit a StyleWarning (not an Error) when a selection verb acquires a non-choice type" in {
      (td: TestData) =>
        parseAndValidateDomain(
          appWith("picklist favColor selects type Amount"),
          shouldFailOnErrors = false
        ) { case (_, _, messages: Messages.Messages) =>
          val warnings = messages.filter(_.message.contains(selectionWarning))
          warnings.size mustBe 1
          warnings.head.kind mustBe Messages.StyleWarning
          messages.filter(m =>
            m.kind == Messages.Error && m.message.contains(selectionWarning)
          ) mustBe empty
        }
    }

    "emit a StyleWarning when a selection verb acquires a predefined String" in { (td: TestData) =>
      parseAndValidateDomain(
        appWith("picklist favColor selects String"),
        shouldFailOnErrors = false
      ) { case (_, _, messages: Messages.Messages) =>
        val warnings = messages.filter(_.message.contains(selectionWarning))
        warnings.size mustBe 1
        warnings.head.kind mustBe Messages.StyleWarning
      }
    }

    "not warn for a non-selection verb regardless of type" in { (td: TestData) =>
      parseAndValidateDomain(appWith("input amount acquires type Amount")) {
        case (_, _, messages: Messages.Messages) =>
          messages.hasErrors mustBe false
          messages.filter(_.message.contains(selectionWarning)) mustBe empty
      }
    }
  }
}
