/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.collection.mutable

trait FileBuilder {

  protected val sb: mutable.StringBuilder = new mutable.StringBuilder()

  protected val newLine = System.lineSeparator()

  def nl: this.type = { sb.append(newLine); this }

  protected val spaces_per_level = 2

  protected def indent(line: String, level: Int = 1): this.type = {
    sb.append(" ".repeat(level*spaces_per_level)).append(line)
    nl
  }


  override def toString: String = sb.toString

  def toLines: Seq[String] = sb.toString.split(newLine).toIndexedSeq

  def clear: Unit = sb.clear()
}
