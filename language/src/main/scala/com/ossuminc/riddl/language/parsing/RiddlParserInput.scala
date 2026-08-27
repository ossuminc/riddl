/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.{Message, Messages}
import com.ossuminc.riddl.utils.{pc, Await, LoadFailure, PlatformContext, URL}
import fastparse.ParserInput
import fastparse.internal.Util

import scala.collection.Searching
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.annotation.nowarn
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.scalajs.js.annotation.*
import scala.io.AnsiColor.{BOLD, RESET}

/** Primary interface to setting up a RIDDL Parser's input. The idea here is to use one of the apply
  * methods in this companion object to construct a RiddlParserInput for a specific input source
  * (file, path, Source, data string, URL, etc.)
  */
@JSExportTopLevel("RiddlParserInput")
object RiddlParserInput {

  val empty: RiddlParserInput = EmptyParserInput

  /** Set up a parser input for parsing directly from a String
    * @param data
    *   data The UTF-8 string to be parsed
    * @param root
    *   The URL from which the input data was derived
    * @param purpose
    *   The purpose for which this data is provided, often the test case name
    */
  @JSExport("createWith")
  implicit def apply(data: String, root: URL, purpose: String = ""): RiddlParserInput = {
    StringParserInput(data, root, purpose)
  }

  /** Set up a parser input for parsing a test case from a String and the testCaseName.
    * @param data
    *   The data to be parsed and returned in a RiddlParserInput
    * @param purpose
    *   A string, typically derived by the scalatest TestData extension in ParserTest.
    */
  @JSExport("createTestInput")
  implicit def apply(data: String, purpose: String): RiddlParserInput = {
    StringParserInput(data, URL.empty, purpose)
  }

  @JSExport("createFromTuple2")
  implicit def apply(data: (String, String)): RiddlParserInput = {
    StringParserInput(data._1, URL.empty, data._2)
  }

  /** Set up a parser input from a [[com.ossuminc.riddl.utils.URL]].
    *
    * @param url
    *   The url from which to load the data
    * @return
    *   A Future[RiddlParserInput] with the RPI set up to load data from the provided url
    */
  @deprecated("Use fromURLSafe, which reports load failures instead of throwing", "2.0.0")
  @nowarn("cat=deprecation")
  def fromURL(url: URL, purpose: String = "")(using
    io: PlatformContext
  ): Future[RiddlParserInput] = {
    implicit val ec: ExecutionContext = io.ec
    io.load(url).map(data => apply(data, url, purpose))
  }

  /** Read a URL into a parser input, reporting a load failure as Messages rather than throwing.
    *
    * A missing file, a directory named where a file belongs, or a binary file are ordinary user
    * mistakes, and each used to arrive at the command-level catch-all as a raw Java exception —
    * `[severe] Exception Thrown:: java.io.FileNotFoundException`. `PlatformContext.loadSafe`
    * classifies them; this is where that classification becomes a RIDDL message with a suggestion,
    * which is possible here and not in `utils` because Messages lives in this module.
    */
  def fromURLSafe(url: URL, purpose: String = "")(using
    io: PlatformContext
  ): Future[Either[Messages, RiddlParserInput]] = {
    implicit val ec: ExecutionContext = io.ec
    io.loadSafe(url).map {
      case Right(data)   => Right(apply(data, url, purpose))
      case Left(failure) => Left(List(loadFailureToMessage(failure)))
    }
  }

  /** Turn a [[LoadFailure]] into an error a user can act on. The advice differs by case, which is
    * the reason loadSafe returns an ADT rather than a string.
    */
  private def loadFailureToMessage(failure: LoadFailure): Message =
    val suggestion = failure match
      case _: LoadFailure.NotFound =>
        "Check the path for a typo, and that the file exists relative to where riddlc was run."
      case _: LoadFailure.NotAFile =>
        "Name the RIDDL file itself, not the directory containing it."
      case _: LoadFailure.Unreadable =>
        "Check the file's permissions."
      case _: LoadFailure.Undecodable =>
        "RIDDL sources are UTF-8 text; this file appears to be binary or in another encoding."
      case _: LoadFailure.Unreachable =>
        "Check the URL and that the resource is reachable."
    Messages.error(failure.describe, At.empty).copy(suggestion = suggestion)
  end loadFailureToMessage

  /** Read a path into a parser input, reporting failure as Messages rather than throwing.
    *
    * Building the URL is inside the try because that can throw too: URL.fromCwdPath and
    * fromFullPath each `require` a particular leading slash, so a caller could be handed an
    * exception before any loading was attempted.
    */
  def fromPathSafe(path: String, purpose: String = "")(using
    io: PlatformContext
  ): Future[Either[Messages, RiddlParserInput]] = {
    implicit val ec: ExecutionContext = io.ec
    try
      if path.isEmpty then
        Future.successful(Left(List(Messages.error("No input file was given", At.empty))))
      else
        val url: URL = if path.head == '/' then URL.fromFullPath(path) else URL.fromCwdPath(path)
        fromURLSafe(url, purpose)
      end if
    catch
      case NonFatal(x) =>
        Future.successful(
          Left(
            List(
              Messages
                .error(s"Invalid input path `$path`: ${x.getMessage}", At.empty)
                .copy(suggestion = "Check the path for a typo or an unexpected leading slash.")
            )
          )
        )
    end try
  }

