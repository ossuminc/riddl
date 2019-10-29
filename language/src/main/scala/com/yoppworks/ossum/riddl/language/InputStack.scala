package com.yoppworks.ossum.riddl.language

import java.io.File

import scala.collection.mutable

/** Unit Tests For InputStack */
case class InputStack(
  ) {

  val files: mutable.ArrayStack[RiddlParserInput] = mutable.ArrayStack()

  def push(input: RiddlParserInput): Unit = files.push(input)

  def push(file: File): Unit = {
    files.push(RiddlParserInput(file))
  }

  def pop(): RiddlParserInput = {
    files.pop()
  }

  def current(): Option[RiddlParserInput] = {
    files.headOption
  }
}
