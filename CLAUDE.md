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
- **Scala 3.8.3** (not Scala 2!) — overrides sbt-ossuminc's
  3.3.7 LTS default. RIDDL originally pinned 3.8.3 to dodge a
  3.3.x compiler infinite loop on opaque + intersection types;
  the override has been kept while we ride ahead of LTS.
- **ALWAYS use Scala 3 syntax**:
  - `while i < end do ... end while` (NOT `while (i < end) { ... }`)
  - No `null` checks — use `Option(x)` instead
  - New control flow syntax with `do`/`then`/`end`

### sbt-ossuminc Plugin

**Current version: 1.4.0** (1.21.0 upgrade).

#### Common Configurations:
```scala
// Scala 3.8.3 (overridden from sbt-ossuminc's 3.3.7 LTS default)
.configure(With.scala3)  // Sets scalaVersion to 3.3.7 LTS
// Then override: scalaVersion := "3.8.3"

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
  `language/shared/src/main/scala/com/ossuminc/riddl/language/bast/`
- **Cross-platform**: JVM, JS, Native
- **Pass**: `passes/shared/.../BASTWriterPass.scala`
- **CLI**: `riddlc bastify <file.riddl>` (write);
  `riddlc unbastify` (read — pending)
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

**Building npm packages** (via sbt-ossuminc 1.4.0 helpers):
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
- Current LTS: **3.3.7**; riddl runs ahead on **3.8.3**
- Next LTS expected: **3.9.x** (Q2 2026)

**Quick Search to Find All References**:
```bash
grep -r "scala-3\." .github/workflows/
```

**Example Fix** (3.8.3 → 3.9.0):
```bash
sed -i 's/scala-3.8.3/scala-3.9.0/g' .github/workflows/*.yml
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
**Cause**: Scala 3.8.3 limitation — default parameter values in a case
class's first parameter list cannot resolve `given` instances from a
subsequent `using` clause in the generated companion `apply` method.
**Fix**: Remove the default value. May be fixed in 3.9.x LTS.
**Example**:
```scala
// This fails in 3.8.3:
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

Co-Authored-By: Claude <model-name> <noreply@anthropic.com>
```

### Branch Strategy
- **main**: Production releases
- **development**: Active development (current work)
- Always push to `development` unless releasing

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
- **walkStatements helper** — private in ValidationPass; walks
  into `WhenStatement` / `MatchStatement` nesting.
- **Definition hashCode/equals override** — `Definition` trait
  overrides both: `hashCode` cheap (id + loc + class); `equals`
  structural via `productEquals`, skipping `Contents` fields.
  Prevents O(subtree) hashing in any `HashMap[Definition, X]`.
  Opaque type `Contents[?]` erases to `ArrayBuffer` at runtime,
  so `case (_: Contents[?], …)` matches correctly.

### Pass Framework & Standard Passes

- **OutlinePass / TreePass** — lightweight `HierarchyPass`
  subclasses in `passes/shared/.../passes/`. OutlinePass →
  flat `Seq[OutlineEntry]`. TreePass → recursive `Seq[TreeNode]`,
  exposed via `RiddlAPI.getOutline()` / `getTree()`. TreePass
  uses a `mutable.Stack[ListBuffer[TreeNode]]` for pure O(n)
  building (not a `HashMap[Definition, ListBuffer]`).
- **Analysis passes** — MessageFlowPass, EntityLifecyclePass,
  DependencyAnalysisPass, AIHelperPass (1.22.0). All in
  `passes/shared/.../analysis/` (or `passes/ai/`); each extends
  `CollectingPass` (or HierarchyPass for AIHelperPass) and
  requires ResolutionPass.
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
- **RiddlLib analysis API** — `getHandlerCompleteness()`,
  `getMessageFlow()`, `getEntityLifecycles()` on the shared
  RiddlLib trait and JS facade. JS facade returns `""` for the
  untyped (None) MessageFlow edges.

### Validation Specifics

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
- **sbt-riddl auto-downloads riddlc** — caches in
  `~/.cache/riddlc/<version>/`; three-tier resolution: explicit
  path > download > PATH. Use `--no-ansi-messages` and strip
  ANSI for version parsing. Pin `riddlcVersion` to a real
  release tag in scripted tests, not the dynver snapshot.
- **sbt plugin visibility** — use `private[plugin] def` (not
  `private def`) so Scala 2.12 doesn't warn "private method
  never used" when sbt macros generate the usage.

### Git Workflow

- **PR merge with branch protection** —
  `gh pr merge --admin --merge --delete-branch=false`.
