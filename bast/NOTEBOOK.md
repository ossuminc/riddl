# Engineering Notebook: BAST Module

## Current Status

**BAST (Binary AST) serialization is fully integrated into the language and passes modules.**

As of Jan 15, 2026, the standalone `bast/` module has been **removed from build.sbt** and the code has been reorganized:
- **Utility code** → `language/shared/src/main/scala/com/ossuminc/riddl/language/bast/`
- **BASTWriterPass** → `passes/shared/src/main/scala/com/ossuminc/riddl/passes/`

This enables BAST to work like Python's `.pyc` files - automatic loading from cache when available.

## Work Completed (Recent)

- [x] Phase 1: Infrastructure - Module structure, format spec, varint codec, byte buffer reader/writer
- [x] Phase 2: Core Serialization - BASTWriter pass, all node types, string interning
- [x] Phase 3: Deserialization - BASTReader, round-trip verification, performance tests
- [x] Phase 4: Import Integration - cf55b169 (Jan 15, 2026)
  - [x] Simplified syntax: `import "file.bast"` (removed `as namespace` clause)
  - [x] Support imports at root level AND inside domains
  - [x] BASTLoader utility to load and populate contents
  - [x] Path resolution verified: `ImportedDomain.SomeType` resolves correctly
- [x] Phase 5: Module Reorganization & Auto-Generation (Jan 15, 2026)
  - [x] Removed standalone `bast/` module from build.sbt
  - [x] Split BASTWriter: utility class in `language/bast/`, Pass wrapper in `passes/`
  - [x] Added `BASTUtils.scala` with `checkForBastFile()`, `loadBAST()`, `tryLoadBastOrParseRiddl()`
  - [x] Integrated automatic BAST loading into `TopLevelParser.parseURL()` and `parseInput()`
  - [x] Added `--auto-generate-bast` / `-B` CLI option to riddlc
  - [x] Added `auto-generate-bast` config file option
  - [x] Implemented auto-generation in `Riddl.parse()` when option enabled

## Completed

**Phase 5: CLI & Auto-Generation**
- [x] Add `riddlc bast-gen` command - 77ba7544
- [x] Add `--auto-generate-bast` flag (auto-generate BAST during parsing)
- [x] Automatic BAST loading when .bast file exists and is newer than .riddl
- [ ] Performance benchmarks (parse RIDDL vs load BAST)
- [ ] Cross-platform tests (JS, Native)

## Design Decisions Log

| Decision | Rationale | Alternatives Considered | Date |
|----------|-----------|------------------------|------|
| No namespace syntax | RIDDL uses nested domains for namespacing; separate namespace concept unnecessary | `import "x.bast" as ns` with namespace qualifier | 2026-01-15 |
| Imports at root + domain only | Simplest useful locations; other containers don't make semantic sense | Root only, All containers | 2026-01-15 |
| BASTImport as Container | Resolution pass naturally traverses Container.contents | Special handling in ResolutionPass | 2026-01-15 |
| Custom binary format | Memory-mappable, optimized for RIDDL; ~10x faster than parsing | FlatBuffers, Protobuf, JSON | 2026-01-10 |
| String interning | Deduplicates common strings (keywords, types); significant size reduction | No interning, external dictionary | 2026-01-10 |
| Delta-encoded locations | ~70% space savings on location data | Full coordinates per node | 2026-01-10 |

## Next Steps

1. ~~Add `riddlc bast-gen` command to generate BAST from RIDDL files~~ DONE
2. Add `--use-bast-cache` flag for automatic BAST generation during parsing
3. Implement duplicate definition error detection
4. Performance benchmarking (parse RIDDL vs load BAST)
5. Cross-platform testing (JS, Native variants)

## Open Questions

- ~~Should BAST files be auto-generated as a build cache, or explicitly created by users?~~ **RESOLVED**: Both options available - explicit via `bast-gen` command, automatic via `--auto-generate-bast` flag
- How should BAST versioning handle breaking format changes?

## Test Status

| Test Suite | Tests | Status |
|------------|-------|--------|
| BASTWriterSpec | 7 | All passing |
| BASTRoundTripTest | 3 | All passing |
| BASTPerformanceTest | 4 | All passing |
| BASTLoaderTest | 5 | All passing |
| **Total** | **19** | **All passing** |

## Key Code Locations

**Language Module** (`language/shared/src/main/scala/com/ossuminc/riddl/language/bast/`):
- `package.scala` - Constants, node type tags (1-255)
- `BinaryFormat.scala` - Header spec, file structure
- `BASTWriter.scala` - Serialization utility class (non-Pass)
- `BASTReader.scala` - Deserialization
- `BASTLoader.scala` - Import loading utility
- `BASTUtils.scala` - File checking, BAST loading helpers

**Passes Module** (`passes/shared/src/main/scala/com/ossuminc/riddl/passes/`):
- `BASTWriterPass.scala` - Pass wrapper using AST traversal framework

**Commands Module** (`commands/jvm/src/main/scala/com/ossuminc/riddl/commands/`):
- `BastGenCommand.scala` - `riddlc bast-gen` command

**Tests** (`passes/jvm/src/test/scala/com/ossuminc/riddl/passes/`):
- `BASTLoaderTest.scala` - Import integration tests
- `BASTRoundTripTest.scala` - Serialization round-trip tests

## Git Information

**Branch**: `development`

**Recent Commits**:
```
cf55b169 Add test confirming path resolution works with BAST imports
1912674a Support BAST imports inside domains
af7e89ae Simplify BAST import syntax: remove "as namespace" clause
cba2bf4c Add BASTLoader tests for import functionality
93f3dc01 Add BASTLoader utility for loading BAST imports
```
