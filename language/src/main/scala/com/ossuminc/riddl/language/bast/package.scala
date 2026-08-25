/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

/** Binary AST (BAST) serialization package
  *
  * BAST provides efficient binary serialization of RIDDL AST nodes (specifically Nebula) for fast
  * loading via the `import` keyword. The format is optimized for quick deserialization at the
  * expense of write speed.
  *
  * Key features:
  *   - Compact binary format with string interning
  *   - Path identifier interning for repeated paths (Phase 8)
  *   - Delta-encoded location data with zigzag encoding
  *   - Variable-length integer encoding (LEB128)
  *   - Cross-platform compatible (JVM, JS, Native)
  *   - Versioned format for schema evolution
  *
  * File Structure:
  * {{{
  *   Header (32 bytes)
  *   Root Node (tree of nodes)                 <- at header.rootOffset, always HEADER_SIZE
  *   String Table (varint count + strings)     <- at header.stringTableOffset
  *   Path Table (varint count + path entries)  <- Phase 8, immediately after the string table
  * }}}
  * The interning tables trail the node tree because the writer is single-pass: it discovers the
  * strings and paths to intern while writing the nodes, so it cannot emit the tables until the tree
  * is complete. The reader does not care — it seeks by way of the header's offsets, reading the
  * tables (`header.stringTableOffset`) before the tree (`header.rootOffset`). Do NOT infer the
  * position of either from the header size alone.
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

  /** BAST format version — the released schema generation.
    *
    * Version 1 was RIDDL 1.x, where the schema was explicitly provisional: it stayed at 1 while the
    * format evolved during development, on the understanding that it would be incremented when the
    * schema was finalized for users. **RIDDL 2.0 is that moment**, so this is 2.
    *
    * Increment it again only for a released schema change; use [[FORMAT_REVISION]] for changes
    * within a generation.
    */
  val VERSION: Int = 2

  /** Format revision — incremented for any internal serialization change (node tags, encoding,
    * table layout) WITHIN a [[VERSION]]. A file whose revision differs is rejected with a clear
    * message rather than misread.
    *
    * Reset to 1 for version 2. Version 1 reached revision 25, but revisions 13 through 25 were
    * consumed entirely inside the unreleased release/2 branch — no file carrying one was ever
    * published, and the last revision anyone outside has is 12 (RIDDL 1.31). Carrying the
    * development churn of one branch into a user-facing constant would have meant a 1.31 file
    * reporting "revision 12 does not match expected revision 25", implying twelve generations the
    * user might have files from and cannot.
    *
    * Numbering restarts safely ONLY because the version moved with it: `Header.isValid` checks
    * version first, and no file in existence carries version 2, so an old revision-13 file cannot
    * be mistaken for a new one. Do NOT renumber again within version 2 — that WOULD reuse numbers
    * that real files carry.
    */
  val FORMAT_REVISION: Short =
    // Connector intentions: `writeConnector` now appends a count plus one byte per intention after
    // the inlet ref, so every byte following a Connector in a revision-12 file would be misread.
    // 13 was connector intentions; 14 adds distinct tags for Constant and Method, which had
    // been sharing NODE_FIELD and corrupting every byte that followed one.
    // 15 adds the Id kind keyword, `self`, `initiate`, `terminate`, on-clause parameter lists
    // and the tell `by` clause -- every one of which appends bytes an older reader would leave
    // in the stream.
    // 16 fixes three latent corruptions in the SAME family as revision 14's Constant/Method fix,
    // found together 2026-08-14 while chasing riddl-models' `sequence`/`parallel`/`optional`
    // report: (1) InteractionContainer (the three block kinds) wrote a contents COUNT that no
    // pass ever followed with the actual items, understating node counts as content was ADDED;
    // (2) `invariant ... is { <statements> <predicate> }` had the identical gap for its block
    // statements; (3) `relationship`, sharing NODE_PIPE with the 13 Interaction kinds, wrote no
    // discriminator byte at all, so the reader silently misread every relationship's location as
    // its own dispatch byte. A revision-15 reader cannot decode files written after this fix, and
    // files this fix produces are unreadable by any older reader -- for all three reasons at once.
    // 17 widens two operand codecs. `morph … with` gains discriminator 2 (a ValueRef) on the
    // RECORD operand, which a revision-16 reader rejects outright as an invalid discriminator; and
    // `yield`/`reply` may now legitimately carry the MESSAGE operand's discriminator 2, which a
    // revision-16 reader THROWS on by design -- its arms said `yield` never accepts a bound name,
    // true of the parser at the time. So the incompatibility runs both ways and is deliberate.
    // 18 adds numeric literals -- value tag 10 and comparand discriminator 3, both of which a
    // revision-17 reader rejects as invalid -- and changes `Constant` to write a full tagged
    // VALUE rather than a bare literal string, so a revision-17 reader misreads the discriminator
    // byte as the start of the string and derails on everything after it. Incompatible both ways,
    // deliberately. This bump is SHARED: BACKLOG § 2 reserves ONE bump for numeric literals, A20
    // typed holes and A38. It is now SPENT -- neither of those may move it again.
    // Also riding 18 (2026-08-15): `writeURL`/`readURL` now write/read all FOUR `URL` fields
    // (scheme, authority, basis, path) instead of just basis+path. A revision-17 reader still
    // decodes the bytes without error -- it just silently drops the leading scheme+authority
    // strings and rebuilds every URL as `file:///<path>`, which is how `shown by
    // https://ossum.tech/...` came back as `file:///...` and is not a `ShownBy`-specific
    // defect: every URL through BAST was affected, `described at <url>` only escaped notice
    // because riddl-models' one instance happened to be a file/relative URL already. Folded
    // into 18 rather than bumping to 19 because 18 has not shipped in a release yet, so no
    // `.bast` file in the wild carries the old two-field shape to be misread.
    // Also riding 18 (2026-08-15): A20 typed holes -- `PromptValue` (writeValue/readValue
    // discriminator 4) APPENDS an optional `as <type>` ascription (`writeOption` +
    // `writeTypeExpression`) after the prompt literal. An untyped prompt now carries one extra
    // `0x00` "none" byte; a revision-17 reader has no idea this byte exists and misreads it as
    // the start of whatever comes next, derailing every byte after the first PromptValue it
    // decodes -- same failure shape as the Constant/Method and numeric-literal changes above.
    // Also riding 18 (2026-08-15, not/! synonymy task 4): `writeWhenStatement` no longer appends
    // the legacy negated-flag byte after the condition -- negation is fully carried by the
    // `NotExpression` inside `condition` (discriminator `2`) since task 1, so the byte task 2 had
    // been writing as a hardcoded `0` placeholder is deleted outright, not merely zeroed. A reader
    // still expecting it would consume the FIRST byte of whatever follows (the `thenStatements`
    // contents count, or -- for an empty `condition` payload with no trailing bytes of its own --
    // the next node's tag) as that flag, then read everything downstream one byte short for the
    // rest of the WhenStatement's subtree. No shipped `.bast` carries the placeholder byte (it was
    // only ever written by task 2's still-unreleased code), so this is folded into revision 18
    // rather than bumping to 19.
    // 18 also carries the `at` lookup value (value tag 11, comparand tag 4, 2026-08-17). It RIDES
    // 18 rather than bumping to 19 because 18 has not shipped -- the latest tag is 2.0.0-rc.14,
    // which is revision 17 -- so no file in anyone's hands carries an 18 without it. Bump to 19
    // for the next BAST change made AFTER 18 ships.
    // 18 also carries A38's refusal reason (2026-08-17), which is the LAST of the three uses this
    // bump was reserved for. `writeRefusalInteraction` now writes a discriminator byte (0 = prose,
    // 1 = invariant reference) where a bare literal string used to begin, so a revision-17 reader
    // consumes that byte as the head of the string's length and derails on everything after it.
    //
    // **18 HAS SHIPPED, in 2.0.0-rc.15 (2026-08-17). THE NEXT BAST CHANGE MUST BUMP TO 19.**
    // Every "rides 18 because 18 has not shipped" argument above is now HISTORY, not a licence --
    // it was sound only while no file in anyone's hands carried an 18, and files carrying one now
    // exist. Riding 18 again would make two mutually unreadable wire formats share a revision
    // number, which is precisely the state the revision gate exists to prevent, and it would fail
    // SILENTLY: the gate would pass and the reader would misalign.
    // 19 adds the `forward` statement (sub-kind 21), whose payload is a message operand followed
    // by ONE discriminator byte (0 = portlet, 1 = processor) and then that reference. A revision-18
    // reader has no arm for sub-kind 21 at all, so it THROWS rather than misreading -- which is the
    // good failure, and the reason the reader's default arm was made to throw instead of
    // fabricating a PromptStatement. Bumped rather than ridden because 18 SHIPPED in 2.0.0-rc.15.
    22 // `system` value: tag 13 in readValue/writeValue. A revision-21 reader hitting tag 13 throws
    // rather than misreading, which is what the revision gate is for.
    // 21 // `empty` value: tag 12 in readValue/writeValue. A revision-20 reader hitting tag 12 throws
    // rather than misreading, but the gate is what makes that a clean failure.
    // 20 was the tell target: sub-kind 9 now carries a processor/value discriminator before the target,
    // so a revision-19 reader would take a value's bytes as a processor ref and misalign
    // everything after it. 19 was the forward statement (sub-kind 21).
    // typed holes + WhenStatement drops the legacy negated-flag byte + A38 refusal reason

  /** Constants and Methods used to share [[NODE_FIELD]] with Field, distinguished by nothing.
    *
    * Both write MORE than a Field does -- a Constant appends its literal value, a Method appends
    * its argument list -- and the reader, which could not tell them apart, read a Field and
    * stopped. Every byte after such a node was then misread, surfacing far away as "Invalid string
    * table index" or a garbage tag. The reader carried the admission in a comment: "This is
    * ambiguous ... For now, assume Field. Writer should disambiguate better." It now does.
    */
  val NODE_CONSTANT: Byte = 109
  val NODE_METHOD: Byte = 110

  /** Magic bytes for BAST file identification: "BAST" */
  val MAGIC_BYTES: Array[Byte] = Array('B'.toByte, 'A'.toByte, 'S'.toByte, 'T'.toByte)

  /** Maximum supported BAST file size (1GB) */
  val MAX_BAST_SIZE: Int = 1000 * 1024 * 1024

  /** Header size in bytes */
  val HEADER_SIZE: Int = 32

  // Node type tags - compact sequential numbering (1-67)
  // Must fit in single byte (0-255)
  // Tag 0 is reserved for FILE_CHANGE_MARKER

  /** Special marker indicating source file change (tag 0, unused as node tag) Written before a node
    * when its source file differs from the current one. Format: FILE_CHANGE_MARKER (0) + path
    * string
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

  // Streamlet shapes (59-64, plus STREAMLET_ROUTER at 102)
  val STREAMLET_SOURCE: Byte = 59
  val STREAMLET_SINK: Byte = 60
  val STREAMLET_FLOW: Byte = 61
  val STREAMLET_MERGE: Byte = 62
  val STREAMLET_SPLIT: Byte = 63
  val STREAMLET_VOID: Byte = 64
  // Router previously shared STREAMLET_VOID's tag (latent bug: Router read back as Void).
  // A distinct tag makes Split/Router/Void round-trip correctly. Placed at 102 (next free byte)
  // to avoid renumbering the existing shape block.
  val STREAMLET_ROUTER: Byte = 102

  // Simple values (65-67) - kept for polymorphic cases
  val NODE_IDENTIFIER: Byte = 65
  val NODE_PATH_IDENTIFIER: Byte = 66
  val NODE_LITERAL_STRING: Byte = 67

  // Statement node (68) - Phase 7: dedicated tag for statements
  // Distinguishes statements from handlers without needing a peek-ahead marker
  val NODE_STATEMENT: Byte = 68

  // Predefined type expressions (69-79) - Phase 7 optimization
  // These are common types with no parameters that save the subtype byte
  val TYPE_INTEGER: Byte = 69 // Saves: TYPE_NUMBER + subtype(1)
  val TYPE_NATURAL: Byte = 70 // Saves: TYPE_NUMBER + subtype(3)
  val TYPE_WHOLE: Byte = 71 // Saves: TYPE_NUMBER + subtype(2)
  val TYPE_REAL: Byte = 72 // Saves: TYPE_NUMBER + subtype(11)
  val TYPE_STRING_DEFAULT: Byte = 73 // Saves: TYPE_STRING + subtype(0) + 2 option bytes
  val TYPE_UUID: Byte = 74 // Saves: TYPE_UNIQUE_ID + subtype(1)
  val TYPE_DATE: Byte = 75 // Saves: TYPE_NUMBER + subtype(30)
  val TYPE_TIME: Byte = 76 // Saves: TYPE_NUMBER + subtype(31)
  val TYPE_DATETIME: Byte = 77 // Saves: TYPE_NUMBER + subtype(32)
  val TYPE_TIMESTAMP: Byte = 78 // Saves: TYPE_NUMBER + subtype(35)
  val TYPE_DURATION: Byte = 79 // Saves: TYPE_NUMBER + subtype(36)

  // Reference node tags (80-105) - Distinct from definition tags
  // Refs have different structure: loc + pathId (no contents or metadata)
  val NODE_AUTHOR_REF: Byte = 80
  val NODE_TYPE_REF: Byte = 81
  val NODE_FIELD_REF: Byte = 82
  val NODE_CONSTANT_REF: Byte = 83
  val NODE_ADAPTOR_REF: Byte = 84
  val NODE_FUNCTION_REF: Byte = 85
  val NODE_HANDLER_REF: Byte = 86
  val NODE_STATE_REF: Byte = 87
  val NODE_ENTITY_REF: Byte = 88
  val NODE_REPOSITORY_REF: Byte = 89
  val NODE_PROJECTOR_REF: Byte = 90
  val NODE_CONTEXT_REF: Byte = 91
  val NODE_STREAMLET_REF: Byte = 92
  val NODE_INLET_REF: Byte = 93
  val NODE_OUTLET_REF: Byte = 94
  val NODE_SAGA_REF: Byte = 95
  val NODE_USER_REF: Byte = 96
  val NODE_EPIC_REF: Byte = 97
  val NODE_GROUP_REF: Byte = 98
  val NODE_INPUT_REF: Byte = 99
  val NODE_OUTPUT_REF: Byte = 100
  val NODE_DOMAIN_REF: Byte = 101

  /** A53: a Version leaf. Tag 102 is taken in the streamlet-shape namespace, so nodes resume at
    * 103.
    */
  val NODE_VERSION: Byte = 103

  /** A47: a Copyright leaf. */
  val NODE_COPYRIGHT: Byte = 104

  /** A42: a FigmaRef metadata item. */
  val NODE_FIGMA_REF: Byte = 105

  /** A9: the `requires` clause of a [[AST.Function]] or [[AST.Saga]]. These used to be written as
    * two optional FIELDS ahead of the contents; they are now ordinary contents nodes, so that
    * comments may sit before or between them. Revision 4.
    */
  val NODE_REQUIRES: Byte = 106
  val NODE_RETURNS: Byte = 107

  /** A70: a [[AST.Correlation]] inside a [[AST.Projector]]. */
  val NODE_CORRELATION: Byte = 108

  /** Flag bit indicating metadata presence in node tag
    *
    * Phase 7 optimization: Use high bit (0x80) of tag byte to indicate whether a node has metadata.
    * If set, metadata count follows; if not set, no metadata is present (saves 1 byte per empty
    * metadata).
    *
    * Tag encoding:
    *   - Bits 0-6: Node type (0-127, we only use 0-67)
    *   - Bit 7: Has metadata flag (1 = has metadata, 0 = no metadata)
    */
  val HAS_METADATA_FLAG: Byte = 0x80.toByte

  /** Flags for header */
  object Flags {
    val COMPRESSED: Short = 0x0001 // Reserved for future compression
    val WITH_LOCATIONS: Short = 0x0002 // Location data included
    val WITH_COMMENTS: Short = 0x0004 // Comments included
    val WITH_DESCRIPTIONS: Short = 0x0008 // Descriptions included
  }
}
