# Engineering Notebook: RIDDL

Records open work, blockers, and design nuances that future AI
sessions need to know. Release history lives in git tags and
GitHub release notes — don't reproduce it here.

## HANDOFF

Orientation for a session with no memory of this work. **Open work is in
`BACKLOG.md`**; durable facts are in `CLAUDE.md`. This says only where things
stand and what a fresh session would get wrong.

**State** — every number below was produced by a command in the session that
wrote this, not recalled:

- Branch `release/2`, tree clean, **PUSHED** — 47 commits went up on 2026-08-04
  (`e5926d418..fdc5c1718`), ending the hold-everything-local period. CI runs on
  `release/*` (scala.yml:9), so pushes here now build. Verify rather than trust:
  `git log --oneline origin/release/2..HEAD | wc -l`.
- HEAD is the tip of `release/2`; the last code commit is the requires/returns
  move (below), on top of `867ab0333` (saga) and `496e77c39` (hashing).
- **Staged build is CURRENT: `2.0.0-rc.9-52-b33decf2`** at
  `~/Code/ossuminc/bin/riddlc`, 20 ivy rows under that version from
  `publishLocal`. Staged 2026-08-04 from a clean tree with `reload`, binary
  copied only after `nativeLink` reported `[success]` — all three preconditions
  met, so the version carries no timestamp suffix and is reproducible from
  `b33decf25`. Verified by ENFORCEMENT, not just `riddlc version`:
  `language/input/invariant-scope.riddl` parses clean here and is a PARSE ERROR
  under the tap's `/opt/homebrew/bin/riddlc`.
- **All three platforms green**, re-run in full on 2026-08-04 after the
  invariant work with `<module>/testOnly *`, one `;`-separated argument,
  `Suites: completed` lines counted against modules requested: **JVM 252 suites
  / 2029 tests** (7 modules), **Scala.js 60 / 674** (5), **Native 173 / 1317**
  (6) — zero failures. External corpus reds unchanged (BACKLOG 1d).

**The requires/returns move is DONE and green** — `1f93cc517`'s incomplete
checkpoint is finished. `requires`/`returns` are `Requires`/`Returns` CONTENT on
`Function` and `Saga`; `input`/`output` remain as derived accessors returning
`Option[TypeRef | Aggregation]`, the exact type the constructor fields had.
Details, and the five lessons, are in NOTEBOOK § "`requires`/`returns` became
content". The `Option[TypeRef]` narrowing is deliberately NOT done — it requires
dropping the deprecated inline aggregation, filed in BACKLOG § 2 with its
verified cost.

**Consumers can pick this up now** — `2.0.0-rc.9-52-b33decf2` is both staged at
`~/Code/ossuminc/bin/riddlc` and in `~/.ivy2/local` (20 rows). It carries the
requires/returns move, `canContain`, the prettify metadata fix and the new
invariant semantics. BAST `FORMAT_REVISION` is now **5**, so any `.bast` written
by an earlier riddlc is rejected with "regenerate .bast files with the current
riddlc" — expected, not a bug.

**Formatting is not a gate before 2.0.** The whole codebase gets one `scalafmt`
pass just before the release ships. Do not run `scalafmtCheckAll`, do not report
it, do not format files incrementally (it drags unrelated files into focused
diffs). Reid's call, 2026-08-04.

`task/` is empty; both earlier incoming tasks are in `task/done/` with Results.
**A third is owed to synapify** — see the re-analysis note in BACKLOG § 4.

**Next:** sweep consumers against the new staged build per BACKLOG 1e —
riddl-generator wants the saga fix, synapify wants the hashing fix and owes a
re-run of its own `AnalysisPassCostTest` (riddl cannot produce that table; the
JS `PlatformContext` is `DOMPlatformContext` and loads by `fetch`, so no riddl
test reads a corpus file under Node).

**Traps, all of which have actually bitten someone here:**

- **`~/Code/ossuminc/bin` is deliberately NOT on `$PATH`.** Consumers use the
  explicit path; bare `riddlc` is still the tap's rc.9.
- **Publishing has THREE preconditions** — clean tree (a dirty one makes dynver
  stamp a timestamp, so the version stops being reproducible from a commit),
  `reload` (a long-running sbt server freezes dynver), and `nativeLink`
  FINISHED before copying the binary. Checklist is in `.claude/skills/rc/`.
  Two of the three were violated on 2026-08-03.
- **Use `<module>/testOnly *`, never `test`** — it resolves to `testQuick` and
  silently skips. **`tNative` is not a Native gate** (BACKLOG.md § 3).
- **`sbt -batch` runs ONLY THE FIRST command argument** — seven
  `'module/testOnly *'` args ran one module, printed "All tests passed" and
  exited 0. Use one `;`-separated argument AND count the `Suites: completed`
  lines against the modules you asked for. An sbt `;` chain also aborts on
  first failure, hiding every later module — so a short count means a red OR a
  skip; either way, look.
- **Re-run all rows after a late change**, not just the ones you expect to move.
- **Checks and APIs here fail by being UNGATED, not under-tested.** When a
  check's guard depends on an accessor, ask what happens the day that accessor
  starts returning things.
- **When a consumer reports duplicating our logic, expose ours** — do not add a
  second copy on our side.
- **A platform asymmetry can look exactly like an algorithm.** The 2026-08-03
  resolution investigation: compare the JVM/JS/Native RATIOS before profiling.
  Parse cost 3.2x on Scala.js while Resolution cost 97x — uniform overhead is
  the runtime, a lone outlier is a specific operation.

**Certainty:** the state block is verified. Anything in BACKLOG.md § 3 marked
"needs a plan" is explicitly unverified. Two notes were found STALE and
corrected on 2026-08-03 (an `attachment ULID` parse claim, and a `task/done/`
file recording work that never happened) — **treat old "known bug" notes, and
`done/` placement, as claims to re-check rather than facts.**

**`task/` holds 1 file:** `2026-08-03-saga-bodies-reject-comments.md`
(riddl-generator). The comment-above-`requires` half is now fixed; append
results and move it to `task/done/` once the restage lets you demonstrate it.

**Run `/ossuminc-skills:check-tasks` in the new session.**

---

## Incoming Tasks

**At session start**, check the `task/` directory for pending work
requests from other projects. Each `.md` file describes a task
(e.g., a dependency upgrade). Treat unresolved tasks as to-do
items unless already completed (verifiable from this notebook,
CLAUDE.md, or git log). After completing a task, append results
to the task file and note the disposition below.

---


## Invariants apply implicitly now (2026-08-04) — DONE

`b33decf25`. Requested by riddl-generator: an invariant did nothing unless some
clause wrote `require invariant X`, so a model could carry a constraint that
read as enforced and was inert. Semantics are in
`ossuminc/RIDDL-Computational-Model.md` §15 (rewritten) — **that is the
authority; don't re-derive from here.**

**The design turn worth remembering.** riddlg's open question was "a pure block
cannot `send`, so a stateless processor cannot gather data", with three options,
all of which were about whether to permit effects. The answer was none of them:
an invariant DECLARES what it reads, and that declaration also decides where it
applies. Nothing became inexpressible and no new machinery appeared beyond one
optional clause.

The reasoning generalizes: **"does not mutate" is the wrong axis for anything
that runs before an effect.** A read-only query satisfies it while breaking the
four properties a precondition actually needs — synchronous, total,
deterministic, terminating. When something must run in a refusal window, check
those four, not purity-as-non-mutation.

**Four things worth keeping:**

1. **A severity has to match its sibling.** I made "invariant on a stateless
   processor" an Error while the analogous "declared but never applied" case was
   a Warning by Reid's ruling. Same defect — an inert invariant — so two
   severities was arbitrary, and the Error rejected an existing fixture
   (`module/mixed-module.riddl:14`) that had been inert under the old rule
   anyway. Downgraded. **When adding a diagnostic, find the nearest existing one
   and match it, or justify why not.**
2. **A test that encodes the old rule fails correctly.** `CompletenessTest`'s
   "warn when invariant is not referenced by require" went red because not
   warning is now right. Rewritten to assert the new rule rather than patched to
   pass — and the case it used to cover is now covered by a second case for the
   `requires <type>` form.
3. **Scala 3 sealed-hierarchy exhaustivity does the finding for you.** Adding
   `InvariantBlock` as a `RiddlValue` broke four unrelated matches in
   SymbolsPass/ResolutionPass/ValidationPass at COMPILE time. Adding it to
   `NonDefinitionValues` fixed all four — the same lever that worked for
   `Requires`/`Returns` the same day. New non-definition AST node ⇒ put it in
   that union.
4. **Two silent prettify defects surfaced only because a fixture had no
   metadata.** `doInvariant` never terminated its line, so a metadata-less
   invariant ran into whatever followed it — output that still re-parsed, which
   is why nobody noticed. Most fixtures carry `with { … }`, whose emission
   supplied the newline by accident. **A fixture without optional decoration is
   a different test than one with it.**

---

## `requires`/`returns` became content, and `???` came with them (2026-08-04) — DONE

The follow-on to the saga-comments fix below. Fixing that one made a comment a
legal saga definition, which promptly exposed a second defect it had been
hiding: a comment written ABOVE `requires` consumed the definitions slot and
`requires` was then rejected. The body grammar was
`[func_input] [func_output] {definitions}` — a fixed PREFIX — so the working
rule was "`requires`/`returns` must be the very first tokens of the body",
exactly where a reader wants a comment explaining them. `Function` had the
identical prefix and the identical defect.

`requires`/`returns` are now ORDINARY CONTENT: new `Requires`/`Returns` AST
nodes in `OccursInSaga`, `OccursInFunction` and `NonDefinitionValues`.
`Function.input`/`output` and `Saga.input`/`output` survive as derived
accessors returning `Option[TypeRef | Aggregation]` — **the exact type the
constructor fields had**, which is why the blast radius was 5 test sites rather
than the ~56 first estimated, and why riddlg and riddl-gen needed no change.

**Five things worth keeping:**

1. **Fixing a prefix means fixing the WHOLE prefix.** Moving the two clauses
   into the content list broke `requires X returns Y ???` — a shape in the
   corpus (`everything_full.riddl:72`) — because every container spells its
   body `undefined | definitions`, so `???` was an alternative to the whole
   list rather than a member of it. It worked before only because the clauses
   sat OUTSIDE that choice. `???` is now a content item too. The lesson: when
   you dissolve a fixed prefix, enumerate everything the prefix's position was
   silently permitting, or you narrow the language while thinking you widened
   it. 23 language tests went red before this was found.
2. **A grammar closure cannot bound cardinality — say so somewhere.** `rep`
   accepts `requires A requires B` happily. `checkRequiresReturnsCardinality`
   in `ParsingContext` enforces zero-or-one AFTER the parse, which is the only
   reason `Function.input`'s `headOption` is a complete answer rather than a
   silent truncation. Same shape as `checkForDuplicateIncludes` beside it.
3. **`Definition.equals` SKIPS Contents fields, so moving a field into
   contents removes it from equality.** `ContextValidationTest` asserted a
   whole `Function` with `mustBe`; after the move that assertion could no
   longer see `requires`/`returns` at all and would have passed with them
   missing. The clauses needed assertions of their own. **Any field-to-contents
   move has this consequence — check every `mustBe` on the container.**
4. **The printer got SIMPLER, not smarter.** `PrettifyVisitor` stopped emitting
   from the `input`/`output` accessors in `openFunction`/`openSaga` and now
   emits `doRequires`/`doReturns` as the contents are walked. Emitting from the
   accessors would have reimposed the very ordering the move removed — the
   clause first, the author's comment after it. **Order is now a property of
   the AST rather than of the printer.**
5. **A named field cannot carry a position, so JSON needed a content kind.**
   `FunctionDto.input` round-trips the VALUE fine and always did; it cannot say
   the comment came first. `$kind: "requires"`/`"returns"` entries now travel in
   the ordered `contents` array, the bucketed fields are read only when a
   document has no ordered contents (reading both would double the clause), and
   both are still written. All four reflective surfaces — parse, prettify, BAST
   (`FORMAT_REVISION` 3 → 4), JSON — now carry position, each with a test that
   asserts ORDER and not merely presence.

**Left open on purpose:** the node stays `TypeRef | Aggregation`. Reid's
`Option[TypeRef]` is the wanted end state but it means dropping the deprecated
inline aggregation, which is a language change with corpus fallout — filed in
BACKLOG § 2 with the verified cost.

---

## A saga body rejected comments because one rule skipped a shared alternative (2026-08-03) — DONE

`867ab0333`. Reported by riddl-generator: `//` between two saga steps was a
parse error whose message never mentioned comments.

