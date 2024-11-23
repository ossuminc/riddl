/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, Timer, URL}
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}
import scala.scalajs.js.annotation.*

/** The TopLevel (Root) parser. This class munges all the individual parsers together and provides top level parsing
  * functionality.
  * @param input
  *   The RiddlParserInput that contains the data to parse
  * @param withVerboseFailures
  *   For the utility of RIDDL implementers.
  */
@JSExportTopLevel("TopLevelParser")
case class TopLevelParser(
  input: RiddlParserInput,
  withVerboseFailures: Boolean
)(using io: PlatformContext)
    extends ExtensibleTopLevelParser

@JSExportTopLevel("TopLevelParser$")
object TopLevelParser {

  import com.ossuminc.riddl.utils.URL

  import scala.concurrent.ExecutionContext

  /** Main entry point into parsing. This sets up the asynchronous (but maybe not parallel) parsing of the input to the
    * parser.
    * @param url
    *   A `file://` or `https://` based url to specify the source of the parser input
    * @param withVerboseFailures
    *   Control whether parse failures are diagnosed verbosely or not. Typically only useful to maintainers of RIDDL, or
    *   test cases
    */
  def parseURL(
    url: URL,
    withVerboseFailures: Boolean = false
  )(using io: PlatformContext): Future[Either[Messages, Root]] = {
    io.load(url).map { (data: String) =>
      val rpi = RiddlParserInput(data.mkString, url)
      val tlp = new TopLevelParser(rpi, withVerboseFailures)
      tlp.parseRoot
    }
  }

  /** Alternate, non-asynchronous interface to parsing. If you have your data already, you can just make your own
    * RiddlParserInput from a string and call this to start parsing.
    * @param input
    *   The RiddlParserInput that contains the data to parse
    * @param withVerboseFailures
    *   For the utility of RIDDL implementers.
    * @return
    */
  def parseInput(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using io: PlatformContext): Either[Messages, Root] = {
    Timer.time(s"parse ${input.origin}", io.options.showTimes) {
      implicit val _: ExecutionContext = io.ec
      val tlp = new TopLevelParser(input, withVerboseFailures)
      tlp.parseRoot
    }
  }

  /** Parse a string directly
   *
   * @param input
   * The input string to parse
   * @param withVerboseFailures
   *   For the utility of RIDDL implementers.
   * @return
   *   Left(messages) -> messages indicaitng the error
   *   Right(root) -> the resulting AST.Root from the parse
   */
  def parseString(
    input: String,
    withVerboseFailures: Boolean = false
  )(using PlatformContext): Either[Messages, Root] = {
    val rpi = RiddlParserInput(input, "")
    val tlp = new TopLevelParser(rpi, withVerboseFailures)
    tlp.parseRoot
  }

  /** Parse an arbitrary (nebulous) set of definitions in any order
    *
    * @param input
    *   The input to parse
    * @param withVerboseFailures
    *   For the utility of RIDDL implementers.
    * @return
    *   - Left(messages) -> messages indicaitng the error
    *   - Right(nebula) -> the nebula containing the list of things that were parsed
    */
  def parseNebula(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using io: PlatformContext): Either[Messages, Nebula] = {
    Timer.time(s"parse nebula from ${input.origin}", io.options.showTimes) {
      val tlp = new TopLevelParser(input, withVerboseFailures)
      tlp.parseNebula
    }
  }

  def parseToTokens(
    input: RiddlParserInput,
    withVerboseFailures: Boolean = false
  )(using io: PlatformContext): Either[Messages, List[Token]] = {
    val tlp = new TopLevelParser(input, withVerboseFailures)
    tlp.parseTokens
  }
}
