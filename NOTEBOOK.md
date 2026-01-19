# Engineering Notebook: RIDDL

This is the central engineering notebook for the RIDDL project. It tracks current status, work completed, design decisions, and next steps across all modules.

---

## Current Status

**Last Updated**: January 18, 2026

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

### Performance Results (January 18, 2026)

**BAST Format v1** - Latest optimizations include all Phase 7 features:

| File | Source | BAST (Phase 6) | BAST (Phase 7a) | BAST (Phase 7b) |
|------|--------|----------------|-----------------|-----------------|
| small.riddl | 2KB | 2.4KB (117%) | 2.2KB (108%) | 2.1KB (**104%**) |
| medium.riddl | 11KB | 10KB (88%) | 8.6KB (75%) | 8.1KB (**71%**) |
| large.riddl | 43KB | 36.6KB (85%) | 31KB (72%) | 29KB (**67.5%**) |

**Phase 7 Optimizations Applied:**
- **Phase 7a**: FILE_CHANGE_MARKER (tag 0) - wrote source path only when it changes (~15% savings)
- **Phase 7b**: Metadata flag + predefined types (Jan 18) - additional ~5% savings:
  - Used high bit of tag byte for metadata presence (skips empty metadata writes)
  - Added 11 predefined type tags (TYPE_INTEGER, TYPE_STRING_DEFAULT, etc.)

**Key achievement**: Large files now at **67.5% of source size** (was 72% after 7a, 85% before)!

**Speed benchmarks** (50 iterations each):

| File | Nodes | Cold Speed | Warm Speed |
|------|-------|------------|------------|
| small.riddl | 60 | **7.8x** | 3.1x |
| medium.riddl | 331 | **10.3x** | 10.4x |
| large.riddl | 1,296 | **6.5x** | 9.0x |
| **Average** | | **8.2x** | **7.5x** |

**Test files** (`testkit/jvm/src/test/resources/performance/`):
- `small.riddl` - ~60 lines, 2 contexts (user management)
- `medium.riddl` - ~370 lines, 7 contexts (e-commerce)
- `large.riddl` - ~1450 lines, 10 contexts (enterprise platform)

**Conclusion**: BAST v1 achieves **6-10x speedup** with files **67-71% of source size** for non-trivial inputs. Small files have minimal overhead due to fixed header/string table costs.

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
2. Finalize BAST schema before release to users (TODO in package.scala)
3. **Phase 7 Optimizations** ✅ **ALL COMPLETE** (January 18, 2026):
   - ✅ **Bug Fix**: Fixed nodes with `At.empty` - ULIDAttachment, BASTReader fallbacks now use valid locations
   - ✅ **Source file change markers**: FILE_CHANGE_MARKER (tag 0) written only when source changes (**15% savings**)
   - ✅ **Empty metadata flag bit**: Use tag high bit (0x80) for metadata presence, fixed signed byte overflow bug (**~2% savings**)
   - ✅ **Predefined type expressions**: 11 new tags (TYPE_INTEGER, TYPE_STRING_DEFAULT, TYPE_UUID, etc.) (**~3% savings**)
   - **Final result**: Large files at **67.5% of source** (exceeded 70-75% target by significant margin!)

### Open Questions

- ~~How should BAST versioning handle breaking format changes?~~ **Resolved**: Single monotonically incrementing 32-bit integer, stays at 1 during development, increment only after schema finalization for users

### Design Decisions

**Compression: Will NOT be implemented**

The `Flags.COMPRESSED` flag in the header is reserved but will never be used. Rationale:
- BAST's primary use case is HTTP transport (web-based tools, APIs)
- HTTP already provides transparent gzip/brotli compression
- Adding compression at the BAST layer would be redundant
- Would add CPU overhead on both ends for no benefit
- Simpler format = easier debugging and cross-platform compatibility

**Incremental Updates: Future consideration**

Supporting partial BAST updates when source changes slightly could be valuable for:
- Large projects where only one file changes
- IDE integrations that need fast refresh
- CI/CD pipelines with incremental builds

