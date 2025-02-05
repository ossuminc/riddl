/*
 * Copyright 2019-2025 Ossum, Inc.
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
  *   The offset in the `source` that defines the starting location
  * @param endOffset
  *   The offset in the `source` that defines the end of the location
  */
@JSExportTopLevel("At")
case class At(source: RiddlParserInput, offset: Int = 0, endOffset: Int = 0) extends Ordered[At] {

  require(
    offset <= endOffset,
    s"Location must have a non-negative length. offset=$offset, endOffset=$endOffset, length=$length"
  )

  def length: Int = endOffset - offset

  import scala.scalajs.js.annotation.JSExport

  @JSExport
  def isEmpty: Boolean = offset == 0 && endOffset == 0 && source == RiddlParserInput.empty

  @JSExport
  @inline def line: Int = source.lineOf(offset) + 1

  @JSExport
  @inline def endLine: Int = source.lineOf(endOffset) + 1

  @JSExport
  @inline def col: Int = offset - source.offsetOf(line - 1) + 1

  @JSExport
  @inline override def toString: String = { source.origin + toShort }

  @JSExport
  @inline def toShort: String = { s"($offset->$endOffset)" }

  @JSExport
  @inline def toLong: String = {
    val sLine = line
    val eLine = endLine
    val endCol = endOffset - source.offsetOf(endLine - 1) + 1
    if sLine == eLine then s"($sLine:$col->$endCol)"
    else s"($sLine:$col->$eLine:$endCol)"
  }

  @JSExport
  @inline def format: String = { source.origin + toLong }

  @JSExport
  override def compare(that: At): Int = {
    val thisRoot = this.source.root.toExternalForm
    val thatRoot = that.source.root.toExternalForm
    if thisRoot == thatRoot then this.offset - that.offset
    else thisRoot.compare(thatRoot)
  }

  @targetName("plus")
  @JSExport
  def +(int: Int): At = At(source, offset + int, endOffset + int)

  /** Extend the length of this At
    *
    * @param extent
    *   The amount by which the length is extended.
    * @return
    *   A copy of this At with the extended length
    */
  @JSExport
  def extend(extent: Int): At = this.copy(endOffset = endOffset + extent)

  @JSExport
  def atEnd: At =
    if endOffset > 0 then this.copy(offset = endOffset - 1, endOffset = endOffset)
    else this.copy(offset = 0, endOffset = 0)

  @JSExport
  override def equals(obj: Any): Boolean = {
    if obj.getClass != classOf[At] then { false }
    else {
      val that = obj.asInstanceOf[At]
      if this.offset != that.offset then { false }
      else { this.source.origin == that.source.origin }
    }
  }
}

@JSExportTopLevel("At$")
object At {

  import scala.scalajs.js.annotation.JSExport

  @JSExport("emptyConst") val empty: At = At(RiddlParserInput.empty)
  @JSExport def empty(input: RiddlParserInput): At = { At(input) }

  def range(from: At, to: At): At = {
    require(from.source == to.source)
    require(from.offset <= to.offset)
    from.copy(endOffset = to.endOffset)
  }

  /** Empty constructor for [[At]] */
  implicit def apply(): At = { At.empty }

  /** Start of line constructor for a specific [[At]] */
  implicit def apply(line: Int, src: RiddlParserInput): At = {
    val (start, end) = src.rangeOf(line)
    src.at(start, end)
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

  /** General constructor of [[At]] providing all three values
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
