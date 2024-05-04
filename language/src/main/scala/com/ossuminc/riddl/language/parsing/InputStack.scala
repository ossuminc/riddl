/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import java.io.File
import java.net.URL
import scala.collection.mutable

/** The stack of input sources while parsing */
case class InputStack(
) {

  private val inputs: mutable.Stack[RiddlParserInput] = mutable.Stack()

  def isEmpty: Boolean = inputs.isEmpty

  def push(input: RiddlParserInput): Unit = synchronized { inputs.push(input) }

  def push(file: File): Unit = { synchronized { inputs.push(FileParserInput(file)) } }

  def push(url: URL): Unit = { synchronized { inputs.push(URLParserInput(url)) } }

  def pop: RiddlParserInput = { synchronized { inputs.pop() } }

  def current: RiddlParserInput = {
    synchronized {
      require(inputs.nonEmpty, "No current input available")
      inputs.headOption.getOrElse(RiddlParserInput.empty)
    }
  }

  def sourceNames: Seq[String] = {
    synchronized { inputs.toSeq.map(_.origin) }
  }
}
