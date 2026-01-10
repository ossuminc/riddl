# RIDDL Project Guide for Claude Code

This file provides specific guidance for working with the RIDDL project. For general ossuminc organization patterns, see `../CLAUDE.md` (parent directory).

## Project Overview

RIDDL (Reactive Interface to Domain Definition Language) is a specification language for designing distributed, reactive, cloud-native systems using DDD principles. It's a **monorepo** containing multiple cross-platform Scala modules.

## Critical Build Information

### Scala Version & Syntax
- **Scala 3.7.4** (not Scala 2!)
- **ALWAYS use Scala 3 syntax**:
  - `while i < end do ... end while` (NOT `while (i < end) { ... }`)
  - No `null` checks - use `Option(x)` instead
  - New control flow syntax with `do`/`then`/`end`

### sbt-ossuminc Plugin

**Current version: 1.0.0** (updated Jan 2026)

#### API Changes from 0.x to 1.0.0:
- `With.Javascript(...)` → `With.ScalaJS(...)`  or `With.scalajs` (lowercase for default)
- `With.Native()` → `With.Native(...)` (now requires parameter list, not just `()`)
- `With.BuildInfo.withKeys(...)` → `With.BuildInfo.withKeys(...)(project)` (curried function)

#### Common Configurations:
```scala
// Scala.js configuration
.jsConfigure(With.ScalaJS(
  header = "RIDDL: module-name",
  hasMain = false,
  forProd = true,
  withCommonJSModule = true
))

// Scala Native configuration
.nativeConfigure(With.Native(
  mode = "fast",              // "debug", "fast", "full", "size", "release"
  buildTarget = "static",     // or "application"
  gc = "none",
  lto = "none"
))

// BuildInfo with custom keys
.jvmConfigure(With.BuildInfo.withKeys(
  "key1" -> value1,
  "key2" -> value2
))
```

## Module Structure & Dependencies

### Dependency Pipeline
```
utils → language → bast → passes → diagrams → commands → riddlc
                     ↓
                  testkit
```

### New Module: bast/
**Purpose**: Binary AST (BAST) serialization for fast module imports

- **Location**: `bast/` (top-level directory)
- **Dependencies**: `language`, `passes`
- **Cross-platform**: JVM, JS, Native
- **Status**: In development (as of Jan 2026)

**Key files**:
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/package.scala` - Constants and node type tags
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/BinaryFormat.scala` - Format specification
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/VarIntCodec.scala` - LEB128 varint encoding
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/ByteBufferWriter.scala` - Binary writer
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/ByteBufferReader.scala` - Binary reader

## NPM Packaging (JavaScript/TypeScript API)

### RiddlAPI Facade
The `riddlLib` module exports a TypeScript-friendly API via `RiddlAPI` object.

**Key features**:
- All method names preserved (not minified) via `@JSExport`
- JavaScript-friendly return types: `{ succeeded: boolean, value?: object, errors?: Array<object> }`
- All Scala types converted to plain JS:
  - `List` → `Array`
  - Case classes → Plain objects
  - `Either` → `{ succeeded, value, errors }`

**Building npm packages**:
```bash
./scripts/pack-npm-modules.sh riddlLib
# Creates: npm-packages/ossuminc-riddl-lib-<version>.tgz
```

**Documentation**:
- `NPM_PACKAGING.md` - npm build and installation guide
- `TYPESCRIPT_API.md` - Complete TypeScript API reference

**Current version**: 1.0.1-11-47d36023 (as of Jan 2026)

## Import vs Include

**CRITICAL DISTINCTION**:

### Include (Context-Aware)
- Can appear anywhere in hierarchy
- Parser rules determined by enclosing container
- `include "entities.riddl"` in a Context → must contain Context-valid content
- **Already implemented**

### Import (Top-Level, Namespaced)
- **Only at top of file**, before any definitions
- Brings in arbitrary Nebula content from BAST files
- Uses **namespaced** approach (Option C from design):
  ```riddl
  import "utils.bast" as utils
  import "types.bast" as common

  domain MyApp is {
    type UserId is utils.UUID
  }
  ```
- **Status**: Stub exists at `language/shared/src/main/scala/com/ossuminc/riddl/language/parsing/ParsingContext.scala:81-89`
- **Test file**: `language/input/import/import.riddl`
- **TODO**: Issue #72

## AST Architecture Details

### Contents[CV] - Opaque Type
- Wraps `ArrayBuffer[CV]` for efficient modification
- **Extension methods**: `.toSeq`, `.isEmpty`, `.nonEmpty`
- **Do NOT use**: `.toList`, `.iterator` directly (not available)
- Pattern: `contents.toSeq.map { ... }.toJSArray` for JS conversion

### Token Representation
- Scala 3 **enum**, not case classes
- Get type name: `token.getClass.getSimpleName.replace("$", "")`
- Extract text: `token.loc.source.data.substring(token.loc.offset, token.loc.endOffset)`

### Location (At)
- Fields: `line`, `col`, `offset`, `endOffset`, `source`
- Always 1-based (not 0-based)
- Delta encoding for BAST: compress by storing differences

## Pass Framework

### Writing a Pass
Prefer `HierarchyPass` for maintaining parent context:

```scala
class MyPass extends HierarchyPass {
  override def process(value: RiddlValue, parents: ParentStack): Unit = {
    value match {
      case d: Domain => processDomain(d, parents)
      case c: Context => processContext(c, parents)
      // ... pattern match all node types
    }
  }

