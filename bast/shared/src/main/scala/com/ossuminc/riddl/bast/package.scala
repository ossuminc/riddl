/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

/** Binary AST (BAST) serialization package
  *
  * BAST provides efficient binary serialization of RIDDL AST nodes (specifically Nebula)
  * for fast loading via the `import` keyword. The format is optimized for quick
  * deserialization at the expense of write speed.
  *
  * Key features:
  * - Compact binary format with string interning
  * - Delta-encoded location data
  * - Variable-length integer encoding
  * - Cross-platform compatible (JVM, JS, Native)
  * - Versioned format for schema evolution
  *
  * Usage:
  * {{{
  *   // Writing BAST
  *   val nebula: Nebula = ...
  *   val writer = new BASTWriter()
  *   val bytes = writer.write(nebula)
  *
  *   // Reading BAST
  *   val reader = new BASTReader(bytes)
  *   val loadedNebula = reader.read()
  * }}}
  */
package object bast {

  /** BAST format version (major.minor)
    *
    * Major version changes indicate breaking format changes.
    * Minor version changes are backward compatible.
    */
  val VERSION_MAJOR: Short = 1
  val VERSION_MINOR: Short = 0

  /** Magic bytes for BAST file identification: "BAST" */
  val MAGIC_BYTES: Array[Byte] = Array('B'.toByte, 'A'.toByte, 'S'.toByte, 'T'.toByte)

  /** Maximum supported BAST file size (1GB) */
  val MAX_BAST_SIZE: Int = 1000 * 1024 * 1024

  /** Header size in bytes */
  val HEADER_SIZE: Int = 32

  // Node type tags - must fit in single byte (0-255)
  // Core AST nodes
  val NODE_NEBULA: Byte = 1
  val NODE_DOMAIN: Byte = 2
  val NODE_CONTEXT: Byte = 3
  val NODE_ENTITY: Byte = 4
  val NODE_TYPE: Byte = 5
  val NODE_FUNCTION: Byte = 6
  val NODE_ADAPTOR: Byte = 7
  val NODE_SAGA: Byte = 8
  val NODE_EPIC: Byte = 9
  val NODE_PROCESSOR: Byte = 10
  val NODE_PROJECTOR: Byte = 11
  val NODE_REPOSITORY: Byte = 12
  val NODE_STREAMLET: Byte = 13
  val NODE_CONNECTOR: Byte = 14
  val NODE_HANDLER: Byte = 15
  val NODE_STATE: Byte = 16
  val NODE_INVARIANT: Byte = 17
  val NODE_TERM: Byte = 18
  val NODE_AUTHOR: Byte = 19
  val NODE_USER: Byte = 20
  val NODE_GROUP: Byte = 21
  val NODE_INPUT: Byte = 22
  val NODE_OUTPUT: Byte = 23
  val NODE_INLET: Byte = 24
  val NODE_OUTLET: Byte = 25
  val NODE_PIPE: Byte = 26
  val NODE_PLANT: Byte = 27
  val NODE_APPLICATION: Byte = 28
  val NODE_MODULE: Byte = 29
  val NODE_FIELD: Byte = 30
  val NODE_ENUMERATOR: Byte = 31
  val NODE_ON_CLAUSE: Byte = 32
  val NODE_INCLUDE: Byte = 33
  val NODE_SAGA_STEP: Byte = 34
  val NODE_SCHEMA: Byte = 35

  // Metadata nodes
  val NODE_DESCRIPTION: Byte = 40
  val NODE_BLOCK_DESCRIPTION: Byte = 41
  val NODE_COMMENT: Byte = 42
  val NODE_BLOCK_COMMENT: Byte = 43

  // Type expressions
  val TYPE_STRING: Byte = 50
  val TYPE_BOOL: Byte = 51
  val TYPE_NUMBER: Byte = 52
  val TYPE_PATTERN: Byte = 53
  val TYPE_AGGREGATION: Byte = 54
  val TYPE_ALTERNATION: Byte = 55
  val TYPE_ENUMERATION: Byte = 56
  val TYPE_MAPPING: Byte = 57
  val TYPE_REF: Byte = 58
  val TYPE_OPTIONAL: Byte = 59
  val TYPE_ONE_OR_MORE: Byte = 60
  val TYPE_ZERO_OR_MORE: Byte = 61
  val TYPE_RANGE: Byte = 62
  val TYPE_UNIQUE_ID: Byte = 63

  // Adaptor directions
  val ADAPTOR_INBOUND: Byte = 70
  val ADAPTOR_OUTBOUND: Byte = 71

  // Streamlet shapes
  val STREAMLET_SOURCE: Byte = 80
  val STREAMLET_SINK: Byte = 81
  val STREAMLET_FLOW: Byte = 82
  val STREAMLET_MERGE: Byte = 83
  val STREAMLET_SPLIT: Byte = 84
  val STREAMLET_VOID: Byte = 85

  // Other nodes
  val NODE_IDENTIFIER: Byte = 100
  val NODE_PATH_IDENTIFIER: Byte = 101
  val NODE_LOCATION: Byte = 102
  val NODE_LITERAL_STRING: Byte = 103

  /** Flags for header */
  object Flags {
    val COMPRESSED: Short = 0x0001       // Reserved for future compression
    val WITH_LOCATIONS: Short = 0x0002   // Location data included
    val WITH_COMMENTS: Short = 0x0004    // Comments included
    val WITH_DESCRIPTIONS: Short = 0x0008 // Descriptions included
  }
}