`sagaDefinitions` was the only container in the VitalDefinition family that did
not lead with `vitalDefinitionContents` — Domain, Function, Epic and Processor
all do. Since `OccursInSaga` is `OccursInVitalDefinition | SagaStep`, `Type` and
`Comment` were ALWAYS legal saga contents; only the parse rule disagreed with
the AST it fed.

**Three things worth keeping:**

1. **Fix the omission, not the symptom.** Adding `comment` alone would have
   fixed the report and left `type` broken in the same place, for the same
   reason — which the reporter had not noticed. Restoring the shared
   alternative fixed both and made saga consistent with its siblings. When a
   rule is the odd one out, ask what ELSE the others get that it doesn't.
2. **A fixture in a skipped file is not coverage.** The obvious home for the
   new syntax, `language/input/full/context.riddl`, is **skipped by the TatSu
   validator as an include fragment** — the fixture would have looked like CI
   coverage and been none. Checking the validator's own output for a `✓` on the
   file is the only way to know. Same family as the false-green traps: verify
   the gate ran, don't assume it.
3. **A `rep(2)` that looks like a semantic guard usually isn't.** It reads as
   "a saga needs two steps", but the real rule is ValidationPass:2585, which
   counts `sagaSteps` and emits a proper Error with a suggestion. Relaxing the
   parser therefore lost no rule and UPGRADED the diagnostic — a parse failure
   at the wrong token became a validation message that says what is wrong.
   Worth checking for this shape before assuming a parser cardinality is
   load-bearing.

---

## Resolution was never slow; a case class hashed the whole file (2026-08-03) — DONE

Reported by synapify as "ResolutionPass takes 4.3s, likely algorithmic". It is
not algorithmic and it is not ResolutionPass. On the JVM, all ten analysis
passes together cost **0.78x one parse**, and Resolution resolves 3730
references in **44.6ms** — 12µs each.

The real defect: `StringParserInput` is a case class whose first field is
`data: String`, the entire text of a source file. `At` holds a
`RiddlParserInput`; `Identifier` and `Definition` hold an `At`;
`ReferenceMap.Key` holds a `Definition`. So the compiler-generated hashCode
chain meant **every refMap add and lookup hashed a whole source file** — twice
per `Definition.hashCode`, once via `id` and once via `loc`.

The JVM and Native never noticed, because both memoise `String.hashCode` into
the string object. A JS string cannot carry that field. Measured on a 139KB
source: 14ns (JVM), 1ns (Native), **181,187ns (Scala.js)**. Fix: memoise the
hash on the parser input — one field per FILE, nothing per node. Scala.js
`Definition.hashCode` went **384,016ns → 217ns (1,770x)**, now at parity with
the JVM.

**Four things worth keeping:**

1. **A platform asymmetry can masquerade as an algorithm.** Every hypothesis in
   the report — and ours — was about complexity: scope walking, candidate
   rebuilding, linear `Contents` scans. The tell was in the *ratios*, not the
   totals: parse cost 3.2x on Scala.js while Resolution cost 97x. Ordinary
   overhead is uniform; when one number is 30x the others on the same runtime,
   the runtime is doing something different, not the algorithm. **Get the
   cross-platform ratio before profiling anything.**
2. **We tested our favourite hypothesis and it was wrong.** ClassTag dispatch
   was the prime suspect for both of us. Measured, Scala.js runs it **5x faster
   than the JVM** (~21ns), which accounts for ~0.2% of the time. Had we "fixed"
   it we would have shipped a plausible refactor across 13 methods and moved
   nothing. The microbenchmark cost an hour and refuted it in one run.
3. **Case-class hashCode is a hazard on any node holding bulk data.** Nothing
   here was written badly; `At`, `Identifier` and `Key` are all ordinary case
   classes. The cost came from a field three layers away that nobody hashing a
   `Key` was thinking about. When a case class transitively reaches a `String`
   that is a *document*, its generated hashCode/equals are O(document).
4. **`sbt -batch` with several command arguments runs only the FIRST.** Seven
   `'module/testOnly *'` args ran `utils` alone, printed "All tests passed",
   and exited 0. Both the exit code and the word "passed" were honest about the
   14% that ran. Use one `;`-separated argument and **count the `Suites:
   completed` lines against the modules you asked for.** Recorded in CLAUDE.md
   beside the other false-green traps — this is the fifth known member of that
   family, which is itself the point: assume a green suite is evidence only when
   you know how much of it ran.

