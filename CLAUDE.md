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

## Backward Compatibility Policy

RIDDL is a heavily used library both by Ossum Inc. and external
consumers. **Never make incompatible changes** to public APIs without
following this process:

1. **No removal of public API** — Do not remove public methods, classes,
   traits, or extension methods. If functionality must be retired, add
   `@deprecated` annotations with a migration message and the target
   major version for removal (e.g., `@deprecated("Use flatten() instead",
   "2.0.0")`).
2. **No breaking signature changes** — Do not change parameter types,
   return types, or add required parameters to existing public methods.
   New parameters must have defaults.
3. **Deprecation warnings until next major release** — Deprecated APIs
   must remain functional through the current major version (1.x). They
   may only be removed in the next major release (2.0.0).
4. **Additive changes only** — New methods, extension methods, classes,
   and traits are always safe. Prefer adding new APIs alongside old ones
   rather than modifying existing ones.

When in doubt, **add, don't change**.

## Critical Build Information

### Scala Version & Syntax
- **Scala 3.9.0-RC4** (not Scala 2!) — RIDDL 2.0 rides ahead of LTS
  on Scala Next. Pinned via `V.scala` + `With.Scala3.configure(version
  = Some(V.scala))` on every CrossModule (sbt-ossuminc's `With.typical`
  otherwise pins its default 3.8.4, applied after `scalaVersion :=`, so
  the plain setting is a no-op — the `With.Scala3.configure` override is
  the real lever). Bump to `3.9.0` when the final release ships (and
  re-grep the `scala-3.9.0-RC4` CI/target paths).
- **Build files are Scala 3 too** — since the sbt 2 upgrade,
  `build.sbt` and `project/*.scala` compile with Scala 3 (no more
  Scala 2.12 build-def rule).
- **ALWAYS use Scala 3 syntax**:
  - `while i < end do ... end while` (NOT `while (i < end) { ... }`)
  - No `null` checks — use `Option(x)` instead
  - New control flow syntax with `do`/`then`/`end`

### sbt-ossuminc Plugin

**Current version: 3.0.3** (sbt 2.0.2, projectMatrix-based
CrossModule). Requires sbt **2.0.2+** — pinned in
`project/build.properties`. sbt 2 credentials live in `~/.sbt/2/`.

#### CrossModule / projectMatrix layout:
- `CrossModule(dir, mod, V.scala)(JVM, JS, Native)` takes the Scala
  version and wraps sbt 2's built-in `projectMatrix`. Extract rows
  with `.jvm`/`.js`/`.native`; wire deps per-row (no cp-level
  `.dependsOn`).
- **Flat source tree** (no more `shared/jvm/js/native`):
  `<mod>/src/{main,test}/scala` (shared), `.../scalajvm`,
  `.../scalajs`, `.../scalanative`, and `.../scala-jvm-native`
  (JVM+Native shared, wired via `unmanagedSourceDirectories`).
- **Build outputs** live under a central virtual-FS tree
  (`sbt.io.virtual=true`, the default): `target/out/<platform>/
  scala-<fullVersion>/<artifactName>/…` — e.g.
  `target/out/jvm/scala-3.9.0-RC4/riddl-utils/`,
  `target/out/sjs1/scala-3.9.0-RC4/riddl-lib/`,
  `target/out/native0.5/scala-3.9.0-RC4/riddlc/`. NOT per-module
  `<mod>/target/…`. Platform dirs are `jvm`/`sjs1`/`native0.5`;
  the path carries the **full** Scala version, not a `-3` binary tag.
- 3.0.3's CrossModule auto-adds `scalajs-stubs % provided` to the
  JVM/Native rows of any module that also targets JS (so shared
  `@JSExport*` code compiles) — no consumer dep needed.
- Cross-platform deps use plain `%%` (the `%%%` operator is gone).

#### Common Configurations:
```scala
// Scala 3.9.0-RC4 — override sbt-ossuminc's 3.8.4 default per module:
.configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
// (plain `scalaVersion := V.scala` is a no-op — With.typical wins over it)

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
**Purpose**: Binary AST serialization for fast module imports.
**Status**: Complete; ~6-10x faster than reparsing source; output
~63-67% of source size on non-trivial inputs.

- **Package**: `com.ossuminc.riddl.language.bast` in
  `language/src/main/scala/com/ossuminc/riddl/language/bast/`
- **Cross-platform**: JVM, JS, Native
- **Pass**: `passes/shared/.../BASTWriterPass.scala`
- **CLI**: `riddlc bastify <file.riddl>` (write);
  `riddlc unbastify` (read — **implemented**; `UnbastifyCommand`, and
  `RiddlModelsRoundTripTest` exercises it over the whole corpus. This line
  said "pending" until 2026-08-11.)
- **Format docs**: live at ossum.tech/riddl, not in this repo

**Key files** in the bast package:
- `package.scala` — constants and node type tags (NODE_*, TYPE_*,
  STREAMLET_*, …)
- `BASTWriter.scala` — serialization (extends HierarchyPass)
- `BASTReader.scala` — deserialization
- `BASTLoader.scala` — import-loading utility
- `BASTUtils.scala` — shared utilities
- `StringTable.scala`, `PathTable.scala` — interning tables

**HAZARD — disjoint tag sets**: `readNode()` only handles `NODE_*`
tags; `readTypeExpression()` only handles `TYPE_*` tags. Crossing
them causes byte misalignment that surfaces as "Invalid string
table index" errors during deserialization.

**HAZARD — one tag per WIRE SHAPE, not per family.** `Constant` and
`Method` were both written with `NODE_FIELD` because all three are
"a name and a type". But a Constant appends its literal value and a
Method appends its argument list, so the reader — which read a Field
— left those bytes in the stream and every byte after such a node
was misread. Fixed 2026-08-13 with `NODE_CONSTANT` (109) /
`NODE_METHOD` (110) and `FORMAT_REVISION` 14.

The reader had ADMITTED it in a comment (*"This is ambiguous … For
now, assume Field. Writer should disambiguate better"*), which is
the part worth learning from: a known-ambiguous decode is a latent
corruption, not a rough edge. **The rule is that two node kinds may
share a tag only if they write byte-identical payloads.**

**A BAST error names where the reader DERAILED, never what derailed
it.** The same single constant surfaced as `Invalid string table
index` in a 13-node model and as `Invalid invariant condition kind:
67` in a 9618-node one, sending both riddl-models and this repo to
bisect an innocent invariant. When diagnosing, bisect toward the
node BEFORE the reported position, and distrust the construct named.

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

**Building npm packages** (via sbt-ossuminc 2.0.1 helpers):
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

### CRITICAL: Target-path layout (sbt 2 virtual FS)

Since the sbt 2 upgrade, build outputs live under a **central**
virtual-FS tree at the repo root (verified empirically — sbt runs with
`sbt.io.virtual=true`):

```
target/out/<platform>/scala-<fullVersion>/<artifactName>/…
```

- `<platform>` ∈ `jvm`, `sjs1`, `native0.5` (NOT `js`/`native`).
- `<fullVersion>` is the **full** Scala version (`scala-3.9.0-RC4`), NOT a
  `-3` binary tag — so a Scala patch bump (3.8.4 → 3.8.5 / 3.9.x) DOES
  move every hardcoded path.
- `<artifactName>` is the `moduleName` (`riddl-utils`, `riddl-lib`,
  `riddlc`, …).

Verified real paths:
- native riddlc: `target/out/native0.5/scala-3.9.0-RC4/riddlc/riddlc`
- native lib: `target/out/native0.5/scala-3.9.0-RC4/riddl-lib/libriddl-lib.a`
- JS opt: `target/out/sjs1/scala-3.9.0-RC4/riddl-lib/riddl-lib-opt/main.js`
- JVM stage: `target/out/jvm/scala-3.9.0-RC4/riddlc/universal/stage/bin/riddlc`
- scoverage: `target/out/jvm/scala-3.9.0-RC4/<artifact>/scoverage-report/scoverage.xml`

Files that hardcode these (update on any full-Scala-version bump):
**scala.yml** (`RIDDLC_PATH`, cache `target/out`, artifact upload paths),
**coverage.yml** + **.sonarcloud.properties** (scoverage), **release.yml**
(native cp + JVM stage zip), **Dockerfile** (stage copy).

**Quick search:** `grep -rn "target/out/.*scala-3\." .github/ Dockerfile .sonarcloud.properties`

**sbt-ossuminc Version Policy**:
- sbt-ossuminc 3.0.x defaults to Scala **3.8.4**; riddl 2.0 overrides to
  **3.9.0-RC4** via `V.scala` + `With.Scala3.configure(version = Some(V.scala))`
  per module (the `CrossModule(...)` axis arg alone does NOT change the
  effective scalaVersion — With.typical overrides it).
- A Scala version bump changes the `scala-<fullVersion>` path segment
  everywhere above — grep and update.

## Testing Patterns

### Parser/EBNF Synchronization Requirement

**Any change to the fastparse parser MUST have a corresponding change to the EBNF grammar.**

The EBNF grammar at `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf`
is the canonical specification of RIDDL syntax. It is validated by a TatSu-based parser
that runs in CI on all `**/input/**/*.riddl` test files.

When modifying the fastparse parser:
1. Update the corresponding rule(s) in `ebnf-grammar.ebnf`
2. Run the EBNF validator locally:
   ```bash
   cd language/src/test/scalajvm/python
   pip install -r requirements.txt  # first time only
   python ebnf_tatsu_validator.py
   ```
3. Ensure both parsers accept the same inputs
4. CI will fail if the EBNF parser cannot parse test files that fastparse accepts

This ensures the documented grammar stays in sync with the actual implementation.

### Reflection / Round-Trip Requirement

**RIDDL is fully reflective by design and necessity: anything that can be
parsed MUST also be emitted.** So a change to the AST or parser is only
half done until PrettifyPass emits the new/changed construct AND a
parse → prettify → re-parse round-trip preserves it. "Parses and
validates" is half the contract; **emit + round-trip is the other half.**

When you add or move a construct (e.g. allowing a definition under a new
container):

1. Confirm `PrettifyVisitor` / `RiddlFileEmitter` emit it. Traversal
   (`HierarchyPass`) and dispatch (`VisitingPass`, `Pass.scala`) are
   generic and type-based, so it often "just works" — but **prove it,
   don't assume it.**
2. **Add a round-trip test** — parse → `PrettifyPass(flatten=true)` →
   re-parse — asserting the construct survives at the SAME place (not
   dropped, not relocated). Template:
   `passes/.../prettify/RepositoryDomainScopeRoundTripTest.scala` (and
   `IdentifierQuotingRoundTripTest.scala`).
3. **Run the FULL suite on all platforms** (`tJVM tJS tNative`), not just
   the module you touched. A green partial suite proves nothing when no
   existing test exercises the new shape.

Also remember BAST (binary AST) is a second serialization surface: a new
AST node generally needs BASTWriter/BASTReader support and a
`FORMAT_REVISION` bump (see the BAST section).

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
**Cause**: Scala 3.8.x limitation — default parameter values in a case
class's first parameter list cannot resolve `given` instances from a
subsequent `using` clause in the generated companion `apply` method.
**Fix**: Remove the default value. May be fixed in 3.9.x LTS.
**Example**:
```scala
// This fails in 3.8.x:
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
1. Create directory: `<moduleName>/src/{main,test}/scala/...`
2. Add to `build.sbt` using `CrossModule` (pass `V.scala`)
3. Add variants to root aggregation; wire deps per-row with `pDep()`
4. Add platform-specific dirs as needed (see below)

