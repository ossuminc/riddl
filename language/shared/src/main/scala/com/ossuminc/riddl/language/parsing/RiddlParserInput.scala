/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.{pc, Await, PlatformContext, URL}
import fastparse.ParserInput
import fastparse.internal.Util

import scala.collection.Searching
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scala.scalajs.js.annotation.*
import scala.io.AnsiColor.{BOLD, RESET}

/** Primary interface to setting up a RIDDL Parser's input. The idea here is to use one of the apply methods in this
  * companion object to construct a RiddlParserInput for a specific input source (file, path, Source, data string, URL,
  * etc.)
  */
@JSExportTopLevel("RiddlParserInput")
object RiddlParserInput {

  val empty: RiddlParserInput = EmptyParserInput

  /** Set up a parser input for parsing directly from a String
    * @param data
    *   data The UTF-8 string to be parsed
    * @param root
    *   The URL from which the input data was derived
    * @param purpose
    *   The purpose for which this data is provided, often the test case name
    */
  @JSExport("createWith")
  implicit def apply(data: String, root: URL, purpose: String = ""): RiddlParserInput = {
    StringParserInput(data, root, purpose)
  }

  /** Set up a parser input for parsing a test case from a String and the testCaseName.
    * @param data
    *   The data to be parsed and returned in a RiddlParserInput
    * @param purpose
    *   A string, typically derived by the scalatest TestData extension in ParserTest.
    */
  @JSExport("createTestInput")
  implicit def apply(data: String, purpose: String): RiddlParserInput = {
    StringParserInput(data, URL.empty, purpose)
  }

  @JSExport("createFromTuple2")
  implicit def apply(data: (String, String)): RiddlParserInput = {
    StringParserInput(data._1, URL.empty, data._2)
  }

  /** Set up a parser input from a [[com.ossuminc.riddl.utils.URL]].
    *
    * @param url
    *   The url from which to load the data
    * @return
    *   A Future[RiddlParserInput] with the RPI set up to load data from the provided url
    */
  def fromURL(url: URL, purpose: String = "")(using io: PlatformContext): Future[RiddlParserInput] = {
    implicit val ec: ExecutionContext = io.ec
    io.load(url).map(data => apply(data, url, purpose))
  }

  def fromPath(path: String, purpose: String = "")(using PlatformContext): Future[RiddlParserInput] = {
    assert(path.nonEmpty, "Path provided to RiddlParserInput.fromPath is empty")
    val url: URL = if path.head == '/' then URL.fromFullPath(path) else URL.fromCwdPath(path)
    fromURL(url, purpose)
  }
}

/** This class provides the loaded data for fastparse to parse. It is the same as fastparse.IndexedParserInput but adds
  * support for file locations with [[At]]. The class is abstract because
  */
abstract class RiddlParserInput extends ParserInput {

  /** The data that will be parsed by fastparse */
  def data: String

  /** The URL from which the [[data]] originated. If it didn't originate from a network or file location, then this
    * should be empty, URL("") so that URL validity checking will be skipped.
    */
  def root: URL

  /** The short origin name to use in error messages as the origin of the error. In test cases that do not use a URL,
    * this should be overridden with the word "empty"
    * @return
    *   Typically the last filename in the URL is sufficient, and that is the default calculated from [[root]].
    */
  def origin: String = if root.isEmpty then "empty" else root.path

  /** The purpose of this parsing input. It could be a test name or blank for normal usage */
  def purpose: String = ""

  override inline def apply(index: Int): Char = data.charAt(index)
  override inline def dropBuffer(index: Int): Unit = {}
  override inline def slice(from: Int, until: Int): String = data.slice(from, until)
  override inline def length: Int = data.length
  override inline def innerLength: Int = length
  override inline def isReachable(index: Int): Boolean = index < length

  def checkTraceable(): Unit = ()

  inline final def isEmpty: Boolean = data.isEmpty
  inline final def nonEmpty: Boolean = !isEmpty

  private lazy val lineNumberLookup: Array[Int] = 
    Util.lineNumberLookup(data).appended(data.length)

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

  private def endOfLineFrom(offset: Int): Int = {
    require(offset <= data.length)
    val line = lineOf(offset)
    lineNumberLookup(line) - 1
  }

  def lineRangeOf(loc: At): (Int, Int) = {
    require(loc.source == this)
    require(loc.offset >= 0)
    require(loc.offset <= loc.endOffset)
    val startLine = lineOf(loc.offset)
    val endLine = lineOf(loc.endOffset) + 1

    require(loc.line > 0)
    val start = lineNumberLookup(startLine)
    val end =
      if lineNumberLookup.length == 1 then { data.length }
      else if endLine >= lineNumberLookup.length then {
        // actually out of bounds but go to last line
        lineNumberLookup(lineNumberLookup.length - 1)
      } else { lineNumberLookup(endLine) }
    start -> end
  }

  @inline final def location(index: Int): At = {
    At(this, index, index + 1)
  }

  @inline final def at(start: Int, end: Int): At = { At(this, start, end) }

  def prettyIndex(index: Int): String = { location(index).toString }

  val nl: String = System.getProperty("line.separator")

  def annotateErrorLine(index: At): String = {
    require(index.source == this)
    require(index.offset >= 0 && index.offset <= data.length, 
      s"${index.offset}>=0 && ${index.offset} <= ${data.length}")
    require(index.endOffset >= 0 && index.endOffset <= data.length)
    if index.source.nonEmpty then {
      val (start, end) = lineRangeOf(index)
      require( start <= index.offset, s"fail: $start <= ${index.offset}")
      require( end >= index.endOffset, s"fail: $end >= ${index.endOffset}")
      val quoted = slice(start, end)
      if quoted.isEmpty then ""
      else {
        if pc.options.noANSIMessages then
          quoted
        else
          val prefixStart = offsetOf(lineOf(start))
          val prefixEnd = Math.max(0, index.offset)
          val errorStart = index.offset
          val errorEnd = Math.min(Math.min(index.endOffset,end),data.length)
          val suffixStart = Math.min(errorEnd, index.source.length)
          val suffixEnd = Math.max(suffixStart, Math.min(endOfLineFrom(end), index.source.length))
          val prefix = data.substring(prefixStart, prefixEnd)
          val error = data.substring(errorStart, errorEnd)
          val suffix = data.substring(suffixStart, suffixEnd)
          prefix + BOLD + error + RESET + suffix
      }
    } else ""
  }
}

@JSExportTopLevel("EmptyParserInput")
case object EmptyParserInput extends RiddlParserInput {
  override def origin: String = "empty"
  override def root: URL = URL.empty
  override def offsetOf(line: Int): Int = { line * 80 }
  override def lineOf(offset: Int): Int = { offset / 80 }
  def data: String = ""
  def from: String = ""
}

protected[parsing] case class StringParserInput(
  data: String,
  root: URL = URL.empty,
  override val purpose: String = ""
) extends RiddlParserInput
