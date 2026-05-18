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

**Last Updated**: 2026-05-18

HEAD is at tag **1.23.0** on `development`, clean working tree,
in sync with origin. No work-in-progress; no stashes.

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
