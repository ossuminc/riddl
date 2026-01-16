# Engineering Notebook: RIDDL

This is the central engineering notebook for the RIDDL project. It tracks current status, work completed, design decisions, and next steps across all modules.

---

## Current Status

**Last Updated**: January 16, 2026

The RIDDL project is a mature compiler and toolchain for the Reactive Interface to Domain Definition Language. Recent work has focused on BAST (Binary AST) serialization for fast module imports.

---

## BAST Module (Binary AST Serialization)

### Status

**BAST serialization is fully integrated into the language and passes modules.**

The standalone `bast/` module has been **removed from build.sbt** and code reorganized:
- **Utility code** → `language/shared/src/main/scala/com/ossuminc/riddl/language/bast/`
- **BASTWriterPass** → `passes/shared/src/main/scala/com/ossuminc/riddl/passes/`

This enables BAST to work like Python's `.pyc` files - automatic loading from cache when available.

### Work Completed

- [x] **Phase 1**: Infrastructure - Module structure, format spec, varint codec, byte buffer reader/writer
- [x] **Phase 2**: Core Serialization - BASTWriter pass, all node types, string interning
- [x] **Phase 3**: Deserialization - BASTReader, round-trip verification, performance tests
- [x] **Phase 4**: Import Integration (Jan 15, 2026)
  - Simplified syntax: `import "file.bast"` (removed `as namespace` clause)
  - Support imports at root level AND inside domains
  - BASTLoader utility to load and populate contents
  - Path resolution verified: `ImportedDomain.SomeType` resolves correctly
- [x] **Phase 5**: Module Reorganization & Auto-Generation (Jan 15, 2026)
  - Removed standalone `bast/` module from build.sbt
  - Split BASTWriter: utility class in `language/bast/`, Pass wrapper in `passes/`
  - Added `BASTUtils.scala` with `checkForBastFile()`, `loadBAST()`, `tryLoadBastOrParseRiddl()`
  - Integrated automatic BAST loading into `TopLevelParser.parseURL()` and `parseInput()`
  - Added `--auto-generate-bast` / `-B` CLI option to riddlc
  - Implemented auto-generation in `Riddl.parse()` when option enabled
- [x] **Phase 6**: Bug Fixes & Test Consolidation (Jan 16, 2026)
  - Fixed Alternation deserialization bug (`readTypeExpressionContents` helper)
  - Created BASTIncrementalTest with 37 test cases
  - Verified all BAST tests migrated to passes module (54 tests)
  - Documented deprecated `bast/jvm/src/test/` files

### Key Technical Insight

**CRITICAL: `readNode()` vs `readTypeExpression()` in BASTReader**:
- `readNode()` handles **definition-level tags**: NODE_TYPE, NODE_DOMAIN, NODE_CONTEXT, etc.
- `readTypeExpression()` handles **type expression tags**: TYPE_REF, TYPE_ALTERNATION, etc.
- **These are DISJOINT sets** - readNode() does NOT handle TYPE_* tags!
- When reading contents containing type expressions (e.g., `Alternation.of`), use `readTypeExpression()`, not `readNode()`
- **Bug pattern**: "Invalid string table index" with huge counts usually means byte stream misalignment from reading TYPE_* tag as NODE_* tag

### Test Status (54 tests, all passing)

| Test Suite | Tests |
|------------|-------|
| BASTMinimalTest | 1 |
| BASTIncrementalTest | 37 |
| BASTWriterSpec | 5 |
| BASTRoundTripTest | 3 |
| BASTPerformanceBenchmark | 3 |
| BASTLoaderTest | 4 |
| BASTDebugTest | 1 |

**Note**: All tests are in `passes/jvm/src/test/`. The deprecated `bast/` directory has been removed.

### Key Code Locations

**Language Module** (`language/shared/.../language/bast/`):
- `package.scala` - Constants, node type tags (NODE_*, TYPE_*)
- `BASTWriter.scala` - Serialization utilities
- `BASTReader.scala` - Deserialization with `readNode()` and `readTypeExpression()`
- `BASTLoader.scala` - Import loading utility
- `BASTUtils.scala` - File checking, BAST loading helpers

**Passes Module** (`passes/shared/.../passes/`):
- `BASTWriterPass.scala` - Pass wrapper using AST traversal framework

