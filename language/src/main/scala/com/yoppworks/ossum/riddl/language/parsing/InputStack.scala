package com.yoppworks.ossum.riddl.language.parsing

import java.io.File
import scala.collection.mutable

/** Unit Tests For InputStack */
case class InputStack(
) {

  private val inputs: mutable.Stack[RiddlParserInput] = mutable.Stack()

  def push(input: RiddlParserInput): Unit = inputs.push(input)

  def push(file: File): Unit = { inputs.push(FileParserInput(file)) }

  def pop: RiddlParserInput = { inputs.pop() }

  def current: Option[RiddlParserInput] = { inputs.headOption }
}