### Cross-Platform Considerations (projectMatrix layout)
- **Shared code**: `<module>/src/{main,test}/scala`
- **Platform-specific**: `<module>/src/{main,test}/scala{jvm,js,native}`
- **JVM+Native shared**: `<module>/src/{main,test}/scala-jvm-native`
  (custom dir; wire with `jvmNativeSrc(...)` in build.sbt)
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

Co-Authored-By: Claude <model-name> <noreply@anthropic.com>
```

### Branch Strategy
- **main** is both the working branch and the release branch —
  commit directly to it. There is **no GitFlow** and no permanent
  `development` branch (see `../CLAUDE.md` "Git Workflow").
- Cut releases by tagging `main`; CI builds from the tag.
- Reach for a short-lived branch only when you want isolation (a
  throwaway experiment, or work you'd like to review as a diff),
  then merge and delete it.
- The `development` and `old-development` branches linger from the
  GitFlow era. As of 1.31.0 `development` is fully contained in
  `main` (0 commits ahead) and is a deletion candidate.
- **Note:** `.claude/skills/ship/SKILL.md` still prescribes
  GitFlow steps — fast-forwarding `main` from `development`
  pre-release, and merging back to `development` post-release.
  Both are no-ops or contrary to current policy. Skip them and
  fix the skill when convenient.

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

## RiddlLib & RiddlAPI Patterns

### Architecture

Core parsing/validation logic lives in `RiddlLib` (shared trait +
companion object) at `riddlLib/shared/.../RiddlLib.scala`. This is
usable on JVM, JS, and Native. The JS-only `RiddlAPI.scala` is a
thin facade that delegates to `RiddlLib` and converts results to
plain JavaScript objects.

- **Cross-platform code**: Use `RiddlLib.parseString(...)` etc.
  with a `given PlatformContext` in scope (provided by each
  platform's `com.ossuminc.riddl.utils.pc`)
- **JS facade**: `RiddlAPI` adds `@JSExport` methods, `getDomains`,
  `inspectRoot`, and JS-only helpers like `formatErrorArray`

### Origin Parameter Pattern

**CRITICAL**: All methods that accept an `origin` parameter use
`RiddlLib.originToURL()` to convert strings to URLs.

```scala
def originToURL(origin: String): URL =
  if origin.startsWith("/") then
    URL.fromFullPath(origin)
  else
    URL(URL.fileScheme, "", "", origin)
  end if
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
// utils/src/main/scala/com/ossuminc/riddl/utils/InfoFormatter.scala
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

## Subtle Patterns and Gotchas

Each subsection is a topic, not a serial number — add new entries
to the right group rather than appending to a list.

### BAST

- **VERSION is a single integer** (`VERSION: Int = 1`) and stays
  at 1 until the schema is finalized for external users.
- **FORMAT_REVISION** must be incremented whenever a BASTWriter
  change produces output that an older BASTReader can't read
  correctly: new statement subtypes, wire-format changes,
  reordered fields, new node tags. Constant lives in
  `language/shared/.../bast/package.scala`.
- **Location comparisons use offsets**, not `line`/`col`.
- **BASTImport in HierarchyPass** — `openBASTImport` /
  `closeBASTImport` hooks plus `traverseBASTImportContents(bi)`.
  All `PassVisitor` implementors must define these (even as
  no-ops); `BASTImport` extends `Container` but not `Branch`, so
  without the hooks it falls through and its contents are never
  visited.

### AST / Language Internals

- **The predefined `Riddl` standard module** (`language/.../
  PredefinedModule.scala`) is readable RIDDL held in a string constant,
  parsed ONCE and cached as a singleton. It holds `type Drain is
  Anything` plus the two terminators `BottomlessPit` (sink, inlet
  `hole`) and `ForeverEmpty` (source, outlet `void`), directly in the
  module (no domain/context — `ModuleContents` is `NebulaContents`).
  **NEVER inject it into a user's `Root.contents`.** The ONLY seam is
  `SymbolsPass.postProcess`, which seeds `predefinedSymTab` /
  `predefinedParentage` — separate maps on `SymbolsOutput` that lookups
  fall back to. Keeping them separate is load-bearing: several public
  APIs (`AnalysisResult.domains/streamlets/…`, `UseCaseWitnessPass`,
  `foreachOverloadedSymbol`) ENUMERATE `parentage`/`symTab`, and seeding
  the shared maps leaks the standard library into "all X in the model".
  A user definition with a colliding name wins structurally (the user's
  table is consulted first) — no ambiguity, no message.
  It also holds **`Envelope`** (2.0.0-rc.10+), the record carrying a message's
  metadata, selected by `option message_envelope("Riddl.Envelope")`. Fields are
  the **CloudEvents v1.0 context attributes**, with ONE forced deviation:
  CloudEvents `id` is spelled **`messageId`**, because RIDDL requires
  identifiers of >= 3 chars and `id` draws a StyleWarning — and the standard
  module must validate clean. There is deliberately **no `data` field**: in
  RIDDL the payload IS the message, already modelled and typed, so Envelope is
  the metadata AROUND a message rather than a wrapper containing one. The
  option is **scope-inherited** (`Seq.empty` validParents — resolved by walking
  UP the parent chain), so declaring it on a context covers every entity in it.
  Opt-in by design: RIDDL specifies meaning, not representation, so how the
  attributes ride (CloudEvents JSON, Kafka headers, gRPC metadata, or nothing
  for an in-process call) stays the generator's choice.
  Both records are legitimately **unused inside the module** — that is the
  design, not a defect — so `PredefinedTerminatorsTest` asserts exactly which
  ones are unused; widen that list when adding another, never loosen it.
  All exemptions (A31 cardinality, unattached/isolated/reachability,
  handler completeness) test REFERENCE IDENTITY via
  `PredefinedModule.isPredefined`, never a name. A port typed `Anything`
  is connector-compatible with every type (`validateConnector`).
  `language/input/predefined/riddl-standard-module.riddl` is a verbatim
  copy so the CI grammar validators cover it; `PredefinedModuleSourceTest`
  fails if the copy drifts from the constant.

- **`on other as x [: <envelope>]` (A57)** — binds the residual message's
  ENVELOPE, not a message: the clause names none. `OnOtherClause` gains
  `binding: Option[Identifier]` and `envelopeType: Option[TypeRef]`, both
  declared BEFORE `contents` and WITHOUT defaults (`@JSExportTopLevel` needs
  defaulted params trailing — same rule as A55). `x`'s type is the ascription
  when written, else whatever `option message_envelope` names in scope
  (`ResolutionPass.envelopePathFor`), so `x` and `x.source` both resolve.
  **The ascription RESTATES the option, it never overrides it.** Three Errors in
  `checkOnOtherBinding`: a binding with no envelope in scope, an ascription with
  no envelope in scope, and an ascription that contradicts the option. A
  per-clause override would mean reading one clause tells you nothing about its
  siblings — exactly what scope inheritance prevents.
  **The type is BARE after the colon** — no keyword. `message` would be untrue
  and `type` is correct only because it is vacuous; the colon already says a
  type follows. Both spellings parse elsewhere in RIDDL, so this is a choice
  about meaning, not consistency.
  **`OnOtherClause` must NOT join `OnMessageLikeClause`** — that is what keeps
  it out of `UseCaseWitnessPass`'s index (see its comment); a clause matching
  every type would witness every step.
  **Rendering lives in `Declaration.ascription`, NOT in the clause's `format`.**
  The prettifier reads the former via `openDef`; putting it on `format` alone
  makes prettify silently DROP the binding on every round trip. That shipped as
  a bug for exactly one commit and is what `OnOtherEnvelopeRoundTripTest` pins.
- **Correlations in projectors (A70, release/2)** — `correlation <id> by <k>[,
  <k>…] yields command <C> is { <handler> } times out after "<duration>" {
  <statements> } [with { … }]`. A keyed accumulation of several events into one
  command the Repository handles. **Semantics live in
  `../RIDDL-Computational-Model.md` §6.2 and §6.5–§6.8 and are NOT restated in
  the code** — that document is the authority for any lowering decision.
  **`yields` names a COMMAND** (Reid, 2026-08-12; it was `yields record <T>`
  for one day). A projector's only output is a change to a repository, and a
  repository is changed by handling a command. The record form could never
  work: a handler clause takes a `messageRef`, which is the four real messages
  only (A9b), so **no `on` clause could name what the correlation produced** —
  which is why the first design had to INFER acceptance from a command that
  *held* the record. Naming the command deletes the inference. Enforced in two
  places on purpose: the wrong KEYWORD dies in the grammar (`commandRef` in
  `ProjectorParser`, so `yields record R` does not parse), while `yields
  command Foo` naming a non-command is an Error from `ValidationPass` — the
  only place with the resolved referent, and a parse-time `error()` there would
  preempt the whole pass chain.
  **The timeout clause is MANDATORY and is grammar, not metadata.** It was
  designed as an optional `else` block plus `option timeout(…)`, which left one
  question unanswerable — what an unbounded correlation means — and needed three
  warnings to paper over it. Reid's ruling made it mandatory, which deletes all
  three states instead of diagnosing them. The reasoning is entity intentions
  again: §4.2 calls options *advisory*, and a bound that MUST fire a block is
  not. Consequences: **no timeout inheritance from the Projector** (nothing is
  left to default, so `RecognizedOptions` is untouched by this feature), the
  duration is a `LiteralString` still duration-VALIDATED via
  `DefinitionValidation.checkPreciseDuration` (shared with the `timeout` option,
  so `times out after "banana"` is an Error), and an empty block is a parse
  error — `do "nothing"` is the discard idiom.
  **Keys are stored AS WRITTEN and never canonicalized**: `Definition.equals` is
  structural and §6.5 makes identity the full tuple, so sorting them would
  silently equate two different declarations. This is the exact OPPOSITE of
  `EntityIntention.canonical`, which sorts so that write order cannot make two
  identical entities compare unequal. Prettify, BAST and JSON all preserve order
  and each has a test asserting it.
  **The effect ban binds FOLDS only.** Fold purity is what makes re-runs safe
  (§6.5); the timeout block exists to have an effect (§6.7), so banning effects
  there would leave it useless. `CorrelationTest` pins both sides — without the
  "legal in the timeout block" case, a ban wrongly applied to the whole
  correlation would still look green.
  Two pre-existing projector checks (needs its own record type; exactly one
  handler) assumed folds live in one top-level handler and are SKIPPED when
  correlations are present; a projector without them validates as before.
  **The repository-accepts-it rule is a COMPLETENESS warning, not an Error**
  (Reid, 2026-08-12, overriding A70 as written): a repository lacking the
  `on command` clause is under-specified, not self-contradictory. A `???`
  repository is exempt, per the standing `???` ruling. Because `yields` names a
  command, the test is plain identity on the resolved `Type` (`eq`, not by
  name — two contexts may each declare a `RecordFulfillment`).
  **The unemitted-event warning does NOT use `MessageFlowPass`** — depending on
  it would reorder the standard passes. `checkCorrelationEventSources` sweeps
  the root once in `postProcess`, GATED on a correlation existing. An `Outlet`
  typed with the event counts as emitting it, so a `???` source that declares
  the port is not reported; adaptor translations deliberately do not count.

