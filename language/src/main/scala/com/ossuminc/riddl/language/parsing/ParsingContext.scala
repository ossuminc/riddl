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

import java.io.File
import java.nio.file.Files
import scala.annotation.unused
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import scala.util.control.NonFatal

/** Unit Tests For ParsingContext */
trait ParsingContext extends ParsingErrors {

  def commonOptions: CommonOptions

  implicit def ec: ExecutionContext

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

  def location[u: P](implicit ctx: P[_]): P[At] = {
    // NOTE: This isn't strictly kosher because of the cast but as long as we
    // NOTE: always use a RiddlParserInput, should be safe enough. This is
    // NOTE: required because of includes and concurrent parsing
    P(Index.map(idx => ctx.input.asInstanceOf[RiddlParserInput].location(idx)))
  }

  def doImport(
    loc: At,
    domainName: Identifier,
    fileName: LiteralString
  )(implicit ctx: P[?]): Domain = {
    val name = fileName.s
    val input = ctx.input.asInstanceOf[RiddlParserInput]
    val file = new File(input.root, name)
    if !file.exists() then {
      error(s"File '$name` does not exist, can't be imported.")
      Domain(loc, domainName)
    } else {
      importDomain(file)
    }
  }

  private def importDomain(
    @unused file: File
  ): Domain = {
    // TODO: implement importDomain, issue #72
    Domain(At(), Identifier(At(), "NotImplemented"))
  }

  private def startNextSource(from: LiteralString)(implicit ctx: P[?]): RiddlParserInput = {
    val str = from.s
    if str.startsWith("http") then {
      val url = java.net.URI(str).toURL
      RiddlParserInput(url)
    } else {
      val name = {
        if str.endsWith(".riddl") then str
        else str + ".riddl"
      }
      val path = ctx.input.asInstanceOf[RiddlParserInput].root.toPath.resolve(name)
      require(Files.exists(path), s"File does not exist: $name")
      require(Files.isReadable(path), s"File is not readable; $name")
      RiddlParserInput(path)
    }
  }

  private def doIncludeParsing[CT <: RiddlValue](loc: At, str: LiteralString, rule: P[?] => P[Seq[CT]])(implicit
    ctx: P[?]
  ): Seq[CT] = {
    try {
      val rpi = startNextSource(str)
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
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception, loc, s"while included '${str.s}'")
        Seq.empty[CT]
    }
  }

  def doInclude[CT <: RiddlValue](
    loc: At,
    str: LiteralString
  )(rule: P[?] => P[Seq[CT]])(implicit ctx: P[?]): AST.IncludeHolder[CT] = {
    Timer.time(s"include '${str.s}'", commonOptions.showIncludeTimes) {
      val future: Future[Seq[CT]] = {
        if commonOptions.maxParallelParsing > 1 then Future { blocking { doIncludeParsing[CT](loc, str, rule) } }
        else Future.successful { doIncludeParsing[CT](loc, str, rule) }
        end if
      }
      AST.IncludeHolder[CT](loc, str.s, commonOptions.maxIncludeWait, future)
    }
  }

  def mergeAsynchContent[CT <: RiddlValue](contents: Contents[CT]): Contents[CT] = {
    val result: Contents[CT] = contents.map {
      case ih: IncludeHolder[CT] @unchecked =>
        val contents: Contents[CT] =
          try {
            val result = Await.result[Contents[CT]](ih.future, ih.maxDelay)
            mergeAsynchContent(result)
          } catch {
            case NonFatal(exception) =>
              makeParseFailureError(exception, ih.loc, s"while including '${ih.origin}''")
              // NOTE: makeParseFailureError already captured the error
              // NOTE: We just want to place empty content into the Include
              Seq.empty[CT]
          }
        Include[CT](ih.loc, ih.origin, contents).asInstanceOf[CT]
      case rv: CT => rv
    }

    val allIncludes = result.filter[Include[?]]
    val distinctIncludes = allIncludes.distinctBy(_.origin)
    for {
      incl <- distinctIncludes
      copies = allIncludes.filter(_.origin == incl.origin) if copies.size > 1
    } yield {
      val copyList = copies.map(i => i.origin + " at " + i.loc.toShort).mkString(", ")
      val message = s"Duplicate include origin detected in $copyList"
      warning(incl.loc, message, "while merging includes")
    }
    result
  }
}
