/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.bast

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{URL, PlatformContext}

/** A RiddlParserInput implementation for BAST deserialization that doesn't have source text.
  *
  * Uses a synthetic lineNumberLookup with fixed line width to enable correct line/col calculation
  * from offsets. This allows At locations to work correctly even without source text.
  *
  * Strategy:
  * - Each line is assumed to be CHARS_PER_LINE characters wide (default 10000)
  * - Line L starts at offset (L-1) * CHARS_PER_LINE
  * - For location at line=L, col=C, offset = (L-1)*CHARS_PER_LINE + (C-1)
  * - At.line and At.col calculations then produce correct values
  *
  * @param root The URL of the original source file
  * @param originPath The origin path (for origin string) - should be root.path
  * @param maxLine Maximum line number expected (used to size lineNumberLookup)
  */
private[bast] class BASTParserInput(
  val root: URL,
  originPath: String,
  maxLine: Int = 10000
)(using pc: PlatformContext) extends RiddlParserInput {

  // Override origin to return just the path, not the full URL
  override val origin: String = originPath

  // Fixed characters per line for synthetic offset calculation
  private val CHARS_PER_LINE = 10000

  // Empty data - BAST has no source text
  override val data: String = ""

  // Build synthetic lineNumberLookup: line N starts at offset (N-1) * CHARS_PER_LINE
  private val syntheticLineNumberLookup: Array[Int] =
    (0 until maxLine).map(_ * CHARS_PER_LINE).toArray :+ (maxLine * CHARS_PER_LINE)

  // Override lineOf to use synthetic lookup
  // Note: Can't use private[language] from bast package, but these are inherited as protected
  override def lineOf(index: Int): Int = {
    // Binary search in synthetic lookup
    val result = syntheticLineNumberLookup.search(index)
    result match {
      case scala.collection.Searching.Found(foundIndex) => foundIndex
      case scala.collection.Searching.InsertionPoint(insertionPoint) =>
        if insertionPoint > 0 then insertionPoint - 1 else 0
    }
  }

  // Override offsetOf to use synthetic lookup
  override def offsetOf(line: Int): Int = {
    if line < 0 then 0
    else if line < syntheticLineNumberLookup.length then syntheticLineNumberLookup(line)
    else syntheticLineNumberLookup(syntheticLineNumberLookup.length - 1)
  }

  /** Create an At location with explicit line/col values.
    *
    * Calculates synthetic offset: (line-1) * CHARS_PER_LINE + (col-1)
    * This offset will produce correct line/col when At.line and At.col are called.
    */
  def createAt(line: Int, col: Int): At = {
    val offset = (line - 1) * CHARS_PER_LINE + (col - 1)
    At(this, offset, offset)
  }
}

/** Companion object for BASTParserInput */
object BASTParserInput {

  /** Create a BASTParserInput from a URL
    * @param url The URL of the original source file
    * @param maxLine Maximum line number expected (default 10000)
    */
  def apply(url: URL, maxLine: Int = 10000)(using PlatformContext): BASTParserInput = {
    new BASTParserInput(url, url.path, maxLine)
  }

  /** Create a BASTParserInput with explicit origin string
    * @param url The URL of the original source file
    * @param origin The origin string for error messages
    * @param maxLine Maximum line number expected
    */
  def apply(url: URL, origin: String, maxLine: Int)(using PlatformContext): BASTParserInput = {
    new BASTParserInput(url, origin, maxLine)
  }
}
