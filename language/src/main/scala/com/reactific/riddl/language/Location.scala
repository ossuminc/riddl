package com.reactific.riddl.language

import scala.language.implicitConversions

/** A location of an item in the input */
case class Location(
  line: Int = 0,
  col: Int = 0,
  source: String = Location.defaultSourceName)
    extends Ordered[Location] {
  override def toString: String = { s"$source$toShort" }
  def toShort: String = { s"($line:$col)"}

  override def compare(that: Location): Int = {
    if (that.line == line) {
      if (this.col == that.col) { this.source.compare(that.source) }
      else { this.col - that.col }
    } else { this.line - that.line }
  }
}

object Location {
  val empty: Location = Location()
  final val defaultSourceName = "default"

  implicit def apply(line: Int): Location = { Location(line, 0, defaultSourceName) }

  implicit def apply(
    pair: (Int, Int)
  ): Location = { Location(pair._1, pair._2, defaultSourceName) }

  implicit def apply(triple: (Int, Int, String)): Location = {
    Location(triple._1, triple._2, triple._3)
  }
}
