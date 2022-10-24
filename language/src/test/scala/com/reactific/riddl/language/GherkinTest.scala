/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Example

/** Unit Tests For Handler */
class GherkinTest extends ParsingTest {
  "Gherkin" should {
    "allow triviality" in {
      val input = """
                    |example Triviality is { then "nothing of consequence" }
                    |""".stripMargin
      parseDefinition[Example](input) match {
        case Left(errors) => fail(errors.format)
        case Right(_)     => succeed
      }
    }
  }
}
