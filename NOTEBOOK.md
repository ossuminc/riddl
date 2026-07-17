# Engineering Notebook: RIDDL

Records open work, blockers, and design nuances that future AI
sessions need to know. Release history lives in git tags and
GitHub release notes — don't reproduce it here.

## Incoming Tasks

**At session start**, check the `task/` directory for pending work
requests from other projects. Each `.md` file describes a task
(e.g., a dependency upgrade). Treat unresolved tasks as to-do
items unless already completed (verifiable from this notebook,
CLAUDE.md, or git log). After completing a task, append results
to the task file and note the disposition below.

---

## In-Flight: sbt 2.0 / sbt-ossuminc 2.0 migration

**Branch**: `feature/sbt2-migration` (off `development`).
**Started**: 2026-07-17. **Status**: code changes done through
Phase 5 + docs; **local build NOT yet verified** (Phase 6) —
blocked on sbt-ossuminc **2.0.1**.

### What's done (committed on the branch)
1. **Source restructure** — all 7 cross modules moved from the
   sbtcrossproject `shared/jvm/js/native/jvm-native` tree to the
   projectMatrix flat layout (`src/{main,test}/scala`, `scalajvm`,
   `scalajs`, `scalanative`, `scala-jvm-native`). Pure `git mv`.
   Python EBNF/GBNF validators now at
   `language/src/test/scalajvm/python`.
2. **Meta-build** — sbt 1.12.3→**2.0.2**; plugin 1.4.0→**2.0.1**;
   dropped scala-xml scheme, jsdom dep, bloop, tracked `metals.sbt`;
   `Dependencies.scala` lost the portable-scala import, `V.scala`
   is 3.8.4, `%%%`→`%%`.
3. **build.sbt** — projectMatrix `CrossModule(…, V.scala)`; new
   `pDep(Project)` per-row deps; `jvmNativeSrc(dir)` helper wires
   the `scala-jvm-native` dir onto JVM+Native rows (utils, language,
   passes); removed all tasty-mima blocks + coveralls; fixed the
   `commandsNative = riddlLib_cp.native` bug.
4. **sbt-riddl plugin** — Scala 3 / sbt 2 (`Setting[?]`,
   `PathFinder.get()`; dropped With.Scala2 + `scalaVersion:=2.12.20`).
5. **CI/tooling** — target paths → `<mod>/target/{jvm,js,native}-3`;
   `~/.sbt/1.0`→`~/.sbt/2`; dropped coveralls; release.yml checkouts
   get `fetch-depth:0`+`fetch-tags:true`; sonar/Dockerfile updated.

### BLOCKER — Phase 0 (separate repo)
sbt-ossuminc `CrossModule` needs `.defaultAxes(VirtualAxis.jvm,
VirtualAxis.scalaABIVersion(scalaVersion))` so project IDs stay
clean (`utils`, not `utils3`). Handed off in
`../sbt-ossuminc/task/crossmodule-defaultaxes.md`; needs a
**2.0.1** publishLocal/release. riddl's `plugins.sbt` already pins
2.0.1. **Do this first**, then run Phase 6.

### Phase 6 watch-items (verify empirically once 2.0.1 lands)
- **Project IDs**: `sbt projects` must show clean names (no `3`
  suffix) — confirms the Phase-0 fix took.
- **Per-row target sub-paths** for JS `fullLinkJS`
  (`riddlLib/target/js-3/riddl-lib-opt/main.js`) and Native
  binaries — the plan flagged these as needing a first-build
  confirmation before the CI paths are trustworthy.
- **npm packaging**: `riddlLib/js/package.json.template` and
  `riddlLib/js/types/index.d.ts` were NOT moved. Under projectMatrix
  the JS row base is the module dir (`riddlLib/`), not `riddlLib/js/`,
  so `With.Packaging.npm` may look for these at `riddlLib/…`. Verify
  `riddlLibJS/npmPrepare` and relocate if needed.
- **build.sbt**: dropped `import sbt.Append.{appendSeqImplicit,
  appendSet}` — if a `++=`/`+=` fails to resolve on first reload,
  restore it (name may differ in sbt 2).
