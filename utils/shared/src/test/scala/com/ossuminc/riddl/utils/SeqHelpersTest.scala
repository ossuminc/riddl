/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

/** Test suite for SeqHelpers extension methods
  *
  * Tests all edge cases including: - Empty sequences - Single element sequences - No matches - All matches - Boundary
  * conditions - Large sequences (performance)
  */
class SeqHelpersTest extends AnyWordSpec with Matchers {

  import SeqHelpers.*

  "SeqHelpers.dropUntil" should {

    "return empty sequence when input is empty" in {
      Seq.empty[Int].dropUntil(_ == 5) mustBe Seq.empty[Int]
    }

    "return empty sequence when no element matches" in {
      Seq(1, 2, 3, 4).dropUntil(_ == 10) mustBe Seq.empty[Int]
    }

    "return sequence from first match onwards" in {
      Seq(1, 2, 3, 4, 5).dropUntil(_ == 3) mustBe Seq(3, 4, 5)
    }

    "return entire sequence when first element matches" in {
      Seq(1, 2, 3, 4, 5).dropUntil(_ == 1) mustBe Seq(1, 2, 3, 4, 5)
    }

    "return single element when last element matches" in {
      Seq(1, 2, 3, 4, 5).dropUntil(_ == 5) mustBe Seq(5)
    }

    "work with single element sequence that matches" in {
      Seq(42).dropUntil(_ == 42) mustBe Seq(42)
    }

    "work with single element sequence that doesn't match" in {
      Seq(42).dropUntil(_ == 10) mustBe Seq.empty[Int]
    }

    "handle predicates that match multiple elements" in {
      Seq(1, 2, 3, 4, 5, 6).dropUntil(_ > 3) mustBe Seq(4, 5, 6)
    }

    "work with strings" in {
      Seq("a", "b", "c", "d").dropUntil(_ == "c") mustBe Seq("c", "d")
    }

    "work with complex predicates" in {
      val seq = Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      seq.dropUntil(x => x > 5 && x % 2 == 0) mustBe Seq(6, 7, 8, 9, 10)
    }

    "handle large sequences efficiently" in {
      val largeSeq = (1 to 10000).toSeq
      val result = largeSeq.dropUntil(_ == 5000)
      result.size mustBe 5001
      result.head mustBe 5000
      result.last mustBe 10000
    }
  }

  "SeqHelpers.dropBefore" should {

    "return empty sequence when input is empty" in {
      Seq.empty[Int].dropBefore(_ == 5) mustBe Seq.empty[Int]
    }

    "return empty sequence when no element matches" in {
      Seq(1, 2, 3, 4).dropBefore(_ == 10) mustBe Seq.empty[Int]
    }

    "return sequence from element before match" in {
      Seq(1, 2, 3, 4, 5).dropBefore(_ == 3) mustBe Seq(2, 3, 4, 5)
    }

    "return entire sequence when first element matches (no element before)" in {
      Seq(1, 2, 3, 4, 5).dropBefore(_ == 1) mustBe Seq(1, 2, 3, 4, 5)
    }

    "return last two elements when last element matches" in {
      Seq(1, 2, 3, 4, 5).dropBefore(_ == 5) mustBe Seq(4, 5)
    }

    "work with single element sequence" in {
      Seq(42).dropBefore(_ == 42) mustBe Seq(42)
    }

    "return element before first match for multiple matches" in {
      Seq(1, 2, 3, 4, 5, 6).dropBefore(_ > 3) mustBe Seq(3, 4, 5, 6)
    }

    "work with strings" in {
      Seq("a", "b", "c", "d").dropBefore(_ == "c") mustBe Seq("b", "c", "d")
    }

    "handle two-element sequence" in {
      Seq(1, 2).dropBefore(_ == 2) mustBe Seq(1, 2)
      Seq(1, 2).dropBefore(_ == 1) mustBe Seq(1, 2)
    }
  }

  "SeqHelpers.allUnique" should {

    "return true for empty sequence" in {
      Seq.empty[Int].allUnique mustBe true
    }

    "return true for single element sequence" in {
      Seq(42).allUnique mustBe true
    }

    "return true when all elements are unique" in {
      Seq(1, 2, 3, 4, 5).allUnique mustBe true
    }

    "return false when there are duplicates" in {
      Seq(1, 2, 3, 2, 4).allUnique mustBe false
    }

    "return false for all identical elements" in {
      Seq(1, 1, 1, 1).allUnique mustBe false
    }

    "return false for duplicate at end" in {
      Seq(1, 2, 3, 4, 5, 1).allUnique mustBe false
    }

    "return false for consecutive duplicates" in {
      Seq(1, 2, 2, 3, 4).allUnique mustBe false
    }

    "work with strings" in {
      Seq("a", "b", "c").allUnique mustBe true
      Seq("a", "b", "a").allUnique mustBe false
    }

    "work with large sequences of unique elements" in {
      val largeSeq = (1 to 10000).toSeq
      largeSeq.allUnique mustBe true
    }

    "work with large sequences with duplicates" in {
      val largeSeq = (1 to 10000).toSeq ++ Seq(5000)
      largeSeq.allUnique mustBe false
    }

    "handle case class equality" in {
      case class Person(name: String, age: Int)
      Seq(Person("Alice", 30), Person("Bob", 25)).allUnique mustBe true
      Seq(Person("Alice", 30), Person("Alice", 30)).allUnique mustBe false
    }
  }

  "SeqHelpers.popUntil" should {

    "clear empty stack" in {
      val stack = mutable.Stack.empty[Int]
      stack.popUntil(_ == 5)
      stack.isEmpty mustBe true
    }

    "clear stack when no element matches" in {
      val stack = mutable.Stack(1, 2, 3, 4)
      stack.popUntil(_ == 10)
      stack.isEmpty mustBe true
    }

    "pop elements until predicate matches" in {
      val stack = mutable.Stack(1, 2, 3, 4, 5)
      stack.popUntil(_ == 3)
      stack.toSeq mustBe Seq(3, 4, 5) // 1 is top, so pops 1,2 to reach 3
    }

    "leave stack unchanged when top element matches" in {
      val stack = mutable.Stack(1, 2, 3, 4, 5)
      stack.popUntil(_ == 1)
      stack.toSeq mustBe Seq(1, 2, 3, 4, 5) // 1 is on top, matches immediately
    }

    "pop all but bottom element when bottom matches" in {
      val stack = mutable.Stack(1, 2, 3, 4, 5)
      stack.popUntil(_ == 5)
      stack.toSeq mustBe Seq(5) // 5 is at bottom, pops 1,2,3,4
    }

    "work with single element stack that matches" in {
      val stack = mutable.Stack(42)
      stack.popUntil(_ == 42)
      stack.toSeq mustBe Seq(42)
    }

    "clear single element stack that doesn't match" in {
      val stack = mutable.Stack(42)
      stack.popUntil(_ == 10)
      stack.isEmpty mustBe true
    }

    "work with strings" in {
      val stack = mutable.Stack("a", "b", "c", "d")
      stack.popUntil(_ == "b")
      stack.toSeq mustBe Seq("b", "c", "d") // "a" is top, pops "a" to reach "b"
    }

    "handle complex predicates" in {
      val stack = mutable.Stack(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      stack.popUntil(x => x <= 5 && x % 2 == 1)
      // 1 is on top. Check: 1 <= 5 && 1 % 2 == 1 → true, match immediately
      stack.top mustBe 1
    }

    "return the same stack instance" in {
      val stack = mutable.Stack(1, 2, 3)
      val result = stack.popUntil(_ == 2)
      result must be theSameInstanceAs stack
    }
  }
}
