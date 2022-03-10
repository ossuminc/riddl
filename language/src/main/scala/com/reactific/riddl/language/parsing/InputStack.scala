/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
