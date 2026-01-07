/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.collection.mutable

/** A trait to build a string up for writing to a file. This provides the platform specific definition of newline, an nl
  * function to insert a newline, and other functions for putting changes into the file content. The file is assumed to
  * have a UTF-8 content type.
  */
trait FileBuilder {

  protected[utils] val sb: mutable.StringBuilder = new mutable.StringBuilder()

  protected val new_line: String = System.lineSeparator()

  protected val spaces_per_level = 2

  private var indent_level: Int = 0

  /** The number of spaces at the current indent level */
  protected def spc: String = {
    " ".repeat(indent_level * spaces_per_level)
  }

  /**
    * Increment the indent by one level
    * @return
    */
  def incr: this.type = {
    indent_level += 1
    this
  }

  /**
    * Decrement the indent by one level
    * @return
    */
  def decr: this.type = {
    require(indent_level >= 1, "unmatched indents")
    indent_level -= 1
    this
  }


  /** Append a newline character
    * @return
    *   This object for call chaining
    */
  def nl: this.type = { sb.append(new_line); this }

  /** Append a string without indent or suffix
    *
    * @param str
    * The string to append
    * @return
    * This object for call chaining
    */
  def add(str: String): this.type = {
    sb.append(str)
    this
  }

  /** Append a line to the buffer without newlines or indentation
    * @param text
    *   The text to be appended
    * @return
    *   This object for call chaining
    */
  def append(text: String): this.type = { sb.append(text); this }

  /** Append a line to the buffer with a terminating newline. No prefix is inserted
    * @param line
    *   The line to be appended
    * @return
    *   This object for call chaining
    */
  def addLine(line: String): this.type =
    sb.append(spc)
      .append(line)
      .append(new_line)
    this
  end addLine  

  /**
    * Add just the indent spaces
    * @return
    *   This object for call chaining
    */
  def addIndent(): this.type = {
    sb.append(s"$spc")
    this
  }

  /** Append an indented line with newline at the end and a `level` width indent prefix. The indent will be done with
    * `level` * `spaces-per-level` space characters.
    * @param line
    *   The text of the line to be indented
    * @return
    *   This object for call chaining
    */
  def addIndent(line: String): this.type = {
    sb.append(spc).append(line)
    this
  }

  override def toString: String = sb.toString

  /** Convert the buffer to a sequence of strings, one line per string
    * @return
    */
  def toLines: Seq[String] = sb.toString.split(new_line).toIndexedSeq

  /** Completely erase the internal buffer
    */
  def clear(): Unit = {
    sb.clear()
    indent_level = 0
  }
}
