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
  **Regenerate it FROM ITS OWN DIRECTORY**, or the `.bast` embeds a different
  source path than the committed one and the diff stops being a one-field
  revision bump:
  ```
  sbt riddlc/stage
  cd language/input/import && <repo>/target/out/jvm/scala-<ver>/riddlc/universal/stage/bin/riddlc bastify NotImplemented.riddl
  ```
  Done right the file is 93 bytes and `cmp` against the committed one differs
  at byte 12 (the revision short) and nowhere else — check that, since a
  larger diff means the path got baked in. (`sbt clean` deletes the stage, so
  regenerate the fixture BEFORE certifying, not after.)
- **Update `../RIDDL-Computational-Model.md` with everything `release/2`
  changed.** Reid, 2026-08-06. That document is the authority for any lowering
  decision — what a conforming generator MUST preserve versus may freely choose
  — so a language change that does not reach it leaves generator authors working
  from a stale contract. This branch has changed a lot of what it describes:
  entity intentions and the four event-sourcing rules, the unified processor
  model, implicit invariant scope, `requires`/`returns` in contents,
  `Riddl.Envelope` + `option message_envelope`, A56 (`tell p`) and A57
  (`on other as x`). Work from `git log 2.0.0-rc.1..HEAD` rather than memory.
  Also add (2026-08-15, numeric-literals plan): the three integer types'
  ranges — `Integer` signed, `Whole` non-negative, `Natural` positive
  (Reid, 2026-08-14) — were undefined anywhere until this work, and a
  `constant` literal outside its declared type's range is now a
  validation Error (`checkNumericLiteralConformance`,
  `passes/.../ValidationPass.scala`). The "vocabulary of information
  shapes" passage that already lists the predefined types is the right
  place for the range table; see the task dropped in
  `../ossum.tech/task/2026-08-15-integer-type-ranges.md` for the
  worked examples and the two related grammar widenings (`Constant`
  accepting a bare literal, `Comparand` accepting one).
  Also add (2026-08-15, A20 typed-holes plan): `prompt("...") as
  <type>` ascribes a type to an AI-computed value — the type is known
  and checkable, the computation is prose an AI fills in at generation
  time. Legal in every position an ordinary value can occupy (`let`,
  `constant`, constructor argument, `set`, `when` condition), with
  either a predefined type or a declared alias. **The ascription
  RESTATES the position's already-known type, it never OVERRIDES
  it** — a contradicting ascription is a validation Error, and a
  `constant` with a `prompt` value needs no ascription at all since
  the constant already declares the type. See the task dropped in
  `../ossum.tech/task/2026-08-15-a20-typed-holes.md` for the full
  writeup and worked examples, and its caution that `Currency`
  cannot be used bare in an example (it requires a `country`
  argument).
  Also add (2026-08-15, not-bang-synonymy plan): `not` and `!` are
  synonymous EVERYWHERE, as the inverse of a boolean expression —
  both spellings build the identical `NotExpression` AST node, so a
  generator lowering a boolean expression needs to know there is only
  ever one node to handle, never a spelling to branch on. `!` is not
  related to the `!` in `!=` (an ordinary comparison operator). See
  the task dropped in
  `../ossum.tech/task/2026-08-15-not-bang-synonymy.md` for the
  worked examples and the `!=` caution.
- **Update the ossum.tech documentation site** with the same syntax changes,
  **plus a LIGHTER treatment of the implied syntax.** Reid, 2026-08-06 — the
  reference currently spells out more of the implicit forms than a reader needs,
  and the balance should shift toward what someone actually writes. Same source
  of truth: the commits on this branch, not recollection.
  (ossum.tech is a separate repo; this is a task DROP, not work done here.)

### 1. Queued, designed, not started

**EXECUTION ORDER (set 2026-08-15, ordered by dependency, not by severity).** All
of § 1 is now the active to-do list. The detailed entries stay in their original
positions below — they are cross-referenced from code comments and other repos'
task files, so they were NOT physically reshuffled; this index carries the order.

| # | Item | Blocked by | Why here |
|---|---|---|---|
| ~~1~~ | ~~`OnInit`/`OnTerm` params~~ | — | **DONE `c530337d9`** — defaulted IN PLACE; the prescribed "move it trailing" was unnecessary and would itself have broken all five positional call sites. |
| ~~2~~ | ~~Close the two stale entries~~ | — | **DONE `d46646e10`** — one of them was the sole holder of a live design rationale, graduated to CLAUDE.md before deletion. |
| ~~3~~ | ~~`valueTypeExpr` predefined types **and** `PromptValue.typeEx`~~ | — | **DONE `141486ed4`** — merged as planned; one function, one corpus A/B. |
| ~~4~~ | ~~Wire `checkPromptAscription` at the remaining A20 positions~~ | — | **DONE `0fd7bb54e`** — all seven wired; the "decide per position" premise dissolved once every position turned out to already hold its expected type. |
| 5 | Close the JVM/Native test gap | — | **MOSTLY DONE 2026-08-15** (`682e835bc`, `54ff5fe73`, `6cf60baf2`, `0919f191e`). Gap **560 → 275**, Native **1840 → 2393**. What remains is ~191 in `commands`, of which 189 are the corpus gate — blocked on a **decision** (isolating the shared corpus per platform), not on effort. |
| 6 | `!` into `Punctuation.tokenPunctuation` | — | Isolated, tooling-facing (idea-plugin, synapify). Fits any gap. |
| 7 | JSON strict-key rejection | — | Needs a design decision before code. Independent. |
| 8 | Three CM amendments owed by the identity design | — | Documents work already SHIPPED, so it is overdue debt under the definition of done. **Fold into § 0's CM sweep — do that once, not twice.** |
| 9 | Survey the CM/A items for future `self` fields | — | Cheap, and it BOUNDS 10 by deciding what is in scope. |
| 10 | Clusterability: `clustered`, `self.isClustered` | 9 | Defines the vocabulary `self.isClustered` was deliberately kept out of the identity spec to avoid forward-referencing. Wants its own plan. |
| 11 | Cross-context `tell` isolation seam | 5 | Largest item and the biggest corpus-migration risk. Needs a counting mode built and run under real resolution before the Error ships. Wants its own plan. |
| ~~12~~ | ~~Two narrow-operand gaps~~ | — | **Part 1 DONE `faf7551c0`** — and it was NOT false-positive-only as filed, it was a missed Error. Part 2 (`emittedMessageTypes`) split out and left last: genuinely advisory, genuinely a restructuring. |

Three real dependency edges, not twelve: **4 ← 3**, **10 ← 9**, **11 ← 5**.
Everything else is independent and may be reordered by appetite. Items 10 and 11
each want an approved plan before implementation, per the standing rule.

