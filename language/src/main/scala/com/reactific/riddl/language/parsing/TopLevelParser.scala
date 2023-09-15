/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.Messages
import fastparse.*
import fastparse.ScalaWhitespace.*

import java.io.File
import java.net.URL
import java.nio.file.Path

/** Top level parsing rules */
class TopLevelParser(rpi: RiddlParserInput)
  extends DomainParser
    with AdaptorParser
    with ApplicationParser
    with ContextParser
    with EntityParser
    with EpicParser
    with FunctionParser
    with HandlerParser
    with ProjectorParser
    with ReferenceParser
    with RepositoryParser
    with SagaParser
    with StreamingParser
    with StatementParser
    with TypeParser
    with CommonParser {

  push(rpi)

  def root[u: P]: P[RootContainer] = {
    P(Start ~ (domain | author).rep(0) ~ End).map { contents =>
      pop
      val groups = contents.groupBy(_.getClass == classOf[Domain])
      val domains: Seq[Domain] = groups.getOrElse(true, Seq.empty[Domain]).asInstanceOf[Seq[Domain]]
      val authors: Seq[Author] = groups.getOrElse(false, Seq.empty[Author]).asInstanceOf[Seq[Author]]
      RootContainer(domains, authors, Seq(rpi))
    }
  }
}

object TopLevelParser {

  def parse(
    input: RiddlParserInput
  ): Either[Messages, RootContainer] = {
    val tlp = new TopLevelParser(input)
    tlp.expect(tlp.root(_)).map(_._1)
  }

  def parse(file: File): Either[Messages, RootContainer] = {
    val fpi = FileParserInput(file)
    parse(fpi)
  }

  def parse(path: Path): Either[Messages, RootContainer] = {
    val fpi = new FileParserInput(path)
    parse(fpi)
  }

  def parse(url: URL): Either[Messages, RootContainer] = {
    val upi = URLParserInput(url)
    parse(upi)
  }

  def parse(
    input: String,
    origin: String = "string"
  ): Either[Messages, RootContainer] = {
    val spi = StringParserInput(input, origin)
    parse(spi)
  }
}
