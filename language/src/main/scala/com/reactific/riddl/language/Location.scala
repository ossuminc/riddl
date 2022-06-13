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

package com.reactific.riddl.language

import com.reactific.riddl.language.parsing.RiddlParserInput
import scala.language.implicitConversions

/** A location of an item in the input */
case class Location(
  source: RiddlParserInput = RiddlParserInput.empty,
  offset: Int = 0
)
  extends Ordered[Location] {

  def isEmpty: Boolean = offset == 0 && source == RiddlParserInput.empty

  lazy val line: Int = source.lineOf(offset) + 1
  lazy val col: Int = offset - source.offsetOf(line-1) + 1

  @inline override def toString: String = { s"${source.origin}$toShort" }
  @inline def toShort: String = { s"($line:$col)"}

  override def compare(that: Location): Int = {
    if (that.offset == offset) {
      this.source.origin.compare(that.source.origin)
    } else { this.offset - that.offset }
  }

  override def equals(obj: Any): Boolean = {
    if (obj.getClass != classOf[Location]) {
      false
    } else {
      val that = obj.asInstanceOf[Location]
      if (offset != that.offset) {
        false
      } else {
        this.source.origin == that.source.origin
      }
    }
  }
}

object Location {
  val empty: Location = Location()
  final val defaultSourceName = RiddlParserInput.empty.origin

  implicit def apply(line: Int): Location = {
    apply(line, RiddlParserInput.empty)
  }

  implicit def apply(line: Int, src: RiddlParserInput): Location = {
    src.location(src.offsetOf(line))
  }

  implicit def apply(
    pair: (Int, Int)
  ): Location = {
    apply(pair, RiddlParserInput.empty)
  }

  implicit def apply(
    pair: (Int, Int),
    src: RiddlParserInput
  ): Location = {
    apply(pair._1, pair._2, src)
  }

  implicit def apply(
    line: Int,
    col: Int,
    src: RiddlParserInput
  ): Location = {
    val offset = src.offsetOf(line-1) + col - 1
    src.location(offset)
  }
}