  override def result: MyPassOutput = MyPassOutput(...)
}
```

**BAST Writer Pattern**:
- BASTWriter will be a Pass subclass
- Sacrifice write speed for read speed
- Use `ByteBufferWriter` for output
- String interning for deduplication

## GitHub Workflows

**Updated**: Jan 2026 for improved reliability and performance

### scala.yml
- Triggers: `main`, `development` branches
- **Parallelized**: JVM/Native/JS builds using matrix strategy
- Timeout: 60 minutes
- Dependency scanning with SARIF upload

### coverage.yml
- Auto-triggers on PRs and pushes (not manual-only)
- Timeout: 45 minutes
- Fixed artifact paths (was broken in earlier versions)

### hugo.yml
- Triggers only on Hugo/doc changes (NOT all .scala files)
- ScalaDoc caching for faster builds
- Timeouts: 30min build, 10min deploy

**All workflows use JDK 25** (standardized)

## Testing Patterns

### Compilation After Every Change
When implementing new code:
1. Write the code
2. **ALWAYS** run `sbt "project <module>" compile`
3. Fix Scala 3 syntax errors immediately
4. Then proceed to next step

### Test Files Location
- Input test files: `language/input/<category>/<file>.riddl`
- Examples: `language/input/import/import.riddl`

## Common Errors & Solutions

### Error: "This construct is not allowed under -new-syntax"
**Cause**: Using Scala 2 syntax
**Fix**: Use Scala 3 syntax with `do`/`end`

### Error: "value kind is not a member of Token"
**Cause**: Token is an enum
**Fix**: Use `token.getClass.getSimpleName`

### Error: "value toList is not a member of Contents"
**Cause**: Contents is opaque type with limited extensions
**Fix**: Use `.toSeq` extension method

### Error: "value Javascript is not a member of With"
**Cause**: sbt-ossuminc 1.0.0 API change
**Fix**: Use `With.ScalaJS` instead

## File Organization

### Creating New Modules
1. Create directory: `<moduleName>/shared/src/{main,test}/scala/...`
2. Add to `build.sbt` using `CrossModule`
3. Add variants to root aggregation
4. Dependencies go in both directions of `cpDep()`

### Cross-Platform Considerations
- **Shared code**: `<module>/shared/src/`
- **Platform-specific**: `<module>/{jvm,js,native}/src/`
- **Avoid** platform-specific APIs in shared code
- Use `PlatformContext` for platform abstraction

## Git Workflow

### Version Management
- **sbt-dynver** generates versions from git tags
- Format: `MAJOR.MINOR.PATCH-commits-hash-YYYYMMDD-HHMM`
- Clean tag: `git tag -a v1.0.0 -m "Release 1.0.0"`

### Commit Message Format
```
Short description (imperative mood)

Detailed explanation of what changed and why.
Focus on "why" rather than "what".

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

### Branch Strategy
- **main**: Production releases
- **development**: Active development (current work)
- Always push to `development` unless releasing

