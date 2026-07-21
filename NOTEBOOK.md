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

## Current Status

**Last Updated**: 2026-07-21

`main` is at **1.31.0**, clean and pushed. All work now lands
directly on `main` (no `development` branch — see CLAUDE.md
"Branch Strategy"). The 1.24.0 provide-tips work described in
earlier notebook revisions shipped long ago; see CLAUDE.md
"Validation Specifics" for its durable design notes.

**Two releases shipped this session:**

- **1.30.0** — registered `protocol`, `event_catalog_version`,
  `sql_dialect`, `sql_table` as recognized options, plus a
  repo-wide formatting rectification (below).
- **1.31.0** — registered `backstage_owner`,
  `backstage_lifecycle`, `backstage_type`, `confluence_space`,
  `confluence_parent`.

Nine generator-metadata options are now registered in total.
The pattern, and how to pick `validParents`, is documented in
CLAUDE.md under "Validation Specifics" — read that before adding
the next one. The recurring gotcha: `KnownOptions.*` lists have
**no consumers**; only `RecognizedOptions.registry` affects
validation.

**Formatting baseline is now clean.** `sbt scalafmt` +
`sbt test:scalafmt` were run across every module (227 files:
123 main, 104 test) and both `scalafmtCheck` and
`test:scalafmtCheck` now pass. Before this, ~227 files were
out of conformance, which meant a real formatting regression
was invisible in the noise. **Keep it clean** — if a diff
starts showing dozens of unrelated reformatted files again,
something has drifted. Note `sbt scalafmt` formats *main*
sources only; test sources need `sbt test:scalafmt`.

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

**(none)** — queue is empty as of 2026-07-21.

Five riddl-generator option-registration tasks were closed out
this session and moved to `task/done/`: `register-protocol-option`
(completed in a prior session), `register-event-catalog-version-
option`, `register-sql-options`, `register-backstage-options`,
`register-confluence-options`. Each has its results appended,
including the reasoning behind any judgment call.

More of these are likely as riddl-generator grows generators.
They are near-mechanical; follow the CLAUDE.md recipe.

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
3. **Housekeeping** (low priority, none blocking):
   - **Delete the `development` branch** (local + remote) and
     `old-development`. `development` is fully contained in
     `main` (0 ahead, 39 behind as of 1.31.0), so nothing is
     lost. Deferred pending an explicit go-ahead.
   - **Fix `.claude/skills/ship/SKILL.md`** — it still prescribes
     the GitFlow pre-flight (fast-forward `main` from
     `development`) and post-release merge-back. Both contradict
     current policy; they were skipped for 1.30.0 and 1.31.0.
   - **Delete the stray `help` git tag.** Almost certainly a
     typo'd `git tag help`. Harmless, but it sorts to the top of
     `git tag --sort=-v:refname` and so leads the tag list when
     working out the latest release.
   - **Verify the `unset GITHUB_TOKEN` guidance for `gh`.** Both
     CLAUDE.md files and the ship skill say to unset the token so
     `gh` falls back to keychain credentials. In the 1.30.0 /
     1.31.0 sessions that produced "please run gh auth login" —
     the keychain was not reachable — and `gh` worked only with
     `GITHUB_TOKEN` **set**. May be specific to a sandboxed tool
     environment rather than the interactive shell; worth
     confirming which before trusting the documented advice.

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
