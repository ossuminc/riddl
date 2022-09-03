/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
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

case class ParserError(
  input: RiddlParserInput,
  loc: Location,
  msg: String,
  context: String = "")
    extends Throwable {

  def format: String = {
    if (loc.isEmpty) {
      s"Error: ${input.origin}: $msg${if (context.nonEmpty) {
        "\nContext: " + context
      }}"

    } else {
      val errorLine = input.annotateErrorLine(loc)
      s"Error: $loc: $msg but got:\n$errorLine${if (context.nonEmpty) { " Context: " + context }}"
    }
  }
}

/** Unit Tests For ParsingContext */
trait ParsingContext {

  private val stack: InputStack = InputStack()

  protected val errors: mutable.ListBuffer[ParserError] = mutable.ListBuffer
    .empty[ParserError]

  @inline
  def current: RiddlParserInput = { stack.current }
  @inline
  def push(path: Path): Unit = { stack.push(path) }
  @inline
  def push(rpi: RiddlParserInput): Unit = { stack.push(rpi) }
  @inline
  def pop: RiddlParserInput = {
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
    @unused
    file: File
  ): Domain = {
    // TODO: implement importDomain
    Domain(Location(), Identifier(Location(), "NotImplemented"))
  }

  def doInclude[T <: Definition](
    str: LiteralString
  )(rule: P[?] => P[Seq[T]]
  ): Include = {
    val name = str.s + ".riddl"
    val path = current.root.toPath.resolve(name)
    if (Files.exists(path) && !Files.isHidden(path)) {
      if (Files.isReadable(path)) {
        push(path)
        try {
          this.expect[Seq[T]](rule) match {
            case Left(theErrors) =>
              theErrors.filterNot(errors.contains).foreach(errors.append)
              Include(str.loc, Seq.empty[T], Some(path))
            case Right((parseResult, _)) =>
              Include(str.loc, parseResult, Some(path))
          }
        } finally { pop }
      } else {
        error(
          str.loc,
          s"File '$name' exits but can't be read, so it can't be included."
        )
        Include(str.loc, Seq.empty[Definition], Some(path))
      }
    } else {
      error(str.loc, s"File '$name' does not exist, so it can't be included.")
      Include(str.loc, Seq.empty[Definition], Some(path))
    }
  }

  def error(msg: String): Unit = {
    val error = ParserError(current, Location.empty, msg)
    errors.append(error)
  }
  def error(loc: Location, msg: String, context: String = ""): Unit = {
    val error = ParserError(current, loc, msg, context)
    errors.append(error)
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
  ): Either[Seq[ParserError], (T, RiddlParserInput)] = {
    val input = current
    fastparse.parse(input, parser(_)) match {
      case Success(content, _) =>
        if (errors.nonEmpty) { Left(errors.toSeq) }
        else { Right(content -> input) }
      case failure: Failure =>
        makeParseFailureError(failure)
        Left(errors.toSeq)
      case _ => throw new IllegalStateException(
          "Parsed[T] should have matched Success or Failure"
        )
    }
  }
}