## Documentation

### Key Documentation Files
- `README.md` - Project overview
- `NPM_PACKAGING.md` - npm build guide
- `TYPESCRIPT_API.md` - TypeScript API reference
- `.github/workflows/*.yml` - CI/CD configuration
- `CLAUDE.md` (this file) - AI assistant guidance

### Updating Documentation
- Keep npm packaging docs in sync with actual build
- Update version numbers when releasing
- Add examples for new features
- Link related documents

## Quick Reference Commands

```bash
# Compile specific module
sbt "project bast" compile

# Run tests for module
sbt "project language" test

# Build npm package
./scripts/pack-npm-modules.sh riddlLib

# Format code
sbt scalafmt

# Check all platforms compile
sbt cJVM cJS cNative

# Run all tests
sbt tJVM tJS tNative

# Package riddlc executable
sbt riddlc/stage
# Result: riddlc/jvm/target/universal/stage/bin/riddlc
```

## Current Work (As of Jan 2026)

### In Progress: BAST (Binary AST) Implementation
**Goal**: Enable fast `import "module.bast" as namespace` functionality

#### Design Overview

**Format Strategy**: Custom hybrid format inspired by FlatBuffers but optimized for RIDDL
- **Memory-mappable layout** with offset-based references (32-bit offsets)
- **String interning** - Deduplicate common strings (keywords, type names)
- **Variable-length integers** (LEB128/varint) for counts/offsets
- **Delta-encoded locations** - Store differences from previous location
- **Lazy deserialization** - Parse nodes on access (not full tree upfront)
- **Versioned schema** - Header includes version for format evolution

**Performance Goals**:
- Sacrifice write speed for read speed (10-50x faster loading vs parsing)
- Compact size with compression
- Cross-platform compatible (JVM, JS, Native)

**File Structure**:
```
┌─────────────────────────────────────┐
│ Header (32 bytes)                   │
│  - Magic: "BAST" (4 bytes)          │
│  - Version: u16 major + u16 minor   │
│  - Flags: u16                       │
│  - String Table Offset: u32         │
│  - Root Offset: u32                 │
│  - File Size: u32                   │
│  - Checksum: u32                    │
│  - Reserved: (8 bytes)              │
├─────────────────────────────────────┤
│ String Interning Table              │
│  - Count: varint                    │
│  - [Length: varint, UTF-8 bytes]... │
├─────────────────────────────────────┤
│ Nebula Root Node                    │
│  - Node Type: u8 (tag)              │
│  - Location: delta-compressed       │
│  - Contents: [Node...]              │
└─────────────────────────────────────┘
```

#### Implementation Checklist

**Phase 1: Infrastructure (COMPLETED)** ✅
- [x] Create `bast/` module structure with JVM/JS/Native variants
- [x] Add to `build.sbt` with dependencies on `language` and `passes`
- [x] Define BAST format specification (`BinaryFormat.scala`)
- [x] Define node type tags (1-255) (`package.scala`)
- [x] Implement VarInt codec with LEB128 encoding (`VarIntCodec.scala`)
- [x] Implement ByteBufferWriter (`ByteBufferWriter.scala`)
- [x] Implement ByteBufferReader (`ByteBufferReader.scala`)
- [x] Update to sbt-ossuminc 1.0.0

**Phase 2: Core Serialization (NEXT - IN PROGRESS)** 🚧
- [ ] Implement StringTable class for string interning
  - Pre-populate with RIDDL keywords and predefined types
  - Build during serialization, reference by varint index
- [ ] Implement BASTWriter as Pass subclass (extends `HierarchyPass`)
  - Use ByteBufferWriter for output
  - Pattern match all AST node types
  - Write node type tags + serialized data
  - Track offsets for backpatching
- [ ] Write node serializers for:
  - [ ] Core: Nebula, Domain, Context, Entity, Type, Function
  - [ ] Streaming: Processor, Projector, Repository, Adaptor, Streamlet
  - [ ] Epics: Epic, Saga, Story, UseCase
  - [ ] Types: Aggregation, Enumeration, Alternation, Mapping
  - [ ] Common: Identifier, PathIdentifier, Location (delta-encoded)
  - [ ] Metadata: Descriptions, Comments, Options
