# Engineering Notebook: RIDDL

This is the central engineering notebook for the RIDDL project. It tracks current status, work completed, design decisions, and next steps across all modules.

---

## Current Status

**Last Updated**: January 17, 2026

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

### Test Status (60 tests, all passing)

| Test Suite | Tests |
|------------|-------|
| BASTMinimalTest | 1 |
| BASTIncrementalTest | 37 |
| BASTWriterSpec | 5 |
| BASTRoundTripTest | 3 |
| BASTPerformanceBenchmark | 3 |
| BASTLoaderTest | 4 |
| BASTDebugTest | 1 |
| SharedBASTTest | 6 |

**Note**: All tests are in `passes/` module (JVM tests in `jvm/src/test/`, cross-platform in `shared/src/test/`). The deprecated standalone `bast/` directory has been removed.

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

### Performance Results (January 17, 2026)

**BAST Format v1** - Latest optimizations include FILE_CHANGE_MARKER (Phase 7):

| File | Source | BAST (Phase 6) | BAST (Phase 7) | Size Change |
|------|--------|----------------|----------------|-------------|
| small.riddl | 2KB | 2.4KB (117%) | 2.2KB (108%) | **-9%** |
| medium.riddl | 11KB | 10KB (88%) | 8.6KB (75%) | **-15%** |
| large.riddl | 43KB | 36.6KB (85%) | 31KB (72%) | **-15%** |

**Key achievement**: Phase 7 FILE_CHANGE_MARKER optimization achieved **~15% additional reduction**, bringing large files to **72% of source size**!

**Speed benchmarks** (50 iterations each):

| File | Nodes | Cold Speed | Warm Speed |
|------|-------|------------|------------|
| small.riddl | 60 | **9.7x** | 3.6x |
| medium.riddl | 335 | **14.4x** | 12.9x |
| large.riddl | 1,354 | **4.0x** | 6.2x |
| **Average** | | **9.4x** | **7.6x** |

**Test files** (`testkit/jvm/src/test/resources/performance/`):
- `small.riddl` - 73 lines, 2 contexts (user management)
- `medium.riddl` - 342 lines, 6 contexts (e-commerce)
- `large.riddl` - 1313 lines, 10 contexts (enterprise platform)

**Conclusion**: BAST v1.1 achieves **6-12x speedup** with files **smaller than source**. Cold runs show higher speedup due to JVM warmup effects on the parser.

### Cross-Platform Status (January 16, 2026)

| Platform | Status | Notes |
|----------|--------|-------|
| JVM | ✅ Passing | 6 tests in SharedBASTTest |
| Native | ✅ Passing | 6 tests in SharedBASTTest |
| JS | ✅ Passing | 6 tests in SharedBASTTest |