- **DocSite / `docsite`** — kept as-is; sbt-paradox has no stable
  sbt 2 build, so DocSite may fail at load. Not aggregated by root.
- **sbt-riddl plugin source** — otherwise-unchanged task API per the
  README; drive any residual sbt.io/Command API errors to zero at
  compile time.
- **Dockerfile** — still installs the sbt 1.10.7 launcher; it should
  honor `build.properties` (2.0.2), but confirm the image builds.

### Known degradations on sbt 2 (accepted)
Coveralls upload, TASTy-MiMa, `sbt-idea-plugin`, and stable
sbt-paradox docs have no sbt 2 builds yet. Regular binary MiMa and
scoverage are retained. Downstream: an sbt-2 `sbt-riddl` requires
consumers to be on sbt 2.

### Local dev note
Move `~/.sbt/1.0/github.sbt` → `~/.sbt/2/github.sbt` before building.

---

## Current Status

**Last Updated**: 2026-07-17

`development` is **ahead of tag 1.23.4** (on `main`) by one
feature: per-message remediation **suggestions** + the
`CommonOptions.provideTips` option, which retires `AIHelperPass`.
Targeted for **1.24.0**. Summary:

- Every `Messages.Message` now carries a `suggestion: String`;
  `Message.format` renders a `Suggestion:` line, and
  `Accumulator.add` strips it unless `provideTips` is on (one
  chokepoint → no default-output / `.check` churn).
- Suggestions authored for **all ~155** validation + resolution
  messages. Catalog: `MESSAGE_SUGGESTIONS.md` (repo root).
- `AIHelperPass` + its two test files **deleted**; `Tip` kind,
  the `advise` command, and `RiddlLib`/`RiddlAPI`
  `analyzeForTips`/`analyzeSourceForTips` kept but re-implemented
  off `provideTips` (`advise` == `validate --provide-tips`;
  analyze* are `@deprecated` for 1.24.0).
- New `--provide-tips` CLI flag + HOCON `provide-tips`.
- New always-on completeness check: a context with entities but
  no repository. Three entity tip-checks (no command/event types,
  unhandled command) are advisory (provideTips-gated).
- Verified: JVM/JS/Native compile; passes 350, language 314,
  commands 232, riddlLib JVM 26 / JS 25 — 0 failures.

Four bug-fix releases preceded this since the May notebook
update: 1.23.1 (path-identifier usage tracking +
duplicate-logging fix), 1.23.3 (EOF-brace crash + EBNF skip
for malformed fixtures; 1.23.2 was a failed partial publish
that never made it to a GitHub release), and 1.23.4 (Prettify
state brace + Native scaladoc disable).

## JSON Input Method — phased roadmap

Branch `feature/json-input-method` (task:
`json-to-ast-input-method.md`). Adds `RiddlLib.parseJson(json,
origin)` + `validateRoot(root)`: a JSON document is mapped onto the
AST correct-by-construction (defaults applied), then validated /
prettified by the existing machinery. JSON is an alternative input
method for programmatic/AI generation. Lives in
`riddlLib/shared/.../json/` (`JsonModel`, `JsonAstBuilder`); uses
upickle 4.0.2 (`%%%`, Native-safe). Schema: `JSON_INPUT.md`.
Coverage ledger: `JSON_COVERAGE.md`.

**Goal:** eventually cover the *entire* RIDDL language,
incrementally; hardest constructs last, none silently dropped. The
ledger + a Phase-9 guard test keep coverage honest.

**Status: all 9 phases complete.** Every RIDDL definition, type
expression, statement, and interaction is expressible in JSON;
`JsonCoverageGuardTest` enforces that the ledger stays complete as
the AST evolves. 28 `JsonInputTest` cases pass on JVM + JS; Native
compiles. Remaining work is review/merge and any follow-on polish
(e.g. extending `metadata` beyond the four primary containers if
desired). Found+fixed a pre-existing prettify bug along the way
(Output double-rendered its verb alias).

