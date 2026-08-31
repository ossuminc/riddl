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

/** `invariant-requires-never-applied` must see the `require invariant` that applies it.
  *
  * An invariant declared `requires <type>` cannot apply implicitly, so it is inert unless some
  * `require invariant X with <expr>` names it. The check collects the appliers by walking every
  * handler clause — and it looked each reference up in the refMap keyed on the **Handler**.
  *
  * That key is wrong. `Pass` pushes the ON-CLAUSE as the parent of the statements it contains, so
  * that is the key the resolver wrote under. The lookup therefore missed every time and the
  * invariant was reported as never applied **while `dump --json` showed the very same reference
  * resolved** — reported by riddl-models 2026-08-27, on a model that validates at zero errors.
  *
  * Diagnosed by instrumenting rather than by reading: the handler-keyed lookup returned `None`
  * while a parent-agnostic one returned the invariant, which located the defect in the KEY rather
  * than in resolution.
  *
  * **The negative case is the point of this suite.** A fix that simply stopped warning would pass
  * a test that only checks the false positive is gone, and would delete the rule. Both directions
  * are asserted here.
  */
class InvariantAppliedLookupTest extends AbstractValidatingTest {

  private def usageFindings(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.filter(_.message.contains("never applied implicitly"))

  /** A repository holding an invariant with `requires <type>`. `applied` decides whether a
    * `require invariant` statement names it, and `nested` whether that statement sits directly in
    * the clause or inside a `when` — the nested form is keyed under a parent that is neither the
    * clause nor the handler.
    */
  private def model(applied: Boolean, nested: Boolean = false): String =
    val require =
      """require invariant KindImpliesCap
        |            with record StoredRow(rowKind = PersistRow.rowKind)""".stripMargin
    val body =
      if !applied then """do "store row""""
      else if nested then s"""when "always" then
        |            $require
        |          end"""
      else s"""$require
        |          do "store row""""
    s"""domain Test is {
       |  context TestCtx is {
       |    type Kind is any of { KindA, KindB }
       |    repository TestRepo as sink is {
       |      record StoredRow is { rowKind: Kind }
       |      schema RowData is relational of rows as record StoredRow
       |        index on field StoredRow.rowKind
       |      command PersistRow is { rowKind: Kind }
       |      invariant KindImpliesCap requires record StoredRow is
       |        StoredRow.rowKind == KindA
       |      handler Hdl is {
       |        on command PersistRow is {
       |          $body
       |        }
       |      }
       |    }
       |  }
       |}""".stripMargin
  end model

  "invariant-requires-never-applied" should {

    "stay silent when a `require invariant` in the clause applies it" in { (td: TestData) =>
      usageFindings(model(applied = true), td.name) mustBe empty
    }

    // walkStatements descends into when/match/foreach, so an applier nested in one is keyed under
    // a parent that is neither the clause nor the handler. Same false positive, one level down.
    "stay silent when the applier is nested inside a `when`" in { (td: TestData) =>
      usageFindings(model(applied = true, nested = true), td.name) mustBe empty
    }

    // The control. Without this, a fix that deleted the rule would look correct.
    "still fire when nothing applies it" in { (td: TestData) =>
      val found = usageFindings(model(applied = false), td.name)
      found.size mustBe 1
      found.head.kind mustBe Messages.UsageWarning
      found.head.ruleId.map(_.code) mustBe Some("invariant-requires-never-applied")
    }
  }
}
