/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.RiddlParserInput

import scala.annotation.targetName
import scala.language.implicitConversions
import scala.scalajs.js.annotation.JSExportTopLevel

/** A location of an item in the input
  * @param source
  *   The [[parsing.RiddlParserInput]] instance from which the location as derived
  * @param offset
  *   The offset in that file/stream the defines the location
  */
@JSExportTopLevel("At", "Language")
case class At(source: RiddlParserInput, offset: Int = 0) extends Ordered[At] {

  def isEmpty: Boolean = offset == 0 && source == RiddlParserInput.empty

  lazy val line: Int = source.lineOf(offset) + 1
  lazy val col: Int = offset - source.offsetOf(line - 1) + 1

  @inline override def toString: String = { source.origin + toShort }
  @inline def toShort: String = { s"($line:$col)" }

  override def compare(that: At): Int = {
    if this.source.origin == that.source.origin then { this.offset - that.offset }
    else { this.source.origin.compare(that.source.origin) }
  }

  @targetName("plus")
  def +(int: Int): At = At(source, offset + int)

  override def equals(obj: Any): Boolean = {
    if obj.getClass != classOf[At] then { false }
    else {
      val that = obj.asInstanceOf[At]
      if this.offset != that.offset then { false }
      else { this.source.origin == that.source.origin }
    }
  }
}

object At {
  val empty: At = At(RiddlParserInput.empty)
  def empty(input: RiddlParserInput): At = { At(input) }
  final val defaultSourceName = RiddlParserInput.empty.origin

  /** Empty constructor for [[At]] */
  implicit def apply(): At = { At(RiddlParserInput.empty) }

  /** Empty constructor at start of a line for the empty [[At]] */
  implicit def apply(line: Int): At = { At(RiddlParserInput.empty, line) }

  /** Start of line constructor for a specific [[At]] */
  implicit def apply(line: Int, src: RiddlParserInput): At = {
    src.location(src.offsetOf(line))
  }

  /** (line, col) constructor of [[At]] for the empty [[parsing.RiddlParserInput]] */
  implicit def apply(
    pair: (Int, Int)
  ): At = { apply(pair, RiddlParserInput.empty) }

  /** (line, col) constructor of [[At]] for as specific [[parsing.RiddlParserInput]] */
  implicit def apply(
    pair: (Int, Int),
    src: RiddlParserInput
  ): At = { apply(pair._1, pair._2, src) }

  /** (line, col, input) constructor of [[At]] for a specific triple */
  implicit def apply(
    triple: (Int, Int, RiddlParserInput)
  ): At = { apply(triple._1, triple._2, triple._3) }

  /**
    * General constructor of [[At]] providing all three values
    * @param line
    *   The line number in the input for this [[At]]
    * @param col
    *   The column number in the input for this [[At]]
    * @param src
    *   The [[parsing.RiddlParserInput]] for this [[At]]
    * @return
    */
  implicit def apply(
    line: Int,
    col: Int,
    src: RiddlParserInput
  ): At = {
    val offset = src.offsetOf(line - 1) + col - 1
    src.location(offset)
  }
}
