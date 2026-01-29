# RIDDL Project Guide for Claude Code

This file provides specific guidance for working with the RIDDL project. For general ossuminc organization patterns, see `../CLAUDE.md` (parent directory).

## Documentation

**RIDDL documentation has moved to [ossum.tech/riddl](https://ossum.tech/riddl/)**

The Hugo-based documentation site at riddl.tech has been consolidated into the
ossum.tech MkDocs site. Key documentation:

- **Language Reference**: https://ossum.tech/riddl/references/language-reference/
- **EBNF Grammar**: https://ossum.tech/riddl/references/ebnf-grammar/
- **Tutorials**: https://ossum.tech/riddl/tutorials/
- **Tools (riddlc)**: https://ossum.tech/riddl/tools/riddlc/

The `doc/` directory in this repository contains legacy Hugo content that
redirects to ossum.tech. Do not add new documentation here.

## Project Overview

RIDDL (Reactive Interface to Domain Definition Language) is a specification language for designing distributed, reactive, cloud-native systems using DDD principles. It's a **monorepo** containing multiple cross-platform Scala modules.

## Critical Build Information

### Scala Version & Syntax
- **Scala 3.3.7 LTS** (not Scala 2!)
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
// Scala 3.3.7 LTS (recommended for projects)
.configure(With.scala3)  // Sets scalaVersion to 3.3.7 LTS

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
utils → language → passes → commands → riddlc
                     ↓
                  testkit
```

**Note**: The `diagrams` and `hugo` modules have been moved to the `riddl-gen` repository.

### BAST Module (Binary AST)
**Purpose**: Binary AST serialization for fast module imports

- **Location**: `language/shared/src/main/scala/com/ossuminc/riddl/language/bast/` (inside language module)
- **Package**: `com.ossuminc.riddl.language.bast`
- **Cross-platform**: JVM, JS, Native
- **Status**: Core functionality complete (as of Jan 2026)

**Note**: The legacy standalone `bast/` directory has been removed (Jan 16, 2026). All BAST code lives in the `language` module's `bast` package.

**Key files** (all in `language/shared/src/main/scala/com/ossuminc/riddl/language/bast/`):
- `package.scala` - Constants and node type tags (NODE_*, TYPE_*, STREAMLET_*, etc.)
- `BASTWriter.scala` - Serialization pass (extends HierarchyPass)
- `BASTReader.scala` - Deserialization
- `BASTLoader.scala` - Import loading utility
- `BASTUtils.scala` - Shared utilities
- `StringTable.scala` - String interning for compression

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
- `BASTWriterPass` (in passes module) extends `HierarchyPass`
- Uses `BASTWriter` utilities (in language module) for byte writing
- Sacrifice write speed for read speed
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

### CRITICAL: Scala Version Change Impact

When the Scala LTS version changes (either directly in build.sbt or when
sbt-ossuminc updates its default), the following files **MUST** be updated:

**GitHub Workflows** (`.github/workflows/`):
1. **scala.yml**:
   - `RIDDLC_PATH` env var: `riddlc/native/target/scala-X.Y.Z/riddlc`
   - Cache paths: `**/target/scala-X.Y.Z`
   - Native artifact path: `riddlc/native/target/scala-X.Y.Z/riddlc`
   - Native artifact path: `riddlLib/native/target/scala-X.Y.Z/libriddl-lib.a`
   - JS artifact path: `riddlLib/js/target/scala-X.Y.Z/riddl-lib-opt/main.js`

2. **coverage.yml**:
   - Coverage report paths: `**/target/scala-X.Y.Z/scoverage-report/`

**sbt-ossuminc Version Policy**:
- sbt-ossuminc always defaults to the latest Scala LTS version
- When sbt-ossuminc is updated, check if its default Scala version changed
- Current LTS: **3.3.7** (3.3.x series)
- Next LTS expected: **3.9.x** (Q2 2026)

**Quick Search to Find All References**:
```bash
grep -r "scala-3\." .github/workflows/
```

**Example Fix** (3.3.7 → 3.9.0):
```bash
# In each workflow file, replace all occurrences:
sed -i 's/scala-3.3.7/scala-3.9.0/g' .github/workflows/*.yml
```

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
- Clean tag: `git tag -a 1.0.0 -m "Release 1.0.0"` (no `v` prefix - it interferes with sbt-dynver)
- **Always run `sbt publishLocal` after tagging** to make the new version available locally

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
│  - Version: u32 (single integer)    │
│  - Flags: u16                       │
│  - String Table Offset: u32         │
│  - Root Offset: u32                 │
│  - File Size: u32                   │
│  - Checksum: u32                    │
│  - Reserved: (4 bytes)              │
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

**Phase 2: Core Serialization (COMPLETED)** ✅
- [x] Implement StringTable class for string interning
- [x] Implement BASTWriter as Pass subclass
- [x] Write node serializers for all AST node types
- [x] Implement header writing with checksum
- [x] Write unit tests for each node type (7 tests in BASTWriterSpec)

**Phase 3: Deserialization (COMPLETED)** ✅
- [x] Implement BASTReader class
- [x] Header parsing and version validation
- [x] String table loading
- [x] Node deserializers (mirror of Phase 2)
- [x] Reconstruct AST relationships (parent-child)
- [x] Error handling for corrupted files
- [x] Round-trip tests (3 tests in BASTRoundTripTest)
- [x] Performance benchmarks (4 tests in BASTPerformanceTest)

**Phase 4: Import Integration (COMPLETED)** ✅
- [x] Import syntax: `import "file.bast"` (simplified - no namespace clause)
- [x] Imports supported at root level AND inside domains
- [x] BASTLoader utility loads BAST files and populates BASTImport.contents
- [x] Path resolution works naturally (BASTImport is a Container)
- [x] Test: references like `ImportedDomain.SomeType` resolve correctly
- [x] 5 tests in BASTLoaderTest

**Phase 5: CLI & Testing (NEXT)** 🚧
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

1. **Simple import syntax** - `import "file.bast"` (no namespace clause)
   - RIDDL uses nested domains for namespacing, not a separate namespace concept
   - Reference imported definitions via domain paths: `ImportedDomain.SomeType`
   - Duplicate definitions produce errors

2. **Imports at root AND inside domains** - Two valid locations
   - Root level: for shared libraries
   - Inside domains: for domain-specific imports
   - Not allowed elsewhere (contexts, entities, etc.)

3. **Writer as Pass** - `BASTWriterPass` (in passes module) extends `HierarchyPass`
   - Idiomatic RIDDL architecture
   - Automatic traversal infrastructure
   - Uses `BASTWriter` utilities from language module for actual byte writing

4. **BASTImport as Container** - Resolution works naturally
   - ResolutionPass traverses Container.contents automatically
   - No special handling needed in resolution pass

#### Files Created

All BAST source files are in `language/shared/src/main/scala/com/ossuminc/riddl/language/bast/`:

**Core** (in `language/shared/.../bast/`):
- `package.scala` - Constants, node tags (NODE_*, TYPE_*), flags
- `StringTable.scala` - String interning for compression
- `BASTWriter.scala` - Writing utilities (not a Pass)
- `BASTReader.scala` - Deserialization with `readNode()` and `readTypeExpression()`
- `BASTLoader.scala` - Import loading utility
- `BASTUtils.scala` - Shared utilities

**Pass** (in `passes/shared/.../passes/`):
- `BASTWriterPass.scala` - Serialization pass (extends HierarchyPass)

**Tests** (in `passes/jvm/src/test/scala/com/ossuminc/riddl/passes/`):
- `BASTMinimalTest.scala` - Basic serialization test (1 test)
- `BASTIncrementalTest.scala` - 37 incremental round-trip tests covering all AST structures
- `BASTWriterSpec.scala` - Serialization tests (5 tests)
- `BASTRoundTripTest.scala` - Round-trip tests (3 tests)
- `BASTPerformanceBenchmark.scala` - Performance benchmarks comparing parse vs load (3 tests)
- `BASTLoaderTest.scala` - Import integration tests (4 tests)
- `BASTDebugTest.scala` - Byte-level debugging test (1 test)
- `BASTBenchmarkRunner.scala` - Standalone benchmark runner (runMain)
- `DeepASTComparison.scala` - AST comparison utility for round-trip verification

**Total: 60 BAST tests** (as of Jan 2026)

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

5. **CRITICAL: readNode() vs readTypeExpression() in BASTReader**:
   - `readNode()` handles **definition-level tags**: NODE_TYPE, NODE_DOMAIN, NODE_CONTEXT, NODE_ENTITY, NODE_FIELD, NODE_ENUMERATOR, etc.
   - `readTypeExpression()` handles **type expression tags**: TYPE_REF, TYPE_ALTERNATION, TYPE_AGGREGATION, TYPE_STRING, TYPE_NUMBER, etc.
   - **These are DISJOINT sets** - readNode() does NOT handle TYPE_* tags!
   - When reading contents that contain type expressions (e.g., `Alternation.of` which contains `AliasedTypeExpression`), you MUST use `readTypeExpression()`, not `readNode()` via `readContentsDeferred()`
   - **Bug pattern**: If you see "Invalid string table index" errors with huge counts like `metadata[1000009]`, it usually means the reader is misaligned because it tried to read a TYPE_* tag as a NODE_* tag
   - **Fix pattern**: Create specialized reader methods like `readTypeExpressionContents()` that read count + call `readTypeExpression()` for each item

6. **Inline Methods for Size Optimization** (Jan 2026):
   - `writeIdentifierInline()` / `readIdentifierInline()` - Used after definition tags when identifier position is known
   - `writePathIdentifierInline()` / `readPathIdentifierInline()` - Used in all reference types since PathIdentifier position is always known
   - `writeTypeRefInline()` / `readTypeRefInline()` - Used in State, Inlet, Outlet, Input where TypeRef position is fixed
   - Inline methods omit the tag byte, saving ~1 byte per usage
   - Tags are still needed for: polymorphic cases (Field/MethodArgument, Outlet/ShownBy, User/UserStory, Group/ContainedGroup)

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

## Working with riddlc CLI

After staging (`sbt riddlc/stage`), the `riddlc` executable provides:

```bash
riddlc help              # Show all available commands
riddlc version           # Version information
riddlc info              # Build information
riddlc parse <file>      # Parse RIDDL file
riddlc validate <file>   # Validate RIDDL file
```

Commands can load options from HOCON config files.

**Executable location**: `riddlc/jvm/target/universal/stage/bin/riddlc`

---

## Development Patterns

### Adding a New Module

```scala
lazy val mymodule_cp = CrossModule("mymodule", "riddl-mymodule")(JVM, JS, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp))
  .configure(With.typical, With.GithubPublishing)
  .settings(
    description := "Description here"
  )
  .jvmConfigure(With.coverage(50))
  .jsConfigure(With.ScalaJS("RIDDL: mymodule", withCommonJSModule = true))
  .nativeConfigure(With.Native(mode = "fast"))

lazy val mymodule = mymodule_cp.jvm
lazy val mymoduleJS = mymodule_cp.js
lazy val mymoduleNative = mymodule_cp.native
```

Then add to root aggregation: `.aggregate(..., mymodule, mymoduleJS, mymoduleNative)`

**Note**: Use `With.ScalaJS(...)` for sbt-ossuminc 1.0.0+, not `With.Javascript(...)`

### Adding a New Pass

1. Extend `Pass`, `DepthFirstPass`, or `HierarchyPass`
2. Implement `process()` method for each AST node type
3. Declare dependencies via `def requires(): Seq[Pass] = Seq(...)`
4. Override `result()` to return your `PassOutput` subclass
5. Add to standard passes or invoke explicitly

### Adding a New Command

1. Define options: `case class MyOptions(...) extends CommandOptions`
2. Define command: `class MyCommand extends Command[MyOptions]`
3. Implement:
   - `def name: String`
   - `def getOptionsParser: OptionParser[MyOptions]`
   - `def run(options: MyOptions, context: PlatformContext): Either[Messages, PassesResult]`
4. Register with `CommandLoader` if using plugin system

---

## Notes for Future Sessions

1. **Always check sbt-ossuminc version** - API may have changed
2. **BAST version is single integer** - `VERSION: Int = 1`, stays at 1 until schema finalized for users
3. **Import stub exists but not implemented** - See ParsingContext.scala:81-89, TODO issue #72
4. **npm packages are versioned by git** - Rebuild after commits to get new version
5. **Workflow improvements made** - Check .github/workflows/ for latest patterns
6. **ONLY commit files related to your current task** - Multiple work streams may be active
7. **Check git status carefully before committing** - Unstage unrelated files
8. **RiddlAPI origin parameters must be URLs** - Use `originToURL()` helper for String origins
9. **Scala 3 lambda syntax required** - `foreach(line => func(line))` not `foreach(func)`
10. **Share code via utils/shared/** - For code used by both JVM and JS variants
11. **BAST code lives in language module** - `language/shared/.../bast/`, NOT the standalone `bast/` directory
12. **BAST readNode() vs readTypeExpression()** - Disjoint tag sets; see "Key Implementation Notes" for details on avoiding deserialization bugs
13. **BAST Hugo documentation is outdated** - `doc/src/main/hugo/content/future-work/bast.md` needs complete rewrite
14. **Use `With.scala3` for Scala version** - Sets Scala 3.3.7 LTS; don't hardcode `scalaVersion` in build.sbt
15. **BAST location comparisons use offsets** - BASTParserInput uses synthetic 10000-char lines for reconstruction; compare offset/endOffset, not line/col
16. **Scala version changes require workflow updates** - When Scala LTS version changes, update all `scala-X.Y.Z` paths in `.github/workflows/*.yml`; see "Scala Version Change Impact" section
