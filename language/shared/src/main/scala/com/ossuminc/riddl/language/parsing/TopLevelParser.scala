/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, CommonOptions, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.Timer
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.io.File
import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext
import scala.scalajs.js.annotation._

/** The TopLevel (Root) parser.
  * This class
  * @param input
  * @param commonOptions
  * @param ec
  */
@JSExportTopLevel("TopLevelParser")
class TopLevelParser(
  val input: RiddlParserInput,
  val commonOptions: CommonOptions = CommonOptions.empty
)(implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global)
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

  private def rootInclude[u: P]: P[IncludeHolder[OccursAtRootScope]] = {
    include[u, OccursAtRootScope](rootValues(_))
  }

  private def rootValues[u: P]: P[Seq[OccursAtRootScope]] = {
    P(
      Start ~ (comment | rootInclude[u] | domain | author)./.rep(1) ~ End
    ).map { (content: Contents[OccursAtRootScope]) =>
      mergeAsynchContent[OccursAtRootScope](content)
    }
  }

  def root[u: P]: P[Root] = {
    rootValues.map { (content: Seq[OccursAtRootScope]) => Root(content) }
  }

  @JSExport
  def parseRoot(withVerboseFailures: Boolean = false): Either[Messages, Root] = {
    parseRule[Root](input, root(_), withVerboseFailures) {
      (result: Either[Messages, Root], input: RiddlParserInput, index: Int) =>
        result match {
          case l: Left[Messages, Root] => l
          case r @ Right(root) =>
            if root.contents.isEmpty then
              error(At(input, index), s"Parser could not translate '${input.origin}' after $index characters")
            end if
            r
        }
    }
  }
}

@JSExportTopLevel("TopLevelParser$")
object TopLevelParser {

  @JSExport  
  def parseInput(
    input: RiddlParserInput,
    commonOptions: CommonOptions = CommonOptions.empty,
    withVerboseFailures: Boolean = false
  ): Either[Messages, Root] = {
    Timer.time(s"parse ${input.origin}", commonOptions.showTimes) {
      // val es: ExecutorService = Executors.newWorkStealingPool(commonOptions.maxParallelParsing)
      implicit val _: ExecutionContext = ExecutionContext.Implicits.global
      val tlp = new TopLevelParser(input, commonOptions)
      tlp.parseRoot(withVerboseFailures)
    }
  }

  def parsePath(
    path: Path,
    commonOptions: CommonOptions = CommonOptions.empty
  ): Either[Messages, Root] = {
    if Files.exists(path) then
      if Files.isReadable(path) then parseInput(RiddlParserInput(path), commonOptions)
      else Left(List(Messages.error(s"Input file `${path.toString} is not readable.")))
      end if
    else Left(List(Messages.error(s"Input file `${path.toString} does not exist.")))
    end if
  }

  def parseFile(
    file: File,
    commonOptions: CommonOptions = CommonOptions.empty
  ): Either[Messages, Root] = {
    parsePath(file.toPath, commonOptions)
  }

  @JSExport
  def parseString(
    input: String,
    commonOptions: CommonOptions = CommonOptions.empty,
    origin: Option[String] = None
  ): Either[Messages, Root] = {
    val spi = StringParserInput(input, origin.getOrElse(s"string(${input.length})"))
    parseInput(spi, commonOptions)
  }
}
