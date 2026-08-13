/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `on init` and `on term` become invocable, so they need parameters.
  *
  * `on term`'s leading parameter is REQUIRED to be Id(this processor): it is invoked from
  * outside, so the caller must say which instance. `on init` has no such parameter -- there is
  * no instance yet, and the identity is minted by initiating.
  */
class LifecycleParametersTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def entityWith(clauses: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is {
       |        handler H is { $clauses } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "on init parameters" should {
    "parse and validate" in { (td: TestData) =>
      diagnostics(
        entityWith("""on init(total: Integer) is { do "start" }"""), "init-params"
      ).justErrors mustBe empty
    }

    "remain optional" in { (td: TestData) =>
      diagnostics(entityWith("""on init is { do "start" }"""), "init-none")
        .justErrors mustBe empty
    }

    "REJECT a parameter naming an undefined type" in { (td: TestData) =>
      // THE case proving parameters are TRAVERSED. They are held in a FIELD, not in
      // `contents`, and Pass.traverse's generic Branch arm walks contents ONLY -- so without
      // its own traverse case this model validates clean while naming a type that need not
      // exist. Same shape as Correlation.timeoutStatements.
      val text = diagnostics(
        entityWith("""on init(x: Nonexistent) is { do "start" }"""), "init-bad-type"
      ).justErrors.map(_.message).mkString("\n")
      text must include("Nonexistent")
    }
  }

  "on term parameters" should {
    "accept a leading Id of the enclosing processor" in { (td: TestData) =>
      diagnostics(
        entityWith("""on term(oid: Id(entity Order), why: String) is { do "end" }"""),
        "term-ok"
      ).justErrors mustBe empty
    }

    "REJECT a missing leading id parameter" in { (td: TestData) =>
      val text = diagnostics(
        entityWith("""on term(why: String) is { do "end" }"""), "term-no-id"
      ).justErrors.map(_.message).mkString("\n")
      text must include("first parameter")
      text must include("Id(")
    }
  }
}
