/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.{AbstractTestingBasis, CallBackLogger, CommonOptions, pc}

/** The `Advisory` kind (Reid's ruling, 2026-09-12, on riddl-generator's proposal).
  *
  * An advisory reports a structural fact that is CONSISTENT with the model as written and
  * INCONSISTENT with what such a declaration usually means. It never changes generability and
  * never names a fix as required; a modeller may dismiss it by design. Severity 1, tied with
  * Style -- generable, ignorable, not actionable -- and NOT a warning: its own `showAdvisories`
  * switch, independent of `showWarnings`, defaulting on. This pins each of those claims, because
  * the kind exists precisely so `adaptor-direction-advisory` stops blocking `gen` -- a wrong
  * severity or a wrong gate would silently recreate that.
  */
class AdvisoryKindTest extends AbstractTestingBasis {

  private val advisory =
    Message(At.empty, "you may well be right", Advisory, ruleId = Some(RuleId.AdaptorDirectionAdvisory))

  private def accumulate(options: CommonOptions)(msgs: Message*): Messages =
    pc.withOptions(options) { _ =>
      // NOT `Accumulator.empty`: that is a shared mutable singleton, and a message added in one
      // case would be found by the next.
      val acc = new Messages.Accumulator()
      msgs.foreach(acc.add)
      acc.toMessages
    }

  private def logged(msgs: List[Message]): String =
    val sb = new StringBuilder
    // The callback gets the LEVEL apart from the text; the `[advisory]` prefix is the level.
    pc.withLogger(CallBackLogger((lvl, m) => sb.append(s"[$lvl] ").append(m).append('\n'))) { _ =>
      Messages.logMessages(msgs)
    }
    sb.toString

  "an Advisory" should {

    "be generable, ignorable and not actionable -- severity 1, tied with Style" in {
      Advisory.severity mustBe StyleWarning.severity
      Advisory.isGenerable mustBe true
      Advisory.isActionable mustBe false
      Advisory.isIgnorable mustBe true
      List(advisory).isGenerable mustBe true
    }

    "NOT be a warning, so it is neither counted as one nor gated by showWarnings" in {
      Advisory.isWarning mustBe false
      Advisory.isAdvisory mustBe true
      advisory.isAdvisory mustBe true
      // `-w false` silences warnings; an advisory is not one and stays.
      accumulate(CommonOptions.noWarnings)(advisory).justAdvisories.size mustBe 1
    }

    "be dropped by the accumulator under --show-advisories false, and only then" in {
      accumulate(CommonOptions(showAdvisories = false))(advisory).justAdvisories mustBe empty
      accumulate(CommonOptions.default)(advisory).justAdvisories.size mustBe 1
    }

    "render with an [advisory] prefix and its rule id" in {
      val out = pc.withOptions(CommonOptions(noANSIMessages = true)) { _ => logged(List(advisory)) }
      out must include("[advisory]")
      out must include("[adaptor-direction-advisory]")
    }
  }
}
