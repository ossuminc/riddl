/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

class AnalysisResultSpec extends AnyWordSpec with Matchers {

  "AnalysisToken" should {
    "generate unique tokens" in {
      val token1 = AnalysisToken.generate()
      val token2 = AnalysisToken.generate()
      token1.value must not equal token2.value
    }

    "round-trip through string conversion" in {
      val original = AnalysisToken.generate()
      val restored = AnalysisToken.fromString(original.value)
      restored.value mustEqual original.value
    }
  }

  "AnalysisMetadata" should {
    "capture analysis timestamp" in {
      val before = System.currentTimeMillis()
      val metadata = AnalysisMetadata(
        analyzedAt = System.currentTimeMillis(),
        rootDomainName = Some("TestDomain"),
        sourceLocation = None,
        sourceHash = None,
        riddlVersion = None
      )
      val after = System.currentTimeMillis()

      metadata.analyzedAt must (be >= before and be <= after)
      metadata.rootDomainName mustBe Some("TestDomain")
    }
  }
}
