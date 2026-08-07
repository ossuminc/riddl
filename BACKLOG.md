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

### 0. Just before 2.0.0 is released

Things deliberately deferred to the release itself, not to be done piecemeal.

- **Run one `scalafmt` pass.** Formatting is not a gate before 2.0 (Reid,
  2026-08-04); do not run `scalafmtCheckAll`, report it, or format
  incrementally. `sbt scalafmtCheck` is red on HEAD — 7 committed files
  reformat, 6 in `commands`.
- **Upgrade riddl-vscode.** Reid, 2026-08-06 — deferred here deliberately, not
  overlooked. It consumes `@ossuminc/riddl-lib` via npm, which carries only
  PUBLISHED releases, so it cannot take a staged build at all and chasing it
  between RCs means cutting an RC for its benefit. It is on `2.0.0-rc.9`
  (`package.json:128`); bring it to 2.0.0 when 2.0.0 exists.
- **Regenerate every checked-in `.bast`.** Reid, 2026-08-06 — same reasoning:
  `FORMAT_REVISION` has moved twice in one day (6 -> 7 -> 8) and may move again
  before 2.0, so regenerating now buys nothing. riddl-models is the known holder.
  In-repo fixtures are NOT deferred — `language/input/import/NotImplemented.bast`
  must be regenerated at each bump or `IncludeAndImportTest` reddens.
- **Update `../RIDDL-Computational-Model.md` with everything `release/2`
  changed.** Reid, 2026-08-06. That document is the authority for any lowering
  decision — what a conforming generator MUST preserve versus may freely choose
  — so a language change that does not reach it leaves generator authors working
  from a stale contract. This branch has changed a lot of what it describes:
  entity intentions and the four event-sourcing rules, the unified processor
  model, implicit invariant scope, `requires`/`returns` in contents,
  `Riddl.Envelope` + `option message_envelope`, A56 (`tell p`) and A57
  (`on other as x`). Work from `git log 2.0.0-rc.1..HEAD` rather than memory.
- **Update the ossum.tech documentation site** with the same syntax changes,
  **plus a LIGHTER treatment of the implied syntax.** Reid, 2026-08-06 — the
  reference currently spells out more of the implicit forms than a reader needs,
  and the balance should shift toward what someone actually writes. Same source
  of truth: the commits on this branch, not recollection.
  (ossum.tech is a separate repo; this is a task DROP, not work done here.)

### 1. Queued, designed, not started

- **An Akka-style asynchronous `ask` statement**, so `yield` is paired with a
  genuine ask. Reid, 2026-08-06. **RESEARCH DONE 2026-08-06; wants Reid's ruling
  on the recommendation below, then a plan. Nothing built.**

  **What the language has today, verified in the code, not recalled:**
  - `yields` on a message type declares WHAT handling it produces
    (`AggregateUseCaseTypeExpression.yields`), and `checkYieldConformance`
    (ValidationPass:910) enforces that a clause actually produces it.
  - `yield` produces that value but names **no destination**. "The sender" is
    implicit.
  - `tell` delivers to a processor and says nothing about a reply.
  - **There is no correlation concept anywhere in the language** — grep for
    `correlation` in `language/src/main` returns nothing.

  So the gap is NOT "we cannot send a request". It is that **riddl cannot say two
  messages are two halves of one interaction.** `yields` describes the callee's
  obligation; nothing describes the caller's expectation, so a generator cannot
  tell a fire-and-forget `tell` from a request whose reply the caller awaits.

  **Recommendation: `ask` declares a CORRELATION, not a mechanism.** RIDDL
  specifies meaning and leaves representation to generators (the same line that
  settled `message_envelope`), so `ask` must not imply a Future, a temp actor, a
  correlation-id field, or a blocking call — all four are lowerings a generator
  should be free to choose between. What the language should add is the FACT that
  a reply is expected and which clause consumes it.

  Sketch, to be argued rather than assumed correct:

      ask command Pay of entity Ledger        // the reply is Pay's declared `yields`

  and the reply is consumed by an ordinary `on <that result>` clause in the
  asking processor. `ask` is then `tell` plus a declared expectation, and the
  existing machinery does the rest: yield conformance already guarantees the
  callee produces it, and A36 witnessing already checks a receiver has a clause
  for a message.

  **Open questions, in the order they need answering:**
  1. **Does `ask` need a completion value at all**, or is "reply arrives as a
     message handled by an on-clause" the whole of it? The latter is far cheaper
     and stays honest about asynchrony; the former re-introduces a call stack
     into a language that deliberately has none.
  2. **Timeout: language or generated code?** Leaning generated code — a timeout
     is a deployment property, and putting a duration in the model invites it to
     be wrong everywhere at once. But a modeller may legitimately want to say
     "this interaction is bounded".
  3. **What does an ask that is never answered mean for handler completeness?**
     There is a real check to be had here: `ask M of P` where P has no clause for
     M, or where M declares no `yields`, is a defect riddl could catch today.
  4. **The refusal path.** `checkYieldConformance` already treats a refusing
     clause as discharging the contract. An ask whose callee refuses gets no
     reply — is that a modelling error, or the expected shape?
  5. Does `ask` belong to Epics/UseCases (an interaction-level concept) rather
     than to statements? The interaction model already describes two-party
     exchanges, and that may be the more natural home.