- [ ] Implement header writing with checksum
- [ ] Write unit tests for each node type

**Phase 3: Deserialization (PENDING)** ⏳
- [ ] Implement BASTReader class
- [ ] Header parsing and version validation
- [ ] String table loading
- [ ] Node deserializers (mirror of Phase 2)
- [ ] Reconstruct AST relationships (parent-child)
- [ ] Error handling for corrupted files
- [ ] Round-trip tests (write → read → compare)

**Phase 4: Import Integration (PENDING)** ⏳
- [ ] Update import syntax parser in `CommonParser.scala`
  - Parse: `import "file.bast" as namespace`
  - Validate: Must be top-level, before any definitions
- [ ] Implement `doImport()` in `ParsingContext.scala` (currently stub)
  - Load BAST file using BASTReader
  - Get Nebula from BAST
  - Return Nebula wrapped with namespace info
- [ ] Implement namespace resolution in path resolution
  - Extend `PathIdentifier` to support namespace prefix
  - Update `ReferenceMap` to resolve namespaced references
  - Example: `utils.UUID` resolves to imported `utils` namespace
- [ ] Add `.bast` file detection to `TopLevelParser`
- [ ] Implement BAST cache invalidation (check mtime)

**Phase 5: CLI & Testing (PENDING)** ⏳
- [ ] Add `riddlc bast-gen` command to generate BAST from RIDDL
- [ ] Add command-line flags:
  - `--use-bast-cache` - Auto-generate/use BAST files
  - `--bast-dir <path>` - BAST cache directory
- [ ] Comprehensive test suite:
  - Small Nebula (single type)
  - Medium Nebula (domain with contexts)
  - Large Nebula (full real-world example)
  - Edge cases (empty, nested, references)
- [ ] Performance benchmarks (parse RIDDL vs load BAST)
- [ ] Cross-platform tests (JVM, JS, Native)

**Phase 6: Documentation (PENDING)** ⏳
- [ ] Write BAST format specification document
- [ ] Document serialization/deserialization API
- [ ] Add ScalaDoc to all public APIs
- [ ] Create usage examples
- [ ] Update main RIDDL documentation

#### Design Decisions Made

1. **Namespaced imports (Option C)** - Chosen over flat or targeted imports
   - Syntax: `import "utils.bast" as utils`
   - No namespace collisions
   - Clear provenance
   - Qualified references: `utils.DomainName.TypeName`

2. **Top-level only** - Imports must occur before any definitions
   - Not inside domains (unlike the stub implementation)
   - Simpler, cleaner semantics
   - Easier to implement and reason about

3. **Writer as Pass** - BASTWriter extends `HierarchyPass`
   - Idiomatic RIDDL architecture
   - Automatic traversal infrastructure
   - Minimal performance overhead

4. **No targeted imports** - Keep it simple for MVP
   - No `import "x.bast" into domain Y` syntax
   - Can add later if needed

#### Files Created

**Completed**:
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/package.scala` - Constants, node tags, flags
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/BinaryFormat.scala` - Format spec, Header
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/VarIntCodec.scala` - LEB128 encoding/decoding
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/ByteBufferWriter.scala` - Binary writer
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/ByteBufferReader.scala` - Binary reader

**Next to create**:
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/StringTable.scala`
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/BASTWriter.scala`
- `bast/shared/src/main/scala/com/ossuminc/riddl/bast/BASTReader.scala`
- `bast/shared/src/test/scala/com/ossuminc/riddl/bast/VarIntCodecTest.scala`

#### Key Implementation Notes

1. **String Table Strategy**:
   - Pre-populate with RIDDL keywords (`domain`, `context`, `type`, etc.)
   - Pre-populate with predefined types (`String`, `Number`, `Boolean`, etc.)
   - Add new strings during serialization
   - Reference by varint index (typically 1-2 bytes)

2. **Location Compression**:
   - Delta-encode: store differences from previous location
   - Run-length encode: many nodes have same source file
   - Saves ~70% space on location data

3. **Node Type Tags**:
   - Single byte (0-255) covers all AST node types
   - Allows bit-packing for common flags
   - Much smaller than class names

4. **Error Handling**:
   - Version checking in header (major version changes = breaking)
   - Checksum validation on load
   - Graceful degradation for unknown node types

#### Related Issues & TODOs

- **Issue #72**: Implement import functionality (currently stub)
- **ParsingContext.scala:81-89**: `doImport()` stub with TODO comment
- **Test file**: `language/input/import/import.riddl` - Syntax exists but returns "NotImplemented"

## Git Workflow & Commit Discipline

### CRITICAL: Selective Committing

**Problem**: This repository often has multiple work streams happening concurrently (BAST development, API fixes, documentation, etc.). Files from different work streams may be staged simultaneously.

**Rule**: ONLY commit files related to the specific task you're working on.

**How to commit selectively**:

```bash
# Check what's staged
git status

