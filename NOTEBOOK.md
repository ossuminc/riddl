# Engineering Notebook: RIDDL

This is the central engineering notebook for the RIDDL project. It tracks current status, work completed, design decisions, and next steps across all modules.

---

## Current Status

**Last Updated**: February 16, 2026 (evening)

**Scala Version**: 3.7.4 (overrides sbt-ossuminc's 3.3.7 LTS
default due to compiler infinite loop bug with opaque
types/intersection types in 3.3.x). All workflow paths updated
to `scala-3.7.4`.

**Release 1.11.0 Published**: Scala.js validation performance
improvements and new APIs. ParentStack caching (Phase 0),
ValidationPass micro-optimizations (Phase 1),
`validateStringQuick()` (Phase 2), `IncrementalValidator`
(Phase 3). Also: ScalaDoc fixes, sbt 1.12.1, sbt-ossuminc
1.3.3 (npm publishLocal support). All tests pass on all
platforms. Published to GitHub Packages.

**Release 1.7.0 Published**: Analysis passes, validation
enhancements, diagram extensions. Committed in 7 cohesive
batches, CI green on all platforms (JVM, JS, Native), tagged,
published to GitHub Packages, GitHub release created. Includes:
- Analysis passes: MessageFlowPass, EntityLifecyclePass,
  DependencyAnalysisPass
- ValidationPass: HandlerCompleteness, RecognizedOptions,
  streaming reachability, new semantic validations
- DiagramsPass: DataFlowDiagramData, DomainDiagramData
- StatsPass: prompt/executable statement classification
- RiddlLib/RiddlAPI: getHandlerCompleteness, getMessageFlow,
  getEntityLifecycles
- Downstream integration plans delivered to 4 projects
  (reference 1.7.0, not 1.6.0 as written in files)

**Release 1.6.0 Published**: ValidationPass bug fixes and new
validations. Fixed SagaStep undo check, SagaStep shape check,
duplicate checkMetadata. Added validation for Schema, Relationship,
Streamlet shape/handler, Adaptor/Repository handler requirements,
Projector repo ref, Epic/UseCase user ref, Function input/output
types. 1,526 tests pass (0 failures). Published to GitHub Packages.

**Release 1.5.0 Published**: Extracted cross-platform `RiddlLib`
trait from JS-only `RiddlAPI`. `RiddlAPI` is now a thin JS facade
delegating to `RiddlLib`. `parseString` returns opaque Root handle;
use `getDomains()`/`inspectRoot()` accessors. Fixed `riddlLibJS/test`
ESModule crash. 1,527 tests pass (0 failures). Published to GitHub
Packages (Maven + npm). BREAKING: TS consumers must update
`parseString` usage.

**Release 1.4.0 Published**: `Container.flatten()` extension, rewritten
`FlattenPass`, multi-platform release workflow. Native macOS ARM64
binary distributed via Homebrew.

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

**Homebrew Tap Updated**: `ossuminc/homebrew-tap` updated to 1.4.0.
macOS ARM64 gets native binary (no JDK dependency). Other platforms
fall back to JVM version with openjdk@21.

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

## Session Log

### February 16, 2026 (evening) — CI Fixes

Fixed two unrelated CI failures on `development` branch:

1. **RiddlModelsRoundTripTest** — `assume()` inside ScalaTest
   `should` block caused suite abort on CI (where `riddl-models`
   doesn't exist). Rewrote to download riddl-models zip from
   GitHub when local checkout absent, using same
   `PathUtils.copyURLToDir` + `Zip.unzip` pattern as
   `RunCommandOnExamplesTest`. Local dev still uses fast local
   checkout. 187 tests pass.

2. **TatSu 5.17.0 import failure** — New release has missing
   `rich` dependency (`ModuleNotFoundError`). Pinned to
   `TatSu>=5.12.0,<5.17.0` (resolves to 5.16.0 on Python 3.12).

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

### 1. Update Consumers for 1.5.0+ Changes (DONE)
**Status**: Complete (February 9, 2026)

Created `RIDDL-UPDATE-NOTES.md` in synapify, riddl-mcp-server,
and ossum.ai covering the 1.5.0 breaking change (opaque Root)
and 1.7.0 new API functions. Existing `RIDDL-INTEGRATION-PLAN.md`
files in synapify and riddl-mcp-server provide detailed guidance.

### 2. Comprehensive TypeScript Declarations for AST & Passes
**Status**: Partially complete

TypeScript declarations for all RiddlAPI methods are now current
(updated February 9, 2026 to add `getHandlerCompleteness`,
`getMessageFlow`, `getEntityLifecycles`, `ast2bast` plus return
type interfaces).

**Remaining**: Full AST hierarchy declarations (Domain, Context,
Entity, etc.) are still opaque. This is lower priority since JS
consumers interact via the RiddlAPI facade, not the raw AST.

### 3. Add `RiddlLib.ast2bast` Function (DONE)
**Status**: Implemented (February 9, 2026)

Added `ast2bast(root: Root)` to:
- `RiddlLib` trait (shared, returns `Array[Byte]`)
- `RiddlLib` object (implementation via BASTWriterPass)
- `RiddlAPI` JS facade (returns `Int8Array`)
- TypeScript declarations in `index.d.ts`
- Test in `RiddlLibTest` (verifies BAST magic header)

### 4. AIHelperPass
AI-friendly validation pass for MCP server integration. See design
section below.

### 5. Scala.js Validation Performance (DONE)
**Status**: Complete (February 16, 2026)

Four-phase optimization to make validation practical for large
models in Scala.js (browser playground, future LSP):

- **Phase 0**: ParentStack caching — replaced type alias with
  wrapper class that caches `toParents` (toSeq) result
- **Phase 1**: ValidationPass micro-optimizations —
  `recursiveFindByType` cache, single-pass handler
  classification, combined SagaStep validation
- **Phase 2**: `validateStringQuick()` — `ValidationMode` enum
  gates expensive postProcess checks for interactive feedback
- **Phase 3**: `IncrementalValidator` — context-level
  fingerprinting and message caching for repeated validation

See session log February 16, 2026 for details.

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

## Changes Since v1.10.2

- Fixed `on pther` typo in `OnOtherClause.id` (AST.scala)
- Fixed missing `|` prefix on continuation lines in
  `emitDescription` (RiddlFileEmitter.scala)
- Fixed `HOMEBREW_TAP_TOKEN` → `HOMEBREW_TAP_SECRET` in
  `release.yml` dispatch step

## Session Log

### February 16, 2026 (Release 1.11.0 — Performance & APIs)

**Focus**: Implement 4-phase Scala.js validation performance
plan, fix ScalaDoc warnings, upgrade build tooling, add npm
publishLocal to sbt-ossuminc, tag and publish 1.11.0.

**Work Completed**:
1. **Phase 0: ParentStack caching** — Replaced `ParentStack`
   type alias with wrapper class caching `toSeq` result,
   invalidated on push/pop. Fixes O(N*D) allocations
2. **Phase 1: ValidationPass micro-optimizations** — Added
   `recursiveFindByType` cache to Finder, `walkStatements`
   helper, single-pass handler classification, combined
   SagaStep validation
3. **Phase 2: `validateStringQuick()`** — Added
   `ValidationMode` enum (Full/Quick) gating expensive
   postProcess checks. New API in RiddlLib + JS facade
4. **Phase 3: `IncrementalValidator`** — Context-level
   fingerprinting (FNV-1a 64-bit) with message caching.
   New files: `ContextFingerprint.scala`,
   `IncrementalValidator.scala`
5. **ScalaDoc fixes** — Fixed 9 broken `[[AST.Contents]]`
   links in AST.scala
6. **sbt-ossuminc 1.3.3** — Added `npmPublishLocal` task
   that hooks into `publishLocal`, producing npm `.tgz`
   automatically. Modified `NpmPackaging.scala` in
   sbt-ossuminc repo
7. **Build upgrades** — sbt 1.12.1, sbt-ossuminc 1.3.3
8. **Released 1.11.0** — Tagged, all tests pass, published
   all modules (JVM, JS, Native) to GitHub Packages

**Key Technical Notes**:
- `Contents[?]` opaque type extensions need concrete type
  parameters, wildcards don't work
- `AST.Set` shadows `scala.collection.immutable.Set` with
  wildcard imports — use selective imports
- FNV-1a 64-bit hash chosen for cross-platform compat (no
  `java.security.MessageDigest` in Scala.js)

**Files Created**:
- `passes/shared/.../ContextFingerprint.scala`
- `passes/shared/.../IncrementalValidator.scala`

**Files Modified**:
- `language/shared/.../AST.scala` — ParentStack class +
  ScalaDoc fixes
- `language/shared/.../Finder.scala` — recursive cache
- `passes/shared/.../Pass.scala` — `quickValidationPasses`
- `passes/shared/.../validate/ValidationPass.scala` —
  ValidationMode, walkStatements, optimizations
- `passes/jvm-native/.../PassTest.scala` — ParentStack API
- `riddlLib/shared/.../RiddlLib.scala` — new API methods
- `riddlLib/js/.../RiddlAPI.scala` — JS facades
- `riddlLib/js/types/index.d.ts` — TS declarations
- `project/plugins.sbt` — sbt-ossuminc 1.3.3
- `project/build.properties` — sbt 1.12.1

---

### February 14, 2026 (Release 1.10.2 — Unbastify Bug Fixes)

**Focus**: Fix two PrettifyPass bugs from unbastify round-trip
testing, then release 1.10.2.

**Work Completed**:
1. **Fixed `on pther` typo** — `OnOtherClause.id` in AST.scala
   had `s"pther"` instead of `s"other"`, causing `on pther is`
   output in unbastified RIDDL
2. **Fixed missing `|` on description continuation lines** —
   `emitDescription()` in RiddlFileEmitter.scala now splits
   `LiteralString.s` on embedded newlines and emits each
   fragment with its own `|` prefix
3. **Released 1.10.2** — Tagged, published all modules (JVM, JS,
   Native) to GitHub Packages, created GitHub release
4. **Fixed homebrew dispatch secret mismatch** — `release.yml`
   referenced `HOMEBREW_TAP_TOKEN` but the repo secret is named
   `HOMEBREW_TAP_SECRET`. Manually dispatched to homebrew-tap
   to update formula for 1.10.2

**Release Process Learnings**:
- Dirty working tree causes sbt-dynver to produce snapshot
  versions even when on a tag — must `git stash` before publish
- Secret names matter: always verify with `gh secret list`

**Test Results**: All tests pass across all modules.

**Files Modified**:
- `language/shared/.../AST.scala` — `pther` → `other`
- `passes/shared/.../prettify/RiddlFileEmitter.scala` — split
  multiline descriptions
- `.github/workflows/release.yml` — secret name fix

---

### February 14, 2026 (Homebrew Dispatch Automation)

**Focus**: Fix `update-homebrew` job in `release.yml` which
failed with 404 because `GITHUB_TOKEN` can't access cross-repo
`ossuminc/homebrew-tap`.

**Work Completed**:
1. **Replaced `update-homebrew` job** in `release.yml` — removed
   direct checkout/push to homebrew-tap, replaced with
   `peter-evans/repository-dispatch@v3` sending `update-formula`
   event with version + SHA256 hashes. Uses `HOMEBREW_TAP_TOKEN`
   (fine-grained PAT scoped to homebrew-tap, Contents read/write)
2. **Created `update-formula.yml`** in `homebrew-tap` — triggered
   by `repository_dispatch`, extracts payload, generates formula
   via `envsubst` + quoted heredoc, commits and pushes
3. **Fixed heredoc expansion bug** — unquoted `<< FORMULA` caused
   bash to expand `$@` and Ruby `#{...}` interpolations. Fixed
   with `<< 'FORMULA'` + `envsubst '$TAG $SHA_MACOS_ARM64 ...'`
4. **End-to-end test** — manually dispatched with 1.10.1 hashes,
   confirmed formula updated correctly in homebrew-tap

**PAT Setup**: Fine-grained token `homebrew-tap-dispatch` scoped
to `ossuminc/homebrew-tap` with Contents read/write. Added as
`HOMEBREW_TAP_TOKEN` in riddl repo secrets.

**Files Modified**:
- `.github/workflows/release.yml` (riddl)

**Cross-project files**:
- `homebrew-tap/.github/workflows/update-formula.yml` (created)

---

### February 14, 2026 (PrettifyPass Bug Fixes & Release 1.10.1)

**Focus**: Fix 6 PrettifyPass bugs preventing RIDDL round-trip
via `riddlc unbastify`, then release.

**Work Completed**:
1. **Bug A: Interactions unimplemented** — Implemented all 12
   interaction types in `doInteraction()` (was all TODO stubs).
   Container types (sequential/parallel/optional) recurse via
   `emitInteractionContents()`. Step types emit parser-correct
   syntax (`step send`, `step focus`, `step for`, etc.)
2. **Bug B: Saga requires/returns dropped** — `openSaga()` now
   emits `requires`/`returns` clauses following `openFunction()`
   pattern
3. **Bug C: SagaStep double-braced** — Changed to
   `openDef(sagaStep, withBrace = false)` so `emitCodeBlock`
   handles the brace, not both
4. **Bug D: Currency parameter lost** — Added specific
   `Currency(country)` case before generic `PredefinedType` in
   `emitTypeExpression`. Changed generic from `p.kind` to
   `p.format`
5. **Bug E: Author metadata dropped** — Added
   `emitMetaData(author.metadata)` call in `doAuthor()`
6. **Bug F: UseCase metadata dropped** — Added
   `emitMetaData(useCase.metadata)` call in `closeUseCase()`
7. **Released 1.10.1** — Tagged, published all modules (JVM, JS,
   Native) to GitHub Packages, created GitHub release, merged
   back to development

**Release Process Learnings**:
- `unset GITHUB_TOKEN` is only for `gh` commands, NOT for sbt
  (sbt needs it for GitHub Packages resolution)
- Always `git pull` before merging when switching branches
- When recommending semver versions, check ALL tags first
  (`git tag --sort=-v:refname`) and present a recommendation

**Test Results**: 270 passes tests, all pass.

**Files Modified**:
- `passes/shared/.../prettify/PrettifyVisitor.scala` — bugs
  A, B, C, E, F
- `passes/shared/.../prettify/RiddlFileEmitter.scala` — bug D

---

### February 14, 2026 (CI Fixes & Insights)

**Focus**: Fix pre-existing CI failures, apply insights
suggestions to CLAUDE.md files, create `/release` skill.

**Work Completed**:
1. **Fixed Dockerfile** — `Dep.scala` → `Dependencies.scala`
   (file was renamed but Dockerfile never updated)
2. **Dropped `macos-13` x86_64 from release.yml** — GitHub
   deprecated `macos-13` runners. Removed matrix entry, SHA
   computation, and updated generated Homebrew formula to
   match actual `homebrew-tap` structure (macOS ARM64 native,
   Linux x86_64 native, JVM fallback for everything else)
3. **Applied insights suggestions to `../CLAUDE.md`** (general,
   all ossuminc projects):
   - Scope discipline (don't expand without asking)
   - `unset GITHUB_TOKEN` before `gh` commands
   - Publish from `main` only
   - Dependency locality (prefer local packages)
   - Single-purpose commits
   - Post-commit verification (`git status` after commit)
4. **Updated `./CLAUDE.md`** — notes #30 and #31 for dropped
   macOS x86_64 target
5. **Created `/release` skill** at
   `.claude/skills/release/SKILL.md` — encodes full release
   workflow with pre-flight checks, publish steps, and
   post-release verification
6. **Un-ignored `.claude/skills/`** in `.gitignore` so skills
   are tracked in git (other `.claude/` contents stay ignored)

**CI Failure Analysis**:
- Docker: Fixed (Dockerfile)
- Release Artifacts: Fixed (dropped `macos-13`)
- RiddlModelsRoundTripTest: Known chicken-egg problem — needs
  .bast files regenerated with 1.10.0 in riddl-models. Not
  actionable from this repo.

**Files Created**:
- `.claude/skills/release/SKILL.md`

**Files Modified**:
- `Dockerfile`
- `.github/workflows/release.yml`
- `.gitignore`
- `CLAUDE.md`

**Cross-project**:
- `../CLAUDE.md` — workflow discipline rules

---

### February 16, 2026 (Scala.js Validation Performance)

**Focus**: Four-phase optimization to make validation practical
for large RIDDL models in Scala.js. The ossum.ai Playground's
`validateString()` took 168s for reactive-bbq (8,105 lines);
even `getTree()` took 152s due to framework-level overhead.

**Root Cause**: `ParentStack.toParents` (AST.scala) called
`mutable.Stack.toSeq` on every AST node visit — O(N*D)
allocations where N=nodes, D=average depth.

**Work Completed**:
1. **Phase 0: ParentStack caching** — Replaced
   `type ParentStack = mutable.Stack[Branch[?]]` with a
   `final class ParentStack` that caches the `toSeq` result,
   invalidating only on push/pop. Same API surface. Expected
   50-70% speedup on all pass traversals.
2. **Phase 1: ValidationPass micro-optimizations** — Added
   `recursiveFindByTypeCache` to Finder (mirrors existing
   `findByTypeCache`). Added `walkStatements[CV]` helper for
   recursive statement walking. Replaced `classifyHandlers`
   with single-pass mutable counter walk. Replaced
   `validateSagaStep` 4×recursiveFindByType with 2 walks
   using mutable Sets.
3. **Phase 2: `validateStringQuick()`** — Added
   `ValidationMode` enum (Full/Quick). Quick mode skips
   `checkStreaming()` and `classifyHandlers()` in postProcess.
   Added `quickValidationPasses` to Pass companion.
   Exposed via RiddlLib trait, RiddlAPI JS facade, and
   TypeScript declarations.
4. **Phase 3: Incremental validation** — Created
   `ContextFingerprint` (FNV-1a 64-bit hashing of Context
   source spans, cross-platform). Created
   `IncrementalValidator` — stateful validator that caches
   messages per-Context, re-validates only changed Contexts.
   Conservative: falls back to full validation when >50%
   changed. Exposed via `createIncrementalValidator()` and
   `validateIncremental()` in RiddlLib/RiddlAPI/TypeScript.

**Also fixed**: Pre-existing infix method warnings in
PassTest.scala (`must be(x)` → `.must(be(x))`), updated
`mutable.Stack.empty` → `ParentStack.empty` in test code.

**Test Results**: 800 tests across language (280), passes
(270), riddlLib (12), commands (238) — all passing on JVM.
Full `sbt test` (JVM + JS + Native) passes.

**Files Created**:
- `passes/shared/.../ContextFingerprint.scala`
- `passes/shared/.../IncrementalValidator.scala`

**Files Modified**:
- `language/shared/.../AST.scala` — ParentStack class
- `language/shared/.../Finder.scala` — recursiveFindByType
  cache
- `passes/shared/.../Pass.scala` — quickValidationPasses
- `passes/shared/.../validate/ValidationPass.scala` —
  ValidationMode, walkStatements, optimized classifyHandlers
  and validateSagaStep
- `passes/jvm-native/.../PassTest.scala` — ParentStack.empty,
  infix fixes
- `riddlLib/shared/.../RiddlLib.scala` — validateStringQuick,
  createIncrementalValidator, validateIncremental, doValidate
  refactor
- `riddlLib/js/.../RiddlAPI.scala` — JS facades
- `riddlLib/js/types/index.d.ts` — TypeScript declarations

---

### February 14, 2026 (RiddlResult ADT)

**Focus**: Add cross-platform `RiddlResult[T]` result type to
replace ad-hoc `Either[Messages, T]` returns in RiddlLib.

**Work Completed**:
1. **Created `RiddlResult.scala`** — Sealed trait with
   `Success[T]` and `Failure` cases. Includes `map`,
   `flatMap`, `toEither`, `fromEither` for interop.
2. **Updated `RiddlLib.scala`** — Changed all 10 methods
   returning `Either[Messages, T]` to `RiddlResult[T]`.
   Changed `ast2bast` from `Array[Byte]` to
   `RiddlResult[Array[Byte]]` (surfaces errors properly).
3. **Updated `RiddlAPI.scala`** — `toJsResult` now accepts
   `RiddlResult[T]`. `ast2bast` wrapped in `toJsResult`.
4. **Updated `index.d.ts`** — `ParseResult<T>` renamed to
   `RiddlResult<T>`. Deprecated alias kept. `ast2bast`
   return type changed to `RiddlResult<Int8Array>`.
5. **Updated `RiddlLibTest.scala`** — All tests use
   `RiddlResult.Success`/`RiddlResult.Failure` matching.

**Test Results**: 13/13 JVM, 11/11 JS — all pass.

**Version**: 1.9.0 released with only RiddlResult changes.
Corrected to 1.10.0 to include BAST fixes that were
mistakenly excluded from 1.9.0.

**Files Created**:
- `riddlLib/shared/.../RiddlResult.scala`

**Files Modified**:
- `riddlLib/shared/.../RiddlLib.scala`
- `riddlLib/js/.../RiddlAPI.scala`
- `riddlLib/js/types/index.d.ts`
- `riddlLib/shared/test/.../RiddlLibTest.scala`

---

### February 11, 2026 (ValidationPass Gap Analysis Completion)

**Focus**: Implement remaining 4 items from the Feb 7 gap
analysis. Three items were already complete (saga compensation
symmetry, entity FSM morph/become, context isolation). This
session implements the remaining four.

**Work Completed**:
1. **Schema kind-specific deep checks** — Extended
   `validateSchema()` with structural validation: Flat warns
   if >1 data node; TimeSeries warns if no indices;
   Hierarchical and Star warn if multiple data nodes but no
   links. Relational FK type mismatch upgraded from Warning
   to Error (a genuine type error, not a style issue).
2. **Handler message type vs container type** — Extended
   `validateHandler()` with Repository and Projector cases.
   Repository handler that handles Events gets a warning
   (repos should handle commands/queries). Projector handler
   that handles Commands/Queries gets a warning (projectors
   should handle events).
3. **Adaptor message direction compatibility** — Added
   direction-specific checks after the existing
   `referencesTargetType` check. Inbound adaptors handling
   Commands/Queries from target context → Error. Outbound
   adaptors handling Events/Results from target context →
   Error.
4. **Streaming sink reachability** — Added reverse BFS in
   `checkStreamingUsage()`. For each connected sink, traces
   upstream via reverse adjacency map. Warns if no path from
   any source reaches the sink.

**Test Cases Created**: 4 new test directories in
`passes/input/check/` with .riddl and .check files covering
26 individual validation messages:
- `schema-kinds/` — 11 messages: flat multi-node, time-series
  no indices, hierarchical/star no links, plus descriptions
  and unused types
- `handler-types/` — 8 messages: repo handles events, proj
  handles commands, unused types, schema description
- `adaptor-direction/` — 3 messages: inbound handles command,
  outbound handles event, command should send event
- `sink-reach/` — 4 messages: sink no upstream source, sink
  no handler, flow no handler, inlet not connected

**Key fixes during test creation**:
- Schema parser expects `time-series` (hyphenated) not
  `timeseries`; consecutive schemas need `with { ... }` blocks
  as terminators for the greedy `data.rep(1)` parser
- Adaptor cross-context resolution fixed: use parent-
  independent `definitionOf[Type](pathId)` instead of parent-
  keyed variant (pre-existing bug)

**Test Results**: 268 passes tests (4 new), 280 language
tests, 49 commands tests, 10 riddlLib tests — all passing.

**Release 1.8.0**: Tagged, published, GitHub release created.

**Files Modified**:
- `passes/shared/.../validate/ValidationPass.scala` — schema,
  handler, adaptor changes
- `passes/shared/.../validate/StreamingValidation.scala` —
  sink reachability BFS
- `passes/jvm-native/.../CheckMessagesTest.scala` — 4 new
  test registrations

**Files Created**:
- `passes/input/check/schema-kinds/` — .riddl + .check
- `passes/input/check/handler-types/` — .riddl + .check
- `passes/input/check/adaptor-direction/` — .riddl + .check
- `passes/input/check/sink-reach/` — .riddl + .check

**Gap Analysis Status**: All 7 items from the Feb 7 analysis
are now complete.

---

### February 9, 2026 (Housekeeping & ast2bast)

**Focus**: Clean up work queue, implement ast2bast, update
TypeScript declarations, create consumer migration notes.

**Work Completed**:
1. **Deleted `DIAGRAMS-PASS-EXTENSIONS.md`** — confirmed R1
   (DataFlowDiagramData) and R3 (DomainDiagramData) are fully
   implemented in DiagramsPass.scala. R2 is a riddl-gen change.
2. **Created consumer migration notes** — wrote
   `RIDDL-UPDATE-NOTES.md` in synapify, riddl-mcp-server, and
   ossum.ai covering the 1.5.0 breaking change (opaque Root)
   and 1.7.0 new API functions.
3. **Added TypeScript declarations** — `index.d.ts` updated
   with `getHandlerCompleteness`, `getMessageFlow`,
   `getEntityLifecycles`, `ast2bast` method declarations plus
   `HandlerCompletenessEntry`, `MessageFlowResult`,
   `MessageFlowEdge`, `EntityLifecycleEntry`,
   `StateTransitionEntry` interfaces.
4. **Implemented `RiddlLib.ast2bast`** — added to shared trait,
   companion object (delegates to BASTWriterPass), and JS
   facade (returns Int8Array). Test verifies BAST magic header.
5. **Updated NOTEBOOK.md** — marked items #1 and #3 done,
   updated #2 status, added session log.

**Test Results**: `sbt riddlLib/test` — 10 tests passed,
0 failures, 1 canceled (ESMSafetyTest skipped, expected).

**Files Created**:
- `../synapify/RIDDL-UPDATE-NOTES.md`
- `../riddl-mcp-server/RIDDL-UPDATE-NOTES.md`
- `../ossum.ai/RIDDL-UPDATE-NOTES.md`

**Files Modified**:
- `riddlLib/shared/.../RiddlLib.scala` — ast2bast trait + impl
- `riddlLib/js/.../RiddlAPI.scala` — ast2bast JS facade
- `riddlLib/shared/test/.../RiddlLibTest.scala` — ast2bast test
- `riddlLib/js/types/index.d.ts` — new method declarations

**Files Deleted**:
- `DIAGRAMS-PASS-EXTENSIONS.md`

---

### February 8, 2026 (Release 1.7.0)

**Focus**: Commit, CI verify, and release all 1.7.0 enhancements.

**Work Completed**:
1. Committed all uncommitted changes in 7 cohesive batches:
   - Analysis passes (3 new files)
   - ValidationPass enhancements (6 files)
   - DiagramsPass extensions + test (2 files)
   - StatementParser cleanup — IntelliJ IDEA warnings (1 file)
   - StatsPass behavioral statistics (1 file)
   - RiddlLib + RiddlAPI analysis API (2 files)
   - CLAUDE.md + NOTEBOOK.md documentation (2 files)
2. Pushed development, created PR #720, admin-merged to main
3. Monitored CI — Scala Build (JVM/JS/Native) and Coverage
   both green
4. Tagged `1.7.0`, ran `sbt clean test publish` (all passed),
   created GitHub release
5. Reviewed downstream `RIDDL-INTEGRATION-PLAN.md` files —
   already comprehensively cover 1.7.0 features (written
   during same session, reference "1.6.0" but content matches
   1.7.0)

**Learnings captured in CLAUDE.md**:
- `unset GITHUB_TOKEN` before `gh` commands for local auth
- `gh pr merge --admin` for branch-protected merges

**Status**: 1.7.0 released. On `development` branch. Clean.
Untracked `DIAGRAMS-PASS-EXTENSIONS.md` left as-is (design doc).

---

### February 8, 2026 (Library Enhancements for Simulator & Generator)

**Focus**: Complete 5-phase implementation plan for RIDDL library
enhancements. Phases 1-3 completed in previous session (context
ran out). This session completed Phases 4-5 and deliverables.

**Work Completed (this session)**:
1. **C1-C5: Options vocabulary and validation** — Implemented
   `validateRecognizedOption` method in `DefinitionValidation.scala`.
   Validates option name recognition, argument count (min/max arity),
   and parent definition type compatibility. Updated `everything.check`
   and `saga.check` with expected StyleWarning messages for
   unrecognized `transient` and `parallel` options.
2. **D1: RiddlLib API extensions** — Added `getHandlerCompleteness`,
   `getMessageFlow`, `getEntityLifecycles` methods to `RiddlLib`
   trait (shared) and `RiddlAPI` (JS facade). Each parses source,
   runs appropriate pass pipeline, converts to typed/JS results.
3. **E1-E4: Downstream integration plans** — Wrote
   `RIDDL-INTEGRATION-PLAN.md` to all 4 downstream project repos.
4. **Full clean build verification** — `sbt clean cJVM` + `sbt tJVM`:
   734 tests, 0 failures across all modules.

**Work Completed (previous session, before context ran out)**:
- A1: Handler completeness in ValidationPass
- A2: Behavioral statistics in StatsPass
- B1: MessageFlowPass (new file)
- B2: EntityLifecyclePass (new file)
- A3: DiagramsPass extensions (DataFlowDiagramData, DomainDiagramData)
- B3: DependencyAnalysisPass (new file)
- C1-C5 partial: OptionSpec/RecognizedOptions added, method stub placed

**Test verification**: `sbt clean test` (all platforms) exited 0.
`sbt test` (incremental, all platforms) also exited 0. Reported
1,526 tests passed, 0 failures. However, the 2-minute wall time
for an all-platform run including Native linking seems
suspiciously fast — verify independently before releasing.

---

### February 7, 2026 (ValidationPass Gap Analysis & Enhancement)

**Focus**: Comprehensive audit and enhancement of ValidationPass
to catch missing validations and fix bugs.

**Work Completed**:
1. **Fixed 3 bugs**:
   - SagaStep checked `doStatements` twice instead of
     `undoStatements` for revert validation
   - SagaStep shape check compared `getClass` of two
     `Contents[Statements]` (always true) — replaced with
     meaningful `nonEmpty` symmetry check
   - `validateState` called `checkMetadata` twice — removed
     duplicate
2. **Added validation for 2 previously unvalidated definitions**:
   - `Schema` — kind vs structure compatibility (flat/document/
     columnar/vector shouldn't have links; graphical should;
     vector expects single data node), data TypeRef resolution,
     link FieldRef resolution, index FieldRef resolution
   - `Relationship` — processor ref resolution, identifier
     length, metadata checks
3. **Added 6 semantic validations**:
   - Streamlet shape vs inlet/outlet count (guarded by
     `nonEmpty` to skip placeholders)
   - Streamlet handler requirement
   - Adaptor handler requirement + empty handler warning
   - Repository handler requirement
   - Projector repository ref resolution
   - Epic/UseCase user story user ref resolution
4. **Added Function input/output type validation** via
   `checkTypeExpression` on `input`/`output` Aggregations
5. **Updated 3 `.check` test expectation files** to reflect
   new validation messages (everything, saga, streaming)

**Test Results**: `sbt clean test` — 1,526 tests, 0 failures
across all modules (JVM, JS, Native).

**Commit**: `6d6185e5` — pushed to `origin/development`

**Files Modified**:
- `passes/shared/.../validate/ValidationPass.scala` — all
  validation enhancements
- `passes/input/check/everything/everything.check` — 12 new
  expected messages
- `passes/input/check/saga/saga.check` — 5 new expected
  messages
- `passes/input/check/streaming/streaming.check` — 3 new
  expected messages

**Remaining from gap analysis** — ALL COMPLETE as of
February 11, 2026:
- ✅ Schema kind-specific deep checks (Feb 11)
- ✅ Adaptor message direction compatibility (Feb 11)
- ✅ Saga compensation symmetry hints (Feb 8, v1.7.0)
- ✅ Entity FSM morph/become statement presence (Feb 8)
- ✅ Streaming sink reachability (Feb 11)
- ✅ Handler message type vs container type (Feb 11)
- ✅ Context isolation warnings (Feb 8, v1.7.0)

---

### February 5, 2026 (RiddlLib Shared Trait, JS Test Fix)

**Focus**: Extract shared RiddlLib trait from JS-only RiddlAPI,
fix riddlLibJS test runner crash.

**Work Completed**:
1. Created `RiddlLib.scala` in `riddlLib/shared/` — trait +
   companion object with cross-platform implementations of
   `parseString`, `parseNebula`, `parseToTokens`, `flattenAST`,
   `validateString`, `getOutline`, `getTree`, `version`,
   `formatInfo`. All methods use `(using PlatformContext)`.
2. Refactored `RiddlAPI.scala` (610 → ~340 lines) — now a thin
   JS facade delegating to `RiddlLib`. Added `getDomains(root)`
   and `inspectRoot(root)` accessors for opaque Root handle.
   `parseString` returns actual Scala Root (not plain JS object).
3. Updated `index.d.ts` — `RootAST` is now opaque branded type,
   added `RootInfo` interface, `flattenAST`, `getDomains`,
   `inspectRoot` declarations.
4. Created `RiddlLibTest.scala` in shared test (8 tests, runs on
   JVM and JS).
5. Un-pended `FlattenPassTest.scala` — passes (3 wrappers before
   flatten, 0 after).
6. Fixed `riddlLibJS/test` crash — overrode `Test /
   scalaJSLinkerConfig` to `CommonJSModule` in `build.sbt`.
   Production bundle stays ESModule. Pre-existing issue since
   ESModule conversion.

**Test Results**: 1,527 tests across all modules, 0 failures.
`riddlLibJS/test` now runs 8 tests successfully (was crashing).

**Breaking Change**: `parseString` returns opaque Root. TypeScript
consumers must use `getDomains()`/`inspectRoot()`.

**Release 1.5.0** (February 6, 2026):
- Merged development → main, tagged 1.5.0
- `sbt clean test` — 1,527 tests, 0 failures
- JS bundle built, ESMSafetyTest clean pass (5.3MB scanned)
- `sbt publish` — all modules to GitHub Packages (Maven)
- `gh release create 1.5.0` — triggers release.yml for native
  builds and homebrew-tap update
- `@ossuminc/riddl-lib@1.5.0` published to npm (GitHub Packages)

**Commits**:
- `407aebdb` — Extract shared RiddlLib trait and fix riddlLibJS
  test crash
- `28c299b5` — Update CLAUDE.md and NOTEBOOK.md for RiddlLib
  shared trait

**Files Created**:
- `riddlLib/shared/src/main/scala/.../RiddlLib.scala`
- `riddlLib/shared/src/test/scala/.../RiddlLibTest.scala`

**Files Modified**:
- `build.sbt` — Test linker config override for riddlLibJS
- `riddlLib/js/src/main/scala/.../RiddlAPI.scala` — thin facade
- `riddlLib/js/types/index.d.ts` — opaque RootAST, new methods
- `riddlLib/jvm/src/test/scala/.../FlattenPassTest.scala` —
  un-pended

**Consumers to update** (parseString breaking change):
- synapify, riddl-mcp-server, ossum.ai

---

### February 5, 2026 (FlattenPass, Release 1.4.0)

**Focus**: Implement FlattenPass per FLATTEN_PASS_SPEC.md for
Synapify, add multi-platform release CI.

**Work Completed**:
1. Added `clear()` and `remove()` extension methods to
   `Contents[CV]` opaque type
2. Added `flatten()` extension method on `Container[CV]` in the
   `language` module — recursively removes Include/BASTImport
   wrappers, promoting children to parent container. Available
   without `passes` dependency.
3. Rewrote `FlattenPass` to use base `Pass` (not `DepthFirstPass`)
   with no-op `process()`, delegating to `root.flatten()` in
   `result()`. Old implementation was buggy (computed new contents
   via splitAt/merge but never assigned back; also unsafe mutation
   during DepthFirstPass traversal).
4. Created `FlattenPassTest` with 10 tests covering all spec
   scenarios: single Include, single BASTImport, nested includes,
   mixed nodes, ordering preservation, deep nesting, empty nodes,
   accessor visibility after flatten, Nebula support.
5. Added Backward Compatibility Policy to CLAUDE.md — no breaking
   changes without deprecation through current major version.
6. Released 1.4.0: tagged, clean test, published to GitHub Packages,
   created GitHub release with JVM + native ARM64 artifacts.
7. Updated homebrew-tap formula: native macOS ARM64 primary (no JDK
   needed), JVM fallback for other platforms.
8. Created `release.yml` workflow for automated multi-platform
   builds: macOS ARM64, macOS x86_64, Linux x86_64, plus JVM.
   Auto-updates homebrew formula with SHA256 hashes on release.

**Design Decisions**:
- `flatten()` lives in `language` module as Container extension
  (not in `passes`) so any consumer can use it without the passes
  dependency
- Base `Pass` instead of `DepthFirstPass` to avoid mutating
  contents during active ArrayBuffer iteration
- Type parameter `Container[CV <: RiddlValue]` provides compile-
  time constraint while still requiring runtime cast for mixed-
  type Include/BASTImport contents

**Version Bump Rationale**: 1.3.1 → 1.4.0 (MINOR) — new public
API (`Container.flatten()` extension, `Contents.clear()`,
`Contents.remove()`), no breaking changes.

**Commits**:
- `95608fcf` — Add Container.flatten() extension and rewrite
  FlattenPass
- `a79f7a5a` — Add multi-platform release workflow

**Files Created**:
- `passes/shared/.../transforms/FlattenPassTest.scala`
- `.github/workflows/release.yml`

**Files Modified**:
- `language/shared/.../Contents.scala` — clear, remove, flatten
- `passes/shared/.../transforms/FlattenPass.scala` — rewritten
- `CLAUDE.md` — backward compatibility policy

**Cross-project**: `../homebrew-tap/Formula/riddlc.rb` → 1.4.0
with native ARM64 support

---

### February 5, 2026 (ESM Shim Fix, Release 1.3.1)

**Focus**: Fix string literals that trigger ESM shim plugin
rewriting in downstream JS projects, add regression test.

**Problem**: ESM shim plugins (Vite esmShimPlugin) scan JS bundles
for `import '…`, `import "…`, and `import(…` patterns and rewrite
them. String literals in shared Scala source containing these
patterns ended up in the riddl-lib JS bundle, corrupting downstream
builds.

**Work Completed**:
1. Found 7 occurrences across 4 shared-source files:
   - `AST.scala` (2, HIGH risk — exact ES import syntax)
   - `BASTLoader.scala` (1, MED)
   - `ValidationPass.scala` (2, LOW-MED)
   - `TopLevelParser.scala` (2, LOW)
2. Fixed all: split keyword via `val imp = "im" + "port"` for
   format output; rephrased error messages to use "BAST file"
   or "BAST load" instead of "BAST import"
3. Added explanatory comments at each location warning future
   developers not to reintroduce the pattern
4. Created `ESMSafetyTest` (`riddlLib/jvm/src/test/`) that:
   - Locates fullLinkJS output via glob (Scala-version agnostic)
   - Scans the 5MB JS bundle for dangerous patterns
   - Reports file path + size when scanning (distinguishes from
     skip)
   - Skips gracefully via `assume` if bundle not built
5. Released 1.3.1 — tagged, clean build, all tests pass, published
   to GitHub Packages, GitHub release created, homebrew-tap updated

**Commits**:
- `1272df2d` — Fix string literals that trigger ESM shim rewriting
- `330b6593` — Add ESM safety regression test for JS bundle

**Files Created**:
- `riddlLib/jvm/src/test/scala/.../ESMSafetyTest.scala`

**Files Modified**:
- `language/shared/.../AST.scala` — split `import` keyword
- `language/shared/.../bast/BASTLoader.scala` — rephrase message
- `language/shared/.../parsing/TopLevelParser.scala` — rephrase
- `passes/shared/.../validate/ValidationPass.scala` — rephrase

**Cross-project**: `../homebrew-tap/Formula/riddlc.rb` → 1.3.1

---

### February 5, 2026 (OutlinePass, TreePass, Release 1.3.0)

**Focus**: Implement getOutline/getTree RiddlAPI methods and release
1.3.0.

**Work Completed**:
1. Created `OutlinePass.scala` — HierarchyPass that produces a flat
   `Seq[OutlineEntry]` with kind, id, depth, line, col, offset for
   every named definition
2. Created `TreePass.scala` — HierarchyPass that produces a recursive
   `Seq[TreeNode]` with children reflecting the definition hierarchy
3. Added `getOutline()` and `getTree()` `@JSExport` methods to
   `RiddlAPI.scala` following existing parse-then-run-pass pattern
4. Added `OutlineEntry` and `TreeNode` TypeScript interfaces plus
   method declarations to `index.d.ts`
5. Tagged 1.3.0, clean build & test (all tests pass), published all
   modules to GitHub Packages
6. Created GitHub release with riddlc.zip asset
7. Updated homebrew-tap formula to 1.3.0

**Version Bump Rationale**: 1.2.3 → 1.3.0 (MINOR) — new features
(OutlinePass, TreePass, npm packaging) with no breaking changes.

**Commits**:
- `0b1e704c` — Add getOutline and getTree methods to RiddlAPI
- `9767b0c9` — Update CLAUDE.md and NOTEBOOK.md

**Files Created**:
- `passes/shared/.../passes/OutlinePass.scala`
- `passes/shared/.../passes/TreePass.scala`

**Files Modified**:
- `riddlLib/js/.../RiddlAPI.scala` — 2 new @JSExport methods
- `riddlLib/js/types/index.d.ts` — OutlineEntry, TreeNode interfaces

**Cross-project**: `../homebrew-tap/Formula/riddlc.rb` → 1.3.0

---

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

## Scala.js Validation Performance (from ossum.ai playground)

**Filed:** 2026-02-16
**Context:** The ossum.ai RIDDL Playground loads models from
riddl-models via `.bast` files, converts them to source with
`root2RiddlSource()`, and optionally validates with
`validateString()`. For the `reactive-bbq` model (8,105 lines /
213KB after flattening), `validateString()` takes **168 seconds**
in the browser (Scala.js) vs seconds on JVM. This makes
interactive validation impractical for large models.

### Benchmark (reactive-bbq, Chrome/V8)

| Operation | Time |
|-----------|------|
| `bast2FlatAST()` | 57ms |
| `root2RiddlSource()` | 12ms |
| `parseString()` | 24ms |
| `getTree()` | 152,714ms |
| `validateString()` | 168,673ms |

**Note:** `getTree()` runs only `TreePass` (not the full
validation pipeline), yet still takes 2.5 minutes. This is
unexpectedly slow and warrants separate investigation — the
tree pass should be lightweight.

### Identified Bottlenecks in ValidationPass

#### 1. Double iteration in `classifyHandlers()`

**Location:** `ValidationPass.scala` ~lines 1260-1299

For every handler, `recursiveFindByType[Statement]` collects
all statements, then the list is iterated **twice** — once
with `.count()` for executable statements, once with
`.count()` for prompt statements. A single-pass accumulator
would halve this work.

**Fix:** Replace two `.count()` calls with one `.foreach`
that increments both counters:

```scala
var executableCount = 0
var promptCount = 0
allStatements.foreach {
  case _: TellStatement | _: SendStatement |
       _: MorphStatement | _: SetStatement |
       _: BecomeStatement | _: ErrorStatement |
       _: CodeStatement =>
    executableCount += 1
  case _: PromptStatement =>
    promptCount += 1
  case _ => ()
}
```

**Estimated impact:** ~15-20% of postProcess time

#### 2. Redundant `symbols.parentsOf()` in streaming

**Location:** `StreamingValidation.scala` ~line 44

`symbols.parentsOf(connector)` is called for every connector
without caching. Additionally, the reverse adjacency map is
built in a separate pass instead of simultaneously with the
forward adjacency, and two full BFS traversals run (sources→
sinks, then sinks→sources) when one bidirectional pass would
suffice.

**Fix:** Cache `parentsOf()` results in a local map. Build
forward and reverse adjacency simultaneously.

**Estimated impact:** ~10-15%

#### 3. Four tree walks per SagaStep

**Location:** `ValidationPass.scala` ~lines 976-1010

Each SagaStep creates two Finders (do/undo), walks each
**twice** (once for `TellStatement`, once for
`SendStatement`), creates intermediate `Seq`s, converts to
`Set`s. Could be 2 walks instead of 4 by collecting both
statement types in a single pass.

**Estimated impact:** ~8-12%

#### 4. Finder cache is per-instance

**Location:** `Finder.scala` ~lines 27-64

Each `Finder(container.contents)` creates a fresh
`findByTypeCache`. Across hundreds of handlers creating new
Finders, no cache reuse occurs. A shared cache keyed by
container identity could help.

### Micro-optimization Summary

These fixes sum to roughly **20-30% improvement** (~30-50s
off the 3-minute Scala.js runtime). Meaningful but doesn't
solve the fundamental 10-50x Scala.js vs JVM gap for
allocation-heavy compute.

### Architectural Approaches (Higher Impact)

1. **Pre-compute validation results into BAST** — Store
   validation messages alongside `.bast` files at build time.
   The playground would just display them with zero compute.
   This is the highest-impact, lowest-risk change.

2. **Incremental validation** — Only re-validate the changed
   definition and its dependents, not the entire model. This
   is how language servers (LSP) stay fast. High effort but
   would make the playground truly interactive for any size
   model.

3. **`validateStringLite()` for JS** — A lighter validation
   pass that skips expensive checks (streaming analysis, saga
   compensation, handler completeness) that matter less in a
   browser playground context. Medium effort, could bring
   large-model validation under 30 seconds.

### Current ossum.ai Workaround

The playground uses a tiered strategy:
- `parseString()` on main thread (~24ms) for every keystroke
- Sources under 10KB: auto-validate in Web Worker on
  keystroke (debounced)
- Sources over 10KB: manual "Validate" button triggers Web
  Worker; page stays responsive while validation runs in
  background
- BAST-loaded models: skip validation entirely (pre-validated)

---

## Git Information

**Branch**: `development`
**Latest release**: 1.10.2 (February 14, 2026)