- ~~**`Value` has no NUMERIC LITERAL, so `initiate entity Order(1)` does not
  parse.**~~ — **STALE, CLOSED and REMOVED 2026-08-15.** Numeric literals shipped
  (`6cfeceb2f`) and closed it. Verified against the staged riddlc at
  `2.0.0-rc.14-121-fe768026`: the entry's own example — `initiate entity
  Order(1)` against `on init(total: Integer)` — validates with **zero errors**.
  Its two supporting claims are dead too: `count > 5` parses (A28's ban was
  reversed) and a constant may hold a bare number. Body deleted; nothing cited
  it (the two `BACKLOG`-referencing code comments point at the `Finder` entry and
  at § 2's `FORMAT_REVISION` reservation, neither of them this).

- ~~**NEW CHECK — the unused `initiate` id Warning.**~~ — **BUILT, CLOSED and
  REMOVED 2026-08-15.** `UnusedInitiateIdTest` is green with 7 cases covering the
  escape-route analysis the entry called "the real work" — terminated, kept in
  state, passed in a message, and used only inside a nested `when` body. Verified
  by running it, not by reading the file name. **Its durable half was GRADUATED
  to CLAUDE.md** (the processor-instance-identity section): why this is a Warning
  and not an Error, and why it is ungated. That reasoning was recorded "so it is
  not re-litigated", which is exactly the kind of thing that must not die with
  the backlog entry.

- ~~**`valueTypeExpr` does not surface a `let`'s declared PREDEFINED type**~~ and
  ~~**`checkTerminate` is SILENT for an ascribed `prompt(…)` target**~~ — **BOTH
  DONE 2026-08-15, `141486ed4`.** Merged deliberately: one function, one corpus
  A/B. `letDeclaredPredefinedType` answers the first from the same
  `PredefTypes.typeExpressionFor` set ResolutionPass and `checkStatementScopes`
  already share, ordered BEFORE inference because a declared type outranks an
  inferred one; a `PromptValue` arm answers the second by returning `typeEx`.
  **The UNASCRIBED hole still yields `None`, by design** — A20's conservative
  rule is untouched, and the silence belongs to the form that says nothing
  rather than to every `prompt(...)`. `TerminateTargetTest` now pins all four
  corners.
  Two things worth keeping. It made an existing comment honest:
  `ResolutionPass:435` claimed *"`ValidationPass.letType`/`checkStatementScopes`
  special-case the same set directly"* — `checkStatementScopes` did, `letType`
  did not, which is the defer-to-something-that-does-not-do-it shape. And the
  corpus A/B came back identical (187/2, 188 clean) while containing **zero**
  `let x: <predefined>` ascriptions and **zero** ascribed prompts — all 834 of
  its `let x:` use named aliases — so that green is evidence the ALIAS path still
  works, and no evidence at all about the shapes actually changed.

- **`JsonModel`'s reader never rejects unknown/misspelled keys — a whole
  defect class, not one stale doc line.** Found while fixing `JSON_INPUT.md:255`'s
  stale `"negated"` field. A producer emitting a misspelled or obsolete key gets
  it silently dropped — no diagnostic, no error, a model that quietly means
  something other than what was written. This matters specifically because
  `JSON_INPUT.md` exists so AI producers can emit schema-constrained JSON without
  reading the Scala: a stale example and a producer typo fail the same silent way.
  **CHARACTERIZED 2026-08-15, `4bb0ba01a`** — `JsonUnknownKeyTest` pins today's
  behaviour at BOTH reader layers, with an isolation control (the same document
  minus the offending key) so a malformed fixture cannot masquerade as a rejected
  one. It goes red the moment strictness changes.
  **⚠ THIS ENTRY'S PROPOSED MECHANISM DOES NOT WORK, and that is the finding.**
  It said "a shared consumed-keys tracker wrapping `ujson.Obj`, most likely".
  That fixes the hand-written readers (`readStatement`, `readValue`,
  `readTypeExpr`, … — 6 binding sites, ~120 selective `m("key")` lookups) and
  **cannot fix the rest**: most DTOs are read by upickle's derived `macroRW`,
  which ignores unknown keys by construction and is not ours to instrument. A
  wrapper sees the lookups a hand-written reader makes, never the ones a derived
  reader makes internally.
  **Two decisions, queued for Reid (NOTEBOOK § QUESTIONS, Q1):** (a) validate the
  `ujson` tree against a key inventory BEFORE upickle sees it — covers both
  layers, one new component, no reader changes — or (b) instrument only the
  hand-written layer and accept the larger one stays silent; and separately,
  Error or Warning. Recommendation on file: (a) with a Warning first, per the
  warn-then-flip sequencing used twice already.
  **The evolution constraint is about MISSING keys, not unknown ones** and is
  already satisfied: readers use `m.get`, so a document predating a field reads
  fine. `JsonUnknownKeyTest` keeps a control case pinning that.

- **`Punctuation.tokenPunctuation` does not include `!`, so `TokenParser`
  swallows `!isValid` / `!(a` as a single `Token.Other` blob** (found in the
  final `!`/`not` synonymy review, 2026-08-15). This is pre-existing — it was
  never fixed when `Punctuation.exclamation` was removed, because that removal
  was inert — but the not-bang-synonymy work multiplies the positions where
  editor tooling (riddl-idea-plugin, synapify) will encounter it, since `!` is
  now a first-class negation spelling rather than a narrow special case. File,
  do not fix opportunistically: `Punctuation.scala:76` (`tokenPunctuation`),
  consumed by `TokenParser.otherToken` (`TokenParser.scala:31`).
- **`OnInitializationClause.parameters` / `OnTerminationClause.parameters` have
  no default and are not trailing** (`AST.scala:4236`). Filed by synapify
  2026-08-14; it is the only thing in rc.14 that broke their build. It departs
  from the compatibility policy quoted in the adjacent `Connector.intentions`
  comment in the same release — *"The compatibility policy requires a new
  parameter to have one"*. Adding `= Seq.empty` is source-compatible and cheap.
  Note the constraint that produced it: `@JSExportTopLevel` requires defaulted
  params to be TRAILING, and `contents`/`metadata` are already defaulted — so
  the fix is to move `parameters` after them, not merely to default it in place.
- ~~**`elements` is not threaded into `widenedOperandType`**~~ — **DONE
  2026-08-15, `faf7551c0`.** A `tell` whose operand IS a `foreach` loop variable
  resolved to nothing, so the `by`/ambiguity Errors and the three completeness
  checks skipped it silently.
  **The entry filed this as "pre-existing in kind" and false-positive-only; it
  was neither.** It was a missed ERROR — an ambiguous derivation that should be
  rejected was accepted — and the scaladoc actively asserted it could not happen
  ("none of this function's three call sites resolve an operand from inside a
  `foreach` body"). `checkTellAddressing` is called from `checkStatementScopes`,
  which recurses into `foreach` bodies precisely to thread those bindings, and
  says so in a comment two lines above the call. **Two claims about the same
  code, in one file, contradicting each other — and the false one was the one
  being relied on.** Second instance found the same day, after `ResolutionPass`'s
  claim that `letType` special-cased predefined keywords.
  Corpus impact nil and honestly so: the whole corpus has **3** `foreach`
  statements and none tells its element.

- **`emittedMessageTypes` is still narrow.** The second of the two gaps, and the
  one that really is a restructuring rather than a fix round. It is a whole-root
  `Finder` sweep with no per-clause scope, feeding A70's correlation-fold
  advisory (`ValidationPass.scala:158` documents the flatness). Still
  false-POSITIVE-only, still zero corpus impact, so it stays deliberately last —
  but it is now filed on its own rather than bundled, because its sibling turned
  out to be a missed Error and bundling the two hid that.

- ~~**FLAKY CI GATE: `PerformanceBenchmarkTest` 100x cache-speedup assertion**~~
  — **DONE 2026-08-14, `32340312e`.** Replaced with a monotonic check (cached
  strictly faster) plus a 5x floor; the precise multiplier stays as `info`
  output. Healthy runs measure 600x–1600x, so the floor has real headroom while
  a genuine regression collapses toward 1x and still fails hard. **Proven still
  able to detect one** by removing `Finder.findByType`'s cache-hit branch and
  watching both assertions go red at 1.17x and 0.9x.
  **A SECOND identically-shaped 10x assertion existed in the same file** and was
  fixed with it. The lesson worth keeping: `BASTPerformanceBenchmark.scala`
  ALREADY carried this exact fix from an earlier round, comment and all — the
  defect class was diagnosed once and its sibling missed, so when fixing a
  test-shape defect, grep for the shape rather than fixing the instance.

- **Close the JVM/Native test gap: 729 cases run on JVM that never run on
  Native.** Reid, 2026-08-14, from the rc.14 certification. *"Testing on the JVM
  does not guarantee correctness on Native, and I can't believe there are ~800
  test cases that genuinely cannot run there."*

  **Measured, not estimated** — rc.14 certification from clean under a throwaway
  `--sbt-cache`, module order taken from the `tJVM`/`tNative` aliases
  (`build.sbt:538`, `:549`):

  | module | JVM | Native | gap |
  |---|---|---|---|
  | commands | 245 | 47 | **−198** |
  | language | 668 | 512 | **−156** |
  | passes | 1196 | 1040 | **−156** |
  | utils | 146 | 108 | −38 |
  | riddlLib | 122 | 111 | −11 |
  | testkit | 2 | 1 | −1 |
  | riddlc | 21 | 21 | 0 |
  | **total** | **2400** | **1840** | **−560** |

  **PROGRESS 2026-08-15: the gap is HALVED, −560 → −275, and what remains is
  essentially ONE suite that needs a decision.** Commits `682e835bc`
  (commands + wiring), `54ff5fe73` (language), `6cf60baf2` (passes),
  `0919f191e` (utils). Measured per module, JVM and Native, after:

  | module | JVM | Native | gap |
  |---|---|---|---|
  | commands | 243 (+2 red) | 52 | **−191** |
  | passes | 1397 | 1355 | −42 |
  | language | 726 | 709 | −17 |
  | utils | 148 | 134 | −14 |
  | riddlLib | 134 | 124 | −10 |
  | testkit | 2 | 1 | −1 |
  | riddlc | 18 | 18 | 0 |
  | **total** | **2668** | **2393** | **−275** |

  **Native went 1840 → 2393 (+553).** The old "expected floor 1840" note below
  is superseded; the floor may only be RAISED by a certified tri-platform run,
  so treat 2393 as the number to certify against, not as an already-raised floor.

  **Nearly all of the remaining −275 is `commands`, and nearly all of THAT is
  `RiddlModelsRoundTripTest`'s 189 cases** — blocked on the shared-corpus
  decision recorded under item 1 below, not on effort. Subtract it and the whole
  repo is within ~86 cases of parity.

  **The entry's per-module predictions were wrong in both directions, which is
  worth remembering before estimating this kind of work again.** `commands` was
  called "probably the cheapest win" and delivered +5. `passes` was to be
  "audited last" because its residue was "likely genuinely JVM-bound", and 26 of
  its 32 files moved with no source change at all. The shape that predicts
  portability is not the module's general hygiene; it is whether a file names a
  JVM-only TYPE, which no import scan reveals.

  **PROGRESS 2026-08-14 (`546f2f834`): `language` closed from −325 to −156.**
  The 13 abstract parser suites now run on Native — their concrete runners moved
  from `src/test/scalajvm` to `src/test/scala-jvm-native` (the root already wired
  by `jvmNativeSrc("language")`), renamed `JVMNativeTests` since they serve both
  platforms. Native 343 → 512, exactly the predicted +169, **nothing excluded**:
  those suites build every input from `RiddlParserInput` and string literals,
  with no `java.io`/`scala.io.Source`/regex-`.r`, so no Native hazard was present.
  **The Native floor is therefore expected to be 1840 at the next full
  certification** — it is NOT raised here, because a floor may only be raised by
  a certified tri-platform run, and this number is arithmetic plus an isolated
  `languageNative/testOnly *`.

  **Remaining: 510 of the 560 sit in three modules.** In this order:

  1. **`commands` (−198) — PARTLY DONE 2026-08-15, `682e835bc`, and its central
     question is ANSWERED: NO.** The module gained the `jvmNativeSrc("commands")`
     wiring it lacked, and three suites now run on both platforms
     (`RegressionTests`, `BastGenCommandTest`, `UnbastifyCommandTest`). Native
     47 → **52**; JVM unchanged at 245, so the gap is now **−193**.
     **The ~200 win hoped for here is NOT available.** The corpus gate
     (`RiddlModelsRoundTripTest`, 189 cases) does run JVM-only, as suspected — but
     it imports `org.apache.commons.io.FileUtils` and `scala.jdk.StreamConverters`.
     The usage is shallow (ONE `forceDeleteOnExit` on the CI download path, TWO
     `.toScala(Seq)` conversions of a `Files.walk`), so the rewrite is small.
     **The rewrite is not the blocker.** That suite WRITES a `.bast` beside every
     model in `../riddl-models` and restores each in a `finally`; running it on
     two platforms means two runs mutating one shared external directory, and sbt
     may run those rows concurrently. **Porting it needs a decision about
     isolating the corpus per platform, not a dependency swap** — that is the
     open question, and it is a design call rather than a chore.
     Also still JVM-bound: `RunCommandOnExamplesTest` (commons-io `FileUtils` +
     `filefilter`) and, through it, `RunCommandsOnExamplesTest` and
     `NamespaceTest`.
     **Method note that cost a cycle: scanning imports OVERSTATES what is
     movable.** `NamespaceTest` imports nothing JVM-only and is still unmovable,
     because it EXTENDS `RunCommandOnExamplesTest`. Check the base class too —
     moved it, watched it fail to compile, moved it back.
     **Candidate scan for the next two modules** (files under `src/test/scalajvm`
     importing no `commons-io`/`scala.jdk`/`java.io`/reflection), recorded so it
     is not re-derived — but treat it as an UPPER BOUND, per the base-class trap
     above, and note several are deliberate JVM runners for shared abstract
     suites (`JVMASTTest`, `JVMValidationTest`, `JVMDiagramsPassTest`) that may
     already have Native twins: **language 19 files**, **passes 32** (dominated by
     one homogeneous block of ~20 `*BAST*` suites, which is the obvious next
     batch), **utils 6**.
  2. **`language` (−156, was −325)** — the abstract-suite half is DONE (see
     PROGRESS above). What remains is its 22 `src/test/scalajvm` files, which
     need the per-file triage below rather than a wiring fix.
  3. **`passes` (−156)** — 26 shared, 139 `scala-jvm-native`, 29 `scalajvm`. This
     module already does the right thing at scale, so the residue is likely
     genuinely JVM-bound; audit it last.

  **The `language` fix is the template for the other two:** the suites were
  already compiled for all three platforms and only the CONCRETE RUNNER was
  JVM-only, so moving it to `src/test/scala-jvm-native` cost nothing and excluded
  nothing. Check for that shape first in each module before assuming a test is
  genuinely JVM-bound.

  **79 test files sit under `src/test/scalajvm` across the seven modules** (utils
  11, language 22, passes 29, commands 7, riddlLib 7, riddlc 1, testkit 2). Each
  needs the same triage: does it use a JVM-only facility, or was `scalajvm` just
  where it got written? Where a real JVM dependency exists (filesystem, reflection,
  `java.*` APIs without a Native equivalent, `Await` on JVM-only futures), ask
  whether it can be abstracted behind `PlatformContext` — which exists for exactly
  this — rather than accepted as unportable.

  **Why this matters beyond coverage arithmetic:** Native fails DIFFERENTLY. It
  rejects regex lookahead the other platforms accept, and a pattern compiled in a
  `val` fails at class INITIALISATION, surfacing as a Severe message with EMPTY
  text that names nothing (`.claude/skills/rc/SKILL.md` § Red flags). A JVM-green
  suite says nothing about that class of defect.

  **Do not lower the Native floor to accommodate anything found here.** If a
  count drops, the standard of proof is the one the rc skill records for the
  2026-08-05 drop: per-row before/after with the unchanged rows shown unchanged.


- ~~**GAP: 13 shared `language` parser suites — 169 cases — never ran on
  Native**~~ — **DONE 2026-08-14, `546f2f834`.** Concrete runners moved from
  `src/test/scalajvm` to `src/test/scala-jvm-native` and renamed `JVMNativeTests`.
  Native `language` 343 → 512, exactly the predicted +169, nothing excluded and
  nothing weakened — the suites build every input from `RiddlParserInput` and
  string literals, so no Native hazard was present. Rolled into the JVM/Native
  gap item above.

- **MEASUREMENT ATTEMPTED 2026-08-14 and it FAILED — a grep cannot answer this.**
  Reid: *"I don't really care, but go ahead and count how many models do this,
  probably not many."* Recorded so the next person does not repeat the attempt.
  What is solid: **7,561** lines carry a `tell`; of **8,254** `to <kind> <path>`
  targets in the corpus, **7,396 are DOTTED (89.6%)** and only **175 are bare**.
  What is NOT solid: a dotted path means the author QUALIFIED the target, not
  that it crosses a context boundary — `to entity OrchestrationContext.Marketplace‑
  Order` may name an entity in the teller's own context. Comparing the path's
  first segment against the nearest enclosing `context` was tried and is
  unsound: sagas and adaptors sit at DOMAIN level, outside any context, so the
  tracked context is stale for exactly the statements most likely to cross a
  boundary. It reported 7,393 of 7,396 as crossing, which is not credible.
  **The real count needs RESOLUTION, not text** — compare the telling
  processor's enclosing Context to the resolved target's, which is a throwaway
  pass over the corpus (or a `riddlc` run), not a command. Given "I don't really
  care", the honest read is: **qualified targets are near-universal, so if the
  seam rule bites it will bite widely** — which is itself the argument for the
  warn-then-flip sequencing this repo now uses twice.

- **Cross-context `tell` isolation seam — MEASURED 2026-08-15. The heuristic was
  wrong by 294x, and the migration is 18 sites.**
  Reid ruled 2026-08-13 that a `tell` into a different context is an Error unless
  the message type is declared in a domain ancestral to both; across domains an
  adaptor is always required. Separately, a cross-context tell is **always** a
  durable channel — the common-domain exemption waives the adaptor, never the
  durability. This completes **A4 (ACCEPTED)**, extending to foreign processor
  TARGETS the seam A4 already applies to foreign message TYPES.

  **The count the entry demanded, done by RESOLUTION rather than text** (a
  throwaway pass over 188 corpus models; each `tell`'s telling Context compared
  against its RESOLVED target's Context):

  | | count | share |
  |---|---|---|
  | `tell` statements | **7,537** | |
  | target unresolved | 0 | — |
  | same context | 7,519 | **99.76%** |
  | **CROSS context** | **18** | **0.24%** |
  | ...crossing DOMAINS (adaptor always required) | 0 | |

  **The old heuristic said 5,301 (64%). The real figure is 18 (0.24%).** The
  entry's conclusion drawn from that heuristic — *"qualified targets are
  near-universal, so if the seam rule bites it will bite widely, which is itself
  the argument for warn-then-flip"* — **does not survive the measurement.** A
  dotted path means the author qualified the target, which is a house style, not
  a boundary crossing; nearly every qualified target names something in the
  teller's own context.

  **All 18 sit in TWO models**, so the migration is a morning's work, not a
  campaign: `ticket-sales/TicketContext.riddl` (4, all `TicketContext ->
  MarketingService`) and `reactive-bbq` (14 — FrontOfHouse -> Kitchen/Bar/Loyalty,
  OnlineOrdering -> Kitchen/Loyalty/Delivery, Delivery -> NotificationService x5,
  MenuManagement -> FrontOfHouse). Every one shares a domain, so none needs an
  adaptor; each needs its message type moved to the common ancestor domain, or an
  adaptor by choice.

  **CAVEAT, and it is the reason the exemption count is not quoted above.** The
  probe could not evaluate the exemption for a `tell` whose operand is a
  `ValueRef` (a `let`-bound constructed message) — there is no `MessageRef` to
  resolve to a `Type`, so those sites' message-type domain is unknown. At least
  the four `ticket-sales` sites are of that shape. **The 18 is solid; treat any
  exemption figure as a lower bound** until the real check, which will have the
  operand's resolved type in hand, reports it.

  **What this changes about sequencing.** With 18 sites the warn-then-flip
  ceremony buys little — the Error can ship with the migration filed alongside
  it. That is a decision, not a deduction, and it is queued (NOTEBOOK §
  QUESTIONS, Q4). Note also this census counted `tell` ONLY; whether `send` is in
  scope for the same seam is unstated in the ruling and should be settled before
  building.

- **Clusterability: `clustered`, and `self.isClustered`.** Split out of the
  identity design 2026-08-13. NOT "multiplicity" — Reid ruled that **entity is
  the only multiply-instantiated processor**; contexts, projectors, streamlets,
  repositories and adaptors are singletons that may be clustered for resilience,
  and clustered instances are interchangeable so clustering does not affect
  addressability.
  **PLANNED 2026-08-15, NOT BUILT — needs a ruling first (NOTEBOOK § QUESTIONS,
  Q3), because the item as filed contains a contradiction.**

  **The contradiction, which is the main finding.** This item promises BOTH a
  `clustered` keyword AND `self.isClustered`. **You can have either, not both.**
  Reid's own admission test for `self` is *runtime-only — anything a generator
  can know statically it should inline*. If `clustered` is written in the model,
  then whether a processor is clustered becomes STATICALLY KNOWABLE, and
  `self.isClustered` is exactly what the test excludes. The field is only
  admissible if clustering is a DEPLOYMENT-time fact absent from the model —
  in which case there is no keyword to add. The 2026-08-15 `self`-fields survey
  reached the same conclusion from the other direction and rejected
  `isClustered` outright.

  **What the CM already settles, so the plan need not re-litigate it:**
  - A Context is a deployment unit and "a conforming realization may run many
    incarnations … may form regional, zonal, or geographical clusters. A
    TypeScript generator must NOT assume singleton processes" (§3.1).
  - Projectors and Streamlets are each "a unit of deployment, clusterability,
    and scalability" (§6.1, §11.1) — so clusterability is ALREADY a CM concept
    for every processor; what is missing is only a way to SAY it in a model.
  - Addressing a clustered singleton means "select the right shard/partition and
    forward … partitioning is load management, not identity" (§4.2).
  - **The one MANDATORY constraint**: a projector with correlations "is no longer
    stateless, and every event bearing a given key tuple must reach the instance
    holding that tuple's partial, so distribution must be by key" (§6.1/§6.6).
    Round-robin is legal ONLY for a correlation-free projector.

  **The design question that follows from that last point.** Everywhere else,
  clustering is ADVISORY — a generator may honour it or deploy one instance, and
  §4.2 says options are advisory, so `clustered` would be an `option`. But
  key-distribution for a correlating projector is a CORRECTNESS requirement, not
  a preference. That is the same shape as the entity-intentions ruling, where a
  hard Error keyed off advisory metadata was judged a category error and the
  keywords became grammar. So: is `clustered` an advisory `option`, or an
  intention in the grammar like `event-sourced` and `at-least-once`? *My reading:
  `option`, because a generator may legitimately decline to cluster — and the
  mandatory half is already implied by declaring correlations, so it needs no
  keyword at all. But that is a ruling, not a deduction.*

  **Scope note:** applying `clustered` to an Entity is meaningless — an entity is
  already distributed by identity (§4.1), so it is sharded by construction. The
  keyword, if admitted, belongs on the five singleton processor kinds only.

- ~~**Survey the CM and every A item for future `self` fields.**~~ — **DONE
  2026-08-15.** Reid's admission test applied: is it runtime-only? Anything a
  generator can know statically it should inline, which is why `version` is in
  and `isClustered` is not. Result — **one genuine candidate, one documentation
  debt, and everything else rejected with a reason.**

  **CANDIDATE: `self.state`** — the FSM state the instance currently occupies
  (§4.5: "the entity occupies exactly one named state at a time", changed by
  `morph`). **The interesting part is that it is only sometimes runtime-only.**
  Inside a handler declared within a `State`, the current state is known
  STATICALLY by construction — the clause could not be running otherwise — so
  there `self.state` is exactly the kind of thing the admission test excludes.
  But a handler declared directly on the ENTITY handles its message whatever
  state the instance is in, and there the current state is genuinely unknowable
  until run time. So the candidate is real but narrow, and admitting it would
  put a field on `self` that is redundant in one position and essential in
  another. **Queued for a ruling (NOTEBOOK § QUESTIONS, Q5)** rather than
  decided: the admission test gives two different answers depending on where you
  stand, which is precisely the sort of thing this survey was meant to surface.

  **~~DEBT: `self.version`'s meaning is undefined~~ — WRONG, RETRACTED 2026-08-16.**
  I filed it as semantically undefined and as the same defect class as
  `Integer`/`Whole`/`Natural` shipping without ranges. It is neither. Reid: it is
  the fully-qualified version number from RIDDL's `version` definition — **static
  but COMPUTED at generation time**, components joined with `.`. That is **A53,
  already implemented**: `AST.composedVersionString` (`VersionSeparator = "."`)
  yields e.g. `"Jellyfish.Garibaldi.4.2"` (pinned by `CopyrightTest:234`) and is
  exposed as `AnalysisResult.composedVersionStringOf`. Its purpose is developer
  convenience: a component names its own version accurately **even when a PARENT
  component's version changes**, because the coordinate is composed from
  versioned ancestors.
  **What was actually missing was the LINK, and it has been written** (2026-08-16):
  `SelfValue.fieldNames`' scaladoc now says which field qualifies for which
  reason, and the CM §4.5 now tells a generator to resolve `self.version` at
  generation time rather than carry it at run time.
  **The lesson is mine, not the language's: I searched for a DEFINITION of
  `self.version` and found none, and concluded there was none — without asking
  what already-implemented thing it might be naming.** A53 was right there. An
  absent definition is evidence of an absent definition, not of an absent
  concept.
  **It also falsified the admission principle as the code stated it.** The
  scaladoc said `self` "carries what cannot be known statically, which is why
  `version` is here" — but `version` is static. The real test, now written down:
  a field belongs on `self` when the author would otherwise restate something
  that can DRIFT, either unknowable until run time (`id`) or derived from context
  that changes without them touching this definition (`version`).

  **REJECTED, with the reason, so they are not re-proposed:**
  - `isClustered`, enclosing context/domain names, the processor's own kind,
    the handler name — all **statically knowable**; a generator inlines them.
  - `correlationId`, `messageId`, `source`, `time`, `replyTo` — these belong to
    the **Envelope** (CloudEvents context attributes, reachable via `option
    message_envelope` and `on other as x`). Duplicating a modelled concept onto
    `self` would create two ways to say one thing, and they would be free to
    disagree.
  - `isActive` / `isPassivated` / shard / partition — **the CM says these are
    invisible to the model.** §4.5 makes activation and passivation "the
    runtime's business", and clustering treats instances as interchangeable.
    Exposing them would let a model depend on something the CM explicitly
    reserves to the runtime, which is worse than merely redundant.

- **Computational Model amendments owed by the identity design.** Three, all
  from 2026-08-13: (a) "activate on first message" (§4, line 999) must become
  rehydrate-an-existing-instance, never create-on-demand, now that `initiate`
  invokes `on init` explicitly; (b) the memory-space axiom — only processors
  within one context are guaranteed to share memory, which is what licenses a
  generator to optimize the same-context `tell`; (c) `Id(P)` (runtime instance
  identity) must not be conflated with the definition ULIDs of line 2523
  (model-time identity of a definition).

- ~~**A20 typed holes: `checkPromptAscription` is not wired at every position**~~ —
  **DONE 2026-08-15, `0fd7bb54e`.** All seven positions are wired: `put`,
  `return`, `require … with`, and the four argument positions
  (`Constructor`/`Call`/`Initiate`/`TerminateStatement`).
  **The decision this entry asked for dissolved on contact.** It said "most need
  the same expected-type lookup ... already do elsewhere", implying some would
  need new machinery and might not be worth it. In fact ALL seven already had
  the expected type in hand where the ascription is visible — there was nothing
  to build, only somewhere to call — so there was no position for which leaving
  it was the better trade.
  **Four of the seven cost ONE call site.** `checkArgumentTypes` already binds
  each argument to its field, and `checkLifecycleInvocation` adapts
  `MethodArgument`s into `Field`s precisely so it can reuse that helper.
  **Which side of the comparison to pass is the part worth remembering.**
  `field.typeEx` goes in directly because it is the type as WRITTEN, which is
  what a syntactic comparison needs; a RESOLVED `Type` must be re-wrapped by
  `selfNamedTypeExpression`, because passing its `typEx` would compare
  `as OrderId` against the underlying `Id(entity Order)` and report a false
  contradiction on correct code.
  The sibling RENDERING defect at the same positions was already closed
  separately (`RiddlFileEmitter.emitValue` is total over them).

### 2. Queued, needs a plan

#### Decided in `../RIDDL-Tools-To-Do-List.md` but never built

**RULINGS TAKEN 2026-08-14, before an unattended run.** Reid answered four questions
up front. Three are built (`exactly-once`, A43+A46 verbatim, and — as of
2026-08-15 — A20). The remaining one (A38, below) is APPROVED BUT NOT BUILT.

**THE `FORMAT_REVISION` BUMP (17 -> 18) IS SPENT — numeric literals landed and
consumed it** (`6cfeceb2f`, 2026-08-14/15). **DONE 2026-08-15 — A20 typed
holes shipped riding the same revision 18** (spelled `prompt("…") as T`,
Reid's choice over `prompt T ("…")` and over the document's un-RIDDL
`Value[T]("prose")`; reuses the shipped `prompt` and ascribes a type after
it, matching `on foo: command Foo` and `let x: T = …`, so nothing new
entered the lexer). **A38 is now the LAST claimant of revision 18** — it
still adds/changes an AST node BAST must carry, and 18 has not shipped in a
release yet, so it rides too rather than bumping again. Decide differently
only if 18 ships before A38 lands — then A38 bumps to 19 and says so in its
commit, per the message-value plan's "the 16 -> 17 bump is SPENT" precedent.

Still unbuilt from A46: the compound-output noun/verb consistency warning (a sound, a
window and a haptic inside one output). The VERBS shipped; this diagnostic did not, and
it is the design-y half.


Surfaced 2026-08-14 by reconciling that document against this branch. **Six**
items carry an ACCEPTED ruling and had no backlog entry, so none was tracked.
Reid, 2026-08-14: *"add the things not built yet to the backlog so they stand a
chance of being implemented in 2.0.0."* Each was verified unbuilt by the grep
quoted with it — **re-run the grep rather than re-deriving the finding**, and
scope it to the whole repo: the first pass of this audit called A42(ii) unbuilt
on a grammar-only grep and was wrong, because the REST client lives in `utils`.
A seventh entry closes the section: a contradiction between the two documents
that needs a ruling before either can be fixed.

- **A42 (iii) — Figma bidirectional scaffolding.** Generate Figma wireframe
  skeletons mirroring the group tree, and draft RIDDL from Figma.
  Verified: **parts (i) and (ii) are BOTH shipped** — `figma_ref` in the grammar,
  `FigmaRef` in `AST.scala:1671` with placement enforced by `mayCarryFigmaRef`,
  and real drift validation via `FigmaClient` (cross-platform, memoized,
  four-valued `FigmaLookup`, off by default behind `--check-figma-drift` +
  `FIGMA_TOKEN`). Only (iii) is missing: `grep -rli "wireframe|scaffold"
  --include="*.scala" .` returns nothing.
  This is **generator work, not riddlc's** — it pairs with Part B item 4 and
  probably belongs in riddl-gen. Filed here so it is tracked somewhere; move it
  when that repo grows a backlog.

- **A38 — the refusal step's operand should name an invariant, not prose.**
  The step kind shipped as `any_interaction_ref "refuses" user_ref
  literal_string`, so the reason is a **prose string**. A38's whole purpose was
  closing the loop between the requirement's named invariant, the `require` that
  enforces it, and the InvariantViolated result a generated test asserts — and a
  string closes none of it. Change the operand to an `invariant_ref`, or admit
  both and warn on the prose form.
  Verified: the rule is in `ebnf-grammar.ebnf` under `step_interactions`.
  Touches parser + EBNF + GBNF + prettify + BAST + JSON, so it needs a
  `FORMAT_REVISION` bump; the corpus must be surveyed for existing prose
  refusals before the string form is removed.

- **RULED 2026-08-14 — `on other` is necessary to the LANGUAGE, not required in
  every handler. A5's generalization is DECLINED; omission is correct.**
  Reid, correcting a first reading of this ruling that took "it MUST be there"
  to mean per-handler:

  > *"`on other` is necessary to the language, not necessary in every handler.
  > If there is nothing to do for a message that is not otherwise handled, then
  > it can be omitted and that is fine. It's better than an `on other { do
  > "nothing" }` kind of nonsense construct, even if that would be good
  > validation."*

  So the construct's job is real — it is the fall-through of a switch, firing
  when a processor receives a message no on-message clause in the handler
  matches — but **an author who has nothing to do on that path writes nothing.**
  Note what the last clause concedes and then overrules: requiring the clause
  WOULD make validation better, and is rejected anyway, because a language that
  forces a do-nothing construct buys its diagnostics by making models lie about
  intent. **Do not re-open this by arguing the validation benefit; it was already
  weighed.**

  **Consequences — three, and the third is an open question:**

  1. **No new presence check.** A5's *"consider generalizing the presence and
     completeness check across all processor kinds"* is considered and DECLINED.
     The comment at `ValidationPass.scala:3492` promising to generalize "later"
     is now stale and should be corrected in place, or the next reader will
     build it.
  2. **The empty-`on other` warning STAYS, and its advice is now clearly the
     right one.** `ValidationPass.scala:553-563` warns that an empty clause
     "will silently discard unhandled messages" and suggests adding statements
     *"or remove it if discarding is intentional"*. That second branch is
     exactly this ruling: the fix for a do-nothing clause is deletion, not
     filling. No change needed.
  3. **ANSWERED 2026-08-14 — the ADAPTOR Error STANDS, and it is not an
     exception.** Reid:

     > *"Adaptors are special since they are translators. They must translate
     > everything, including messages they are not designed to translate! Even
     > if that translation is 'Sorry, I can't translate that'. Doing nothing on
     > an unknown message is to silently omit from an inter-context
     > conversation."*

     This is an **application** of the general ruling, not a carve-out from it.
     The general rule is "nothing to do → omit the clause"; for an adaptor there
     is **never nothing to do**, because refusing to translate is itself a
     translation that the other context is owed. That is exactly why the clause
     is required here and nowhere else — and it means the rule needs no special
     pleading if it is ever questioned again.
     **Corollary worth knowing, not yet acted on:** by the same argument an
     adaptor's `on other` with an EMPTY body is as wrong as a missing one — it
     drops the message just as silently. Today that is only the model-wide
     Completeness warning (`:553-563`), not the adaptor Error. Whether to raise
     it for adaptors specifically was not asked and is not assumed.

     *Comment at `ValidationPass.scala:3489` corrected 2026-08-14 to record all
     of this in place, since the stale "generalize later" promise was what put
     the question on the table.*

  **The corpus measurement now reads the other way, and supports the ruling.**
  Brace-matched over riddl-models, 2026-08-14: of **3,606** handler blocks,
  **2,311 (64.1%)** carry an `on other` and **1,295 (35.9%)** do not, across 22
  of 29 model dirs. Under the first (wrong) reading that was migration scope.
  Under the ruling it is evidence: **roughly a third of handlers legitimately
  have nothing to do on the fall-through path**, and a presence check would have
  told all 1,295 of them to write nonsense. Kept because it is the number that
  would have been re-derived to justify the check.

  **A5's sentence is still struck from `../RIDDL-Tools-To-Do-List.md`, but for a
  different reason than first recorded.** The sentence — *"Silence is only a
  deliberate filter when an explicit 'on other' clause exists and does nothing
  with the message"* — is wrong because **omission IS the deliberate filter**.
  It required an explicit do-nothing clause as the marker of intent, which is
  precisely the construct this ruling calls nonsense. (The first correction said
  it was wrong because presence is mandatory. That was the misreading.)

- **Audit the remaining catch-all matches against Reid's no-silent-fallthrough
  rule.** **Add `Finder.fieldChildren` to the list** (2026-08-15): it is 29
  hand-written cases ending in `case _ => Seq.empty`, so a future node holding
  statements or values in a FIELD returns nothing rather than failing loudly.
  Landed deliberately — consolidating four scattered special cases into ONE
  extension point was a genuine improvement and was not held for this — but it
  is the same shape as everything else in this item, and the next field-held
  node will be silently invisible to every `Finder` consumer. All 11 `Value`
  arms and all 18 `Statement` arms are covered *today*; nothing keeps arm 12 or
  19 covered tomorrow.
  Reid ruled 2026-08-09: *"There must be no non-sealed matches — it is
  okay to fall through to generate an error or exception but not okay to not
  select anything and then carry on as if nothing happened."* Offered as a
  follow-up and never answered, so it is filed rather than lost.

  **What is already done** (do not redo): the total dispatches were fixed at
  `286ef8157` and around it — `Pass.processValue` now throws on an unhandled
  `Value` rather than returning unit, `BASTWriter`/`BASTReader` throw instead of
  a `println`-and-drop and a placeholder `PromptStatement`, `classifyHandlers`
  enumerates all 17 `Statement` kinds with no catch-all, and
  `countValueFailPoints` enumerates the rest.

  **What is NOT done:** roughly 140 remaining `case _ => ()` sites across the
  codebase, most of which are legitimately "not interested in this node" rather
  than "silently gave up". The work is to separate those two, not to delete the
  arm — a mechanical sweep would be wrong. Suggested order: `passes/` first
  (where a miss changes validation results), then `language/`, then the
  serialization surfaces, which are already done.

  Start with `grep -rn "case _ =>" --include=*.scala passes/ language/ | wc -l`
  to size it before committing to a plan.

  **Widen the sweep to no-op HOOKS, not just catch-all arms** (added 2026-08-14,
  from the prettify emitter fix `2ebe24a6c`). `PrettifyVisitor.doMethod` was
  `Unit = ()` with the comment *"Methods are handled by their type"* — they were
  not, and every `method` was dropped from prettified output. The arm was
  perfectly explicit and the dispatch perfectly total; the defect was in a
  CLAIM ABOUT CODE ELSEWHERE that nothing verified. Same shape in
  `Pass.processValue`, where `ShownBy` was skipped because such values are
  *"read by the definition that holds them"* — a survey of the visitors that
  existed, not a property of the node, and false the moment prettify needed it.
  So the sweep's question is not only *"does this arm mean 'I don't know what
  this is'?"* but also **"if this arm defers to something else, does that
  something else actually do it?"** Both were found by reading, never by the
  compiler: `-Werror` is live in `passes` but a wildcard arm makes a match
  syntactically exhaustive, so the prescribed terminal `throw` is itself what
  silences the warning.
  A cheap first pass: `grep -n "Unit = ()" passes/…/prettify/PrettifyVisitor.scala`
  and check each justification against the code it names. The five there are all
  legitimate NOW — `doMethod` only became so with this fix.

  **Widen the sweep a third time: to ONE-LEVEL COLLECTORS** (added 2026-08-15,
  from `DiagramsPass.captureUseCase`, `b8a6057fb`, reported by riddl-generator).
  A third shape that this sweep's two questions both miss. `captureUseCase`
  enumerated its cases explicitly — no catch-all — and deferred nothing to
  anywhere, so it passes both tests above and was still wrong: it mapped over
  `uc.contents.toSeq` and gave `InteractionContainer` an arm returning
  `Seq.empty`, so nested steps were never collected. **The arm was not a
  fall-through; it was a wrong answer, written deliberately.** Consumers render
  from the same data by RECURSING through those containers, so capture and
  render disagreed about what a use case contains.
  The symptom is the one this whole item keeps circling: an empty result is
  indistinguishable from a model that does not use the construct. Note it was
  only *sometimes* empty — 4 of reactive-bbq's 12 use cases returned partial maps
  and 1 returned nothing, which is worse, because a partial answer is not even
  suspicious.
  So the third question is: **"does this collector descend as far as the code
  that CONSUMES its output does?"** Sizing grep:
  `grep -rn "contents.toSeq" --include=*.scala passes/ | grep -v "flatMap\|Finder"`.
  Same family as `Finder.recursiveFindByType` (`b55d1d5cc`) — riddlg hit both
  within a day, from different directions, which is the reason to believe there
  are more.
  **A fourth defect rode along and is worth its own line, because no sweep of
  matches or traversals would find it**: `.sortWith(actorsFirst).toMap` silently
  DISCARDS the sort. Scala's `Map1`..`Map4` keep insertion order incidentally;
  the fifth entry becomes a hash-ordered `HashMap`. Any `.sorted…toMap` pipeline
  in this codebase is a latent version of the same bug and is invisible below
  five elements — `grep -rn "sortWith\|sortBy" --include=*.scala passes/ | grep -i "toMap"`
  is worth running once. Fixed here with `immutable.VectorMap`, which preserves
  the declared `Map` type.

- **Finish the `Streamlet` → `Processor` migration in the remaining passes.**
  Filed by Reid 2026-08-10 when the same defect was fixed in
  `StreamingValidation` (`70b0f527a`). These sites narrow to the concrete
  `Streamlet` case class the same way the streaming graph did, so they see one
  of the six processor kinds and silently ignore the rest:

  - `AnalysisResult.scala:179` — `symbols.parentage.keys.collect { case s:
    Streamlet => s }`. **Public API whose MEANING would change**, so it needs a
    decision, not just an edit: does `AnalysisResult.streamlets` mean "the
    Streamlet definitions" or "the port-bearing processors"? Adding a second
    accessor is the additive option the compatibility policy prefers.
  - `MessageFlowPass.scala:291,303`
  - `DiagramsPass.scala:193,216,394,450,456`
  - `StatsPass.scala:171`

  Out of scope deliberately in `70b0f527a` — different consumers, and the
  public-API question above. `Pass.scala`'s `openStreamlet`/`closeStreamlet`
  and `RiddlFileEmitter`/`PrettifyVisitor`'s uses are NOT in this list: those
  are legitimately about the case class (visitor hooks, keyword emission).

- **A lookup value: `<mapping|array> at <index>`.** Reid, 2026-08-10, syntax his
  suggestion. Wants a plan. Filed out of the `foreach`-over-a-mapping question:
  the destructuring form below covers the loop body, but **outside a loop a
  mapping is currently write-only** — there is no way to name the value stored
  at a key, so a model can declare a mapping and never read it. The same holds
  inside a loop for any key other than the one being iterated.

  **Verified absent, not assumed.** The `Value` union is `LiteralString |
  PromptValue | Constructor | ValueRef | GetValue | BooleanExpression | Call |
  Ask` (AST.scala:2933) — nothing indexes. `GetValue` (AST.scala:3081) is `get
  from <inlet|state>`, a read of a port or state, not a subscript. The grammar
  has no subscript form either; `index on <field>` (ebnf-grammar.ebnf:439) is
  schema metadata.

  **Design questions the plan must answer:**
  - **What it yields when the key is absent** — the hard one. An Optional
    result needs a way to interrogate it, which RIDDL has no syntax for; an
    Error is untrue, since a missing key is ordinary; a total function needs a
    default nobody declared. Most likely `canFail = true` and let the value's
    existing failure machinery carry it, which is what `countValueFailPoints`
    already does for `send`/`tell`/`call`/`yield`/`reply`/`put`/`get`.
  - **What it applies to.** `Mapping` by key is the motivating case. A
    `Sequence`/`Table` by ordinal is the "array" half of Reid's syntax, and
    `Set`/`Graph` have no index at all, so this is not simply "any collection".
  - **The result type** — `to` for a Mapping, the element type for a sequence.
    `collectionElementType` (ValidationPass) already computes the latter.
  - Whether `at` reads well in a `when` guard, its most likely home.

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
- ~~`BASTPerformanceBenchmark` is timing-flaky~~ — **DONE 2026-08-07,
  `5c3d5cbc8`.** Cold single-shot measurement now reported, not asserted; the
  warmed 50-iteration benchmark gates on the MEDIAN rather than the mean.
- ~~`BASTParserInput`'s synthetic line index is unreachable dead code~~ — **DONE
  2026-08-07, `307812fa8`, but the premise here was FALSE and cost a wrong
  first move.** This entry claimed "with `positionsKnown = false`, `At` never
  consults its `lineOf`/`offsetOf`". It did: `At.endLine` and the `endCol`
  inside `At.toLong` were UNGUARDED, so the 10000-chars-per-line scheme was
  live, and every BAST-sourced message formatted as `(0:0->1:N)` — honest start
  line, fabricated end line. It was wrong OUTPUT, not dead code. Deleting the
  overrides under the stated premise would have moved the fabrication into the
  base class instead of removing it. Guarding `endLine`/`endCol` came first;
  only then was the machinery genuinely dead.
- **Should an imported definition RESOLVE without an explicit flatten?** The
  accessors now report `.bast`-imported definitions (2026-08-03), so
  `domain.types` lists an imported type — but a reference to it still fails to
  resolve until `FlattenPass` runs, because the symbol table is built by
  traversal rather than by the accessors. That split is currently pinned by
  `BASTImportLoadingTest`, and it is defensible (reading is the client's
  question, resolving is the model's), but it means a model can name a type its
  own accessors report. Decide whether SymbolsPass should index wrapper
  contents, or whether the flatten requirement should be stated more loudly.
- ~~**A conditionally refusing clause escapes yield conformance**~~ — **DONE
  2026-08-07, `1d87a109a`**, at BOTH sites (Reid's call): the Error in
  `checkYieldConformance` and its CompletenessWarning sibling.
  `dischargesOnEveryPath` replaces "does a refusal appear ANYWHERE in the
  clause". A `when` needs both branches, a `match` needs every case AND a
  `default`, a `foreach` never discharges. Error wording moved to "does not
  yield it on every path" — "never" stopped being true.
  **Two findings worth keeping:**
  1. **Emitting ANY message settles a path**, not just yielding the declared
     event or refusing with `error`/`require`. The first version assumed
     declining means `error`/`require`, and NO in-repo fixture could have
     contradicted it, because every fixture shared the assumption. riddl-models
     reactive-bbq (`LoyaltyAccount.riddl:579`) declines by RECORDING a rejection
     event — the more faithful design for an event-sourced entity. The external
     corpus was the only thing that could catch this.
  2. **An empty `else { }` is a PARSE ERROR** — an empty pseudo-code block does
     not parse. So the escape is not an empty else but a NON-EMPTY one that
     neither yields nor refuses. Making `else`/`default` mandatory in the
     grammar was considered and REJECTED (Reid, 2026-08-07): ~56 sites across
     three repos, and it would not have closed the hole anyway.
- **The QueryCase completeness check shares the "anywhere in the clause"
  shape.** Filed 2026-08-07 out of `1d87a109a`, deliberately not changed there —
  it was not the approved hole, and it has no refusal exemption, so the
  conditional-refusal escape does not apply to it in the same way. QueryCase
  still asks whether a result-emitting statement appears anywhere rather than on
  every path. Decide whether it should use `dischargesOnEveryPath` too; if so,
  expect the same kind of corpus correction the command case needed.
- ~~`Comment` in a `Group`'s contents cannot be rebuilt~~ — **STALE, resolved
  somewhere along the way; removed after verifying 2026-08-06.** Both halves of
  the claim are now false: `OccursInGroup` DOES include `Comment`
  (`AST.scala:900`) and `GroupParser.groupDefinitions` accepts it
  (`GroupParser.scala:28`). The "3 pinned occurrences" are gone —
  `Root2JsonFixturesTest` reports `identical=91`, `lossy=0`, `divergent=0`, and
  the test carries no Comment allowance. No decision needed; nothing to do.
- **Saga step statements are NEVER VALIDATED — not just reachability.**
  **⚠ LIKELY ALREADY FIXED — verify and close before doing any work here.**
  Found 2026-08-11: `git log -S "case sagaStep: SagaStep =>" -- passes/…/Pass.scala`
  gives **`a1bce0d50` (2026-08-07) "Traverse saga step statements, which were
  never validated at all"** — one day AFTER this item was filed. `Pass.traverse`
  now has a `SagaStep` case ahead of `case leaf: Leaf` that traverses
  `doStatements`/`undoStatements`, which is exactly the root cause described
  below. Re-run the repro before treating any of the following as true; if it is
  green, delete this item and keep only the reachability question if that
  survives. Not deleted outright because the entry has several sub-claims and
  only the traversal one was checked.

  Filed as "saga reachability", VERIFIED 2026-08-06 and it is materially worse
  than that.
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
  `contents` rather than in it, so the traversal never descends into them.

  **DONE 2026-08-07, `a1bce0d50` — but NOT the way this entry proposed, and the
  reason is kept so it is not re-proposed.** This entry called for the
  `3e4af6801` treatment: make `SagaStep` a `Branch`, statements into `contents`.
  **That precedent does not transfer:** it moved two SINGLETON clauses into one
  ordered list, whereas a SagaStep has TWO distinct blocks, and
  `JsonModel.SagaStepDto` exposes them as separate `do` and `undo` arrays.
  Merging them loses a distinction the JSON wire format — read by synapify and
  riddl-gen — depends on, and forces a BAST `FORMAT_REVISION` bump.

  What was done instead: one `SagaStep` case in the base `Pass.traverse`,
  descending into both statement lists WITHOUT pushing (`ParentStack.push` takes
  a `Branch[?]`; Include and BASTImport already traverse this way, and
  `parents.head` staying the Saga is the correct resolution scope). No AST, JSON
  or BAST change. ~12 `Pass`-derived passes now see saga statements;
  `HierarchyPass` overrides `traverse` entirely, so Prettify, BASTWriter,
  Outline and Tree were untouched.

  The predicted "wave of findings" did not materialise — 23 saga steps across
  all three corpora, and the only golden change was the REMOVAL of two FALSE
  `is unused` warnings (`blah` and `UndoSomething` are named by a step's `send`
  statements, so they were never unused, only unseen).
- ~~**`validateArbitraryInteraction`'s refMap path is dead**~~ — **ALREADY FIXED
  at `55d5dc6d9` (2026-07-27), "Fix arbitrary-interaction ref resolution (was
  dead — wrong refMap key)".** Discovered 2026-08-07 while planning to implement
  it: the call site already resolves with `useCase` as the scope and carries a
  comment saying so. This entry sat open for eleven days after the work shipped.
- ~~`PlatformContext.withOptions` lacks try/finally~~ — **STALE; `withOptions`
  was fixed at `2eefeec52` (2026-07-27), eight days before this entry was last
  read.** The unfixed twin was `withLogger`, directly above it, with the same
  failure mode: a throwing body leaves the swapped-in logger installed globally,
  so a later sequential suite writes into a dead test's capture buffer. **DONE
  2026-08-07, `359949e83`.**
- ~~**`Blob`, `Unknown`, `Range` are RESERVED BUT UNUSABLE.**~~ — **DONE
  2026-08-07, `f41cf399f`**, all three parts. Left below because the DIAGNOSIS
  is the durable part: three tables that look authoritative, two of which are
  not. Two residues were filed rather than fixed — see the two entries after
  this one. Original analysis, verified 2026-08-07
  by three `riddlc` probes, correcting an earlier reading of this entry that
  treated tokenizing as evidence of parseability — it is not. For all three,
  `type X is <Name>` fails with "Path '<Name>' was not resolved", AND defining
  your own type by that name fails with "redefines built-in type" — unusable in
  both directions. The cause is two tables that drifted apart:
  `PredefType.allPredefTypes` (reserves names; read by `ValidationPass:1268`)
  and `PredefTypes.anyPredefType` (tokenizer only; read by `TokenParser:61`).
  Neither is the REAL type parser, which is `TypeParser.predefinedTypes:327`.
  **Reid ruled 2026-08-07 — three different jobs, not one cleanup:**
  1. **Add `Blob` to the parser.** It is a legitimate type missing only its
     syntax: `AST.Blob(loc, BlobKind)` exists, the `BlobKind` enum exists,
     BASTWriter/Reader round-trip it and `ASTTest:117-124` tests its `format`.
     Grammar-surface change — parser rule + EBNF + GBNF regen + prettify +
     round-trip test + corpus re-run.
  2. **Drop capitalized `Range`, keep `range(n,m)`.** The working type is the
     LOWERCASE `range(1,10)` (`TypeParser:617` via `Keywords.range`,
     `Keyword.range = "range"`, verified validating clean). Capitalized `Range`
     is a phantom that only reserves the name and mis-highlights it; dropping
     it frees `Range` for users.
  3. **Drop `Unknown`.** Nothing behind it — no AST node, no parser rule, just
     the reservation and a tokenizer entry.

- **`TypeParserTest` has never run on Native.** Found 2026-08-07 while checking
  where new tests executed. It is `abstract class TypeParserTest` with concrete
  subclasses ONLY in `language/src/test/scalajvm/.../JVMTests.scala:22` and
  `language/src/test/scalajs/.../JSTests.scala:22` — there is no Native one, so
  ScalaTest never instantiates it there. This is CLAUDE.md's documented trap #2
  (abstract spec with no concrete subclass: cases never appear in the log at
  all, not even as skipped). Pre-existing, NOT introduced by the Blob work.
  **Do not just add the subclass and assume green** — it would newly execute a
  large parser suite on a platform that has never run it, and this repo's own
  history says to expect findings on a first run. Worth checking whether other
  `language` suites have the same gap; the audit is the task, not the one-liner.
- **Arity exemption for `error-sink` inlets** — riddl-models asked; deferred as a
  design decision, then FIXED in rc.9. Verify nothing else wants it.

### 3. Owed to other repos

- **DELIVERED 2026-08-15 to riddl-generator: `Finder` was returning incomplete
  results across 27 node fields.** Task dropped at
  `../riddl-generator/task/2026-08-15-finder-was-missing-content-across-27-fields.md`
  — **verified written, not merely claimed** (the 49-alias entry below is what
  that qualifier exists for). Fixed here at `b55d1d5cc` + `a3c0aa345`, both
  AFTER riddlg's pin and in **no tag yet** (latest is `2.0.0-rc.14`), so they
  cannot act until the next RC or a local `publishLocal`. Awaiting their reply
  on whether any of the 27 fields still comes back empty, and on whether any
  generated output got SHORTER (the unexpected direction).

- **BLOCKED ON riddl-models: reactive-bbq's 2 `terminate` lines.** `terminate`
  now names an INSTANCE (a value typed `Id(entity E)`), so
  `terminate entity FrontOfHouse.TableOrder` no longer parses. Both corpus uses
  are SELF-termination and become `terminate self.id`; task dropped at
  `../riddl-models/task/2026-08-15-terminate-now-names-an-instance.md` with the
  exact file:line pairs — **verified written to disk, not merely claimed.**
  Until it lands, `reactive-bbq` fails to PARSE, which costs
  `Root2JsonCorpusTest` its json-identity case (parsed 189/190 rather than
  190/190) and `PassCostBenchmark` its only case. **Neither is a defect here and
  neither must be "fixed" here.** A/B verified 2026-08-15: validation-parity is
  `cleanRoundTrip=59` both with and without the change, so the 59/190 baseline
  is UNMOVED, and `commands` is 115/130 both ways.
  ~~AWAITING riddl-generator: `terminate` design ruled but unimplemented.~~
  **CLOSED** — implemented the same day; their task file is in `task/done/`
  with the AST, the CM reference and the other moved surfaces.

- **AWAITING riddl-models: the exact `figma` input from their emitter report.**
  Their `2026-08-14-prettify-emitter-drops-method-and-shown-by.md` claimed
  `figma` on a domain or context "writes no file, exits 7, prints no error".
  **Not reproduced** — riddlc prints a specific Error (*"A 'figma' reference is
  not allowed on Domain 'Dom'; it may only appear on an input, an output, a
  group, or an application-intended context"*) and exit 7 with no output is
  CORRECT for a validation Error. Their report says they saw `[style]` and
  `[missing]` messages, which rules out `--quiet`, so something differs between
  the two inputs and guessing would be worse than asking. Asked in the Results
  section of that file (now in `task/done/`). **Nothing here is blocked on it**;
  if the real complaint is that A42 forbids `figma` on a domain at all, that is a
  language question and belongs in § 2, not a defect.
- **riddl-models' coverage model is being held out of their repo** until this
  lands, so **CI grammar validation is NOT currently exercising `method`,
  `shown by`, `table of … of […]`, `attachment`, `replica of` or `figma` against
  the corpus** — precisely the gate that would have caught all six emitter
  defects. Expect it to land after the next RC. Until then, the only coverage for
  those constructs is this repo's own round-trip tests
  (`AggregateContentsRoundTripTest`, `ShownByRoundTripTest`,
  `TypeExpressionSpacingRoundTripTest`, `AttachmentRoundTripTest`).

- **NEARLY UNBLOCKED — riddl-models landed the migration mid-session
  (2026-08-15 19:19).** Their `2e619c44`, *"Upgrade to riddl
  2.0.0-rc.14-121-fe768026 and validate the corpus cleanly"*, took the binary
  staged an hour earlier and did the `terminate` AND bare-message-operand
  migrations in one pass. **Re-measured immediately after, at that commit:**

  | measurement | earlier same day | now |
  |---|---|---|
  | corpus models validating clean | 59/190 | **188/190** |
  | `RiddlModelsRoundTripTest` | 115 ok / 130 failed | **187 ok / 2 failed** |
  | `Root2JsonCorpusTest` json-identity | 189/190 | **190/190 (100%)** |
  | `Root2JsonCorpusTest` validation-parity | 59 | **188 (98.9%)** |

  **All that remains is TWO models**, and they are the same two by every
  instrument: `patterns/entity/aggregate-root/example` (6 errors) and
  `patterns/entity/event-sourced/example` (14 errors), both still carrying
  bare-message operands (18 message-type + 2 record-type). Note both files are
  named `example.riddl`, so a failure list shows "example.riddl, example.riddl"
  — do not read that as one model reported twice.

  **⚠ `Root2JsonCorpusTest`'s NAME AND ASSERTION DISAGREE, and that is a defect
  here, not in the corpus.** The case is called *"...(>= 95% of models)"* and its
  own `+ validation-parity` line reports a percentage against that threshold —
  but `Root2JsonCorpusTest.scala:173` asserts **strict equality**, failing with
  `188 was not equal to 190`. So 98.9% clears the documented gate and fails the
  real one. Decide which is intended and make the two agree; this entry and § 0
  have both been repeating the ≥95% figure that the code does not implement.

  **The stale-number lesson, third instance in one day.** Every figure in the
  old version of this entry — 173/189, then 59/190, then 115/130 — was accurate
  when written and wrong within hours, because the corpus is a LIVE checkout
  that another session edits in parallel. Twice during this session
  `git status` in `../riddl-models` changed between two consecutive commands.
  **Re-measure before quoting any corpus number; never carry one forward.**
  **Cause, CORRECTED 2026-08-15 — it is no longer the alias fix.** The original
  cause was `ccd278c00`, which taught the tell-addressing check to resolve `Id`
  aliases, turning it on for the spelling riddl-models uses and surfacing 49
  ambiguity Errors it had been hiding. **Those 49 are gone** (see below), and
  what keeps these suites red now is a DIFFERENT and larger thing: Migration 2,
  the bare-message-operand tightening, which accounts for 130 of the 131 failing
  models. The 131st is `reactive-bbq`'s two unmigrated `terminate` lines.
  Keep the history because the two are easy to confuse — the alias fix is closed,
  the corpus is still red, and those facts are unrelated.
  All 49 are corpus-side, in three classes, checked against riddl-models'
  sources rather than inferred from the messages: genuine two-id ambiguity
  (`CartsMerged {targetCartId, sourceCartId}`) needing `by`; actor fields
  legitimately of the same entity (`identityId` + `suspendedBy`) also needing
  `by`; and **wrong-entity aliases** — `nursing-workflow/types.riddl:18 type
  TaskId is Id(NursingContext.NurseShift)`, `radiology-workflow/types.riddl:27
  type ReportId is Id(ImagingExam)`, `member-enrollment/types.riddl:2 type
  MemberId is Id(Enrollment)`, `policy-lifecycle/types.riddl:8,14
  BeneficiaryId`/`RiderId is Id(LifePolicy)`.
  **✅ THE 49 ARE CLEARED, AND THE LIST NEVER NEEDED TO BE DELIVERED** (verified
  2026-08-15). riddl-models fixed all of them ITSELF in `29598ad1`, "Clear all
  49 addressing errors, and correct 18 Id aliases", dated **2026-08-14** — the
  day before this entry was written demanding the list. They derived it from
  their own `riddlc` run and classified all three classes; 16 aliases became
  plain identifiers (a task is not a shift, a report is not an exam), two models
  whose only alias was the wrong one were repointed at `MachineId`/`PartId`, and
  27 genuine two-id sites gained `by <field>`.
  **Everything this entry previously said was owed is moot**: the phantom
  filename, the ~44 unwritten sites, the "regenerate before expecting them to
  act", and the RC-certification blocker. The task file in riddl-models has been
  corrected in place so it stops asking them to redo finished work.
  **Verified, not assumed** — a sweep of all 190 corpus entry points at
  `2.0.0-rc.14-120-7cb40f45` reports **zero** ambiguity Errors, and the
  instrument was PROVEN able to fire first (a positive-control model, in both
  the inline `Id(entity X)` and the `type OrderId is Id(entity X)` alias
  spellings — the alias one being what `ccd278c00` turned on and what this
  corpus uses). The five wrong-entity aliases named above are now `UUID`s.
  **The lesson worth keeping: this entry was the stale artifact.** It described
  work as outstanding for a day after it was finished, in confident detail, and
  nothing about reading it suggested otherwise — the same failure mode this file
  documents for test counts. A backlog item asserting another repo owes us
  something should be re-verified against that repo before being acted on.
  **~~A second, UNEXPLAINED regression~~ — EXPLAINED, then RESOLVED, both
  2026-08-15.** The 59/190 was never a mystery: a sweep of all 190 entry points
  found 131 models carrying an Error and **130 of them carrying exactly ONE
  error class** — 343 × *"names a message type, not a value"* plus 19 for record
  types, i.e. the bare-message-operand tightening. The 131st was `reactive-bbq`,
  which did not parse at all for two unmigrated `terminate entity X` lines.
  Nothing else appeared anywhere in the corpus. **riddl-models then landed the
  migration the same evening and the figure went to 188/190**, exactly as the
  diagnosis predicted — which is the corroboration, since the prediction was
  made before their commit existed.
  **Method note, because the first attempt got it wrong**: key the sweep's output
  files on each model's RELATIVE PATH, not its `.conf` basename — ten corpus
  models share a basename, so a basename-keyed run silently overwrites ten
  results and reports 180 files as though they were 190. Caught only by
  reconciling the file count against the exit-code count.
- ~~**Tell consumers about two BREAKING changes landed 2026-08-10.**~~
  **CLOSED 2026-08-11 by Reid: no announcement needed**, for either half.
  Recorded so it is not re-raised: (1) BAST `FORMAT_REVISION`, now **11**, so
  every `.bast` from an earlier build is rejected with a message telling the
  reader to regenerate; (2) a mapping's VALUE type is now resolved
  (`b307909b5`), so `mapping from K to Nonexistent` used to validate clean and
  now errors. The second needed no ruling on BEHAVIOUR at any point — it is a
  correct tightening — only on whether to notify, which is what is now closed.

- ~~Restage `~/Code/ossuminc/bin/riddlc`~~ — **DONE 2026-08-12**, at
  `2.0.0-rc.12-4-092ec2be`. Verified by BEHAVIOUR, not by version string: the
  two `when invariant X` reproducers that threw on rc.12 now validate clean.
  **`bin/riddlc` is the NATIVE binary, a REAL FILE** (Reid, 2026-08-12) — the
  same shape as `bin/riddlg`. It is not a symlink and not the JVM launcher, and
  `scripts/publish-and-stage.sh` now installs and verifies that exact path.
  **The arrangement this replaced is worth remembering, because it failed
  silently.** `bin/riddlc` used to be a hand-made symlink into `../riddlc-dist`,
  created a day before the script existed and invisible to git (`bin/` is
  ignored). The script wrote and verified `riddlc-dist/bin/riddlc` while its own
  header promised something about `bin/riddlc` — so the rule was enforced
  nowhere, and any change to the symlink would have left it reporting success
  over a frozen binary. `riddlc-dist/` is deleted; nothing references it.
  Restaging is still not a standalone act: the script runs `publishLocal` and
  `riddlcNative/nativeLink` in ONE sbt invocation, because the ivy artifacts and
  the CLI must never disagree about what the language accepts. Use the script;
  do not stage by hand.
- **synapify: `flattenAST` workaround can be dropped.** `Contents.definitions`
  became include- and import-transparent on 2026-08-06 (their task file, now in
  `task/done/`), so their 33 `.definitions` sites no longer need the tree
  physically flattened first. Nothing owed until they take a build containing
  it — worth folding into whatever upgrade task they get next rather than a task of
  its own, since the change is source-compatible and their current code stays
  correct. (The standing 'consumer sweep' item was removed 2026-08-12 — Reid:
  riddl-generator is on rc.12 and building its own rc.1, and the rest are not
  worth tracking here.)
- riddl-vscode: adoption task for `IncrementalValidator` — hold until the 2.0
  upgrade above, so they take one change rather than two.
- ~~ossum.tech doc debts~~ — **DROPPED as a task 2026-08-12** into
  `../ossum.tech/task/2026-08-12-riddl-2.0-doc-debts.md`, so it is tracked there
  now, not here. It carries the `ForeverEmpty`/`BottomlessPit` error-sink idiom,
  the event-sourced `on init` idiom, paths-into-Functions plus the new
  function-privacy StyleWarning, and the rc.12 language changes (`yields
  command`, the `set`/`get from state` scope rules, the reworded `do`-statements
  warning, `FORMAT_REVISION` 12, and the two previously-silent breaking changes).
  **One item was CANCELLED as stale, verified not recalled:** the
  `/riddl/2.0/licenses/` 404 no longer exists — `riddlc info` prints
  `github.com/ossuminc/riddl` and `opensource.org/license/apache-2-0`, neither of
  which is an ossum.tech URL.
