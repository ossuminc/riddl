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

import com.reactific.riddl.language.ast.Location
import fastparse.ParserInput
import fastparse.internal.Util

import java.io.File
import java.nio.file.Path
import scala.collection.Searching
import scala.io.Source
import scala.language.implicitConversions

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

  private lazy val lineNumberLookup: Array[Int] =
    Util.lineNumberLookup(data)

  private[language] def offsetOf(line: Int): Int = {
    if (line < 0) {
      lineNumberLookup(line)
    } else if (line < lineNumberLookup.length) {
      lineNumberLookup(line)
    } else {
      lineNumberLookup(lineNumberLookup.length-1)
    }
  }

  private[language] def lineOf(index: Int): Int = {
    val result = lineNumberLookup.search(index)
    result match {
      case Searching.Found(foundIndex) => foundIndex
      case Searching.InsertionPoint(insertionPoint) =>
        if (insertionPoint > 0) insertionPoint - 1 else insertionPoint
      case _ => 0
    }
  }

  def rangeOf(index: Int): (Int, Int) = {
    val line = lineOf(index)
    val start = lineNumberLookup(line)
    val end = lineNumberLookup(line + 1)
    start -> end
  }

  def rangeOf(loc: Location): (Int, Int) = {
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

  @inline final def location(index: Int): Location = { Location(this,index) }

  def prettyIndex(index: Int): String = { location(index).toString }

  val nl: String = System.getProperty("line.separator")

  def annotateErrorLine(index: Location): String = {
    val (start, end) = rangeOf(index)
    val col = index.col - 1
    slice(start, end).stripTrailing() + nl + " ".repeat(col) + "^" + nl
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
  origin: String = Location.defaultSourceName)
    extends RiddlParserInput {
  val root: File = new File(System.getProperty("user.dir"))
}

case class FileParserInput(file: File) extends RiddlParserInput {

  val data: String = {
    val source: Source = Source.fromFile(file)
    try { source.getLines().mkString("\n") }
    finally { source.close() }
  }
  val root: File = file.getParentFile
  def origin: String = file.getName
  def this(path: Path) = this(path.toFile)
}

case class SourceParserInput(source: Source, origin: String) extends RiddlParserInput {

  val data: String =
    try { source.mkString }
    finally { source.close() }
  val root: File = new File(System.getProperty("user.dir"))
}

object RiddlParserInput {

  val empty: RiddlParserInput = EmptyParserInput()

  implicit def apply(
    data: String
  ): RiddlParserInput = { StringParserInput(data) }

  implicit def apply(source: Source): RiddlParserInput = { SourceParserInput(source, source.descr) }
  implicit def apply(file: File): RiddlParserInput = { FileParserInput(file) }

  implicit def apply(path: Path): RiddlParserInput = { FileParserInput(path.toFile) }
}