- **Connector intentions: `persistent` plus `at-least-once` | `at-most-once`.**
  Reid, 2026-08-07, while ruling on where persistence is valid. A connector's
  durability and delivery guarantee belong in the GRAMMAR as intentions, the way
  entity intentions do, not as `option persistent` — options are advisory
  ("honored if possible") and a delivery guarantee is not advisory. Same
  category error the entity-intentions work fixed at 2.0.0-rc.10.
  **Sequence matters:** the option must keep working until the intentions exist,
  because **426 `option persistent()` uses across riddl-models are all on
  connectors** (verified 2026-08-07) and would have nowhere to go. So: add the
  intentions, deprecate the option, migrate the corpus, then remove.
  Repository is deliberately NOT included — Reid: "persistent by implication, so
  it doesn't need the option or the intention."

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

### 2. Queued, needs a plan
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
- ~~`Comment` in a `Group`'s contents cannot be rebuilt~~ — **STALE, resolved
  somewhere along the way; removed after verifying 2026-08-06.** Both halves of
  the claim are now false: `OccursInGroup` DOES include `Comment`
  (`AST.scala:900`) and `GroupParser.groupDefinitions` accepts it
  (`GroupParser.scala:28`). The "3 pinned occurrences" are gone —
  `Root2JsonFixturesTest` reports `identical=91`, `lossy=0`, `divergent=0`, and
  the test carries no Comment allowance. No decision needed; nothing to do.
- **Saga step statements are NEVER VALIDATED — not just reachability.** Filed as
  "saga reachability", VERIFIED 2026-08-06 and it is materially worse than that.
  **Promoted: this is a silent correctness hole, not a missing warning.**

  Repro, one file, both statements identical in shape:

      entity Caller is { handler H is {
        on command Dom.Ours.Doit is {
          tell command Dom.Ours.NoSuchCommand to entity Dom.Ours.NoSuchEntity } } }
      saga Flow is {
        step One is {
          tell command Dom.Ours.AlsoBogus to entity Dom.Ours.AlsoMissing
        } reverted by { do "undo" } }

  riddlc reports `NoSuchCommand` and `NoSuchEntity` as unresolved. It reports
  **nothing at all** about `AlsoBogus` or `AlsoMissing`. A saga step can name
  definitions that do not exist and validate clean. The reachability warning is
  just the symptom that happened to get noticed.

  **Root cause, and it is a known shape:** `SagaStep extends Leaf`
  (`AST.scala:4802-4808`) with `doStatements` / `undoStatements` as FIELDS beside
  `contents` rather than in it, so no `HierarchyPass` traversal ever descends
  into them — which is exactly the defect `3e4af6801` fixed for
  `requires`/`returns` by moving those clauses INTO contents.

  **Not small, despite where it was filed.** The correct fix is the same move —
  make `SagaStep` a `Branch` carrying its statements as contents — which is an
  AST change touching parser, prettify, BAST (+ `FORMAT_REVISION`), JSON and
  resolution. And it will newly subject every saga statement in every model to
  validation that has never run on them, so expect a wave of findings on first
  run, in this repo's fixtures and in riddl-models. Budget for that, not for a
  one-line filter. Wants a plan.
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
- **Arity exemption for `error-sink` inlets** — riddl-models asked; deferred as a
  design decision, then FIXED in rc.9. Verify nothing else wants it.

### 3. Owed to other repos
- **Consumer sweep — task files dropped 2026-08-06 in THREE repos.** Pins read
  from each build file after `git fetch`, not from memory:
  - **riddl-generator** — `2.0.0-rc.10-15-3df5cf44`. **CURRENT**, matching the
    staged build exactly. Nothing owed.
  - **synapify** — `2.0.0-rc.10-15-3df5cf44`. **CURRENT.** Nothing owed.
  - riddl-models — `build.sbt:21` at `2.0.0-rc.10-2-ff3a59b4`, 13 behind.
  - riddl-examples — `build.sbt:21` at `2.0.0-rc.9-54-64b7b413`.
  - riddl-idea-plugin — `project/Dependencies.scala:7` at `2.0.0-rc.9-42-37b0db94`.
  - **riddl-vscode — deliberately NOT swept**; deferred to release, see § 0.

  Each task names the target and what changed since that repo's pin, and tells
  them to LEAVE `.bast` files alone (§ 0). Waiting on their sessions.
- **synapify: `flattenAST` workaround can be dropped.** `Contents.definitions`
  became include- and import-transparent on 2026-08-06 (their task file, now in
  `task/done/`), so their 33 `.definitions` sites no longer need the tree
  physically flattened first. Nothing owed until they take a build containing
  it — worth folding into the next consumer sweep rather than a task of its own,
  since the change is source-compatible and their current code stays correct.
- riddl-vscode: adoption task for `IncrementalValidator` — hold until the 2.0
  upgrade above, so they take one change rather than two.
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
