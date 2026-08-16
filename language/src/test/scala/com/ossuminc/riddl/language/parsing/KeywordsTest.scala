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
      // 163 at 2.0: `self` joined -- the running processor instance (`self`/`self.<field>`). It
      // is NOT a definitionKeyword (a `self` field/identifier stays legal), only a value-position
      // keyword tried before `valueRef`.
      //
      // 162 before that: A70 added `correlation` plus the three particles of its mandatory `times
      // out after` clause. Only `correlation` is a definitionKeyword -- `times`, `out` and `after`
      // are ordinary English that a model may still use as identifiers, and they are listed here
      // only so the tokenizer colours them.
      //
      // 158 before that: `replies` joined when a query's declared result stopped sharing the
      // command's `yields` keyword, and `ask` joined with the statement that correlates a query
      // with its reply. Before those, 156 rather than 152 because `get`, `put`, `refuses` and
      // `require_` were declared as Keyword constants but omitted from allKeywords, so anything
      // driven by that list silently under-counted.
      Keyword.allKeywords.size must be(163)
    }
  }

  "Punctuation" should {
    "produce all punctuation marks" in {
      Punctuation.allPunctuation.size must be(18)
    }
  }

  "Readability" should {
    "produce all readability words" in {
      ReadabilityWords.allReadability.size must be(15)
    }
  }
}
