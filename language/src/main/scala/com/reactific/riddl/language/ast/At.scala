/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.ast

import com.reactific.riddl.language.parsing.RiddlParserInput

import scala.language.implicitConversions

/** A location of an item in the input */
case class At(
  source: RiddlParserInput,
  offset: Int = 0)
    extends Ordered[At] {

  def isEmpty: Boolean = offset == 0 && source == RiddlParserInput.empty

  lazy val line: Int = source.lineOf(offset) + 1
  lazy val col: Int = offset - source.offsetOf(line - 1) + 1

  @inline override def toString: String = { source.origin + toShort }
  @inline def toShort: String = { s"($line:$col)" }

  override def compare(that: At): Int = {
    if this.source.origin == that.source.origin then { this.offset - that.offset }
    else { this.source.origin.compare(that.source.origin) }
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
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

  implicit def apply(): At = { At(RiddlParserInput.empty) }
  implicit def apply(line: Int): At = { At(RiddlParserInput.empty, line)
  }

  implicit def apply(line: Int, src: RiddlParserInput): At = {
    src.location(src.offsetOf(line))
  }

  implicit def apply(
    pair: (Int, Int)
  ): At = { apply(pair, RiddlParserInput.empty) }

  implicit def apply(
    pair: (Int, Int),
    src: RiddlParserInput
  ): At = { apply(pair._1, pair._2, src) }

  implicit def apply(
    triple: (Int, Int, RiddlParserInput)
  ): At = { apply(triple._1, triple._2, triple._3) }

  implicit def apply(
    line: Int,
    col: Int,
    src: RiddlParserInput
  ): At = {
    val offset = src.offsetOf(line - 1) + col - 1
    src.location(offset)
  }
}
