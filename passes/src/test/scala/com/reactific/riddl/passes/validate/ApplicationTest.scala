/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.parsing.RiddlParserInput

class ApplicationTest extends ValidatingTest {

  "Application" should {
    "parse a simple case " in {
      val rpi = RiddlParserInput(
        """domain foo is {
          |  application Test is {
          |    option is technology("react.js")
          |    result Title { content: String }
          |    command Name { content: String }
          |    group Together is {
          |      output One is {
          |        presents result Title
          |      } described as "Show a blank page with title"
          |      input Two is {
          |        acquires command Name
          |      } described as "yield  a Name"
          |    } described as "Show a title, collect a Name"
          |  } described as "A very simple app just for testing"
          |} described as "Just a parsing convenience"
          |""".stripMargin
      )
      parseAndValidateDomain(rpi) {
        case (
              domain: Domain,
              _: RiddlParserInput,
              messages: Messages.Messages
            ) =>
          domain.applications mustNot be(empty)
          domain.applications.head.types.size mustBe (2)
          messages.isOnlyIgnorable mustBe true
      }
    }
  }
}
