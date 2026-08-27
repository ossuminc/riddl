/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.AbstractTestingBasis

/** Unit Tests For UIVerbs classification (A44 selection verbs + A46 presentation verbs). */
class UIVerbsTest extends AbstractTestingBasis {

  "UIVerbs" should {
    "classify selection verbs" in {
      UIVerbs.selectionVerbs mustBe Set("selects", "chooses", "picks")
      UIVerbs.selectionVerbs.foreach { v =>
        UIVerbs.isSelectionVerb(v) mustBe true
        UIVerbs.verbCategory(v) mustBe "selection"
      }
    }

    "classify presentation verbs (A46)" in {
      UIVerbs.presentationVerbs mustBe Set("presents", "shows", "displays", "writes", "emits")
      UIVerbs.presentationVerbs.foreach { v =>
        UIVerbs.isPresentationVerb(v) mustBe true
        UIVerbs.verbCategory(v) mustBe "presentation"
      }
    }

    "classify everything else as acquisition" in {
      UIVerbs.verbCategory("acquires") mustBe "acquisition"
      UIVerbs.isSelectionVerb("acquires") mustBe false
      UIVerbs.isPresentationVerb("acquires") mustBe false
    }
  }
}
