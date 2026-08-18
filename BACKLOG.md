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

**ITEM IDs — STABLE, never reassigned (corrected 2026-08-17).** Every open item
carries a `[section.n]` identifier so it can be named in conversation without
quoting its title.

**An ID belongs to an item for life.** When an item closes its number RETIRES with
it and is never reused, so gaps in the sequence are expected and correct.

They were positional and renumbered on every close for one day, which was a
mistake: it silently moved items under the person reading them, and it did — the
lookup-value item was 2.5 in one message and read as 2.6 in the next, in a
conversation where both of us were naming items by number. **A handle that changes
is worse than no handle**, because it fails exactly when it is being relied on.

### 0. Just before 2.0.0 is released

Things deliberately deferred to the release itself, not to be done piecemeal.

- **[0.1]** **Run one `scalafmt` pass.** Formatting is not a gate before 2.0 (Reid,
  2026-08-04); do not run `scalafmtCheckAll`, report it, or format
  incrementally. `sbt scalafmtCheck` is red on HEAD — 7 committed files
  reformat, 6 in `commands`.
- **[0.2]** **Upgrade riddl-vscode.** Reid, 2026-08-06 — deferred here deliberately, not
  overlooked. It consumes `@ossuminc/riddl-lib` via npm, which carries only
  PUBLISHED releases, so it cannot take a staged build at all and chasing it
  between RCs means cutting an RC for its benefit. It is on `2.0.0-rc.9`
  (`package.json:128`); bring it to 2.0.0 when 2.0.0 exists.
- **[0.3]** **Regenerate every checked-in `.bast`.** Reid, 2026-08-06 — same reasoning:
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
- **[0.4]** **Update `../RIDDL-Computational-Model.md` with everything `release/2`
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
- **[0.5]** **Update the ossum.tech documentation site** with the same syntax changes,
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

- ~~**`JsonModel`'s reader never rejects unknown/misspelled keys.**~~ — **DONE
  2026-08-16, `927898a97`, as a WARNING** per Reid's ruling. Validated on the raw
  `ujson` tree BEFORE any reader runs, which is the only place both reader layers
  are visible at once: this entry's proposed consumed-keys tracker would have
  covered the 6 hand-written readers and never the 59 macro-derived ones, where
  upickle drops unknown keys internally with no hook of ours.
  **Two guards, and the second is not redundant.**
  `JsonUnknownKeyVocabularyTest` re-derives the vocabulary from `JsonModel.scala`'s
  own source, so the list cannot drift from the readers.
  `JsonKeyFalsePositiveTest` runs every corpus model through the writer and back.
  **It caught two defects the source-derived guard could not see, either of which
  would have fired on EVERY correct document:** the writer emits sigil keys
  (`$kind`, `$at`) that no DTO field or reader lookup spells (188/188 models
  warned), and a `Schema`'s `data`/`links` are maps keyed by the MODELLER's
  identifiers (184/188).
  **The second is the lesson.** I had asserted in a comment that no object in the
  schema is keyed by data, having grepped the READERS for key iteration and found
  none — nothing appears there because upickle's derived `Map[String, _]` reader
  does the iterating. Only running the writer's output back through the check
  exposed it. **A diagnostic that fires on correct input is worse than none**, so
  both checks are permanent tests rather than things verified once.
  Corpus: 0 unrecognized keys across 188 models.

- ~~**[1.1]** **STRATEGIC: should the JSON input surface exist at all?**~~ — **RULED
  2026-08-17 by Reid: option C — keep it for hosted models, point self-hosted
  users at GBNF/XGrammar, and DOCUMENT the split.** Done in `61d028e4e`: the
  rationale now sits at the top of `JsonModel`, where anyone extending 131 DTOs
  will read it — the deciding fact, the price, and what JSON does not buy. **The
  documentation WAS the deliverable**: the surface was drifting without a stated
  justification, which is what made the question live. Original analysis kept
  below.
  Not urgent — the warning above protects today's users either way — but it
  outlives that fix and should be decided rather than drift.
  **The argument for retiring it.** JSON guarantees SHAPE only. Our own path runs
  the identical validation passes afterwards, so "correct-by-construction" covers
  structure and never meaning, and an AI must learn RIDDL's semantics regardless.
  The price is a second serialization surface — **131 DTOs tracking the AST** —
  which is what produced the unknown-key defect class in the first place.
  **What the alternatives actually are** (researched 2026-08-16):
  - **Hosted frontier models (Claude, GPT, Gemini) expose JSON Schema and tool
    schemas only.** There is no logit-level hook for an arbitrary CFG, so for them
    JSON is the ONLY constrained channel. This is the fact that decides it.
  - **Self-hosted:** GBNF already exists here (`riddl-grammar.gbnf`, generated
    from the EBNF, CI-checked against drift), and **XGrammar** is the modern retry
    if GBNF's collapse on our 263-rule grammar was a performance problem.
    Outlines / lm-format-enforcer are equivalents.
  - **Generate RIDDL then repair**, using riddlc's diagnostics and `provideTips`.
    No constrained decoding, but far more viable than when JSON was chosen.
  **Options:** keep JSON; retire it in favour of repair loops; or keep it only for
  hosted models and point self-hosted users at GBNF/XGrammar.

- ~~**`Punctuation.tokenPunctuation` does not include `!`.**~~ — **DONE
  2026-08-15, `66388821c`.** The damage was worse than filed: not `!isValid` but
  `!isValid then do "no" end` — the whole remainder of the input — became one
  `Token.Other`, so an editor lost highlighting for everything after the `!`.
  `!` is guarded by a negative lookahead rather than listed in the `StringIn`,
  mirroring the parser's `"!" ~~ !"="`: `!=` is a comparison OPERATOR and this set
  holds no comparison operators at all. No EBNF change — `not_expression` already
  described it; the tokenizer is a highlighting surface, which is why the parser
  accepted `!` all along while the tokenizer did not.

- ~~**`OnInitializationClause.parameters` / `OnTerminationClause.parameters` have
  no default.**~~ — **DONE 2026-08-15, `c530337d9`.** Defaulted IN PLACE.
  **This entry's prescribed fix would have caused a second break:** it said to move
  the field after `contents`/`metadata` because `@JSExportTopLevel` wants defaulted
  params trailing, but that constraint bites only while the field has NO default —
  once it has one, `loc` is the sole undefaulted parameter and the rule is already
  satisfied where it stands. Moving it would have broken all five positional
  construction sites (`HandlerParser` x2, `BASTReader`, `JsonAstBuilder` x2).
  Verified on cJS and cNative, which is the only place that hazard is visible.

- ~~**[1.2]** **`emittedMessageTypes` is still narrow.**~~ — **DONE 2026-08-17.**
  Closed by looking the answer up in `ValidationOutput.deliverableTypes` ([4.3])
  rather than re-resolving. **The entry's size estimate was wrong**: it judged
  the fix to be "walking the root container-by-container the way
  `checkStatementScopes` already does", and `checkStatementScopes` already did
  that walk — the obstacle had dissolved without anyone noticing. Second time
  today ([2.6] was the first). Superseded: The second of the two gaps, and the
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