Verified after the change: JVM 248 suites / 2007 tests, JS 60 / 674, Native
149 / 1058 — **0 failures on all three**. `ParserInputHashingTest` (9 cases)
pins the contract, including `At.isEmpty`'s `source == RiddlParserInput.empty`
identity. Three benchmarks committed, none asserting a timing threshold (see
BACKLOG § 3 on why `BASTPerformanceBenchmark`'s ratio assert is a bad pattern).

**Still owed to synapify:** a before/after of *their* table. riddl cannot
produce it — the Scala.js `PlatformContext` is `DOMPlatformContext`, which loads
by `fetch`, so no riddl test reads reactive-bbq off disk under Node. They own
`AnalysisPassCostTest` and should re-run it against the new build. Said so
explicitly in the task file rather than leaving the criterion quietly unmet.

**Second filing.** The same problem was reported 2026-03-13 at 3.4s and moved to
`task/done/` with an empty Results section and no work done — no performance
commit exists in that file's history. Corrected in place. Its sibling from the
same minute had the identical empty-Results shape but a real fix behind it
(`367669016`), so the placeholder was never the tell; only `git log` was. **A
file's presence in `done/` is not evidence that its work happened.**

---

## `format` renders the declaration (2026-08-03) — DONE

`9c922e42e`. Reported by riddl-generator, whose six line citations were all
exact; task file in `task/done/` with the transcript.

2.0 put meaning into prefixes and suffixes — entity intentions, a context's
intention, `initial`, `yields`, `as <shape>` — and `format` rendered none of it,
so `aggregate consistent event-sourced entity Order` formatted as
`entity Order`. Streamlet was wrong the other way: it emitted `source Ingest`,
the spelling 2.0 deprecated, from the DERIVED shape, while the prettifier
normalizes the same definition to `processor Ingest as source`.

**The fix was never really about `format`.** `RiddlFileEmitter.openDef` already
held the correct composition; the defect was that a consumer could not reach it,
so riddlg had started re-deriving it. `AST.Declaration` is now the one
implementation and `format`, `openDef` and `openState` all use it.

**Two things worth keeping:**

1. **Prove sharing, do not assert it.** Neutralizing the shared prefix reddens
   the four `format` cases AND the prettify round-trip tests. That is what makes
   "one implementation" a fact rather than an intention — if they drift, a test
   fails first.
2. **This is the third instance of the same shape**, after the include walks and
   the ungated checks: knowledge that exists in one place, is unreachable from
   another, and gets re-derived by whoever needs it next. **When a consumer
   reports duplicating our logic, the fix is to expose ours — not to add a
   second copy on our side of the line.**

---

## BAST positions: recoverable, and honest when they are not (2026-08-03) — DONE

`bd9e0a705`. Reported by synapify, which cannot move AnalysisPass off the
Electron main thread without a redundant re-parse.

**The report's diagnosis was wrong in a way worth remembering.** It said BAST
cannot carry positions. BAST carries them fine: `BASTWriter.writeLocation`
delta-encodes the REAL offset, and `DeepASTComparison` already verified offsets
round-trip exactly. `At` has always derived line/col lazily from
`source.lineOf(offset)` — it stores only `(source, offset, endOffset)`.

The defect was that the reader attached a `BASTParserInput` whose line index is
SYNTHETIC (line L starts at L×10000) and then fed it real offsets, so anything
under offset 10000 landed on line 1 at col = offset. **Two halves of one
subsystem with contradictory contracts.**

Fix cost nothing structural: `positionsKnown` on `RiddlParserInput` (default
true, so `At.empty` still reports 1:1 and no golden moved), an optional
`sources` map on `BASTReader.read`, and deleting a cast to `BASTParserInput`
that made supplying a real source a ClassCastException. No format change, no
FORMAT_REVISION bump, no size increase, no filesystem access — which matters
because BAST is read on JS and Native.

**Three things worth keeping:**

1. **Ask what the code already does before believing it cannot.** The lazy
   derivation the "fix" would supposedly need was already `At`'s design; the
   offsets were already preserved. Only the source attachment was wrong.
2. **A confident wrong answer is worse than an absent one.** The old behaviour
   returned line 1 with a plausible column — good enough for a Problems pane to
   point at. Positions now report 0, which is unrepresentable as a 1-based
   position, so a consumer can detect it.
3. **`DeepASTComparison` had carried a comment explaining the wrong line/col as
   a difference in line breaks.** A defect notice written down and lived with,
   which is why this survived. Corrected.

---

## Two over-broad checks narrowed (2026-08-03) — DONE

`0dba8d26b` (Q2) and `30979985d` (Q3). Reported by riddl-examples and
riddl-models against the staged build; task files carry the transcripts.

- **Q2** — `checkYieldConformance` demanded a `yield` from clauses that cannot
  contain one. `StatementParser` grants `yieldStatement` to ProcessorKind
  Entity/Context/Repository only, so a sink handling a `yields`-declaring
  command had no satisfiable spelling: `on other` dodged the yield rule but left
  the epic step unwitnessed under A36. **Our regression** — `0054a8433` scoped
  the exemption to clauses that REFUSE, and a clause that FORWARDS is a third
  case nobody thought of. Now enforced only where a yield is legal.
- **Q3** — Completeness 4b demanded dispatch-to-entity from split/merge/flow,
  whose job is routing between ports. Restricted to `Sink` via `effectiveShape`.

**The pattern, stated once because it has now happened three times.** Both of
these, and the four `@JSExport` helpers before them, were not under-tested —
they were **ungated**. Completeness 4b had zero tests of any kind and could not
even be reached in the corpus, because its outer guard keys off `c.entities` and
every entity there lives in an include. Making the accessors include-transparent
woke it up, and the first thing it did was demand something no model could
provide. **When a check's guard depends on an accessor, ask what happens the day
that accessor starts returning things.**

First fully-clean matrix of the session afterwards: 19 rows, 3,946 tests, zero
failures, no external reds — riddl-examples landed its dokn migration and both
riddl-models suites pass.

---

## Accessors see through `include` (2026-08-03) — DONE

`2a8b87a6b`, `c98e33e5e`, `dbd350020`, `0d83caabc`. Reported by riddl-generator
against the staged build; task file in `task/done/` with the full transcript.

`context.entities` was empty whenever the entity was written in an included
file, while `context.repositories` in the same context answered normally — the
model giving different answers based only on which file the author typed into.
riddlg generated 582 files for reactive-bbq with no entity class among them, and
nothing failed. The 35 named accessors now use `filterThroughIncludes`.

**Four things worth keeping:**

0. **Provenance is riddl's business, not the reader's.** The first cut
   descended `Include` but not `BASTImport`, reasoning that importing a
   COMPILED artifact is a different claim from textual composition. Reid
   overruled it: a client asking what is in a processor wants the full list and
   does not care how each member got there. Both wrappers are descended; the
   method is `filterThroughWrappers`, not `...Includes`. Structure and
   resolution are untouched — see § 3 for the one loose end that leaves.
1. **The gap was never "untested", it was UNGATED.** riddl validates by
   traversing (`HierarchyPass`, `Finder`), so every internal test took that
   path; the accessor path that consumers use had no test at all. Four of the
   eight `@JSExport` consumer helpers — `getEntities` among them — had zero test
   references. Fixing transparency without adding
   `ConsumerReadsIncludedDefinitionsTest` would have reset the clock on the same
   bug. **Add to that suite whenever an accessor is added.**
2. **The compiler contained the same workaround it was being asked about.**
   Seven helpers in AST.scala were written `x.foo ++ x.includes.flatMap(...)`,
   which is exactly the "every consumer reimplements the recursion" complaint —
   and they double counted the moment the accessors worked. Collapsed.
3. **`recursiveFindByType` was riddlg's plan B, and still the wrong fix — but
   not for the reason first recorded.** The note said it would return nested
   sub-contexts' entities; contexts cannot nest, so that shape is a syntax error
   rather than a rare case, and for `Entity` under a `Context` the two agree
   exactly. riddlg filed the correction. The accurate rule is that the two
   ANSWER DIFFERENT QUESTIONS, and they diverge under a Domain (domains nest)
   and for `Type` under a Context (recursive find reaches types inside
   entities). Worth remembering as a habit: a caution that sounds right is still
   worth checking against the grammar before it goes in writing.
4. **No fixture and no `.check` golden moved.** Validation results are
   unchanged, which confirms the defect was purely on the consumer surface.
5. **Verified on all 19 rows, not the 6 that had changed.** After the import
   reversal only `language` and `passes` had been re-run, and the summary still
   quoted pre-reversal numbers for the rest. Reid caught it. Full matrix:
   3,934 tests, one pre-existing external failure. `commands` and `riddlc` were
   the ones that mattered — they carry the external-corpus suites, where a
   double count would show up as a diff in real model output rather than a unit
   assertion. **Re-run everything after a late reversal, not just the modules
   whose tests you expect to move.**

---

## Refusing a command discharges its `yields` contract (2026-08-02) — DONE

`0054a8433`. Reported by riddl-models against the staged build; task file in
`task/done/yields-conformance-forces-refusing-clauses-to-yield.md` with the full
verification transcript.

`checkYieldConformance` required EVERY `on command C` clause to yield C's
declared event. With R1 making `yields` mandatory on any command an
event-sourced entity handles, the ordinary shape — a command accepted in one
state and refused in the others — became unexpressible: each refusing clause had
to yield the success event it had just declined to produce. Fixed by exempting a
clause that refuses, using the `ErrorStatement || RequireStatement` predicate the
sibling check at :509 already had.

**Three things worth keeping:**

1. **The task file proposed keying on `error` alone; that would have fixed half
   of it.** A `require`-based refusal produced the identical error. Testing the
   proposal rather than implementing it is what caught this.
2. **The corpus could not reproduce it.** reactive-bbq reports zero such errors
   in its committed state, because riddl-models backed the conversion out when it
   failed. The defect only appears when you construct the condition. A corpus run
   is not a substitute for building the case.
3. **R1 and the wrong-type branch were deliberately left alone.** Refusing
   exempts a clause from having to yield, not from yielding correctly.

Residual gap (conditional refusals) is recorded in § 3 — it is the honest limit
of the predicate, not an oversight.

---

## Four defect fixes from riddl-models + riddl-vscode (2026-08-01) — DONE

**`@JSExport` on an overridden `toString` breaks ToPrimitive in Scala.js.** The
biggest catch of the batch. Interpolation compiles to JS `+`, so `s"...$loc..."`
threw `TypeError: Cannot convert object to primitive value` — crashing the whole
validation run on JS while the JVM passed. `At` and `URL` both carried it. JS
callers get `toString` from the prototype anyway, so the export bought nothing.
Guarded by `ToPrimitiveCoercionTest`, which is JS-only **by necessity**: on the
JVM every assertion in it passes regardless of the annotation, which is precisely
why it survived. Grep before adding `@JSExport` near any `toString`.

**A swallowed diagnostic hides the bug under it.** The JS `ExceptionUtils` shim
returned `Array.empty` ("can't get stack traces in JS"), so the pass runner's
catch-all rendered EVERY JS exception as a Severe with no text — a blank squiggle
on line 1 in an IDE. riddl-vscode read it as "a rule that lost its message" and
filtered it defensively. Fixing the shim FIRST is what made the crash
diagnosable; the stack trace naming `checkNonEmpty` appeared immediately. A
reporting path that can silently produce nothing is worse than no reporting path.

**The npm template overrides the sbt-ossuminc generator.** `riddlLib/js/
package.json.template` is what actually ships — proved by the published
description and `reactive-systems` keyword, which `build.sbt` does not declare.
The generator was already fixed upstream and it changed nothing. **Check which
source actually produced the artifact before fixing the one that looks right.**

**Two checks must agree on what a domain is.** rc.8's error-sink checks did not:
missing ran per-domain, uniqueness used a recursive find crossing nested `Domain`
boundaries, so a nested model could satisfy NEITHER. Fix: both use the sinks a
domain declares itself (descend through Include, stop at Domain), an ancestor's
sink satisfies a subdomain (nearest wins), and a domain with no processors of its
own is not asked at all.

**Incremental caching must cover everything it claims to validate.**
`IncrementalValidator` fingerprinted Contexts only, so a domain-level edit
changed nothing it could see and it served stale results — dropping real errors
while the user typed. Correctness beat speed: a domain-level change now forces a
full validation.

---

## Infix alternation `A | B` accepted (2026-08-01) — DONE

Accepted as a second spelling of `one of { A or B }`; **not** canonical.
PrettifyPass still emits the words, so a bar-written document normalises on its
next round trip. Both spellings produce the identical `Alternation`, which is
what makes accepting a second spelling safe at all.

**Why `infixAlternation` is tried FIRST, before `predefinedTypes`:** so the two
spellings behave the same. `one of { String or Integer }` today gives
`Path 'String' was not resolved` (alternation operands are type REFERENCES, not
predefined types — verified before choosing). Placing the infix rule later would
have made `String | Integer` a *parse* error pointing at the bar instead. It is
safe to try first only because it REQUIRES at least one `|` — lose that guard
and every lone type reference becomes a one-element Alternation.

`|` is otherwise used only for `described as` margin lines, a different context.

**Reflectivity checklist for a syntax addition** (all done here): parser + EBNF +
regenerated GBNF + a corpus fixture under `language/input/` so the CI TatSu
validator exercises it + a prettify round-trip test + all three rows.
Corpora re-validated: riddl-models 189/189, riddl-examples 9/9.

---

## GeneratorError + error-sink completed (2026-08-01) — DONE

`Operations` is **withdrawn** from the standard module; `HardError` is renamed
**`GeneratorError`** (the name should state the SOURCE — a generator produces
one). riddlg's bug report is the argument for the withdrawal in miniature: the
predefined sink's self-qualified path failed to resolve and the error pointed at
a file the modeller cannot see or edit. The standard library owes a generator the
SHAPE of a notification and a way to NAME its destination — nothing more.

**The unused warning is the design, not a defect.** `GeneratorError` has no
predefined receiver, so validating the module alone reports `Record
'GeneratorError' is unused`. Rather than tolerate it, `PredefinedTerminatorsTest`
asserts it EXACTLY and asserts the converse — a model declaring an error-sink
inlet of that type clears it. Canaried both ways.

**A missing error-sink is a MISSING warning, not COMPLETENESS.** `isIgnorable`
is `severity < CompletenessWarning`, so Completeness asserts structural
incompleteness (unfed inlets, unreachable sinks). "Has not said where hard errors
go" is the "has no author" family. Emitting it as Completeness turned **thirteen
unrelated suites red** for models that were otherwise fine — that was the tell,
and it is the reason to reach for `addMissing` here.

**An error-sink inlet must accept `GeneratorError`** — directly, or via an
alternation including it so a model can route its own error messages to the same
inlet. Otherwise Error: a generator has nothing it can send there.

**Vacuous-test trap, twice in one sitting.** Every error-sink case asserts an
ABSENCE, which a non-parsing fixture satisfies trivially. A `printf "%s"` with a
literal `\n`, then a bad alternation (`A | B` instead of `one of { A or B }`),
each produced green cases proving nothing; only the case asserting PRESENCE gave
it away. `ErrorSinkTest.messagesFor` now FAILS on "Expected one of" rather than
reporting on a model that never parsed. Worth copying wherever absence is
asserted.

**Repo-wide:** `sbt scalafmtCheck` is RED on HEAD independent of this work — 7
committed files reformat (6 in `commands`). Left alone rather than swept into
these commits.

---

## A10 fully registered + duration positivity (2026-07-31) — DONE

`retry` (Saga added), `undo-retry`, `failure-message` — names chosen by
riddl-generator after we held them rather than settling by implementing
first. **That was worth doing**: riddlg pushed back correctly on my
objection to reusing `retry`, pointing out `timeout` already has exactly
that one-concept-two-scopes shape.

`retry`'s optional second argument is a backoff duration, so the temporal
check is now INDEX-AWARE (`temporalArgIndex`) rather than "first argument
of a temporal option". `retry("3")` must stay valid; a bare `"3"` would be
flagged vague if the wrong index were read.

**Non-positive durations are errors**, with their OWN message — vague and
non-positive need different fixes ("state a unit" vs "state a magnitude").
`PT0S` needed care: the ISO-8601 path matches SHAPE only and never parses
a value, so positivity there is a non-zero-digit test. The shape carries
no sign, so zero is the only non-positive ISO case.

**Precedence is a generator contract riddlc does NOT enforce** — a step's
own `retry`/`timeout` wins, the saga's applies to steps without one, else
the A10 default. Recorded in the registry comment because writing it down
is the only way two generators agree.

---

## `activate` accepted as an acquisition verb (2026-07-31) — DONE

`button Checkout activate Confirmation` used to be a bare parse error.
`activate` is now accepted; the other fourteen aliases stay third-person
and their imperatives stay REJECTED, asserted by test so a widening must
be deliberate.

**A grammar change touches THREE artifacts, not two.** The parser
whitelist, `ebnf-grammar.ebnf`, AND `riddl-grammar.gbnf` — the GBNF is
generated by `ebnf_to_gbnf.py` and CI fails on drift. The incoming task
listed only the first two. `--check` was already failing before this
change, so the committed GBNF had drifted independently.

**Scope had a hidden coupling.** Pairing the whole vocabulary would have
required `select`/`choose`/`pick` in `UIVerbs.selectionVerbs` too — it
gates the choice-type check at `ValidationPass:2652` — or those inputs
would parse while silently skipping it. The narrow change avoids it.

---

## Option registry work for riddl-generator (2026-07-31) — DONE

Three riddlg tasks, shipping in 2.0.0-rc.5.

- **`available`/`consistent` widened to Repository.** Computational model
  §5.6: a Repository is a Processor, so its WRITE side is single-writer by
  default and `available` hands arbitration to the storage engine. The
  registry already had this exact shape for `transient`.
- **`timeout` widened to Saga** (Tools-To-Do-List Part A item 10). It is
  the third terminal condition of a `parallel` saga (§9.8) and had no
  expression in the language.
- **`compensate` DEREGISTERED** on Reid's ruling. The decisive fact is in
  the parser: `SagaParser.sagaStep` requires `reverted by`
  UNCONDITIONALLY, so a saga without compensation cannot be written and
  the option distinguished nothing. Its A10 citation was also wrong — A10
  asks for timeout/step-retries/undo-retries/error-string, not this. The
  registry now carries a DO-NOT-RE-REGISTER note with that history.

**Vague durations are now an ERROR.** A `timeout`/`delay` argument that
does not state a unit fails validation: `"30"` is ambiguous between
seconds and milliseconds. Uses `scala.concurrent.duration.Duration`;
ISO-8601 is matched BY SHAPE because `java.time.Duration.parse` is
JVM-only and would make riddlc behave differently per platform.

### The recurring hazard, stated once

Three separate tests this session asserted the ABSENCE of a warning and
passed for the wrong reason:

1. an invalid fixture that never parsed, so there were no messages at all;
2. `showStyleWarnings` being off — the accumulator DROPS StyleWarnings,
   and `pc.options` is global state other suites mutate, so a case passed
   alone and failed in the full suite;
3. `withClue` interpolating Messages — `Message.toString` is unsafe under
   Scala.js and `withClue` evaluates eagerly, so every case failed on the
   JS row while reporting, not asserting.

**A test asserting absence proves nothing until it has been canaried.**
Revert the change and confirm it goes red.

**`RecognizedOptionsTest` has a no-shrink ratchet** over the former
hand-written option lists. Move its baseline only with a reason recorded
inline — never to make a red run green.

---

## Portlet options were never validated — FIXED

`checkDefinition` validated metadata only under `case vd: VitalDefinition[?]`.
`Inlet`/`Outlet` are `Leaf`s, so their options were never checked:
`option zzznotanoption("x")` on an outlet was accepted in SILENCE while the
same typo on a vital definition drew a StyleWarning. Found by
riddl-generator while asking for the `lowering` option — which was the
smaller half of their report.

**Two constraints, both found by breaking tests, not by reasoning:**

1. **Narrow the arm to `Portlet`.** A broad `WithMetaData` arm
   double-validates every definition whose validator calls BOTH
   `checkDefinition` and `checkMetadata` — Constant, Adaptor, Schema and
   others do. Symptom: doubled FigmaRef message counts.
2. **Contents only.** `checkMetadataContents` is split out of
   `checkMetadata` so portlets are checked without inheriting "metadata
   should not be empty" or "should have a description". Routing the
   description check through the shared path made **14 suites** demand a
   description on every type and field.

**Test fixture gotchas** hit while writing the test, worth knowing:
`AbstractValidatingTest` is a FIXTURE spec, so every case body takes
`(td: TestData)` and builds inputs as `RiddlParserInput(src, td)`; an
`outlet` takes a MESSAGE type ref (`command X`), not `type X`; and the bare
shape keyword `flow F is {…}` **no longer parses** — it is
`processor F as flow is {…}`.

---

## CI: two build-gate defects found cutting rc.3 — BOTH FIXED

**1. `target/out` must NOT be cached.** Restoring sbt 2 build outputs into
a fresh checkout leaves sbt believing the meta-build is already built, so
`project/Dependencies.scala` never contributes its symbols and `build.sbt`
collapses with dozens of `Not found: V` / `Not found: Dep` plus an
`Append` ambiguity on a line nobody edited. The cascade points everywhere
except the cause.

**A cache written by a GREEN run is just as poisonous as a stale one** —
#2192 and #2193 restored #2191's successful cache and failed on
markdown-only commits. Dropping `restore-keys` first did NOT fix it; that
only made one run cold by accident. The rule that actually held: every
cold build passed, every cache-restoring build failed. Step removed
entirely; Coursier/ivy2 dependency caches are separate and fine.

**2. `set every Compile/doc/sources := Seq.empty` blanks
`Compile/sources` ITSELF.** `set every` does not respect the `Compile /
doc` scope prefix — sbt logs "Defining Compile / sources, Global /
sources and 75 others". Harmless until the empty-module guard landed,
then every module looked source-less and the guard fired on all of them.
Replaced with `With.NoDocs` on the JVM rows, where the intent is scoped
correctly and survives a workflow rewrite.

**Diagnostic that settles this class of failure fast:** compare
suite/test COUNTS between runs, and check whether the run was cold or
restored a cache. An exit code proves nothing; #2196 is trustworthy
because its counts rose by exactly the 19 tests added.

---

## SysLoggerTest flake — FIXED (was an intermittent CI red)

`SysLoggerTest` captures what SysLogger wrote to stdout by swapping the
**global** `System.out`. sbt runs a module's suites in PARALLEL, so any
other utils suite printing during that window landed in the capture and
the assertion failed on "random garbage". It failed CI run 30552046714
(JVM only; JS and Native fine) on a commit that touched nothing related.

Fixed with `utils / Test / parallelExecution := false`. **The suite's own
`SequentialNestedSuiteExecution` does NOT cover this** — it orders NESTED
suites and says nothing about siblings running concurrently. That
mislead is why the FIXME sat unresolved.

Three tests (severe/warning/info) had been **commented out** rather than
fixed. They are restored and pass: utils went 137 → 140 tests, clean
5 runs in a row.

Cost is negligible — utils' suites take seconds — and it beats a release
gate that fails at random.

**If another module ever grows a stdout-capturing test, it needs the same
setting.** The pattern to avoid is asserting exclusive ownership of a
global stream inside a parallel runner.

---

## sbt-riddl tasks were cached no-ops under sbt 2 — DONE

Reported by the riddl-models session. **`sbt riddlcValidate` reported
success on a corpus it never re-validated.** All six `taskKey[Unit]`
riddlc tasks return Unit, which sbt 2 caches, while the `.riddl`/`.conf`
files they read are discovered INSIDE the body by `resolveConfs` and so
are never declared inputs. Editing sources, `clean`, and deleting
`target/` all failed to invalidate it. First run in a fresh checkout
executes, so CI looked fine while local development silently stopped
checking.

Fixed by wrapping all six in `Def.uncached` — the same lever the compile
hook already used. `riddlcDownload`/`riddlcBinary` were already opted
out.

**The scripted test now runs, breaks the model, and runs again**,
asserting the second run FAILS. The old test invoked each task exactly
once, and a first invocation always executes — which is precisely why
this shipped. Proven by reverting the fix: scripted then fails at
`-> riddlcValidate` with "Command succeeded but failure was expected".

**`bastify` tolerates semantic errors** — given an unresolved path it
parses and writes a BAST with exit 0. Only a model that cannot PARSE
makes it fail, so the bastify assertion needs its own
`changes/unparseable.riddl`. Do not reuse the validate fixture.

Scripted fixtures are checked against riddlc **1.23.0** as well as the
local build, because `sbt-test/.../build.sbt` pins `riddlcVersion`
1.13.0 rather than using the local one.

Two claims in the task file were wrong and are corrected in its Results:
`riddlcDownload`/`riddlcBinary` were never cached, and riddl's own CI
does NOT use `riddlcValidate` (scala.yml runs `sbt-riddl/scripted`; the
corpus is checked by `RiddlModelsRoundTripTest`).

---

## Third-party license notices in `riddlc info` — DONE

`riddlc info` now ends with a one-line-per-project attribution block,
grouped by license, and every distribution ships
`THIRD-PARTY-NOTICES.txt` with the full texts. Both the file name and
`https://ossum.tech/riddl/licenses/` are printed.

**The list is a hand-maintained CONSTANT**
(`utils/.../ThirdPartyNotices.scala`) — not generated, and not read from
a file. Only the JVM build has a filesystem; the Native binary has no
resources at all, and the same text must render in Scala.js. So **it
goes stale silently when dependencies change.** `ThirdPartyNoticesTest`
pins the shape (80 columns, every license group, both links) but CANNOT
know a dependency was added. Regenerate it whenever deps change:

- JVM truth is the staged `riddlc/universal/stage/lib` (what ships).
- JS/Native from `<mod>/Runtime/fullClasspath`.
- Licenses from each artifact's POM in `~/Library/Caches/Coursier`,
  walking to the parent POM when the child declares none.
- **Do NOT take the copyright holder from `<developer>`** — that is the
  first committer, not the holder. For Apache projects it is the ASF;
  read `META-INF/NOTICE` from the jar, which Apache-2.0 §4(d) requires
  be reproduced anyway.

Notices print LAST, below the JVM/OS lines. That ordering lives in
`InfoCommand`, not `InfoFormatter`, so `InfoFormatter.formatBuildInfo`
(no notices) exists alongside `formatInfo` (with them). Calling
`formatInfo` from `InfoCommand` buries the block mid-output — the bug
`InfoCommandTest` now guards.

### Two build defects this surfaced — BOTH NOW FIXED

Writing the notices exposed two dependencies that were shipping by
accident. Both are fixed, and the notices lost a line each.

1. **ScalaTest/Scalactic shipped on JS and Native.**
   `Dep.scalatest_nojvm`/`scalactic_nojvm` were added WITHOUT `% Test`
   at three sites — utils JS, utils Native, language Native — so a test
   framework was a runtime dependency of the native binary and the JS
   bundle (16 artifacts each). Now `% Test`. **`testkit` was left
   alone on purpose**: its MAIN sources use ScalaTest because it is a
   test kit exporting test helpers, so compile scope there is correct,
   and blanket-scoping it would break the published `riddl-testkit`.
   Nothing in the riddlc/riddl-lib chain depends on testkit, so it does
   not leak into the distributions.
2. **logback-core (EPL-1.0 / LGPL-2.1) arrived via
   `airframe-json` → `airframe-log`.** Excluded in
   `Dependencies.scala`. JVM distribution went 21 → 20 jars.
   **This was treated as a runtime risk, not a no-op**: airframe-log
   can bind logback reflectively, so a missing class would surface only
   at runtime. Verified with `riddlc info` AND a real
   `riddlc validate` — airframe-log falls back to `java.util.logging`.

**riddl now carries NO copyleft dependency.** Everything is Apache-2.0,
MIT or BSD-3-Clause. `ThirdPartyNoticesTest` asserts the ABSENCE
(`must not include "logback"` / `"LGPL"` / `"ScalaTest"`) so a
regression fails the build rather than quietly re-adding an obligation.

Certified from clean, tri-platform: tJVM 541, tJS 213, tNative 396,
zero failures.

Filed `ossum.tech/task/publish-riddl-license-page.md` for the web page.
**The URL is compiled into riddlc**, so it cannot be silently
redirected.

---

## Corpus-blocking bug reports from riddl-models/riddl-examples — DONE

Four reports arrived while release/2 was being finished, all filed against the
staged `riddlc`. All four fixed, each with a regression test; task files carry
their results in `task/done/`.

| Report | Cause | Fix |
|---|---|---|
| prettify drops `updates repository` | `RepositoryRef` is a Reference in contents, so the visitor never saw it | `ef4945d14` |
| schema types "unused" | `Schema` refs live in FIELDS, not contents; no resolver case at all | `508403875` |
| `show … to …` unvalidatable | parser hardcoded the empty relationship the validator rejects | `46384b252` |
| MessageFlowPass false positives | nested `send`/`tell` resolved only their `Constructor` operand | `9042c17d6` |

**One pattern explains three of them.** Anything in `contents` that is not a
`Definition` — a Reference, a `Comment` — is invisible to the generic machinery,
and every pass has to reach for it by hand. Comments (#70) were the first
instance, `RepositoryRef` the second, and a `Schema`'s field-borne refs the third.
Expect the next one to have the same shape.

**`quietly` is the instrument for resolution-without-policing.** Populate the
refMap so downstream passes can look references up; do NOT start reporting
references that have never been checked. Nested `send`/`tell` use it, as A55's
`ValueRef` does. Ignoring this took `RiddlModelsRoundTripTest` from 106 failures
to 180 in one change, when a strict schema resolution began rejecting clauses
nothing had ever validated.

**`of <name> as type <T>` is STRICT, by ruling.** A path landing on an Entity is
an error even though it parses: the syntax says `type`, so it must be a type.
This surfaced 202 errors across 186 of 187 riddl-models — nearly the whole corpus
— and the ruling was held with those numbers in hand. The corpus is being
corrected. `SchemaUsageTest` pins the semantics.

**`when prompt("…")` (`7147a0039`).** A54 made a bare `"x"` a literal and
`prompt("x")` an AI-evaluated value, but a `when` condition could only be written
as a bare string and `prompt(...)` did not parse there at all. It does now and
the bare form is deprecated. Carried through all six surfaces; BAST condition
flag 4, FORMAT_REVISION 1 -> 2. `matchGuard` needed nothing — it never accepted
a string.

**A fifth of the same family: a body-less `state` lost its metadata**
(`d576ce59c`). `closeState` called `closeDef` only when the state had contents,
and `closeDef` emits the closing brace AND the metadata — so `state X of record
R with { … }`, having no brace to close, lost its `briefly` and `described as`
too. 41 states across 41 corpus files, 82 missing warnings, blocking
canonicalisation. Audited every other closer: all call `close(x)`
unconditionally, so this was the only guarded one.

**`IncludeHygieneTest` now fails DETERMINISTICALLY in a full `passes` run** while
passing in isolation. Proven independent of the prettify fix by stashing that
change and re-running. It is the known `PlatformContext.withOptions` defect —
no try/finally, so a throwing test poisons global options for later sequential
suites. Intermittent before, consistent now, so CI will see it. Unfixed.

**Two tests no longer fetch `dokn.riddl` over the network** (`aecf4392e`).
`RiddlParserInputTest` asserted byte offsets into a file in *another repository*,
so migrating riddl-examples to 2.0 broke it — catching a correct migration, not a
defect, and aborting a coverage run. It now reads
`language/input/parser-input/offsets.riddl` through a local file URL. The JS
`TopLevelParserTest` parses an inline model. `RunRiddlcOnRemoteTest` is left
alone: the remote IS its subject.

## #70 — the JSON round trip is a real fidelity check now — DONE

The AST<->JSON round trip was checked for IDENTITY — `root -> json1 -> root ->
json2`, asserting `json1 == json2`. That is a good check of determinism and
re-readability and a **useless** check of fidelity: a construct the serializer
drops is missing from both sides, so identity holds over the wreckage. It was
holding over 242 lost nodes.

**Two new checks, both in `Root2JsonFixturesTest`** (JVM, walks
`language/passes/riddlc/commands input`, 109 fixtures of which 84 are standalone
models):

1. identity, as before;
2. a **census** — count every node by kind, including metadata, in the original
   and the re-parsed tree, and require them equal. This is what catches a
   dropped construct.

Both name their skips and assert a floor on how many models were actually
compared, so neither can go vacuously green.

**What the census found, and what was fixed:**

- **25 definitions** (types, fields, schemas, authors, streamlets, inlets, a
  connector). The AST unions had widened in release/2 and the DTOs never
  followed: a domain gained connectors and repositories, every processor became
  port-bearing so an entity may own streamlets, a root may carry a top-level
  author. `JsonifierPass` now records which children each parent consumed and
  reports the rest as `droppedKinds` — that guard found all five gaps in one
  run and will find the next one the day a union widens again.
- **45 metadata nodes**, 31 of them block descriptions. Rich metadata rode on
  seven DTOs; RIDDL lets any definition carry it. Every definition DTO carries
  it now.
- **171 comments.** `Comment` is a `RiddlValue`, not a `Leaf`, so it arrived at
  `processValue` (which did nothing) rather than `processLeaf` — invisible to
  the pass AND to its own drop guard.

**Known gap, pinned at exactly 3 occurrences:** a comment opening a `group`
body. The parser puts it in the group's contents, but `AST.OccursInGroup` is
`Group | ContainedGroup | Input | Output` and admits no `Comment`, so there is
no legal way to rebuild it. `Contents` is an opaque `ArrayBuffer`, which is how
the parser gets away with it at runtime. **The parser and the AST union
disagree — that is an AST bug, not a serializer bug.** Fixing it means widening
the union, which touches BAST and prettify. Awaiting a decision.

**`Root2JsonCorpusTest` had been passing without running.** It drove off the 187
checked-in `.bast` files in `../riddl-models`, which are at `formatRevision` 12
against a current 25; `Header.isValid` rejects a mismatch, every read failed,
every failure was silently skipped, and both assertions reduced to `0 mustBe 0`.
It now drives from the `.conf` entry points' sibling `.riddl` sources and
reports parse failures rather than skipping them. It is EXPECTED RED until the
corpus is migrated (standing policy), alongside `RiddlModelsRoundTripTest`.
This is a fourth member of the "suite passes without running" family catalogued
in CLAUDE.md — and the first one where the vacuum was caused by a data file
going stale rather than by test-framework misuse.

## JSON fidelity ratchet — DONE (reached zero)

Follow-on from #70. `Root2JsonFixturesTest` gained a third check —
**prettify agreement**, `root2RiddlSource(root0) == root2RiddlSource(root1)` —
which sees lost or reordered FIELDS that neither `json1 == json2` (blind to
anything dropped on both trips) nor the node census (counts nodes by class) can.
It landed as a ratchet at 63 divergent fixtures and was driven to **0**; the
`DivergentCeiling` constant is gone and the suite asserts `divergent mustBe
empty`. JSON is now RIDDL's fourth fully-reflective surface, alongside prettify,
BAST and the parser.

**The mandate that shaped it:** reflectivity is not a metric to improve, it is a
binary property. `root -> JSON -> root` recovers the EXACT AST, *including the
order of definitions within their parent*. A ceiling above zero is a standing
statement that the surface is not reflective.

**What was actually wrong, in the order fixed:**

| Cause | Ratchet |
|---|---|
| aggregate flavour — `type X is {…}` returned as `record X is {…}` | 63 → 62 |
| **source order** — per-kind buckets, the dominant cause | 62 → 49 |
| `String` bounds rendering | 49 → 24 |
| TypeRef keyword on ports and inputs | 24 → 14 |
| on-clause `from`; group/input/output alias | 14 → 12 |
| metadata order (`with { … }` buckets) | 12 → 7 |
| `briefly`'s position; `refJs` not writing the alias | 7 → 2 |
| a use case's steps interleaved with its comments | 2 → 0 |

**The shape of the fix.** A container's children travel in ONE ordered
`contents` array of `$kind`-tagged entries, and a `with { … }` block's entries
in `metadata.items`. The per-kind buckets are still READ (so older documents
load) but never written; `parseJsonWithMessages` reports a `Deprecation` naming
the containers that used them.

**Traps worth remembering — all four are the same shape, a second code path
that quietly disagrees with the first:**

- **upickle tags sealed hierarchies.** Making the DTOs extend a `sealed trait`
  silently added `$type` to every object in the schema; the round trip still
  agreed with ITSELF, so the fixtures test stayed green at 85/85 and only the
  hand-authored `json-examples` caught it. `ContentDto` is a Scala 3 UNION —
  which is also how the AST models the same idea — so derivation is untouched
  and exhaustivity is still checked.
- **Hand-written codecs drop new fields.** `RecordDto.comments` and
  `RefDto.keyword` were both added to the case class and both went on being
  dropped, because `writeTypeExpr` and `refJs` are hand-written rather than
  derived. Anything in `JsonModel`'s manual codec section needs the field added
  in two places.
- **The tag key is `$kind`, not `kind`.** `OnClauseDto` and `SchemaDto` carry a
  `kind` FIELD of their own and `ujson.Obj.from` keeps the last of a duplicate
  pair, so the tag silently overwrote the data.
- **`withContents` overwrote a container that built its own.** A use case reads
  its steps straight off the node rather than from the scope stack, and the
  generic attach-the-kids step replaced them with just the comments.

**The wrappers are represented too, and `NotRepresented` is GONE.** `Include`
and `BASTImport` are ordered content entries carrying their already-loaded
contents nested, so read-back still needs no I/O and stays Native-safe. Two
traversal notes: `Pass.traverse` deliberately does NOT push a scope for an
`Include` (its children belong to the enclosing container), so `JsonifierPass`
overrides `traverse` to give it one; `BASTImport` uses the `openBASTImport` /
`closeBASTImport` hooks `HierarchyPass` already provides. The census now counts
every kind with no exclusions and reports `lossy=0`.

`ULIDAttachment` is represented as an ordered metadata item. Note `metaOf`'s
"is this metadata empty?" guard had to learn about the ordered items, or a block
containing ONLY a kind with no bucket of its own vanished entirely.

**Still open:**

- `Root2JsonCorpusTest` red by standing policy (2 of 189 external models fail to
  re-parse: `reactive-bbq.riddl`, `fund-accounting.riddl`).
- **`Nebula` stays deferred, and the ledger's reason is now the honest one:** it
  is not a child of any container and never appears in a `Root`, so it is not a
  fidelity gap at all. Representing it would mean a new top-level document shape
  (a `parseJsonNebula`), which is a feature, not a reflectivity fix.
- ~~**`attachment ULID is "…"` appears NOT TO PARSE**~~ — **FIXED since, and
  this note was stale.** Re-checked 2026-08-03: `CommonParser.scala:360` now
  factors `attachment` out and alternates the BODIES
  (`ulidAttachmentBody | namedAttachmentBody`), so there is nothing to backtrack
  over, and `language/input/attachments.riddl` is a fixture covering all three
  forms. `riddlc parse` on it reports 0 errors. Left visible rather than deleted
  because a stale "known bug" is worse than none — it sends the next reader
  chasing something already repaired.

## Two parser fixes found by the JSON work — DONE

**`attachment ULID is "…"` could not be parsed at all.** `Keywords.keyword` ends
in a cut — `P(key ~~ &(isNotKeywordChar))./` — so once the `attachment` keyword
matched, the enclosing `|` in `metaData` could not backtrack and whichever
attachment rule came first won outright. The general rule was first, so
`ulidAttachment` was unreachable and the ULID form failed where a mime type was
expected, having never been tried. Reordering would only have broken the other
two the same way; the prefix had to be FACTORED so the keyword is matched once,
ahead of the choice. `bastImport` in the same file was already factored exactly
this way, with a comment describing the identical hazard.

The construct had **no fixture and no test anywhere**, which is how a documented
piece of syntax stayed unreachable unnoticed. Both added:
`language/input/attachments.riddl` covers all three forms for the CI grammar
validators, and `MetaDataTest` gains two cases — including an ordinary
attachment NAMED `ULID`, which proves the branches backtrack against each other
rather than the first winning.

**`state X is <recordRef>` is deprecated.** `of` is the canonical 2.0 spelling.
`is` was also accepted and — since `is` is itself optional — so was nothing at
all, which left one keyword doing two jobs in a single production: `stateBody`
already uses `is` to introduce the BODY, as every other definition does. The old
spellings still parse (they are used throughout the suite and the external
corpus) and now emit a `deprecation`. `StateRecordIntroTest` pins both halves —
that they still parse, and that they say so. No `.check` golden moved, because
every fixture already writes `of`.

## A55 — optional local name binding for the on-clause message — DONE

`on foo: command Foo { … }` binds an optional local name to the handled
message. The `:` is ordinary **type ascription** — the same rule as
`let x: T = …` and a field declaration `p1: String` — so the parser reuses
`HandlerParser.maybeName`, the very combinator the `from <name>: <origin>`
clause already uses. `when` is untouched.

**Slice 1 (done).** `binding: Option[Identifier]` on `OnMessageLikeClause`
and on both concrete nodes. It sits **immediately after `from`, without a
default**, because `@JSExportTopLevel` requires defaulted parameters to be
trailing and `contents`/`metadata` are defaulted. All four reflection
surfaces carry it: prettify, BAST (no new node tag — the existing on-clause
sub-discriminators 2 and 4 grew a field; `FORMAT_REVISION` 22 → 23, and the
checked-in `NotImplemented.bast` fixture's header was bumped to match), JSON
(`OnClauseDto.binding` is **defaulted** so pre-A55 JSON still reads), and the
EBNF/GBNF grammars plus a corpus fixture.

**Adjacent bug fixed in slice 1:** prettify never emitted an on-clause's
`from [<name>:] <origin>` clause at all, so `on command C.DoIt from di:
context C` silently lost its origin on **every** round trip. It is emitted
now, in the same `openDef` slot the binding uses.

**Slice 2 (done): `ValueRef` is resolved by the RESOLVER, not by hand.**
A54 had `ResolutionPass` skip `ValueRef` outright and matched only
`path.value.last` in validation against three in-scope field sources — so
`garbage.nonsense.conditionRed` validated whenever `conditionRed` was a
field of the handled message. The leading components were never examined.
That hole is a symptom of the duplication, and it closes as a consequence
of removing it.

- The three `case _: ValueRef => ()` opt-outs are gone. A ValueRef is now
  queued and resolved in `postProcess` (its value-scope anchors are reached
  THROUGH other references, and the pass visits definitions in source
  order, so a handler written above the state it reads must not lose).
- Only the ANCHOR choice differs from an ordinary reference: the on-clause
  BINDING, else a field of the handled message / entity state / function
  `requires` input, else the ordinary `findAnchor` route (which covers
  qualified paths like `GState.active` and bare `constant` names). The rest
  is `resolvePathFromAnchor`'s existing walk — no new traversal machinery.
- **`let`-locals stay LEXICAL.** A `let` is not a Definition and is
  statement-ORDERED (visible only after its declaration, shadowed by inner
  blocks), which the symbol table cannot model. They stay threaded by
  `checkStatementScopes`; everything else goes through the refMap. The
  ValueRef walk therefore runs QUIETLY (`ResolutionPass.quietly`) — only
  validation knows whether a failure is real, so it owns the diagnostic.
- **A `let`'s type is now INFERRED from its expression** when it has no
  `let x: T = …` annotation, which is what makes `let bar = foo; bar.a`
  work. `validateForeachCollection`'s "has no declared type" complaint is
  reworded to "no declared or inferable type" and fires less often.
- `whenValueRefCategory`, `validateComparand`, `comparandCategory`,
  `matchSubject*` all read the refMap now; `valueAllowedFields` and
  `constantOf` are deleted (the resolver does that lookup).
- Warnings: a local shadowing an outer definition → Warning; a binding
  colliding with a field of the message/state → Warning (legal: bare `foo`
  is the binding, `foo.foo` is the field); a local name not BEGINNING with
  a lowercase letter → StyleWarning (camelCase stays legal).

**Latent bug found and fixed:** `findMatchingCandidate`'s on-clause arm was
guarded by `omc.msg.id.nonEmpty`, but `Reference.id` is a reference's
OPTIONAL LOCAL NAME (what `from di: context C` sets) and no `MessageRef`
ever carries one — so the arm was **unreachable**. The intended test is
`omc.msg.nonEmpty` (a non-empty pathId), and it is what lets an
`on foo: command Foo` binding walk `foo.someField`.

## #60 Slice 2 — the predefined `Riddl` standard module — DONE

Two terminators, available to EVERY model with no `import` and no author
declaration: **`BottomlessPit`** (a `sink` that consumes everything and
emits nothing) and **`ForeverEmpty`** (a `source` that never produces).
They exist because under the unified streaming model every port is the
endpoint of exactly one connector (A31), so every outlet must terminate
somewhere and every inlet must be fed.

- **The module is readable RIDDL, not hand-built AST.**
  `language/.../PredefinedModule.scala` holds the source in a string
  constant, parses it once via `TopLevelParser.parseString`, and caches the
  resulting `Module` singleton (so `eq` comparisons are meaningful).
  Streamlets and the `Drain` type (`type Drain is Anything`) live DIRECTLY
  in the module — `ModuleContents` is the wide `NebulaContents` union, so
  no domain/context wrapping.
- **The seam is the SYMBOL TABLE, not the AST.** `SymbolsPass.postProcess`
  seeds `predefinedSymTab`/`predefinedParentage` — **two NEW maps on
  `SymbolsOutput`, deliberately separate** from `symTab`/`parentage`.
  Lookups consult the user's tables first and fall back to the predefined
  ones. Consequences: (a) `AnalysisResult.domains/streamlets/…`,
  `UseCaseWitnessPass`, and `foreachOverloadedSymbol`, which all ENUMERATE
  `parentage`/`symTab`, still see only the user's model (the first cut
  seeded the shared maps and broke `AnalysisPassSpec`); (b) a user
  definition with a colliding name WINS structurally, with no ambiguity
  and no message.
- **Non-injection is the invariant.** The module never enters
  `Root.contents`. A terminator-free model's AST, prettify, BAST bytes and
  JSON are unchanged — asserted directly (BAST bytes before-passes ==
  after-passes; same for JSON).
- **Exemptions** (all by REFERENCE IDENTITY against the singleton, never by
  name): A31 port cardinality; unattached-port; isolated-streamlet;
  source→sink and sink←source reachability (reaching `BottomlessPit`
  TERMINATES a pipeline; `ForeverEmpty` ORIGINATES one); handler
  completeness (Empty/PromptOnly). Plus a general rule in
  `validateConnector`: a port whose type is `Anything` is compatible with
  every other type, which is what lets one drain absorb any message type.
- **EBNF drift found and fixed:** `streamlet` was
  `source|sink|flow|merge|split|router|void` — missing `processor`, so a
  `processor` written directly in a *module* parsed with fastparse but not
  with the published grammar. Added `| processor`; GBNF regenerated
  (296 rules, all validators pass). `riddl_grammar.lark` was left alone —
  it has no `processor` rule at all and is separately stale.
- **Grammar coverage:** `language/input/predefined/riddl-standard-module.riddl`
  is a verbatim copy of the constant so the CI TatSu/GBNF validators scan
  it; `PredefinedModuleSourceTest` (JVM) fails if the two drift.

## #60 Slice 1 — `Anything` replaces `Abstract` — DONE

The dual of `Nothing` already existed as the `Abstract` predefined type
(`isAssignmentCompatible = true`, plus a both-directions special case in
the `TypeExpression` base). Renamed the AST node to `Anything`; `Abstract`
survives as a deprecated *input* spelling and a deprecated *Scala* alias.

- **AST** — `case class Anything(loc)`; `@deprecated type Abstract =
  Anything` + `@deprecated val Abstract: Anything.type = Anything`, so
  `Abstract(loc)` AND `case Abstract(loc)` both still compile downstream
  (test in `TypeExpressionTest`). Nothing internal touches the alias, so
  `-Werror` stays quiet.
- **Parser** — `otherPredefTypes` accepts both; `Abstract` yields an
  `Anything` node plus exactly ONE `deprecation(...)` (same mechanism as
  `reply`→`yield` / `prompt`→`do`).
- **Prettify/JSON** — both key off `getClass.getSimpleName`, so output is
  now `Anything` for free. JSON still ACCEPTS `"Abstract"` on input and
  normalizes it to `"Anything"` on output.
- **BAST — the tag did NOT change** (`TYPE_REF`/subtype 99), so the wire
  format is identical and `FORMAT_REVISION` stayed at **21**. Guarded by a
  `BASTRoundTripTest` case asserting `Anything` and `Abstract` sources
  produce byte-identical BAST.
- **Fixture** — `language/input/full/domain.riddl` now has one `Anything`
  type and one `Abstract` type, so the CI TatSu run covers both arms.

**Found along the way:** `passes/.../prettify/RiddlFileEmitterTest.scala`
is `abstract` with **no concrete subclass anywhere** — it compiles but has
never run. Worth wiring up (or deleting) separately.

## Session 2026-07-26 — Unified streaming processor model (A37/A31/A32/A6)

Shipped a large release/2 change (27 commits, pushed to origin at
`4af86d67`) unifying the processor model, plus the earlier A9b tail and
a deprecation-logging fix.

**What changed & why.** Every `Processor` (context/entity/projector/
repository/adaptor + a new generic `processor` keyword) can now declare
inlets/outlets and an optional `as <shape>` ascription; shape is
otherwise derived from arity. The old `source/sink/flow/merge/split/
router` keywords are deprecated aliases (synonyms: cascade/fanin/
broadcast/fanout). Contexts gained an optional intention prefix
(`application|external|gateway|service`). Validation added A31
one-connector-per-port, `as`/arity check + omitted-shape nudge, A37
intention rules, option deprecations, and A6 `tell` reachability. All
reflection surfaces updated (Prettify, BAST `FORMAT_REVISION` 10→11,
JSON, EBNF+GBNF). Executed as an 18-task plan via subagents + a
whole-branch review. Design docs live in `docs/superpowers/{specs,plans}/
2026-07-26-unified-streaming-processor-model-*`.

**What went wrong (root causes worth remembering).**
- I added the `Intention` enum *between* `@JSExportTopLevel("Context")`
  and `case class Context`, orphaning the annotation onto the enum →
  `cJS` broke. Root cause: I verified AST edits with `cJVM` only.
  Lesson: AST/`@JSExport` edits need `cJS`+`cNative`, not just `cJVM`.
- `test`/`tJVM` resolve to **`testQuick`**, which incrementally SKIPPED
  language/passes after a fix (ran 0 tests) even with the `ac` cache
  cleared — a false "green". Root cause: testQuick dependency tracking,
  not the action-cache. Lesson: for a real full run use
  `language/testOnly *` / `passes/testOnly *`, not `test`.
- Parser `warning()`/`deprecation()` were silently dropped on a
  *successful* parse (`parseRule` only returned the message buffer on
  fastparse failure). Fixed by threading parse messages via
  `PassInput.parseMessages` → `PassesResult.additionalMessages`; a
  final review caught that the first fix only covered `parseAndValidate`
  (not `parse`/`stats`/`bastify`) and it was extended.

**Unfinished / awaiting others.**
- **#56 corpus migration** (riddl-models, riddl-examples) to the new
  syntax — the external worker is actively migrating (commands tests
  went 51→232 passing during the session). Task drops filed this
  session (see `../riddl-models/task/`, `../riddl-examples/task/`).
- A37 **rule 3** (UI-groups-only-in-application) is scoped to
  explicitly-declared non-application intention; tighten to also fire on
  intention-less contexts AFTER the corpus is migrated to mark UI
  contexts `application`.
- A6 tell-reachability is direct-connector only (transitive deferred);
  it will warn broadly on legacy connector-less `tell` — expected.

**Method worth reusing.** Subagent-per-task with file-based briefs/
reports (kept controller context lean across 18 tasks), a git-ignored
ledger at `.superpowers/sdd/progress.md` for compaction recovery, and a
final whole-branch review subagent → one fix pass. Each task verified
green before commit.

---

## Deferred — blocked on prerequisites (do NOT start yet)

### #45 — `put`/`get` UI-boundary statements — DEFERRED (out of order)
Prereqs first: the UI / application (output-input-triplet) model must land
before this makes sense. Design as discussed, for when it's unblocked:
- `put <value> to output <outputRef>` (push to a UI Output from an on-clause);
  `get`: `let <id> = get from input <inputRef>` (pull, its own statement kind,
  not an extension of LetStatement). Revive `put`/`get` keywords (they were
  storage statements long ago, removed when repository sufficed).
- Completes the boundary-statement census: send/tell = streaming, call =
  function, put/get = UI.
- **can-fail**: both can fail (put when UI absent/headless, get when input
  unset). Model via a `def canFail: Boolean` on `Statement` (default false;
  true for Send/Tell/Put/Get) — one source of truth shared with #12.
- **Witnessing** (greenfield — no existing use-case-realization validation):
  a `ShowOutputInteraction` witnessed by a `put` to that output; a
  `TakeInputInteraction` by a handler on the input's message type OR a `get`
  referencing it. Emit CompletenessWarning for an unwitnessed step.
- Open question for when unblocked: which handler statement-sets may contain
  put/get.

### #12 — Single failure point per saga do-block — DEFERRED
Blocked on the **complete can-fail census**. A saga step's do-block is
all-or-nothing (undo assumes all-or-none happened); warn when it contains > 1
potential failure point — a statement-kind count. Census: send, tell, call,
yield, put, get CAN fail; let, set, when, match, foreach CANNOT.
**Why deferred, not done-partial:** `call` doesn't exist, `yield` (≈ reply) is
TBD, `foreach` is new, and put/get are behind #45. Shipping with only
send/tell would give false "single failure point" passes that flip to warnings
later — worse than waiting. Build once, correctly, after the census statements
exist (via the shared `Statement.canFail`).

## Connectors at Domain scope (2.0) — DONE, internal green

Branch `release/2`. Mirrors the Repository-at-Domain-scope feature.
- **AST**: `OccursInDomain += Connector`; `Domain extends
  WithConnectors[DomainContents]`.
- **Parser**: `DomainParser.domainDefinitions += connector` (StreamingParser
  already mixed in). EBNF `domain_content += connector`; GBNF regenerated.
- **Validation** (reworked `StreamingValidation.checkConnectorPersistence` →
  `checkConnectorPlacement`; the old version `require(false)`-crashed on a
  connector with no context — would have crashed on domain-scoped ones):
  resolve each end's context AND domain (`domainOf` = first Domain in
  `parentsOf`), take the connector's own scope from
  `symbols.contextOf(connector).isEmpty`, then:
  1. ends in different domains → ERROR (domain-analysis failure; terminal).
  2. domain-scoped + ends in same context → ERROR (over-scoped).
  3. context-scoped + ends cross contexts → ERROR (under-scoped).
  4. domain-scoped + cross-context + not `persistent` → **CompletenessWarning**
     (so AI can adapt) — durability at a context boundary can be model
     correctness, not just deployment.
  Existing "remove persistent when same-context" warning preserved verbatim.
  Rules are conservative: only fire when BOTH ends resolve (no false positives
  on unresolved refs).
- **Reflection**: prettify (`doConnector`) + BAST (`writeConnector`/
  `readConnectorNode`) already generic; proven by `ConnectorDomainScopeRoundTripTest`
  (prettify) + a `BASTRoundTripTest` case.
- **Tests**: 5 rule cases in `StreamValidatorTest` + 2 round-trips + EBNF↔
  fastparse parity (`language/input/domain-connector.riddl` +
  `ConnectorScopeFileTest` + TatSu). **Internal suite: 0 failures.**
- **External**: riddl-models has cross-context connectors at context scope
  (`reactive-bbq`'s 3, plus more) → correctly caught as under-scoped ERRORs
  (feature working on real data). Committed `90fdae10`, pushed; CI run
  30176269811 is red **only externally** (JS green; JVM/Native red with **0
  non-external failures**, 46 connector-scope errors across riddl-models).
  Goes green once riddl-models `main` is conformed — tasks dropped in
  `../riddl-models/task` + `../riddl-examples/task`.

## Explicit `initial` marker on states & handlers (2.0) — DONE, tri-platform green

Branch `release/2`. Roadmap item #14. An optional `initial` keyword before
`state`/`handler` makes the starting state (and the live-after-morph handler)
explicit and refactor-safe under reordering; unmarked models keep the
first-declared semantics.

- **AST**: `isInitial: Boolean = false` added (last field, after metadata) to
  both `State` and `Handler`. Defaulted + last → `@JSExportTopLevel` safe.
- **Parser**: the marker is discovered *after* the full set is parsed so the
  "first one is initial" default is position-based, not lexical. `EntityParser`
  helpers `markFirstHandlerInitial` (per-state) and `defaultEntityInitials`
  (marks first State if none marked; if a single state, marks the first
  entity-scope Handler) rebuild the first-of-type via `.copy(isInitial=true)`
  when none is explicitly marked. **Cut-collision trap** (same shape as the
  handler-kinds `on` cut): `Keywords.initial` cuts, so a plain `initial`
  alternative can't backtrack into `state`/`handler`. Fixed with the
  non-cutting `Keywords.maybeInitial: P[Boolean]` (`(kw ~~ &(isNotKeywordChar))
  .!.?` — the `.!` is required; `.?` on a Unit parser yields Unit, not Option).
- **Validation** (`ValidationPass`): >1 `initial` handler in a state → ERROR;
  >1 `initial` state in an entity → ERROR; if the entity has ≤1 state, >1
  `initial` entity-scope handler → ERROR. Each with a `suggestion`.
- **Reflection (all 3 surfaces)**: BAST writer/reader U8 flag on State+Handler,
  `FORMAT_REVISION` 7→8; Prettify emits `initial ` prefix (`PrettifyVisitor`
  openState, `RiddlFileEmitter.openDef` for Handler) — emitted even on the
  defaulted-first so the round-trip is AST-preserving; JSON `StateDto`/
  `HandlerDto` gained `isInitial`, wired in `JsonifierPass` + `JsonAstBuilder`.
- **EBNF↔fastparse parity**: `["initial"]` on the `state`/`handler` rules; GBNF
  regenerated (263 rules, validator PASS); fixture
  `language/input/initial-marker.riddl` parsed by both fastparse
  (`InitialMarkerFileTest`) and TatSu.
- **Tests**: `InitialMarkerTest` (4 validation cases), `InitialMarkerRoundTripTest`
  (prettify RT), BAST + JSON round-trip cases extended, `InitialMarkerFileTest`
  (parity). `KeywordsTest` 144→145. **Tri-platform: JVM 1067 pass / 0 internal
  failures; tJS + tNative green.** (The lone JVM external failure is the
  pre-existing connector-scope conformance in riddl-models' `reactive-bbq`,
  documented above — unrelated to this feature.)

## A9 — Function/Saga `requires`/`returns` as named type refs — DONE

Branch `release/2`. Roadmap item **A9** (first of the 37-task release/2 queue,
see the To-Do list). `requires`/`returns` on `Function` and `Saga` now take a
**named `TypeRef`** (any type — so `type Age = Integer; function F is { requires
Age returns Age }` works for unary/nullary fns) instead of only an inline
aggregation. Field type is the union `Option[TypeRef | Aggregation]`.

- **Not aggregate-restricted** (user ruling): `TypeRef`, not a
  Message/Record-only ref. The `AggregateRef` hierarchy cleanup the user also
  wants (make `MessageRef` = the 4 real messages, reparent `RecordRef` under a
  new `AggregateRef`) is a **separate, filed task** (queue item **A9b**, #54) —
  A9 uses `TypeRef` and doesn't touch the ref hierarchy. Audit finding for A9b:
  no abstract `case _: MessageRef` matches exist, but records DO flow through
  `MessageRef` via the `messageRef` parser (feeds send/tell/on/morph/reply) +
  BAST/JSON, so the reparent changes those statements' semantics — hence its own
  task.
- **Non-breaking**: inline `requires { … }` still parses/validates but emits a
  **new `Messages.Deprecation` kind** (severity 3, shown with warnings, never
  blocks; `addDeprecation` helper; `justDeprecations` filter). External corpus
  does NOT break (deprecation is a Warning). Advisory migration tasks can be
  dropped in riddl-models/riddl-examples later.
- **Gap closed**: `validateSaga` and the resolution pass's Saga case never
  touched input/output before — A9 wires both (new `SagaValidatorTest`).
- **Reflection (all surfaces)**: parser `funcInput`/`funcOutput` →
  `(aggregation | typeRef)` (widen each branch to the union — fastparse `|`
  otherwise infers the LUB `RiddlValue`); resolve via `resolveATypeRef`;
  validate via `checkTypeRef` (+ deprecation on inline); Prettify emits
  `ref.format`; **BAST** discriminator byte (0=ref,1=agg) + `FORMAT_REVISION`
  **8→9**; **JSON** new `ArgDto(ref, fields)`. EBNF `func_input`/`func_output`
  → `( aggregation | type_ref )`, GBNF regen (263 rules), TatSu ✓ on new fixture
  `language/input/requires-returns-ref.riddl`.
- **Tests**: `FunctionValidatorTest` (+2), `SagaValidatorTest` (new),
  `RequiresReturnsRefFileTest` (parity), `RequiresReturnsRoundTripTest`
  (prettify), BAST + JSON round-trip cases. `everything.check` / `saga.check`
  regenerated (saga validation gap now fires field warnings on the inline form).
  Ledgers updated: `MESSAGE_SUGGESTIONS.md`, `JSON_COVERAGE.md`.

## A26 — Pure-only functions — DONE

Branch `release/2`. Roadmap item **A26** (2nd of the 37-task queue). A `Function`
body must be pure: it may not write entity state (`set`/`morph`/`become`),
`send`/`tell`, or `reply`. `require`/`error` (refusal) and pure computation
(`let`/`when`/`match`/`prompt`/`code`) stay legal. Enforced at **parse time**
(user's choice) so purity is structural — effect statements can't enter a
function's AST (which A23 relies on).

- **One-file change** in `StatementParser.scala`: `messagingStatements` and a new
  `setStatements` helper reject `send`/`tell`/`set` for `ProcessorKind.Function`
  with `Fail.opaque` messages; the `statement` dispatcher's new `Function` case
  appends a `morph`/`become`/`reply` `Fail.opaque` after `base`.
- **Parser gotcha (memory'd):** the ban must SUBTRACT inside `base` (mirror the
  `ActivationClause` pattern) or APPEND after base. Do NOT prepend
  `keywordAlt ~/ Fail | base` — it breaks `functionDefinitions`' `rep`
  termination at `}` so even valid pure functions fail to parse. Cost me a few
  iterations; see [[statement-restriction-parser-pattern]].
- **Deferred:** "may not READ entity state" — structurally undetectable today
  (function conditions/lets/expressions are opaque `LiteralString`s; no
  structured field reads exist). Needs A17/A28 first. **`put`/`get`** join the
  ban when A45 lands (flagged on task #47).
- **No reflection changes** (parser restriction; no new AST/construct) — no
  prettify/BAST/JSON/EBNF. **Impact nil:** 0 external and 0 internal `.riddl`
  function bodies used effects; the only internal fix was
  `FunctionValidatorTest`'s "simple function" (used `set` → made pure with `let`).
- **Tests:** new `PureFunctionTest` (reject each of set/send/tell/morph/become/
  reply; accept prompt/require/let/error; assert the pure-function message).

## In-flight: Handler kinds per processor (2.0) — DESIGN LOCKED, WIP

**Branch**: `release/2`. One combined change (user's choice). Uncommitted
WIP. **STATUS (this session): feature BUILT + verified; JVM suite 35→5
failures, all understood as test-expectation updates (not logic bugs).**

### Done + verified
- **AST** (#12): `OnEventClause`, `OnActivationClause`, `OnPassivationClause`
  added; both event/message nodes now share a new sealed trait
  `OnMessageLikeClause` (has `msg`/`from`) so resolution/flow/dep/diagram/
  validation treat them uniformly. `StatementsSet` record refactor
  (`ProcessorKind` × `ClauseRestriction`). **Fastparse trap fixed**: the
  ban parsers MUST be `def`s not `val`s (a `P[T]` is a parsing *run*; a
  val executes at its definition position and corrupts the alternation —
  caught by verification, 14 failures → 0).
- **Parser** (#13): `HandlerParser` dispatches on `set.processor` — no
  ProcessorParser changes needed (processors already pass the right set).
  `on event` and `on <msg>` are ONE parser (`onMessageOrEventClause`) that
  branches on the parsed ref type via `flatMap` (needed because
  `Keywords.on` cuts, so two `on …` alternatives can't backtrack); event
  bodies parse under `forEvent` (bans require/error). Projector ref set
  rejects command/query/record; non-entity rejects activate/passivate;
  activation bodies ban send/tell/reply/morph/become. `eventRef`/
  `resultRef` de-privatized. Keywords `activate`/`passivate` +
  `on activate`/`on passivate`. **EBNF updated. GBNF NOT yet regenerated.**
- **BAST** (#14): writer/reader sub-kinds 4=Event,5=Activate,6=Passivate;
  `FORMAT_REVISION` 6→7; `BASTWriterPass` traversal cases added (the
  explicit per-subtype match would else silently drop them).
- **Prettify** (#14): `OnEventClause` added to `RiddlFileEmitter.openDef`
  msg-format special case; activate/passivate/init/term/other all emit via
  `id.format`.
- **JSON** (riddlLib, a 3rd serialization surface): JsonifierPass +
  JsonAstBuilder handle event/activate/passivate.
- **Validator** (#15): removed the now-dead projector command/query
  WARNING (parse error supersedes); added adaptor "missing `on other`" →
  ERROR (generalizable later). All `OnMessageClause` collect-sites that
  should count events broadened to `OnMessageLikeClause` (restores
  pre-change behavior — events *were* OnMessageClause); kind-filtered
  (command/query) sites left precise.
- **Reflection tests** (#16): 6 new HandlerTest parse/parse-error cases
  (JVM+JS); `HandlerKindsRoundTripTest` (prettify, JVM+Native);
  `BASTRoundTripTest` handler-kinds case. **All green** (12 + 6).
- **Corpus fixes so far**: context.riddl, SharedValidationTest,
  SharedAdaptorTest (×2), everything_full.riddl, adaptor-direction.riddl,
  commands/adaptors.riddl (error→prompt in on-event; add on-other).

### DONE since (all green)
- **5 JVM expectation updates** applied: PassTest opens 57→58 + values
  25→26; StatsPass categories 23→25 (new "On Event" + "On Other"), All.count
  22→24, numStatements 7→8; HandlerValidatorTest string "OnMessageClause"→
  "On Event" (OnEventClause renders via its `kind` override);
  KeywordsTest 142→144. Both `.check` files regenerated (handler-types
  projector converted command→event + repurposed; adaptor-direction down to
  the 2 direction errors — my `error` in on-other counts as executable so the
  "only prompt" warnings correctly vanished).
- **EBNF** updated (`ebnf-grammar.ebnf`): `on_clause` now includes
  `on_activate_clause` / `on_passivate_clause` / `on_event_clause` (the last
  reuses the existing `event_ref`; `on_message_clause` stays a superset since
  EBNF is context-free and can't express the projector/statement bans).
  **GBNF** regenerated (263 rules) from it + `gbnf_validator.py` PASSED.
- **EBNF↔fastparse parity PROVEN** (not just asserted): added corpus fixture
  `language/input/handler-kinds.riddl` exercising `on activate`/`on passivate`/
  `on event`/`on other`. It parses under BOTH the TatSu EBNF validator
  (`ebnf_tatsu_validator.py`, run locally in a venv — 67/87, 0 unexpected
  failures) AND fastparse (new `HandlerKindsFileTest`). Before this, no
  input-dir file used `on activate`/`on passivate`, so those new EBNF rules
  were unexercised.
- **tJVM: green** except the 16 external `RiddlModelsRoundTripTest` cases
  (see below). **tJS: green (63/63).** **tNative: green** (one further count
  shift, `VisitingPassTest` values 24→25 — same `on other` node; fixed and
  confirmed on both Native and JVM).
- **Cross-repo tasks dropped**: `../riddl-models/task/` and
  `../riddl-examples/task/` (`2026-07-25-handler-kinds-2.0-conformance.md`).

### The only remaining red is EXTERNAL (expected, coordinated)
`commands/…/RiddlModelsRoundTripTest` round-trips the **external**
`../riddl-models` checkout. 16 models fail: **48** adaptor handlers lack
`on other`; **6** `on event` clauses use `require`/`error`. Per the "fix
internal, drop tasks external" directive these are fixed in riddl-models
(task dropped), NOT here. This test goes green once riddl-models `main` is
updated. JVM-only test (scalajvm), so it does not affect tJS/tNative.

### Clean-from-scratch certification (the incremental runs were NOT enough)
sbt 2's action cache (`~/Library/Caches/sbt/v2/ac`) keys test results on the
compiled classpath but is BLIND to fixtures a test READS AT RUNTIME — so the
`tJVM`/`tJS`/`tNative` aliases served stale passes for fixture-reading tests
after I edited `.riddl`/`.check` files, and only ran ~30 of ~125 suites.
`clean` does NOT fix this (global cache). Forcing a real run
(`rm -rf ~/Library/Caches/sbt/v2/ac`, keep `cas`) surfaced two INTERNAL
failures the incremental runs had masked — both now fixed:
  - `TokenParserTest` everything_full token count 401→407 (my `on other`).
  - `JsonCoverageGuardTest` — added `OnEventClause`/`OnActivationClause`/
    `OnPassivationClause` rows to `JSON_COVERAGE.md`.
Full fresh results: **JVM 1031+ / 0 internal fail, JS 409 / 0, Native 370 /
0 + nativeLink OK.** (See memory `sbt2-action-cache-fixture-blindspot`.)

### External corpus (was a moving target — now fixed)
`RiddlModelsRoundTripTest` (commands) + `Root2JsonCorpusTest` (riddlLib) read
the LIVE `../riddl-models` checkout. A concurrent instance completed the
dropped task (58 `on other` added, 4 `require`/`error` removed; 187/187
validate clean, all `.bast` regenerated), so those tests now PASS
locally: `RiddlModelsRoundTripTest` 0 fail (was 16); `Root2JsonCorpusTest`
181/181 reparsed, 96.8% byte-identical (over the 95% bar).

### Regression tests (comprehensive — added so we never revisit)
- **Parse matrix** (`HandlerTest`, 16 cases): parse `on activate`/`on
  passivate`/`on event`; reject `on command`/`on query`/`on record` in a
  projector; ACCEPT `on event`/`on result` in a projector; reject
  `require` AND `error` in `on event`; reject `on activate` AND
  `on passivate` outside an entity; reject ALL of send/tell/reply/morph/
  become in `on activate` (parser now bans all five uniformly with the
  "side-effect-free" message).
- **Validation** (`SharedAdaptorTest` +2, `HandlerValidatorTest` +1):
  adaptor handler missing `on other` → ERROR; with `on other` → clean;
  entity `on activate`/`on passivate`/`on event` validate with no
  spurious errors.
- **Reflection**: prettify RT (`HandlerKindsRoundTripTest`), BAST RT
  (`BASTRoundTripTest` case), JSON RT (`JsonRoundTripTest` case), JSON
  ledger (`JsonCoverageGuardTest`), EBNF↔fastparse parity
  (`HandlerKindsFileTest` + `language/input/handler-kinds.riddl` +
  TatSu validator).

### DONE — committed + CI green on all platforms
Handler-kinds landed as one commit (`66af0752`). `release/2` pushed; `scala.yml`
(workflow_dispatch) is **green on JVM + JS + Native** (run 30172643158), which
also confirms the external riddl-models/riddl-examples fixes in a clean CI env
(RiddlModelsRoundTripTest / Root2JsonCorpusTest download the pushed `main`).

### CI / sbt 2 fixes required on release/2 (were pre-existing, cache-masked)
Dispatching `scala.yml` on release/2 surfaced three sbt-2 CI issues that
`main` never hit (main restores plugins/deps from cache and hadn't re-fetched
in ~18 months). Fixed in commits after the feature commit:
1. **Plugin-resolution 401** — under sbt 2 the global `~/.sbt/2/github.sbt`
   credential is NOT applied to meta-build (plugin) resolution, so fetching
   sbt-ossuminc 3.0.3 from GitHub Packages returned 401. **NOT** a permission
   gap: the automatic `GITHUB_TOKEN` reads the public package fine (direct
   `curl -u x-access-token:$TOKEN` → 302). Fix: restore
   `credentials += Credentials("GitHub Package Registry","maven.pkg.github.com",
   "x-access-token", sys.env.getOrElse("GITHUB_TOKEN",""))` in
   **`project/plugins.sbt`** (the meta-build) — as pre-1.4 revisions had.
   GitHub Packages Maven requires auth even for PUBLIC packages (401 to
   anonymous); only the Container registry allows anonymous.
2. **sbt-2 CLI** — a single quoted multi-command arg is parsed as ONE command
   line; the old `sbt clean cJVM tJVM` fails ("Expected whitespace"). Use the
   `;`-list form: `sbt "; clean; cJVM; tJVM"`. Fixed in scala.yml + coverage.yml.
3. **dynver** — shallow `actions/checkout` breaks `git describe` ("No names
   found"). Add `fetch-depth: 0` + `fetch-tags: true`.

**Goal**: `HandlerParser` stops assuming one handler grammar and offers a
family of handler kinds per processor. Three features on that spine:
- **Projector = event-only**: `projectorHandler` offers event + result
  clauses only; `on command`/`on query` in a projector is a **parse
  error**. Remove the existing ValidationPass projector command/query
  warning.
- **Entity `on activate` / `on passivate`**: new per-rehydration /
  per-eviction lifecycle clauses, entity-only, distinct from once-ever
  init/term. Statements banned: outbound messaging (send/tell/reply/
  morph/become) — activation must be side-effect-free.
- **Adaptor `on other` completeness**: an adaptor handler with no
  `on other` clause is a validation **ERROR** (adaptors only for now;
  bring the user a proposed generalization list before extending to other
  processor kinds).

**Locked design decisions** (from the interview):
- **`on event` becomes its own node `OnEventClause` EVERYWHERE** (not just
  projectors). Its statement set bans `require`/`error` at parse time —
  "events must always be accepted" is structural. `on event` moves OUT of
  `OnMessageClause` (which now covers command/query/result/record only).
- New AST nodes: **`OnEventClause`** (has a `msg` ref like OnMessageClause),
  **`OnActivationClause`**, **`OnPassivationClause`** (lifecycle, no ref,
  like OnInit/OnTerm). All extend `OnClause`. **DONE.**
- **`StatementsSet` is now a record** `(processor: ProcessorKind, clause:
  ClauseRestriction = Unrestricted)` with companion vals preserving the old
  `StatementsSet.X` call sites, and `.forEvent`/`.forActivation`.
  `ClauseRestriction`: `Unrestricted | EventClause (bans require/error) |
  ActivationClause (bans send/tell/reply/morph/become)`. Composition lives
  in `anyDefStatements`/`statement`. **DONE + compiles.**
- **Message-kind restriction stays STRUCTURAL, not in the record**: distinct
  `onEventClause` (uses `eventRef`) vs `onMessageClause` (non-event refs);
  the projector's result-only need is a local `allowedKinds` param on
  `onMessageClause`. (User agreed after discussion.)
- Reflection: 3 new nodes ⇒ BAST tags + **`FORMAT_REVISION` 6→7**, prettify
  emission, EBNF/GBNF, and per-node parse+validate+prettify-RT+BAST-RT
  tests. Plus parse-error tests (command/query-in-projector; require/error-
  in-event; activate/passivate-outside-entity; outbound-msg-in-activate).
  Fix internal corpus; **drop correction tasks in `../riddl-models/task`
  and `../riddl-examples/task`**.

**Ripple finding (good news)**: `passes` compiles clean adding the nodes —
the `VisitingPass` dispatch handles `OnClause` **generically** via
`openOnClause`/`closeOnClause`, so NO per-pass hooks are needed. But a clean
compile does NOT mean BAST/prettify handle the new nodes (fallthroughs) —
those need explicit emission, proven by round-trip tests, not the compiler.

**Remaining (concrete)**:
1. Parser: `onEventClause`; split `onMessageClause` (non-event refs +
   `allowedKinds`); `onActivationClause`/`onPassivationClause` + `activate`/
   `passivate` keywords; `projectorHandler`/`entityHandler`/`defaultHandler`;
   wire `ProcessorParser` per processor. EBNF + regen GBNF.
2. BAST: 3 node tags, `FORMAT_REVISION` 6→7, writer + reader.
3. Prettify: emit the 3 clauses (per-node keyword in `PrettifyVisitor`).
4. Validator: remove projector warning; add adaptor on-other ERROR.
5. Tests (full reflection matrix + parse-error cases); corpus fixes;
   cross-repo task drops. Full tri-platform + EBNF/GBNF green.

---

## sbt 2.0 / sbt-ossuminc 3.0 / Scala 3.9 — the riddl 2.0 baseline

**Branch**: `release/2` (created from `feature/sbt2-migration`, which
was off `development`). This is the **riddl 2.0** baseline. **Status
(2026-07-24): locally verified end-to-end.** A full
`sbt "; clean; tJVM; tJS; tNative"` passes from scratch — all modules
compile and all tests pass on JVM/JS/Native, 0 failures. Publishing,
the sbt-riddl scripted test, and the EBNF/GBNF validators all pass.
`main` has been merged in (format rectification + 1.30/1.31 option
registrations). Not yet merged back to `main`.

### Scala 3.9.0-RC4 (adopted 2026-07-24)
riddl 2.0 now targets **Scala 3.9.0-RC4** (was 3.8.4; RC1 verified
first, then bumped to RC4). Verified: all three platforms compile and
**all tests pass, 0 failures** on the RC — scala-js and scala-native
both publish 3.9.0-RC4 toolchains. Findings:
- **The lever is `With.Scala3.configure(version = Some(V.scala))` per
  module**, NOT `V.scala` / `scalaVersion :=` alone (sbt-ossuminc's
  `With.typical` pins its 3.8.4 default and is applied *after* the
  module `scalaVersion` setting, so that setting is a no-op). The
  README's `With.Scala3(version=…)` shorthand does not exist.
- **One code change needed**: `DefinitionValidatorTest` passed a
  `Seq[Message]` where `Messages` (= `List[Message]`) is required;
  3.9.0-RC4's stricter implicit search (scala/scala3#25910/#26210) no
  longer accepts it (3.9 is *correct* — Seq ⊅ List; 3.8.4 was lenient).
  Fixed with `.toList` (valid on both versions) — **not** a Scala bug,
  so no Scala Center report warranted.
- **When 3.9.0 final ships**: bump `V.scala` 3.9.0-RC4 → 3.9.0 and
  re-grep the `scala-3.9.0-RC4` path segments in CI/sonar/Dockerfile/
  CLAUDE.md.

### What shipped (committed on the branch)
1. **Source restructure** — all 7 cross modules moved (pure `git mv`)
   from `shared/jvm/js/native/jvm-native` to the projectMatrix flat
   layout: `src/{main,test}/{scala,scalajvm,scalajs,scalanative,
   scala-jvm-native}`. Python validators → `language/src/test/scalajvm/python`.
2. **Meta-build** — sbt 1.12.3→**2.0.2**; sbt-ossuminc 1.4.0→**3.0.3**;
   dropped scala-xml scheme / jsdom / bloop / tracked `metals.sbt`;
   `Dependencies.scala`: no portable-scala import, `V.scala=3.8.4`,
   `%%%`→`%%`.
3. **build.sbt** — projectMatrix `CrossModule(…, V.scala)`; `pDep`
   per-row deps; `jvmNativeSrc(dir)` wires `scala-jvm-native` onto the
   JVM+Native rows (utils/language/passes); removed tasty-mima +
   coveralls; fixed the `commandsNative = riddlLib_cp.native` bug.
4. **sbt-riddl plugin** — Scala 3 / sbt 2: `Setting[?]`,
   `PathFinder.get()`, **`Def.uncached`** on the File-/CompileAnalysis-
   returning tasks (`riddlcDownload`, `riddlcBinary`, the
   validateOnCompile hook — required, else the plugin won't compile);
   dropped With.Scala2 + `scalaVersion:=2.12.20`.
5. **CI/tooling** — corrected to the real sbt-2 `target/out/…` layout
   (see CLAUDE.md "Target-path layout"); `~/.sbt/1.0`→`~/.sbt/2`;
   dropped coveralls; release.yml checkouts get `fetch-depth:0` +
   `fetch-tags:true`; sonar/Dockerfile updated.
6. **Test/validator path fixes** — several tests + the python
   validators hardcoded old `…/jvm/src/test/resources` /
   `…/shared/src/main/…` paths; updated to the new layout.

### Key facts learned in verification
- **Run sbt with `sbt --server …`, not the default.** The sbt 2 CLI
  uses the `sbtn` native thin client, which talks to a **detached**
  server (`--detach-stdio`) — piped stdout comes back **empty** and the
  build looks hung. `sbt --server <cmds>` runs in the foreground with
  attached stdout. Don't trust `--server`'s exit code; grep the log.
- **Project IDs are clean** (`utils`, `utilsJS`, `utilsNative`, …) —
  the sbt-ossuminc `defaultAxes` fix (shipped 3.0.1) works.
- **scalajs-stubs** is JVM-only (`_3`); sbt-ossuminc **3.0.3** auto-adds
  it (`% provided`) to the JVM+Native rows of JS-targeting modules, so
  no consumer dep is needed. (3.0.0/3.0.1 did not — an interim manual
  workaround was removed once 3.0.3 landed.)
- **`riddlLib/js/{package.json.template,types}` did NOT need moving** —
  `riddlLibJS/npmPrepare`/`fullLinkJS` published the npm tgz fine.
- **DocSite / dropped `import scala.collection.Seq`** — load-time issue
  from 3.0.0 already fixed on the branch; build loads clean.

### Known non-blockers (deferred)
- **scalafmt**: earlier `scalafmtCheck` flagged ~327 files — pre-existing
  drift because this branch was based on `development`, *behind* `main`'s
  "Rectify code format style" commits. **Resolved** by merging `main` into
  this branch (2026-07-24) to pick up the rectification, then running
  `sbt scalafmt` / `sbt test:scalafmt` for the migration's own new files.
- **scaladoc**: `Compile/doc` fails on `@JSExport*`-annotated modules
  (`provided` scalajs-stubs isn't on the doc classpath). Non-fatal —
  `publishLocal` published every artifact anyway, and the release CI
  already disables doc (`set every Compile/doc/sources := Seq.empty`).
  Pre-existing category, not a migration regression.
- **Degradations accepted on sbt 2**: coveralls, TASTy-MiMa,
  sbt-idea-plugin, stable sbt-paradox. Regular binary MiMa + scoverage
  retained. A Scala-3/sbt-2 `sbt-riddl` requires sbt-2 consumers.

### CI caveat
CI path edits match the locally-observed `target/out/…` layout but are
only truly confirmable in a real CI run. The Dockerfile still installs
the sbt 1.10.7 launcher (honors `build.properties`; confirm on first
image build).

### Local dev note
Move `~/.sbt/1.0/github.sbt` → `~/.sbt/2/github.sbt` before building.

---

## Current Status

**Last Updated**: 2026-07-24

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
| At 2.0.0 final | **Drop `RIDDL_MODELS_BRANCH: release/2` from `scala.yml`** once the riddl-models 2.0 corpus merges to its `main`. The override exists because `RiddlModelsRoundTripTest` falls back to downloading a branch zip when there is no local checkout, and riddl-models `main` still holds 1.x models — so CI failed on the 2.0 grammar while local runs (reading the developer's `release/2` checkout) passed 189/189. The default in the test is `main`, so deleting the line is the whole fix. |

The CodeQL v3 → v4 upgrade that sat here is DONE — `upload-sarif@v4` went in
with the Node 20 action sweep, well ahead of its December 2026 deadline.

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
