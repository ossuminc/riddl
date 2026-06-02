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

**Last Updated**: 2026-06-02

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
