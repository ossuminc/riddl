/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.AbstractTestingBasis

class KeywordsTest extends AbstractTestingBasis {

  "Keyword" should {
    "produce all keywords" in {
      // 157, not 156: `replies` joined at 2.0, when a query's declared result stopped sharing
      // the command's `yields` keyword. Before that, 156 rather than 152 because `get`, `put`,
      // `refuses` and `require_` were declared as Keyword constants but omitted from allKeywords,
      // so anything driven by that list silently under-counted.
      Keyword.allKeywords.size must be(157)
    }
  }

  "Punctuation" should {
    "produce all punctuation marks" in {
      Punctuation.allPunctuation.size must be(17)
    }
  }

  "Readability" should {
    "produce all readability words" in {
      ReadabilityWords.allReadability.size must be(15)
    }
  }
}
