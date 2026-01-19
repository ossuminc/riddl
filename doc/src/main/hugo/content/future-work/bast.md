---
title: "Binary AST (BAST)"
type: "page"
weight: 10
draft: "false"
---

When the `riddlc` compiler parses a RIDDL document, it translates it to an Abstract
Syntax Tree (AST) in memory. The AST is then used by other passes to validate and
translate the AST into other forms.

## BAST Format

The Binary AST (BAST) format is a compact binary serialization of a validated AST.
BAST files are designed for:

- **Fast loading**: 10-50x faster than parsing RIDDL source
- **Compact storage**: Uses string interning, path interning, and variable-length encoding
- **Cross-platform compatibility**: Works on JVM, JavaScript, and Native platforms

### File Structure

```
┌─────────────────────────────────────┐
│ Header (32 bytes)                   │
│  - Magic: "BAST" (4 bytes)          │
│  - Version: u32                     │
│  - Flags: u16                       │
│  - String Table Offset: u32         │
│  - Path Table Offset: u32           │
│  - Root Offset: u32                 │
│  - File Size: u32                   │
│  - Checksum: u32                    │
├─────────────────────────────────────┤
│ String Interning Table              │
│  - Count: varint                    │
│  - [Length: varint, UTF-8 bytes]... │
├─────────────────────────────────────┤
│ Path Interning Table                │
│  - Count: varint                    │
│  - [Path indices...]...             │
├─────────────────────────────────────┤
│ Nebula Root Node                    │
│  - Node Type: u8 (tag)              │
│  - Location: delta-compressed       │
│  - Contents: [Node...]              │
└─────────────────────────────────────┘
```

## Import Syntax

BAST files can be imported into RIDDL source files using the `import` statement:

```riddl
import "library.bast"

domain MyApp is {
  // Reference imported definitions via their path
  type UserId is ImportedDomain.CommonTypes.UUID
}
```

Imports can appear:
- At the root level of a file (before any definitions)
- Inside domain definitions

## Generating BAST Files

BAST files are generated using `riddlc` (not yet implemented in CLI, available via API):

```scala
// Generate BAST from a parsed and validated AST
val result = Riddl.parse(source)
result.foreach { root =>
  BASTWriterPass.write(root, outputPath)
}
```

## Implementation Status

The BAST format has been implemented in Phases 1-8:

- ✅ Phase 1: Infrastructure (ByteBuffer readers/writers, VarInt codec)
- ✅ Phase 2: Core Serialization (StringTable, all node serializers)
- ✅ Phase 3: Deserialization (BASTReader, round-trip tests)
- ✅ Phase 4: Import Integration (BASTLoader, import parsing)
- ✅ Phase 5-8: Optimizations (delta encoding, inline methods, path interning)

### Files

Core implementation files:
- `language/shared/.../bast/package.scala` - Constants and node tags
- `language/shared/.../bast/StringTable.scala` - String interning
- `language/shared/.../bast/PathTable.scala` - Path interning
- `language/shared/.../bast/BASTWriter.scala` - Serialization utilities
- `language/shared/.../bast/BASTReader.scala` - Deserialization
- `language/shared/.../bast/BASTLoader.scala` - Import loading

Pass:
- `passes/shared/.../passes/BASTWriterPass.scala` - Serialization pass

## Remaining Work

- CLI command `riddlc bast-gen` for generating BAST files
- Command-line flags: `--use-bast-cache`, `--bast-dir`
- Comprehensive benchmarks and optimization
