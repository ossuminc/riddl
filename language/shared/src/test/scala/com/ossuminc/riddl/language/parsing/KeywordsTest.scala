/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.AbstractTestingBasis

class KeywordsTest extends AbstractTestingBasis {

  "Keyword" should {
    "produce all keywords" in {
      Keyword.allKeywords.size must be(138)
    }
  }

  "Punctuation" should {
    "produce all punctuation marks" in {
      Punctuation.allPunctuation.size must be (17)
    }
  }

  "Readability" should {
    "produce all readability words" in {
      ReadabilityWords.allReadability.size must be(15)
    }
  }
}
