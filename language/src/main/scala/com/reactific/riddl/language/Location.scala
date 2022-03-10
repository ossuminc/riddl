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
