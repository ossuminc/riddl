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

import com.reactific.riddl.language.AST.RootContainer
import fastparse.*
import fastparse.ScalaWhitespace.*

import java.io.File
import java.nio.file.Path

/** Top level parsing rules */
class TopLevelParser(rpi: RiddlParserInput) extends DomainParser {
  push(rpi)

  def fileRoot[u: P]: P[RootContainer] = {
    P(Start ~ domain.rep(0) ~ End).map(RootContainer(_, inputSeen))
  }
}

case class FileParser(topFile: File)
    extends TopLevelParser(RiddlParserInput(topFile))

case class StringParser(content: String)
    extends TopLevelParser(RiddlParserInput(content))

object TopLevelParser {

  def parse(
    input: RiddlParserInput
  ): Either[Seq[ParserError], RootContainer] = {
    val tlp = new TopLevelParser(input)
    tlp.expect(tlp.fileRoot(_)).map(_._1)
  }

  def parse(file: File): Either[Seq[ParserError], RootContainer] = {
    val fpi = FileParserInput(file)
    val tlp = new TopLevelParser(fpi)
    tlp.expect(tlp.fileRoot(_)).map(_._1)
  }

  def parse(path: Path): Either[Seq[ParserError], RootContainer] = {
    val fpi = new FileParserInput(path)
    val tlp = new TopLevelParser(fpi)
    tlp.expect(tlp.fileRoot(_)).map(_._1)
  }

  def parse(
    input: String,
    origin: String = "string"
  ): Either[Seq[ParserError], RootContainer] = {
    val sp = StringParserInput(input, origin)
    val tlp = new TopLevelParser(sp)
    tlp.expect(tlp.fileRoot(_)).map(_._1)
  }

}
