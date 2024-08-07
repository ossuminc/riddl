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
  @JSExport
  def rpiFromURL(url: URL, purpose: String = ""): Future[RiddlParserInput] = {
    Loader(url).load.map(data => apply(data, url, purpose))
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
    StringParserInput(data, URL.empty, source.descr)
  }

  /** Set up a parser input for parsing directly from a Java File
    *
    * @param file
    *   The java.io.File from which UTF-8 text will be read and parsed.
    * @note
    *   JVM Only
    */
  def rpiFromFile(file: java.io.File, purpose: String = ""): RiddlParserInput = {
    val data: String = {
      val source: scala.io.Source = scala.io.Source.fromFile(file)
      try {
        source.getLines().mkString("\n")
      } finally {
        source.close()
      }
    }
    val fileURL = file.toURI.toURL
    val filePath = fileURL.toExternalForm // fix bug in JVM!
    StringParserInput(data, URL(filePath), purpose)
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
  def origin: String = if root.isEmpty then "empty" else root.getFile

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
        val col = Integer.max(index.col - 1, 0)
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
  root: URL = URL.empty,
  override val purpose: String = ""
) extends RiddlParserInput