- ~~**[1.3]** **Close the JVM/Native test gap.**~~ — **CLOSED 2026-08-18 by Reid:
  *"find the easy/obvious ones... then declare victory — close enough: we are in
  the thousands of common tests."*** Final: **JVM 2747 / Native 2708, gap −39**,
  from −729 originally. `commands`, `riddlLib` and `riddlc` are at parity.
  **The last big win was a BUILD omission, not a platform constraint**: riddlLib
  was the one cross-platform module with no `scala-jvm-native` test wiring, so
  every suite it had was JVM-only by accident. Two lines of `build.sbt`.
  **What remains is deliberately JVM-bound** and should stay: benchmarks (timing),
  `Tar`/`FigmaClient` (no Native impl), `LoaderTest` (names `JVMPlatformContext`),
  `LoadingURLTests` (real behavioural difference in URL error handling — it
  COMPILES on Native and fails, which is worth knowing), and two suites still on
  commons-io. **Do not reopen this to chase the last 39.** Superseded detail:
- **~~[1.3] history~~: Close the JVM/Native test gap: 729 cases run on JVM that never run on
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

  **RE-MEASURED 2026-08-17 at the `2.0.0-rc.15` certification (clean, tri-platform,
  `testOnly *`), and the picture is UNCHANGED — which is the useful finding:**

  | module | JVM | Native | gap |
  |---|---|---|---|
  | commands | 245 | 52 | **−193** |
  | passes | 1446 | 1403 | −43 |
  | language | 731 | 714 | −17 |
  | utils | 148 | 134 | −14 |
  | riddlLib | 144 | 131 | −13 |
  | testkit | 2 | 1 | −1 |
  | riddlc | 21 | 21 | 0 |
  | **total** | **2737** | **2456** | **−281** |

  The gap moved −275 → −281 only because new tests landed JVM-side; **no module
  regressed and none improved**. So the 2026-08-15 conclusion holds exactly:
  subtract `commands` and the repo is within ~88 cases of parity, and `commands`
  is blocked on a decision rather than on effort. Re-measuring cost one grep of a
  certification log that had to be produced anyway — worth doing at every RC, so
  the entry can never drift as far as the ~800 estimate it opened with.

  **THE `commands` BLOCKER IS NOW IDENTIFIED PRECISELY (2026-08-18), by experiment
  rather than by reading.** Three suites there — `RunCommandOnExamplesTest` and its
  two subclasses — cannot run on Native, and the reason is NOT what the entry
  assumed. Moving them to `scala-jvm-native` and compiling reveals a chain:

  1. **`org.apache.commons.io`** (a JVM-only Java library) blocks COMPILATION.
     Removable: all three uses (`iterateFiles`, `iterateFilesAndDirs`,
     `forceDeleteOnExit`) map cleanly onto `java.nio.file`, which Native's javalib
     does support. Verified — the rewrite compiles for Native and stays green on
     JVM at 245.
  2. **`java.net.URL.getFile` is `???` in Native's javalib** — compiles, then
     throws `scala.NotImplementedError` at run time. Also removable: it is used
     only to derive a filename, recoverable from `toExternalForm`.
  3. **`java.net.URL.openStream` is `???` too — and this one is the wall.** These
     suites DOWNLOAD the riddl-examples archive over HTTP in `beforeAll`. That is
     network I/O; no string trick substitutes for it.

  **So the "shared-corpus decision" this entry gestured at is a concrete
  question:** should these suites keep downloading a pinned archive, or read the
  sibling `../riddl-examples` checkout the way `RiddlModelsRoundTripTest` already
  reads `../riddl-models`? Downloading is hermetic and Native-impossible; reading
  a checkout is Native-possible and makes the tests depend on the working copy.
  **That is Reid's call, and it is the whole of the remaining gap** — roughly 193
  of the 281 cases.

  **RESOLVED 2026-08-18 for `commands` — the gap there is ZERO** (`1c318b844`).
  Reid ruled the design: read the already-checked-out `../riddl-models` and
  `../riddl-examples`, SKIP when absent rather than fail, and let CI clone them so
  the runner has them locally. **That removes the download rather than repairing
  it**, which is what three earlier attempts had been trying to do.

  | module | JVM | Native | gap |
  |---|---|---|---|
  | commands | 245 | 245 | **0** |
  | passes | 1456 | 1413 | −43 |
  | language | 731 | 714 | −17 |
  | utils | 148 | 134 | −14 |
  | riddlLib | 144 | 131 | −13 |
  | testkit | 2 | 1 | −1 |
  | riddlc | 21 | 21 | 0 |
  | **total** | **2745** | **2659** | **−86** |

  **−281 → −86**, and `RiddlModelsRoundTripTest`'s 189 cases — the single largest
  block — now run on both platforms. What remains is the ~86 spread thinly across
  five modules, with no single blocker.
  **The next RC certification should RAISE the Native floor substantially** (2456 →
  ~2659); these numbers are from `testOnly *`, which ignores incremental state, but
  the floor may only be raised by a certified clean tri-platform run.

  **The skip is LOUD by design.** CLAUDE.md records that a cancelled corpus suite
  "reads as green in a summary scan"; the message names the absolute path searched
  and the branch expected. The CI step carries the same warning, because the
  failure mode of this design is SILENCE — a clone that fails leaves the suites
  skipping and the log green.

  **Still open and worth knowing, since `loadBytes` is now public API:** the Native
  fetch returns a SHORT body for a binary URL. Redirect-following did not fix it.
  **Leading untested theory: sttp's Native backend truncates at the first NUL
  byte**, treating the body as a C string — a ZIP's header contains NULs within
  the first few bytes, which fits "too short to be Zip" exactly and explains why
  the redirect fix changed nothing. Test it by fetching a known-length binary and
  asserting the byte count. Nothing in the build depends on it now.

  **Superseded — steps 1-3 were fixed** in `e4f91525c`, on
  Reid's steer to use sttp (already a dependency) or write the code, and with his
  permission to extend `PlatformContext` provided all three platforms implement it.

  - **`PlatformContext.loadBytes(url): Future[Array[Byte]]`** is new — the binary
    counterpart of `load`, which returns a `String` and therefore corrupts a ZIP.
    JVM uses `openStream`; Native uses sttp (the same stack `load` already uses
    for text); Scala.js uses `dom.fetch` with `arrayBuffer()`.
  - `PathUtils.copyURLToDir` no longer touches either stub.
  - commons-io is gone from `RunCommandOnExamplesTest`.

  **A FOURTH link is what remains, and it was invisible until the first three
  were cleared**: the download arrives SHORT on Native, so unzip reports
  `java.util.zip.ZipException: too short to be Zip`. Note what that error proves —
  **`java.util.zip` WORKS on Native**, or it could not produce a real ZipException.
  So this is one bug in the Native fetch, not another missing platform capability.
  Verified with curl that the archive URL 302-redirects (0 bytes without `-L`,
  207,955 with), and explicit redirect-following was added to the Native fetch
  without resolving it — so the cause is elsewhere in that fetch, and that is
  exactly where the next attempt starts.

  **The three test files stay in `scalajvm`** until item 4 is fixed: moving them
  leaves two suites ABORTING on Native, which is worse than not running them.
  Moving them back is a one-line `git mv` each once the fetch is right.

  **Nearly all of the remaining gap is `commands`, and nearly all of THAT is
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
     may run those rows concurrently.
     **DECIDED 2026-08-16 by Reid: it STAYS JVM-ONLY. This is a decision, not an
     omission.** The suite's value is CORPUS coverage, which does not vary by
     platform, and BAST's platform behaviour is separately covered by the 26 BAST
     suites moved to Native in `6cf60baf2`. Copying the corpus per platform, or
     serialising the two rows, would buy a number rather than a fact. **Do not
     "close" this by porting it.** Excluding it, the whole repo sits about 86
     cases from tri-platform parity, and that residue is the part worth chasing.
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

