# Engineering Notebook: RIDDL

This is the central engineering notebook for the RIDDL project. It tracks current status, work completed, design decisions, and next steps across all modules.

---

## Current Status

**Last Updated**: February 4, 2026

**Scala Version**: 3.7.4 (overrides sbt-ossuminc's 3.3.7 LTS default due to
compiler infinite loop bug with opaque types/intersection types in 3.3.x).
All workflow paths updated to `scala-3.7.4`.

**Release 1.2.3 Published**: Comprehensive fix for all
`System.lineSeparator()` calls in shared code that returned `\0` in
Scala.js. Added `(using PlatformContext)` to `FileBuilder` trait and
propagated through entire hierarchy. All tests pass. Published to
GitHub Packages. Merged to main.

**npm Package Published**: `@ossuminc/riddl-lib` published to GitHub
Packages npm registry via CI and locally. ESModule format with TypeScript
declarations. CI workflow (`npm-publish.yml`) uses sbt-ossuminc 1.3.0
tasks (`npmPublishGithub`/`npmPublishNpmjs`). Consumable via
`npm install @ossuminc/riddl-lib` with
`@ossuminc:registry=https://npm.pkg.github.com` in `.npmrc`.

**Packaging Infrastructure**: Docker, npm, and TypeScript support added:
- `Dockerfile` — Multi-stage build with custom JRE via jlink (~80-100MB image)
- `docker-publish.yml` — CI workflow for Docker image publishing to ghcr.io
- `npm-publish.yml` — CI workflow for npm package publishing
- `riddlLib/js/types/index.d.ts` — TypeScript type definitions
- `package.json.template` — Enhanced with TypeScript and ES module support
- `pack-npm-modules.sh` — Updated with TypeScript definitions integration

**Homebrew Tap Updated**: `ossuminc/homebrew-tap` updated to 1.2.3 with
correct riddlc.zip asset. `brew install ossuminc/tap/riddlc` works.

**Branch Cleanup Complete**: All stale feature/bugfix branches deleted. Only `main`
and `development` branches remain.

**EBNF Validation Complete** (Feb 1, 2026):
- Internal test files: 59/77 passed, 13 include fragments skipped, 5 expected
  failures (all have bugs that fastparse also rejects)
- External test files (riddl-examples): 8/9 passed, 1 expected failure (Trello
  needs AI regeneration)
- Key fixes made: comment regex tokens (avoid whitespace-skipping issues),
  statement rule (added morph/become), pseudo_code_contents rule, interactions
  rule (added comment support)
- CI updated to validate against riddl-examples repository
- Tasks #7-10 created for fastparse fixes to match EBNF (hex escapes, cardinality
  mutual exclusivity, metadata with-block requirement)

The RIDDL project is a mature compiler and toolchain for the Reactive Interface
to Domain Definition Language. BAST serialization is **complete** (60 tests,
6-10x speedup). Hugo and diagrams modules moved to another repository.

