package com.reactific.riddl.utils

object  SeqHelpers {

  implicit class SeqHelpers[T](seq: Seq[T]) {
    def dropUntil(f: T => Boolean): Seq[T] = {
      val index = seq.indexWhere(f)
      if (index < 0) {
        Seq.empty[T]
      } else {
        seq.drop(index)
      }
    }
  }
}
