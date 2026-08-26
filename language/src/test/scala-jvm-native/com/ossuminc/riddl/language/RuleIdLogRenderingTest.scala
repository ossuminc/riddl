/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.{AbstractTestingBasis, CallBackLogger, pc}

/** The rule id is rendered by the LOGGER, beside the kind prefix it already supplies.
  *
  * **This suite is `scala-jvm-native` because `withLogger` cannot capture on Scala.js.**
  * `DOMPlatformContext` overrides `def log` to return a fresh `SysLogger()` on every call
  * (`DOMPlatformContext.scala:88`), so the logger `withLogger` swaps into the `logger` field is
  * never consulted and a capture reads the empty string. The rendering itself is fine on JS -- CI
  * printed `[error] [field-duplicate-name] ...` in the very run where this assertion failed, which
  * is the tell that the INSTRUMENT was broken and not the feature. Filed as BACKLOG [1.18].
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