- ~~**[1.4]** **How many corpus `tell`s cross a context boundary?**~~ — **RETIRED
  2026-08-18 by Reid (option B).** Not answered; retired. The measurement existed
  to size the blast radius before the cross-context isolation seam became an
  Error, and **that check shipped anyway** (`3059a43f8`), so the decision it was
  meant to inform is already made. Reid's original framing was *"I don't really
  care"*, and nothing has since made it matter.
  **The one durable fact is kept below, because it is a trap, not a task**: a
  grep CANNOT answer this. A dotted path means the author QUALIFIED the target,
  not that it crosses a boundary, and comparing the first segment to the nearest
  enclosing `context` is unsound because sagas and adaptors sit at DOMAIN level.
  That approach reported 7,393 of 7,396 as crossing, which is not credible.
  Anyone tempted to re-measure by text should read that and stop. Original:
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

- ~~**Cross-context `tell` isolation seam.**~~ — **DONE 2026-08-16, `3059a43f8`.**
  Shipped straight as an **Error**, skipping this repo's warn-then-flip, because
  the census removed the reason for it: **18 crossings in 7,537 tells (0.24%)**,
  not the 5,301 (64%) a text heuristic had claimed — a dotted path means the
  author QUALIFIED the target, not that it crosses anything.
  **The real migration is smaller still: 8 Errors, in ONE model.** Ten of the 18
  are already adaptor-mediated — all 4 in `ticket-sales` sit inside `adaptor
  MarketingAdapter`, several in reactive-bbq inside `adaptor ToLoyalty`/`ToBar` —
  verified by reading those sources rather than inferred from the drop. Migration
  task with the 8 file:line pairs and the modelling choice at each is at
  `../riddl-models/task/2026-08-16-cross-context-tell-is-now-an-error.md`.
  **Scope, both settled on evidence:** `send` is NOT covered (its target is a
  `PortletRef`, so it cannot name a foreign processor at all; a message crossing
  by `send` goes through a CONNECTOR, the streaming counterpart of an adaptor),
  and an **Adaptor is exempt**, since A4 makes it the sanctioned place to name
  another context's messages.
  **The bug worth remembering: the exemption must test the IMMEDIATE parent.**
  `parentsOf` returns every ancestor, so a type declared inside the target's own
  context still lists the shared domain among them — as does everything in the
  tree — and the exemption swallowed the whole rule until it asked `parentOf`.
  Recorded in CM §3.6. `RiddlModelsRoundTripTest` goes 187/2 → **186/3** until
  riddl-models migrates; under the 100%-corpus gate that blocks a release.

