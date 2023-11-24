/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.At
import com.reactific.riddl.language.Messages.Messages
import fastparse.{P, *}
import fastparse.Parsed.Success
import fastparse.Parsed.Failure
import fastparse.MultiLineWhitespace.*

import java.io.File
import java.net.URL
import java.nio.file.Path
import scala.util.control.NonFatal

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
    with CommonParser
    with ParsingContext {

  push(rpi)

  def root[u: P]: P[RootContainer] = {
    val curr_input = current
    P(
      Start ~ comments ~ (domain | author)./.rep(1) ~ comments ~ End
    ).map { case (preComments, content, postComments) =>
      pop
      RootContainer(preComments, content, postComments, Seq(curr_input))
    }
  }

  def parseRootContainer(
    withVerboseFailures: Boolean = false
  ): Either[Messages, RootContainer] = {
    val input = current
    try {
      fastparse.parse[RootContainer](input, root(_), withVerboseFailures) match {
        case Success(root, index) =>
          if errors.nonEmpty then Left(errors.toList)
          else if root.contents.isEmpty then
            error(
              At(input, index),
              s"Parser could not translate '${input.origin}' after $index characters",
              s"while parsing ${input.origin}"
            )
            Right(root)
          else Right(root)
        case failure: Failure =>
          makeParseFailureError(failure)
          Left(errors.toList)
      }
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception)
        Left(errors.toList)
    }
  }
}

object TopLevelParser {

  def parse(
    input: RiddlParserInput
  ): Either[Messages, RootContainer] = {
    val tlp = new TopLevelParser(input)
    tlp.parseRootContainer()
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
