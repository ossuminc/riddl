/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.{AbstractTestingBasis, pc}

/** [1.18]: `pc.log` must return the SAME logger each call, on every platform.
  *
  * `DOMPlatformContext` overrode `def log` to return a fresh `SysLogger()` per call. That defeated
  * `withLogger` — the visible symptom — but it also silently zeroed the per-instance message
  * counters `Logger.summary` reports, because every `count()` landed on a different instance. The
  * second casualty was invisible until the first was diagnosed.
  *
  * SHARED on purpose: the defect was a platform difference, so a suite that skips the platform it
  * differs on cannot see it return.
  */
class LoggerIdentityTest extends AbstractTestingBasis {

  "pc.log" should {

    "return the same instance across calls, so state survives" in {
      // Reference identity is the whole assertion: a fresh logger per call is precisely the bug.
      assert(pc.log eq pc.log, "pc.log returned a different Logger on a second call")
    }

    "accumulate message counts rather than losing them" in {
      val before = pc.log.summary
      pc.log.error("one")
      pc.log.error("two")
      val after = pc.log.summary
      // Counting into a discarded instance leaves the summary unchanged, which is what happened
      // on JS for as long as the override stood.
      assert(before != after, s"summary did not change after logging:\n$after")
    }
  }
}
