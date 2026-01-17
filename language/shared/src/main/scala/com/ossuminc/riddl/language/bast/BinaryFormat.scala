/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

/** Binary format specification for BAST files
  *
  * File Structure:
  * {{{
  * ┌─────────────────────────────────────┐
  * │ Header (32 bytes)                   │
  * │  - Magic: "BAST" (4 bytes)          │
  * │  - Version: u32                     │
  * │  - Flags: u16                       │
  * │  - Reserved1: u16                   │
  * │  - String Table Offset: u32         │
  * │  - Root Offset: u32                 │
  * │  - File Size: u32                   │
  * │  - Checksum: u32                    │
  * │  - Reserved2: (4 bytes)             │
  * ├─────────────────────────────────────┤
  * │ String Interning Table              │
  * │  - Count: varint                    │
  * │  - [Length: varint, UTF-8 bytes]... │
  * ├─────────────────────────────────────┤
  * │ Nebula Root Node                    │
  * │  - Node Type: u8                    │
  * │  - Location: compressed (optional)  │
  * │  - Contents Count: varint           │
  * │  - Contents: [Node...]              │
  * └─────────────────────────────────────┘
  * }}}
  *
  * Variable-length integers (varint) use LEB128 encoding:
  * - Values 0-127: 1 byte
  * - Values 128-16383: 2 bytes
  * - Etc.
  *
  * Location encoding (zigzag delta-compressed):
  * - Source path: string table index
  * - Offset delta: zigzag varint (difference from previous offset)
  * - EndOffset delta: zigzag varint (difference from previous endOffset)
  */
object BinaryFormat {

  /** BAST file header structure */
  case class Header(
    magic: Array[Byte],           // Must equal MAGIC_BYTES
    version: Int,                 // Single monotonically incrementing version
    flags: Short,
    stringTableOffset: Int,
    rootOffset: Int,
    fileSize: Int,
    checksum: Int,
    reserved: Array[Byte]         // 4 bytes reserved for future use
  ) {
    def isValid: Boolean = {
      magic.sameElements(MAGIC_BYTES) &&
      version == VERSION &&
      fileSize > 0 &&
      fileSize <= MAX_BAST_SIZE
    }

    def hasLocations: Boolean = (flags & Flags.WITH_LOCATIONS) != 0
    def hasComments: Boolean = (flags & Flags.WITH_COMMENTS) != 0
    def hasDescriptions: Boolean = (flags & Flags.WITH_DESCRIPTIONS) != 0
  }

  object Header {
    def apply(
      stringTableOffset: Int,
      rootOffset: Int,
      fileSize: Int,
      checksum: Int,
      flags: Short = (Flags.WITH_LOCATIONS | Flags.WITH_DESCRIPTIONS).toShort
    ): Header = {
      Header(
        magic = MAGIC_BYTES,
        version = VERSION,
        flags = flags,
        stringTableOffset = stringTableOffset,
        rootOffset = rootOffset,
        fileSize = fileSize,
        checksum = checksum,
        reserved = new Array[Byte](4)
      )
    }
  }

  /** Calculate simple checksum (CRC32 alternative for cross-platform compatibility) */
  def calculateChecksum(bytes: Array[Byte], start: Int, length: Int): Int = {
    var checksum = 0
    var i = start
    val end = start + length
    while i < end do
      checksum = ((checksum << 5) - checksum) + (bytes(i) & 0xFF)
      i += 1
    end while
    checksum
  }

  /** Serialize a header to bytes
    *
    * @param header The header to serialize
    * @return 32-byte array containing the serialized header
    */
  def serializeHeader(header: Header): Array[Byte] = {
    val writer = new ByteBufferWriter()
    writer.writeRawBytes(header.magic)       // 4 bytes
    writer.writeInt(header.version)           // 4 bytes
    writer.writeShort(header.flags)           // 2 bytes
    writer.writeShort(0)                      // 2 bytes reserved
    writer.writeInt(header.stringTableOffset) // 4 bytes
    writer.writeInt(header.rootOffset)        // 4 bytes
    writer.writeInt(header.fileSize)          // 4 bytes
    writer.writeInt(header.checksum)          // 4 bytes
    writer.writeRawBytes(header.reserved)     // 4 bytes reserved
    writer.toByteArray
  }
}
