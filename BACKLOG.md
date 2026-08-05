# RIDDL Backlog

The single home for open work: tasks, features, bugs, and design questions not
yet decided. **If it is not here, it is not tracked.** Items carry the
verification already done, with `file:line` where a claim was checked against
the code, so the next session does not re-derive it.

Completed items leave this file: what they taught goes to `NOTEBOOK.md`, what
is durably true goes to `CLAUDE.md`. Incoming `task/*.md` files are INPUTS —
triage them with `/ossuminc-skills:check-tasks` and file what survives here.

Lines migrated from NOTEBOOK.md predate the 80-column rule for this file; clean
them as items are touched rather than in one sweep.

Large items get their own plan (`~/.claude/plans/`) before implementation; the
plan is discarded once built.

### 1. Entity intentions + event-sourcing rules — IN FLIGHT, not done

Code is COMPLETE and committed (`c87099520`, `78cefd05c`, `67ede5b3d` on
`release/2`, unpushed): the six semantic options are now keywords before
`entity`, and the four event-sourcing rules are enforced as Errors. Implemented,
tested and canaried — `EntityIntentionRoundTripTest` 13/13,
`EventSourcedEntityTest` 10/10, JVM row green except item (a).

**The ITEM is not done, but only (e) remains.** As of 2026-08-05 (a), (b), (c),
(d) and (d2) are all closed and verified; the item stays here until **(e), the
soak / rc.10**, lands. Full detail in the `entity-intentions` memory.

Note on (b), since the record disagreed with itself for a day: it read "Native
in progress" while NOTEBOOK § HANDOFF claimed all three platforms green at
"Native 173/1318". Both were honest — the HANDOFF number came from the `tNative`
alias, which is 5/7 JVM rows (§ 3), so it was never a Native measurement. The
seven real rows have now been run explicitly: 176 suites / 1339 tests, zero
failures. **When two lines of the record disagree, the alias is usually the
liar.**

a. **DONE.** Migrate `language/input/dokn.riddl` — was the only in-repo red
   (`ExamplesTest`). Now validates with ZERO errors, `should compile dokn` is
   green, and the prettify round trip preserves every new construct.
   4 event-sourced entities predating the rules; 7 handled commands violate R1
   (Company 1, Driver 2, Location 4 — confirmed against the staged binary).
   R3/R4 are already satisfied: no command clause mutates, and Location's
   existing `on event` applies an event declared inside it.
   **Not purely additive, as first assumed.** `yields` exists only on the
   kind-first type form (`def_of_type_kind_type`, ebnf-grammar.ebnf:112):
   `command X yields event Y is { … }`. dokn declares commands the type-first
   way (`type X is command { … }`), which admits no `yields`, so each of the 7
   must be reshaped to the kind-first form before an `on event Y` clause can be
   added for it.
   A FIFTH rule bites at the same time and is easy to miss by reading
   `checkEventSourcing` alone: `checkYieldConformance` (A19, ValidationPass:788)
   requires the clause to actually contain `yield event E` once the command
   declares `yields E`. Keep the existing `send`; add the `yield` beside it.
   Fallout fixed: `RootComparisonTest` asserted a model scores exactly 1.0
   against itself, but `countCosine` computed `Σc²/(√Σc²·√Σc²)` and `√x·√x ≠ x`
   for ~47% of integer magnitudes — it had been passing by luck, and dokn's new
   counts lost the toss. Reformulated to `dot/√(sumA·sumB)`, exact on 200k
   random count vectors (RootComparison.scala:295).
b. **DONE — JS and Native both verified.** `tJS` green: 657 tests, 0 failures.
   Native was the open half, because `tNative` is not a real Native gate (§ 3).
   All **seven genuine Native rows** were named explicitly and run 2026-08-05 in
   one `;`-separated argument, with `Suites: completed` counted against the seven
   modules asked for — **7 lines for 7 modules, 0 failures**:

   | row | suites | tests |
   |---|---:|---:|
   | `utilsNative` | 7 | 108 |
   | `languageNative` | 32 | 337 |
   | `passesNative` | 118 | 723 |
   | `testkitNative` | 1 | 1 |
   | `riddlLibNative` | 4 | 102 |
   | `commandsNative` | 11 | 47 |
   | `riddlcNative` | 3 | 21 |
   | **total** | **176** | **1339** |

   The single `pending` is `institutional-commerce`, explicitly `pending` at
   `RunRiddlcOnLocalTest.scala:50` — not a skip. `riddl-examples dokn` passes on
   Native too.
   **The feared Native-only backlog does not exist** (§ 3 predicted one). Log
   kept at `scratchpad/native.log`.
