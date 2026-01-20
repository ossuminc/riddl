/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import scala.collection.mutable

/** Path interning table for BAST serialization (Phase 8 optimization)
  *
  * Provides efficient path storage by deduplicating repeated path patterns.
  * PathIdentifiers often repeat the same sequences (e.g., "Domain.Context.Entity"),
  * and this table allows them to be referenced by a single index instead of
  * repeating the full sequence.
  *
  * The table stores paths as sequences of string table indices for maximum
  * compression (avoiding string duplication between tables).
  *
  * Encoding in BAST:
  * - If count > 0: inline path follows (count string indices)
  * - If count == 0: next varint is path table index
  */
class PathTable(stringTable: StringTable) {
  // Store paths as sequences of string indices (not strings) for efficiency
  private val paths = mutable.ArrayBuffer[Seq[Int]]()
  private val pathToIndex = mutable.HashMap[Seq[Int], Int]()

  /** Intern a path and return its index
    *
    * If the path already exists in the table, returns the existing index.
    * Otherwise, adds the path to the table and returns the new index.
    *
    * @param pathValues The path components (strings)
    * @return The index of the path in the table, or -1 if path is empty or single-element
    */
  def intern(pathValues: Seq[String]): Int = {
    // Don't intern empty or single-element paths - no savings
    if pathValues.length <= 1 then return -1

    // Convert to string indices
    val indices = pathValues.map(stringTable.intern)

    pathToIndex.get(indices) match {
      case Some(index) => index
      case None =>
        val index = paths.length
        paths += indices
        pathToIndex(indices) = index
        index
    }
  }

  /** Check if a path has been interned and return its index
    *
    * @param pathValues The path components (strings)
    * @return Some(index) if the path is interned, None otherwise
    */
  def indexOf(pathValues: Seq[String]): Option[Int] = {
    if pathValues.length <= 1 then None
    else
      val indices = pathValues.map(str => stringTable.indexOf(str).getOrElse(-1))
      // If any string isn't in the string table, the path can't be in the path table
      if indices.contains(-1) then None
      else pathToIndex.get(indices)
  }

  /** Get a path by its index
    *
    * @param index The index to lookup
    * @return The path components as strings
    * @throws IndexOutOfBoundsException if index is invalid
    */
  def lookup(index: Int): Seq[String] = {
    require(index >= 0 && index < paths.length,
      s"Invalid path table index: $index (table size: ${paths.length})")
    paths(index).map(stringTable.lookup)
  }

  /** Get the total number of paths in the table */
  def size: Int = paths.length

  /** Check if a path exists in the table */
  def contains(pathValues: Seq[String]): Boolean = indexOf(pathValues).isDefined

  /** Serialize the path table to a byte buffer
    *
    * Format:
    * - Count: varint (number of paths)
    * - For each path:
    *   - Length: varint (number of path components)
    *   - For each component: varint (string table index)
    */
  def writeTo(writer: ByteBufferWriter): Unit = {
    writer.writeVarInt(paths.length)
    paths.foreach { indices =>
      writer.writeVarInt(indices.length)
      indices.foreach(idx => writer.writeVarInt(idx))
    }
  }

  /** Statistics for debugging and optimization */
  def stats: PathTableStats = {
    val totalPaths = paths.length
    val totalComponents = paths.map(_.length).sum
    val avgComponents = if paths.nonEmpty then totalComponents.toDouble / paths.length else 0.0

    // Estimate bytes saved: for each interned path, we save (components * avgVarIntSize) - 1
    // Assume avg varint size of 1.5 bytes per string index, minus 1 byte for the path table index
    val estimatedBytesSaved = paths.map { indices =>
      val inlineSize = 1 + indices.length * 2 // count varint + indices
      val tableRefSize = 2 // 0 marker + index varint
      (inlineSize - tableRefSize) * (pathToIndex.size - 1) // times number of references minus first
    }.sum

    PathTableStats(
      totalPaths = totalPaths,
      totalComponents = totalComponents,
      averageComponents = avgComponents,
      estimatedBytesSaved = estimatedBytesSaved
    )
  }

  /** Clear the path table */
  def reset(): Unit = {
    paths.clear()
    pathToIndex.clear()
  }
}

/** Statistics about path table efficiency */
case class PathTableStats(
  totalPaths: Int,
  totalComponents: Int,
  averageComponents: Double,
  estimatedBytesSaved: Int
)

object PathTable {

  /** Create a new empty path table
    *
    * @param stringTable The string table to use for string indices
    */
  def apply(stringTable: StringTable): PathTable = new PathTable(stringTable)

  /** Deserialize a path table from a byte buffer
    *
    * @param reader The byte buffer to read from
    * @param stringTable The string table for looking up strings
    * @return A populated path table
    */
  def readFrom(reader: ByteBufferReader, stringTable: StringTable): PathTable = {
    val table = new PathTable(stringTable)

    val count = reader.readVarInt()

    var i = 0
    while i < count do
      val length = reader.readVarInt()
      val indices = (0 until length).map(_ => reader.readVarInt())

      // Add directly to avoid duplication check
      table.paths += indices
      table.pathToIndex(indices) = i
      i += 1
    end while

    table
  }
}