This is **not currently planned** but noted as an area of potential future interest.

**Lazy Loading: Under evaluation**

See "Future Considerations: Lazy Loading" section below for analysis.

---

## Future Considerations: Lazy Loading

### What is Lazy Loading?

Instead of deserializing the entire BAST file into memory on load, lazy loading would:
1. Memory-map the BAST file (or keep bytes in memory)
2. Parse only the header and string table upfront
3. Deserialize individual nodes on-demand when accessed
4. Cache deserialized nodes for subsequent access

### Current Implementation

The current `BASTReader.read()` approach:
```
1. Read header (32 bytes)
2. Validate checksum
3. Load entire string table into memory
4. Recursively deserialize ALL nodes starting from root
5. Return complete Nebula AST in memory
```

### Potential Lazy Implementation

```
1. Read header (32 bytes)
2. Keep byte array reference (or mmap file)
3. Load string table (required for any node access)
4. Return LazyNebula proxy with root offset
5. On first access to contents: deserialize children
6. Cache deserialized nodes in WeakHashMap
```

### Benefits

| Benefit | Impact | Use Case |
|---------|--------|----------|
| Faster initial load | High | Large BAST files where only part is needed |
| Lower memory peak | Medium | Memory-constrained environments |
| Incremental parsing | Medium | IDE features that only need specific nodes |
| Partial file access | Low | Extracting single definition from large module |

### Drawbacks

| Drawback | Severity | Mitigation |
|----------|----------|------------|
| More complex code | High | Significant refactoring of reader |
| Random access overhead | Medium | Cache frequently accessed nodes |
| Memory mapping complexity | Medium | Platform-specific (JS has no mmap) |
| Debugging difficulty | Medium | Harder to trace deserialization issues |
| Thread safety concerns | Low | Need synchronization for cache |

### Performance Analysis

**Current approach** (eager loading):
- large.riddl (43KB source → 29KB BAST): ~0.78ms warm load
- All 1,296 nodes deserialized upfront
- Memory: ~full AST size in heap

**Lazy approach** (estimated):
- Initial load: ~0.1-0.2ms (header + string table only)
- Per-node access: ~0.001-0.01ms (amortized with caching)
- Full traversal: Similar to eager (~0.8-1.0ms with cache overhead)

### Recommendation

**Do not implement lazy loading at this time.**

Reasons:
1. Current load times are already excellent (sub-millisecond for typical files)
2. RIDDL files are typically small enough to fit entirely in memory
3. Most use cases need the full AST anyway (validation, transformation)
4. Implementation complexity is significant
5. Cross-platform concerns (JS cannot memory-map)

**When to reconsider:**
- If BAST files regularly exceed 10MB
- If use cases emerge that only need partial AST access
- If memory constraints become a real issue

---

## Future Considerations: Phase 8 Size Optimizations

Analysis performed January 18, 2026 on `large.riddl` (43KB source → 29KB BAST at 67.5%)

### Optimization 1: PathIdentifier Value Interning (Recommended)

**Current encoding** for PathIdentifier (e.g., `TenantId` or `Entity.StateRecord`):
```
Location (2-4 bytes) + Count (1 byte) + N × StringIndex (1-2 bytes each)
```

**Observation**: Identifiers repeat frequently in RIDDL models:
- `UserId`: 65 occurrences
- `TenantId`: 52 occurrences
- `String`: 181 occurrences
- Total: 2,069 identifier references, only 529 unique
- **Repetition rate: 74.4%**

**Proposed optimization**: Create a PathValueTable (similar to StringTable):
```
First occurrence: Location + PathValueIndex + [PathValue in table]
Subsequent:       Location + PathValueIndex
```

**Estimated savings**:
- Current path bytes: ~6,200 bytes (in large.riddl)
- With interning: ~4,700 bytes
- **Savings: ~1,500 bytes (5% of total BAST)**

**Implementation complexity**: Medium
- Add PathValueTable to BASTWriter/BASTReader
- Modify `writePathIdentifierInline()` to check table first
- First bit of index indicates: 0 = table lookup, 1 = inline path