- ~~**Clusterability: `clustered`, and `self.isClustered`.**~~ — **DONE
  2026-08-16.** Reid chose the keyword and DECLINED the `self` field, resolving
  the contradiction this item was filed with: writing `clustered` in the model is
  exactly what makes clustering statically knowable, which is what the `self`
  admission test excludes. `SelfValue.fieldNames` stays closed at `id`/`version`.
  Shipped as an advisory **`option clustered`**, not a grammar intention, on the
  test every intention has been judged by — *may a generator decline to honour
  it?* Here it may: deploying one instance is a legitimate realization. Contrast
  `event-sourced`, where declining changes what the model MEANS.
  Scoped to the SINGLETON processors (Context, Projector, Repository, Adaptor and
  the seven streamlet shapes, spelled out because a Streamlet's parent kind is
  its shape's simple name). **Not an Entity** — already distributed by identity,
  so it would state nothing; the misplacement is a StyleWarning rather than an
  Error because it asserts nothing false, unlike `persistent` on a stateless
  definition. Verified end to end against the staged riddlc: silent on a Context
  and a Projector, and on an Entity it reports *"Option 'clustered' is not
  typically used on Entity definitions (expected: Context, Projector, …)"*.
  The one HARD rule nearby deliberately needs no keyword: a correlating projector
  must distribute by key rather than round-robin, which follows from declaring
  correlations. Recorded in CM §39.1.

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

- ~~**Computational Model amendments owed by the identity design.**~~ — **DONE
  2026-08-16, `18bdb8f` in the `ossuminc` repo.** All three: §4.5 now reads
  "rehydrate an already-existing instance" with a paragraph on why activation is
  never creation; §3.6 gains the memory-space axiom in its POSITIVE form (only
  processors within one context are guaranteed to share memory, which is what
  licenses optimizing a same-context `tell`); and §38.9 states, as a table, that a
  definition's ULID is not `Id(P)` — the failure modes are symmetric and both
  silent.

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

- ~~**[2.1]** **A42 (iii) — Figma bidirectional scaffolding.**~~ — **RULED 2026-08-17
  by Reid, and the ruling is NEITHER option I offered.** Scaffolding is DROPPED
  entirely: *"Just allow the Figma metadata URL on RIDDL UI elements and don't
  worry about the mapping or framing or correctness. It is only to keep an
  association. Allow it in an application intended context too so an entire
  application design can be referenced at that level."*
  **NO CODE WAS NEEDED — the ruling was already implemented.**
  `DefinitionValidation.mayCarryFigmaRef` already admits exactly `Input`, `Output`,
  `Group` and a `Context` whose intention is `Application`, and `FigmaRefTest`
  already pins both halves ("be accepted on input, output, group and an
  application-intended context" / "be rejected on a context that is not
  application-intended"). Verified rather than assumed.
  **Drift validation (part ii) is KEPT.** It is opt-in behind
  `--check-figma-drift`, off by default, so it does not contradict "only to keep an
  association" — the association is what you get by default and verification is
  there for whoever wants it. Removing shipped, working, opt-in functionality would
  have been over-reading the ruling; say so if it was meant.
  The (a)/(b) analysis below is retained ONLY because it records a platform fact
  worth not rediscovering: **Figma's REST API cannot create frames.** Superseded: Parts (i) and (ii) shipped: the
  `shown by figma "fileKey" node "1:23"` reference form with placement enforced by
  `mayCarryFigmaRef`, and drift validation through `FigmaClient`, four-valued and
  off by default behind `--check-figma-drift`.

  **⚠ THE FIRST QUESTION IS WHETHER THIS BELONGS IN THIS REPO.** A42's own text
  says part (iii) *"is generator work and pairs with Part B item 4"* — and Part B
  is **riddlg**, whose item 4 is the UI application generator ("structure and
  wiring come from RIDDL, the visual skin from Figma design tokens, with
  bidirectional scaffolding between them"). Filing it in riddl's backlog was
  probably a filing error. **Recommendation: move the bulk to riddlg and keep in
  riddl only what riddlg cannot do for itself.**

  **The two halves are NOT symmetric in difficulty, and this is the substance.**

  **(a) RIDDL → Figma (generate wireframe skeletons from the group tree).**
  Blocked on a platform constraint, not on modelling. **Figma's REST API cannot
  create frames** — node creation lives in the Plugin API, which runs *inside*
  Figma, so `FigmaClient` (one method, `lookupNode`, read-only) cannot be extended
  into this. The real options are: ship a small Figma **plugin** that consumes a
  RIDDL-derived spec; emit a format Figma **imports**; or use whatever write
  surface REST now exposes. **Confirm against current Figma docs before planning
  further — this constraint is the whole shape of (a), and their API moves.**

  **(b) Figma → RIDDL (draft RIDDL from a Figma file).** Tractable today with the
  READ surface that already exists: walk a file's frames and emit `group` /
  `input` / `output` skeletons, reusing part (ii)'s name-normalisation (bare
  word-characters, so "Login Screen" ↔ `LoginScreen`). **Do (b) first** — it is
  useful alone, needs no new platform capability, and exercises the mapping in the
  direction where a wrong guess costs a draft rather than a design file.

  **What riddl owes either way**, and the only part that is clearly ours: a stable
  view of the UI structure for a generator to walk (the group/input/output tree is
  already reachable via the content accessors and `TreePass`), `FigmaRef` in the
  AST (shipped), and `FigmaClient` if a write surface ever lands here rather than
  in riddlg.

  **Carry part (ii)'s two rulings into anything built here**, since they were
  learned the expensive way: a lookup result must distinguish *not found* from
  *could not ask* (`Unavailable` is not drift), and **a build must never fail
  because of the network** — off by default, never fatal.

- ~~**[2.2]** **A38 — ADMIT an invariant reference as an ALTERNATIVE refusal operand.**~~
  — **DONE 2026-08-17, `e4f6f33f3`.** Additive exactly as the corrected framing
  said: prose stays valid and unwarned. Full reflective surface (parser, EBNF,
  regenerated GBNF, prettify, BAST, JSON), rode `FORMAT_REVISION` 18 as reserved
  — **18 is now fully spent; the next BAST change bumps to 19.** CM §29 records
  it (`ossuminc` `adfce7c`), including that the taxonomy there had never listed
  `refusal` at all.
  **Three lessons worth keeping.** (1) The corrected framing was right that no
  corpus survey was needed, and the reason generalizes: removing a form needs a
  survey, adding one does not. (2) Keeping the two forms DISTINCT is what drove
  every design choice — a BAST discriminator, two separate JSON keys, and a test
  that prose spelling a path stays prose. One key holding both would have left
  every reader guessing. (3) **The fixture found a defect the feature had nothing
  to do with** — see the next entry.

- ~~**JSON dropped EVERY interaction's metadata.**~~ — **DONE 2026-08-17, in
  `e4f6f33f3`.** `JsonAstBuilder.buildInteraction` hardcoded
  `Contents.empty[MetaData]()`, so `step … with { briefly "…" }` came back
  without the brief, for all thirteen interaction kinds. **745 affected keys in
  the corpus**, so this recovered real data. Undetected because no fixture in the
  repo had ever put metadata on an interaction step; A38's fixture is the first,
  and `Root2JsonFixturesTest` caught it the moment it existed. Fixed on the
  `InteractionContentDto` WRAPPER, so one change covers all thirteen kinds.
  **STILL OPEN, and named rather than papered over: metadata on a NESTED step**
  (inside `sequential`/`parallel`/`optional`) is still dropped — those hold a
  bare `Seq[InteractionDto]` with no wrapper to carry it, so closing it is a
  schema change, not a wiring fix. Filed as **[1.5]** below.

- ~~**[1.5]** **JSON drops metadata on a NESTED interaction step.**~~ — **DONE
  2026-08-17, `61d028e4e`**, unblocked by [1.1]'s ruling. Composites now hold
  `InteractionContentDto`, but **the JSON stays FLAT** — a nested step keeps its
  own object with `brief`/`metadata` as optional keys beside `kind`, rather than
  gaining a `{"interaction": {…}}` wrapper. So the schema gains keys, not a
  nesting level, and an old-shape reader is unaffected. The fixture that measured
  it is back in permanently and `Root2JsonFixturesTest` reports lossy=0.
  Original analysis: A step inside a
  `sequential`/`parallel`/`optional` composite is serialized as a bare
  `InteractionDto`, with no `InteractionContentDto` wrapper to carry `brief`/
  `metadata` — so `sequence { step … with { briefly "x" } }` loses the brief on
  every round trip, while the same step at use-case top level now survives.
  Closing it means the composites' `interactions` field becoming a `Seq` of
  wrappers, which changes the document schema. Marked at
  `JsonAstBuilder.nestedInteraction`, which exists to make the gap visible at the
  code rather than only here.

  **MEASURED 2026-08-17, not reasoned** — the entry was first written from
  reading the code, and this repo's own rule is that a claim about behaviour
  needs a run. Add a `with { briefly "…" }` to a step inside a `sequence` in
  `language/input/refusal-reason.riddl` and run
  `riddlLib/testOnly *Root2JsonFixturesTest*`: it reports
  **`BriefDescription: 2 -> 1`** — the top-level step's brief survives, the
  nested one does not. That is a two-minute reproduction for whoever takes this.
  The fixture change was REVERTED rather than left in place, because it turns two
  suites red and a permanently-red gate stops being read.

  **DELIBERATELY NOT FIXED in the same session that found it**, and the reason is
  [1.1]: Reid asked on 2026-08-16 whether the JSON INPUT surface should exist at
  all. Closing this means changing that surface's document schema — nested steps
  going from `{"kind": …}` to `{"interaction": {"kind": …}, "brief": …}` — which
  is a compatibility event for every consumer reading it. **Settle [1.1] first;
  if the input surface goes away, this fix is free (write-side only), and if it
  stays, the schema change wants announcing rather than discovering.**

- **~~[2.2] superseded framing~~ (kept only so the reasoning is not re-derived).**
  **CORRECTED 2026-08-16 by Reid, and the previous framing was wrong.** This
  entry said the operand "should name an invariant, NOT prose", and offered as a
  fallback "admit both and warn on the prose form". Both are incorrect, for the
  same reason: **RIDDL has two legitimate refusal mechanisms, and only one of
  them has an invariant to name.** A handler refuses either with
  `require invariant X` or with `error "<prose>"` — the validator treats them as
  equivalent discharges in two independent places
  (`ValidationPass.scala:678` and `:1729`, both matching
  `case _: ErrorStatement | _: RequireStatement`).
  So narrowing the step's operand to an `invariant_ref` would make an
  error-based refusal **undocumentable**, and warning on the prose form would fire
  on models that are correct — the false-positive failure mode this repo has now
  hit twice in two days.
  **The work that remains is purely ADDITIVE:** allow
  `any_interaction_ref "refuses" user_ref (literal_string | invariant_ref)`.
  Prose stays valid, unwarned, and is the honest spelling when the refusal is an
  `error`. A38's goal — closing the loop to the `require` and the
  `InvariantViolated` a generated test asserts — is then met exactly where an
  invariant exists to close it to, and claims nothing where one does not.
  Touches parser + EBNF + GBNF + prettify + BAST + JSON, so it needs a
  `FORMAT_REVISION` bump. **No corpus survey is needed to REMOVE the string form,
  because the string form is not being removed** — which is most of what made the
  original framing expensive.

- ~~**RULED 2026-08-14 — `on other` is necessary to the LANGUAGE, not required in
  every handler.**~~ — **REMOVED from the backlog 2026-08-16 (Reid: "if there's
  nothing to build, why is it on this list?"). Nothing to build; the ruling is
  already implemented and documented.** A5's generalization is DECLINED and
  omission is correct; the check says so at its own site, and CLAUDE.md carries
  the reasoning. Struck rather than deleted only so the next reader does not
  re-derive the question — a backlog is for OPEN WORK, and a recorded decision
  with no task attached belongs in CLAUDE.md, which has it.

- ~~**[2.3]** **Audit the remaining catch-all matches.**~~ — **CLOSED 2026-08-18 by
  Reid**, who asked whether exhaustiveness carries significant correctness value.
  **My answer: no, and the audit's own record is the evidence.** Across three
  slices, the hit rate was **5 real defects in ~25 sites read**, and every one of
  the five was found by REASONING ABOUT A SYMPTOM rather than by sweeping — the
  BASTReader direction default, the two JsonifierPass wrong answers,
  `PrettifyVisitor.keyword`, and [2.6]'s resolution seam. Meanwhile the two most
  expensive defects of the whole week (`typeDeps` empty forever, MessageFlowPass
  dropping edges) were **not `case _ =>` arms at all**, so a complete sweep of
  this shape would have missed both.
  **The rule stands and is documented in CLAUDE.md**; what is retired is the
  ambition to enumerate every site. Fix the shape when a symptom points at it.
  Superseded detail:
- **~~[2.3] history~~: Audit the remaining catch-all matches against Reid's no-silent-fallthrough
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

  **AUDIT PROGRESS 2026-08-16.** Sized: **198** `case _ =>` sites across
  `passes/src/main` + `language/src/main`, 94 of them in `ValidationPass` alone.
  A classifier that flags "catch-all whose siblings name a growable union" marked
  182 of the 198 — **useless, and worth recording as a dead end**: it counts typed
  arms (`case _: Foo =>`, which are not catch-alls at all) and any sibling type
  name. **Classification has to be by what the arm MEANS, which is reading, not
  grepping** — which is what this entry has said from the start.
  **FIXED:** `PrettifyVisitor.keyword`'s `case _: Definition => "unknown"`, the
  one CLAUDE.md already flagged. It emitted `unknown Foo is { … }` — text that
  does not parse — silently, from the one pass whose whole contract is that its
  output re-parses. Now throws, naming the kind and the id. Unreachable today,
  demonstrated rather than assumed: `passes` 1404 green and the corpus round trip
  unchanged at 186/3, with no `IllegalStateException`.
  **EXAMINED AND LEGITIMATE — do not "fix" these:** `AST.scala:2898`
  (`Type.kind`'s `case _ => "Type"` — a plain `type X is String` genuinely IS kind
  "Type") and `AST.scala:4735` (`Declaration.ascription`'s `case _ => ""` — a type
  with no `yields` genuinely has no ascription). Both are the *"nothing to do
  here"* class, which the rule explicitly permits.
  **AUDIT PROGRESS 2026-08-17, `7296cfc27`.** The output-producing slice named
  below is now WORKED, and it was small: `grep -rn --include='*.scala'
  'case _ *=> *"'` over `passes`/`language`/`riddlLib`/`commands` returns **10
  sites**, not hundreds, so this slice cost an hour rather than the day the
  198-site figure suggests. **Sizing a slice by grep is cheap even though
  CLASSIFYING one is not.**
  **FIXED (3):** `BASTReader.readAdaptorNode` defaulted an unrecognized direction
  tag to `InboundAdaptor` — the worst answer available, since direction decides
  which side of a bridge produces and which consumes; now throws, matching the
  rest of that reader. `JsonifierPass`'s adaptor `case _ => "outbound"` and
  `aggregateFlavour`'s `case _ => "aggregation"` are enumerated, so a third
  `AdaptorDirection` or `AggregateTypeExpression` is a compile error (-Werror is
  live in riddlLib) rather than a silent reversal or a lost keyword.
  **EXAMINED AND LEGITIMATE — do not "fix" these:** `TypeParser.literalKindFor`'s
  `case _ => "a numeric literal"` (its only caller has already established the
  type is numeric-like; the `Bool` arm above it is the exception) and
  `cardinality`'s `prefixStr` `case _ => ""` (**unreachable**: the three
  suffix-only combinations are matched by earlier arms and `.!.?` can only yield
  "many"/"optional"). Plus the two already cleared: `AST.scala:2907`, `:4748`.
  **That is the whole output-producing slice.** `RiddlAPI.scala:156` carries its
  own justification (Comment/Include have no identifier).

  **AUDIT PROGRESS 2026-08-18 — the resolution-position slice, sized and started.**
  `grep -rn -E "case _ *=> *(None|Seq\.empty|Nil)"` over `passes`/`language`/
  `riddlLib` returns **80 sites**, concentrated in `ValidationPass` (35),
  `UseCaseWitnessPass` (9), `JsonifierPass` (7) and `AST.scala` (7).
  **EXAMINED AND CLEARED, with a test rather than an opinion:**
  `ResolutionPass.valueScopeField`'s `aggFields` (`:724`) — it reads a Type's
  fields and answers `Seq.empty` for anything that is not an
  `AggregateTypeExpression`, which does NOT see through an alias. That made it a
  strong suspect, since `isAddressFieldFor` had exactly this defect and was taught
  to follow alias chains in `ccd278c00` because aliasing is riddl-models' house
  style. **It is not a defect**: an aliased state record resolves exactly as a
  direct one, because `valueScopeField` is not the only route — the A55 `ValueRef`
  walk reaches the field anyway. `AliasedValueScopeTest` pins both forms, so a
  future change that made `valueScopeField` the sole route reddens here instead of
  reddening riddl-models.
  **Method note worth keeping: the suspicion was well-founded and still wrong.**
  Reasoning from "this shape was a defect over there" got the site onto the list;
  only running it settled the question. That is the same lesson as [2.6]'s
  unreachable arm, in the opposite direction.

  **The remaining ~78 are unexamined.** Continue with the same
  slice: `case _ => None` and `case _ => Seq.empty` in RESOLUTION positions,
  where an empty answer is read downstream as "no such thing" — the shape that
  produced both of this week's found defects (`DependencyAnalysisPass.typeDeps`
  empty forever, `MessageFlowPass` dropping let-local edges). Neither of those
  was a `case _ =>` arm at all, which is the caution: **this item's grep-shaped
  framing cannot find the defects its own examples are made of.**

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

- ~~**[2.4]** **Finish the `Streamlet` → `Processor` migration in the remaining
  passes.**~~ — **DONE 2026-08-17, `2c19d6d70`.** The public-API question is
  answered the additive way: `AnalysisResult.streamlets` and
  `DataFlowDiagramData.streamlets` keep their exact meaning and type, and
  `processors` / `portBearing` are added alongside. Every accessor in that family
  is named after a KIND, so widening the one would have made it the only one
  whose name does not say what it returns. **See [4.1] — Reid has not confirmed
  this reading.**
  **Both behavioural defects were WRONG ANSWERS, not dropped work**, which the
  entry did not anticipate: MessageFlowPass's grandparent fallback SUCCEEDED on
  the enclosing Context (a Context is a Processor too), so a flow from an
  entity's own outlet named the container as producer; DiagramsPass fell back to
  the PORT, drawing arrows from an outlet to an inlet with neither owner shown.
  Demonstrated by reverting each helper, not assumed.
  **A third defect rode along that no Streamlet-narrowing sweep would find**:
  `makeProcessorRelationships`'s Streamlet arm called `makeInletRelationships`
  and then `makeOutletRelationships`, and a block's value is its LAST expression
  — the inlet relationships were computed and discarded. Same family as the
  `.sortWith(…).toMap` discard recorded above.
  **`StatsPass` was examined and is NOT part of this**: all three of its
  `Streamlet` sites are arms of a total enumeration over the six kinds. Its
  "+1 for shape" counting only Streamlets is a genuine question — see [4.4].

- ~~**A lookup value: `<mapping|array> at <index>`.**~~ — **DONE 2026-08-17,
  `9ec30c5b5` (parser) + `977813b58` (the rest).** Mapping by key, Sequence by
  ordinal, Table by one ordinal per dimension; `Set`/`Graph` rejected; literal
  indices type-checked against the collection's key type. CM §3.6 records it.
  **The blocker was a parser CUT, not the syntax.** `Keywords.keyword` ends in
  `./` (`Keywords.scala:39`), so matching the word `at` COMMITS the parser. A
  lookup is reachable from both `comparand` (a comparison operand) and
  `booleanAtom` (a bare value); with the cut, whichever route ran first poisoned
  the other, because only the bare case has to backtrack out of `comparison`'s
  first alternative. `when inv at "sku" > 0` worked while `let n = inv at "sku"`
  failed, moving the rule between the two only traded one failure for the other,
  and removing my own `~/` after `at` changed nothing — **the cut was never mine.**
  Fixed with `NoCut` around the optional clause, plus ONE rule that parses a ref
  and optionally extends it rather than two alternatives, since anything that
  makes the parser CHOOSE must backtrack across that keyword.
  **`-Werror` found 3 of the ~13 sites**; the rest were behind catch-alls. Two
  guards caught real omissions: BASTWriter's total dispatch threw the moment a
  lookup reached it, and the JSON vocabulary guard caught `collection` missing
  from `knownKeys` — without which the unknown-key warning would have fired on
  every document containing a lookup.

- ~~**[2.6]** **BUILD: an imported definition must RESOLVE without an explicit
  flatten.**~~ — **DONE 2026-08-17, `f99bd3d27`.** Two arms of
  `findMatchingCandidate` switched from `directDefinitions` to `definitions`. The
  obstacle this entry recorded — that `filterThroughWrappers` cannot express
  "includes but not imports" — DISSOLVED with the ruling, since the two wrappers
  are now meant to be treated alike, which is why it was two words rather than a
  redesign. **Both arms are instances of [2.3]'s named next slice** (an empty
  answer in a resolution position); this is the third example and the first found
  by deliberately looking for the shape.
  **The method mattered more than the fix.** My first attempt patched a
  plausible-looking `case _ => Seq.empty` in `candidatesFromContents`, and the
  suite stayed GREEN — the arm is unreachable for a `BASTImport`, because every
  caller passes it `directDefinitions`, which has already filtered wrappers out.
  Only printing the actual messages showed the error was still there. **A green
  suite after a fix is not evidence the fix did anything**; instrument.
  **Zero corpus movement, and that is evidence about the corpus** — riddl-models
  uses no `.bast` imports. The real evidence is `BASTImportLoadingTest`'s pinned
  contract, INVERTED: it required an error naming `App.Money` and now requires
  none.
  **NEW, filed as [4.6]:** a local and an imported definition may now share a
  name, and declaration order decides which wins.