| Phase | Scope | Status |
|---|---|---|
| 1 | Core DDD: domain/context/entity/type/field/state/handler/on-clauses/invariant/author/messages + common type exprs + `do` statement | **done** |
| 2 | Remaining type expressions (SI units, time, collections, URI/Blob/…, SpecificRange) + Constant/User/Enumerator values (Method→P3, Term→P9) | **done** |
| 3 | Full statement language + Function + Method | **done** |
| 4 | Adaptor/Streamlet/Inlet/Outlet/Connector/Relationship/Projector/Repository+Schema | **done** |
| 5 | Saga/SagaStep | **done** |
| 6 | Module + deep nesting | **done** |
| 7 | Epic/UseCase + interactions (the hardest tier) | **done** |
| 8 | Group/Input/Output/ContainedGroup | **done** |
| 9 | Metadata (Description/Terms/options/attachments/AuthorRef/Comment) + automated coverage guard test | **done** |

Deferred (out of scope, documented in the ledger): Include /
Import / BAST — file-reference mechanisms incompatible with a
self-contained, no-I/O, Native-safe JSON document.

Decisions: hand-written upickle ReadWriter for the polymorphic
`typeExpression` (cardinality wrapper vs `kind` tag); `Option`
encoded null-or-value via a custom `AttributeTagged` pickler; a
named `Record` maps to a RecordCase aggregate (a real RIDDL
`record`) so `state … of record X` resolves.

## Open Tasks in `task/`

- `upgrade-sbt-riddl-1.15.4.md` — downstream task for
  riddl-models; sbt-riddl in this repo is already past 1.15.4
  via the normal release cadence

Completed task files live in `task/done/` (gitignored, local
hygiene only).

## Active Work Queue

1. **riddl-models validation errors** (handed off) — see
   `../riddl-models/TASK-fix-validation-errors.md`. As of last
   check, 45 of 186 entry points fail `riddlc validate`. Error
   categories: ambiguous path refs, `briefly` outside `with {}`,
   unresolved `EmailAddress`/`Year` types, decimal fractional
   parts. After fixes, add riddl-models EBNF validation to
   `.github/workflows/scala.yml` (mirror the riddl-examples
   pattern).
2. **TypeScript AST declarations** (low priority) — full AST
   hierarchy (Domain, Context, Entity, …) remains opaque on the
   JS side. Public RiddlAPI methods are declared. JS consumers
   are expected to use the facade, not the raw AST.

## Blocked

(none)

## Scheduled

| Date     | Task |
|----------|------|
| Nov 2026 | Upgrade CodeQL Action v3 → v4. `.github/workflows/scala.yml` line 182: `github/codeql-action/upload-sarif@v3` → `@v4`. GitHub deprecates v3 in December 2026. |

---

## Design Nuances (for future work)

### BAST Format Decisions

Any change that bumps `FORMAT_REVISION` will need to honor or
revisit these:

| Decision | Rationale |
|----------|-----------|
| Custom binary format (not Proto/FlatBuffers) | Memory-mappable; ~10x faster than reparsing source |
| String + Path interning tables | Deduplication; path table sits after string table so no header change was needed when it was added |
| Delta-encoded locations w/ zigzag | ~70% space savings; zigzag handles negative deltas |
| Line/col dropped from BAST | Computed from offset; saves ~4 bytes/node |
| Compact tag numbering (1-67) | Eliminates gaps; easier maintenance |
| Metadata flag in tag high bit | Tags fit in 7 bits; saves 1 byte for empty metadata |
| Dedicated message-ref tags | Eliminates polymorphism, saves 1 byte/ref |
| Inline PathIdentifier / inline TypeRef in known positions | Position is unambiguous in refs / inlet/outlet/state/input — saves 1 byte each |
| Source-file change markers (not per-location path index) | Sources change rarely vs locations |
| Single integer `VERSION = 1` | Stays at 1 until the schema is finalized for external users; granular changes use `FORMAT_REVISION` |
| HTTP compression > library compression | HTTP layer already handles transport |

**Disjoint tag sets**: `readNode()` handles `NODE_*` tags only;
`readTypeExpression()` handles `TYPE_*` tags only. Crossing them
causes byte misalignment that surfaces as "Invalid string table
index" deserialization errors.

