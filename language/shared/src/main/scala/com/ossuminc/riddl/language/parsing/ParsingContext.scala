/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At, CommonOptions, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.Timer
import com.ossuminc.riddl.utils.SeqHelpers.*
import fastparse.*
import fastparse.Parsed.Failure
import fastparse.Parsed.Success

import scala.annotation.unused
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

/** Unit Tests For ParsingContext */
trait ParsingContext extends ParsingErrors {

  import com.ossuminc.riddl.utils.URL
  import fastparse.P

  def commonOptions: CommonOptions

  def parseRule[RESULT <: RiddlValue](
    rpi: RiddlParserInput,
    rule: P[?] => P[RESULT],
    withVerboseFailures: Boolean = false
  )(validate: (result: Either[Messages, RESULT], input: RiddlParserInput, index: Int) => Either[Messages, RESULT] = {
    (result: Either[Messages, RESULT], _: RiddlParserInput, _: Int) => result
  }): Either[Messages, RESULT] = {
    try {
      fastparse.parse[RESULT](rpi, rule(_), withVerboseFailures) match {
        case Success(root, index) =>
          if messagesNonEmpty then validate(Left(messagesAsList), rpi, index)
          else validate(Right(root), rpi, index)
          end if
        case failure: Failure =>
          makeParseFailureError(failure, rpi)
          validate(Left(messagesAsList), rpi, 0)
      }
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception, At((0, 0), rpi))
        validate(Left(messagesAsList), rpi, 0)
    }
  }

  def location[u: P](implicit ctx: P[?]): P[At] = {
    // NOTE: This isn't strictly kosher because of the cast but as long as we
    // NOTE: always use a RiddlParserInput, should be safe enough. This is
    // NOTE: required because of includes and concurrent parsing
    P(Index.map(idx => ctx.input.asInstanceOf[RiddlParserInput].location(idx)))
  }

  def doImport(
    loc: At,
    domainName: Identifier,
    url: URL
  )(implicit ctx: P[?]): Domain = {
    // TODO: implement importDomain, issue #72
    Domain(At(), Identifier(At(), "NotImplemented"))
    // importDomain(url)
  }

  def doInclude[CT <: RiddlValue](
    loc: At,
    str: LiteralString
  )(rule: P[?] => P[Seq[CT]])(implicit ctx: P[?]): AST.IncludeHolder[CT] = {
    Timer.time(s"include '${str.s}'", commonOptions.showIncludeTimes) {
      val contentsF: Future[Seq[CT]] = doIncludeParsing[CT](loc, str, rule)
      AST.IncludeHolder[CT](loc, str.s, contentsF)
    }
  }

  private def doIncludeParsing[CT <: RiddlValue](
    loc: At, 
    str: LiteralString, 
    rule: P[?] => P[Seq[CT]])(implicit ctx: P[?]
  ): Future[Seq[CT]] = {
    try {
      import com.ossuminc.riddl.utils.{Loader, URL}
      val path = str.s
      val url: URL = if path.startsWith("http") then {
        URL(path)
      } else {
        val name: String = {
          if path.endsWith(".riddl") then path
          else path + ".riddl"
        }
        ctx.input.asInstanceOf[RiddlParserInput].root.resolve(name)
      }
      Loader(url).load.map { (lines: Iterator[String]) =>
        val rpi = RiddlParserInput(lines.mkString, url)
        doParse[CT](loc, rpi, str, rule)
      }
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception, loc, s"while including '${str.s}'")
        Future.failed(exception) 
    }
  }

  private def doParse[CT <: RiddlValue](loc: At, rpi: RiddlParserInput, str: LiteralString, rule: P[?] => P[Seq[CT]])(
    implicit ctx: P[?]
  ): Seq[CT] = {
    fastparse.parse[Seq[CT]](rpi, rule(_), verboseFailures = true) match {
      case Success(content, _) =>
        if messagesNonEmpty then Seq.empty[CT]
        else if content.isEmpty then
          error(loc, s"Parser could not translate '${rpi.origin}''", s"while including '${str.s}''")
        end if
        content
      case failure: Failure =>
        makeParseFailureError(failure, rpi)
        Seq.empty[CT]
    }
  }


}