  @deprecated("Use fromPathSafe, which reports failures instead of throwing", "2.0.0")
  @nowarn("cat=deprecation")
  def fromPath(path: String, purpose: String = "")(using
    PlatformContext
  ): Future[RiddlParserInput] = {
    assert(path.nonEmpty, "Path provided to RiddlParserInput.fromPath is empty")
    val url: URL = if path.head == '/' then URL.fromFullPath(path) else URL.fromCwdPath(path)
    fromURL(url, purpose)
  }
}

/** This class provides the loaded data for fastparse to parse. It is the same as
  * fastparse.IndexedParserInput but adds support for file locations with [[At]]. The class is
  * abstract because
  */
abstract class RiddlParserInput(using pc: PlatformContext) extends ParserInput {

  /** The data that will be parsed by fastparse */
  def data: String

  /** The URL from which the [[data]] originated. If it didn't originate from a network or file
    * location, then this should be empty, URL("") so that URL validity checking will be skipped.
    */
  def root: URL

  /** The short origin name to use in error messages as the origin of the error. In test cases that
    * do not use a URL, this should be overridden with the word "empty"
    * @return
    *   Typically the last filename in the URL is sufficient, and that is the default calculated
    *   from [[root]].
    */
  def origin: String = if root.isEmpty then "empty" else root.path

  /** The purpose of this parsing input. It could be a test name or blank for normal usage */
  def purpose: String = ""

  override inline def apply(index: Int): Char = data.charAt(index)
  override inline def dropBuffer(index: Int): Unit = {}
  override inline def slice(from: Int, until: Int): String = data.slice(from, until)
  override inline def length: Int = data.length
  override inline def innerLength: Int = length
  override inline def isReachable(index: Int): Boolean = index < length

  def checkTraceable(): Unit = ()

  inline final def isEmpty: Boolean = data.isEmpty
  inline final def nonEmpty: Boolean = !isEmpty

  private lazy val lineNumberLookup: Array[Int] =
    Util.lineNumberLookup(data).appended(data.length)

  // Simple LRU cache for line lookups: stores (index, lineNumber) pairs
  // Parsing is mostly sequential, so recent lookups predict future lookups
  private val lineCache: Array[(Int, Int)] = Array.fill(4)((-1, -1))
  private var lineCachePos: Int = 0

  /** Whether line and column can honestly be derived from an offset against this input.
    *
    * False only for an input that carries an origin but no text — a BAST-reconstructed source.
    * `At.line`/`At.col` then report 0, which is unrepresentable as a real 1-based position, rather
    * than a plausible wrong number. Supplying the real source to `BASTReader.read` makes them true
    * again. Defaults to true so `RiddlParserInput.empty` keeps reporting `1:1` as it always has.
    */
  def positionsKnown: Boolean = true

  private[language] def offsetOf(line: Int): Int = {
    if line < 0 then { lineNumberLookup(line) }
    else if line < lineNumberLookup.length then { lineNumberLookup(line) }
    else { lineNumberLookup(lineNumberLookup.length - 1) }
  }

  private[language] def lineOf(index: Int): Int = {
    // Check cache first - O(1) for recent lookups
    var i = 0
    while i < lineCache.length do
      if lineCache(i)._1 == index then return lineCache(i)._2
      i += 1

    // Cache miss - do binary search
    val result = lineNumberLookup.search(index)
    val lineNum = result match {
      case Searching.Found(foundIndex) => foundIndex
      case Searching.InsertionPoint(insertionPoint) =>
        if insertionPoint > 0 then insertionPoint - 1 else insertionPoint
    }

    // Update cache with new result (circular buffer)
    lineCache(lineCachePos) = (index, lineNum)
    lineCachePos = (lineCachePos + 1) % lineCache.length

    lineNum
  }

  def rangeOf(index: Int): (Int, Int) = {
    val line = lineOf(index)
    val start = lineNumberLookup(line)
    val end = lineNumberLookup(line + 1)
    start -> end
  }

  private def endOfLineFrom(offset: Int): Int = {
    require(offset <= data.length)
    val line = lineOf(offset)
    lineNumberLookup(line)
  }

