/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.*

/** A `reply` is an executable statement, exactly as a `yield` is.
  *
  * The 2.0 yield/reply split gave a query's result its own node, and THREE matches that named
  * `YieldStatement` were not updated with it. None is a sealed match, so `-Werror` said nothing —
  * each simply fell through to its default and did less than it should:
  *
  *   - `classifyHandlers` counted a `reply` as neither executable nor prompt, so a handler doing
  *     real work was reported as empty or prompt-only;
  *   - `valueReferencedDefs` did not see a `reply`'s message ref, hiding it from cross-context
  *     reference checking;
  *   - `countValueFailPoints` did not count a `reply`'s operand as an A12 failure point.
  *
  * riddl-models found the first by migrating 406 `yield result` sites to `reply result` and
  * watching warnings go 96 -> 123 with no behavioural change. All 27 new warnings were this bug, in
  * the two flavours the classifier's arithmetic predicts. They suggested grepping for siblings;
  * that is how the other two were found.
  *
  * The shapes below are riddl-models' two repro cases, reduced.
  */
class ReplyIsExecutableTest extends AbstractValidatingTest {

  private def messagesFor(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, msgs) =>
        captured = msgs
        succeed
      }
    }
    captured

  private def textFor(src: String, origin: String): String =
    messagesFor(src, origin).map(_.message).mkString("\n")

  /** A context whose handler's clauses are parameterised — the point being a handler with NO
    * command clause to carry the executable count for it.
    */
  private def model(clauseBody: String): String =
    s"""domain D is {
       |  context C is {
       |    result FillResult is { ok: Boolean } with { briefly "r" }
       |    query Fills replies result D.C.FillResult is { id: Integer } with { briefly "q" }
       |    record R is { total: Integer } with { briefly "rc" }
       |    entity E is {
       |      state S of record D.C.R is {
       |        handler CounselingQueries is {
       |          on query D.C.Fills is { $clauseBody }
       |        } with { briefly "h" }
       |      } with { briefly "st" }
       |    } with { briefly "en" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private val noExecutable = "has no executable statements"
  private val promptOnly = "contains only 'do' statements"

  "a handler whose only statement is a `reply`" should {
    "classify as Executable, not Empty" in { (td: TestData) =>
      // riddl-models' CounselingQueries shape: bare `reply result X`, no `do`.
      textFor(model("reply result D.C.FillResult"), td.name) mustNot include(noExecutable)
    }
  }

  "a handler with `do` plus `reply`" should {
    "classify as Executable, not PromptOnly" in { (td: TestData) =>
      // riddl-models' PlayerPersistence shape.
      textFor(
        model("""do "look it up" reply result D.C.FillResult"""),
        td.name
      ) mustNot include(promptOnly)
    }
  }

  "the classifier's controls" should {

    "still report a handler that is genuinely prompt-only" in { (td: TestData) =>
      // Without this, "does not include promptOnly" would pass by the message never firing.
      textFor(model("""do "just describe it" """), td.name) must include(promptOnly)
    }

    "leave `yield event` handlers unaffected" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    event Paid is { v: Integer } with { briefly "e" }
          |    command Pay yields event D.C.Paid is { p: Integer } with { briefly "cm" }
          |    record R is { total: Integer } with { briefly "rc" }
          |    entity E is {
          |      state S of record D.C.R is {
          |        handler H is {
          |          on command D.C.Pay is { yield event D.C.Paid }
          |        } with { briefly "h" }
          |      } with { briefly "st" }
          |    } with { briefly "en" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = textFor(src, td.name)
      text mustNot include(noExecutable)
      text mustNot include(promptOnly)
    }
  }
}
