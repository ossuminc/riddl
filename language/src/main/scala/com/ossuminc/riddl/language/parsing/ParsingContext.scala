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
import fastparse.{P, *}
import fastparse.Parsed.Failure
import fastparse.Parsed.Success
import org.apache.commons.lang3.exception.ExceptionUtils

import java.io.File
import java.nio.file.Path
import scala.annotation.unused
import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/** Unit Tests For ParsingContext */
trait ParsingContext extends ParsingErrors {

  def commonOptions: CommonOptions
  implicit def ec: ExecutionContext

  protected val stack: InputStack = InputStack()

  @inline def current: RiddlParserInput = { stack.current }
  @inline protected def push(path: Path): Unit = { stack.push(path) }
  @inline protected def push(rpi: RiddlParserInput): Unit = { stack.push(rpi) }
  @inline protected def pop: RiddlParserInput = {
    val rpi = stack.pop
    filesSeen.append(rpi)
    rpi
  }

  private val filesSeen: mutable.ListBuffer[RiddlParserInput] = mutable.ListBuffer.empty[RiddlParserInput]

  def inputSeen: Seq[RiddlParserInput] = filesSeen.toSeq

  def parseRule[RESULT <: RiddlValue](
    rule: P[?] => P[RESULT],
    withVerboseFailures: Boolean = false
  )(validate: (result: RESULT, input: RiddlParserInput, index: Int) => RESULT = {
    (result: RESULT, _: RiddlParserInput, _: Int) => result
  }): Either[Messages, RESULT] = {
    val input = current
    try {
      fastparse.parse[RESULT](input, rule(_), withVerboseFailures) match {
        case Success(root, index) =>
          if errors.nonEmpty then Left(errors.toList) else Right(validate(root, input, index))
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

  def parseInputForRule[RESULT <: RiddlValue](
    timerName: String,
    input: RiddlParserInput,
    rule: P[?] => P[RESULT],
    withVerboseFailure: Boolean = false
  )(implicit ec: ExecutionContext): Future[Either[Messages, RESULT]] = {
    Future {
      Timer.time(timerName, commonOptions.showTimes) {
        val tlp = new TopLevelParser(input)
        tlp.parseRule[RESULT](rule, withVerboseFailure)()
      }
    }
  }

  def location[u: P]: P[At] = {
    P(Index.map(idx => current.location(idx)))
  }

  def doImport(
    loc: At,
    domainName: Identifier,
    fileName: LiteralString
  ): Domain = {
    val name = fileName.s
    val file = new File(current.root, name)
    if !file.exists() then {
      error(s"File '$name` does not exist, can't be imported.")
      Domain(loc, domainName)
    } else { importDomain(file) }
  }

  private def importDomain(
    @unused file: File
  ): Domain = {
    // TODO: implement importDomain, issue #72
    Domain(At(), Identifier(At(), "NotImplemented"))
  }

  private def startNextSource(from: LiteralString): RiddlParserInput = {
    val str = from.s
    val source = if str.startsWith("http") then {
      val url = java.net.URI(str).toURL
      RiddlParserInput(url)
    } else {
      val name = {
        if str.endsWith(".riddl") then str
        else str + ".riddl"
      }
      val path = current.root.toPath.resolve(name)
      RiddlParserInput(path)
    }
    source
  }

  def doInclude[CT <: RiddlValue](
    loc: At,
    str: LiteralString
  )(rule: P[?] => P[Seq[CT]]): AST.IncludeHolder[CT] = {
    // TODO: implement parallel parsing at include points and use the
    // TODO: commonOption.maxParallelParsing to limit parallelism
    val rpi = startNextSource(str)
    val future = Future[Either[Messages, Seq[CT]]] {
      try {
        this.expectMultiple[CT](rpi, rule) match {
          case Left(theErrors) =>
            theErrors.filterNot(errors.contains).foreach(errors.append)
            Left(theErrors)
          case Right((parseResult, _)) =>
            Right(parseResult)
        }
      } catch {
        case NonFatal(exception) =>
          val message = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n  ", "\n  ", "\n")
          val err = Messages.Message(loc, message, Messages.Error, s"while including ${str.s}")
          val msgs: Messages.Messages = errors.toList :+ err
          Left(msgs)
      }
    }
    AST.IncludeHolder[CT](loc, rpi, commonOptions.maxIncludeWait, future)
  }

  def mergeAsynchContent[CT <: RiddlValue](contents: Contents[CT]): Contents[CT] = {
    contents.map {
      case ih: IncludeHolder[CT] @unchecked =>
        val contents: Contents[CT] = try {
          Await.result[Either[Messages,Seq[CT]]](ih.future, ih.maxDelay) match {
            case Left(messages: Messages) =>
              messages.foreach(m => addMessage(m))
              Seq.empty[CT]
            case Right(content: Seq[CT]) =>
              content
          }
        } catch {
          case NonFatal(exception) =>
            makeParseFailureError(exception, ih.loc, s"while including '${ih.included.origin}")
            Seq.empty[CT]
        }
        Include[CT](ih.loc, ih.included, contents).asInstanceOf[CT]
      case rv: CT => rv
    }
  }

  def expect[T <: RiddlValue](
    parser: P[?] => P[T],
    withVerboseFailures: Boolean = false
  ): Either[Messages, (T, RiddlParserInput)] = {
    val input = current
    try {
      fastparse.parse[T](input, parser(_), withVerboseFailures) match {
        case Success(content, _) =>
          if errors.nonEmpty then Left(errors.toList)
          else Right(content -> input)
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

  def expectMultiple[T <: RiddlValue](
    source: RiddlParserInput,
    parser: P[?] => P[Seq[T]],
    withVerboseFailures: Boolean = false
  ): Either[Messages, (Seq[T], RiddlParserInput)] = {
    val input = current
    try {
      fastparse.parse[Seq[T]](input, parser(_), withVerboseFailures) match {
        case Success(content, index) =>
          if errors.nonEmpty then Left(errors.toList)
          else if content.isEmpty then
            error(
              At(input, index),
              s"Parser could not translate '${input.origin}''",
              s"while including ${source.origin}"
            )
            Right(content -> input)
          else Right(content -> input)
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