- ~~**[4.6]** import/local shadowing.~~ — **RULED C, DONE `0e8441aca`: the LOCAL
  declaration always wins, regardless of position, AND the ambiguity warns naming
  EVERY side** (Reid: there may be more than two). Precedence is expressed as
  ORDERING (`localsThenImported`), so `.headOption` IS the rule and there is no
  second place to keep in step. The warning stays silent when all matches are
  local — that is `checkUniqueContent`'s report. Both orderings are tested, and
  that pair IS the ruling. Superseded: Consequence of [2.6]: `findMatchingCandidate` takes the FIRST
  match in contents order, so whether `type Money` declared in a domain beats a
  `Money` imported into that domain depends on which was written first. Nothing
  warns. Options, none of them obviously right: leave it (position is a
  defensible rule and matches how includes already behave); warn on the
  collision; or make local always win regardless of position. **My
  recommendation is to WARN and not to change the winner**, because the ambiguity
  is what the author needs told, and silently reordering precedence would make
  the same source mean different things before and after an upgrade. Not built —
  this one genuinely needs a ruling, and no corpus model exercises it.

  **~~Superseded framing~~** (kept so the reasoning is not re-derived):
  Today the two halves disagree: the content accessors report a `.bast`-imported
  definition (`domain.types` lists it, since 2026-08-03), but a REFERENCE to it
  does not resolve until `FlattenPass` runs, because the symbol table is built by
  TRAVERSAL and `BASTImport` is a wrapper the traversal does not descend. So a
  model can SEE an imported type and cannot NAME it, which is the kind of split
  that makes imports feel broken without ever producing a clear error.
  **The known obstacle, recorded so it is not rediscovered:** `ResolutionPass`
  keeps its own manual walk that descends `Include` and deliberately NOT
  `BASTImport`, reading `directDefinitions` at 7 sites, precisely because
  `Contents.filterThroughWrappers` cannot express "includes but not imports". That
  asymmetry is what has to change, and it is the reason this was filed as a
  question rather than a chore. Pinned today by `BASTImportLoadingTest` and
  `IncludeAndImportTest`, both of which assert the CURRENT behaviour and will need
  to move with it.