c. **DONE.** `MESSAGE_SUGGESTIONS.md` — added the intention-conflict Error and
   R1/R2/R3+R4, and REMOVED the stale `is event-sourced but this command handler
   does not emit an event` row, whose check was deleted with this work.
   `JSON_COVERAGE.md` — Entity row now lists `intentions`.
d. **DONE — riddl-models has landed its migration.** Verified 2026-08-05 against
   the live checkout at `f3ff0ade` (clean tree), not against the task file:
   - Full independent sweep of **all 189 `.conf` entry points** with the staged
     `~/Code/ossuminc/bin/riddlc` (`2.0.0-rc.9-54-64b7b413`): **189/189 with zero
     errors**.
   - 27 entities still carry the `event-sourced` keyword (reactive-bbq's 13, the
     `patterns/entity/event-sourced/` template and example, …), so the four rules
     ARE being enforced against them — this is conformance, not evasion by
     dropping the intention.
   - **The two expected-red suites are green** and are internal signal again:
     `RiddlModelsRoundTripTest` 189/189, `Root2JsonCorpusTest` reports
     `models=189 reparsed=189 cleanRoundTrip=189 (100.0%)` against a 95% floor
     (was 179/189).
   Fallout fixed here: `RiddlModelsRoundTripTest.pendingModels` excluded 6 models
   for "pre-existing validation errors … redefine built-in type names", carrying
   the comment "Remove after fixing in riddl-models". That reason no longer held
   — each validates with 0 errors — so 6 round-trip tests were being skipped
   inside a suite that read as green. Set emptied; the suite went 183 succeeded
   + 6 pending → **189 succeeded, 0 pending, 0 failed**, with all six named
   individually in the log.
d2. **DONE — riddl-examples dokn is migrated.** Verified 2026-08-05:
   `riddlc from src/riddl/dokn/dokn.conf validate` reports **0 errors** (warnings
   only — unreachable `tell` targets, which are a different backlog item), and
   `RunRiddlcOnLocalTest` "should validate riddl-examples dokn" passes. Five
   event-sourced entities now, not four — Company, Driver, Note, Medium **and
   Location** — all spelled `event-sourced available entity`, none reverted to a
   plain `entity`. The suite's `succeeded 3, pending 1` is not a skip of dokn:
   the pending one is `institutional-commerce`, explicitly `pending` at
   `RunRiddlcOnLocalTest.scala:50`.
   **Still owed on their side:** the task file
   `../riddl-examples/task/migrate-dokn-to-event-sourcing-rules.md` is still in
   `task/` with no `## Results` section, so their repo does not record its own
   completion. That is theirs to close, not ours.
e. **rc.10 is deferred — we soak via a locally staged build instead.** An RC is a
   slow CI round trip, and this change breaks consumers in ways worth finding
   before a tag exists. So: `sbt "reload; publishLocal"` for every module and
   platform plus the `sbt-riddl` plugin, and `riddlcNative/nativeLink` copied to
   `~/Code/ossuminc/bin/riddlc`. Consumers use that path EXPLICITLY — it is not
   on `$PATH`, where bare `riddlc` still resolves to the tap's rc.9.
   Currently staged: **`2.0.0-rc.9-54-64b7b413`** (all 20 rows in
   `~/.ivy2/local`, binary verified to report it, 2026-08-04 22:35). If HEAD is
   ahead of that, check whether the extra commits are documentation-only before
   re-staging — a NOTEBOOK edit does not change the binary. As of `b163d3c85`
   the only diff from the staged commit is NOTEBOOK.md, so no restage is owed. First staged the same day at
   `2.0.0-rc.9-6-46c5968d`, which was verified to reject dokn's 7 R1 violations
   — that is how riddl-models found the refusing-clause defect in § 2.
   Consumers to sweep: riddl-generator, riddl-models, riddl-examples,
   riddl-idea-plugin, riddl-vscode, synapify. **Exit condition:** riddlg's
   upgrades complete (expect a few days) — then push, CI, and cut rc.10.
   Re-publish + re-stage after each riddl commit; the version string changes
   every time, which is what keeps consumer resolution cache-safe.

### 2. Queued, designed, not started

- **Carry source locations through the JSON surface** — plan written and
  approved-pending. Every JSON-built node has `At.empty`; adds `$at` per contents
  entry with an origin/document basis.