**Documentation**: [ossum.tech/riddl](https://ossum.tech/riddl/) - all new docs
go there, not this repo.

---

## Active Work Queue

### 0. Fix riddl-models Validation Errors (HANDED OFF)
**Status**: Handed off — task document written at
`../riddl-models/TASK-fix-validation-errors.md`

45 of 186 riddl-models files fail `riddlc validate`. Error categories:
ambiguous path references (23), `briefly` outside `with {}` (4),
unresolved `EmailAddress` type (7), unresolved `Year` type (5),
decimal fractional part (2), complex multi-error (4).

After all files pass, add riddl-models EBNF validation to CI
(`.github/workflows/scala.yml`, mirroring existing riddl-examples
pattern).

### 1. Comprehensive TypeScript Declarations for AST & Passes
Expand `riddlLib/js/types/index.d.ts` to cover the full AST and Pass
hierarchies so TypeScript consumers can access the rich capabilities
of the RIDDL compiler — not just the RiddlAPI facade.

**Current state**: The existing `index.d.ts` (~390 lines) only
declares types for the `RiddlAPI` object's 12 exported methods and
their return types. The underlying AST node types (Domain, Context,
Entity, Handler, State, Type, etc.), pass outputs, and message types
are represented as opaque `object` or simplified interfaces.

**Goal**: Provide complete TypeScript type declarations for:
- **AST hierarchy** — All `RiddlValue` subtypes: `Domain`, `Context`,
  `Entity`, `Handler`, `State`, `Adaptor`, `Saga`, `Projector`,
  `Repository`, `Streamlet`, `Epic`, `Function`, `Type`, statements,
  expressions, etc.
- **Type expressions** — `PredefinedType`, `AggregateTypeExpression`,
  `EntityReferenceTypeExpression`, `AliasedTypeExpression`, etc.
- **Pass outputs** — `PassesResult`, `SymbolsOutput`,
  `ResolutionOutput`, `ValidationOutput`, message collections
- **Messages** — `KindOfMessage` subtypes (`Error`, `Warning`,
  `Info`, `MissingWarning`, `StyleWarning`, `UsageWarning`)
- **Location** — `At` with `line`, `col`, `offset`, `endOffset`
- **Contents** — Typed container contents for each definition kind

**Approach**: Generate declarations from the Scala AST source files
in `language/shared/src/main/scala/com/ossuminc/riddl/language/AST.scala`
and related files. Consider whether declarations should be
hand-maintained or auto-generated via a build step.

**File**: `riddlLib/js/types/index.d.ts`

### 2. AIHelperPass
AI-friendly validation pass for MCP server integration. See design
section below.

---

## Blocked Tasks

(none)

---

## Scheduled Tasks

| Date | Task | Notes |
|------|------|-------|
| March 1, 2026 | Review and remove `doc/src/main/hugo/content/` | Hugo content migrated to ossum.tech/riddl. Redirect site is in `doc/redirect-site/`. After confirming redirects have been working for ~1 month, the Hugo content directory can be deleted. Keep `doc/redirect-site/` for ongoing redirects. |
| November 2026 | Upgrade CodeQL Action v3 → v4 | GitHub deprecating CodeQL Action v3 in December 2026. Update `.github/workflows/scala.yml` line 182: `github/codeql-action/upload-sarif@v3` → `@v4`. See [changelog](https://github.blog/changelog/2025-10-28-upcoming-deprecation-of-codeql-action-v3/). |

---

## Import Functionality (Issue #72) - COMPLETE ✅

**Status**: Fully implemented (January 30, 2026)

BAST import is fully functional with three syntax variants:
- **Full import**: `import "file.bast"`
- **Selective import**: `import domain X from "file.bast"`
- **Aliased import**: `import type T from "file.bast" as MyT`

Allowed at root level, inside domains, and inside contexts. 14
definition kinds supported. 4 tests passing in `BASTLoaderTest.scala`.
Integrated into `ValidationPass`.

**Key files**: `CommonParser.scala` (parsing), `TopLevelParser.scala`
(post-parse loading), `BASTLoader.scala` (BAST reading), `AST.scala`
(`BASTImport` case class).

**Note**: The old `doImport()` stub in `ParsingContext.scala:82-90`
is superseded but still present. Could be cleaned up.

---

## BAST Module - COMPLETE ✅

**Status**: Fully implemented and integrated (60 tests, all passing)

| Metric | Result |
|--------|--------|
| Speed | 6-10x faster than parsing RIDDL |
| Size | 63-67% of source file size |
| Platforms | JVM, JS, Native |
| CLI | `riddlc bastify <file>` |

**Code locations**:
- Utilities: `language/shared/.../bast/`
- Pass: `passes/shared/.../BASTWriterPass.scala`

**Documentation**: BAST format specification goes to ossum.tech/riddl (not here).

**Key technical note**: `readNode()` and `readTypeExpression()` handle DISJOINT tag
sets - mixing them causes byte misalignment. See CLAUDE.md for details.



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
| Source file change markers | Only mark when source changes, not per-location | Per-location path index | 2026-01-17 |
| Metadata flag in tag high bit | Tags 1-67 fit in 7 bits; saves 1 byte for empty metadata | Separate count byte | 2026-01-18 |
| PathTable for path interning | Deduplicates repeated paths; ~5% size savings | No path interning | 2026-01-19 |
| Path table after string table | No header change needed; simpler implementation | Separate header offset | 2026-01-19 |

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

### February 4, 2026 (Knowledge Base Update & riddl-models Validation)

**Focus**: Verify import (#72) completion, update knowledge base, prepare
riddl-models validation work for handoff.

**Work Completed**:
1. Verified Import (#72) is FULLY IMPLEMENTED — updated NOTEBOOK.md
   and CLAUDE.md to reflect this (removed from Active Work Queue,
   added "COMPLETE" section)
2. Updated Homebrew tap to riddlc 1.2.3:
   - Built riddlc via `sbt riddlc/stage`, created zip, uploaded to
     GitHub release 1.2.3 (which was missing the riddlc.zip asset)
   - Updated formula version and SHA256 in homebrew-tap
   - Committed, pushed, and verified `brew upgrade` works
3. Ran `riddlc validate` on all 186 riddl-models entry points:
   - 141 pass, 45 fail
   - Categorized all errors into 6 types
4. Wrote comprehensive handoff document:
   `../riddl-models/TASK-fix-validation-errors.md`
   - Lists all 45 failing files with error categories
   - Includes fix instructions and validation commands
   - Describes the CI integration step after fixes

**Cross-project changes**:
- `../homebrew-tap/Formula/riddlc.rb` — version 1.2.3, new SHA256
- `../riddl-models/TASK-fix-validation-errors.md` — handoff document

---

### February 3-4, 2026 (npm Package Publishing to GitHub Packages)

**Focus**: Publish `@ossuminc/riddl-lib` as npm package to GH Packages
for consumption by Synapify and ossum.ai

**Key Finding**: Most infrastructure already existed (TypeScript
declarations, package.json template, npm-publish.yml workflow). The
sbt-ossuminc plugin already had `With.Packaging.npm()` and
`With.Publishing.npm()` helpers ready to use.

**Work Completed**:
1. Fixed Scala.js module kind: changed from CommonJS to ESModule
   (`withCommonJSModule = true` removed). Package.json already
   declared `"type": "module"` so this resolved the mismatch.
2. Wired up `With.Packaging.npm()` for riddlLibJS with scope
   `@ossuminc`, keywords, and ESModule flag
3. Wired up `With.Publishing.npm(registries = Seq("github"))`
4. Removed `export default RiddlAPI` from index.d.ts (ESModule
   uses named exports only)
5. Published `@ossuminc/riddl-lib` locally to GitHub Packages
   npm registry with `--tag dev`
6. Simplified `.github/workflows/npm-publish.yml` — replaced
   ~150 lines of custom shell scripting with sbt task calls
   (`riddlLibJS/npmPublishGithub`, `riddlLibJS/npmPublishNpmjs`)
7. Upgraded sbt-ossuminc to 1.3.0 (published to GH Packages,
   fixes npmTypesDir convention, CI-compatible)
8. CI workflow verified green — "Publish to GitHub Packages"
   step passes end-to-end from development branch

**Issues Encountered & Resolved**:
- npm requires `--tag` for prerelease versions (sbt-dynver format
  `1.2.3-1-hash-date` is a prerelease)
- `gh auth` needed `write:packages` scope refresh for npm
  publishing (`gh auth refresh -s write:packages`)
- CI couldn't resolve locally-published sbt-ossuminc 1.2.5-4;
  upgraded to published 1.3.0
- sbt-ossuminc 1.3.0 also fixed the npmTypesDir convention
  mismatch, removing need for manual override

**Commits** (on development):
- `c5361e87` — Add npm packaging via sbt-ossuminc and publish
  to GitHub Packages
- `e36b7a3e` — Upgrade sbt-ossuminc to 1.3.0 for CI-compatible
  npm packaging

**Files Modified**:
- `project/plugins.sbt` — sbt-ossuminc 1.3.0
- `build.sbt` — ESModule, npm packaging/publishing config
- `riddlLib/js/types/index.d.ts` — removed default export
- `.github/workflows/npm-publish.yml` — simplified to sbt tasks

**Remaining**:
- Test consumption from Synapify and ossum.ai
- When ready for release, tag a clean version (e.g., `1.2.4`)
  to get a proper semver npm version

---

### February 3, 2026 (Release 1.2.3 - System.lineSeparator Fix)

**Focus**: Comprehensive fix for `System.lineSeparator()` returning
`\0` null bytes in Scala.js shared code

**Root Cause**: `System.lineSeparator()` returns `\0` in Scala.js,
not just in `Messages.scala` (fixed in 1.2.2) but throughout all
shared code including `FileBuilder`, `StringHelpers`, and command
files.

**Approach**: The architecturally correct fix — added
`(using PlatformContext)` trait parameter to `FileBuilder` and
propagated through the entire class hierarchy. This ensures all
code uses `pc.newline` which returns the correct value per platform.

**Work Completed**:
1. ✅ **Fixed JVM/Native PlatformContext** — Changed hardcoded
   `"\n"` to `System.lineSeparator()` (correct on these platforms)
2. ✅ **Fixed Messages.scala** — Changed `System.lineSeparator()`
   to existing `nl` constant
3. ✅ **Fixed StringHelpers.toPrettyString** — Added
   `(using PlatformContext)`, uses `pc.newline`
4. ✅ **Fixed FileBuilder hierarchy** — Added
   `(using PlatformContext)` trait parameter, propagated through:
   `OutputFile`, `TextFileWriter`, `RiddlFileEmitter`,
   `PrettifyState`, `PrettifyVisitor`, `PrettifyOutput`
5. ✅ **Fixed Command files** — Replaced `System.lineSeparator()`
   with `io.newline`/`pc.newline` in `Command.scala`,
   `HelpCommand.scala`, `AboutCommand.scala`
6. ✅ **Fixed JS export issues** — Removed `@JSExportTopLevel`
   from `PrettifyState` and `PrettifyOutput` (incompatible with
   `using` parameter lists in Scala.js exports)
7. ✅ **All tests pass** — 715+ JVM tests, JS and Native compile
8. ✅ **Released 1.2.3** — Tagged, published to GitHub Packages,
   merged to main, GitHub release created

**Scala 3.7.4 Compiler Limitation Discovered**:
Default parameter values in a case class's first parameter list
cannot resolve `given` instances from a subsequent `using` clause
in the generated companion `apply` method. Worked around by
removing the default value for `PrettifyOutput.state` (only call
site provides it explicitly anyway). Documented with comment noting
potential fix in 3.9.x LTS.

**Commits** (on development, merged to main):
- `5bcfbc68` - Fix System.lineSeparator() returning null bytes in
  Scala.js

**Files Modified** (18 files):
- `utils/shared/.../FileBuilder.scala` — trait parameter
- `utils/shared/.../StringHelpers.scala` — using clause
- `utils/shared/test/.../StringHelpersTest.scala`
- `utils/jvm/.../JVMPlatformContext.scala`
- `utils/native/.../NativePlatformContext.scala`
- `utils/jvm-native/.../OutputFile.scala`
- `utils/jvm-native/.../TextFileWriter.scala`
- `utils/jvm/test/.../FileBuilderTest.scala`
- `utils/jvm/test/.../TextFileWriterTest.scala`
- `language/shared/.../Messages.scala`
- `passes/shared/.../PrettifyPass.scala`
- `passes/shared/.../PrettifyState.scala`
- `passes/shared/.../PrettifyVisitor.scala`
- `passes/shared/.../RiddlFileEmitter.scala`
- `passes/jvm-native/test/.../RiddlFileEmitterTest.scala`
- `commands/shared/.../Command.scala`
- `commands/shared/.../AboutCommand.scala`
- `commands/shared/.../HelpCommand.scala`

---

### February 3, 2026 (Packaging Infrastructure)

**Focus**: Packaging infrastructure for Docker, npm, TypeScript, and
sbt-ossuminc plan document

**Work Completed**:
1. ✅ **Wrote sbt-ossuminc PACKAGING-PLAN.md** — Comprehensive design
   document for new `With.Packaging.npm()`, `With.Publishing.npm()`,
   `With.Packaging.homebrew()`, `With.Packaging.linux()`, and
   `With.Packaging.windowsMsi()` helpers. Includes API signatures,
   implementation approach, scripted test strategy, 6-phase plan,
   migration guide for riddl, and design decisions.
2. ✅ **Updated CLAUDE.md for Scala 3.7.4** — All version references
   corrected from 3.3.7 to 3.7.4 with compiler bug explanation
3. ✅ **Committed workflow path updates** — `scala.yml` and
   `coverage.yml` paths updated from `scala-3.3.7` to `scala-3.7.4`
4. ✅ **Committed Docker packaging** — `Dockerfile` (multi-stage with
   jlink custom JRE), `docker-publish.yml` workflow, `build.sbt`
   docker configuration
5. ✅ **Committed npm/TypeScript packaging** — Enhanced
   `package.json.template`, updated `pack-npm-modules.sh` with TS
   integration, added `riddlLib/js/types/index.d.ts` (392 lines),
   added `npm-publish.yml` workflow
6. ✅ **Pushed all to development** — 4 commits pushed

**Commits** (pushed to development):
- `6faa01eb` - Update workflow paths for Scala 3.7.4
- `2f63be1d` - Add Docker packaging infrastructure for riddlc
- `ba185b99` - Add npm packaging improvements and TypeScript support
- `ae6f8342` - Update CLAUDE.md and NOTEBOOK.md for Scala 3.7.4 and
  packaging

**Cross-project artifact**: `sbt-ossuminc/PACKAGING-PLAN.md` created
for another worker to implement in sbt-ossuminc 1.3.0

---

### February 3, 2026 (Release 1.2.2 - Scala.js Bugfix)

**Focus**: Fix Scala.js error message truncation blocking synapify

**Root Cause**: `System.lineSeparator()` and `System.getProperty("line.separator")`
both return `null` in Scala.js, causing error messages to include "null" instead
of newlines, resulting in truncated/malformed output.

**Work Completed**:
1. ✅ **Fixed Messages.scala** - Changed `System.lineSeparator()` to `"\n"`
2. ✅ **Fixed RiddlParserInput.scala** - Changed `System.getProperty("line.separator")` to `"\n"`
3. ✅ **Created bugfix branch** `bugfix/js-newline-null`, merged to development
4. ✅ **Waited for CI** - Both Scala Build and Coverage passed
5. ✅ **Released 1.2.2** - Tagged, pushed, published to GitHub Packages
6. ✅ **Created GitHub release** - https://github.com/ossuminc/riddl/releases/tag/1.2.2
7. ✅ **Updated Homebrew formula** - ossuminc/homebrew-tap updated to 1.2.2
8. ✅ **Cleaned up** - Deleted bugfix branch (local and remote)

**Files Modified**:
- `language/shared/src/main/scala/com/ossuminc/riddl/language/Messages.scala`
- `language/shared/src/main/scala/com/ossuminc/riddl/language/parsing/RiddlParserInput.scala`

**Test Results**: All 715 JVM tests pass

**Homebrew Tap** (also completed this session):
- Created `ossuminc/homebrew-tap` repository
- Added `Formula/riddlc.rb` formula with openjdk@21 dependency
- Added README.md with installation instructions
- No registration needed - Homebrew auto-discovers `username/homebrew-tap` repos

**Branch Cleanup** (also completed this session):
- Deleted merged branches: `feature/parsing-fixes`, `feature/tatsu-ebnf-validation`,
  `591-create-brew-packager`, `664-feature-add-timing-data-to-parsing-results`,
  `feature/scala-bug`
- Closed Issue #38 (BAST complete), deleted branch `38-implement-bast-file-read-write`
- Closed Issue #608 (moved to riddl-gen), deleted branch
- Deleted `OSS-275-Prompt-Generation` branch (moved to riddl-gen)
- Deleted `367-auto-entity-id` branch (issue already closed, feature not implemented)
- Deleted `304-get-code-coverage--80` branch (stale, 9 months old)
- Deleted `consistent-output` branch (stale, 16 months old)
- Only `main` and `development` branches remain

---

### February 1, 2026 (Cardinality Fix)

**Focus**: Fix cardinality prefix/suffix mutual exclusivity

**Branch**: `feature/parsing-fixes`

**Work Completed**:
1. ✅ **Updated EBNF grammar** to allow `many optional` as valid prefix combination
   - `type_expression` and `field_type_expression` now accept `("many" ["optional"] | "optional")`
2. ✅ **Updated TypeParser.scala** to enforce mutual exclusivity
   - Allows prefix only: `many` (=+), `optional` (=?), `many optional` (=*)
   - Allows suffix only: `?`, `+`, `*`
   - Rejects prefix AND suffix together with clear error message
3. ✅ **Restored rbbq.riddl** to use `many optional RewardEvent` syntax
   - Demonstrates valid cardinality prefix usage
   - Fixes TokenParserTest expected offsets

**Test Results**: All 715 tests pass across all modules

**Task #10 Verification** (metadata with-block requirement):
- Confirmed fastparse already correctly enforces `with { }` wrapper for metadata
- `} briefly "..."` (after close, no with) → Rejected ✅
- `{ briefly "..." }` (inside body) → Rejected ✅
- `} with { briefly "..." }` (correct syntax) → Accepted ✅
- No code changes needed - task was already satisfied

**Release 1.2.1** (February 1, 2026):
- Merged `feature/parsing-fixes` to `main`
- Tagged and pushed `1.2.1`
- All 715 tests passed
- Published to GitHub Packages
- Created GitHub release: https://github.com/ossuminc/riddl/releases/tag/1.2.1

**Files Modified**:
- `language/shared/src/main/resources/riddl/grammar/ebnf-grammar.ebnf`
- `language/shared/src/main/scala/com/ossuminc/riddl/language/parsing/TypeParser.scala`
- `language/input/rbbq.riddl`

---

### February 1, 2026 (TatSu EBNF Validation - In Progress)

**Focus**: Implement automated EBNF grammar validation in CI using TatSu

**Branch**: `feature/tatsu-ebnf-validation`

**Context**: The EBNF grammar at `language/shared/src/main/resources/riddl/grammar/ebnf-grammar.ebnf` documents RIDDL syntax but can drift from the actual fastparse implementation. This work adds CI validation to catch drift.

**Work Completed**:
1. ✅ **Created TatSu-based validator framework**
   - `language/jvm/src/test/python/ebnf_preprocessor.py` - Converts EBNF to TatSu format
   - `language/jvm/src/test/python/ebnf_tatsu_validator.py` - Validates RIDDL files
   - Updated `requirements.txt` with TatSu>=5.12.0

2. ✅ **Updated CI workflow** (`.github/workflows/scala.yml`)
   - Changed from Lark-based to TatSu-based validation

3. ✅ **Updated CLAUDE.md**
   - Added "Parser/EBNF Synchronization Requirement" section

4. ✅ **Fixed EBNF Issue #1: `???` placeholder ordering**
   - Problem: PEG parsers try alternatives in order; closures matching zero items shadow `???`
   - Fixed `enumerators` (line 100): `{enumerator [","]} | "???"` → `"???" | {enumerator [","]}`
   - Fixed `aggregate_definitions` (line 115): same pattern

5. 🚧 **Discovered EBNF Issue #2: `simple_identifier` consuming whitespace**
   - TatSu skips whitespace between tokens, even inside closures
   - `simple_identifier = letter { letter | digit | "_" | "-" }` causes "A from context Two" to parse as single identifier
   - **Proposed fix**: `simple_identifier = /[a-zA-Z][a-zA-Z0-9_-]*/` (regex pattern)

**Current Validation Results**:
- 14/77 files pass
- 13 skipped (include fragments)
- 2 expected failures
- 48 unexpected failures (EBNF/parser drift to fix)

**Key Learning**: TatSu uses PEG semantics where:
- Alternatives are tried in order (put specific literals before general patterns)
- Whitespace is skipped between token matches (lexical rules need regex patterns)
- Closures matching zero items "succeed" and don't try next alternative

**Files Created**:
- `language/jvm/src/test/python/ebnf_preprocessor.py`
- `language/jvm/src/test/python/ebnf_tatsu_validator.py`

**Files Modified**:
- `language/jvm/src/test/python/requirements.txt`
- `language/shared/src/main/resources/riddl/grammar/ebnf-grammar.ebnf` (2 fixes)
- `.github/workflows/scala.yml`
- `.gitignore` (added .venv)
- `CLAUDE.md`

**Next Steps**:
1. Fix `simple_identifier` to use regex pattern (prevents whitespace consumption)
2. Fix `quoted_identifier` similarly
3. Review and fix other lexical rules (zone, option_name, etc.)
4. Work through remaining 48 failures systematically

---

### January 31, 2026 (Scala 3.7.4 Compiler Bug - RESOLVED)

**Focus**: Fix Scala 3.7.4 compiler infinite loop caused by opaque type Contents

**Root Cause**: Duplicate Contents definitions - one in AST.scala object (lines 107-229) and one at package level in Contents.scala. Scala 3.7.4 has a known bug with opaque types inside objects, especially with intersection types like `CV & CV2` in the merge method.

**Work Completed**:
1. ✅ **Fixed compiler infinite loop**
   - Removed Contents definition from inside AST.scala object
   - Kept only package-level Contents.scala with opaque type and extensions
   - Renamed `map` extension to `mapValue` to avoid ambiguity with Seq.map
   - Changed merge to return `Contents[RiddlValue]` instead of `CV & CV2`
2. ✅ **Fixed BASTImport conflicts**
   - Renamed `kind: Option[String]` field to `kindOpt: Option[String]`
   - Added `override def kind: String = kindOpt.getOrElse(super.kind)` method
   - Updated BASTWriter and BASTLoader to use `kindOpt`
3. ✅ **Updated test file imports**
   - Changed all test imports from `import Contents` to `import language.{Contents, *}`
   - Extensions at package level require wildcard import to be visible
4. ✅ **Attempted package object approach** - Did not work
   - Extensions inside package object can't access opaque type internals
   - Opaque type's underlying ArrayBuffer only visible in same file
   - Reverted to keeping extensions at package level in Contents.scala
5. ✅ **Updated ../CLAUDE.md** - Added collaboration protocol
   - Never rush ahead without approval
   - Questions deserve answers, not immediate actions
   - One file at a time for approval with Edit tool
   - Wait for explicit approval before code changes

**Files Modified** (33 files total):
- language/shared/.../AST.scala - Removed Contents, fixed BASTImport
- language/shared/.../Contents.scala - Package-level opaque type and extensions
- language/shared/.../bast/BASTWriter.scala - Use bi.kindOpt
- language/shared/.../bast/BASTLoader.scala - Use bi.kindOpt
- language/shared/.../parsing/*.scala (24 files) - Added Contents import
- language/.../test/.../parsing/*.scala (15 files) - Changed to wildcard import

**Test Results**:
- All 714 JVM tests pass ✅
- 1 unrelated failure in local project validation test (shopify-cart.riddl)

**Session 2 Work** (same day):
6. ✅ **Fixed 16 fastparse context function errors in test files**
   - `TestParserTest.scala`, `TestParsingRules.scala`, `CommonParserTest.scala`
   - Changed `tp.root` → `p => tp.root(using p)` (explicit lambda for context functions)
   - Changed `toEndOfLine` → `p => toEndOfLine(using p)`
7. ✅ **Fixed passes module import errors** (9 main files, 23 test files)
   - Added `import com.ossuminc.riddl.language.{Contents, *}` for extension methods
   - Fixed `with` → `&` intersection type syntax in BASTWriterPass.scala
8. ✅ **Fixed unreachable case warnings** in ReferenceMapTest.scala
   - Removed `case x => fail(...)` after exhaustive `Option` matches

**Commits**:
- `1b022e0a` - Fix Scala 3.7.4 compiler hang by extracting Contents to package level
- (pending) - Fix all test compilation errors for Scala 3.7.4

---

### January 30, 2026 (Scala Version Upgrade - BLOCKED)

**Focus**: Upgrade from Scala 3.3.7 LTS to newer version to fix compiler issues

**Goal**: Needed to upgrade Scala to avoid issues with Scala 3.7's changed underscore syntax
for fastparse context-bound methods (`methodName(_)` → `p => methodName(using p)`).

**Work Completed**:
1. ✅ **Updated parser files to use explicit lambda syntax** - All parser files updated from
   `include[u, XxxContents](xxxDefinitions(_))` to `include[u, XxxContents](p => xxxDefinitions(using p))`
2. ✅ **Restructured AST.scala extension methods** - Moved `apply(n: Int)` extension into Contents
   companion object to prevent namespace pollution affecting fastparse's method resolution
3. ✅ **Created isolated test cases** - Verified fixes work in standalone Scala-CLI tests

**Parser Files Modified** (explicit lambda syntax):
- AdaptorParser.scala, ContextParser.scala, DomainParser.scala, EntityParser.scala
- EpicParser.scala, FunctionParser.scala, ModuleParser.scala, ProjectorParser.scala
- RepositoryParser.scala, RootParser.scala, SagaParser.scala, StreamingParser.scala
- ExtensibleTopLevelParser.scala, GroupParser.scala

**BLOCKER: Scala Compiler Infinite Loop**

Both Scala 3.7.4 and 3.6.3 exhibit an infinite loop in the compiler's type system when
compiling AST.scala. The jstack shows:

```
at dotty.tools.dotc.core.Types$Type.hasClassSymbol(Types.scala:648)
at dotty.tools.dotc.core.Types$Type.hasClassSymbol(Types.scala:648)
...
at dotty.tools.dotc.core.SymDenotations$ClassDenotation.computeAndOrType$1
```

The `computeAndOrType` indicates the intersection type `Contents[CV & CV2]` in the `merge`
extension method is triggering the bug. The compiler recurses infinitely when computing
the type for:

```scala
extension [CV <: RiddlValue, CV2 <: RiddlValue](container: Contents[CV])
  def merge(other: Contents[CV2]): Contents[CV & CV2] = ...
```

**Current State**:
- `build.sbt` set to Scala 3.7.4 (7 modules)
- Parser files updated with explicit lambda syntax
- AST.scala extension methods restructured
- Compilation hangs indefinitely due to compiler bug

**Next Steps** (for user to research):
1. Check if there's a Scala compiler issue filed for this specific pattern
2. Try alternative formulations of the merge method that avoid the intersection type
3. Test with Scala 3.5.x or earlier versions
4. Consider if the merge method can use a different type strategy

**Commits** (pushed to GitHub):

*development branch* (selective BAST imports, EBNF work):
- `fd58e5a3` - Update NOTEBOOK.md with January 30 session status
- `cfb38395` - Update EBNF grammar for selective BAST imports
- `bc8faa6d` - Add selective import support to BAST module
- `53fa68be` - Add selective BAST import parsing
- `f153c508` - Add test files for BAST imports and EBNF validation

*feature/scala-bug branch* (Scala 3.7.4 upgrade work - WIP):
- `2ec7cc82` - WIP: Scala 3.7.4 upgrade - parser and AST changes

---

### January 29, 2026 (CI Build Fix)

**Focus**: Fix failing GitHub Actions workflows

**Root Causes Identified**:
1. `diagrams` module referenced in workflows but moved to `riddl-gen` repository
2. Scala version paths incorrect: `scala-3.4.3` instead of `scala-3.3.7` LTS

**Tasks Completed**:
1. ✅ **Fixed scala.yml**
   - Removed `diagrams/publishLocal` (line 104)
   - Changed all `scala-3.4.3` → `scala-3.3.7` (env var, cache paths, artifact paths)
2. ✅ **Fixed coverage.yml**
   - Removed `diagrams/Test/compile` and `diagrams/test`
   - Changed all `scala-3.4.3` → `scala-3.3.7` in artifact paths
3. ✅ **Updated CLAUDE.md**
   - Fixed incorrect "Scala 3.7.4" → "Scala 3.3.7 LTS"
   - Added "CRITICAL: Scala Version Change Impact" section documenting all files needing updates when Scala version changes
   - Added note #16 to "Notes for Future Sessions"
4. ✅ **Scheduled CodeQL v3 → v4 upgrade** for November 2026 (deprecation in December 2026)

**Commits**:
- `613a0bfd` - Fix CI workflows: remove diagrams module and correct Scala version

**Build Results**: ✅ All jobs passing
- dependency-check (41s) ✅
- scala-build (JS) (3m56s) ✅
- scala-build (JVM) (4m26s) ✅
- scala-build (Native) (10m21s) ✅
- coverage (3m14s) ✅

---

### January 29, 2026 (Documentation Migration to ossum.tech)

**Focus**: Migrate riddl.tech documentation to ossum.tech/riddl and set up redirects

**Tasks Completed**:
1. ✅ **Updated README.md** - Changed all riddl.tech URLs to ossum.tech/riddl
2. ✅ **Updated CLAUDE.md** - Added Documentation section pointing to ossum.tech/riddl
3. ✅ **Created redirect site** (`doc/redirect-site/`) - 9 HTML files with meta refresh + JS redirects
4. ✅ **Updated hugo.yml workflow** - Now deploys redirect site instead of Hugo build
5. ✅ **Updated .gitignore** - Added `.claude/` and package-lock.json

**Redirect Pages Created**:
- `index.html` → ossum.tech/riddl/
- `404.html` → ossum.tech/riddl/
- `introduction/index.html` → ossum.tech/riddl/introduction/
- `concepts/index.html` → ossum.tech/riddl/concepts/
- `guides/index.html` → ossum.tech/riddl/guides/
- `tooling/index.html` → ossum.tech/riddl/tools/
- `tooling/riddlc/index.html` → ossum.tech/riddl/tools/riddlc/
- `tutorial/index.html` → ossum.tech/riddl/tutorials/
- `tutorial/rbbq/index.html` → ossum.tech/riddl/tutorials/rbbq/

**Commits**:
- `a30a86a` - Update README.md URLs from riddl.tech to ossum.tech/riddl
- `7e61c3e` - Add redirect site and update hugo.yml workflow
- `0f6c1e4` - Update .gitignore and add scheduled removal task

**Note**: Hugo content in `doc/src/main/hugo/content/` retained until March 1, 2026 review (see Scheduled Tasks).

---

### January 28, 2026 (Hugo/Diagrams Removal)

**Focus**: Remove Hugo documentation generation and diagrams modules (moved to riddl-gen repository)

**Context**: Hugo documentation generation and diagram generation capabilities have been relocated to the separate `riddl-gen` repository. This session completed the removal from the main RIDDL codebase.

**Tasks Completed**:
1. ✅ **Remove diagrams module** (12 files, 709 deletions)
   - Mermaid diagram generators (C4, context maps, data flow, ERD, etc.)

2. ✅ **Remove hugo command code** (28 files, 3070 deletions)
   - HugoCommand, HugoPass, writers, themes, utilities

3. ✅ **Remove hugo tests and config** (19 files, 1270 deletions)
   - All hugo-related test files and hugo.conf

4. ✅ **Update build and command loader** (3 files)
   - Removed diagrams from build.sbt aggregation
   - Removed HugoCommand from CommandLoader
   - Updated RegressionTests

5. ✅ **Update documentation** (7 files)
   - command-line-help.md - current riddlc commands
   - hugo.md, diagrams.md, translation/_index.md - riddl-gen notices
   - options.md - current command options
   - ways-to-use-riddl.md - riddl-gen reference
   - riddlcExamples.md - deprecation warning

6. ✅ **Update CLAUDE.md and EBNF grammar** (2 files)
   - Updated dependency pipeline
   - Updated grammar for current commands

**Commits** (6 cohesive batches):
- `4edf7a8c` - Remove diagrams module (moved to riddl-gen)
- `4f9433cc` - Remove hugo command code (moved to riddl-gen)
- `e963485a` - Remove hugo tests and config (moved to riddl-gen)
- `3c242dd3` - Update build and command loader for hugo/diagrams removal
- `cf4e9683` - Update documentation for hugo/diagrams relocation to riddl-gen
- `7e4462dd` - Update CLAUDE.md and EBNF grammar for hugo/diagrams removal

**Current riddlc Commands**:
- `about`, `bastify`, `dump`, `unbastify`, `flatten`, `from`, `help`, `info`, `onchange`, `parse`, `prettify`, `repeat`, `stats`, `validate`, `version`

---

### January 27, 2026 (Scala 3.3.7 LTS Migration)

**Focus**: Update riddl to use Scala 3.3.7 LTS and fix test failures

**Tasks Completed**:
1. ✅ **Scala 3.3.7 LTS Migration**
   - Added `With.scala3` to Root project configuration in `build.sbt`
   - Removed hardcoded `Global / scalaVersion := "3.7.4"`
   - Now uses sbt-ossuminc's default Scala 3.3.7 LTS

2. ✅ **BAST Test Fixes**
   - `DeepASTComparison.scala` - Changed to compare offsets instead of line/col (BASTParserInput uses synthetic 10000-char lines)
   - `BASTFileReadTest.scala` - Removed dependency on non-existent `everything.bast` file
   - `BastGenCommandTest.scala` - Updated command name from "bast-gen" to "bastify"

3. ✅ **Committed changes** to riddl repository

4. ✅ **External Project Validation** (institutional-commerce) - Completed
   - Validated `/Users/reid/Code/ossuminc/institutional-commerce`
   - All RIDDL syntax issues fixed, project now validates properly

**Files Modified**:
- `build.sbt` - Added `With.scala3` to Root project
- `passes/jvm/.../DeepASTComparison.scala` - Compare offsets instead of line/col
- `passes/jvm/.../BASTFileReadTest.scala` - Removed file comparison
- `commands/jvm/.../BastGenCommandTest.scala` - Changed command name

---

### January 20, 2026 (BAST Phase 9: Critical Bug Fix)

**Focus**: Fix BAST ref/definition tag collision causing byte misalignment

**Root Cause Identified**:
Reference types (RepositoryRef, ProjectorRef, etc.) shared the same tag values as their definition counterparts, but had different binary structures:
- Definitions: loc, id, contents, metadata
- References: loc, pathId only

When deserializing a RepositoryRef, the reader thought it was a Repository definition and tried to read contents/metadata, causing byte stream misalignment.

**Tasks Completed**:
1. ✅ **Added 22 new dedicated REF tags (80-101)** in `package.scala`
   - NODE_AUTHOR_REF through NODE_DOMAIN_REF
2. ✅ **Updated BASTWriter** to use new REF tags for all reference writes
3. ✅ **Added readXxxRefNode() methods** in BASTReader for all new REF tags
4. ✅ **Updated readReference(), readProcessorRef(), readPortletRef()** to handle new tags
5. ✅ **Fixed message ref node readers** to not consume tag (readNode already consumed it)
6. ✅ **Fixed BASTDebugTest** byte order issues in header reading
7. ✅ **Released version 1.1.2** - Pushed to GitHub and published locally
8. ✅ **Merged AIHelperPass-DESIGN.md** into NOTEBOOK.md, deleted original file

**Test Results**: 252 tests pass, 2 fail (location line/col reconstruction - separate issue)

**Files Modified**:
- `language/shared/.../bast/package.scala` - Added 22 REF tags
- `language/shared/.../bast/BASTWriter.scala` - Updated to use REF tags
- `language/shared/.../bast/BASTReader.scala` - Added readXxxRefNode() methods, updated dispatchers
- `passes/jvm/.../BASTDebugTest.scala` - Fixed byte order

**Remaining Issues**:
- Location line/col reconstruction in BASTParserInput needs fixing (separate bug)
- unbastify command still pending

---

### January 20, 2026 (Post-Release: bastify/unbastify Commands)

**Focus**: Add CLI commands for BAST generation and reconstitution

**Tasks Completed**:
1. ✅ **Published release 1.1.1** to GitHub Packages (all platforms)
2. ✅ **Added `bastify` command** - Converts RIDDL to BAST
   - Usage: `riddlc bastify <input.riddl>`
   - Places .bast file next to source
   - Simplified from original `bast-gen` (removed extra options)

**In Progress**:
3. 🚧 **Add `unbastify` command** - Converts BAST back to RIDDL
   - Should produce all included/imported files, not just one file
   - Will use include information saved during BAST serialization
   - Target: Reflection test (riddl → bast → riddl should be identical)

**Shelved Tasks** (to do later):
1. **Critical review of RIDDL language** - Assess completeness for declarative distributed system specification, identify useful idioms, evaluate statement sufficiency
2. **Compressed documentation table for BAST** - English text (comments, descriptions) compresses well. Could add separate "doc table" using LZ4 or zstd compression for long strings (>50 chars). Estimated 50-70% reduction for documentation-heavy files. Would need cross-platform compression library.

**Files Modified**:
- `commands/jvm/.../BastifyCommand.scala` - New (renamed from BastGenCommand)
- `commands/jvm/.../CommandLoader.scala` - Updated to use bastify

---

### January 19, 2026 (Phase 8 Complete - Release Preparation)

**Focus**: Implement Phase 8 PathIdentifier interning and prepare for release

**Tasks Completed**:
1. ✅ **Phase 8 PathIdentifier Interning**
   - Created `PathTable.scala` class mirroring StringTable pattern
   - Updated `BASTWriter.writePathIdentifierInline()` to use path table
   - Updated `BASTReader.readPathIdentifierInline()` to handle both lookup and inline modes
   - Encoding: count==0 means table lookup, count>0 means inline path
   - Path table written immediately after string table (no header changes)
   - All 60 BAST tests pass

2. ✅ **Documentation Updates**
   - Updated `package.scala` with Phase 8 file format info
   - Updated NOTEBOOK.md with Phase 8 completion
   - Marked AsciiDoc module as deferred for future release

**Files Created**:
- `language/shared/.../bast/PathTable.scala` - New path interning table

**Files Modified**:
- `language/shared/.../bast/BASTWriter.scala` - Added pathTable, updated writePathIdentifierInline
- `language/shared/.../bast/BASTReader.scala` - Added pathTable loading, updated readPathIdentifierInline
- `language/shared/.../bast/package.scala` - Updated documentation

**Test Results**: All 60 BAST tests pass

**Release Status**: Ready for commit, PR, and release

---

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

## Future Work: AIHelperPass

**Status**: Design complete, implementation pending

### Overview

The **AIHelperPass** is a proposed validation pass designed specifically for AI consumers working with RIDDL models. Unlike the standard ValidationPass which focuses on finding errors and warnings, AIHelperPass provides proactive guidance to help AI systems iteratively build and improve RIDDL models.

### Design Goals

1. **Proactive Guidance** - Suggest what to add next, not just what's wrong
2. **AI-Optimized Output** - Messages formatted for AI consumption
3. **Lightweight Execution** - No reference resolution dependency (faster, works on incomplete models)
4. **Iterative Development** - Support incremental model building by an AI

### Key Differences from ValidationPass

| Aspect | ValidationPass | AIHelperPass |
|--------|----------------|--------------|
| Purpose | Find problems | Suggest improvements |
| Dependencies | SymbolsPass + ResolutionPass | SymbolsPass only |
| Output | Errors, Warnings | Tips (primarily) |
| Reference checking | Yes | No |
| Completeness | Requires complete model | Works on incomplete models |
| Consumer | Humans, CI/CD | AI systems |

### New Message Type: `Tip`

```scala
case class Tip(
  loc: At,
  message: String,
  category: TipCategory,
  priority: Int = 5,    // 1-10, higher = more important
  context: Option[String] = None
) extends KindOfMessage

enum TipCategory:
  case Completeness    // Missing but commonly needed elements
  case Pattern         // Recognized patterns that could be completed
  case BestPractice    // Conventional RIDDL idioms
  case Relationship    // Connections between definitions
  case Documentation   // Description and metadata suggestions
```

### Tip Generation Rules

1. **Empty Container Tips** - Domain/Context/Entity without contents
2. **Incomplete Entity Tips** - Missing state, handlers, events
3. **Context Completeness Tips** - Entities without repository, no adaptors
4. **Handler Completeness Tips** - Empty on-clauses
5. **Type Enhancement Tips** - Events missing correlation ID or timestamp
6. **Documentation Tips** - Missing descriptions on significant definitions
7. **Relationship Tips** - Isolated contexts with no connections

### MCP Server Integration

AIHelperPass is designed to integrate with the `riddl-mcp-server` for AI-assisted model generation:

```scala
Tool(
  name = "validate-partial",
  inputSchema = JsonSchema(...),
  description = Some("Analyze RIDDL model and provide AI-friendly tips for improvement")
)
```

### Implementation Phases

- [ ] **Phase 1**: Add `Tip` message type to Messages.scala, create AIHelperPass skeleton
- [ ] **Phase 2**: Entity and handler completeness checks
- [ ] **Phase 3**: Context and relationship analysis
- [ ] **Phase 4**: Documentation checks and priority tuning

### Open Questions

1. Should Tips include suggested RIDDL code snippets?
2. Should there be a "dismiss tip" mechanism?
3. How to handle tips that become irrelevant after model changes?

*Design Author: Claude (AI Assistant), January 17, 2026*

---

## Git Information

**Branch**: `main`
**Latest release**: 1.2.3 (February 3, 2026)