### Optimization 2: Location Delta Run-Length Encoding (Potential)

**Observation**: Many consecutive definitions share the same source file and have sequential line numbers.

**Current**: Each location is delta-encoded from the previous.

**Potential enhancement**: Use run-length encoding for sequences of "same file, next line":
- Flag byte indicates: "increment line by 1, same file"
- Saves offset/endOffset bytes for simple sequential definitions

**Estimated savings**: 500-1,000 bytes (2-3%)
**Implementation complexity**: Medium-High

### Optimization 3: SagaStep Structure (Not Recommended)

**Observation**: SagaSteps always have exactly 2 Contents fields (doStatements, undoStatements).

**Current encoding**: Writes count for each Contents field.

**Potential**: Could eliminate counts since always 2 fields.

**Assessment**: **Not worth implementing**
- Typical RIDDL models have very few SagaSteps (0-5)
- Savings: ~5-10 bytes per SagaStep
- Total savings: <50 bytes in typical models
- Adds special-case complexity for minimal gain

### Optimization 4: Reference Kind Consolidation (Potential)

**Observation**: We have 20+ reference types (TypeRef, StateRef, EntityRef, etc.) each with their own tag.

**Current**: Each reference type has a dedicated NODE_* tag.

**Potential**: Could use a single REFERENCE tag + subtype byte for less common references, keeping dedicated tags only for the most frequent (TypeRef, FieldRef, StateRef).

**Assessment**: Marginal benefit, increases code complexity.

### Summary Recommendation

**Phase 8 should focus on PathIdentifier Value Interning**:
- Clearest win with ~5% additional size reduction
- Well-understood implementation pattern (mirrors StringTable)
- Benefits grow with model size and reference density

**Target after Phase 8**: Large files at ~63-64% of source size (from current 67.5%)

---

## Planned: AsciiDoc Generation Module

### Overview

Create a new pass or module that converts the RIDDL AST into AsciiDoc format, enabling generation of PDFs, static websites, and other standard documentation formats via Maven tooling.

### Motivation

The existing Hugo-based documentation generation (`HugoPass`) produces Markdown for Hugo static sites. While effective, this approach:
- Requires Hugo toolchain knowledge
- Limited to web output
- Custom theme dependencies

AsciiDoc with Maven provides:
- **Multiple output formats**: PDF, HTML, EPUB, DocBook, man pages
- **Industry-standard tooling**: Maven/Gradle integration, CI/CD friendly
- **Rich formatting**: Tables, admonitions, cross-references, includes
- **Professional PDFs**: Via Asciidoctor PDF with customizable themes
- **Single source**: One AsciiDoc source generates all formats

### Proposed Architecture

```
passes/
  └── AsciiDocPass.scala      # Main pass converting AST → AsciiDoc

asciidoc/                     # New module (or part of passes)
  ├── AsciiDocWriter.scala    # AsciiDoc syntax generation
  ├── AsciiDocTheme.scala     # Theming/styling configuration
  ├── MavenProjectGenerator.scala  # Generate pom.xml for builds
  └── templates/              # AsciiDoc templates per definition type
```

### Key Features

1. **AST → AsciiDoc Conversion**
   - Domain/Context/Entity documentation pages
   - Type definitions with cross-references
   - Handler/Saga/Workflow documentation
   - Auto-generated diagrams (PlantUML/Mermaid embedded)

2. **Maven Integration**
   - Generate `pom.xml` with Asciidoctor Maven Plugin
   - Configure PDF, HTML5, DocBook backends
   - Support for custom themes/stylesheets

3. **Output Formats** (via Maven build)
   - HTML5 static site
   - PDF documentation
   - EPUB for e-readers
   - DocBook XML for further processing

4. **Cross-Reference Support**
   - Inter-document links
   - Glossary generation
   - Index generation

### Implementation Phases