- **Processor instance identity (2.0, release/2)** — `Id(P)`, `self`,
  `initiate`, `terminate`, and structural `tell` addressing. Five constructs,
  one gap: **RIDDL could describe processors but not INSTANCES of them.**
  - **`Id(P)` names any Processor**, not just an Entity (Adaptor, Context,
    Entity, Projector, Repository, Streamlet). The keyword form
    `Id(entity Order)` is CANONICAL and the bare `Id(Order)` is the shorthand —
    `UniqueId.kindKeyword` stores the keyword *as written* (a `String`, not an
    enum, so prettify is byte-exact without a mapping table), and
    `TypeValidation` makes it **tell the truth**: a keyword contradicting the
    resolved referent's kind is an Error, because a wrong keyword is worse than
    no keyword — a reader believes it. Keyword-name disambiguation is a
    RIDDL-wide idiom and a bare `Order` could be a context, a message or an
    entity, which is why the keyword was kept rather than deprecated.
  - **`Id(P)` is RUNTIME instance identity and is NOT the definition ULID** of
    CM line 2523, which is model-time identity of a *definition*. Two instances
    of `Order` share one definition ULID and never share an `Id(Order)`.
    `isAssignmentCompatible` is deliberately UNCHANGED (still compatible with
    `String_`/`Pattern`): the value is opaque and system-generated, so a
    BUSINESS key belongs in `on init`'s parameters and lives in state.
  - **`self`'s type is a synthesized `Aggregation`, and that is load-bearing.**
    Because the type is an ordinary record, `let me = self` followed by `me.id`
    resolves through the SAME `ValueRef` path walk every other value uses — so
    no resolution rule anywhere has to know `self` exists. A bespoke node would
    have needed special-casing at each of those sites. The consequence is that
    the type is not user-nameable (`self.id` is `Id(Order)` in an Order handler
    and `Id(Shipping)` in a Shipping one), so `let me: T = self` has no `T` to
    write and `self` is not assignable into a message field — pass `self.id`.
    `SelfValue.fieldNames` is a CLOSED set (`id`, `version`); adding one is a
    language change. The admission test is **runtime-only**: anything a
    generator can know statically it should inline, which is why `version` is
    in and `isClustered` is not (filed separately).
    `enclosingProcessorOf` terminates at `Function` AND `Saga` — a Saga sits
    inside a Context routinely, so without the second terminator `self` in a
    saga step silently typed as the enclosing Context's identity.
  - **`initiate` supplies the invocation `on init` always lacked** — it does
    NOT add a second way for an instance to exist. Construction still completes
    only when `on init` finishes; CM line 999's "activate on first message" is
    rehydration, not creation. Without it no `Id(P)` value could ever come into
    being and the whole addressing story would have been inert.
    **`initiate` is a VALUE (it yields the new `Id(P)`) and `terminate` is a
    STATEMENT (termination produces nothing).** That asymmetry is why their
    bans live in validation and not the parser: `value` carries no
    `StatementsSet` to gate on, so parser-gating one and validating the other
    would split one rule across two layers. `on init`/`on term` gained
    parameter lists; arity and argument types are checked in `ValidationPass`
    (`checkInitiate`/`checkTerminate`), never the parser, because a parse-time
    `error()` preempts the whole pass chain. Both fold an Entity's STATE
    handlers in when looking for the clause, exactly as `validateAsk` does —
    `on init` commonly lives inside a `State`.
    **NEITHER clause requires a parameter, and `terminate`'s parentheses are
    OPTIONAL** (Reid, 2026-08-14). `on term` briefly required a leading
    `Id(<enclosing processor>)` on the reasoning that it is invoked from outside
    so the caller must say which instance — true, but it does not follow that
    the CLAUSE must declare it: **`self` is in scope for the whole body and
    stays live to the very end of it**, so `self.id` already names the instance
    being terminated and the requirement only made the author restate what the
    language supplies. It made the argumentless form — expected to be the COMMON
    one — a hard Error. Removed, NOT relaxed to "if present it must be an `Id`":
    a termination reason is an ordinary thing to pass.
    That requirement was also the SOLE reason the bare `terminate P` form had
    been removed (with it, a no-argument `terminate` could never satisfy the
    arity check, so the spelling always failed validation) — so both came back
    together, and `TerminateStatement.format` now mirrors `Initiate.format`,
    emitting no parens for an empty list. `terminate P()` still parses and
    prettifies to the bare form. The resolved-identity lesson the deleted check
    carried is NOT lost: it lives on in `isAddressFieldFor`.
  - **Addressing is STRUCTURAL: the address is the message's field typed
    `Id(target)`**, found without annotation; `by <field>` only DISAMBIGUATES
    when more than one field qualifies. Candidates match by **resolved
    identity** (`eq` through the refMap), never by the path's last segment —
    two entities named `Order` in different contexts must not collide, and the
    name-matching version turned a legal model into a false ambiguity Error.
    The field's `UniqueId` must be looked up with its OWNING `Type` as the
    refMap key's parent (`Pass` pushes a `Type` — a `Branch` — for its own
    children), which is why `fieldsWithOwner` carries the owner along.
    Zero candidates is a **CompletenessWarning and only for an Entity target**:
    an entity is the only multiply-instantiated processor, and the corpus holds
    7,556 `tell`s against **7** `Id(...)`-typed fields, so an Error would have
    condemned essentially every model that exists. Ambiguity IS an Error —
    it is a contradiction, not an omission.
    **The candidate test follows ALIAS CHAINS but never NESTING** (Reid,
    2026-08-14). A field typed `OrderId`, where `type OrderId is Id(Order)`, IS
    an address — that alias is riddl-models' documented house style, and until
    `ccd278c00` `isAddressFieldFor` matched `UniqueId` alone, so it recognised
    only the rare inline spelling and misfired on the common one (72 of 86
    distinct findings in reactive-bbq were false; it aborted their `checkAll`).
    But `result R is { thing: ThingBase }`, where the NESTED record carries the
    id, stays flagged: descending into an aggregate's fields is an unbounded
    search — a record holding a record holding a record — with no principled
    stopping point, so **the id must be a field of the record actually named.**
    Renaming is followed; containment is not.
    **Both alias walks carry a visited list, and the reason is a real crash:**
    `type A is B` / `type B is A` sent `fieldsWithOwner` into infinite recursion
    in rc.14 (`java.lang.StackOverflowError`, reproduced against the released
    binary), surfacing as `[severe] Exception Thrown` with no line number.
    Reference identity (`eq`), NOT a `Set`/`contains` guard — `Definition`
    overrides `equals` structurally, so a set would fuse two distinct identical
    alias declarations and truncate a legitimate chain.
    **Fixing the alias case cost the corpus 49 Errors it had been hiding**, in
    16 of 189 models — the fourth reminder that a green corpus is evidence about
    the corpus. All 49 were corpus defects in three classes: genuine two-id
    ambiguity needing `by`, actor fields legitimately of the same entity
    (`identityId` + `suspendedBy`) also needing `by`, and **wrong-entity
    aliases** (`type TaskId is Id(NurseShift)`, `type MemberId is
    Id(Enrollment)`) that no `tell` had ever exposed.
  - **`initiate`/`terminate` are effects** — banned in a function body (pure,
    A26) and in `on activate`/`on passivate` (must be side-effect-free), and in
    a correlation fold (purity is what makes re-runs safe, A70/§6.5). The fold
    ban lives in exactly ONE place (`validateCorrelation`), not duplicated into
    `checkInstanceEffectScope`, so a fold offender is never double-reported.
    Every ban is wired into `checkStatementScopes`, **not** `validateStatement`
    — the latter never sees statements held in a FIELD
    (`when`/`match`/`foreach`), the trap two tasks of this plan fell into.
  - **BAST**: `FORMAT_REVISION` **15**; value tags 8 = `Initiate`,
    9 = `SelfValue`; statement sub-kind 20 = `terminate`.

- **A new `Branch` node breaks three things silently** — all found building A70,
  none caught by the compiler:
  1. **`Containment.of`** (`AST.scala`) is an exhaustive match over `Branch`
     with no fallback arm → runtime `MatchError`, not a compile error.
  2. **`Pass.traverse`'s generic `case branch: Branch[?]` walks `contents`
     ONLY.** Statements held in a FIELD (as `Correlation.timeoutStatements` and
     `SagaStep.do/undoStatements` are) need their own case BEFORE that arm, or
     they are never resolved and never validated — the model validates clean
     while naming definitions that need not exist. `HierarchyPass` deliberately
     does NOT do this: its visitors emit field-held statements themselves, in
     the position the syntax requires.
  3. **`VisitingPass.openContainer`/`closeContainer` end in `case _: Definition
     => ()`**, so a new node falls through in silence.
  Also remember `PrettifyVisitor.keyword`, whose fallback is the string
  `"unknown"`.

