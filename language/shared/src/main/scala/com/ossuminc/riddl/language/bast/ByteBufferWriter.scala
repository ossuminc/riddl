/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import scala.collection.mutable.ArrayBuffer

/** Platform-agnostic byte buffer writer for building BAST binary data
  *
  * Provides methods for writing primitive types, strings, and varints
  * to a growing byte buffer.
  */
class ByteBufferWriter {
  private val buffer = ArrayBuffer[Byte]()

  /** Current write position in the buffer */
  def position: Int = buffer.length

  /** Write a single byte */
  def writeByte(value: Byte): Unit = {
    buffer += value
  }

  /** Write a single byte as an unsigned value */
  def writeU8(value: Int): Unit = {
    require(value >= 0 && value <= 255, s"U8 value out of range: $value")
    buffer += value.toByte
  }

  /** Write a 16-bit short (big-endian) */
  def writeShort(value: Short): Unit = {
    buffer += ((value >> 8) & 0xFF).toByte
    buffer += (value & 0xFF).toByte
  }

  /** Write a 32-bit integer (big-endian) */
  def writeInt(value: Int): Unit = {
    buffer += ((value >> 24) & 0xFF).toByte
    buffer += ((value >> 16) & 0xFF).toByte
    buffer += ((value >> 8) & 0xFF).toByte
    buffer += (value & 0xFF).toByte
  }

  /** Write a 64-bit long (big-endian) */
  def writeLong(value: Long): Unit = {
    buffer += ((value >> 56) & 0xFF).toByte
    buffer += ((value >> 48) & 0xFF).toByte
    buffer += ((value >> 40) & 0xFF).toByte
    buffer += ((value >> 32) & 0xFF).toByte
    buffer += ((value >> 24) & 0xFF).toByte
    buffer += ((value >> 16) & 0xFF).toByte
    buffer += ((value >> 8) & 0xFF).toByte
    buffer += (value & 0xFF).toByte
  }

  /** Write a variable-length integer */
  def writeVarInt(value: Int): Unit = {
    val bytes = VarIntCodec.encode(value)
    buffer ++= bytes
  }

  /** Write a variable-length long */
  def writeVarLong(value: Long): Unit = {
    val bytes = VarIntCodec.encodeLong(value)
    buffer ++= bytes
  }

  /** Write a byte array with length prefix */
  def writeBytes(bytes: Array[Byte]): Unit = {
    writeVarInt(bytes.length)
    buffer ++= bytes
  }

  /** Write a UTF-8 string with length prefix */
  def writeString(str: String): Unit = {
    val bytes = str.getBytes("UTF-8")
    writeBytes(bytes)
  }

  /** Write raw bytes without length prefix */
  def writeRawBytes(bytes: Array[Byte]): Unit = {
    buffer ++= bytes
  }

  /** Write a boolean as a byte (0 = false, 1 = true) */
  def writeBoolean(value: Boolean): Unit = {
    buffer += (if value then 1.toByte else 0.toByte)
  }

  /** Get the current buffer contents as an array */
  def toByteArray: Array[Byte] = buffer.toArray

  /** Clear the buffer */
  def clear(): Unit = {
    buffer.clear()
  }

  /** Update a previously written integer at a specific position
    * Useful for backpatching offsets after writing data
    */
  def updateInt(position: Int, value: Int): Unit = {
    require(position >= 0 && position + 3 < buffer.length,
      s"Invalid position for updateInt: $position (buffer size: ${buffer.length})")

    buffer(position) = ((value >> 24) & 0xFF).toByte
    buffer(position + 1) = ((value >> 16) & 0xFF).toByte
    buffer(position + 2) = ((value >> 8) & 0xFF).toByte
    buffer(position + 3) = (value & 0xFF).toByte
  }

  /** Update a previously written short at a specific position */
  def updateShort(position: Int, value: Short): Unit = {
    require(position >= 0 && position + 1 < buffer.length,
      s"Invalid position for updateShort: $position (buffer size: ${buffer.length})")

    buffer(position) = ((value >> 8) & 0xFF).toByte
    buffer(position + 1) = (value & 0xFF).toByte
  }

  /** Reserve space for an integer and return the position
    * Used for forward references that will be backpatched later
    */
  def reserveInt(): Int = {
    val pos = position
    writeInt(0) // Placeholder
    pos
  }

  /** Reserve space for a short and return the position */
  def reserveShort(): Int = {
    val pos = position
    writeShort(0) // Placeholder
    pos
  }

  /** Get current size of the buffer */
  def size: Int = buffer.length
}
