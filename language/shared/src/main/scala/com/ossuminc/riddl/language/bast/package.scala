/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

/** Binary AST (BAST) serialization package
  *
  * BAST provides efficient binary serialization of RIDDL AST nodes (specifically Nebula)
  * for fast loading via the `import` keyword. The format is optimized for quick
  * deserialization at the expense of write speed.
  *
  * Key features:
  * - Compact binary format with string interning
  * - Path identifier interning for repeated paths (Phase 8)
  * - Delta-encoded location data with zigzag encoding
  * - Variable-length integer encoding (LEB128)
  * - Cross-platform compatible (JVM, JS, Native)
  * - Versioned format for schema evolution
  *
  * File Structure:
  * {{{
  *   Header (32 bytes)
  *   String Table (varint count + strings)
  *   Path Table (varint count + path entries)  <- Phase 8
  *   Root Nebula Node (tree of nodes)
  * }}}
  *
  * Usage:
  * {{{
  *   // Writing BAST (via BASTWriterPass)
  *   val nebula: Nebula = ...
  *   val pass = new BASTWriterPass()
  *   val bytes = pass.run(nebula)
  *
  *   // Reading BAST
  *   val reader = new BASTReader(bytes)
  *   val loadedNebula = reader.read()
  * }}}
  */
package object bast {

  /** BAST format version - single monotonically incrementing integer.
    *
    * TODO: Finalize BAST schema before releasing to users. Until then,
    * version stays at 1 even as format evolves during development.
    *
    * When finalizing, document the schema and increment version for
    * any future breaking changes.
    */
  val VERSION: Int = 1

  /** Magic bytes for BAST file identification: "BAST" */
  val MAGIC_BYTES: Array[Byte] = Array('B'.toByte, 'A'.toByte, 'S'.toByte, 'T'.toByte)

  /** Maximum supported BAST file size (1GB) */
  val MAX_BAST_SIZE: Int = 1000 * 1024 * 1024

  /** Header size in bytes */
  val HEADER_SIZE: Int = 32

  // Node type tags - compact sequential numbering (1-67)
  // Must fit in single byte (0-255)
  // Tag 0 is reserved for FILE_CHANGE_MARKER

  /** Special marker indicating source file change (tag 0, unused as node tag)
    * Written before a node when its source file differs from the current one.
    * Format: FILE_CHANGE_MARKER (0) + path string
    */
  val FILE_CHANGE_MARKER: Byte = 0

  // Definitions (1-33)
  val NODE_NEBULA: Byte = 1
  val NODE_DOMAIN: Byte = 2
  val NODE_CONTEXT: Byte = 3
  val NODE_ENTITY: Byte = 4
  val NODE_TYPE: Byte = 5
  val NODE_FUNCTION: Byte = 6
  val NODE_ADAPTOR: Byte = 7
  val NODE_SAGA: Byte = 8
  val NODE_EPIC: Byte = 9
  val NODE_PROJECTOR: Byte = 10
  val NODE_REPOSITORY: Byte = 11
  val NODE_STREAMLET: Byte = 12
  val NODE_CONNECTOR: Byte = 13
  val NODE_HANDLER: Byte = 14
  val NODE_STATE: Byte = 15
  val NODE_INVARIANT: Byte = 16
  val NODE_TERM: Byte = 17
  val NODE_AUTHOR: Byte = 18
  val NODE_USER: Byte = 19
  val NODE_GROUP: Byte = 20
  val NODE_INPUT: Byte = 21
  val NODE_OUTPUT: Byte = 22
  val NODE_INLET: Byte = 23
  val NODE_OUTLET: Byte = 24
  val NODE_PIPE: Byte = 25
  val NODE_MODULE: Byte = 26
  val NODE_FIELD: Byte = 27
  val NODE_ENUMERATOR: Byte = 28
  val NODE_ON_CLAUSE: Byte = 29
  val NODE_INCLUDE: Byte = 30
  val NODE_SAGA_STEP: Byte = 31
  val NODE_SCHEMA: Byte = 32
  val NODE_BAST_IMPORT: Byte = 33

  // Metadata nodes (34-37)
  val NODE_DESCRIPTION: Byte = 34
  val NODE_BLOCK_DESCRIPTION: Byte = 35
  val NODE_COMMENT: Byte = 36
  val NODE_BLOCK_COMMENT: Byte = 37

  // Message References - dedicated tags (38-42)
  val NODE_COMMAND_REF: Byte = 38
  val NODE_EVENT_REF: Byte = 39
  val NODE_QUERY_REF: Byte = 40
  val NODE_RESULT_REF: Byte = 41
  val NODE_RECORD_REF: Byte = 42

  // Type expressions (43-56)
  val TYPE_STRING: Byte = 43
  val TYPE_BOOL: Byte = 44
  val TYPE_NUMBER: Byte = 45
  val TYPE_PATTERN: Byte = 46
  val TYPE_AGGREGATION: Byte = 47
  val TYPE_ALTERNATION: Byte = 48
  val TYPE_ENUMERATION: Byte = 49
  val TYPE_MAPPING: Byte = 50
  val TYPE_REF: Byte = 51
  val TYPE_OPTIONAL: Byte = 52
  val TYPE_ONE_OR_MORE: Byte = 53
  val TYPE_ZERO_OR_MORE: Byte = 54
  val TYPE_RANGE: Byte = 55
  val TYPE_UNIQUE_ID: Byte = 56

  // Adaptor directions (57-58)
  val ADAPTOR_INBOUND: Byte = 57
  val ADAPTOR_OUTBOUND: Byte = 58

  // Streamlet shapes (59-64)
  val STREAMLET_SOURCE: Byte = 59
  val STREAMLET_SINK: Byte = 60
  val STREAMLET_FLOW: Byte = 61
  val STREAMLET_MERGE: Byte = 62
  val STREAMLET_SPLIT: Byte = 63
  val STREAMLET_VOID: Byte = 64

  // Simple values (65-67) - kept for polymorphic cases
  val NODE_IDENTIFIER: Byte = 65
  val NODE_PATH_IDENTIFIER: Byte = 66
  val NODE_LITERAL_STRING: Byte = 67

  // Statement node (68) - Phase 7: dedicated tag for statements
  // Distinguishes statements from handlers without needing a peek-ahead marker
  val NODE_STATEMENT: Byte = 68

  // Predefined type expressions (69-79) - Phase 7 optimization
  // These are common types with no parameters that save the subtype byte
  val TYPE_INTEGER: Byte = 69      // Saves: TYPE_NUMBER + subtype(1)
  val TYPE_NATURAL: Byte = 70      // Saves: TYPE_NUMBER + subtype(3)
  val TYPE_WHOLE: Byte = 71        // Saves: TYPE_NUMBER + subtype(2)
  val TYPE_REAL: Byte = 72         // Saves: TYPE_NUMBER + subtype(11)
  val TYPE_STRING_DEFAULT: Byte = 73 // Saves: TYPE_STRING + subtype(0) + 2 option bytes
  val TYPE_UUID: Byte = 74         // Saves: TYPE_UNIQUE_ID + subtype(1)
  val TYPE_DATE: Byte = 75         // Saves: TYPE_NUMBER + subtype(30)
  val TYPE_TIME: Byte = 76         // Saves: TYPE_NUMBER + subtype(31)
  val TYPE_DATETIME: Byte = 77     // Saves: TYPE_NUMBER + subtype(32)
  val TYPE_TIMESTAMP: Byte = 78    // Saves: TYPE_NUMBER + subtype(35)
  val TYPE_DURATION: Byte = 79     // Saves: TYPE_NUMBER + subtype(36)

  /** Flag bit indicating metadata presence in node tag
    *
    * Phase 7 optimization: Use high bit (0x80) of tag byte to indicate
    * whether a node has metadata. If set, metadata count follows;
    * if not set, no metadata is present (saves 1 byte per empty metadata).
    *
    * Tag encoding:
    * - Bits 0-6: Node type (0-127, we only use 0-67)
    * - Bit 7: Has metadata flag (1 = has metadata, 0 = no metadata)
    */
  val HAS_METADATA_FLAG: Byte = 0x80.toByte

  /** Flags for header */
  object Flags {
    val COMPRESSED: Short = 0x0001       // Reserved for future compression
    val WITH_LOCATIONS: Short = 0x0002   // Location data included
    val WITH_COMMENTS: Short = 0x0004    // Comments included
    val WITH_DESCRIPTIONS: Short = 0x0008 // Descriptions included
  }
}
