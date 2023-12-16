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

  def push(input: RiddlParserInput): Unit = inputs.push(input)

  def push(file: File): Unit = { inputs.push(FileParserInput(file)) }

  def push(url: URL): Unit = { inputs.push(URLParserInput(url)) }

  def pop: RiddlParserInput = { inputs.pop() }

  def current: RiddlParserInput = {
    require(inputs.nonEmpty, "No current input available")
    inputs.headOption.getOrElse(RiddlParserInput.empty)
  }
}
