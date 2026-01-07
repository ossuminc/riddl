/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.collection.mutable
import scala.scalajs.js.annotation._

@JSExportTopLevel("SeqHelpers")
object SeqHelpers {

  extension[T] (seq: Seq[T])
    def dropUntil(f: T => Boolean): Seq[T] = {
      val index = seq.indexWhere(f)
      if index < 0 then { Seq.empty[T] }
      else { seq.drop(index) }
    }

    def dropBefore(f: T => Boolean): Seq[T] = {
      val index = seq.indexWhere(f)
      if index < 0 then {Seq.empty[T]}
      else if index == 0 then {
        seq
      } else {seq.drop(index - 1)}
    }

    def allUnique: Boolean = {
      // Use capacity hint for better performance on large sequences
      // HashSet with initial capacity avoids repeated resizing/rehashing
      val initialCapacity = seq match {
        case indexed: scala.collection.IndexedSeq[?] =>
          // For indexed sequences with O(1) size, use size as hint
          // Clamp to reasonable range to avoid over-allocation for small seqs
          math.max(16, indexed.size)
        case _ =>
          // For other sequences, use default capacity (size might be expensive)
          16
      }
      val set = scala.collection.mutable.HashSet.empty[T]
      set.sizeHint(initialCapacity)

      seq.forall { x =>
        if set(x) then false
        else {
          set += x
          true
        }
      }
    }
  
  extension[T](stack: mutable.Stack[T])
    def popUntil(f: T => Boolean): mutable.Stack[T] = {
      // Pop elements from the top until we find one that matches the predicate
      // or until the stack is empty
      while stack.nonEmpty && !f(stack.top) do {
        stack.pop()
      }
      stack
    }
  
}