- ~~**BUILD: a query must REPLY or REFUSE on every path.**~~ — **DONE 2026-08-16,
  `07228a9a6`.** Now uses `dischargesOnEveryPath` with the refusal exemption, so
  the query rule is exactly PARALLEL to the command rule rather than stricter.
  **Shipped with NO migration**, and that was measured rather than hoped: 943
  on-query clauses in the corpus, only 5 files with a conditional inside one, and
  every one already replies or refuses on every path. Contrast the error-sink
  ruling, which needed 187.
  **Measuring found an older, unrelated defect — the more valuable half.** The
  check was producing **10 false warnings across 6 models** on handlers that
  plainly do reply, because reply/yield operands were still resolved with the
  NARROW `operandMessageKind`, which cannot see through a `ValueRef`. The comment
  there said they "stay MessageRef | Constructor only until Task 2"; Task 2 landed
  long ago and nothing came back to widen it, so the canonical spelling
  riddl-models actually uses — `let r: type X.Result = prompt(…)` then `reply r` —
  was invisible. Widened; corpus goes **10 → 0**.
  That is the third "claim about code elsewhere that nothing verified" found in
  this branch, and the second where the stale claim was a promissory note about
  work that had since landed.

### 3. Owed to other repos

- ~~**[3.1]** riddl-generator's `Finder` incompleteness.~~ — **CLOSED by THEM
  2026-08-16**, verified 2026-08-18 in their `task/done/`: all four criteria met.
  They re-pinned to `rc.14-121`, diffed against baselines (**longer, never
  shorter — the direction predicted**), audited all 57 `recursiveFindByType`
  sites, and found **no field still empty**.
  **Their audit is worth keeping**: riddlg sweeps NO value type at all — every
  sweep names a Statement or a Definition — so the whole value-composition half of
  the fix could not affect them. The real delta was exactly TWO containers,
  `Correlation.timeoutStatements` and `InvariantBlock.statements`. Superseded:
