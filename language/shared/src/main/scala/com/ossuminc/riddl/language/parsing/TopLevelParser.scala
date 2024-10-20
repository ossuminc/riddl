/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{PlatformIOContext, Timer, CommonOptions}
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.annotation.*

/** The TopLevel (Root) parser. This class
  * @param commonOptions
  *   Common options to use during the parsing
  */
@JSExportTopLevel("TopLevelParser")
class TopLevelParser(using io: PlatformIOContext)
    extends ProcessorParser
    with DomainParser
    with AdaptorParser
    with ApplicationParser
    with ContextParser
    with EntityParser
    with EpicParser
    with FunctionParser
    with ModuleParser
    with NebulaParser
    with ProjectorParser
    with RepositoryParser
    with RootParser
    with SagaParser
    with StreamingParser
    with StatementParser
    with ParsingContext {

  @JSExport
  def parseRoot(input: RiddlParserInput, withVerboseFailures: Boolean = false): Either[Messages, Root] = {
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

  @JSExport
  def parseNebula(input: RiddlParserInput, withVerboseFailures: Boolean = false): Either[Messages, Nebula] = {
    parseRule[Nebula](input, nebula(_), withVerboseFailures) {
      (result: Either[Messages, Nebula], input: RiddlParserInput, index: Int) =>
        result match {
          case left: Left[Messages, Root] => left
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
  def parseURL(
    url: URL,
    withVerboseFailures: Boolean = false
  )(using io: PlatformIOContext): Future[Either[Messages, Root]] = {
    io.load(url).map { (data: String) =>
      val rpi = RiddlParserInput(data.mkString, url)
      parseInput(rpi, withVerboseFailures)
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
  def parseInput(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using io: PlatformIOContext): Either[Messages, Root] = {
    Timer.time(s"parse ${input.origin}", io.options.showTimes) {
      implicit val _: ExecutionContext = io.ec
      val tlp = new TopLevelParser()
      tlp.parseRoot(input, withVerboseFailures)
    }
  }

  def parseString(
    input: String,
    withVerboseFailures: Boolean = false
  )(using PlatformIOContext): Either[Messages, Root] = {
    val rpi = RiddlParserInput(input, "")
    parseInput(rpi)
  }

  def parseNebulaFromInput(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using io: PlatformIOContext): Either[Messages, Nebula] = {
    Timer.time(s"parse nebula from ${input.origin}", io.options.showTimes) {
      implicit val _: ExecutionContext = io.ec
      val tlp = new TopLevelParser()
      tlp.parseNebula(input, withVerboseFailures)
    }
  }
}
