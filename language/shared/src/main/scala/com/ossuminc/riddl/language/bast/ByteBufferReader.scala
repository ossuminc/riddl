/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

/** Platform-agnostic byte buffer reader for reading BAST binary data
  *
  * Provides methods for reading primitive types, strings, and varints
  * from a byte array with automatic position tracking.
  */
class ByteBufferReader(private val bytes: Array[Byte]) {
  private var pos = 0

  /** Current read position in the buffer */
  def position: Int = pos

  /** Set the read position */
  def seek(newPosition: Int): Unit = {
    require(newPosition >= 0 && newPosition <= bytes.length,
      s"Invalid seek position: $newPosition (buffer size: ${bytes.length})")
    pos = newPosition
  }

  /** Check if there are more bytes available to read */
  def hasRemaining: Boolean = pos < bytes.length

  /** Number of bytes remaining */
  def remaining: Int = bytes.length - pos

  /** Read a single byte */
  def readByte(): Byte = {
    require(hasRemaining, s"Buffer underflow at position $pos")
    val value = bytes(pos)
    pos += 1
    value
  }

  /** Read a single byte as unsigned value (0-255) */
  def readU8(): Int = {
    readByte() & 0xFF
  }

  /** Read a 16-bit short (big-endian) */
  def readShort(): Short = {
    require(remaining >= 2, s"Not enough bytes for short at position $pos")
    val b1 = bytes(pos) & 0xFF
    val b2 = bytes(pos + 1) & 0xFF
    pos += 2
    ((b1 << 8) | b2).toShort
  }

  /** Read a 32-bit integer (big-endian) */
  def readInt(): Int = {
    require(remaining >= 4, s"Not enough bytes for int at position $pos")
    val b1 = bytes(pos) & 0xFF
    val b2 = bytes(pos + 1) & 0xFF
    val b3 = bytes(pos + 2) & 0xFF
    val b4 = bytes(pos + 3) & 0xFF
    pos += 4
    (b1 << 24) | (b2 << 16) | (b3 << 8) | b4
  }

  /** Read a 64-bit long (big-endian) */
  def readLong(): Long = {
    require(remaining >= 8, s"Not enough bytes for long at position $pos")
    val b1 = (bytes(pos) & 0xFF).toLong
    val b2 = (bytes(pos + 1) & 0xFF).toLong
    val b3 = (bytes(pos + 2) & 0xFF).toLong
    val b4 = (bytes(pos + 3) & 0xFF).toLong
    val b5 = (bytes(pos + 4) & 0xFF).toLong
    val b6 = (bytes(pos + 5) & 0xFF).toLong
    val b7 = (bytes(pos + 6) & 0xFF).toLong
    val b8 = (bytes(pos + 7) & 0xFF).toLong
    pos += 8
    (b1 << 56) | (b2 << 48) | (b3 << 40) | (b4 << 32) |
      (b5 << 24) | (b6 << 16) | (b7 << 8) | b8
  }

  /** Read a variable-length integer */
  def readVarInt(): Int = {
    val (value, bytesRead) = VarIntCodec.decode(bytes, pos)
    pos += bytesRead
    value
  }

  /** Read a variable-length long */
  def readVarLong(): Long = {
    val (value, bytesRead) = VarIntCodec.decodeLong(bytes, pos)
    pos += bytesRead
    value
  }

  /** Read a byte array with length prefix */
  def readBytes(): Array[Byte] = {
    val length = readVarInt()
    require(remaining >= length,
      s"Not enough bytes: need $length, have $remaining at position $pos")

    val result = new Array[Byte](length)
    System.arraycopy(bytes, pos, result, 0, length)
    pos += length
    result
  }

  /** Read a UTF-8 string with length prefix */
  def readString(): String = {
    val stringBytes = readBytes()
    new String(stringBytes, "UTF-8")
  }

  /** Read raw bytes of specified length without length prefix */
  def readRawBytes(length: Int): Array[Byte] = {
    require(remaining >= length,
      s"Not enough bytes: need $length, have $remaining at position $pos")

    val result = new Array[Byte](length)
    System.arraycopy(bytes, pos, result, 0, length)
    pos += length
    result
  }

  /** Read a boolean (0 = false, non-zero = true) */
  def readBoolean(): Boolean = {
    readByte() != 0
  }

  /** Peek at the next byte without advancing position */
  def peekByte(): Byte = {
    require(hasRemaining, s"Buffer underflow at position $pos")
    bytes(pos)
  }

  /** Peek at the next unsigned byte without advancing position */
  def peekU8(): Int = {
    peekByte() & 0xFF
  }

  /** Skip the specified number of bytes */
  def skip(count: Int): Unit = {
    require(remaining >= count,
      s"Cannot skip $count bytes: only $remaining remaining at position $pos")
    pos += count
  }

  /** Get total size of the buffer */
  def size: Int = bytes.length
}
