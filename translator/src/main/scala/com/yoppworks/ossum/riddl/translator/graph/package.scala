package com.yoppworks.ossum.riddl.translator

package object graph {

  implicit final class StringExtensions(private val s: String) extends AnyVal {
    def indent(num: Int): String = (" " * num) + s
  }

}
