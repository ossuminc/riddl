/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils
import scala.collection.mutable

trait FileBuilder {

  protected val sb: mutable.StringBuilder = new mutable.StringBuilder()

  val newLine = System.lineSeparator()
  def nl: this.type = { sb.append(newLine); this }

  override def toString: String = sb.toString
  def toLines: Seq[String] = sb.toString.split(newLine).toIndexedSeq

  def clear: Unit = sb.clear()
}
