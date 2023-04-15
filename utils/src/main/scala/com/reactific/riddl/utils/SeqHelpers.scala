/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

import scala.collection.mutable

object SeqHelpers {

  implicit class SeqHelpers[T](seq: Seq[T]) {
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
      val set = scala.collection.mutable.Set[T]()
      seq.forall { x =>
        if set(x) then false
        else {
          set += x
          true
        }
      }
    }
  }

  implicit class StackHelpers[T](stack: mutable.Stack[T]) {
    def popUntil(f: T => Boolean): mutable.Stack[T] = {
      val index = stack.indexWhere(f) - 1
      if index < 0 then { stack.clearAndShrink() }
      else { for _ <- 0 to index do { stack.pop() }; stack }
    }
  }
}
