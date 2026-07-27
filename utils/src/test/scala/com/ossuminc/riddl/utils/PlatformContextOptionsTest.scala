/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Tests for PlatformContext.withOptions option-restoration semantics. */
class PlatformContextOptionsTest extends AnyWordSpec with Matchers {

  "PlatformContext.withOptions" should {

    "restore the previous options after a successful body" in {
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

    "restore the previous options even when the body throws" in {
      val prior = pc.options
      val temporary = prior.copy(showStyleWarnings = !prior.showStyleWarnings)
      an[IllegalStateException] must be thrownBy {
        pc.withOptions(temporary) { _ =>
          throw new IllegalStateException("boom")
        }
      }
      // The throwing body must not poison the global options for later suites
      pc.options mustBe prior
    }
  }
}