- **~~[3.1] history~~: DELIVERED 2026-08-15 to riddl-generator: `Finder` was returning incomplete
  results across 27 node fields.** Task dropped at
  `../riddl-generator/task/2026-08-15-finder-was-missing-content-across-27-fields.md`
  — **verified written, not merely claimed** (the 49-alias entry below is what
  that qualifier exists for). Fixed here at `b55d1d5cc` + `a3c0aa345`, both
  AFTER riddlg's pin and in **no tag yet** (latest is `2.0.0-rc.14`), so they
  cannot act until the next RC or a local `publishLocal`. Awaiting their reply
  on whether any of the 27 fields still comes back empty, and on whether any
  generated output got SHORTER (the unexpected direction).

- ~~**BLOCKED ON riddl-models: reactive-bbq's 2 `terminate` lines.**~~ — **DONE by
  riddl-models 2026-08-15, `2e619c44`.** Both migrated to `terminate self.id` in
  the same commit that took `2.0.0-rc.14-121` and cleared the bare-message-operand
  migration. reactive-bbq parses again; it is red now for a DIFFERENT and newer
  reason (the cross-context seam, § 3), which is worth not confusing with this.

- ~~**[3.2]** The exact `figma` input behind riddl-models' emitter report.~~ —
  **CLOSED 2026-08-18.** Reduced in scope by Reid and now moot: riddl-models'
  `task/` is empty and nothing here was ever blocked on it. The behaviour was
  NOT reproduced — riddlc prints a specific Error and exits 7, which is correct —
  and [2.1]'s ruling has since settled what `figma` may decorate (UI elements and
  an application-intent Context), which is the language question their report
  might have been circling. Superseded:
- **~~[3.2] history~~: AWAITING riddl-models: the exact `figma` input from their emitter report.**
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
- ~~**[3.3]** riddl-models' coverage model held out of their repo.~~ — **CLOSED
  2026-08-18, verified rather than assumed**: `../riddl-models/language-coverage/`
  exists (`.riddl`, `.conf` and `.bast`), so CI's EBNF validation — which walks
  that repo — now exercises `method`, `shown by`, `table of … of […]`,
  `attachment`, `replica of` and `figma` against the corpus. That was the gate
  whose absence let six emitter defects through. Superseded:
- **~~[3.3] history~~: riddl-models' coverage model is being held out of their repo** until this
  lands, so **CI grammar validation is NOT currently exercising `method`,
  `shown by`, `table of … of […]`, `attachment`, `replica of` or `figma` against
  the corpus** — precisely the gate that would have caught all six emitter
  defects. Expect it to land after the next RC. Until then, the only coverage for
  those constructs is this repo's own round-trip tests
  (`AggregateContentsRoundTripTest`, `ShownByRoundTripTest`,
  `TypeExpressionSpacingRoundTripTest`, `AttachmentRoundTripTest`).

- ~~**[3.4]** riddl-models corpus migration.~~ — **COMPLETE 2026-08-17.** Their
  `99fc29d1` (*"Upgrade to riddl 2.0.0-rc.15; the corpus validates 188/188 with
  zero errors"*) took validation-parity to **190/190** and `RiddlModelsRoundTripTest`
  to green. **Reid's release gate — "corpus at 100%" — is MET.** The last two red
  cases anywhere in this repo's suite belong to `../riddl-examples`, which now has
  a migration task (`task/2026-08-17-migrate-to-2.0-syntax.md`). History:
- **~~[3.4] superseded~~ NEARLY UNBLOCKED — riddl-models landed the migration mid-session
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

  **RE-MEASURED 2026-08-17 and it is now THREE, not two.** validation-parity is
  **187/190**, and the third model is **`reactive-bbq.riddl`**, failing on two
  errors of a kind not in the list above:
  *"crosses the context isolation seam from Context 'X' to Context 'X':
  receiveDrinkOrder is not declared in a domain ancestral to both"* (and the same
  for `sendPushNotification`). Measured against `origin/release/2` with my own
  changes STASHED, so this is corpus drift or a pre-existing seam defect, not
  anything landed this session. **Whose side it belongs on is unresolved — see
  [4.5].** The 188 figure above is what was true on 2026-08-15; it is left in
  place because the delta is the interesting part.

  **~~`Root2JsonCorpusTest`'s name and assertion disagree~~ — RESOLVED
  2026-08-16 by Reid: "Corpus at 100% should be the release gate, > 95% is
  contrived."** So the EQUALITY assertion was right all along (its own comment
  said *"NO allowance"*) and the ADVERTISING was wrong. The case is renamed to
  "(EVERY model)" and now reports a count rather than a percentage — a figure
  like 98.9% reads as a score against a threshold, and there is no threshold. §0
  and this entry had both been repeating the ≥95% bar as though the code
  implemented it.
  **Consequence, and it is the point of a gate: this suite stays RED until
  riddl-models migrates its last two models** (`patterns/entity/aggregate-root`,
  `patterns/entity/event-sourced`). That is not a defect here.

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
- ~~**[3.5]** synapify's `flattenAST` workaround.~~ — **CLOSED 2026-08-18: already
  delivered.** The task file is in their `task/` as
  `2026-08-15-flattenAST-workaround-can-be-dropped.md`, so the guidance is with
  them and nothing is owed from here. They still call `flattenAST` (3 sites, all
  in one test) and their code stays correct either way — it is their call when to
  take it. Superseded:
- **~~[3.5] history~~: synapify: `flattenAST` workaround can be dropped.** `Contents.definitions`
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

### 4. RULED by Reid 2026-08-17 — **ALL COMPLIANCE WORK COMPLETE**