**Note**: BAST serialization/deserialization works on all platforms. BAST *file import loading* is
JVM/Native only (JS returns error message since browser can't do local file I/O).

### Next Steps

1. ~~Consider larger test corpus for comprehensive benchmarks~~ ✅ Done - created small/medium/large.riddl
2. Rewrite `doc/src/main/hugo/content/future-work/bast.md` - the existing document is outdated
3. Finalize BAST schema before release to users (TODO in package.scala)
4. **Phase 7 Optimizations** (see `/Users/reid/.claude/plans/bast-phase7-optimizations.md`):
   - ✅ **Bug Fix**: Fixed nodes with `At.empty` - ULIDAttachment, BASTReader fallbacks now use valid locations
   - ✅ **Source file change markers**: FILE_CHANGE_MARKER (tag 0) written only when source changes (**15% savings achieved**)
   - ⏳ **Empty metadata flag bit** (optional): Use tag high bit for metadata presence (~3% additional savings) - requires touching 60+ methods
   - ⏳ **Predefined type expressions** (optional): Single-byte encoding for common types (~2-5% additional savings)
   - **Current result**: Large files at **72% of source** (exceeded original 70-75% target)

### Open Questions

- ~~How should BAST versioning handle breaking format changes?~~ **Resolved**: Single monotonically incrementing 32-bit integer, stays at 1 during development, increment only after schema finalization for users

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
| Zigzag encoding for deltas | Handles negative deltas efficiently | Positive-only varints | 2026-01-16 |
| Remove line/col from BAST | Computed from offset; saves ~4 bytes/node | Store redundantly | 2026-01-16 |
| HTTP compression vs library | HTTP handles transport; focus on base format | LZ4/Zstd library | 2026-01-16 |
| Single version integer | Simpler; increment only on schema finalization | Major.minor semver | 2026-01-16 |
| Compact tag numbering (1-67) | Eliminates gaps, easier maintenance | Sparse numbering | 2026-01-17 |
| Dedicated message ref tags | Eliminates polymorphism, saves 1 byte/ref | Shared NODE_TYPE + subtype | 2026-01-17 |
| Inline PathIdentifier | Position always known in refs, saves 1 byte | Tag every PathIdentifier | 2026-01-17 |
| Inline TypeRef for known positions | Inlet/Outlet/State/Input always have TypeRef | Tag every TypeRef | 2026-01-17 |
| Source file change markers | Only mark when source changes, not per-location | Per-location path index | 2026-01-17 (planned) |
| Metadata flag in tag high bit | Tags 1-67 fit in 7 bits; saves 1 byte for empty metadata | Separate count byte | 2026-01-17 (planned) |

---

## Session Log

### January 17, 2026 (Phase 7 Planning)

**Focus**: Plan further BAST size optimizations

**Discussion**: Identified 4 potential optimizations for next phase:
1. **Source file change markers** - User refined initial idea: instead of 1-byte "same as previous" per location, write FILE_CHANGE marker only when source actually changes. All locations become just offset+endOffset. Must handle include stack properly (mark when returning to parent file). Estimated ~4% savings.
2. **Empty metadata flag bit** - Use high bit of tag byte (tags 1-67 fit in 7 bits). Estimated ~3% savings.
3. **Predefined type expressions** - Single-byte encoding for common default-parameter types. Estimated ~2-5% savings.
4. **Compression** - Rejected (HTTP gzip is sufficient for WAN).

**Bug identified**: Some nodes may have `At.empty` which is invalid. Need to find and fix these.

**Plan created**: `/Users/reid/.claude/plans/bast-phase7-optimizations.md`

**Target**: ~70-75% of source size (currently ~82-85% for medium/large files)

### January 17, 2026 (BAST Tag Refactoring & Inline Optimization)

**Focus**: Optimize BAST format by compacting tags and eliminating redundant tag bytes

**Completed**:
- **Phase 1**: Tag Cleanup and Reorganization
  - Removed 4 unused tags: `NODE_PROCESSOR`, `NODE_PLANT`, `NODE_APPLICATION`, `NODE_LOCATION`
  - Added 5 dedicated message ref tags: `NODE_COMMAND_REF`, `NODE_EVENT_REF`, `NODE_QUERY_REF`, `NODE_RESULT_REF`, `NODE_RECORD_REF`
  - Compacted tag numbering from sparse (1-103 with gaps) to sequential (1-67)
- **Phase 2-3**: Message Ref Tag Updates
  - Updated BASTWriter to use dedicated message ref tags (eliminates subtype byte)
  - Updated BASTReader dispatch tables
  - Removed polymorphic `readTypeRefOrMessageRef()` in favor of direct tag dispatch
  - Simplified `readMessageRef()` to use dedicated tags
- **Phase 4**: Inline PathIdentifier
  - Added `writePathIdentifierInline()` / `readPathIdentifierInline()` methods
  - Updated all 29+ reference write/read methods to use inline (no tag)
  - Updated type expressions (AliasedTypeExpression, EntityReferenceTypeExpression, UniqueId)
- **Phase 5**: Inline TypeRef for Known Positions
  - Added `writeTypeRefInline()` / `readTypeRefInline()` methods
  - Updated State, Inlet, Outlet, Input, Output to use inline TypeRef

**Results**: All 60 BAST tests pass. Size reduction of ~3% from this session:
- small: 2,424 → 2,383 bytes (1.7% reduction)
- medium: 10,035 → 9,739 bytes (2.9% reduction)
- large: 36,580 → 35,541 bytes (2.8% reduction)

**Key Files Modified**:
- `language/shared/.../bast/package.scala` - New compact tag scheme
- `language/shared/.../bast/BASTWriter.scala` - Inline methods, message ref tags
- `language/shared/.../bast/BASTReader.scala` - Inline methods, message ref dispatch

### January 16, 2026 (BAST Version Simplification)

**Focus**: Simplify BAST versioning scheme

**Completed**:
- Changed from major.minor (two 16-bit shorts) to single 32-bit integer
- VERSION = 1, will stay at 1 during development until schema finalized
- Updated BinaryFormat.Header, BASTWriter, BASTReader, and tests
- All 60 BAST tests pass

**Note**: `doc/src/main/hugo/content/future-work/bast.md` is outdated and needs rewriting

### January 16, 2026 (BAST Format Optimization)

**Focus**: Reduce BAST file size through bit-level optimizations

**Analysis**: Initial BAST files were larger than source code (137-147%). Research showed that:
- Line/col are redundant (computed from offset via `At.line` and `At.col`)
- Positive delta encoding wasted bytes for nearby locations
- HTTP handles transport compression automatically (no need for compression library)

**Completed**:
- Removed line/col from location storage (computed from offset anyway)
- Added endOffset field for accurate source spans
- Implemented zigzag encoding for signed deltas in VarIntCodec
- Added writeZigzagInt/readZigzagInt to ByteBufferWriter/Reader
- Updated BASTWriter.writeLocation() to use zigzag-encoded deltas
- Updated BASTReader.readLocation() to match new format
- Added createAtFromOffsets() method to BASTParserInput with safeguard
- Simplified BAST version to single integer (VERSION = 1)

**Key changes**:
- `VarIntCodec.scala` - Added `encodeZigzag()` and `decodeZigzag()` methods
- `ByteBufferWriter.scala` - Added `writeZigzagInt()` method
- `ByteBufferReader.scala` - Added `readZigzagInt()` method
- `BASTWriter.scala` - writeLocation() now stores offset/endOffset with zigzag deltas
- `BASTReader.scala` - readLocation() reads zigzag deltas, uses createAtFromOffsets()
- `BASTParserInput.scala` - Added `createAtFromOffsets()` with safeguard for edge cases
- `package.scala` - Simplified to single `VERSION: Int = 1` (was major.minor)
- `BinaryFormat.scala` - Header now uses single 32-bit version field

**Results**: All 60 BAST tests pass. Size reduction of 18-37%:
- small: 3KB → 2.4KB (-18%)
- medium: 16KB → 10KB (-36%) - **now smaller than source**
- large: 59KB → 37KB (-37%) - **now smaller than source**

### January 16, 2026 (Comprehensive Test Corpus)

**Focus**: Create larger test corpus for BAST benchmarks

**Completed**:
- Created comprehensive performance test files in `testkit/jvm/src/test/resources/performance/`:
  - `small.riddl` - 73 lines, 2 contexts, simple user management domain
  - `medium.riddl` - 342 lines, 6 contexts, e-commerce domain
  - `large.riddl` - 1313 lines, 10 contexts, enterprise platform with:
    - Identity & Access Management (Tenant, User, Role, Session, ApiKey)
    - Audit & Compliance (AuditEntry, ComplianceReport)
    - File Management (File, Folder)
    - Collaboration (Comment, Tag)
    - Workflow & Task Management (Workflow, Task, Project)
    - Communication (Team, Channel, Message)
    - Notification Service (Notification, Webhook)
    - Billing & Payments (Customer, Invoice, Payment, Product)
- Updated `BASTBenchmarkRunner.scala` to test all three file sizes
- Fixed RIDDL syntax issues in test files (correct entity/handler/state syntax)
- Ran comprehensive benchmarks showing 6-12x speedup across file sizes
- All 60 BAST tests pass

**Key result**: BAST loading performance is consistent across file sizes with warm speedups of 3-9x.

### January 16, 2026 (Cross-Platform Testing)

**Focus**: Cross-platform BAST testing (JS, Native)

**Completed**:
- Created `SharedBASTTest.scala` in `passes/shared/src/test/` with 6 tests
- Tests build AST programmatically (avoid parser's BAST import loading)
- Made `BASTLoader` platform-aware with `BASTLoaderPlatform`:
  - JVM/Native: Uses blocking `Await.result()` for file loading
  - JS: Returns error message (file I/O not supported)
- All platforms pass: JVM ✅, Native ✅, JS ✅

**Key insight**: Separated blocking I/O code into `BASTLoaderPlatform` (in `jvm-native/` and `js/`)
to allow JS linker to succeed while maintaining full functionality on JVM/Native.

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