# Unstage files not related to current task
git restore --staged path/to/file1 path/to/file2

# Stage only what you need
git add path/to/related/file

# Commit
git commit -m "Focused commit message"
```

### Amending Commits

If you accidentally commit too many files:

```bash
# Undo the commit but keep changes staged
git reset --soft HEAD~1

# Unstage the unrelated files
git restore --staged unrelated/files

# Recommit with only the relevant files
git commit -m "Corrected commit message"

# Re-stage the other work for future commits
git add unrelated/files
```

### Common Files to Check

Files that often appear staged but may not be related to your work:
- `CLAUDE.md` - Documentation updates
- `build.sbt.bak` - Backup files (should be in .gitignore)
- `bast/` - BAST development work
- `examples/` - Example code
- `language/.../Keywords.scala` - Keyword updates
- `passes/.../ValidationPass.scala` - Validation rule updates

### .gitignore Best Practices

Always add backup files and temporary files to `.gitignore`:
- `*.bak` - Backup files
- `*.tmp` - Temporary files
- `target/` - Build artifacts (already present)
- `.metals/` - Metals IDE files (already present)

## RiddlAPI Common Patterns

### Origin Parameter Pattern

**CRITICAL**: All RiddlAPI methods that accept an `origin` parameter expect a `URL` object, not a `String`.

**Problem**: Passing a String directly results in `URL.empty` being used, causing messages to show "empty" instead of the actual filename.

**Solution**: Use the `originToURL()` helper (as of Jan 2026):

```scala
private def originToURL(origin: String): URL = {
  if origin.startsWith("/") then
    // Full file path - use fromFullPath
    URL.fromFullPath(origin)
  else
    // Simple identifier or relative path - create URL with path component
    URL(URL.fileScheme, "", "", origin)
  end if
}
```

### Scala 3 Lambda Syntax

**Wrong** (Scala 2 style):
```scala
lines.foreach(pc.log.info)  // Error: type mismatch
```

**Correct** (Scala 3):
```scala
lines.foreach(line => pc.log.info(line))
```

**Reason**: Scala 3 doesn't automatically convert by-name parameters (`=> String`) to function parameters (`String => Unit`).

### Shared Utilities Pattern

When code needs to be shared between JVM (riddlc commands) and JS (RiddlAPI), put it in `utils/shared/`:

**Example**: `InfoFormatter` is used by both:
- `commands/InfoCommand.scala` (JVM)
- `riddlLib/RiddlAPI.scala` (JS via `@JSExport`)

```scala
// utils/shared/src/main/scala/com/ossuminc/riddl/utils/InfoFormatter.scala
object InfoFormatter {
  def formatInfo: String = {
    // Build info formatting logic
  }
}
```

## Notes for Future Sessions

1. **Always check sbt-ossuminc version** - API may have changed
2. **BAST format is versioned** - Header includes major/minor version
3. **Import stub exists but not implemented** - See ParsingContext.scala:81-89, TODO issue #72
4. **npm packages are versioned by git** - Rebuild after commits to get new version
5. **Workflow improvements made** - Check .github/workflows/ for latest patterns
6. **ONLY commit files related to your current task** - Multiple work streams may be active
7. **Check git status carefully before committing** - Unstage unrelated files
8. **RiddlAPI origin parameters must be URLs** - Use `originToURL()` helper for String origins
9. **Scala 3 lambda syntax required** - `foreach(line => func(line))` not `foreach(func)`
10. **Share code via utils/shared/** - For code used by both JVM and JS variants
