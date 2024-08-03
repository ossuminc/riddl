/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.{Path, URL, Loader}
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
  implicit def apply(data: String, origin: URL): RiddlParserInput = {
    StringParserInput(data, origin)
  }

  /** Set up a parser input for parsing a test case from a String and the testCaseName.
    * @param data
    *   The data to be parsed and returned in a RiddlParserInput
    * @param testCaseName
    *   A string, typically derived by the scalatest TestData extension in ParserTest.
    */
  @JSExport("createTestInput")
  implicit def apply(data: String, testCaseName: String = "unspecified test case"): RiddlParserInput = {
    StringParserInput(data, URL(testCaseName))
  }

  /** Set up a parser input from a [[com.ossuminc.riddl.utils.URL]].
    *
    * @param url
    *   The url from which to load the data
    * @return
    *   A Future[RiddlParserInput] with the RPI set up to load data from the provided url
    */
  @JSExport
  def rpiFromURL(url: URL): Future[RiddlParserInput] = {
    Loader(url).load.map(data => apply(data,url))
  }

  /** Set up a parser input for parsing directly from a Scala Source
    *
    * @param source
    *   The Source from which UTF-8 text will be read and parsed
    * @note
    *   JVM Only
    */
  def rpiFromSource(source: scala.io.Source): RiddlParserInput = {
    val data: String =
      try {
        source.getLines().mkString("\n")
      } finally {
        source.close()
      }
    StringParserInput(data, URL(source.descr))
  }

  /** Set up a parser input for parsing directly from a Java File
    *
    * @param file
    *   The java.io.File from which UTF-8 text will be read and parsed.
    * @note
    *   JVM Only
    */
  def rpiFromFile(file: java.io.File): RiddlParserInput = {
    val data: String = {
      val source: scala.io.Source = scala.io.Source.fromFile(file)
      try {
        source.getLines().mkString("\n")
      } finally {
        source.close()
      }
    }
    StringParserInput(data, URL(file.toURI.toURL.toString))
  }

  /** Set up a parser input for parsing directly from a file at a specific Path
    *
    * @param path
    *   The java.nio.path.Path from which UTF-8 text will be read and parsed.
    * @note
    *   JVM Only
    */
  def rpiFromPath(path: java.nio.file.Path): RiddlParserInput = rpiFromFile(path.toFile)

}

/** Same as fastparse.IndexedParserInput but with Location support */
abstract class RiddlParserInput extends ParserInput {
  def root: URL
  def data: String
  def origin: String = root.getFile
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
  override def root: URL = URL("/")
  override def offsetOf(line: Int): Int = { line * 80 }
  override def lineOf(offset: Int): Int = { offset / 80 }
  def data: String = ""
  def from: String = ""
}

protected[parsing] case class StringParserInput(
  data: String,
  root: URL
) extends RiddlParserInput {
  def from: String = root.getFile
}
