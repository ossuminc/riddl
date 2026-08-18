/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{URL, PlatformContext}

/** A [[RiddlParserInput]] for BAST deserialization, which carries an origin but NO source text.
  *
  * It exists to give a BAST-reconstructed [[com.ossuminc.riddl.language.At]] an origin string for
  * messages. It deliberately does NOT try to derive line or column: `positionsKnown` is false, so
  * `At.line`/`col`/`endLine` all report 0.
  *
  * HISTORY, so the machinery is not helpfully reinvented. This class used to fabricate positions
  * from a synthetic line index on a fixed 10000-chars-per-line scheme -- `lineOf`/`offsetOf`
  * overrides, a `syntheticLineNumberLookup` array, a `maxLine` parameter, and `createAt` /
  * `createAtFromOffsets` builders. All of it was WRONG, not merely unused: BAST stores REAL source
  * offsets (`BASTWriter.writeLocation` delta-encodes `loc.offset`), so decoding one as though line
  * L began at L*10000 put every offset under 10000 on line 1 at col = offset -- a confidently wrong
  * position, which a Problems pane renders as a squiggle on the wrong line. An absent position is
  * strictly better than a plausible fiction.
  *
  * To get TRUE positions, supply the real source to `BASTReader.read` via its `suppliedSources`
  * map; that substitutes a genuine [[RiddlParserInput]] and this class is never constructed.
  *
  * @param root
  *   The URL of the original source file
  * @param originPath
  *   The origin path (for the origin string) - should be root.path
  */
private[bast] class BASTParserInput(
  val root: URL,
  originPath: String
)(using pc: PlatformContext)
    extends RiddlParserInput {

  // Override origin to return just the path, not the full URL
  override val origin: String = originPath

  // Empty data - BAST has no source text
  override val data: String = ""

  // Cannot honestly derive line or column from an offset with no text to resolve it against. This
  // is what makes At report 0 rather than a fabricated position; see the class comment.
  override def positionsKnown: Boolean = false
}

/** Companion object for BASTParserInput */
object BASTParserInput {

  /** Create a BASTParserInput from a URL, using its path as the origin string
    * @param url
    *   The URL of the original source file
    */
  def apply(url: URL)(using PlatformContext): BASTParserInput = {
    new BASTParserInput(url, url.path)
  }

  /** Create a BASTParserInput with an explicit origin string
    * @param url
    *   The URL of the original source file
    * @param origin
    *   The origin string for error messages
    */
  def apply(url: URL, origin: String)(using PlatformContext): BASTParserInput = {
    new BASTParserInput(url, origin)
  }
}
