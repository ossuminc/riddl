/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.{At, Messages}
import com.reactific.riddl.language.Messages.Messages
import fastparse.*
import fastparse.Parsed.Failure
import fastparse.Parsed.Success
import fastparse.internal.Lazy
import org.apache.commons.lang3.exception.ExceptionUtils

import java.io.File
import java.nio.file.Path
import scala.annotation.unused
import scala.collection.mutable
import scala.util.control.NonFatal

/** Unit Tests For ParsingContext */
trait ParsingContext {

  private val stack: InputStack = InputStack()

  protected val errors: mutable.ListBuffer[Messages.Message] = mutable.ListBuffer.empty[Messages.Message]

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

  def doInclude[T <: Definition](
    str: LiteralString
  )(rule: P[?] => P[Seq[T]]): Include[T] = {
    // TODO: implement parallel parsing at include points and use the
    // TODO: commonOption.maxParallelParsing to limit parallelism
    val source = if str.s.startsWith("http") then {
      val url = java.net.URI(str.s).toURL
      push(url)
      str.s
    } else {
      val name = {
        if str.s.endsWith(".riddl") then str.s
        else str.s + ".riddl"
      }
      val path = current.root.toPath.resolve(name)
      push(path)
      path.toString
    }
    try {
      this.expectMultiple[T](str.s, rule) match {
        case Left(theErrors) =>
          theErrors.filterNot(errors.contains).foreach(errors.append)
          Include[T](str.loc, Seq.empty[T], Some(source))
        case Right((parseResult, _)) =>
          Include[T](str.loc, parseResult, Some(source))
      }
    } catch {
      case NonFatal(exception) =>
        val message = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n  ", "\n  ", "\n")
        error(str.loc, s"Include file '${str.s}' not found: $message ")
        Include[T](str.loc, Seq.empty[T], Some(str.s))
    } finally {
      pop
    }
  }

  def error(message: String): Unit = {
    val msg = Messages.Message(At.empty(current), message, Messages.Error)
    errors.append(msg)
  }
  def error(loc: At, message: String, context: String = ""): Unit = {
    val msg = Messages.Message(loc, message, Messages.Error, context)
    errors.append(msg)
  }

  private def mkTerminals(list: List[Lazy[String]]): String = {
    list
      .map(_.force)
      .map {
        case s: String if s.startsWith("char-pred")  => "pattern"
        case s: String if s.startsWith("chars-with") => "pattern"
        case s: String if s == "fail"                => "whitespace after keyword"
        case s: String                               => s
      }
      .distinct
      .mkString("(", " | ", ")")
  }

  private def makeParseFailureError(failure: Failure): Unit = {
    val location = current.location(failure.index)
    val trace = failure.trace()
    val msg = trace.terminals.value.size match {
      case 0 => "Unexpected content"
      case 1 => s"Expected " + mkTerminals(trace.terminals.value)
      case _ => s"Expected one of " + mkTerminals(trace.terminals.value)
    }
    val context = trace.groups.render
    error(location, msg, context)
  }

  private def makeParseFailureError(exception: Throwable): Unit = {
    val message = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n", "\n  ", "\n")
    error(At.empty, message)
  }

  def expect[T <: RiddlNode](
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

  def expectMultiple[T <: Definition](
    source: String,
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
              s"while including $source"
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
