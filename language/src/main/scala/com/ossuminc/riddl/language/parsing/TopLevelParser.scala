/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, CommonOptions, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.io.File
import java.nio.file.{Files, Path}
import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext

/** Top level parsing rules */
class TopLevelParser(rpi: RiddlParserInput, val commonOptions: CommonOptions = CommonOptions.empty )
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

  private def rootInclude[u: P]: P[Include[OccursAtRootScope]] = {
    include[OccursAtRootScope, u](rootValues(_))
  }

  private def rootValues[u: P]: P[Seq[OccursAtRootScope]] = {
    P(
      Start ~ (comment | rootInclude[u] | domain | author)./.rep(1) ~ End
    )
  }

  private def root[u: P]: P[Root] = {
    val curr_input = current
    rootValues.map { (content: Seq[OccursAtRootScope]) =>
      pop
      Root(content, Seq(curr_input))
    }
  }

  def parseRoot(
    withVerboseFailures: Boolean = false
  ): Either[Messages, Root] = {
    parseRule[Root](root(_), withVerboseFailures) { (root: Root, input: RiddlParserInput, index: Int) =>
      if root.contents.isEmpty then
        error(
          At(input, index),
          s"Parser could not translate '${input.origin}' after $index characters",
          s"while parsing ${input.origin}"
        )
      end if
      root
    }
  }
}

object TopLevelParser {


  def parseRoot(
    path: Path,
    commonOptions: CommonOptions
  ): Either[Messages, Root] = {
    if Files.exists(path) then {
      if Files.isReadable(path) then
        val es: ExecutorService = Executors.newWorkStealingPool(commonOptions.maxParallelParsing)
        implicit val _: ExecutionContext = ExecutionContext.fromExecutorService(es)
        val fpi = new FileParserInput(path)
        val tlp = new TopLevelParser(fpi)
        tlp.parseRoot()
      else {
        Left(
          List(Messages.error(s"Input file `${path.toString} is not readable."))
        )
      }
    } else {
      Left(
        List(Messages.error(s"Input file `${path.toString} does not exist."))
      )
    }
  }

  def parse(input: RiddlParserInput): Either[Messages, Root] = {
    val tlp = new TopLevelParser(input)
    tlp.parseRoot()
  }
  def parse(file: File): Either[Messages, Root] = {
    val fpi = FileParserInput(file)
    parse(fpi)
  }


  def parse(
    input: String,
    origin: String = "string"
  ): Either[Messages, Root] = {
    val spi = StringParserInput(input, origin)
    parse(spi)
  }
}
