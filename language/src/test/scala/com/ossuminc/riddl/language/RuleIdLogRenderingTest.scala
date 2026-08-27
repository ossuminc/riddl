/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.{AbstractTestingBasis, CallBackLogger, pc}

/** The rule id is rendered by the LOGGER, beside the kind prefix it already supplies.
  *
  * **This suite is SHARED again as of [1.18].** It lived in `scala-jvm-native` for a day because
  * `withLogger` could not capture on Scala.js: `DOMPlatformContext` overrode `def log` to return a
  * FRESH `SysLogger()` on every call, so the logger `withLogger` swapped into the `logger` field
  * was never consulted. The override also silently zeroed every per-instance message counter, and
  * it returned exactly what the base field is already initialised to -- so deleting it restored
  * `withLogger` and the counters while changing nothing about default behaviour.
  *
  * Running here on all three rows is the point: the defect was a PLATFORM difference, and a suite
  * that skips the platform it differs on cannot see it come back.
  *
  * The platform-independent half -- that the id stays OUT of `Message.format`, where
  * `CheckMessagesTest` compares its goldens -- is asserted in the shared `RuleIdTest`.
  */
class RuleIdLogRenderingTest extends AbstractTestingBasis {

  private def logged(msgs: List[Messages.Message], showIds: Boolean): String =
    val sb = new StringBuilder
    pc.withOptions(pc.options.copy(showMessageIds = showIds)) { _ =>
      pc.withLogger(CallBackLogger((_, m) => sb.append(m).append('\n'))) { _ =>
        Messages.logMessages(msgs)
      }
    }
    sb.toString

  "the logger" should {

    "render the code rustc-style" in {
      // `[error] [field-duplicate-name] ...`, the shape `error[E0433]:` has.
      val m = Messages.Message(At.empty, "something is wrong", Messages.Error,
        ruleId = Some(RuleId.FieldDuplicateName))
      logged(List(m), showIds = true) must include("[field-duplicate-name]")
    }

    "print no id at all under --no-msg-ids" in {
      // The flag's contract is that output is EXACTLY what it was before rule ids existed, so this
      // asserts the ABSENCE of the code rather than a different arrangement of it.
      val m = Messages.Message(At.empty, "something is wrong", Messages.Error,
        ruleId = Some(RuleId.FieldDuplicateName))
      val out = logged(List(m), showIds = false)
      out mustNot include("field-duplicate-name")
      out must include("something is wrong")
    }

    "add nothing for a message that has no rule" in {
      val m = Messages.Message(At.empty, "something is wrong", Messages.Error)
      logged(List(m), showIds = true) mustNot include("[")
    }
  }
}
