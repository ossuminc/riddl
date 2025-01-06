/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import org.scalatest.TestData

trait SharedApplicationTest extends AbstractValidatingTest {

  "Application" should {
    "parse a simple case " in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain foo is {
          |  context Test is {
          |    option is technology("react.js")
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
          messages.isOnlyIgnorable mustBe true
      }
    }
  }
}