**Commands Module** (`commands/jvm/.../commands/`):
- `BastGenCommand.scala` - `riddlc bast-gen` command

### Performance Results (January 16, 2026)

Benchmark on `dokn.riddl` (7.5KB, 167 nodes):

| Metric | Parse (text→AST) | Load (binary→AST) | Speedup |
|--------|------------------|-------------------|---------|
| Cold run | 141.15 ms | 16.97 ms | **8.3x** |
| Warm avg (50 iter) | 6.21 ms | 0.67 ms | **9.3x** |

**Conclusion**: BAST loading achieves ~9x speedup, close to the 10x target.

### Cross-Platform Status (January 16, 2026)

| Platform | Status | Notes |
|----------|--------|-------|
| JVM | ✅ Passing | 6 tests in SharedBASTTest |
| Native | ✅ Passing | 6 tests in SharedBASTTest |
| JS | ⚠️ Known limitation | No local file I/O on browser platform |

**Note**: JS cannot support BAST file loading because `DOMPlatformContext` throws `FileNotFoundException`
for `file://` URLs. This is an inherent platform limitation, not a bug. BAST import loading is a JVM/Native feature.

### Next Steps

1. Consider larger test corpus for comprehensive benchmarks
2. Document BAST format specification

### Open Questions

- How should BAST versioning handle breaking format changes?

---

## Design Decisions Log

| Decision | Rationale | Alternatives | Date |
|----------|-----------|--------------|------|
| No namespace syntax for imports | RIDDL uses nested domains for namespacing | `import "x.bast" as ns` | 2026-01-15 |
| Imports at root + domain only | Simplest useful locations | Root only, All containers | 2026-01-15 |
| BASTImport as Container | Resolution pass naturally traverses contents | Special handling | 2026-01-15 |
| Custom binary format | Memory-mappable, ~10x faster than parsing | FlatBuffers, Protobuf | 2026-01-10 |
| String interning | Deduplicates common strings | No interning | 2026-01-10 |
| Delta-encoded locations | ~70% space savings | Full coordinates | 2026-01-10 |

---

## Session Log

### January 16, 2026 (Cross-Platform Testing)

**Focus**: Cross-platform BAST testing (JS, Native)

**Completed**:
- Created `SharedBASTTest.scala` in `passes/shared/src/test/` with 6 tests
- Tests build AST programmatically (avoid parser's BAST import loading)
- JVM: All 6 tests pass ✅
- Native: All 6 tests pass ✅
- JS: Known platform limitation (no local file I/O)

**Conclusion**: BAST serialization/deserialization works correctly on JVM and Native.
JS cannot support BAST file loading because `DOMPlatformContext` throws `FileNotFoundException`
for `file://` URLs - this is an inherent browser platform limitation, not a bug.

### January 16, 2026 (Cleanup & Benchmarking)

**Focus**: Cleanup deprecated files and verify performance

**Completed**:
- Removed deprecated `bast/` directory (no longer in build.sbt, all code migrated)
- Ran performance benchmarks showing **9.3x speedup** (warmed up)
- Updated documentation with benchmark results

### January 16, 2026 (Continuation)

**Focus**: Test migration verification

**Completed**:
- Confirmed all BAST test files already exist in `passes/jvm/src/test/` with correct imports
- Files in `bast/jvm/src/test/` are deprecated duplicates using old `BASTWriter.creator()` API
- Authoritative tests use `BASTWriterPass.creator()` from the passes module
- All 54 BAST tests pass, all 244 passes module tests pass

### January 16, 2026 (Earlier)

**Focus**: Debugging "Invalid string table index" deserialization errors

**Completed**:
- Created BASTIncrementalTest with 37 test cases building from simple to complex structures
- Identified root cause: `Alternation` types caused byte stream misalignment
- Fixed by adding `readTypeExpressionContents()` helper method to BASTReader
- Updated `TYPE_ALTERNATION` case to use new helper instead of `readContentsDeferred()`

**Root Cause Analysis**:
- Writer serializes `AliasedTypeExpression` items using `TYPE_REF` tags
- Reader's `readContentsDeferred()` called `readNode()`
- `readNode()` only handles NODE_* tags, not TYPE_* tags
- This caused byte misinterpretation leading to invalid string table indices

---

## Git Information

**Branch**: `development`
**Main branch**: `main`
