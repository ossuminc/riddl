package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST.Location
import fastparse.ParserInput
import fastparse.internal.Util

import scala.io.Source

/** Same as fastparse.IndexedParserInput but with Location support */
case class RiddlParserInput(data: String) extends ParserInput {
  override def apply(index: Int): Char = data.charAt(index)
  override def dropBuffer(index: Int): Unit = {}
  override def slice(from: Int, until: Int): String = data.slice(from, until)
  override def length: Int = data.length
  override def innerLength: Int = length
  override def isReachable(index: Int): Boolean = index < length

  def checkTraceable(): Unit = ()

  private[this] lazy val lineNumberLookup = Util.lineNumberLookup(data)

  def location(index: Int): Location = {
    val line = lineNumberLookup.indexWhere(_ > index) match {
      case -1 => lineNumberLookup.length - 1
      case n  => math.max(0, n - 1)
    }
    val col = index - lineNumberLookup(line)
    Location(line + 1, col + 1)
  }

  def prettyIndex(index: Int): String = {
    location(index).toString
  }
}

object RiddlParserInput {
  implicit def apply(data: String): RiddlParserInput = {
    new RiddlParserInput(data)
  }
  implicit def apply(source: Source): RiddlParserInput = {
    val lines = source.getLines()
    val input = lines.mkString("\n")
    RiddlParserInput(input)
  }
  implicit def apply(file: File): RiddlParserInput = {
    val source = Source.fromFile(file)
    RiddlParserInput(source)
  }
}