- [ ] **Phase 1**: Core AsciiDocWriter with basic AST node rendering
- [ ] **Phase 2**: AsciiDocPass extending HierarchyPass
- [ ] **Phase 3**: Maven project generation (pom.xml templates)
- [ ] **Phase 4**: Theme/styling system
- [ ] **Phase 5**: `riddlc asciidoc` command integration
- [ ] **Phase 6**: Documentation and examples

### Open Questions

- Should this replace or complement Hugo support?
- What level of diagram integration (PlantUML, Mermaid, Graphviz)?
- Support for custom AsciiDoc templates per organization?
- Integration with existing `diagrams` module?

### References

- [Asciidoctor](https://asciidoctor.org/)
- [Asciidoctor Maven Plugin](https://docs.asciidoctor.org/maven-tools/latest/)
- [Asciidoctor PDF](https://docs.asciidoctor.org/pdf-converter/latest/)
- Existing Hugo pass: `passes/jvm/src/main/scala/com/ossuminc/riddl/passes/hugo/`

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

## Known Parser Issues

### PseudoCodeBlock with ??? and Comments

**Status**: ✅ FIXED January 19, 2026

The `pseudoCodeBlock` parser now allows comments before and/or after `???`:
- `{ ??? }`
- `{ ??? // comment }`
- `{ // comment ??? }`
- `{ // c1 ??? // c2 }`

**Fix applied** in `StatementParser.scala`:
```scala
(open ~ comment.rep(0) ~ undefined(Seq.empty[Statements]) ~ comment.rep(0) ~ close).map {
  case (before, _, after) => before ++ after
}
```

---

## Session Log

### January 19, 2026 (CI Build Fixes Complete)

**Focus**: Complete CI build fixes from previous session

**Tasks Completed**:
1. ✅ **AdaptorWriterTest expected output update** - Updated byte positions and removed string literal from expected output
2. ✅ **Hugo CI environment fix** - Added `isHugoInstalled` check to skip Hugo binary execution when not available
3. ✅ **Parser fix for `{ ??? // comment }`** - Extended `pseudoCodeBlock` to allow comments before/after `???`

**Files Modified**:
- `commands/jvm/src/test/scala/.../AdaptorWriterTest.scala` - Updated expected positions
- `commands/jvm/src/test/scala/.../HugoPassTest.scala` - Added Hugo installation check
- `language/shared/src/main/scala/.../StatementParser.scala` - Extended pseudoCodeBlock grammar

**Test Results**: All 75 commands tests pass, all 280 language tests pass

---

### January 18, 2026 (CI Build Failures - Initial Investigation)

**Focus**: Investigate and fix CI build failures

**Context**: CI builds started failing after statement syntax changes in the parser. 5 tests were failing due to test input files using old or invalid statement syntax.

**Tests Failing**:
1. `RootOverviewDiagramTest` - context-relationships.riddl parse errors
2. `ContextMapDiagramTest` - same file
3. `ToDoPassListTest` - everything.riddl parse errors
4. `AdaptorWriterTest` - adaptors.riddl parse errors
5. `HugoPassTest` (example sources) - Hugo not installed in CI

**Root Causes Identified**:
1. On clauses missing `is` keyword (e.g., `on command X {` → `on command X is {`)
2. Statements missing type keywords (e.g., `tell X to Y` → `tell command X to entity Y`)
3. Bare string literals not valid in handler bodies (pseudo-code strings)
4. Hugo binary not available in CI (separate environmental issue)

**Completed**:
- [x] Fixed `commands/input/hugo/context-relationships.riddl` - added `is` keyword to on clauses, fixed statement syntax
- [x] Fixed `commands/input/everything.riddl` - added `is` keyword, fixed send/set statements
- [x] Fixed `commands/input/adaptors.riddl` - converted string literal to comment

**Discovered Parser Issue**: `{ ??? // comment }` is not allowed by grammar - documented for next session.

---

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

## Deferred Tasks

Tasks intentionally deferred to the end of the current work list:

1. Rewrite `doc/src/main/hugo/content/future-work/bast.md` - the existing BAST documentation is outdated and needs a complete rewrite reflecting the current implementation

---

## Git Information

**Branch**: `development`
**Main branch**: `main`
