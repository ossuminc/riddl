# Engineering Notebook: BAST Module

## Current Status

**BAST (Binary AST) serialization and import integration is complete through Phase 4.**

The `bast/` module provides efficient binary serialization/deserialization of RIDDL AST nodes, enabling fast `import "module.bast"` functionality. All 19 tests pass.

## Work Completed (Recent)

- [x] Phase 1: Infrastructure - Module structure, format spec, varint codec, byte buffer reader/writer
- [x] Phase 2: Core Serialization - BASTWriter pass, all node types, string interning
- [x] Phase 3: Deserialization - BASTReader, round-trip verification, performance tests
- [x] Phase 4: Import Integration - cf55b169 (Jan 15, 2026)
  - [x] Simplified syntax: `import "file.bast"` (removed `as namespace` clause)
  - [x] Support imports at root level AND inside domains
  - [x] BASTLoader utility to load and populate contents
  - [x] Path resolution verified: `ImportedDomain.SomeType` resolves correctly

## In Progress

**Phase 5: CLI & Testing**
- [x] Add `riddlc bast-gen` command - 77ba7544
- [ ] Add `--use-bast-cache` flag (auto-generate BAST during parsing)
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

- Should BAST files be auto-generated as a build cache, or explicitly created by users?
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

- `package.scala` - Constants, node type tags (1-255)
- `BinaryFormat.scala` - Header spec, file structure
- `BASTWriter.scala` - Serialization pass
- `BASTReader.scala` - Deserialization
- `BASTLoader.scala` - Import loading utility
- `BASTLoaderTest.scala` - Import integration tests

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
