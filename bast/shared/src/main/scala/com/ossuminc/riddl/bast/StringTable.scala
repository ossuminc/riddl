/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.bast

import scala.collection.mutable

/** String interning table for BAST serialization
  *
  * Provides efficient string storage by deduplicating common strings
  * and allowing reference by index. Pre-populated with RIDDL keywords
  * and predefined types to minimize file size.
  */
class StringTable {
  private val strings = mutable.ArrayBuffer[String]()
  private val stringToIndex = mutable.HashMap[String, Int]()

  // Pre-populate with RIDDL keywords for maximum compression
  private val keywords = Seq(
    // Structure keywords
    "domain", "context", "entity", "adaptor", "saga", "streamlet",
    "processor", "projector", "repository", "connector", "application",
    "epic", "story", "useCase", "function", "type", "author", "user",
    "term", "include", "import",

    // Type keywords
    "String", "Number", "Integer", "Decimal", "Boolean", "Date", "Time",
    "DateTime", "TimeStamp", "URL", "UUID", "Current", "Id", "Abstract",
    "Pattern", "Enumeration", "Alternation", "Aggregation", "Mapping",
    "Graph", "Table", "Replica", "Reference", "Range", "Optional",
    "ZeroOrMore", "OneOrMore", "required",

    // Common structural keywords
    "is", "as", "of", "from", "to", "by", "with", "for", "in", "on",
    "at", "and", "or", "not", "if", "then", "else", "briefly", "described",
    "explained", "contains", "yields", "returns", "requires", "ensures",
    "invariant", "options", "pipe", "source", "sink", "flow", "merge",
    "split", "void", "command", "event", "query", "result", "record",

    // Handler keywords
    "handler", "on", "other", "arbitrary",

    // Relationship keywords
    "sends", "acquires", "uses",

    // Common names
    "id", "name", "description", "value", "data", "message", "request",
    "response", "error", "status", "code", "timestamp", "userId",
    "created", "updated", "deleted"
  )

  // Initialize with keywords
  keywords.foreach(intern)

  /** Intern a string and return its index
    *
    * If the string already exists in the table, returns the existing index.
    * Otherwise, adds the string to the table and returns the new index.
    *
    * @param str The string to intern
    * @return The index of the string in the table
    */
  def intern(str: String): Int = {
    stringToIndex.get(str) match {
      case Some(index) => index
      case None =>
        val index = strings.length
        strings += str
        stringToIndex(str) = index
        index
    }
  }

  /** Get a string by its index
    *
    * @param index The index to lookup
    * @return The string at that index
    * @throws IndexOutOfBoundsException if index is invalid
    */
  def lookup(index: Int): String = {
    require(index >= 0 && index < strings.length,
      s"Invalid string table index: $index (table size: ${strings.length})")
    strings(index)
  }

  /** Get the total number of strings in the table */
  def size: Int = strings.length

  /** Check if a string exists in the table */
  def contains(str: String): Boolean = stringToIndex.contains(str)

  /** Get the index of a string if it exists
    *
    * @param str The string to find
    * @return Some(index) if found, None otherwise
    */
  def indexOf(str: String): Option[Int] = stringToIndex.get(str)

  /** Get all strings as a sequence */
  def toSeq: Seq[String] = strings.toSeq

  /** Serialize the string table to a byte buffer
    *
    * Format:
    * - Count: varint (number of strings)
    * - For each string:
    *   - Length: varint (UTF-8 byte count)
    *   - Bytes: UTF-8 encoded string
    */
  def writeTo(writer: ByteBufferWriter): Unit = {
    writer.writeVarInt(strings.length)
    strings.foreach { str =>
      val bytes = str.getBytes("UTF-8")
      writer.writeVarInt(bytes.length)
      writer.writeRawBytes(bytes)
    }
  }

  /** Clear the string table (except keywords) */
  def reset(): Unit = {
    strings.clear()
    stringToIndex.clear()
    keywords.foreach(intern)
  }

  /** Statistics for debugging and optimization */
  def stats: StringTableStats = {
    val totalChars = strings.map(_.length).sum
    val avgChars = if strings.nonEmpty then totalChars.toDouble / strings.length else 0.0
    val duplicatesAvoided = stringToIndex.size - keywords.size

    StringTableStats(
      totalStrings = strings.length,
      uniqueStrings = stringToIndex.size,
      totalCharacters = totalChars,
      averageLength = avgChars,
      duplicatesAvoided = duplicatesAvoided
    )
  }
}

/** Statistics about string table efficiency */
case class StringTableStats(
  totalStrings: Int,
  uniqueStrings: Int,
  totalCharacters: Int,
  averageLength: Double,
  duplicatesAvoided: Int
)

object StringTable {

  /** Create a new empty string table with keywords pre-loaded */
  def apply(): StringTable = new StringTable()

  /** Deserialize a string table from a byte buffer
    *
    * @param reader The byte buffer to read from
    * @return A populated string table
    */
  def readFrom(reader: ByteBufferReader): StringTable = {
    val table = new StringTable()

    // Clear the pre-populated keywords since we'll load from file
    table.strings.clear()
    table.stringToIndex.clear()

    val count = reader.readVarInt()

    var i = 0
    while i < count do
      val length = reader.readVarInt()
      val bytes = reader.readRawBytes(length)
      val str = new String(bytes, "UTF-8")

      // Add directly to avoid duplication check
      table.strings += str
      table.stringToIndex(str) = i
      i += 1
    end while

    table
  }
}
