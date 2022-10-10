/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.Messages

import com.reactific.riddl.language.ast.Location
import fastparse.*
import fastparse.Parsed.Failure
import fastparse.Parsed.Success
import fastparse.internal.Lazy

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import scala.annotation.unused
import scala.collection.mutable

/** Unit Tests For ParsingContext */
trait ParsingContext extends Terminals {

  private val stack: InputStack = InputStack()

  protected val errors: mutable.ListBuffer[Messages.Message] = mutable
    .ListBuffer.empty[Messages.Message]

  @inline def current: RiddlParserInput = { stack.current }
  @inline def push(path: Path): Unit = { stack.push(path) }
  @inline def push(rpi: RiddlParserInput): Unit = { stack.push(rpi) }
  @inline def pop: RiddlParserInput = {
    val rpi = stack.pop
    filesSeen.append(rpi)
    rpi
  }
  private val filesSeen: mutable.ListBuffer[RiddlParserInput] = mutable
    .ListBuffer.empty[RiddlParserInput]

  def inputSeen: Seq[RiddlParserInput] = filesSeen.toSeq

  def location[u: P]: P[Location] = {
    P(Index.map(idx => current.location(idx)))
  }

  def doImport(
    loc: Location,
    domainName: Identifier,
    fileName: LiteralString
  ): Domain = {
    val name = fileName.s
    val file = new File(current.root, name)
    if (!file.exists()) {
      error(s"File '$name` does not exist, can't be imported.")
      Domain(loc, domainName)
    } else { importDomain(file) }
  }

  def importDomain(
    @unused file: File
  ): Domain = {
    // TODO: implement importDomain
    Domain(Location(), Identifier(Location(), "NotImplemented"))
  }

  def doInclude[T <: Definition](
    str: LiteralString
  )(rule: P[?] => P[Seq[T]]
  ): Include[T] = {
    val name = str.s + ".riddl"
    val path = current.root.toPath.resolve(name)
    if (Files.exists(path) && !Files.isHidden(path)) {
      if (Files.isReadable(path)) {
        push(path)
        try {
          this.expect[Seq[T]](rule) match {
            case Left(theErrors) =>
              theErrors.filterNot(errors.contains).foreach(errors.append)
              Include[T](str.loc, Seq.empty[T], Some(path))
            case Right((parseResult, _)) =>
              Include[T](str.loc, parseResult, Some(path))
          }
        } finally { pop }
      } else {
        error(
          str.loc,
          s"File '$name' exits but can't be read, so it can't be included."
        )
        Include[T](str.loc, Seq.empty[T], Some(path))
      }
    } else {
      error(str.loc, s"File '$name' does not exist, so it can't be included.")
      Include[T](str.loc, Seq.empty[T], Some(path))
    }
  }

  def error(message: String): Unit = {
    val msg = Messages.Message(Location.empty(current), message, Messages.Error)
    errors.append(msg)
  }
  def error(loc: Location, message: String, context: String = ""): Unit = {
    val msg = Messages.Message(loc, message, Messages.Error, context)
    errors.append(msg)
  }

  private def mkTerminals(list: List[Lazy[String]]): String = {
    list.map(_.force).map {
      case s: String if s.startsWith("char-pred")  => "pattern"
      case s: String if s.startsWith("chars-with") => "pattern"
      case s: String                               => s
    }.distinct.mkString("(", " | ", ")")
  }

  def makeParseFailureError(failure: Failure): Unit = {
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

  def expect[T](
    parser: P[?] => P[T]
  ): Either[Messages, (T, RiddlParserInput)] = {
    val input = current
    fastparse.parse(input, parser(_)) match {
      case Success(content, _) =>
        if (errors.nonEmpty) { Left(errors.toList) }
        else { Right(content -> input) }
      case failure: Failure =>
        makeParseFailureError(failure)
        Left(errors.toList)
      case _ => throw new IllegalStateException(
          "Parsed[T] should have matched Success or Failure"
        )
    }
  }
}
