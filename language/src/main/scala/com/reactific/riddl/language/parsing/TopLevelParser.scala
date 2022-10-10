/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.Messages
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
  ): Either[Messages, RootContainer] = {
    val tlp = new TopLevelParser(input)
    tlp.expect(tlp.fileRoot(_)).map(_._1)
  }

  def parse(file: File): Either[Messages, RootContainer] = {
    val fpi = FileParserInput(file)
    val tlp = new TopLevelParser(fpi)
    tlp.expect(tlp.fileRoot(_)).map(_._1)
  }

  def parse(path: Path): Either[Messages, RootContainer] = {
    val fpi = new FileParserInput(path)
    val tlp = new TopLevelParser(fpi)
    tlp.expect(tlp.fileRoot(_)).map(_._1)
  }

  def parse(
    input: String,
    origin: String = "string"
  ): Either[Messages, RootContainer] = {
    val sp = StringParserInput(input, origin)
    val tlp = new TopLevelParser(sp)
    tlp.expect(tlp.fileRoot(_)).map(_._1)
  }

}
