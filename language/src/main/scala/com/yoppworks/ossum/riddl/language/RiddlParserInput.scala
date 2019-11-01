package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST.Location
import fastparse.ParserInput
import fastparse.internal.Util

import scala.io.Source

/** Same as fastparse.IndexedParserInput but with Location support */
case class RiddlParserInput(data: String, origin: String) extends ParserInput {
  override def apply(index: Int): Char = data.charAt(index)
  override def dropBuffer(index: Int): Unit = {}
  override def slice(from: Int, until: Int): String = data.slice(from, until)
  override def length: Int = data.length
  override def innerLength: Int = length
  override def isReachable(index: Int): Boolean = index < length

  def checkTraceable(): Unit = ()

  private[this] lazy val lineNumberLookup = Util.lineNumberLookup(data)

  private def lineOf(index: Int): Int = {
    lineNumberLookup.indexWhere(_ > index) match {
      case -1 => 0
      case n  => math.max(0, n - 1)
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
    val end = if (lineNumberLookup.length == 1) {
      data.length
    } else {
      lineNumberLookup(loc.line)
    }
    start -> end
  }

  def location(index: Int): Location = {
    val line = lineOf(index)
    val col = index - lineNumberLookup(line)
    Location(line + 1, col + 1)
  }

  def prettyIndex(index: Int): String = {
    location(index).toString
  }

  val nl: String = System.getProperty("line.separator")

  def annotateErrorLine(index: Location): String = {
    val (start, end) = rangeOf(index)
    val col = index.col - 1
    slice(start, end).stripTrailing() + nl + " ".repeat(col) + "^" + nl
  }
}

object RiddlParserInput {
  implicit def apply(data: String): RiddlParserInput = {
    new RiddlParserInput(data, "internal")
  }
  implicit def apply(source: Source): RiddlParserInput = {
    val lines = source.getLines()
    val input = lines.mkString("\n")
    RiddlParserInput(input, source.descr)
  }
  implicit def apply(file: File): RiddlParserInput = {
    val source = Source.fromFile(file)
    new RiddlParserInput(source.getLines().mkString("\n"), file.getName)
  }
}