- **Deprecate `type X is <aggregate_use_case> {…}`** (approved 2026-08-02:
  deprecate in 2.0, remove in 3.0). Target is ONLY the type-first spelling of an
  aggregate use case; plain `type` (`type Address = {…}`, `type M is Pattern(…)`,
  `type L is any of {…}`) is unaffected and stays.
  **Why it is vestigial, not merely redundant:** it produces the same AST as the
  kind-first form — verified by prettifying both, which emits `command A is {…}`
  for each — so the canonical emitter already erases it and a type-first model
  never round-trips back to its own spelling. It is also strictly LESS expressive
  at the surface: `yields` exists only on `def_of_type_kind_type`
  (ebnf-grammar.ebnf:112), which is what blocked the dokn migration.
  **The corpus has voted:** riddl-models has 9,337 aggregate declarations, all
  kind-first, zero type-first. All 167 type-first occurrences are pre-1.0
  fixtures inside this repo.
  Rejected alternative: adding `yields` to the type-first form — grammar surface
  spent on a spelling nobody writes and the printer will not emit.
  **Known cost:** those 167 fixtures start warning, and since parse-time
  deprecations surface under every command, their `.check` goldens all shift.
  Wants its own plan and its own soak; do NOT bolt it onto the intentions work.

- **Drop the deprecated inline aggregation from `requires`/`returns`, then
  narrow the accessors to `Option[TypeRef]`.** Decided by Reid 2026-08-04 while
  moving the clauses into contents: `Option[TypeRef]` is the wanted END state,
  but it is a language change, not a type tidy-up, so it does not ride along.
  Today `Requires.what` / `Returns.what` are `TypeRef | Aggregation` and
  `Function.input` / `Saga.input` return `Option[TypeRef | Aggregation]` —
  **exactly the type the constructor fields had**, which is why the move cost
  consumers nothing.
  **Do NOT narrow the accessor while the node stays wide.** A saga written
  `requires { a: Integer }` would then read as having no input at all, and any
  check gated on `input.isEmpty` fires wrongly — the ungated-accessor failure
  mode this repo keeps rediscovering.
  **Verified cost of doing it properly** (checked 2026-08-04, not estimated):
  4 fixtures use the inline form — `language/input/everything_full.riddl:72,97`,
  `language/input/module/mixed-module.riddl:17`,
  `language/input/requires-returns-ref.riddl` — plus two tests that ASSERT the
  deprecation fires (`FunctionValidatorTest:106`, `SagaValidatorTest:56`), the
  `aggregation` alternative in `func_input`/`func_output` in the EBNF + a GBNF
  regen, the `ArgDto.fields` read path in JSON, and an external-corpus re-run.
  Sequence: deprecate loudly for a release, then remove.

### 3. Queued, needs a plan
- **A keyword-named field reports the error several tokens upstream.** From
  riddl-generator 2026-08-03, filed here because it is a real diagnostic defect
  even though they marked it "no action needed". A field in a message
  aggregation named after a keyword — `command Store is { entity: Order }` —
  reports `Expected one of ("(" | "yields")` pointing at the `is`, several
  tokens BEFORE the offending `entity`. The rule (keywords are not field names)
  is correct; the attribution is what costs time, and it is the same class of
  cost as the saga-comment defect fixed in `867ab0333` — a message that does not
  name the thing that is actually wrong. Wants a plan because good attribution
  here likely means a cut/`~/` placement change in the aggregation rule, and
  those interact with `rep` termination (see the statement-restriction pattern
  note in memory). Low priority; a nuisance, not a blocker.
- **Move ResolutionPass off `ClassTag` for type differentiation.** A measured
  cleanup, NOT a fix for anything currently slow — filed 2026-08-03 after the
  ClassTag hypothesis was tested and refuted as the cause of the Scala.js
  resolution cost (that was the source-file hashing; see NOTEBOOK).
  `isSameKind` (ResolutionPass.scala:661) does
  `classTag[DEF].runtimeClass.isAssignableFrom(d.getClass)`. Measured over
  168,400 tests by `TypeTestCostBenchmark` (`passes/src/test/scala/`):

  | strategy | JVM | JS | Native |
  |---|---:|---:|---:|
  | current (classTag per call) | 19.5 ms | 3.6 ms | 0.9 ms |
  | hoisted (runtimeClass once) | 17.2 ms | 3.8 ms | 0.2 ms |
  | predicate (stored lambda) | 2.9 ms | 2.0 ms | 0.5 ms |
  | direct (isInstanceOf) | 0.9 ms | 0.9 ms | 0.1 ms |

  So ClassTag costs 20x a direct `isInstanceOf` on the JVM, 15x on Native, 4x
  on JS — worst, in absolute terms, on the platform nobody is complaining
  about. A stored predicate recovers most of it and adds NO data to any node;
  Scala 3's `TypeTest[Definition, T]` reaches the `direct` row outright,
  because at call sites where `T` is statically known the compiler emits a
  plain `isInstanceOf`.
  **Why it needs a plan rather than a patch:** ResolutionPass does not only
  TEST with the ClassTag, it also reads `classTag[T].runtimeClass.getSimpleName`
  for error messages (:922, :946) and compares exact class identity (:1271,
  :1275). Moving off ClassTag therefore wants a small `Kind` abstraction
  carrying both a predicate and a display name, threaded through ~13 generic
  methods. That is a real refactor for a few milliseconds on the JVM, so it is
  explicitly LOW priority — do not bundle it with performance work that has a
  measured user impact.
