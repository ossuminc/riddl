/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.parsing.RiddlParserInput

class ApplicationTest extends ValidatingTest {

  "Application" should {
    "parse a simple case " in {
      val rpi = RiddlParserInput(
        """domain foo is {
          |  application Test is {
          |    option is technology("react.js")
          |    type Title = String
          |    type Name = String
          |    display One is {
          |      presents Title
          |    } described as "Show a blank page with title"
          |    form Two is {
          |      presents Title
          |      collects Name
          |    } described as "Show a blank page with title, collect a Name"
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
          messages mustBe (empty)
      }
    }
  }
}