### AIHelperPass Design Rationale

(Shipped 1.22.0; impl at
`passes/shared/.../passes/ai/AIHelperPass.scala`.)

Distinct from ValidationPass: produces `Tip` messages (severity 0,
"soft" proactive guidance), works on incomplete models (no
ResolutionPass dependency), and rewrites resolution errors into
actionable Tips on a second path. Tip categories: Completeness,
Pattern, BestPractice, Relationship, Documentation. Entry points
exposed cross-platform via `RiddlLib.analyzeForTips` /
`analyzeSourceForTips`; `riddlc advise` is the CLI surface.

### Path-Identifier Usage Tracking (1.23.1)

`ResolutionPass.resolvePathFromAnchor` now records each anchor
and non-terminal intermediate component of every path
identifier as a **path usage** of `parents.head`. Storage is
in parallel maps `usesInPath` / `usedInPathBy` on `UsageBase`
(separate from the existing `uses` / `usedBy` to keep
`Usages.getUsers` semantics unchanged for downstream callers).

Filters:
- Self-references skipped via `user ne use`.
- Ancestor-qualified self references (e.g.,
  `state AState of fooBar.fields` inside `entity fooBar`)
  are filtered by `parents.exists(_ eq anchor)` — internal
  qualification does not count as external usage.

`UsageResolution.checkUnused`:
- Suppresses `is unused` when either direct or path usage
  exists.
- Emits a new `CompletenessWarning` for Types only when usage
  is path-only (addressable by path but never declared as a
  field's / state's type).

Public API on `Usages`: `isUsedInPath(d)` and
`getPathUsers(d)`. Tests in `UsageTest.scala` cover
positive / negative / mixed / scope / regression cases.

### EOF Brace Crash in Error Reporter (1.23.3)

Two `require` calls in `RiddlParserInput.annotateErrorLine`
were over-strict for parse failures at EOF — when a closing
`}` is missing, the failure's `At.endOffset` lands one past
the line range computed by `lineRangeOf`, the requires threw
`IllegalArgumentException`, and the outer `runMain` catch
surfaced it as `[severe] Exception Thrown` instead of a
normal `[error]` with file/line context. Removed the
requires; downstream slicing already clamps via `Math.min`.

Regression fixtures at `language/input/riddl-bad/{badDomain,
badEntity}.riddl` (3 opens / 2 closes by design) + a
`TopLevelParserTest` case lock this in. `badEntity.riddl` is
listed in `INCLUDE_FRAGMENTS` of both `ebnf_tatsu_validator.py`
and `ebnf_validator.py` so the EBNF-grammar CI job skips it.

### Native Scaladoc Race (1.23.4 mitigation)

Scala 3.8.3 scaladoc has a race in
`dotty.tools.scaladoc.renderers.Resources.allResources` that
crashes intermittently when multiple `doc` tasks run in
parallel under `publish`. It cost two releases (1.23.2 never
shipped; 1.23.3 needed a hand recovery). Workaround in
`build.sbt`: `passesNative` and `riddlLibNative` set
`Compile / doc / sources := Seq.empty` so the Native scaladoc
task is a no-op. JVM and JS scaladoc are unaffected. If the
race ever bites another Native module, apply the same one
line to its `.nativeSettings`.

### Scala.js Validation Performance

(Shipped 1.11.0; mostly resolved.)

Four-phase optimization made interactive validation practical for
the browser playground:

- **ParentStack** is now a class that caches its `toParents`
  (toSeq) result — replaces the previous type alias.
- ValidationPass micro-opts: single-pass handler classification,
  combined SagaStep validation, `recursiveFindByType` cache.
- `ValidationMode.Quick` skips `checkStreaming` and
  `classifyHandlers` in postProcess for interactive feedback.
- `IncrementalValidator` caches messages per-Context using FNV-1a
  fingerprints (`validator.reset()` forces full recheck).

ossum.ai's playground still uses a tiered Web Worker strategy for
very large models (BAST-loaded models skip validation since they
were pre-validated at build time).
