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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.annotation._

/** The TopLevel (Root) parser. This class
  * @param input
  * @param commonOptions
  * @param ec
  */
@JSExportTopLevel("TopLevelParser")
class TopLevelParser(
  val input: RiddlParserInput,
  val commonOptions: CommonOptions = CommonOptions.empty
) extends DomainParser
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

  import scala.concurrent.Future

  private def rootInclude[u: P]: P[Include[RootScopeContents]] = {
    include[u, RootScopeContents](rootDefinitions(_))
  }

  private def rootDefinitions[u:P]: P[Seq[RootScopeContents]] = {
    P(comment | rootInclude[u] | domain | author).asInstanceOf[P[RootScopeContents]]./.rep(1)
  }
  
  protected def root[u: P]: P[Root] = {
    P(Start ~ rootDefinitions ~ End).map { (content: Seq[RootScopeContents]) => Root(content) }
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

  import com.ossuminc.riddl.utils.URL

  import scala.concurrent.ExecutionContext

  /** Main entry point into parsing. This sets up the asynchronous (but maybe not parallel) parsing of the input to the
    * parser.
    * @param url
    *   A `file://` or `https://` based url to specify the source of the parser input
    * @param commonOptions
    *   Options relevant to parsing the input
    * @param withVerboseFailures
    *   Control whether parse failures are diagnosed verbosely or not. Typically only useful to maintainers of RIDDL, or
    *   test cases
    */
  @JSExport
  def parseURL(
    url: URL,
    commonOptions: CommonOptions = CommonOptions.empty,
    withVerboseFailures: Boolean = false
  )(implicit
    ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  ): Future[Either[Messages, Root]] = {
    import com.ossuminc.riddl.utils.Loader
    Loader(url).load.map { (data: String) =>
      val rpi = RiddlParserInput(data.mkString, url)
      parseInput(rpi, commonOptions, withVerboseFailures)
    }
  }

  /** Alternate, non-asynchronous interface to parsing. If you have your data already, you can just make your own
    * RiddlParserInput from a string and call this to start parsing.
    * @param input
    *   The RiddlParserInput that contains the data to parse
    * @param commonOptions
    *   The common options that could affect parsing or its output
    * @param withVerboseFailures
    *   For the utility of RIDDL implementers.
    * @return
    */
  @JSExport
  def parseInput(
    input: RiddlParserInput,
    commonOptions: CommonOptions = CommonOptions.empty,
    withVerboseFailures: Boolean = false
  ): Either[Messages, Root] = {
    Timer.time(s"parse ${input.origin}", commonOptions.showTimes) {
      implicit val _: ExecutionContext = ExecutionContext.Implicits.global
      val tlp = new TopLevelParser(input, commonOptions)
      tlp.parseRoot(withVerboseFailures)
    }
  }
}

