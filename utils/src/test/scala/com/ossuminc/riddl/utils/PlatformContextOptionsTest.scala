/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Tests for PlatformContext.withOptions option-restoration semantics.
  *
  * `pc.options` is shared global state and `withOptions` swaps the whole options object under
  * `pc`'s monitor, so with sbt running suites in parallel a concurrent suite's `withOptions` can
  * mutate the global between this test's capture and its assertion. Every `withOptions` (and hence
  * every option mutation) synchronizes on `pc`, so we hold `pc`'s monitor across the whole capture
  * -> act -> assert window to make these assertions deterministic. (`synchronized` is reentrant, so
  * the nested `withOptions` inside still works.)
  */
class PlatformContextOptionsTest extends AnyWordSpec with Matchers {

  "PlatformContext.withOptions" should {

    "restore the previous options after a successful body" in {
      pc.synchronized {
        val prior = pc.options
        val temporary = prior.copy(showStyleWarnings = !prior.showStyleWarnings)
        val result = pc.withOptions(temporary) { opts =>
          pc.options mustBe temporary
          opts mustBe temporary
          42
        }
        result mustBe 42
        pc.options mustBe prior
      }
    }

    "restore the previous options even when the body throws" in {
      pc.synchronized {
        val prior = pc.options
        val temporary = prior.copy(showStyleWarnings = !prior.showStyleWarnings)
        an[IllegalStateException] must be thrownBy {
          pc.withOptions(temporary) { _ =>
            throw new IllegalStateException("boom")
          }
        }
        // The throwing body must have restored the prior options (not poisoned the global).
        pc.options mustBe prior
      }
    }
  }
}
