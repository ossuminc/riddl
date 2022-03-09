package com.reactific.riddl.language.parsing

import java.io.File
import scala.collection.mutable

/** The stack of input sources while parsing */
case class InputStack(
) {

  private val inputs: mutable.Stack[RiddlParserInput] = mutable.Stack()

  def push(input: RiddlParserInput): Unit = inputs.push(input)

  def push(file: File): Unit = { inputs.push(FileParserInput(file)) }

  def pop: RiddlParserInput = { inputs.pop() }

  def current: RiddlParserInput = {
    require(inputs.nonEmpty, "No current input available")
    inputs.head
  }
}
