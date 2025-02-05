/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.parsing.ParsingTest
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** Unit Tests For RegressionTests */
class RegressionTests extends ParsingTest {

  val regressionsFolder = "commands/input/regressions/"
  val output = "commands/shared/target/regressions/"

  "Regressions" should {
    "not produce a MatchError" in { (td: TestData) =>
      val source = "match-error.riddl"
      val args = Array(
        "hugo",
        "-o",
        output + "/match-error",
        "--with-statistics=true",
        "--with-glossary=true",
        "--with-todo-list=true",
        regressionsFolder + source
      )
      Commands.runMainForTest(args) match {
        case Left(messages) => fail(messages.format)
        case Right(pr) => succeed
      }
    }
  }
}
