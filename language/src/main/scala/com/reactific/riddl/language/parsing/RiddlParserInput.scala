/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.ast.At
import fastparse.ParserInput
import fastparse.internal.Util

import java.io.File
import java.net.URL
import java.nio.file.Path
import scala.collection.Searching
import scala.io.Source
import scala.language.implicitConversions

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

  private lazy val lineNumberLookup: Array[Int] = Util.lineNumberLookup(data)

  private[language] def offsetOf(line: Int): Int = {
    if (line < 0) { lineNumberLookup(line) }
    else if (line < lineNumberLookup.length) { lineNumberLookup(line) }
    else { lineNumberLookup(lineNumberLookup.length - 1) }
  }

  private[language] def lineOf(index: Int): Int = {
    val result = lineNumberLookup.search(index)
    result match {
      case Searching.Found(foundIndex) => foundIndex
      case Searching.InsertionPoint(insertionPoint) =>
        if (insertionPoint > 0) insertionPoint - 1 else insertionPoint
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
      if (lineNumberLookup.length == 1) { data.length }
      else if (loc.line >= lineNumberLookup.length) {
        // actually out of bounds but go to last line
        lineNumberLookup(lineNumberLookup.length - 1)
      } else { lineNumberLookup(loc.line) }
    start -> end
  }

  @inline final def location(index: Int): At = { At(this, index) }

  def prettyIndex(index: Int): String = { location(index).toString }

  val nl: String = System.getProperty("line.separator")

  def annotateErrorLine(index: At): String = {
    if (index.source.nonEmpty) {
      val (start, end) = rangeOf(index)
      val quoted = slice(start, end).stripTrailing()
      if (quoted.isEmpty) ""
      else {
        val col = index.col - 1
        quoted + nl + " ".repeat(col) + "^" + nl
      }
    } else ""
  }
}

private case class EmptyParserInput() extends RiddlParserInput {
  override def origin: String = "empty"

  override def data: String = ""

  override def root: File = File.listRoots().head

  override def offsetOf(line: Int): Int = { line * 80 }
  override def lineOf(offset: Int): Int = { offset / 80 }
}

case class StringParserInput(
  data: String,
  origin: String = At.defaultSourceName)
    extends RiddlParserInput {
  val root: File = new File(System.getProperty("user.dir"))
  override def isEmpty: Boolean = data.isEmpty
}

case class FileParserInput(file: File) extends RiddlParserInput {

  lazy val data: String = {
    val source: Source = Source.fromFile(file)
    try { source.getLines().mkString("\n") }
    finally { source.close() }
  }
  override def isEmpty: Boolean = data.isEmpty
  val root: File = file.getParentFile
  def origin: String = file.getName
  def this(path: Path) = this(path.toFile)
}

case class URLParserInput(url: URL) extends RiddlParserInput {
  require(url.getProtocol.startsWith("http"), s"Non-http URL protocol '${url.getProtocol}``")
  lazy val data: String = {
    val source: Source = Source.fromURL(url)
    try { source.getLines().mkString("\n") }
    finally { source.close() }
  }
  override def isEmpty: Boolean = data.isEmpty
  val root: File = new File(url.getFile)
  def origin: String = url.toString
}

case class SourceParserInput(source: Source, origin: String)
    extends RiddlParserInput {

  lazy val data: String =
    try { source.mkString }
    finally { source.close() }
  val root: File = new File(System.getProperty("user.dir"))
}

object RiddlParserInput {

  val empty: RiddlParserInput = EmptyParserInput()

  implicit def apply(
    data: String
  ): RiddlParserInput = { StringParserInput(data) }

  implicit def apply(source: Source): RiddlParserInput = {
    SourceParserInput(source, source.descr)
  }
  implicit def apply(file: File): RiddlParserInput = { FileParserInput(file) }

  implicit def apply(path: Path): RiddlParserInput = {
    FileParserInput(path.toFile)
  }

  implicit def apply(url: URL): RiddlParserInput = URLParserInput(url)
}
