/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.{Path,URL}
import fastparse.ParserInput
import fastparse.internal.Util

import java.io.File
import scala.collection.Searching
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.util.{Try, Success, Failure}
import scala.scalajs.js.annotation.*

/** Primary interface to setting up a RIDDL Parser's input. The idea here is to use one of the apply methods in this
  * companion object to construct a RiddlParserInput for a specific input source (file, path, Source, data string, URL,
  * etc.)
  */
@JSExportTopLevel("RiddlParserInput")
object RiddlParserInput {

  val empty: RiddlParserInput = EmptyParserInput

  /** Set up a parser input for parsing directly from a String
    * @#param
    *   data The UTF-8 string to be parsed
    */
  @JSExport("createWith")
  implicit def apply(data: String): RiddlParserInput = {
    StringParserInput(data)
  }

  @JSExport("createWith")
  implicit def apply(data: String, origin: String): RiddlParserInput = {
    StringParserInput(data, origin)
  }

  @JSExport("createWith")
  implicit def apply(path: Path): RiddlParserInput = {
    val source = Source.fromFile(path.path)
    apply(source)
  }

  /** Set up a parser input for parsing directly from a Scala Source
    * @param source
    *   The Source from which UTF-8 text will be read and parsed
    */
  implicit def apply(source: Source): RiddlParserInput = {
    val data: String =
      try {
        source.getLines().mkString("\n")
      } finally {
        source.close()
      }
    StringParserInput(data, source.descr)
  }

  /** Set up a parser input for parsing directly from a Java File
    * @param file
    *   The java.io.File from which UTF-8 text will be read and parsed.
    */
  implicit def apply(file: File): RiddlParserInput = {
    val data: String = {
      val source: Source = Source.fromFile(file)
      try {
        source.getLines().mkString("\n")
      } finally {
        source.close()
      }
    }
    StringParserInput(data, file.getName)
  }

  /** Set up a parser input for parsing directly from a file at a specific Path
    * @param path
    *   THe java.nio.path.Path from which UTF-8 text will be read and parsed.
    */
  implicit def apply(path: java.nio.file.Path): RiddlParserInput = apply(path.toFile)

  /** Set up a parser input for parsing directly from a file at a specific URL
    * @param url
    *   The java.net.URL from which UTF-8 text will be read and parsed.
    */
//  implicit def apply(url: java.net.URL): RiddlParserInput = {
//    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
//    url.
//    val data: Future[String] = URL.load(url).map(_.mkString("\n"))
//    StringParserInput(data, )
//  }
}

/** Same as fastparse.IndexedParserInput but with Location support */
abstract class RiddlParserInput extends ParserInput {
  def origin: String
  def data: String
  def root: File

  override def apply(index: Int): Char = data.charAt(index)
  override def dropBuffer(index: Int): Unit = {}
  override def slice(from: Int, until: Int): String = data.slice(from, until)
  override def length: Int = data.length
  override def innerLength: Int = length
  override def isReachable(index: Int): Boolean = index < length

  def checkTraceable(): Unit = ()
  inline final def isEmpty: Boolean = data.isEmpty
  inline final def nonEmpty: Boolean = !isEmpty
  def from: String
  def path: Option[Path] = None

  private lazy val lineNumberLookup: Array[Int] = Util.lineNumberLookup(data)

  private[language] def offsetOf(line: Int): Int = {
    if line < 0 then { lineNumberLookup(line) }
    else if line < lineNumberLookup.length then { lineNumberLookup(line) }
    else { lineNumberLookup(lineNumberLookup.length - 1) }
  }

  private[language] def lineOf(index: Int): Int = {
    val result = lineNumberLookup.search(index)
    result match {
      case Searching.Found(foundIndex) => foundIndex
      case Searching.InsertionPoint(insertionPoint) =>
        if insertionPoint > 0 then insertionPoint - 1 else insertionPoint
    }
  }

  def rangeOf(index: Int): (Int, Int) = {
    val line = lineOf(index)
    val start = lineNumberLookup(line)
    val end = lineNumberLookup(line + 1)
    start -> end
  }

  def rangeOf(loc: At): (Int, Int) = {
    require(loc.line > 0)
    val start = lineNumberLookup(loc.line - 1)
    val end =
      if lineNumberLookup.length == 1 then { data.length }
      else if loc.line >= lineNumberLookup.length then {
        // actually out of bounds but go to last line
        lineNumberLookup(lineNumberLookup.length - 1)
      } else { lineNumberLookup(loc.line) }
    start -> end
  }

  @inline final def location(index: Int): At = { At(this, index) }

  def prettyIndex(index: Int): String = { location(index).toString }

  val nl: String = System.getProperty("line.separator")

  def annotateErrorLine(index: At): String = {
    if index.source.nonEmpty then {
      val (start, end) = rangeOf(index)
      val quoted = slice(start, end).stripTrailing()
      if quoted.isEmpty then ""
      else {
        val col = index.col - 1
        quoted + nl + " ".repeat(col) + "^" + nl
      }
    } else ""
  }
}

@JSExportTopLevel("EmptyParserInput")
case object EmptyParserInput extends RiddlParserInput {
  override def origin: String = "empty"
  override def root: File = File.listRoots().head
  override def offsetOf(line: Int): Int = { line * 80 }
  override def lineOf(offset: Int): Int = { offset / 80 }
  def data: String = ""
  def from: String = ""
}

private[parsing] case class StringParserInput(
  data: String,
  origin: String = At.defaultSourceName
) extends RiddlParserInput {
  val root: File = new File(System.getProperty("user.dir"))
  def from: String = origin
}

private[parsing] case class FutureParserInput(
  future: Future[String],
  origin: String = At.defaultSourceName
) extends RiddlParserInput {
  var data: String = ""
  def from: String = origin
  def root: java.io.File = new File("")
  def map[T](process: RiddlParserInput => T): Future[T] = {
    future.map { delayed_data => process(this) }
  }
}