- **Typed holes (A20, release/2)** — `prompt("...") as <type>` ascribes a
  type to an AI-computed value: the type is known and checkable at compile
  time, the computation is prose an AI fills in at generation time. It is the
  seam between RIDDL's deterministic tier and its AI tier. `PromptValue`
  (already the node `prompt("...")` produced) gains `typeEx:
  Option[TypeExpression]`; unascribed `prompt(...)` is unchanged and still
  valid. Legal in every position an ordinary `Value` can occupy — `let`,
  `constant`, a constructor argument, `set`, and a `when` condition (which
  must resolve to `Boolean`) — with either a predefined type or a declared
  alias.
  **The ascription's type reference RESOLVES, like any other TypeExpression**
  (2026-08-15 whole-branch review) — `ResolutionPass.resolveValue`'s
  `PromptValue` arm used to say "no references" and do nothing, so `prompt(
  "x") as Nonexistent` validated clean while naming a type that need not
  exist. It now calls the same `resolveTypeExpression` every other
  TypeExpression position uses, which recurses `Cardinality` wrappers for
  free and records the resolved Type in `usedBy`, so a Type named ONLY by an
  ascription is not wrongly flagged unused.
  **The ascription RESTATES the position's already-known type; it never
  OVERRIDES it.** `let x: Real = prompt("...") as String` is a validation
  Error (contradiction), not a coercion — checked by the same
  `checkValueType` a `set` already used. **The comparison is deliberately
  SYNTACTIC, not resolved-type**, mirroring A57: `constant G: Real =
  prompt("...") as Score` (`type Score is Real`) is still an Error even
  though the alias's underlying type is `Real`, because RIDDL treats a
  declared alias as a distinct name, not a transparent synonym — a resolved
  comparison would swallow exactly the contradiction this rule exists to
  catch. `typeAscriptionName` (`ValidationPass`) does the comparison; it
  RECURSES through the four `Cardinality` wrappers (discarding them rather
  than folding them into the name) and compares only the LAST path segment
  on both sides — both fixed 2026-08-15 after review found false positives
  on `let x: OrderId = prompt(…) as OrderId?` and on a qualified restatement
  (`let x: Common.OrderId = prompt(…) as Common.OrderId`), and a false
  negative where two differently-aliased `Optional`s compared equal by
  `kind` alone. Comparing only the last segment is a KNOWN, accepted
  limitation shared with `checkOnOtherBinding`: two differently-scoped types
  sharing a simple name compare equal here, because the check stays
  syntactic rather than resolving through the symbol table.
  **A `constant` with a `prompt` value needs no ascription at all, because
  the constant's own type declaration already supplies it** — `constant G:
  Real = prompt("...")` is the complete, idiomatic form; adding `as Real` is
  legal but redundant. Where nothing else states a type (a bare `let x =
  prompt(...)`, a bare constructor argument, a `when` condition with no
  other source of truth) the ascription is the ONLY source of the type —
  there it is doing real work, but it is still describing what is already
  true about the hole, never coercing it. The seam warning for an
  UNASCRIBED hole is deliberately CONSERVATIVE: it fires only at call sites
  that already carry an expected type to compare against (`let`, `constant`
  via `checkValueType`), not at constructor arguments, since nothing wires
  an expected type there today. **Nor at `put`, `return`, `require … with`,
  or a call/constructor argument** — filed to BACKLOG § 1 as a decision to
  revisit, not a ruling; those positions can legally carry an ascribed
  `prompt(...)` and nothing checks it today.
  **`PromptValue.format`'s `ascriptionFormat` and `RiddlFileEmitter` were
  the SAME "dispatch written twice" risk documented under Total Dispatch
  below, and 2026-08-15's review fix makes `RiddlFileEmitter.emitValue` the
  ONE emitter-level dispatch** — it routes a `PromptValue` ascription
  through `emitTypeExpression`, the total dispatch every other
  TypeExpression position already uses, for the four positions
  `checkPromptAscription` validates (`constant`/`let`/`set`/`when`).
  `ascriptionFormat` remains, narrower, for contexts the emitter cannot
  reach — `.format`-based error messages, and a `PromptValue` nested inside
  a `Constructor`/`Call`/`Initiate`/`TerminateStatement` argument (also
  filed to BACKLOG § 1). Before the fix, `ascriptionFormat`'s `case other
  => other.format` fallback mis-rendered several TypeExpression shapes as
  unparseable source: an enumeration, a table, an entity reference, and a
  parameterized predefined type all round-tripped to text riddlc rejects.
  **Historical correction (2026-08-15)**: an earlier version of this entry
  claimed the spurious `type` keyword bug (`as OrderId` rendering as `as
  type OrderId`) meant the string "does not mean the same thing on
  re-parse" — false. `aliasedTypeExpression` defaults an omitted keyword to
  `"type"` too, so both spellings parse to an AST-IDENTICAL node; the
  defect was cosmetic (an un-authored keyword in emitted source), never
  semantic. `ascriptionFormat` still strips it and RECURSES through
  `Optional`/`ZeroOrMore`/`OneOrMore`/`SpecificRange` wrappers rather than
  falling back to `.format`, or the same cosmetic bug resurfaces one level
  down (`as OrderId?` → `as type OrderId?`). **`Currency` cannot appear bare
  in an example** — it is a predefined type requiring a `country` argument
  (`Currency(USD)`), so `prompt("...") as Currency` does not compile, and it
  does NOT resolve to `Real` or anything else underneath — it is its own
  distinct `PredefinedType`. Use `Real`, `String`, `Boolean`, `Score`, or a
  declared alias in examples instead.
  **BAST/JSON**: rides `FORMAT_REVISION` 18 (the bump numeric literals
  already spent), not a new bump — see the FORMAT_REVISION note in
  BACKLOG § 2 for who claims 18 next.
- **On-clause message binding (A55, release/2)** — `on foo: command
  Foo { … }` optionally binds a local name to the handled message.
  The `:` is ordinary TYPE ASCRIPTION (same rule as `let x: T = …`
  and `p1: String`), so the parser reuses `HandlerParser.maybeName`.
  `binding: Option[Identifier]` sits on `OnMessageLikeClause` and
  BOTH concrete nodes, declared immediately after `from` and
  **without a default** — `@JSExportTopLevel` requires defaulted
  params to be TRAILING and `contents`/`metadata` are defaulted.
  `id`/`format` stay derived from `msg`. Bare `foo` denotes the whole
  message; `foo.field` is an ordinary path walk. See "Validation
  Specifics" for how it resolves.
- **Entity intentions (2.0.0-rc.10)** — six keywords written BEFORE `entity`, in
  three INDEPENDENT groups, mutually exclusive within a group: role
  (`aggregate`), consistency (`consistent` | `available`), persistence
  (`event-sourced` | `persistent` | `transient`). `Entity.intentions:
  Seq[EntityIntention]`; enum + companion at `AST.scala:4144`.
  **They are grammar, not options, on purpose.** They were `with { option
  event-sourced }` until 2.0, but the Computational Model §4.2 calls options
  advisory ("honored if possible"), and a hard Error keyed off advisory metadata
  is a category error — see `checkEventSourcing`. The old `option` spellings
  still parse, deprecated. `persistent` replaces the uninformative `value`.
  Two from one group is an **Error, not a parse failure**, so the message can
  name both. `event-sourced` sits in the persistence group because it IMPLIES
  persistent. Any order parses; the parser stores them via
  `EntityIntention.canonical` because **`Definition.equals` compares this
  field** — write order must never make two identical entities compare unequal.
  Prettify emits `canonicalOrder`.
  **Four event-sourcing rules are Errors** (`ValidationPass.scala:1865`), because
  replay must reproduce the same state changes: R1 every handled command declares
  `yields`; R2 every yielded event has an `on event` clause; R3/R4 no `set`/
  `morph` outside handling one of the entity's OWN events. R1/R2 read the
  `yields` DECLARATION on the command's type, never `yield` statements in a body.
  Two traps when migrating a model: `yields` exists ONLY on the kind-first form
  (`command X yields event Y is {…}`), so type-first commands must be reshaped;
  and R3 forbids `set` in `on init` while an empty body is a parse error, so the
  idiom is `on init is { yield event Created }` plus an `on event Created` clause
  that does the mutation.
- **Unified processor model (2026-07-26, release/2)** — every
  `Processor` (Context/Entity/Projector/Repository/Adaptor + the
  generic `processor` keyword) is port-bearing: `Inlet`/`Outlet` are in
  `OccursInProcessor`, and `WithInlets`/`WithOutlets` are mixed into the
  `Processor` base. Each carries `ascribedShape: Option[StreamletShape]`
  (None ⇒ derived from arity via `Processor.arityShape`/`effectiveShape`).
  Surface: `[<intention>] context <id> [as <shape>] is {…}` and
  `processor <id> [as <shape>] is {…}`. The old streamlet shape keywords
  are deprecated aliases; `StreamletShape.fromKeyword` canonicalizes
  synonyms (cascade→Flow, fanin→Merge, broadcast/fanout→Split). `Context`
  has `intention: Option[Intention]` (Application/External/Gateway/
  Service). Shape/intention now participate in `Definition.equals`, so
  keep their `loc` at `At.empty` on every surface (parser/BAST/JSON).
- **Numeric literals (2026-08-15, `release/2`)** — `NumericLiteral(loc, text)`
  in the `Value` and `Comparand` unions, accepting
  `[+-]? digits [. digits] [(e|E) [+-] digits]`. No digit separators, no radix
  prefixes.
  **The text is stored AS WRITTEN and that is the whole design.** `1.50`, `007`,
  `+3` and `2E+8` are not recoverable from a parsed `Long`/`BigDecimal`, so a
  parsed payload would make prettify diverge from source on first use. Same
  reasoning as `UniqueId.kindKeyword` and correlation keys. It also keeps
  `BigDecimal` off the Native and JS paths, and needs one BAST tag (value **10**,
  comparand **3**) rather than two. **JSON stores it as a `ujson.Str`, never a
  `ujson.Num`** — `ujson.Num` is a Double and would silently turn `1.50` into
  `1.5`. A JSON-identity fixed-point test cannot catch that, because a
  consistently-mangled value is still a perfect fixed point; assert the text.
  **`count > 5` now parses, REVERSING A28's deliberate narrowing.** `Comparand`
  was ref-only on purpose, "so magic-constant comparisons cannot be constructed
  at all" — Reid reversed it 2026-08-14 on the evidence that the whole 189-model
  corpus contained exactly ONE constant, so the rule had no uptake to protect
  (plausibly because naming a number meant quoting it). The intent survives as a
  StyleWarning whose population started at zero. `count > true` is still a parse
  error: booleans are atoms, not comparands.
  **`Integer` is signed, `Whole` is `>= 0`, `Natural` is `>= 1`** (Reid,
  2026-08-14). Until then the three had NO definition anywhere — no scaladoc, no
  language reference, no Computational Model entry — so the check had nothing to
  enforce. They are documented at `AST.scala:2518-2530`; a check cannot enforce a
  rule the language never states.
  **Literals are held STRICTER than references, deliberately.**
  `NumericType.isAssignmentCompatible` (`:1912`) lets ANY numeric accept any
  other and STAYS that way — `let x: Nat = someRealField` is unchanged. Only a
  literal, whose value the compiler can see, is range-checked
  (`checkNumericLiteralConformance`). `NumericLiteralConformanceTest` pins the
  loose side so a later "tidy-up" of `isAssignmentCompatible` reddens instead of
  silently changing behaviour far beyond literals.
  **`Bool extends IntegerTypeExpression extends NumericType`**, so any check
  matching `IntegerTypeExpression` also catches Boolean-typed values — put an
  explicit `Bool` arm first, or a Boolean constant is told it "requires a whole
  number".
  **Never call `asLong` in a match guard.** It is `text.toLong` and the parser
  accepts unbounded digit runs, so a 20-digit literal throws
  `NumberFormatException` *inside the guard* and surfaces as `[severe] Exception
  Thrown` with no line number. Use `asBigDecimal` or test the text.

- **`Constant` holds four kinds, and prettify emits `:`** (2026-08-15).
  `ConstantValue = LiteralString | NumericLiteral | BooleanLiteral | PromptValue`
  — a narrowing of `Value`, defined the way `Comparand` is. Deliberately NOT the
  full union, which would admit `Call`, `Ask` and `Initiate` in a constant. The
  `PromptValue` arm is a **typed hole**: the constant declares the type and the
  computation is prose, so it needs no `as T` — see the full A20 typed-holes
  entry above (AST / Language Internals) for the ascribed form and its
  restate-never-override rule, built on this precedent.
  **There was never any parser work for the separator.** `CommonParser.is` (`:38`)
  is `StringIn("is","are",":","=").?` and has always accepted the colon, and
  omission. All spellings are legal, none warns, and prettify emits `: `.
  **The quoted numeric/boolean form is CONSUMED by the parser**, not merely
  deprecated — that is what makes its `autoFixable = true` honest and the round
  trip converge, exactly as `ConnectorOptionToIntention` does. A deprecation
  claiming `autoFixable` while prettify re-emits the old spelling is a lie a
  migration tool will act on.

- **A20 typed holes — `prompt("…") as T`** (2026-08-15). `PromptValue` gains
  `typeEx: Option[TypeExpression] = None`; one node, not two, because the forms
  differ by an `Option` and not by wire shape. The default is legal ONLY because
  it is trailing (`@JSExportTopLevel` forbids a non-trailing default, which is
  why A55/A57's fields had to go undefaulted — `PromptValue` has no
  `contents`/`metadata` after it).
  **The ascription RESTATES the position's type and NEVER overrides it**, per
  A57. Agreement is silent — writing the type out lets the hole read standalone —
  and a contradiction is an Error.
  **The comparison is SYNTACTIC on purpose, not by resolved type.**
  `constant G: Real = prompt("g") as Currency` must Error even though `type
  Currency is Real` resolves to the same underlying type; a resolved comparison
  would swallow exactly the contradiction the rule exists to catch. Mirrors
  `checkOnOtherBinding`.
  **The untyped-seam warning is deliberately CONSERVATIVE** (Reid, 2026-08-15):
  it fires on an unascribed `let x = prompt("…")` with no declared type, and
  **nowhere else**. `when` is wired to `Boolean`; constructor arguments, `set`
  and every unwired position stay SILENT. The evidence was a count — all 288
  `prompt(` uses in riddl-models already carry a type (273 authors wrote the
  ascription unprompted; the other 15 are `when` conditions) — so the warning's
  whole value is for future code and its whole risk is firing on correct code.
  **"We did not wire this position" is not the same fact as "the language cannot
  type this position", and only the second deserves a diagnostic.**
  **`Currency` is a predefined type requiring a `country` argument**, so it
  cannot be written bare. Several early A20 examples used `as Currency` and do
  not compile.

- **`PromptValue.ascriptionFormat` is a SECOND, narrower copy of `emitTypeExpression`
  — CLOSED 2026-08-15, prettify never reaches it for a `Value` anymore.** Until this
  fix, only the four validated positions (`constant`, `let`, `set`, `when`) routed
  through `RiddlFileEmitter.emitValue`, and `emitValue`'s fallback for every OTHER
  `Value` shape was `add(other.format)` — so a `PromptValue` nested one level
  deeper (a `Constructor`/`Call`/`Initiate` argument, an `InvariantCondition`'s
  `with` argument, a `LogicalExpression`/`NotExpression` operand) fell straight
  back into `.format` and reached `ascriptionFormat`'s narrower dispatch, which
  could emit non-parsing output (`as any of {…}`, `as Currency(USD)`,
  `as table of T of [3,3]`, `as reference to entity E`).
  **`emitValue` is now TOTAL over every `Value` shape that can contain a nested
  `PromptValue`**: `Constructor`/`Call`/`Initiate` route their arguments through
  new `emitConstructorArg(s)` helpers (which recurse through `emitValue`, so a
  named `id = value` argument's value gets the same treatment); `InvariantCondition`
  routes its `with` argument; `LogicalExpression`/`NotExpression` route their
  operands through a new `emitLogicalOperand` helper that preserves the same
  parenthesizing rule as `LogicalExpression.format`'s private `paren` helper
  (kept in step by hand, since that helper is private to `AST.scala` and this
  emitter cannot call it). Every `emitStatement` site whose operand can reach a
  `PromptValue` — `send`/`tell`/`yield`/`reply`/`morph … with` (via a
  `Constructor`/`RecordRef` operand, through a new `emitConstructorOperand`
  helper), `put`, `return` (previously unhandled at all — both fell to the
  generic `case statement: Statement => addLine(statement.format)` arm and are
  now explicit cases), `require … with`, a `when` condition's `BooleanExpression`
  arm, and a `match`/`case` guard — now routes through `emitValue` too.
  `PrettifyVisitor.doInvariant`'s condition rendering (`invariant X is <condition>`)
  had the same defect and is fixed the same way, EXCEPT for the `InvariantBlock`
  form (`invariant X is { <stmts> <predicate> }`), which still renders via
  `.format` — that is the separate, pre-existing "two dispatches"
  (`Statement.format` vs. `emitStatement`) gap documented under Total Dispatch
  above, not this one, and this fix does not reach it.
  `ascriptionFormat` remains in `AST.scala`, unchanged, for the one place this
  emitter genuinely cannot reach: `.format`-based error-message rendering. It is
  no longer reachable from prettify output.
  Proven by `TypedHoleContainerAscriptionRoundTripTest` (`passes/.../prettify/`):
  a named `Constructor` argument (`any of {…}`), a named `Call` argument
  (`Currency(USD)`), a nested `LogicalExpression` with the parenthesizing
  intact (`reference to entity E`), and a `not` (`table of T of […]`) — all
  four previously mis-emitted, all four verified to fail before the fix via
  `git stash`. `AST.scala` is in `language` and `RiddlFileEmitter` in `passes`,
  so the copy still cannot call the original — the two must be kept in step by
  hand, which is precisely why this pattern keeps recurring here.
  **What is NOT fixed by this**: `checkPromptAscription` (validation) is still
  wired at only the same four positions, so an ascription that CONTRADICTS its
  position's actual expected type is silently accepted at `put`, `return`,
  `require … with`, and a `Call`/`Constructor`/`Initiate`/`TerminateStatement`
  argument. That is a different defect (a missing check, not broken output) at
  an overlapping set of positions — see BACKLOG § 1.

- **AST.Set shadows scala.Set** — use selective imports or
  qualify as `scala.collection.immutable.Set`.
- **Schema match ordering** — Schema extends `Leaf` (Definition)
  but is also in the `NonDefinitionValues` union. Its case must
  appear BEFORE `case _: NonDefinitionValues`. Same trap for
  `Relationship` vs `case _: Definition`.
- **State is a Branch**, not a Leaf, of `Branch[StateContents]`
  where `StateContents = Handler | Comment`. `PassVisitor` uses
  `openState` / `closeState` (not `doState`). ResolutionPass
  prepends State to parents (as with all Branches), so refMap
  keys for State's type ref use State as parent, not Entity.
- **`do "..."` is an alias for `prompt "..."`** — both produce
  `PromptStatement`.
- **`not` and `!` are SYNONYMOUS everywhere, as the inverse of a
  boolean expression** (ruling 2026-08-14). `!` is legal in every
  position `not` is. `not` is prefix and recurses (`not not a`), and
  both work wherever a boolean expression does.
  **This OVERRIDES the 2026-08-13 ruling**, which said `not` was the
  only general-purpose negation, that `!` was a legacy spelling
  accepted ONLY as `when !<bare-identifier>`, and that it "will not be
  extended to" anything more. Do not restore that reasoning — it
  argued `!` buys no expressiveness and costs four surfaces, and the
  author has ruled the other way.
  **The branch does NOT comply yet, and the work is in BACKLOG § 1.**
  Today `!` is a special case of `when_condition` alone, taking a bare
  IDENTIFIER rather than an expression, so `!(a and b)`, `require !x`
  and `let y = !x` are parse errors; and the two spellings build
  different ASTs — `not` a real negation node, `!` a `negated:
  Boolean` on `WhenStatement` — so unifying them moves prettify, BAST
  and JSON and needs a `FORMAT_REVISION` bump. Watch `!=`: a `!`
  prefix rule ahead of `comparison` swallows its `!` unless guarded,
  and regex lookahead is unavailable on Scala Native.
- **walkStatements helper** — private in ValidationPass; walks
  into `WhenStatement` / `MatchStatement` nesting.
- **Accessors see through the provenance wrappers; `Finder` sees
  through everything.** The 35 `contents` accessors (`context.entities`,
  `domain.contexts`, `handler.clauses`, …) use
  `Contents.filterThroughWrappers`, which descends **`Include` AND
  `BASTImport`** — the same two `flatten()` removes. HOW a definition
  reached a container is riddl's bookkeeping; a client asking what is in
  a context wants the whole list and has no stake in whether a member
  was written inline, included, or imported. Three rules follow:
  1. **`Contents.filter` stays literal** ("my direct children"), and
     `includes` must keep using it, since the wrapper is matched BEFORE
     the type test. `vitals`/`processors` also stay literal — their
     callers (DiagramsPass, StatsPass) already reach included
     definitions another way and would double count. Reasons are
     recorded at each in `Contents.scala`.
     **`definitions` was the third of those and is transparent as of
     2026-08-06** (synapify's task), with `directDefinitions` added as
     the literal form. That change disproved the rule the old comment
     stated — "make it transparent AND delete the caller's manual
     walk". ResolutionPass's walk descends `Include` and deliberately
     NOT `BASTImport`, and `filterThroughWrappers` cannot express
     "includes but not imports", so **ResolutionPass keeps its walk and
     reads `directDefinitions`** (7 sites). Making it transparent would
     have made imports resolve, breaking rule 2 below.
     Three validation checks read `definitions` and moved with it:
     `checkContents` and `checkIncludeHygiene` stopped emitting two
     FALSE warnings (a container whose content all arrived by include
     was told it "should have content"), and `checkUniqueContent`
     STARTED reporting duplicate sibling names across an include
     boundary — a real ambiguity, approved as a deliberate tightening
     (Reid, 2026-08-06). It cost the corpus nothing: 189/189 riddl-models
     validate with zero errors. Pinned by
     `IncludeTransparentValidationTest`.
  2. **READING and RESOLVING answer differently for imports, on
     purpose.** `domain.types` reports a `.bast`-imported type, but a
     reference to it does NOT resolve until an explicit `flatten` — the
     symbol table is built by traversal, not by these accessors, and
     S61-2's contract that loading only fills wrappers is unchanged.
     Structure is likewise untouched: `contents.filter` still shows
     nothing spliced in, and `BASTLoader.getImports` still finds the
     wrapper. Pinned in `BASTImportLoadingTest` and
     `IncludeAndImportTest`.
  3. **`Finder.recursiveFindByType` and the accessors answer DIFFERENT
     QUESTIONS** — it walks EVERY `Container`, the accessor walks only
     the provenance wrappers. Where they diverge: under a **Domain**
     (domains DO nest, `domain_content`, ebnf-grammar.ebnf:77), and for
     `Type` under a Context, since a recursive find also picks up types
     declared inside entities — riddl-generator relies on exactly that
     to emit state records. Where they do NOT diverge: `Entity` under a
     `Context`, because contexts cannot nest (`context_definition` :85
     omits `context`, `entity_content` :96 omits `entity`, and
     `processor_definition_contents` has no `entity`). Pick by the
     question, not by reflex — an earlier version of this note warned
     that recursive find "returns nested contexts' entities", which the
     grammar forbids; riddl-generator caught it.
  Before 2026-08-03, `context.entities` was empty whenever the entity
  lived in an include — silently. That is how riddl-generator produced
  582 files for reactive-bbq with no entity class among them while the
  model validated clean. It survived because riddl validates by
  TRAVERSING and every internal test took that path; the consumer path
  had no gate at all. `ConsumerReadsIncludedDefinitionsTest` is now that
  gate — **add to it whenever you add an accessor.**
- **Definition hashCode/equals override** — `Definition` trait
  overrides both: `hashCode` cheap (id + loc + class); `equals`
  structural via `productEquals`, skipping `Contents` fields.
  Prevents O(subtree) hashing in any `HashMap[Definition, X]`.
  Opaque type `Contents[?]` erases to `ArrayBuffer` at runtime,
  so `case (_: Contents[?], …)` matches correctly.

### Total Dispatch — no silent fall-through

**Reid's standing rule (2026-08-09): "There must be no non-sealed matches — it
is okay to fall through to generate an error or exception but not okay to not
select anything and then carry on as if nothing happened."**

A `case _ => ()` on a SEALED hierarchy is the failure mode: it compiles, and
when a new node type is added the code quietly does nothing for it. Every
symptom then appears far from the cause — an empty output, a dropped statement,
a model that validates clean and means something else.

- **Enumerate the cases — and do it by READING, because nothing checks it for
  you. `-Werror` is NOT a safety net here.** This file said it was until
  2026-08-13; the claim is false as this repo is configured, and believing it
  is how the processor-instance-identity branch shipped seven missed dispatch
  or dispatch-input sites (five across its tasks 2/4/5, two more found by task
  7's review) — every one caught by a human reading code or a code review,
  **none** by the compiler. Two independent reasons, and the second is the
  important one:
  1. `language` and `commands` compile with `--no-warnings` alongside
     `-Werror` (`build.sbt:229`, `:417`), so in those two modules there is no
     warning left for `-Werror` to escalate. (An earlier note here named
     `passes` and `riddlLib` as well — wrong; check `build.sbt` before
     repeating it. `-Werror` really is live in those two.)
  2. Where `-Werror` IS live it still cannot help, because **a wildcard arm
     makes a match exhaustive** — so the terminal `throw` this section
     prescribes is itself what silences the compiler. Follow the rule and you
     are guaranteed never to be told the hierarchy grew. Most of the seven
     were in `passes`, where warnings are on.
  The real net is that `throw`, and it fires at RUN time on the first test that
  exercises the missing arm — so it protects you exactly as far as your tests
  reach, and not one node further. When you add a node type, grep the
  dispatches and read them; do not wait to be told.
- **When a branch genuinely cannot be reached, `throw`** rather than return
  unit. `Pass.processValue` does this; so do `BASTWriter`/`BASTReader`, which
  previously used a `println`-and-drop and a placeholder `PromptStatement`
  respectively — both of which produced corrupt output instead of a failure.
- **`case _ => ()` remains correct for "not interested in this node"** — a
  visitor that handles three of forty types. The test is whether the arm means
  *"nothing to do here"* or *"I do not know what this is"*. Only the second is
  the bug.
- **Enumerate the domain of the FUNCTION, not of the nearest-looking type.**
  `stateReadsIn`/`asksIn`/`countValueFailPoints` walk what `statementValues`
  yields, which is WIDER than `Value`: `WhenStatement.condition` alone is
  `LiteralString | Identifier | ValueRef | BooleanExpression | PromptValue`,
  and `Identifier` appears in no other member. Auditing `Value` exhaustively
  therefore still misses it — which is exactly how `when !isValid`, a form
  that validated on rc.11, threw on rc.13 (fixed 2026-08-13). The throw did
  its job; the enumeration was against the wrong hierarchy.
- **A total walk is still defeated if its INPUT drops a field.** Auditing the
  match arms proves nothing about the fields each arm forgot to RETURN.
  `statementValues` was total over the statement kinds and nonetheless never
  yielded `RequireStatement.argument` (the `with <expr>` operand) or
  `MatchCase.guard` — both full `Value`s — so an `initiate` parked in
  `require X with initiate entity Order` was invisible to every walk built on
  it at once: state-reads, asks, the A12 fail-point census, and the
  instance-effect ban that was itself written correctly (found 2026-08-13 by
  task 7's review of the instance-identity plan). Check the arms AND their
  payloads.
- **A dispatch written TWICE hides the incomplete copy behind the complete one.**
  `AST.WhenStatement.format` had four arms over a five-member `condition` union
  (no `PromptValue`), so `when prompt("…")` threw a `MatchError` — and it
  survived because `PrettifyVisitor` does NOT route through it:
  `RiddlFileEmitter.emitStatement` keeps its OWN copy of that same dispatch, and
  that copy has the arm. So the reflectivity round trip, which is what normally
  proves a `format` total, could never reach the hole; prettifying the construct
  produced correct output on the released binary. Fixed 2026-08-14 (Task 5 of the
  message-value plan made it reachable by rendering a clause body).
  **When you find two implementations of one dispatch, the tested one tells you
  nothing about the other — read both.** `Statement.format` and
  `RiddlFileEmitter.emitStatement` are that pair; keep them in step.
- **Fix the SHAPE of a dispatch/recursion defect, not the instance.** The
  alias-chain cycle guard was added to `fieldsWithOwner` in rc.14 and its sibling
  `aggregateFieldsOf` was left unguarded, so `type A is B` / `type B is A` still
  killed the stack — it was simply latent until a caller reached a cyclic alias
  (2026-08-14). Same lesson the flaky-benchmark round recorded a day earlier:
  when fixing a defect of this class, grep for the shape.

Known-total today: `Pass.processValue`, `classifyHandlers` (all 17 `Statement`
kinds), `countValueFailPoints`, BASTWriter/BASTReader statement dispatch. The
remaining ~140 catch-alls are unaudited — see BACKLOG § 2.

**A new `Value` arm touches EIGHT sites, not five** (counted 2026-08-15 adding
`NumericLiteral`; the plan said five and `-Werror` found three more). Beyond
`ValidationPass`'s four walks (`countValueFailPoints`, `stateReadsIn`,
`initiatesIn`, `asksIn`) and `validateValue`, there are: **`AST.NonDefinitionValues`**
— a parallel union to `Value` that is easy to miss entirely — **`ValidationPass.valueType`**,
and **`JsonifierPass`** in `riddlLib`. Widening **`Comparand`** is a SEPARATE
family of its own: `resolveComparand`, `serializeComparand`, `buildComparand`,
plus the BAST writer/reader pair. Grep and read; do not trust a five-item list.

**A catch-all that "just works" is how a literal disappears.** Before Task 3
added its arm, `JsonAstBuilder.buildComparand`'s pre-existing
`case other => ValueRef(curAt, PathIdentifier.empty)` silently degraded a numeric
comparand into an empty reference — no error, no warning, a valid-looking wrong
answer. That is a live instance of the unaudited catch-alls above, not a
hypothetical.

### Emptiness — `isEmpty` means NO CONTENTS, never "absent"

**Reid has been bitten by this repeatedly while developing RIDDL, and it can make
EVERYTHING fail if implemented wrong. Read this before touching `isEmpty` or
before "fixing" a spurious emptiness warning.**

- **The contract**: `RiddlValue.isEmpty` defaults to **`true`**, documented at
  `AST.scala:98` as *"non-containers are always empty"*. Emptiness asks whether a
  node HAS CONTENTS. It does **not** ask whether the author supplied it, and it
  does **not** mean "all optional fields are None".
- **Overrides belong on CONCRETE case classes** that genuinely have contents, and
  should fold in their parents' `isEmpty` result. Traits with no members of their
  own generally need nothing — auditing every subclass is the wrong sweep.
- **`Statement` deliberately inherits the `true` default.** Statements have no
  bodies, so they are ALWAYS empty, and it never matters: they are leaves that
  traversal never descends into.
- **Among the `Value` kinds, only `LiteralString` overrides it** (`:181`,
  `s.isEmpty`) — the one Value whose emptiness is a real question, because an
  empty string IS the author writing nothing. `Call`, `Ask`, `Constructor`,
  `ValueRef`, `GetValue` and `BooleanLiteral` are non-containers and correctly
  report empty ALWAYS.

**The gotcha this produces.** `checkNonEmptyValue` (`BasicValidation.scala:279`)
asks `value.nonEmpty`, so it is meaningful ONLY for a `LiteralString`. Eight of
its ten call sites in `ValidationPass` honour that — they pass a `LiteralString`
field (`PromptStatement.what`, `ErrorStatement.message`, `CodeStatement.language`,
`LiteralPattern.literal`, `PromptValue.prompt`) or guard with `case ls:
LiteralString =>` and explicitly skip `ValueRef`/`BooleanExpression`. Two sites
passed an arbitrary `Value` unguarded and therefore fired on correct code:
`let`'s expression and `set`'s value, so `let q = call function F(…)` and `set
field S.flag to true` were both reported "must not be empty". Fixed 2026-08-10 by
guarding both on `LiteralString`; pinned by `ValueEmptinessCheckTest`.

**The trap to avoid.** The tempting "fix" is to override `isEmpty` on `Call`/
`Constructor`/`ValueRef`/`BooleanLiteral` so they report non-empty. That
REDEFINES emptiness from *contentless* to *present*, which is a different
question and the one the whole traversal/flatten layer depends on. **When an
emptiness check misfires, the bug is almost always in the CALLER asking the wrong
question, not in the node's `isEmpty`.** Non-literal values get their real
validation — resolution and type-checking — in `checkStatementScopes`.

### Pass Framework & Standard Passes

- **OutlinePass / TreePass** — lightweight `HierarchyPass`
  subclasses in `passes/shared/.../passes/`. OutlinePass →
  flat `Seq[OutlineEntry]`. TreePass → recursive `Seq[TreeNode]`,
  exposed via `RiddlAPI.getOutline()` / `getTree()`. TreePass
  uses a `mutable.Stack[ListBuffer[TreeNode]]` for pure O(n)
  building (not a `HashMap[Definition, ListBuffer]`).
- **Analysis passes** — MessageFlowPass, EntityLifecyclePass,
  DependencyAnalysisPass (1.22.0). All in
  `passes/shared/.../analysis/`; each extends `CollectingPass`
  and requires ResolutionPass. (AIHelperPass was removed in
  1.24.0 — see "Message suggestions" under Validation Specifics.)
- **MessageFlowPass** — `MessageFlowEdge.messageType` is
  `Option[Type]` (adaptor declarations produce `None`; typed
  handler edges produce `Some`). Direction-aware:
  `InboundAdaptor`("from") → producer=referent, consumer=source;
  `OutboundAdaptor`("to") → producer=source, consumer=referent.
  `MessageFlowOutput.edgesForDomain()` / `edgesForContext()` take
  a `SymbolsOutput` parameter for parent-chain walking.
- **UsageResolution** uses `mutable.Set[Definition]` for
  `uses` / `usedBy` (was `Seq`). API boundary methods (`getUsers`,
  `getUses`) return `.toSeq`.
- **ParentStack is a class**, not a type alias. Use
  `ParentStack.empty` (not `mutable.Stack.empty`). Same API
  (push, pop, toParents). It caches `toParents` (toSeq).
- **ValidationMode enum** — `Full` or `Quick`. Quick skips
  `checkStreaming` and `classifyHandlers` in postProcess.
- **IncrementalValidator** — caches messages per-Context using
  FNV-1a fingerprints. `validator.reset()` forces a full recheck.
- **RecognizedOptions registry** — validates option names,
  argument counts, parent types. Unrecognized → StyleWarning.
  **This registry is the ONLY thing validation consults.** The
  `KnownOptions.*` lists in `language/.../KnownOptions.scala`
  (`adaptor`, `context`, `domain`, …) have **no consumers
  anywhere in the codebase** — they are advisory/reference data
  exported to JS via `@JSExportTopLevel`. Adding a name there
  does NOT clear a warning; adding it to
  `RecognizedOptions.registry` does. Keep both in sync anyway,
  since `KnownOptions` is public API.
- **Generator-metadata options** (1.30.0, 1.31.0) — riddl-gen
  and friends drive output from RIDDL metadata, with option
  names prefixed for their target so they are self-describing:
  `protocol` (AsyncAPI), `event_catalog_version` (EventCatalog),
  `sql_dialect` / `sql_table` (SQL DDL), `backstage_owner` /
  `backstage_lifecycle` / `backstage_type` (Backstage catalog),
  `confluence_space` / `confluence_parent` (Confluence). These
  parse fine without registration but draw a spurious "not a
  recognized RIDDL option" StyleWarning until registered.
  **Choosing `validParents`:** use `Seq.empty` when the
  generator resolves the value by walking up the parent chain
  (so it is legitimately settable at any level) — this is the
  common case. Use a specific list (e.g. `Seq("Domain")` for the
  `confluence_*` pair) when the generator reads it from exactly
  one kind of definition, so a misplaced option gets a "not
  typically used on X (expected: Y)" nudge instead of passing
  silently. Registering a new one is ~3 edits: `KnownOption`
  constant, `KnownOptions.*` list membership, registry entry,
  plus a `CompletenessTest` case.
- **RiddlLib analysis API** — `getHandlerCompleteness()`,
  `getMessageFlow()`, `getEntityLifecycles()` on the shared
  RiddlLib trait and JS facade. JS facade returns `""` for the
  untyped (None) MessageFlow edges.
- **Path-identifier usages tracked separately** (1.23.1).
  `ResolutionPass.resolvePathFromAnchor` calls
  `associatePathUsage(parents.head, intermediate)` for each
  anchor + non-terminal component, into the new
  `usesInPath` / `usedInPathBy` maps on `UsageBase`. Existing
  `uses` / `usedBy` semantics are intentionally unchanged so
  `Usages.getUsers` and `AnalysisResult.getUsers` don't shift
  underneath callers. Filtered against `user eq use` and
  `parents.exists(_ eq anchor)` so internal self-references
  don't leak in. Public accessors: `Usages.isUsedInPath(d)` /
  `getPathUsers(d)`.
- **Path-only usage triggers a CompletenessWarning** (Types
  only). When a Type's `usedBy` is empty but `usedInPathBy`
  is non-empty, `UsageResolution.checkUnused` emits "only
  referenced in path identifiers" — the type is addressable
  but can't carry data because nothing declares a field /
  state of that type.

### Validation Specifics

- **The three integer types (`Integer`/`Whole`/`Natural`) have defined ranges,
  and a LITERAL is checked more strictly than a REFERENCE** (numeric-literals
  plan, 2026-08-14/15). Ruled by Reid: `Integer` is signed (any whole
  number), `Whole` is non-negative (`>= 0`, the counting type), `Natural` is
  positive (`>= 1`, the ordinal type, excludes zero). These were undefined
  everywhere — code, grammar, language reference, Computational Model — until
  this work, so nothing could enforce a distinction between them.
  `ValidationPass.checkNumericLiteralConformance` enforces it now, but ONLY
  against a `NumericLiteral` value on a `Constant` — a `ValueRef` is
  untouched, and `NumericType.isAssignmentCompatible` deliberately still lets
  ANY numeric type flow into any other by reference (`let x: Natural =
  someRealField` stays legal). The asymmetry is intentional: a literal's
  value is statically known where a reference's is not, so only the literal
  can be held to the stricter standard. The fractional-value check
  (`IntegerTypeExpression` rejecting a decimal) is reported BEFORE the
  `Natural`/`Whole` range checks — both are integer-type violations, and a
  range message for `1.5` would be true but useless next to "has a
  fractional part". `Bool` is excluded even though it extends
  `IntegerTypeExpression`: a Boolean-typed constant is a different kind of
  thing, not "a whole number with a fractional part."
- **Connector intentions (`persistent`, `at-least-once` | `at-most-once`)** —
  keywords written BEFORE `connector`, two independent groups, mutually exclusive
  within a group (an Error, not a parse failure, so both keywords can be named).
  **Absence of a delivery keyword means `at-least-once`** — Computational Model
  §25.7 already said so, so nothing was invented and an absent keyword draws NO
  warning; `at-most-once` exists to make that section's "knowing downgrade, never
  a silent one" enforceable. `at-least-once` is writable and redundant.
  **ORDERING is deliberately NOT an intention**: §25.7 makes `unordered`
  "permission, not mandate" with a best-effort obligation, which is the
  definition of advisory. The admission test for the enum is whether a generator
  may decline to honour the keyword.
  `option persistent` is deprecated and **CONSUMED** into the intention by the
  parser, which is what makes the round trip converge and migrated 430 corpus
  uses for free. **Ask `Connector.isPersistent`, never `hasOption("persistent")`**
  — it accepts both spellings, and three validation gates go through it.
  Two traps this hit, both documented elsewhere in this file and both worth
  re-reading before touching AST: inserting the enum between
  `@JSExportTopLevel("Connector")` and its case class silently reattached the
  annotation (invisible to `cJVM`), and `StreamingValidation` had an
  `options.find(…).get` that was safe only while persistence could come from
  nowhere else.

- **The stream-shape arity table is TOTAL, and `sink`/`source` take ANY port
  count** (Reid, 2026-08-12). `Processor.shapeForArity` maps every non-negative
  `(outlets, inlets)`:

  | shape | outlets | inlets |
  |---|---|---|
  | `void` | 0 | 0 |
  | `sink` | 0 | **≥1** |
  | `source` | **≥1** | 0 |
  | `flow` | 1 | 1 |
  | `merge` | 1 | ≥2 |
  | `split` | ≥2 | 1 |
  | `router` | ≥2 | ≥2 |

  `sink` and `source` were pinned to exactly one port until 2026-08-12, which
  left `(0, ≥2)` and `(≥2, 0)` unnamed; they fell to a catch-all returning
  `Void`, so `repository R as sink` with two inlets was rejected as "its arity is
  void". **The final arm now THROWS** — it is reachable only for a negative
  count — because returning a plausible shape is how the gap became a confident
  wrong diagnosis that `validateProcessorShape` reported as fact.
  **Two places encode this and both must move together:** the table above, and
  the parser's per-shape `minInlets`/`maxInlets`/`minOutlets`/`maxOutlets` in
  `StreamingParser` (`sink R` and `repository R as sink` must agree about what a
  sink is). Their prior agreement was not corroboration — it was one assumption
  written twice.

- **`external context Foo` is an INTENTION, not `option external` — test both.**
  `Context.intention: Option[Intention]` (Application/External/Gateway/Service)
  is set by the keyword form `external context Foo is {…}`, which is what
  riddl-models uses almost exclusively. `hasOption("external")` is the OTHER
  spelling (`with { option external }`) and does NOT see it. A check that
  exempts external contexts must ask for both:
  `c.intention.contains(Intention.External) || c.hasOption("external")`.
  Testing only the option cost 1120 false warnings across the corpus in one run
  — every event declared in an `external context` block, i.e. exactly the
  systems a model deliberately does not implement, reported as emitted by
  nothing.
  **The correct idiom was already in the codebase** at
  `StreamingValidation.scala:66`, which has always asked for both; it just was
  not copied. Two sites still ask for the option ONLY —
  `ValidationPass.scala:248` (`checkCompletenessPostProcess`) and `:581`
  (`validateOnMessageClause`) — so an `external context` is NOT exempt from
  those two. Filed in BACKLOG; each needs its own corpus A/B, since widening an
  exemption changes which models escape a different check.

- **Statement scope: `set` and `get from state` need something that OWNS state**
  (Reid, 2026-08-12). `set` is legal only in an **Entity** (which owns its
  `State`) or a **Projector** (which owns the read-model record its folds build —
  A70 REQUIRES it). It is an Error in a Context (§3.5: state lives in contained
  entities/repositories/projectors, "never in the Context itself"), a Saga (§9.5:
  a saga's state is housekeeping with "no domain-specific value"), a Repository,
  an Adaptor and the streamlets. A **Function** is deliberately not reported here
  — A26 already rejects `set` at the keyword, and a second message would
  double-report.
  **A Repository is banned despite the corpus appearing to disagree.** 97 `set`s
  across reactive-bbq and two pattern templates were added to silence *"contains
  only prompt statements"* — evidence about that warning, not about what a
  repository does. The warning now **exempts repositories** (most of their
  on-clauses legitimately hold one `do` standing in for SQL) and says **`do`**,
  not `prompt` (`do` is canonical; `prompt` is the deprecated synonym, and
  `prompt(…)` with parens is a VALUE). Do not re-admit `set` in a repository
  without re-reading that ruling — the two halves must move together.
  `get from state` is legal only inside the entity that OWNS the state: outside
  any entity there is nothing to read (and in a saga step this is the rule the
  `ask` ban already states, which reading state directly would otherwise bypass),
  and inside a *different* entity it crosses §4.6's encapsulation rule. That
  second half is why the whole rule lives in **validation, not the parser** — it
  needs the resolved `State` and its owner. **`get from input` is untouched**:
  `GetValue.source` is `InputRef | StateRef`, and inputs are confined to
  application contexts indirectly, because A41 pins UI groups there, so an
  `input` reference outside one has nothing to resolve against. Giving it a
  dedicated message was considered and REJECTED (2026-08-12) — but know the
  tradeoff that accepts: what the author actually sees is the GENERIC
  *"Path 'Screen.NameField' was not resolved"* (verified, not assumed), **not**
  A41's message, which fires on a misplaced group declaration rather than on this.
  It is correct and it is unhelpful. Revisit if it confuses anyone in practice;
  the reason to leave it is that `get from input` outside an application context
  is nearly always a symptom of a missing group, which A41 does report well.
  Hooked in `validateStatement`, which every statement reaches WITH its parents —
  including saga-step statements, whose `parents.head` is the **Saga** (a SagaStep
  is a Leaf and is never pushed; see `Pass.traverse`). Note `checkStatementScopes`
  is NOT that hook: it is wired only to on-clauses and function bodies.

- **`???` is a body that says "known to be incomplete" — validation must EXEMPT
  it** (Reid's ruling, 2026-08-11). Any definition whose body is `???` earns at
  most a **Missing** warning saying the body should be provided. Every other
  check — structural requirements, completeness, wiring, cross-references — is
  skipped for it, because the author has already said *don't expect much*.
  This is why a check must not reason from what a `???` body does NOT contain:
  `repository R is { ??? }` is not missing its handlers, it is unwritten, and a
  rule that fires on it will fire on nearly every stub in the corpus. When
  adding a check, guard it on `nonEmpty` (see the streamlet shape check, which
  already does exactly this) rather than reporting the stub.

- **A parse-time `error()` PREEMPTS validation — the pass chain never runs.**
  So whatever the parser says is the ONLY thing the author sees, and any
  more specific diagnostic ValidationPass would have produced for that input
  is silently lost. Learned 2026-08-08 adding the `yields`/`replies` pairing:
  checking it in the parser looked equivalent to checking it in validation and
  is not — it killed three existing A19 messages ("should be one of these
  message types", "Only command and query types may declare") because those
  inputs stopped reaching the pass that emits them.
  **Rule: put a check in the parser ONLY when validation cannot make it**, and
  the test is whether the evidence survives into the AST. The keyword/use-case
  pairing qualifies: `usecase` is in the AST but which KEYWORD was written is
  not, so by validation time the evidence is gone. Everything else belongs in
  ValidationPass.
  Two corollaries:
  - A parser `error()` is otherwise NON-FATAL and accumulating (see
    `defOfTypeKindType`'s type-alias check), so it looks harmless in isolation.
    The damage is to the passes that never run, not to parsing.
  - Parse-time messages travel a DIFFERENT channel:
    `parseInputWithMessages` → `PassInput.parseMessages` →
    `PassesResult.additionalMessages`. They reach users under every `riddlc`
    command, but `parseAndValidate` in tests DISCARDS them — assert them with
    `TopLevelParser.parseInputWithMessages` (pattern:
    `RecognizedOptionSetTest:98`).
- **`ValueRef` resolves in the RESOLVER (A55), not in validation.**
  `ResolutionPass` queues every `ValueRef` and resolves it in
  `postProcess` (its anchors are reached through other references,
  and the pass visits definitions in source order). Only the ANCHOR
  differs from an ordinary reference: the on-clause `binding`, else
  a field of the handled message / entity state / function
  `requires` input (`valueScopeField`), else the ordinary
  `findAnchor` route. The rest is `resolvePathFromAnchor`'s walk.
  Validation reads `refMap.anyDefinitionOf(path, parents.head)`.
  **Do NOT reintroduce last-component name matching** — that was
  A54's `valueAllowedFields`/`constantOf`, and it let
  `garbage.nonsense.realField` validate.
  - **`let`-locals stay LEXICAL** — a `let` is not a Definition and
    is statement-ORDERED (visible only after its declaration,
    shadowed by inner blocks), which the symbol table cannot model.
    They are threaded by `checkStatementScopes`; a `let`'s type is
    DECLARED (`let x: T = …`) or INFERRED from its expression
    (`letType`). Because the resolver cannot see them, the ValueRef
    walk runs under `ResolutionPass.quietly` (suppresses
    `notResolved`/`wrongType`/`ambiguous`) and **validation owns the
    diagnostic**.
  - **`Reference.id` is a reference's optional LOCAL NAME**, the one
    `from di: context C` sets — NOT the referenced definition's id.
    No `MessageRef` ever carries one, which is why
    `findMatchingCandidate`'s on-clause arm was dead until A55
    changed its guard to `omc.msg.nonEmpty`.
- **Message suggestions / `provideTips` (1.24.0)** — every
  `Messages.Message` carries a `suggestion: String`; any pass
  attaches one at the message-creation site (via the `addX`/
  `check` helpers' trailing `suggestion` param). The single
  chokepoint `Messages.Accumulator.add` STRIPS the suggestion
  unless `CommonOptions.provideTips` is set, and `Message.format`
  appends a `Suggestion:` line only when present — so default
  output is unchanged (no `.check` churn). `riddlc advise` ==
  `validate` with `provideTips=true`; `--provide-tips` /
  HOCON `provide-tips` toggle it. This replaced `AIHelperPass`:
  the pass and its tests are deleted; the `Tip` message kind is
  retained but has no producer; `RiddlLib.analyzeForTips`/
  `analyzeSourceForTips` + the `advise` command are kept,
  re-implemented to run standard passes with `provideTips=true`
  (analyze* are `@deprecated`). Human/AI catalog of every
  message→suggestion pair: `MESSAGE_SUGGESTIONS.md` (repo root).
  Three entity completeness checks promoted from old AIHelper
  tips (no command types, no event types, unhandled command) are
  ADVISORY — gated behind `provideTips` because message types are
  often context-scoped (`summon[PlatformContext].options.provideTips`
  in `validateEntity`). The context-with-entities-but-no-repository
  check is ALWAYS-ON (`c.repositories.isEmpty`), gated only by
  `showCompletenessWarnings`.
- **Streamlet shape check** — guard on `nonEmpty` before
  checking inlet/outlet counts (empty = placeholder).
- **Adaptor cross-context type resolution** — use the
  parent-independent
  `resolution.refMap.definitionOf[Type](pathId)`.
- **Schema parser** — `schemaKind` uses `"time-series"`
  (hyphenated). Consecutive schemas need `with { ... }` blocks.
- **CheckMessagesTest `.check` file format** — lines starting
  with space are continuation lines; non-space lines begin new
  entries. Don't insert mid-continuation.
- **RiddlResult[T]** replaces `Either[Messages, T]` — sealed ADT
  with `Success[T]` / `Failure`; use `result.toEither` for
  backward compat.

### Container / Flatten / FileBuilder

- **Container.flatten()** recursively removes Include / BASTImport
  wrappers in place. Use base `Pass`, not `DepthFirstPass` —
  mutating contents during traversal corrupts ArrayBuffer
  iteration.
- **FileBuilder requires PlatformContext** — `trait FileBuilder
  (using PlatformContext)`. All subclasses must propagate the
  `using` clause.

### PrettifyPass

- **Multi-file mode** — `flatten=false` (default) preserves
  include/import structure; `-s true` collapses to single file.
- **`PrettifyState.toDestination()`** strips leading/trailing
  `/` from `outDir` (URL basis can't start with `/`).
- **Include paths** — `openInclude` uses `url.path` (relative
  filename), not `url.toExternalForm` (absolute URL).
- **`RiddlFileEmitter.trimTrailingNewline()`** — used in
  `closeType` to join `}` with ` with {` on the same line.

### JS / npm / TypeScript

- **parseString returns an opaque Root in JS** — use
  `getDomains(root)` or `inspectRoot(root)` to access data;
  TypeScript type is branded `RootAST`.
- **RiddlLib.ast2bast(root)** returns `RiddlResult[Array[Byte]]`
  on the shared side / `RiddlResult<Int8Array>` in TS.
- **riddlLibJS tests** override `Test / scalaJSLinkerConfig` to
  `CommonJSModule`. Production stays ESModule.
- **ESM shim hazard** — never put `import '`, `import "`, or
  `import(` in shared string literals; ESM shim plugins rewrite
  these patterns. Use string concatenation. `ESMSafetyTest`
  enforces it.
- **npm prerelease publishing** — sbt-dynver versions like
  `1.2.3-1-hash` are prerelease per npm semver; pass `--tag dev`.
- **GitHub Packages npm auth** — `gh auth refresh -s write:packages`
  is required.

### Build / CI / Tooling

- **Three ways a test suite passes without running** (all found in
  #64, which had hidden 38 dead cases — including a completely
  non-parsing `import "f.bast"` — for months). A green suite is NOT
  proof the assertions ran; the check is to drop a `fail("canary")`
  into a case body and confirm the suite goes red.
  1. **TestData lambda on a plain spec.** `AbstractTestingBasis`
     (`utils/src/test/.../AbstractTestingBasis.scala`) is a PLAIN
     `AnyWordSpec with Matchers`, so its `in` takes a by-name
     `=> Any`. Writing `in { (td: TestData) => body }` there merely
     constructs a `Function1` and **never evaluates `body`** —
     deterministic Scala semantics, not sbt elision. That form is
     only meaningful on `AbstractTestingBasisWithTestData` (the
     `FixtureAnyWordSpec` base) and everything derived from it
     (`AbstractParsingTest` → `ParsingTest` → `AbstractValidatingTest`
     → `AbstractRunPassTest`). **Rule: if a case body takes `(td:
     TestData)`, the suite MUST extend a `…WithTestData` base.**
  2. **Abstract spec with no concrete subclass.** The runner never
     instantiates it, so its cases never appear in the log at all —
     zero mentions, not even as skipped. Either make the class
     concrete or declare a subclass in the platform aggregator
     (`JVMTests.scala` / `JSTests.scala`). Beware the silent trap:
     a class stays abstract because an inherited member is
     unimplemented (`PrettifyPassTest` declared `checkAFile(Path,
     File)` against a base wanting `checkAFile(Path, Path)`).
  3. **Constructor parameters on a concrete suite.** ScalaTest cannot
     instantiate `class FooTest(using PlatformContext)`, so it is
     never discovered. Concrete suites take NO parameters; import
     `com.ossuminc.riddl.utils.pc` instead.
- **Unawaited Future in a non-async spec** is a fourth variant of the
  same failure: `inputFuture.map { … assertions … }` followed by
  `Await.result(inputFuture, …)` awaits the WRONG future — the
  assertions run detached and their failures are discarded. Await the
  MAPPED future. (**`BASTWriterSpec` does NOT have this shape** — this note
  said it did until 2026-08-14, wrongly. All five of its cases bind
  `assertionFuture = inputFuture.map { … }` and await THAT
  (`BASTWriterSpec.scala:35`/`70`, `:77`/`123`, `:130`/`170`, `:177`/`225`,
  `:232`/`254`), which is the correct form. The failure mode is still real
  and worth watching for; it just has no instance in the repo today.)
- **`test`/`tJVM` resolve to `testQuick`** — which incrementally
  SKIPS test suites it judges unaffected, even after a source change
  and even with `~/Library/Caches/sbt/v2/ac` cleared (a DIFFERENT cache
  from testQuick's own succeeded-tests tracking). Symptom: "No tests to
  run for language / Test / testQuick" and a false green. For a
  guaranteed full run after edits, use `<module>/testOnly *` (e.g.
  `language/testOnly * ; passes/testOnly *`), which ignores incremental
  state. This is separate from — and additive to — the action-cache
  fixture blindspot.
- **`sbt -batch` runs only the FIRST command argument** — found
  2026-08-03. `sbt -batch 'utils/testOnly *' 'language/testOnly *' …`
  with seven module arguments ran `utils` ONLY, printed
  "Suites: completed 18 / Tests: succeeded 146 / All tests passed",
  and **exited 0**. The other six modules never ran and nothing said
  so. This is the most deceptive member of the false-green family
  because both the exit code and the word "passed" are honest about
  the 14% that executed. Put every command in ONE argument separated
  by `;` — `sbt -batch 'a/testOnly *; b/testOnly *; …'` — and then
  **count the `Suites: completed` lines against the number of modules
  you asked for.** (The `;` chain still aborts at the first failure,
  so a short count means either a red or a skip; either way, look.)
- **`@JSExport*` annotation placement** — an `@JSExportTopLevel(...)`
  binds to the very next definition. Inserting a new
  `enum`/`object`/class between the annotation and its case class
  silently reattaches it (breaks `cJS`, invisible to `cJVM`). Any AST
  edit near an exported type MUST be checked with `cJS` (and `cNative`),
  not `cJVM` alone.
- **Scala.js stale-incremental devirtualization** — when a class gains a
  `WithX` accessor trait (or any mixin changing which field a trait
  method resolves to), the JS linker can keep a *stale devirtualization*
  of that method to the OLD owner's field, producing a runtime
  `TypeError` while `cJS` succeeds. Neither a passing `cJS` nor deleting
  the `*-fastopt` dir clears it — only `<module>JS/clean` does. Symptom:
  JS-only runtime failure that no compile catches. Learned adding
  `WithContexts` etc. to `Module` (#61).
- **Parse-time messages now surface** — `warning()`/`deprecation()`
  emitted during a *successful* parse used to be dropped (`parseRule`
  returned the buffer only on fastparse failure). They now flow via
  `TopLevelParser.parseInputWithMessages` → `PassInput.parseMessages` →
  `PassesResult.additionalMessages`, so deprecations show under every
  `riddlc` command, not just `validate`. New parse-time warnings
  therefore appear in `.check` goldens.
- **release.yml** — triggered by `gh release create`. Builds
  native riddlc (macOS ARM64, Linux x86_64) + JVM universal.
  Sends `repository_dispatch` to homebrew-tap with SHA256s.
  Requires the `HOMEBREW_TAP_SECRET` repo secret.
- **sbt-dynver wants a clean working tree** — `git stash`
  modified files before `sbt publish` on a release tag.
- **External-repo tests** — download at construction time (not
  in `beforeAll`) for ScalaTest `AnyWordSpec`.
- **TatSu pin** — `TatSu>=5.12.0,<5.17.0`. 5.17.0 has a missing
  `rich` dependency that breaks import.
- **EBNF TatSu syntax** — `{rule}+` not `rule+` for positive
  closure; TatSu requires curly braces around the repeated
  element.
- **ScalaDoc + inline + opaque types** — keep `inline` off
  `Contents` extension methods (NPE in
  `ScalaSignatureProvider.methodSignature`). Filed:
  scala/scala3#25306.
- **Scala 3.8.x scaladoc parallel race** — multiple `doc`
  tasks running concurrently under `publish` crash in
  `dotty.tools.scaladoc.renderers.Resources.allResources`.
  Symptom: `(<module>Native / Compile / doc)
  java.lang.reflect.InvocationTargetException` partway
  through `sbt clean test publish`, leaving partial Maven
  artifacts on GitHub Packages. Workaround applied to
  `passesNative` and `riddlLibNative` in `build.sbt`:
  `.nativeSettings(Compile / doc / sources := Seq.empty)`.
  If a future Native module trips the same race, add the same
  one line.
- **`annotateErrorLine` tolerates EOF-boundary `At`** — when a
  parser failure points one past EOF (typical "missing `}`"
  case), the failure's `endOffset` can exceed the line range
  computed by `lineRangeOf`. Downstream slicing in
  `annotateErrorLine` already clamps via `Math.min`, so the
  function does NOT assert on the boundary. Don't reintroduce
  the `require(end >= index.endOffset, …)` check that lived
  there before 1.23.3 — it crashes the error reporter itself
  and surfaces the real parse error as `[severe] Exception
  Thrown` instead of a normal `[error]`.
- **sbt-riddl auto-downloads riddlc** — caches in
  `~/.cache/riddlc/<version>/`; three-tier resolution: explicit
  path > download > PATH. Use `--no-ansi-messages` and strip
  ANSI for version parsing. Pin `riddlcVersion` to a real
  release tag in scripted tests, not the dynver snapshot.
- **sbt plugin visibility** — use `private[plugin] def` (not
  `private def`) so the compiler doesn't warn "private method
  never used" when sbt macros generate the usage. (The sbt-riddl
  plugin is now Scala 3 / sbt 2, but the pattern still holds.)

### Git Workflow

- **PR merge with branch protection** —
  `gh pr merge --admin --merge --delete-branch=false`.
