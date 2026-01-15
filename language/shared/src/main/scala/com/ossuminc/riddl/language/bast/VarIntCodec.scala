/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import scala.collection.mutable.ArrayBuffer

/** Variable-length integer encoding/decoding using LEB128 format
  *
  * LEB128 (Little Endian Base 128) uses the high bit of each byte as a
  * continuation bit. This allows small numbers to use fewer bytes.
  *
  * Encoding:
  * - 0-127: 1 byte
  * - 128-16383: 2 bytes
  * - 16384-2097151: 3 bytes
  * - etc.
  *
  * Format:
  * - Bit 7: continuation bit (1 = more bytes follow, 0 = last byte)
  * - Bits 0-6: data bits
  */
object VarIntCodec {

  /** Encode a positive integer as varint
    *
    * @param value The integer to encode (must be >= 0)
    * @return Array of bytes representing the varint
    */
  def encode(value: Int): Array[Byte] = {
    require(value >= 0, s"VarInt encoding requires non-negative value, got: $value")

    val buffer = ArrayBuffer[Byte]()
    var remaining = value

    while remaining > 127 do
      // Set continuation bit (0x80) and lower 7 bits
      buffer += ((remaining & 0x7F) | 0x80).toByte
      remaining = remaining >>> 7
    end while

    // Last byte without continuation bit
    buffer += (remaining & 0x7F).toByte

    buffer.toArray
  }

  /** Decode a varint from a byte array
    *
    * @param bytes The byte array containing the varint
    * @param offset Starting offset in the array
    * @return Tuple of (decoded value, number of bytes consumed)
    */
  def decode(bytes: Array[Byte], offset: Int): (Int, Int) = {
    require(offset >= 0 && offset < bytes.length,
      s"Invalid offset: $offset (array length: ${bytes.length})")

    var result = 0
    var shift = 0
    var bytesRead = 0
    var pos = offset

    while pos < bytes.length do
      val byte = bytes(pos) & 0xFF
      bytesRead += 1
      pos += 1

      // Extract lower 7 bits and add to result
      result |= (byte & 0x7F) << shift
      shift += 7

      // Check continuation bit
      if (byte & 0x80) == 0 then
        return (result, bytesRead)
    end while

    throw new IllegalArgumentException(
      s"Incomplete varint at offset $offset: no terminating byte found"
    )
  }

  /** Encode a long as varint
    *
    * @param value The long to encode (must be >= 0)
    * @return Array of bytes representing the varint
    */
  def encodeLong(value: Long): Array[Byte] = {
    require(value >= 0L, s"VarInt encoding requires non-negative value, got: $value")

    val buffer = ArrayBuffer[Byte]()
    var remaining = value

    while remaining > 127L do
      buffer += ((remaining & 0x7FL) | 0x80L).toByte
      remaining = remaining >>> 7
    end while

    buffer += (remaining & 0x7FL).toByte

    buffer.toArray
  }

  /** Decode a varint as a long from a byte array
    *
    * @param bytes The byte array containing the varint
    * @param offset Starting offset in the array
    * @return Tuple of (decoded value, number of bytes consumed)
    */
  def decodeLong(bytes: Array[Byte], offset: Int): (Long, Int) = {
    require(offset >= 0 && offset < bytes.length,
      s"Invalid offset: $offset (array length: ${bytes.length})")

    var result = 0L
    var shift = 0
    var bytesRead = 0
    var pos = offset

    while pos < bytes.length do
      val byte = bytes(pos) & 0xFF
      bytesRead += 1
      pos += 1

      result |= (byte & 0x7FL) << shift
      shift += 7

      if (byte & 0x80) == 0 then
        return (result, bytesRead)
    end while

    throw new IllegalArgumentException(
      s"Incomplete varint at offset $offset: no terminating byte found"
    )
  }

  /** Calculate the encoded size of an integer without encoding it
    *
    * @param value The value to measure
    * @return Number of bytes the encoded varint would occupy
    */
  def encodedSize(value: Int): Int = {
    require(value >= 0, s"VarInt encoding requires non-negative value, got: $value")

    if value == 0 then return 1

    var remaining = value
    var size = 0

    while remaining > 0 do
      remaining = remaining >>> 7
      size += 1
    end while

    size
  }

  /** Calculate the encoded size of a long without encoding it
    *
    * @param value The value to measure
    * @return Number of bytes the encoded varint would occupy
    */
  def encodedSizeLong(value: Long): Int = {
    require(value >= 0L, s"VarInt encoding requires non-negative value, got: $value")

    if value == 0L then return 1

    var remaining = value
    var size = 0

    while remaining > 0L do
      remaining = remaining >>> 7
      size += 1
    end while

    size
  }
}
