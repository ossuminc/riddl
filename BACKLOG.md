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

**The ITEM is not done.** It cannot ship until these four are finished, and it
stays here until they are. Full detail in the `entity-intentions` memory.

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
b. **JS DONE, Native in progress.** `tJS` green: 657 tests, 0 failures.
   `tNative` is NOT a real Native gate — see § 3; the genuinely-native rows are
   being run explicitly instead.
c. **DONE.** `MESSAGE_SUGGESTIONS.md` — added the intention-conflict Error and
   R1/R2/R3+R4, and REMOVED the stale `is event-sourced but this command handler
   does not emit an event` row, whose check was deleted with this work.
   `JSON_COVERAGE.md` — Entity row now lists `intentions`.
d. riddl-models task drop: 11 corpus entities + the event-sourced pattern
   template violate all four rules. **In motion on their side** — `16eb6ab1`
   converts six reactive-bbq entities, `aa68cdd6` gives repositories their own
   persistence commands. They are blocked on the refusing-clause defect in § 2.
   Until they land, these external-corpus suites are EXPECTED RED and are not
   internal signal: `RiddlModelsRoundTripTest` (9) and `Root2JsonCorpusTest`
   (179/189 clean vs a 95% floor).
d2. **riddl-examples has its own, harder copy of dokn** — task dropped at
   `../riddl-examples/task/migrate-dokn-to-event-sourcing-rules.md`. Four
   event-sourced entities (Company, Driver, Note, Medium) with `set` in `on init`
   and `morph` in command clauses, so it needs the full treatment including the
   `on init is { yield event X }` idiom. Blocks `RunRiddlcOnLocalTest`
   "should validate riddl-examples dokn" (7 errors).
e. **rc.10 is deferred — we soak via a locally staged build instead.** An RC is a
   slow CI round trip, and this change breaks consumers in ways worth finding
   before a tag exists. So: `sbt "reload; publishLocal"` for every module and
   platform plus the `sbt-riddl` plugin, and `riddlcNative/nativeLink` copied to
   `~/Code/ossuminc/bin/riddlc`. Consumers use that path EXPLICITLY — it is not
   on `$PATH`, where bare `riddlc` still resolves to the tap's rc.9.
   Currently staged: **`2.0.0-rc.9-34-5488fd9d`** (all 20 rows in
   `~/.ivy2/local`, binary verified to report it). If HEAD is ahead of that, check
   whether the extra commits are documentation-only before re-staging — a
   NOTEBOOK edit does not change the binary. First staged the same day at
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
- **`tNative` tests the JVM rows for 5 of its 7 modules** — found 2026-08-02.
  The alias runs `utils`, `language`, `testkit`, `commands`, `riddlLib`, and all
  five are the `.jvm` projects (build.sbt:218, 271, 346, 406, 433). Only
  `passesNative` and `riddlcNative` are actually Native. The Native rows exist
  and are aggregated (`utilsNative`:220, `languageNative`:273,
  `testkitNative`:349, `riddlLibNative`:409, `commandsNative`:435), so the fix is
  to name them — but that is exactly why it needs a plan: nothing has gated those
  rows, so expect a backlog of Native-only reds the moment they run.
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
- **`sbt scalafmtCheck` is red on HEAD** — 7 committed files reformat, 6 in
  `commands`. Deferred to just before 2.0.0.
- **Arity exemption for `error-sink` inlets** — riddl-models asked; deferred as a
  design decision, then FIXED in rc.9. Verify nothing else wants it.

### 4. Owed to other repos
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
