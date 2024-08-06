/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.CommandPlugin
import com.ossuminc.riddl.language.parsing.ParsingTest

/** Unit Tests For RegressionTests */
class RegressionTests extends ParsingTest {

  val regressionsFolder = "commands/src/test/input/regressions/"
  val output = "commands/target/regressions/"

  "Regressions" should {
    "not produce a MatchError" in {
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
      CommandPlugin.runMainForTest(args) match {
        case Left(messages) => fail(messages.format)
        case Right(pr)      => succeed
      }
    }
  }
}
