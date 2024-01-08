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
import fastparse.*
import fastparse.Parsed.Failure
import fastparse.Parsed.Success
import org.apache.commons.lang3.exception.ExceptionUtils

import java.io.File
import java.nio.file.{Files, Path}
import scala.annotation.unused
import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/** Unit Tests For ParsingContext */
trait ParsingContext extends ParsingErrors {

  def commonOptions: CommonOptions

  implicit def ec: ExecutionContext

  def parseRule[RESULT <: RiddlValue](
    rpi: RiddlParserInput,
    rule: P[?] => P[RESULT],
    withVerboseFailures: Boolean = false
  )(validate: (result: RESULT, input: RiddlParserInput, index: Int) => RESULT = {
    (result: RESULT, _: RiddlParserInput, _: Int) => result
  }): Either[Messages, RESULT] = {
    try {
      fastparse.parse[RESULT](rpi, rule(_), withVerboseFailures) match {
        case Success(root, index) =>
          if errorsNonEmpty then Left(errorsAsList) else Right(validate(root, rpi, index))
        case failure: Failure =>
          makeParseFailureError(failure, rpi)
          Left(errorsAsList)
      }
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception)
        Left(errorsAsList)
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

  def doInclude[CT <: RiddlValue](
    loc: At,
    str: LiteralString
  )(rule: P[?] => P[Seq[CT]])(implicit ctx: P[?]): AST.IncludeHolder[CT] = {
    val future = Future[Either[Messages, Seq[CT]]] {
      Timer.time(s"include '${str.s}'", commonOptions.showIncludeTimes ) {
        try {
          val rpi = startNextSource(str)
          fastparse.parse[Seq[CT]](rpi, rule(_), verboseFailures = true) match {
            case Success(content, _) =>
              if errorsNonEmpty then Left(errorsAsList)
              else if content.isEmpty then
                error(loc, s"Parser could not translate '${rpi.origin}''", s"while including '${str.s}''")
                Right(content)
              else Right(content)
            case failure: Failure =>
              makeParseFailureError(failure, rpi)
              Left(errorsAsList)
          }
        } catch {
          case NonFatal(exception) =>
            makeParseFailureError(exception, loc, s"while included '${str.s}'")
            Left(errorsAsList)
        }
      }
    }
    AST.IncludeHolder[CT](loc, str.s, commonOptions.maxIncludeWait, future)
  }

  def mergeAsynchContent[CT <: RiddlValue](contents: Contents[CT]): Contents[CT] = {
    contents.map {
      case ih: IncludeHolder[?] =>
        val contents: Contents[CT] =
          try {
            Await.result(ih.future, ih.maxDelay) match {
              case Left(messages: Messages) =>
                Seq.empty[CT]
              case Right(content: Seq[?]) =>
                content.asInstanceOf[Seq[CT]]
            }
          } catch {
            case NonFatal(exception) =>
              makeParseFailureError(exception, ih.loc, s"while including '${ih.origin}''")
              Seq.empty[CT]
          }
        Include[CT](ih.loc, ih.origin, contents).asInstanceOf[CT]
      case rv: CT => rv
    }
  }
}