  def lineRangeOf(loc: At): (Int, Int) = {
    require(loc.source == this)
    require(loc.offset >= 0)
    require(loc.offset <= loc.endOffset)
    val startLine = lineOf(loc.offset)
    val endLine = lineOf(loc.endOffset) + 1

    require(loc.line > 0)
    val start = lineNumberLookup(startLine)
    val end =
      if lineNumberLookup.length == 1 then { data.length }
      else if endLine >= lineNumberLookup.length then {
        // endOffset is on or past the last line, use end of data
        data.length
      } else { lineNumberLookup(endLine) }
    start -> end
  }

  @inline final def location(index: Int): At = {
    At(this, index, index + 1)
  }

  @inline final def at(start: Int, end: Int): At = { At(this, start, end) }

  def prettyIndex(index: Int): String = { location(index).toString }

  // Use "\n" directly since System.getProperty returns null in Scala.js
  val nl: String = "\n"

  def annotateErrorLine(index: At): String = {
    require(index.source == this)
    if this.data.length > 0 && this.nonEmpty then
      require(
        index.offset >= 0 && index.offset <= data.length,
        s"${index.offset}>=0 && ${index.offset} <= ${data.length}"
      )
      require(
        index.endOffset >= 0 && index.endOffset <= data.length + 1,
        s"${index.endOffset} >= 0 && ${index.endOffset} <= ${data.length + 1}"
      )
      val (start, end) = lineRangeOf(index)
      // When a parse failure occurs at EOF (e.g., missing closing `}`),
      // the failure's `At` can have `endOffset` past the end of the line
      // computed by `lineRangeOf`. The original `require` would crash the
      // error reporter itself. Downstream slicing already clamps via
      // `Math.min`, so this defensive code can tolerate the boundary
      // condition instead of failing.
      val quoted = slice(start, end)
      if quoted.isEmpty then ""
      else {
        if pc.options.noANSIMessages then quoted
        else
          val prefixStart = offsetOf(lineOf(start))
          val prefixEnd = Math.max(0, index.offset)
          val errorStart = index.offset
          val errorEnd = Math.min(Math.min(index.endOffset, end), data.length)
          val suffixStart = Math.min(errorEnd, index.source.length)
          val suffixEnd = Math.max(suffixStart, Math.min(endOfLineFrom(end), index.source.length))
          val prefix = data.substring(prefixStart, prefixEnd)
          val error = data.substring(errorStart, errorEnd)
          val suffix = data.substring(suffixStart, suffixEnd)
          prefix + BOLD + error + RESET + suffix
      }
    else ""
    end if
  }
}

import com.ossuminc.riddl.utils.pc

@JSExportTopLevel("EmptyParserInput")
case object EmptyParserInput extends RiddlParserInput() {
  override def origin: String = "empty"
  override def root: URL = URL.empty
  override def offsetOf(line: Int): Int = { line * 80 }
  override def lineOf(offset: Int): Int = { offset / 80 }
  def data: String = ""
  def from: String = ""
}

protected[parsing] case class StringParserInput(
  data: String,
  root: URL = URL.empty,
  override val purpose: String = ""
) extends RiddlParserInput {
  override def toString: String = {
    super.toString ++ s", data: ${data.length} chars, origin: $origin"
  }

  /** Memoised hash. The generated `hashCode` for this case class hashes [[data]] — the ENTIRE text
    * of a source file — and that hash is reached constantly: `At` holds a [[RiddlParserInput]],
    * `Identifier` and `Definition` hold an `At`, and `ReferenceMap.Key` holds a `Definition`, so
    * every reference-map add and lookup hashed a whole file.
    *
    * The JVM and Native never noticed, because both memoise `String.hashCode` into the string
    * object. Scala.js cannot — a JS string has nowhere to put the field — so it re-walked every
    * character on every call. Measured on a 139KB source: 14ns (JVM), 1ns (Native), **181,187ns
    * (Scala.js)**, i.e. 3402x the cost of hashing a short name where the other platforms pay 1.0x.
    * That single asymmetry, not any algorithmic complexity, is what made ResolutionPass 97x slower
    * on Scala.js than on the JVM.
    *
    * Caching it here restores on every platform the property the JVM already had. It costs one
    * field per SOURCE FILE and nothing per AST node. The value is deterministic in the fields, so
    * equal inputs still hash equally.
    */
  private lazy val cachedHashCode: Int =
    scala.util.hashing.MurmurHash3.productHash(this)

  override def hashCode(): Int = cachedHashCode

  /** Overriding `hashCode` on a case class suppresses the compiler-generated `equals` (Scala 3
    * spec) — the same trap recorded on `AST.Definition` — so it must be written out. The `eq` fast
    * path is a bonus fix: the generated `equals` compared [[data]] character-by-character, which
    * was the same O(file) cost on every hash collision.
    */
  override def equals(that: Any): Boolean = that match
    case other: StringParserInput =>
      (this eq other) || (data == other.data && root == other.root && purpose == other.purpose)
    case _ => false
  end equals
}
