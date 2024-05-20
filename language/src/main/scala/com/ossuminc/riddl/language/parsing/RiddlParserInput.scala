/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing
import com.ossuminc.riddl.language.At
import fastparse.ParserInput
import fastparse.internal.Util

import java.io.File
import java.net.{URI, URL}
import java.nio.file.Path
import scala.collection.Searching
import scala.io.Source
import scala.language.implicitConversions

/** Primary interface to setting up a RIDDL Parser's input. The idea here is to use one of the apply methods in this
  * companion object to construct a RiddlParserInput for a specific input source (file, path, Source, data string, URL,
  * etc.)
  */
object RiddlParserInput {

  val empty: RiddlParserInput = EmptyParserInput()

  /** Set up a parser input for parsing directly from a String
    * @#param
    *   data The UTF-8 string to be parsed
    */
  implicit def apply(data: String): RiddlParserInput = {
    StringParserInput(data)
  }

  implicit def apply(data: String, origin: String): RiddlParserInput = {
    StringParserInput(data, origin)
  }

  /** Set up a parser input for parsing directly from a Scala Source
    * @param source
    *   The Source from which UTF-8 text will be read and parsed
    */
  implicit def apply(source: Source): RiddlParserInput = {
    SourceParserInput(source, source.descr)
  }

  /** Set up a parser input for parsing directly from a Java File
    * @param file
    *   The java.io.File from which UTF-8 text will be read and parsed.
    */
  implicit def apply(file: File): RiddlParserInput = {
    FileParserInput(file)
  }

  /** Set up a parser input for parsing directly from a file at a specific Path
    * @param path
    *   THe java.nio.path.Path from which UTF-8 text will be read and parsed.
    */
  implicit def apply(path: Path): RiddlParserInput = {
    FileParserInput(path.toFile)
  }

  /** Set up a parser input for parsing directly from a file at a specific URL
    * @param url
    *   The java.net.URL from which UTF-8 text will be read and parsed.
    */
  implicit def apply(url: URL): RiddlParserInput = URLParserInput(url)

  /** Set up a parser input for parsing directly from a file at a specific URI
    * @param uri
    *   The java.net.URI from which UTF-8 text will be read and parsed.
    */
  implicit def apply(uri: URI): RiddlParserInput = URIParserInput(uri)
}

/** Same as fastparse.IndexedParserInput but with Location support */
abstract class RiddlParserInput extends ParserInput {
  def origin: String
  def data: String
  def root: File

  def isEmpty: Boolean = { data.isEmpty }
  final def nonEmpty: Boolean = !isEmpty
  override def apply(index: Int): Char = data.charAt(index)
  override def dropBuffer(index: Int): Unit = {}
  override def slice(from: Int, until: Int): String = data.slice(from, until)
  override def length: Int = data.length
  override def innerLength: Int = length
  override def isReachable(index: Int): Boolean = index < length

  def checkTraceable(): Unit = ()

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

case class EmptyParserInput() extends RiddlParserInput {
  override def origin: String = "empty"

  override def data: String = ""

  override def root: File = File.listRoots().head

  override def offsetOf(line: Int): Int = { line * 80 }
  override def lineOf(offset: Int): Int = { offset / 80 }

  def from: String = ""
}

private[parsing] case class StringParserInput(
  data: String,
  origin: String = At.defaultSourceName
) extends RiddlParserInput {
  val root: File = new File(System.getProperty("user.dir"))
  override def isEmpty: Boolean = data.isEmpty
  def from: String = origin
}

private[parsing] case class FileParserInput(file: File) extends RiddlParserInput {

  lazy val data: String = {
    val source: Source = Source.fromFile(file)
    try { source.getLines().mkString("\n") }
    finally { source.close() }
  }
  override def isEmpty: Boolean = data.isEmpty
  val root: File = file.getParentFile
  def origin: String = file.getName

  override def from: String = {
    val path = file.getAbsolutePath
    val index = path.lastIndexOf("riddl/")
    path.substring(index + 6)
  }

  override def path: Option[Path] = {
    Some(file.getAbsoluteFile.toPath)
  }
}

private[parsing] case class URIParserInput(uri: URI) extends RiddlParserInput {
  lazy val data: String = {
    val source: Source = Source.fromURL(uri.toURL)
    try { source.getLines().mkString("\n") }
    finally { source.close() }
  }
  override def isEmpty: Boolean = data.isEmpty
  val root: File = new File(uri.getPath)
  def origin: String = uri.toString
  def from: String = uri.toString
  override def path: Option[Path] = {
    Some(root.toPath)
  }
}

private[parsing] case class URLParserInput(url: URL) extends RiddlParserInput {
  // require(url.getProtocol.startsWith("http"), s"Non-http URL protocol '${url.getProtocol}``")
  lazy val data: String = {
    val source: Source = Source.fromURL(url)
    try {
      source.getLines().mkString("\n")
    } finally { source.close() }
  }
  assert(data.nonEmpty, s"Empty content from ${url.toExternalForm}")
  override def isEmpty: Boolean = data.isEmpty
  val root: File = new File(url.getFile)
  def origin: String = url.toString
  def from: String = url.toString
  override def path: Option[Path] = Some(root.toPath)
}

private[parsing] case class SourceParserInput(source: Source, origin: String) extends RiddlParserInput {

  lazy val data: String =
    try { source.mkString }
    finally { source.close() }
  val root: File = new File(System.getProperty("user.dir"))

  def from: String = source.descr
  override def path: Option[Path] = Some(root.toPath)

}