**Every ruling is implemented, tested and committed** (`5919d0234` [4.4],
`2dc789ec4` [4.1], `0e8441aca` [4.6], `b1b78c389` [4.2], `61d028e4e` [1.1]+[1.5]).
`[2.1]` needed no code — see below. `[4.5]` went moot on its own.

**The corpus gate is now MET: validation-parity 190/190**, after riddl-models
shipped `99fc29d1` the same day. Three of the four long-standing known-red suites
are green; the fourth is riddl-examples, another repo, and a task has been dropped
there.



All eight questions were answered in one pass. **Three confirmed what was built
(`[4.3]`), and five changed it** — so this section is now a work list, not a
question list. Each item below records the RULING verbatim in substance, then
what it costs. Struck items are complete.

**The rulings, in one place:**

| # | Ruling |
|---|---|
| `[1.1]` | **C** — keep JSON for hosted models, point self-hosted at GBNF/XGrammar, and DOCUMENT the split |
| `[2.1]` | **Neither A nor B.** Drop scaffolding entirely; just allow the Figma URL as an ASSOCIATION on UI elements, and on an application-intent Context |
| `[4.1]` | **B** — redefine `streamlets` to mean all port-bearing processors; delete the accessors added as redundant |
| `[4.2]` | **A, but far broader** — `typeDeps` is a TYPE-DEPENDENCY graph, not a message one |
| `[4.3]` | **A** — keep as built. No work. |
| `[4.4]` | **B** — count the shape spec for any processor that can ascribe one |
| `[4.5]` | **C** — investigate enough to route it; may be moot once rc.15 reaches riddl-models. **MOOT, exactly as predicted: riddl-models shipped `99fc29d1` the same day and the corpus is 190/190.** |
| `[4.6]` | **C** — local always wins, AND warn, and **the warning must name ALL sides** (there may be more than two) |

- ~~**[4.1]** `streamlets` meaning.~~ — **RULED B, DONE `2dc789ec4`: `streamlets`
  now means every PORT-BEARING processor**, and `processors`/`portBearing` are
  deleted. Reid: *"streamlets is now an older idea, but in the new model every
  processor is capable of having one or more portlets."* The AST CONTAINMENT
  accessor `WithStreamlets.streamlets` is deliberately UNCHANGED — say so if the
  ruling was meant to reach it. Superseded reasoning: The [2.4] question, decided the additive
  way: `processors` and `portBearing` were ADDED rather than widening the
  existing accessors. Reasoning: every accessor in that family (`domains`,
  `contexts`, `entities`, …) is named after a KIND, so widening this one alone
  would make it the only one whose name does not say what it returns, and would
  change the answer under existing callers without their asking. The
  compatibility policy says add, don't change.
  **If you want the other reading**, the change is two `collect` bodies plus a
  type on each field; the accessors added here would then be redundant and
  should be deleted rather than left as synonyms. Landed in `2c19d6d70`.

- ~~**[4.2]** `typeDeps`' meaning.~~ — **RULED A-but-broader, DONE `b1b78c389`:
  it is a TYPE-DEPENDENCY graph.** Reid's example is the contract —
  record→set→named-integer — so a consumer can find loops and walk the hierarchy.
  Reuses the resolver's existing usage edges, folding FIELD-level uses up to the
  owning type (without that fold the record→set half is simply absent). **Known
  caveat recorded on the field**: the `tell` edges remain, so two processors
  telling each other's messages read as a cycle here; if a consumer needs purely
  structural edges the answer is to SPLIT the map, which is worth asking about.
  Superseded:
  The field was empty for every model ever analyzed (its guard could not
  succeed — see the note in `DependencyAnalysisPassTest`), so filling it required
  choosing what its source means. I chose "handling PlaceOrder leads to telling
  ShipOrder", because the handled message is the only Type in a `tell`'s
  surroundings and it matches the field's own documentation, *"map from each type
  to types it references"*.
  **This makes a public field go from always-empty to populated.** No in-repo
  consumer reads it, so nothing here changes behaviour, but riddl-gen or riddlsim
  might. If the intended edge was something else, say so and it moves. Landed in
  `1a3c1cf05`.

- ~~**[4.3]** `deliverableTypes` on the pass output.~~ — **RULED A: keep as built.**
  The only ruling that confirmed what was there. Superseded: This is a new public field on a pass
  output, keyed by the statement itself, and it is how MessageFlowPass and
  DependencyAnalysisPass resolve a `let`-local operand without duplicating
  ValidationPass's scope-threading walk.
  **The design question is whether a pass output is the right place for it**, or
  whether the resolution belongs in a shared utility both passes call directly
  (which would mean re-walking the statement tree to rebuild the `let` scope, so
  I did not). Landed in `b6b3dd03e`; the shared read path is
  `DeliverableTypes.of`.

- ~~**[4.4]** shape counting.~~ — **RULED B, DONE `5919d0234`: every processor's
  shape counts**, numerator and denominator both, moved into the shared helpers so
  they cannot drift apart. A Streamlet still counts unconditionally (its shape is
  required and known even when `ascribedShape` is None). **Expect maturity
  percentages to DROP** for processors that ascribe no shape — that is the ruling,
  not a side effect. Superseded:
  ALONE deliberately in [2.4], because unlike the other sites this is not a
  narrowing bug — it is a question about what a maturity metric should count.
  Every Processor may now carry an `ascribedShape`, so a Context or Entity that
  ascribes one arguably has the same specification to complete. **The counter
  argument is that a Streamlet is the only kind for which a shape is REQUIRED**,
  and a maturity denominator should count required specifications, not available
  ones. I believe the current behaviour is right and did not change it.
  Sites: `StatsPass.scala:354` and `:439`.

- ~~**[4.5]** **`reactive-bbq` now fails the corpus validation-parity gate.**~~ —
  **MOOT 2026-08-17, exactly as Reid predicted when ruling C.** riddl-models
  shipped `99fc29d1` (*"Upgrade to riddl 2.0.0-rc.15; the corpus validates 188/188
  with zero errors"*) and validation-parity went **187/190 → 190/190**. Neither
  investigation nor routing was needed; the answer was "theirs, and already fixed".
  **The lesson is about sequencing, not about the defect**: this was filed as a
  question at 15:00 and answered by another repo's commit before anyone looked at
  it. When a corpus number moves and the corpus is a live checkout, WAIT one beat
  before spending effort attributing it. Original analysis: Re-measured 2026-08-17: 187/190, not the 188
  recorded in [3.4]. The two new errors are context-isolation-seam errors
  (*"receiveDrinkOrder is not declared in a domain ancestral to both"*), a
  different class from the bare-message-operand errors the other two models
  carry.
  **Verified NOT caused by anything this session landed** — measured with my
  changes stashed, giving the identical 187 and the identical three model names.
  So it is either drift in `../riddl-models` (a live checkout another session
  edits) or a pre-existing seam-check defect. **Deciding that needs a look at
  the model, which is another repo's business** — hence a question rather than a
  task. If it is ours, it is a real Error being reported on a correct model,
  which is the false-positive shape this repo has been bitten by repeatedly.