- **`BASTPerformanceBenchmark` is timing-flaky.** It asserts BAST load beats
  parse (`speedup > 1.0`). Observed on one machine, back to back: 0.9956x (a
  FAILURE), then 13.0x, 9.3x, 6.1x — and within a single run, parse ranged
  2.03ms to 33.03ms. The measured effect is real and large; the threshold is
  simply being compared against a number with more variance than headroom on a
  loaded machine. It will fail intermittently in CI and teach people to re-run
  reds. Either warm up and take a median of N, or assert something stable (e.g.
  a floor well below the real ratio) and report the number without gating on it.
- **`BASTParserInput`'s synthetic line index is now unreachable dead code.**
  With `positionsKnown = false`, `At` never consults its `lineOf`/`offsetOf`,
  and `createAtFromOffsets` lost its last caller when `readLocation` stopped
  casting. It still fabricates positions on the 10000-chars-per-line scheme for
  anything calling it DIRECTLY, which is exactly the plausible-looking machinery
  that caused the original defect. Delete it, after checking for direct callers
  of `lineOf`/`offsetOf` on a BAST-attached source (message formatting and
  `annotateErrorLine` are the ones to look at).
- **Should an imported definition RESOLVE without an explicit flatten?** The
  accessors now report `.bast`-imported definitions (2026-08-03), so
  `domain.types` lists an imported type — but a reference to it still fails to
  resolve until `FlattenPass` runs, because the symbol table is built by
  traversal rather than by the accessors. That split is currently pinned by
  `BASTImportLoadingTest`, and it is defensible (reading is the client's
  question, resolving is the model's), but it means a model can name a type its
  own accessors report. Decide whether SymbolsPass should index wrapper
  contents, or whether the flatten requirement should be stated more loudly.
- **A conditionally refusing clause escapes yield conformance** — the residual
  gap left by `0054a8433`. `checkYieldConformance` asks whether a refusal appears
  ANYWHERE in the clause, so a clause that refuses on one branch and forgets to
  yield on its success path passes unchecked. The precise rule wants "cannot
  reach the end of the clause having produced the declared event", i.e.
  path-sensitive analysis. The sibling check at ValidationPass.scala:509 has the
  identical weakness, so fixing one should fix both. Not newly introduced and not
  urgent — but it is the honest limit of the current predicate, so it is written
  down rather than implied.
- **`tNative` tests the JVM rows for 5 of its 7 modules** — found 2026-08-02,
  **still true, but no longer risky to fix** (verified 2026-08-05). The alias
  (build.sbt:552) runs `utils`, `language`, `testkit`, `commands`, `riddlLib`,
  and all five are the `.jvm` projects (build.sbt:218, 271, 346, 406, 433). Only
  `passesNative` and `riddlcNative` are actually Native.
  **The blocker is gone.** This wanted a plan because "nothing has gated those
  rows, so expect a backlog of Native-only reds the moment they run." They have
  now been run — all seven, 176 suites / 1339 tests, **zero failures** (§ 1b).
  So the fix is the mechanical one: replace the five JVM names with
  `utilsNative`, `languageNative`, `testkitNative`, `commandsNative`,
  `riddlLibNative`. No plan needed; no reds to work through first.
  **Scope is narrower than it looks:** `cNative` (build.sbt:527) already names
  all seven Native rows, so Native code does COMPILE everywhere in CI. Only test
  EXECUTION falls back to the JVM. What has never been gated is Native *runtime*
  behaviour in those five modules — which is precisely what the 2026-08-05 run
  exercised and found clean.
  **CI inherits this**: `scala.yml:97` runs `sbt "; clean; c${platform};
  t${platform}"`, so the Native matrix leg has been reporting green off the JVM
  rows.
  `tJS` does this correctly (it names `utilsJS`, `languageJS`, `passesJS`,
  `testkitJS`, `riddlLibJS`), which is what makes the Native asymmetry look like
  an oversight rather than a decision. Same defect class as the one the `tJVM`
  comment (build.sbt:540) was written to prevent: "a release gate that skips
  three modules and reports success is worse than no gate."
- **`Comment` in a `Group`'s contents cannot be rebuilt** — the parser puts one
  there but `OccursInGroup` admits none. Pinned at 3 occurrences in
  `Root2JsonFixturesTest`. Widen the union or attach as metadata. Needs a
  decision.
- **Saga reachability** — the usage walk appears not to traverse saga
  `doStatements`, so a `tell … to context <external>` in a saga step draws no
  "not reachable" warning while the same statement in a handler does.
- **`validateArbitraryInteraction`'s refMap path is dead** — interaction refs are
  keyed under the UseCase. Re-key, or delete and use the symbol table as A39 did.
- **`PlatformContext.withOptions` lacks try/finally** — a throwing test poisons
  global options for later sequential suites. One-liner.
- **`Blob`, `Unknown`, `Range` in the tokenizer tables** are unreachable from the
  grammar (riddl-vscode). Remove them or make them reachable.
- **`riddl_grammar.lark` is stale AND dead — delete it or revive it.** Verified
  2026-08-04 (flagged unverified by ossum.tech; both halves confirmed here).
  `language/src/test/scalajvm/python/riddl_grammar.lark:369` still carries the
  pre-A28 invariant rule (`literal_string` only — no boolean expression, no
  `requires`, no block form) and has no `boolean_atom`/`comparand` rules at all.
  Its ONLY consumer is `ebnf_validator.py`, and **CI does not run that**:
  `.github/workflows/scala.yml:189,230` run `ebnf_tatsu_validator.py` and
  `gbnf_validator.py` only. So it is not a gate that has gone quiet — it is a
  trap for the next reader who assumes it is authoritative. Deleting is the
  cheap option; reviving means maintaining a third grammar.
- **Rule on same-named invariants at entity and state scope.** With implicit
  application (§15.2) an entity-level `invariant X` and a state-level
  `invariant X` both apply inside that state. Nothing special-cases it today —
  ordinary duplicate-name rules apply. My recommendation in the approved plan
  was Error rather than shadowing, on the grounds that silently shadowing a
  CHECK is the failure mode the whole implicit-invariant change exists to
  remove. Not built, and not urgent; needs Reid's ruling first.
- **`sbt scalafmtCheck` is red on HEAD** — 7 committed files reformat, 6 in
  `commands`. Deferred to just before 2.0.0.
- **Arity exemption for `error-sink` inlets** — riddl-models asked; deferred as a
  design decision, then FIXED in rc.9. Verify nothing else wants it.

### 4. Owed to other repos
- **Sweep consumers onto `2.0.0-rc.9-54-64b7b413`.** Pins verified 2026-08-05:
  - **riddl-models — already current** (`build.sbt:21` `riddlVersion =
    "2.0.0-rc.9-54-64b7b413"`, driving `riddlcVersion` and all three test deps).
    Nothing owed.
  - **riddl-generator — behind** at `2.0.0-rc.9-48-fdc5c171`
    (`../riddl-generator/project/Dependencies.scala:53`).
  - **riddl-examples — behind** at the same `2.0.0-rc.9-48-fdc5c171`
    (`../riddl-examples/build.sbt:21`, `With.Riddl.library`). Its *models* are
    already conformant (§ 1d2); this is only the dependency pin.

  Both laggards predate `canContain`, the prettify metadata fix, the invariant
  semantics and the `when invariant X` work. Still to check:
  riddl-idea-plugin, riddl-vscode, synapify.
  **BAST `FORMAT_REVISION` is 6**, so any checked-in `.bast` from an earlier
  build is rejected with "regenerate .bast files with the current riddlc" —
  expected, not a bug, but worth saying in each bump task.
- riddl-vscode: adoption task for `IncrementalValidator` now that rc.9 ships.
- ossum.tech: `/riddl/2.0/licenses/` (the URL `riddlc info` prints is a 404), the
  two silent breaking changes in the migration guide, and docs for the
  `ForeverEmpty.void` error-sink idiom.
- ossum.tech: the event-sourced **`on init` idiom**. R3 forbids `set` in
  `on init` and an empty handler body is a parse error, so init cannot simply be
  dropped — but `yield` is legal there. Working form (from riddl-models):
  `on init is { yield event ShiftCreated }` paired with
  `on event ShiftCreated is { morph …; set state ActiveShift to "…" }` —
  initial state arrives by replaying the creation event. Every event-sourced
  entity needs this, so it wants an example, not just a rule.
