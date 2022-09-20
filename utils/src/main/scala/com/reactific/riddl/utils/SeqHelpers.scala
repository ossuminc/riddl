package com.reactific.riddl.utils

import scala.collection.mutable

object SeqHelpers {

  implicit class SeqHelpers[T](seq: Seq[T]) {
    def dropUntil(f: T => Boolean): Seq[T] = {
      val index = seq.indexWhere(f)
      if (index < 0) { Seq.empty[T] }
      else { seq.drop(index) }
    }
  }

  implicit class StackHelpers[T](stack: mutable.Stack[T]) {
    def popUntil(f: T => Boolean): mutable.Stack[T] = {
      val index = stack.indexWhere(f) - 1
      if (index < 0) { stack.clearAndShrink() }
      else { for (_ <- 0 to index) { stack.pop() }; stack }
    }
  }
}
