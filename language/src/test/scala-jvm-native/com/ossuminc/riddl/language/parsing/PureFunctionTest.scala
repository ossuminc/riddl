/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.Context
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.TestData

/** A26: a Function is pure — its body may not contain effect statements (`set`/`send`/`tell`/
  * `morph`/`become`/`yield`/`reply`). These are rejected at parse time (the ban cuts at the
  * keyword), so the offending statement need not resolve.
  */
class PureFunctionTest extends AbstractParsingTest {

  private def fnWith(stmt: String): String =
    s"""context c is {
       |  function f is {
       |    requires { a: Integer }
       |    $stmt
       |  }
       |}
       |""".stripMargin

  "Pure functions" should {
    for stmt <- Seq(
        "set field a to \"1\"",
        "send command Go to inlet c.e.t.in",
        "tell command Go to entity c.e",
        "morph entity c.e to state c.e.s with record Go",
        "become entity c.e to handler c.e.h",
        "yield command Go",
        "reply command Go"
      )
    do
      val kw = stmt.takeWhile(_ != ' ')
      s"reject '$kw' in a function body" in { (td: TestData) =>
        parseDefinition[Context](RiddlParserInput(fnWith(stmt), td)) match
          case Left(errors) => errors must not(be(empty))
          case Right(_)     => fail(s"expected parse failure for a function containing: $stmt")
      }

    for stmt <- Seq(
        "set field a to \"1\"",
        "send command Go to inlet c.e.t.in",
        "morph entity c.e to state c.e.s with record Go"
      )
    do
      s"report the pure-function message for '${stmt.takeWhile(_ != ' ')}'" in { (td: TestData) =>
        parseDefinition[Context](RiddlParserInput(fnWith(stmt), td)) match
          case Left(errors) => errors.map(_.format).mkString must include("a function is pure")
          case Right(_)     => fail("expected parse failure")
      }

    for (label, stmt) <- Seq(
        "prompt" -> "prompt \"an arbitrary step\"",
        "require" -> "require \"a precondition\"",
        "let" -> "let x = \"compute something\"",
        "error" -> "error \"boom\""
      )
    do
      s"accept a pure function using '$label'" in { (td: TestData) =>
        parseDefinition[Context](RiddlParserInput(fnWith(stmt), td)) match
          case Left(errors) =>
            fail(s"pure '$label' should parse:\n" + errors.map(_.format).mkString("\n"))
          case Right(_) => succeed
      }
  }
}
