/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.{AbstractTestingBasis, pc}

/** Pins the equality and hashing contract of [[RiddlParserInput]].
  *
  * `StringParserInput` is a case class whose first field is `data` — the entire text of a source
  * file — so the compiler-generated `hashCode` hashed the whole file on every call. `At` holds a
  * `RiddlParserInput`, `Identifier` and `Definition` hold an `At`, and `ReferenceMap.Key` holds a
  * `Definition`, so EVERY reference-map operation hashed a whole source file.
  *
  * That was nearly free on the JVM and Native, which memoise `String.hashCode` into the string
  * object, and catastrophic on Scala.js, where a JS string cannot carry that field and the hash is
  * recomputed character-by-character every time. Measured on a 139KB source: 14ns on the JVM, 1ns
  * on Native, **181,187ns on Scala.js** — 3402x the cost of hashing a short name, versus 1.0x on
  * the other two platforms.
  *
  * The fix memoises the hash on the parser input (one per FILE, nothing per AST node). These tests
  * pin the contract that fix must not break — they are correctness tests, not performance tests;
  * the numbers live in `DefinitionHashCostBenchmark`.
  */
class ParserInputHashingTest extends AbstractTestingBasis {

  "RiddlParserInput hashing" should {

    "give equal inputs equal hash codes" in {
      val a = RiddlParserInput("domain A is { ??? }", "purpose")
      val b = RiddlParserInput("domain A is { ??? }", "purpose")
      a mustBe b
      a.hashCode mustBe b.hashCode
    }

    "distinguish inputs with different data" in {
      val a = RiddlParserInput("domain A is { ??? }", "purpose")
      val b = RiddlParserInput("domain B is { ??? }", "purpose")
      a must not be b
    }

    "return a stable hash across repeated calls" in {
      // The memoised value must not drift; this is what makes it safe as a HashMap key.
      val a = RiddlParserInput("domain A is { ??? }", "purpose")
      val first = a.hashCode
      for _ <- 1 to 100 do a.hashCode mustBe first
      end for
    }

    "keep an input equal to itself" in {
      val a = RiddlParserInput("domain A is { ??? }", "purpose")
      a mustBe a
      a.equals(a) mustBe true
    }

    "not equal a non-input" in {
      val a = RiddlParserInput("domain A is { ??? }", "purpose")
      a.equals("domain A is { ??? }") mustBe false
    }

    "keep the empty input recognisable" in {
      // At.isEmpty compares `source == RiddlParserInput.empty`, so this identity must survive any
      // change to equals.
      RiddlParserInput.empty mustBe RiddlParserInput.empty
      At(RiddlParserInput.empty).isEmpty mustBe true
    }

    "not report a populated location as empty" in {
      val a = RiddlParserInput("domain A is { ??? }", "purpose")
      At(a, 1, 5).isEmpty mustBe false
    }
  }

  "At hashing" should {

    "give equal locations equal hash codes" in {
      val a = RiddlParserInput("domain A is { ??? }", "purpose")
      At(a, 3, 9).hashCode mustBe At(a, 3, 9).hashCode
      At(a, 3, 9) mustBe At(a, 3, 9)
    }

    "distinguish locations by offset" in {
      val a = RiddlParserInput("domain A is { ??? }", "purpose")
      At(a, 3, 9) must not be At(a, 4, 9)
    }
  }
}
