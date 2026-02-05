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
- **Scala 3.7.4** (not Scala 2!) — overrides sbt-ossuminc's 3.3.7 LTS
  default due to a compiler infinite loop bug in 3.3.x with opaque
  types and intersection types (see `build.sbt` header comment)
- **ALWAYS use Scala 3 syntax**:
  - `while i < end do ... end while` (NOT `while (i < end) { ... }`)
  - No `null` checks - use `Option(x)` instead
  - New control flow syntax with `do`/`then`/`end`

### sbt-ossuminc Plugin

**Current version: 1.3.0** (updated Feb 2026)

#### API Changes from 0.x to 1.0.0:
- `With.Javascript(...)` → `With.ScalaJS(...)`  or `With.scalajs` (lowercase for default)
- `With.Native()` → `With.Native(...)` (now requires parameter list, not just `()`)
- `With.BuildInfo.withKeys(...)` → `With.BuildInfo.withKeys(...)(project)` (curried function)

#### Common Configurations:
```scala
// Scala 3.7.4 (overridden from sbt-ossuminc's 3.3.7 LTS default)
.configure(With.scala3)  // Sets scalaVersion to 3.3.7 LTS
// Then override: scalaVersion := "3.7.4"

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

**Building npm packages** (via sbt-ossuminc 1.3.0 helpers):
```bash
sbt riddlLibJS/npmPrepare        # Assemble package (pure sbt)
sbt riddlLibJS/npmPack           # Create .tgz tarball
sbt riddlLibJS/npmPublishGithub  # Publish to GH Packages
sbt riddlLibJS/npmPublishNpmjs   # Publish to npmjs.com
```

**CI Workflow**: `.github/workflows/npm-publish.yml` triggers on
release or manual dispatch, uses sbt tasks directly.

**Module format**: ESModule (`"type": "module"` in package.json).
Consumers use `import { RiddlAPI } from '@ossuminc/riddl-lib'`.

**Documentation**:
- `NPM_PACKAGING.md` - npm build and installation guide
- `TYPESCRIPT_API.md` - Complete TypeScript API reference

**Published**: `@ossuminc/riddl-lib` on GitHub Packages npm registry

## Import vs Include

**CRITICAL DISTINCTION**:

### Include (Context-Aware)
- Can appear anywhere in hierarchy
- Parser rules determined by enclosing container
- `include "entities.riddl"` in a Context → must contain Context-valid content
- **Already implemented**

### Import (BAST Files) - COMPLETE ✅
- Loads BAST-serialized content into RIDDL models
- **Full import**: `import "file.bast"` — loads all Nebula contents
- **Selective import**: `import domain X from "file.bast"`
- **Aliased import**: `import type T from "file.bast" as MyT`
- **Allowed locations**: Root level, inside domains, inside contexts
- 14 definition kinds supported (domain, context, entity, type, etc.)
- **Key files**:
  - `CommonParser.scala` — `bastImport()`, `selectiveBastImport()`
  - `TopLevelParser.scala` — `loadBASTImports()` post-parse loading
  - `BASTLoader.scala` — BAST file reading and content population
  - `AST.scala` — `BASTImport` case class
- **Tests**: 4 passing in `BASTLoaderTest.scala`
- **Validation**: Integrated into `ValidationPass`

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
- Current LTS: **3.3.7** (3.3.x series), but riddl uses **3.7.4**
  due to compiler bug (see build.sbt)
- Next LTS expected: **3.9.x** (Q2 2026)

**Quick Search to Find All References**:
```bash
grep -r "scala-3\." .github/workflows/
```

**Example Fix** (3.7.4 → 3.9.0):
```bash
# In each workflow file, replace all occurrences:
sed -i 's/scala-3.7.4/scala-3.9.0/g' .github/workflows/*.yml
```

## Testing Patterns

### Parser/EBNF Synchronization Requirement

**Any change to the fastparse parser MUST have a corresponding change to the EBNF grammar.**

The EBNF grammar at `language/shared/src/main/resources/riddl/grammar/ebnf-grammar.ebnf`
is the canonical specification of RIDDL syntax. It is validated by a TatSu-based parser
that runs in CI on all `**/input/**/*.riddl` test files.

When modifying the fastparse parser:
1. Update the corresponding rule(s) in `ebnf-grammar.ebnf`
2. Run the EBNF validator locally:
   ```bash
   cd language/jvm/src/test/python
   pip install -r requirements.txt  # first time only
   python ebnf_tatsu_validator.py
   ```
3. Ensure both parsers accept the same inputs
4. CI will fail if the EBNF parser cannot parse test files that fastparse accepts

This ensures the documented grammar stays in sync with the actual implementation.

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

### Error: "No given instance of PlatformContext for default parameter"
**Cause**: Scala 3.7.4 limitation — default parameter values in a case
class's first parameter list cannot resolve `given` instances from a
subsequent `using` clause in the generated companion `apply` method.
**Fix**: Remove the default value. May be fixed in 3.9.x LTS.
**Example**:
```scala
// This fails in 3.7.4:
case class Foo(x: Bar = Bar())(using PlatformContext)
// Fix: remove default (or provide explicit given)
case class Foo(x: Bar)(using PlatformContext)
```

### Error: "parameters with defaults must be at the end" (Scala.js)
**Cause**: `@JSExportTopLevel` on a case class with `(using
PlatformContext)` in a second parameter list. The JS export sees the
context parameter as a non-default parameter after defaulted params.
**Fix**: Remove `@JSExportTopLevel` from internal data structures that
don't need to be constructed from JS code.

### System.lineSeparator() returns null in Scala.js
**Cause**: `System.lineSeparator()` returns `\0` in Scala.js
**Fix**: Use `PlatformContext.newline` instead. Never use
`System.lineSeparator()` in shared code. The `FileBuilder` trait
and its entire hierarchy use `(using PlatformContext)` for this.

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

## Current Work Priorities

### 1. AIHelperPass
AI-friendly validation pass for MCP server integration. Design complete in NOTEBOOK.md.

- Provides proactive guidance (Tips) rather than just errors/warnings
- Works on incomplete models (no ResolutionPass dependency)
- Designed for iterative AI-driven model building

---

## BAST Module (Binary AST) - COMPLETE ✅

BAST serialization is **fully implemented** with 60 tests passing and 6-10x speedup.

### CLI Commands
- `riddlc bastify <input.riddl>` - Convert RIDDL to BAST
- `riddlc unbastify <input.bast>` - Convert BAST back to RIDDL (pending)

### Performance Results
- **Speed**: 6-10x faster than parsing RIDDL source
- **Size**: 63-67% of source file size for non-trivial inputs
- **Cross-platform**: JVM, JS, Native all supported

### Key Files
**Language module** (`language/shared/.../bast/`):
- `BASTWriter.scala`, `BASTReader.scala` - Serialization/deserialization
- `BASTLoader.scala`, `BASTUtils.scala` - Loading utilities
- `StringTable.scala`, `PathTable.scala` - Interning tables

**Passes module**: `BASTWriterPass.scala` - Pass wrapper for AST traversal

### Documentation
BAST format specification and API documentation should be added to
**ossum.tech/riddl** (not this repository). See the Documentation section above.

### Critical Implementation Notes

**readNode() vs readTypeExpression()** - These handle DISJOINT tag sets:
- `readNode()` → NODE_* tags (definitions)
- `readTypeExpression()` → TYPE_* tags (type expressions)
- Mixing them causes byte misalignment ("Invalid string table index" errors)

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
3. **BAST Import is fully implemented** - Full, selective, and aliased imports all work (Issue #72 resolved)
4. **npm packages are versioned by git** - Rebuild after commits to get new version
5. **Workflow improvements made** - Check .github/workflows/ for latest patterns
6. **ONLY commit files related to your current task** - Multiple work streams may be active
7. **Check git status carefully before committing** - Unstage unrelated files
8. **RiddlAPI origin parameters must be URLs** - Use `originToURL()` helper for String origins
9. **Scala 3 lambda syntax required** - `foreach(line => func(line))` not `foreach(func)`
10. **Share code via utils/shared/** - For code used by both JVM and JS variants
11. **BAST code lives in language module** - `language/shared/.../bast/`, NOT standalone directory
12. **BAST readNode() vs readTypeExpression()** - Disjoint tag sets; mixing causes byte misalignment
13. **Scala version is 3.7.4** - Each module overrides `scalaVersion := "3.7.4"` (3.3.x has compiler bug with opaque types)
14. **BAST location comparisons use offsets** - Compare offset/endOffset, not line/col
15. **Scala version changes require workflow updates** - Update `scala-X.Y.Z` paths in workflows
16. **All RIDDL documentation goes to ossum.tech** - Don't add docs to this repo's `doc/` directory
17. **Never use System.lineSeparator() in shared code** - Use `PlatformContext.newline` instead; returns `\0` in Scala.js
18. **FileBuilder requires PlatformContext** - `trait FileBuilder(using PlatformContext)` — all subclasses must propagate the using clause
19. **Scala 3.7.4 default param limitation** - Case class defaults can't resolve givens from a subsequent using clause in generated apply; remove defaults or provide explicit givens
20. **@JSExportTopLevel incompatible with using clauses** - Don't use on case classes that have `(using PlatformContext)` in a second parameter list
21. **npm packaging uses sbt-ossuminc helpers** - `With.Packaging.npm()` assembles package, `With.Publishing.npm()` publishes. Tasks: `npmPrepare`, `npmPack`, `npmPublishGithub`
22. **npmTypesDir fixed in sbt-ossuminc 1.3.0** - Earlier versions had a convention mismatch (JS variant `baseDir/js/types/` doubled to `module/js/js/types/`). No override needed with 1.3.0+
23. **npm requires --tag for prerelease versions** - sbt-dynver versions like `1.2.3-1-hash` are prerelease per npm semver. Must pass `--tag dev` when publishing
24. **riddlLib JS is ESModule** - Changed from CommonJS (`withCommonJSModule = true` removed). Package.json has `"type": "module"`. Consumers use `import { RiddlAPI } from '@ossuminc/riddl-lib'`
25. **gh auth needs write:packages for npm** - Run `gh auth refresh -s write:packages` if publishing to GH Packages npm registry
