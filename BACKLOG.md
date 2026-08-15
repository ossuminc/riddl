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
- **Update the ossum.tech documentation site** with the same syntax changes,
  **plus a LIGHTER treatment of the implied syntax.** Reid, 2026-08-06 — the
  reference currently spells out more of the implicit forms than a reader needs,
  and the balance should shift toward what someone actually writes. Same source
  of truth: the commits on this branch, not recollection.
  (ossum.tech is a separate repo; this is a task DROP, not work done here.)

### 1. Queued, designed, not started

- **Make `!` a full synonym of `not` (A28) — RULED 2026-08-14, branch does not
  comply.** Reid, reviewing the `RIDDL-Tools-To-Do-List.md` reconciliation:
  *"`not` and `!` should be synonymous everywhere as the inverse of a boolean
  expression."* `!` must be legal in **every position `not` is**.
  This **OVERRIDES the 2026-08-13 ruling** recorded in `CLAUDE.md` — that `not`
  was RIDDL's only general-purpose negation, that `!` was a legacy spelling
  accepted ONLY as `when !<bare-identifier>`, and that it "will not be extended
  to" anything more. That paragraph has been rewritten; do not restore it.

  **What does not comply, verified 2026-08-14 at `ecfd69d2c`:**
  - `!` is a special case of `when_condition` alone —
    `when_condition = … | "!" identifier | …` (`ebnf-grammar.ebnf:275`) — and it
    takes a **bare identifier, not an expression**. So `!(a and b)`,
    `require !x` and `let y = !x` are all parse errors today. The fix is an
    alternative in `not_expression` (`"not" | "!"`), after which the
    `when_condition` special case should go away rather than sit beside it.
  - The two spellings build **different ASTs**: `not` a real negation node
    (`AST.scala:3337`), `!` a `negated: Boolean` flag on `WhenStatement`
    (`AST.scala:3660`). Synonymy means ONE node for both, which moves prettify,
    BAST and JSON — so this needs a **`FORMAT_REVISION` bump**.

  **Hazard, and it is a real one:** `!=` is a comparison operator
  (`ebnf-grammar.ebnf:351`). A `!` prefix rule ahead of `comparison` will swallow
  the `!` of `a != b` unless guarded by a not-followed-by-`=` test — and
  **regex lookahead is unavailable**, since Scala Native cannot compile it
  (`583d47556` removed one for exactly that reason). Use fastparse's `!` negative
  lookahead combinator on a literal `"="`, not a regex.

  **One design question to answer while building it:** which spelling prettify
  emits. Emitting `not` for both makes `!` a one-way alias and every round trip
  rewrites an author's `!` — which the reflectivity mandate arguably forbids;
  preserving what was written costs a flag on the node. Pick deliberately.

  **Already compliant, do not touch:** parenthesised grouping.
  `"(" boolean_expression ")"` is a `boolean_atom` in both the grammar
  (`ebnf-grammar.ebnf:357`) and the parser (`StatementParser.scala:590`), below
  `comparison`, so it composes with everything above it — `not (a and b)`,
  `(a or b) and c` and a parenthesised comparison all parse.

- **DONE 2026-08-14 (`4feb5a370`) — the bare-message-operand Error shipped.**
  **⚠ AN UNTRIAGED riddl-models REPORT SAYS "read this before flipping", AND IT
  ARRIVED AFTER THE FLIP.**
  `task/2026-08-14-valueref-migration-blinds-the-populates-repository-check.md`:
  the `Event 'X' populates Repository 'R' but is not defined in it` check fires only
  when the operand is a **`MessageRef`**. Rewrite the same statement to the `ValueRef`
  arm — which is exactly what this Error now compels — and the check stops firing with
  nothing about the model changed. Corpus-wide that took it from **863 warnings to 9**;
  the 854 are hidden, not fixed. Verified there with a negative control (reverting ONE
  site to the bare form brings its warning back).
  **This is not an argument to unflip** — it is a check that must learn the widened
  operand, the same lesson as `9d0e47acd` ("Fix consumers left blind by the send/tell
  operand widening"), which fixed the same class of blindness for the addressing and
  completeness checks and evidently missed this one. Triage the task file first; the
  fix belongs beside `checkBareMessageOperand`.

  Cost 30 tests across 20 suites, not the "one line" this entry estimated: our own
  fixtures were written in the same bare style as the corpus. 12 sites in two
  shared inputs (`everything_full.riddl`, `dokn.riddl`), 16 inline fixtures, and
  `TokenParserFileTest`'s token golden 408 -> 413. **The corpus stays red until
  riddl-models' migration lands here** — do not read that as a regression.
  Original entry follows.

- ~~**APPROVED 2026-08-14 — flip the bare-message-operand warning to an Error.**~~
  Reid: *"riddl-models is working on it from the same riddl version we are using;
  yes, make the flip, riddl-models should be corrected soon."* The block is
  lifted: they are building against our staged build, so shipping the Error is
  what gives them the diagnostics to fix against.
  **Go in with eyes open about CI.** This turns 14,714 warnings into Errors, so
  the corpus tests stay red until riddl-models lands its migration — on top of
  the two already red by design (§ 3). **Do not treat a red corpus as a
  regression while both are outstanding**, and do not "fix" it here.
  The change itself is one `addCompleteness` → `addError` in
  `checkBareMessageOperand`, plus flipping the `errorsOf(msgs) mustBe empty`
  assertions in `BareMessageOperandWarningTest` — which exist precisely so the
  severity cannot move silently. **Keep the field-less exemption** (62 of the
  14,714): with no data the type fully determines the value.
  Detail below, kept because it records the measurement.

- **Flip the bare-message-operand warning to an Error — BLOCKED on riddl-models.**
  The message-value-source design's end state (D3): naming a message type with no
  value is an Error. It shipped 2026-08-14 as a CompletenessWarning because the
  corpus holds **14,714** bare message refs the check reaches (plus **645** bare
  `morph` record refs) and **zero** constructor uses, so an Error invalidates every
  message-sending statement in all 189 models at once while CI requires them clean.
  Measured, not estimated — full table in
  `task/done/2026-08-14-where-does-a-message-refs-value-come-from.md`.
  The migration is filed at riddl-models
  `task/2026-08-14-bare-message-operands-now-warn-corpus-wide.md`, deliberately with
  no deadline. **Flip only when that repo reports zero**; the change itself is one
  `addCompleteness` → `addError` in `checkBareMessageOperand`, plus flipping the
  `errorsOf(msgs) mustBe empty` assertions in `BareMessageOperandWarningTest` — which
  are there precisely so the severity cannot move silently.
  Keep the field-less exemption when flipping: with no data the type fully determines
  the value, and it accounts for 62 of the 14,714.

- **DONE 2026-08-14 (`a68e4a037`) — `exactly-once` is a delivery INTENTION and all
  three option spellings are retired.** Reid ruled it into the enum, which is what
  unblocked deprecating them together; all three now CONSUME into their intention as
  `persistent` does. Original entry follows.

- ~~**`at-least-once` / `at-most-once` / `exactly-once` are inert as OPTIONS.**~~
  Filed by synapify 2026-08-14 alongside the `option persistent` retirement
  (`e0a424ed0`), and verified here:
  `StreamingParser.deprecatedConnectorOptions` (`StreamingParser.scala:61`)
  contains ONLY `"persistent"`, so unlike it the delivery options are NOT
  consumed into the intention they duplicate. `option at-least-once()` therefore
  parses as a plain registry option, means nothing, and draws no message at all
  — two spellings where one is silently inert.
  **Not a mechanical repeat of the `persistent` fix**, which is why this is
  filed rather than done: `exactly-once` is a registry option with **no
  intention at all**, so deprecating the option spellings first needs a ruling
  on whether it should become one. Deprecating two of three and leaving the
  third current would be its own inconsistency. Decide `exactly-once`, then do
  all three together (consume in the parser, mark `deprecatedFor`, extend the
  Connector drift guard in `RecognizedOptionSetTest`).
- **`OnInitializationClause.parameters` / `OnTerminationClause.parameters` have
  no default and are not trailing** (`AST.scala:4236`). Filed by synapify
  2026-08-14; it is the only thing in rc.14 that broke their build. It departs
  from the compatibility policy quoted in the adjacent `Connector.intentions`
  comment in the same release — *"The compatibility policy requires a new
  parameter to have one"*. Adding `= Seq.empty` is source-compatible and cheap.
  Note the constraint that produced it: `@JSExportTopLevel` requires defaulted
  params to be TRAILING, and `contents`/`metadata` are already defaulted — so
  the fix is to move `parameters` after them, not merely to default it in place.
- **Two narrow-operand gaps left by the message-value widening (Task 1,
  `9d0e47acd`).** Both are false-POSITIVE-only (an advisory warning that should
  not fire), never a missed Error, and both have zero corpus impact today because
  riddl-models uses no widened-source operand yet. Revisit if adoption grows.
  1. **`elements` is not threaded into `widenedOperandType`** — it is called with
     `Map.empty`, so a `foreach`-bound loop variable
     (`foreach msg in cmds do { tell msg to X }`) validates as a legal operand
     but is invisible to `checkTellAddressing`'s `by`/ambiguity Errors and to the
     three completeness checks. **Pre-existing in kind** — the old `operandType`
     never consulted `elements` either — and `foreach` element was not among the
     five source kinds Task 1 enumerated. Documented in the helper's docstring
     rather than left silent.
  2. **`emittedMessageTypes` is still narrow** — a whole-root `Finder` sweep with
     no per-clause scope, feeding A70's correlation-fold advisory. Fixing it
     properly needs a restructuring bigger than a fix round.


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

  1. **`commands` (−198) is the alarming one, and probably the cheapest win.**
     245 JVM against 47 Native, and the module has NO `src/test/scala-jvm-native`
     directory at all — 14 shared test files and 7 under `scalajvm`. The JVM
     count matches the riddl-models corpus gate exactly, which means **our single
     largest regression net almost certainly runs JVM-only.** Confirm that first;
     if the corpus round trip can run on Native, that one change is worth ~200.
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


- **RETIRED 2026-08-14 — COMPLETE, 7 of 7. This entry is kept for one release as
  a pointer and should be deleted at 2.0.0.** Tasks 4-7 landed in `f40822d3e`,
  `691f0e28c`, `11bf9c59c` and `4f26e6462`; the branch was certified
  tri-platform afterwards. What it taught is in `NOTEBOOK.md`; what is durably
  true is in `CLAUDE.md`; the language decision is item 72 of
  `../RIDDL-Tools-To-Do-List.md`. **The only work still outstanding from this
  design is the bare-operand Error flip, tracked as its own item above** — do not
  re-read the task detail below expecting open work. The text through Task 7 is
  left in place this once because it records measurements and reasoning that
  nothing else captures.

- ~~**A message ref must be able to name its VALUE, not just its type — IN
  FLIGHT. Tasks 1-3 of 7 DONE; Task 4 IS NEXT and Tasks 4-7 NOT STARTED.**~~
  **Plan: `docs/superpowers/plans/2026-08-14-message-value-source.md`** — it has
  the per-task detail and the Global Constraints; work from it, not from this
  entry.
  **Design: `docs/superpowers/specs/2026-08-14-message-value-source-design.md`.**
  **Ledger: `.superpowers/sdd/2026-08-14-message-value-source/progress.md`** —
  gitignored, but it names every commit so `git log` reconstructs it.

  **Task 1 (DONE): the SOURCE is widened on `send`/`tell` only.** An operand may
  now name a state field, `let`-local, function result or `ask` result, not just
  an on-clause binding; `self` is rejected with its own message. Review found and
  fixed a regression Task 1 CREATED — `checkTellAddressing`'s `by`/ambiguity
  Errors and three completeness checks read a narrow probe that returned `None`
  for the newly-legal operands. Both fixed; the four-copy alias-following
  one-liner is now one extracted `resolveTypeAlias`.

  **Task 2 (DONE, `e87249acc`): `yield`/`reply`/`morph … with` take the widened
  operand.** `yield`/`reply` reuse `deliverableMessageValue`; `morph` gets the
  record-side counterpart. The recorded reason they had been excluded from A56 —
  *"it would interact with yield conformance"* — did NOT survive inspection: that
  comparison is by RESOLVED TYPE, which a `ValueRef` supplies exactly as a
  `MessageRef` does, so it is a check to keep working, not a reason to stay
  narrow. `morph` deliberately does NOT reuse `checkMessageOperandSource` (a
  morph carries the RECORD typing the target state, A9b, so the message-kind test
  would reject every correct use); `checkMorphOperandSource` requires the name to
  resolve AND to match the state's record type when that is known.

  **Task 3 (DONE, `db2eb4235`): all four reflection surfaces, at
  `FORMAT_REVISION` 17.** The 16 → 17 bump is SPENT — no later task may move it.
  `language/input/import/NotImplemented.bast` regenerated: 93 bytes, byte 12 only
  (octal 20 → 21). JSON carries the `ValueRef` with the reserved kind `"bound"`
  on all three statements, so no new DTO and no schema change; `JSON_COVERAGE.md`
  rows updated. Prettify needed NO change (statements emit via `Statement.format`,
  which already handled `ValueRef`) — its round-trip tests are regression guards,
  not TDD, and they assert the NODE KIND because `yield evt` re-emitted as `yield
  event evt` would satisfy a string assertion while naming a type that does not
  exist.

  **Remaining, in plan order:** **T4 the bare-form CompletenessWarning — the next
  one, and NOT small** (see its own note below); T5 the unused-`initiate`-id
  Warning (Reid: *"just build it"*); T6 a test pinning saga-step legality; T7
  certify.

  **T4's two hard constraints, both easy to get wrong:** (a) it must ship as a
  **WARNING, never an Error** — the corpus holds **14,730** bare refs and ZERO
  constructor uses, so an Error invalidates every message-sending statement in all
  189 models while CI wants 189/189 clean; (b) the field-less exemption changes
  that 14,730, which has **already been quoted to riddlg**, so the reduced number
  must be COUNTED and the figure corrected wherever it was cited.

  **T2's site audit is SPENT — Task 2 consumed it and the line numbers below are
  now stale.** Kept only because it records WHICH KINDS of site exist for a
  widening of this shape, which T4 will need again. The three
  AST nodes are `YieldStatement.msg` (`AST:3591`), `ReplyStatement.msg`
  (`AST:3627`) — both `MessageRef | Constructor` — and `MorphStatement.value`
  (`AST:3520`, `RecordRef | Constructor`). Everything that dispatches over them:
  - **`ValidationPass`** (~25 sites): `:180`/`:179` (`note(operandType(...))`),
    `:731`/`:730`, `:718`/`:717`, `:688`, `:1427` (ReplyStatement case),
    `:1511`/`:1512`, `:1544`, `:1567`/`:1568`, `:2695`, `:2993`/`:2994`/`:2996`,
    `:3896`/`:3897`/`:3898` (`msgRefs`), `:4981`/`:4982`/`:4983`
    (**`statementValues`** — the INPUT every walk is built on; a field dropped
    here defeats a total match, which is how `require X with initiate` evaded
    two bans), `:5155`/`:5156`, `:6899`/`:6905`/`:6907`, `:7001`/`:7002`.
  - **JSON, BOTH directions**: `JsonifierPass` `:1363` (morph), `:1372` (yield),
    `:1373` (reply); `JsonAstBuilder` `:1410` (morph), `:1425` (yield), `:1426`
    (reply). `serializeMsgOperand`/`buildMsgOperand` are the shared helpers.
  - **Parser**: `StatementParser` `:174` (yield), `:186` (reply), `:270` (morph).
  - **EBNF**: `:307` `yield_statement`, `:308` `reply_statement`, `:387`
    `morph_statement`; the target is `:298` `deliverable_message_value`, which
    `send`/`tell` already use.
  Prettify goes through `format` on each node. **The compiler will NOT flag a
  missed arm** — a wildcard makes a match syntactically exhaustive, so the
  terminal `throw` this repo prescribes is itself what silences it. Audit by
  reading.

  **Three design questions were resolved by the controller, not by Reid** —
  flagged so they can be overruled: field-less messages are EXEMPT from the
  warning; `reply` is IN scope; `self` must fail with its own message.

  **READ THIS FIRST, or you will design a feature that partly exists: A56
  ALREADY BUILT HALF OF IT.** `SendStatement.msg` (`AST:3496`) and
  `TellStatement.msg` (`:3570`) are ALREADY `MessageRef | Constructor | ValueRef`;
  the EBNF already has `deliverable_message_value = message_value |
  path_identifier` (`:298`) wired into `send`/`tell`; `ValidationPass` already
  dispatches the `ValueRef` arm (`:1163`, `:1192`); and `operandType` (`:752`),
  `operandMessageKind` (`:763`) and `operandMessageName` (`:931`) already handle
  it. **`on p: command Ping is { tell p to entity F }` works TODAY.** The task
  file that requested this does not mention any of that. This is a WIDENING of
  A56, and far cheaper than "98.2% of generated holes" suggests.

  Four things are genuinely missing: the source is restricted to on-clause
  bindings and is a hard **Error** otherwise (`checkBoundMessageOperand`, `:920`
  — whose message names binding as the only legal source and becomes a lie once
  widened); `yield`/`reply` are excluded; `morph … with` is excluded
  (`MorphStatement.value: RecordRef | Constructor`, `AST:3520`) which is riddlg's
  other 37.6%; and nothing warns on the bare form.
  **The stated reason `yield`/`reply` were excluded does not survive the
  widening** — their operand is compared against the declared `yields`/`replies`,
  and that comparison is by resolved TYPE, which a `ValueRef` supplies exactly as
  a `MessageRef` does.

  **Three open questions need a ruling BEFORE implementation** (design § 6):
  Q1 does a field-less message (`event Started is { }`) need a value at all —
  the type fully determines it, so warning is `???`-style noise, and exempting
  them may take a real bite out of the 14,730 below, so COUNT before quoting
  that number publicly; Q2 is `reply` in scope for the warning, given its type is
  already pinned by the clause; Q3 does the widened source admit `self` — it must
  fail, but with a good message rather than "does not name a message".

  **Wire format:** C2/C3 change what three statements can hold, so BAST needs
  `FORMAT_REVISION` **16 → 17** — note 16 was just consumed by the
  interaction-block fix (`78a025362`), so this is a SECOND bump and
  `language/input/import/NotImplemented.bast` must be regenerated again, from its
  own directory.

  Origin: `task/done/2026-08-14-where-does-a-message-refs-value-come-from.md`.
  `send event Foo to outlet Bar` names a message TYPE and says nothing about
  where the value comes from, so a generator has nothing to lower. Measured by
  riddlg on reactive-bbq: **659 of 1088 `AI FILL` markers (60.6%) are exactly
  this**, and with the record-ref analogue in `morph` it is **98.2% of every
  hole** in the generated system. Each becomes a `null` in generated Java.

  **Reid's decisions, all three (2026-08-14):**
  1. **Shape — a bare `ValueRef` becomes a THIRD arm** of `message_value`,
     which is `constructor | message_ref` today (A54, `ebnf-grammar.ebnf:292`).
     No new keyword. It is unambiguous because RIDDL requires the kind keyword
     on a type, so a bare identifier can only be a value:
     `on placed: event OrderPlaced is { send placed to outlet Downstream }`.
     **`from` was REJECTED** for this: it already means the SENDER in epic
     interactions (`send command Foo from user U to context C`), and one word
     with two meanings in sibling constructs is a cost authors pay repeatedly.
  2. **Sources — ANY resolvable `ValueRef`**: state-record field, on-clause
     binding (A55), `let`-local, function result, `ask` result. One rule rather
     than an enumeration to keep in sync, reusing machinery A55 already built
     and tested. The value's type must BE the message type — that check is what
     makes the feature worth more than a comment.
  3. **The bare form becomes an ERROR — but NOT in one step.** End state is an
     Error; the route there is warn-first, per the repo's deprecate-then-remove
     path (the same one prescribed below for the inline aggregation in
     `requires`/`returns`).

  **The measurement that forced the sequencing** (counted 2026-08-14, do not
  re-derive): riddl-models holds **14,730 bare message refs — 7,541 `tell`,
  6,445 `send`, 406 `reply`, 349 `yield` — and ZERO uses of the constructor
  form.** So an Error does not migrate the corpus, it invalidates every
  message-sending statement in all 189 models at once, and the CI gate requires
  189/189 validating clean. Sequence: ship the ValueRef arm + a
  CompletenessWarning, drop a migration task on riddl-models, flip to Error when
  the corpus is clean. riddlg is served either way — a warning marks all 14,730
  sites for it exactly as an Error would.

  **Needs a plan before implementation** (standing rule: each item gets a plan
  approved first). The plan must cover: the EBNF/GBNF change and TatSu
  re-validation; type-checking the ValueRef against the message type; all four
  statements (`send`/`yield`/`tell`/`reply`) plus the `morph … with` record
  analogue, since `message_value` is shared; and the four reflectivity surfaces.

  **One observation from riddlg worth keeping, because it is about US:**
  riddl-models uses the constructor form zero times while riddlg's own fixtures
  use it 30 times across 6 spec files. Each body of tests was green about a path
  the other never exercised. That is the same fixture-vs-corpus blindness that
  hid the instance-identity Critical, and the CI grammar validation against
  riddl-models proves only that those models parse — never that the corpus
  reaches the language's expressive range.

- **DONE 2026-08-14 — all four resolved, and one was already fixed.**
  `7ed365e60` `validateConstructor` now follows alias chains (3 tests, revert-proved
  under a throwaway cache). `fdf29927f` `Pass.traverse` descends into `when`/`match`/
  `foreach` bodies, closing BOTH the resolver-side and validation-side halves — they
  were one defect seen from two passes. **The saga-statements bug is CLOSED as
  already fixed** by `a1bce0d50`, verified with this entry's own repro rather than by
  reading: all four bogus paths are reported. Corpus A/B for the traversal change:
  **21 non-bare-operand errors before, 21 after** — it surfaced nothing in the corpus
  and exactly one real defect in our own fixture (`everything_full.riddl` sent to
  `APlant.Source.Commands`; the outlet is `OutCommands`). Original entry follows.

- ~~**APPROVED TO FIX 2026-08-14 — all four bugs. Reid: *"Bugs: fix all 4."***~~
  The four are: `ResolutionPass` not descending into nested statement bodies
  (below); `validateConstructor` not following type aliases (below); the
  statement-scope checks missing nested statements (below); and saga step
  statements never being validated (§ 2).
  **Fix them as ONE shape, not four tickets.** Three of the four are the same
  defect — statements held in a **field** (`when`/`match`/`foreach` bodies,
  `SagaStep.do/undoStatements`) are skipped by the generic `Branch` traversal,
  which walks `contents` only. That is already a documented trap in `CLAUDE.md`,
  and fixing one pass does not fix the other: the resolver half is in
  `ResolutionPass`, the validation half in `ValidationPass`, and the saga half is
  a third instance. The standing rule applies — *fix the SHAPE of a
  dispatch/recursion defect, not the instance* — so the fix should grep for every
  field-held statement site rather than patching the three that were reported.
  **Each needs a corpus A/B before it lands.** These can only ADD resolution and
  validation, so each will surface references that were silently unchecked; that
  is the point, but it means the corpus delta is the deliverable, not an
  afterthought. Sequence them AFTER the bare-operand Error flip, or the two
  deltas become impossible to tell apart.
  `validateConstructor` is the odd one out — an ordinary missing alias walk, and
  the cheapest of the four.

- **BUG: `ResolutionPass` does not descend into nested statement bodies, so an
  ordinary reference inside `foreach`/`when`/`match` never enters the refMap.**
  Found 2026-08-14 while writing the lexical-scope threading tests (`957f64534`);
  pre-existing, not introduced there. The `ForeachStatement` arm says so
  outright. Consequence: only LEXICALLY-carried names (a `let`, a loop element,
  a lifecycle parameter) resolve inside a nested body — an ordinary reference,
  such as a state field, does not, because nothing ever put it in the refMap.
  **This is the resolver-side twin of the validation-side hole already filed
  below** (`checkStateReadScope` and friends never seeing nested statements):
  same cause — statements nested in `when`/`match`/`foreach` are FIELD-held and
  the generic traversal skips them — but this half is in `ResolutionPass`, so
  fixing the validation half does not fix it.
  **Verified, and it already shaped a test:** the `foreach` comparison case in
  `LexicalScopeThreadingTest` compares two ELEMENT fields rather than an element
  against a state field, because the latter would have passed for the wrong
  reason. Any future test in this area risks the same trap.
  Needs a corpus A/B — this can only ADD resolution, so it may surface
  references that were silently unresolved and are now type-checked.

- **BUG: `validateConstructor` does not follow type aliases.** Found
  2026-08-13 by the Task 6 review of the processor-instance-identity plan;
  pre-existing, not introduced there. `validateConstructor`
  (`ValidationPass.scala:6058-6060`) computes a constructor's fields with
  `typ.typEx match { case ate: AggregateTypeExpression => ate.fields; case _ =>
  Seq.empty }` — no alias-following. So `command Ship is Shipment` followed by
  `command Ship(orderId = oid)` misreports *"'orderId' is not a field of Type
  'Ship'"* and gets the arity wrong, on a legal model.
  **The fix already exists next door:** `checkTellAddressing`'s `fieldsWithOwner`
  (`ValidationPass.scala:5291-5296`) walks `AliasedTypeExpression` through
  `resolution.refMap.definitionOf[Type](ate.pathId)` recursively, as does the
  older `aggregateFieldsOf` (`:5426-5434`). Reuse one of them rather than
  writing a third.
  Verified real: Task 6's alias regression tests had to route AROUND this bug
  (bare `MessageRef` tells, no constructor args) to test what they were actually
  testing. Needs a corpus A/B — this can only REMOVE false errors, but count
  them, since a model may have been edited to satisfy the wrong diagnostic.

- **BUG (shipped in rc.13): statement-scope checks miss nested statements.**
  Found 2026-08-13 by a code review of unrelated work. `checkStateReadScope` is
  wired into `validateStatement`, which only sees statements the pass dispatcher
  visits. `Statement extends RiddlValue`, NOT `Branch`, so `Pass.traverse`'s
  final `case value: RiddlValue` arm never descends into
  `WhenStatement.thenStatements`, `MatchCase.statements` or
  `ForeachStatement.doStatements` — those are FIELDS, the same hazard
  `Pass.scala:311-326` (SagaStep) and `:329-340` (Correlation) each needed a
  special case for. `statementValues` does not recurse either.
  **So the `set` and `get from state` scope rules from `0f06d85d9` silently do
  not apply inside `when` / `match` / `foreach`.** A `set` in a context handler
  is an Error at statement top level and accepted one `when` deeper.
  The depth-complete hook already exists and is the fix: `checkStatementScopes`
  (`ValidationPass.scala:5967`) recurses at `:6026` and `:6040` and calls
  `validateValue` at any depth. Move the check there, keeping ONE call site.
  Needs a corpus A/B — this can only ADD messages, and the models that were
  silently passing may be numerous.

- ~~**GAP: 13 shared `language` parser suites — 169 cases — never ran on
  Native**~~ — **DONE 2026-08-14, `546f2f834`.** Concrete runners moved from
  `src/test/scalajvm` to `src/test/scala-jvm-native` and renamed `JVMNativeTests`.
  Native `language` 343 → 512, exactly the predicted +169, nothing excluded and
  nothing weakened — the suites build every input from `RiddlParserInput` and
  string literals, so no Native hazard was present. Rolled into the JVM/Native
  gap item above.

- **DONE 2026-08-14 (`8177418d1`) — the test exists, covering do- AND undo-blocks.**
  It was correct by accident: both ban predicates in `checkInstanceEffectScope`
  are structurally false for a saga-step statement, so nothing would have gone
  red. Original entry follows.

- ~~**CONFIRMED 2026-08-14 — build the test. Reid: *"yes, add a test case for it
  to make sure it continues to be supported."***~~ No design question remains; the
  deliverable is a `SagaValidatorTest` case asserting `initiate` and `terminate`
  both validate clean in a saga step, citing the ruling in a comment. Cheap, and
  it is the only thing standing between the ruling and a future tightening of
  `checkInstanceEffectScope` silently removing the legality. Context follows.

- **RULED 2026-08-14: `initiate`/`terminate` ARE legal inside a saga step.**
  Reid: *"a saga may need new entities to be created."* That settles it — but
  the behaviour is currently correct **by accident, not by decision**, so the
  remaining work is a TEST that pins it. Without one, the next person to tighten
  `checkInstanceEffectScope` removes the legality and nothing goes red.
  Add a case asserting `initiate` and `terminate` both validate clean in a saga
  step, with a comment citing this ruling. Note the asymmetry the final review
  flagged is now RESOLVED in the other direction: `self` is banned in a saga
  step while these are legal, which is coherent — a saga has no instance
  identity of its own, but it may create and destroy instances.
  Original context, kept because it explains why nothing enforces it:
  `checkInstanceEffectScope`
  (`ValidationPass.scala:1029-1054`) bans them in exactly two shapes — a
  parent that is an `OnActivationClause`/`OnPassivationClause`, or any
  `Function` in the parent chain — and for a saga-step statement `parents.head`
  is the **Saga** (a `SagaStep` is a `Leaf` and is never pushed; see
  `Pass.traverse`), so both predicates are structurally false. Nothing tests
  either way.
  **The likely answer is "legal"** — a saga step exists to have effects, which
  is exactly why the two existing bans do not name it — but a construct that is
  permitted because nobody wrote the predicate is not the same as one that is
  permitted on purpose, and the third banned context (a correlation fold) shows
  the design does discriminate. Reid to rule; then either add a test pinning
  legality or add the predicate.
  **The sharpest argument that this needs an actual ruling** (final whole-branch
  review, 2026-08-13): `self` IS banned in a saga step — deliberately, with its
  own message, per §"What this design does NOT cover" — while `initiate` and
  `terminate` are not. So the feature as shipped says a saga step has no
  instance identity but MAY create and destroy instances. Whichever way it is
  ruled, the two halves should agree; today they disagree by accident.

- **NEW CHECK — Reid, 2026-08-14: "no further task is needed, just build it."**
  **Folded into the message-value implementation above**, not a separate task.
  A `let x = initiate …` whose id is **never subsequently referenced** draws a
  plain **Warning** — on by default, NOT gated behind
  `showCompletenessWarnings`, because unlike a missing tell address this is
  locally decidable from the clause body alone.
  **Why a Warning and not an Error**, recorded so it is not re-litigated: a
  self-terminating worker legitimately has an unused id, and an Error would
  make that pattern unwritable; RIDDL specifies MEANING, so an unstated fate is
  under-specification (which warns) rather than self-contradiction (which
  errors); and nothing in the corpus uses `initiate` at all, so the repo's
  standing "do not ship the Error before counting" rule has no data to clear.
  **The real work is the escape-route analysis, not the message.** An id
  escapes by being `set` into state, passed as an argument to a `tell`, passed
  to `terminate`, yielded in an event, or `put` to a repository — and the sweep
  must be conservative enough that no legal model is rejected. That is what
  makes it a task rather than a line in this one.

- **DONE 2026-08-14 (`cb2ea1dd7`) — `isIdForEntity` resolves through `uniqueIdReferent`
  and compares with `eq`.** Test asserts a COUNT, not nonEmpty, which is what makes it
  fail under the old name match; revert-proved. Note for anyone attempting a CLI corpus
  A/B on a completeness check: each model's `.conf` supplies its own options and
  overrides `--show-completeness-warnings`, so the count stays at zero either way and
  the comparison says nothing. Original entry follows.

- ~~**NAME-MATCHING SURVIVOR: `isIdForEntity`**~~ (`ValidationPass.scala:2343`,
  inside `validateEntity`'s "does not define an Id type for its identity"
  completeness check) decides whether a `Type` is an Id for THIS entity with
  `uid.entityPath.value.lastOption.contains(entity.id.value)` — the
  last-segment NAME match Reid overruled for task 6 (`isAddressFieldFor`) and
  again in the final review (`checkOnTermLeadingParameter`). Found by the sweep
  those two fixes prompted, 2026-08-13; **NOT fixed there** because it PREDATES
  the instance-identity branch (`git log -L` gives `99549df47`, the AIHelperPass
  replacement) and it drives a CompletenessWarning over all 189 corpus models,
  so it needs its own A/B rather than a ride on someone else's.
  **Why it matters more now than it did**: `Id(P)` widened from Entity to any
  Processor this branch, so a `type X is Id(Other.Order)` in a model with two
  same-named entities can silence the warning for BOTH — and, symmetrically, an
  `Id(repository Foo)` whose last segment matches an entity name can be counted
  as that entity's identity type.
  **Fix**: resolve `uid.entityPath` through the refMap and compare with `eq`,
  as `isAddressFieldFor` does. Note the lookup's key parent is the OWNING Type,
  and `TypeValidation.uniqueIdReferent` already encapsulates that — reuse it
  rather than writing a third variant.
  (Also noted by the same sweep and deliberately left alone: the projector
  "declares a repository but never tells it" check at `:3007` matches
  `repoRef.pathId.value.lastOption` against each tell's target name. Same
  category, also pre-existing, lower stakes — it is a UsageWarning about a
  reference the author wrote in the same definition.)

- **`Value` has no NUMERIC LITERAL, so `initiate entity Order(1)` does not
  parse** against `on init(total: Integer)`. Pre-existing A54 limitation
  (`count > 5` and `record R(1)` both fail to parse today; see
  `StatementsTest`'s "reject a bare-number comparison operand"), and the
  existing tests work around it by declaring `String` parameters and passing
  `"1"`. It is filed HERE because lifecycle parameters are the first construct
  designed around passing SCALARS, so this is where it bites hardest: the
  design spec's own example, `on init(custId: Id(entity Customer), total:
  Currency)`, cannot be invoked with a literal amount.

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

- **Cross-context `tell` isolation seam — Error, but MEASURE FIRST.**
  Reid ruled 2026-08-13 that a `tell` into a different context is an Error
  unless the message type is declared in a domain ancestral to both; across
  domains an
  adaptor is always required. Separately and independently, a cross-context tell
  is **always** a durable channel — the common-domain exemption waives the
  adaptor, never the durability.
  This completes **A4 (ACCEPTED)**, which already rejects foreign *message
  types* outside adaptor scope; this extends the same seam to foreign
  *processor targets*.
  **Do not ship the Error before counting.** A heuristic says 5,301
  cross-context tells (64% of all tells) but the method is unsound, and the
  exemption's size is UNMEASURABLE by grep: 603 of 996 corpus files are include
  fragments with no top-level construct, so nothing file-local can tell a
  domain-level message type from a context-level one. Build the check with a
  counting mode, run it under riddlc's real resolution, then decide the
  migration.

- **Clusterability: `clustered`, and `self.isClustered`.** Split out of the
  identity design 2026-08-13. NOT "multiplicity" — Reid ruled that **entity is
  the only multiply-instantiated processor**; contexts, projectors, streamlets,
  repositories and adaptors are singletons that may be clustered for resilience,
  and clustered instances are interchangeable so clustering does not affect
  addressability. `self.isClustered` was deliberately kept out of the identity
  spec because it would have forward-referenced vocabulary this item defines.

- **Survey the CM and every A item for future `self` fields.** Reid, 2026-08-13.
  `self` currently carries `id` and `version`. Find the other usually-available
  pieces of processor information that belong there, classifying each by whether
  it is statically knowable — in which case a generator inlines it and it does
  NOT belong on `self` — or genuinely runtime-only, which is the admission test
  the design settled on. `self.isClustered` is already claimed by the
  clusterability item above.

- **Computational Model amendments owed by the identity design.** Three, all
  from 2026-08-13: (a) "activate on first message" (§4, line 999) must become
  rehydrate-an-existing-instance, never create-on-demand, now that `initiate`
  invokes `on init` explicitly; (b) the memory-space axiom — only processors
  within one context are guaranteed to share memory, which is what licenses a
  generator to optimize the same-context `tell`; (c) `Id(P)` (runtime instance
  identity) must not be conflated with the definition ULIDs of line 2523
  (model-time identity of a definition).

- **BLOCKED UNTIL 3.0 — drop the deprecated inline aggregation from
  `requires`/`returns`, then narrow the accessors to `Option[TypeRef]`.**
  **Reid, 2026-08-12: wait for 3.0.** Removing a deprecated form is a breaking
  change, and the compatibility policy in `CLAUDE.md` allows it only in the next
  MAJOR release — the inline form was deprecated during 2.x development, so 2.0
  is not where it goes. Do not start this against `release/2`; the detail below
  is kept because it was verified and would otherwise be re-derived in a year.
  Originally decided by Reid 2026-08-04 while moving the clauses into contents: `Option[TypeRef]` is the wanted END state,
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

#### Decided in `../RIDDL-Tools-To-Do-List.md` but never built

**RULINGS TAKEN 2026-08-14, before an unattended run.** Reid answered four questions
up front. Two are built (`exactly-once`, A43+A46 verbatim). The other two are
APPROVED BUT NOT BUILT and are the largest remaining items:

- **Numeric literals in `Value` — APPROVED, integers AND decimals.** Unblocks
  `initiate entity Order(1)`, `count > 5`, `record R(1)` and the identity spec's own
  `on init(total: Currency)` example. Touches parser, EBNF, GBNF, prettify, BAST and
  JSON. **Wants a plan first** — it widens a closed union that four reflection
  surfaces switch on.
- **A20 typed holes — APPROVED, spelled `prompt("…") as T`.** Reid chose this over
  `prompt T ("…")` and over the document's un-RIDDL `Value[T]("prose")`. It reuses the
  shipped `prompt` and ascribes a type after it, matching `on foo: command Foo` and
  `let x: T = …`, so nothing new enters the lexer. Also wants a plan.

**THESE TWO AND A38 SHARE ONE `FORMAT_REVISION` BUMP (17 -> 18).** Each adds or changes
an AST node that BAST must carry. Doing them in three commits with three bumps would be
three needless `.bast` regenerations across riddl-models; doing them without a bump at
all silently corrupts old files. **Decide the bump ONCE, in whichever lands first, and
say so in its commit** — the message-value plan's "the 16 -> 17 bump is SPENT" note is
the precedent for how to record it.

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

- **A20 — typed-hole value expressions, `Value[T]("prose")`.** A vague-but-typed
  expression: the type is known and checkable, the computation is prose filled in
  by AI at generation time. Types the seam between the deterministic and AI
  tiers, which is untyped today.
  Verified unbuilt: `grep -n "Value\[" language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf`
  returns nothing. The untyped relative DID ship — `prompt_value = "prompt" "("
  literal_string ")"` — so the seam exists and is exactly as untyped as A20
  complains of. `let x: T = prompt("…")` is the nearest available approximation
  and constrains the hole from outside; decide whether that is enough before
  building the general form.

- **DONE 2026-08-14 (`5072bad5b`) — A43 and A46 shipped together.** Same four rules,
  and A46's verbs only make sense beside A43's modalities. Fixture at
  `language/input/modality-aliases.riddl`; TatSu 104/127 -> 105/128, the extra pass IS
  that file. **A46's mixed grammatical person is deliberate** (Reid, verbatim) and is
  commented at the parser, because it is the OPPOSITE call to the one already recorded
  on `acquisitionAliases` — the next reader will otherwise "regularise" it.
  Original entries follow.

- ~~**A43 — modality-extended alias vocabulary.**~~ Outputs gain `sound`, `speech`,
  `haptic`; inputs gain `voice`, `gesture`, `gaze`; groups gain `scene`, `space`,
  `zone`. Closed lists, **zero structural change** — the input/output/group triad
  is already the modality-free logical core — so the work is three `StringIn`
  lists plus their EBNF rules plus a corpus fixture.
  Verified unbuilt: none of the nine words appears in the alias rules of
  `ebnf-grammar.ebnf`.

- ~~**A46 — presentation-alias additions and compound consistency.**~~ *(shipped with A43; the compound-output noun/verb consistency WARNING is NOT built — see below.)* `plays`
  (sound/animation); `speaks`, `announces` (speech); `vibrates`, `pulses`,
  `nudges` (haptics); `diffuses` (scent); `serve`, `offer`, `taste`. Plus a
  warning for noun/verb inconsistency across a compound output's parts.
  Verified unbuilt: `presentation_aliases` is still the original five
  (`ebnf-grammar.ebnf`, and the matching `StringIn` in `GroupParser.scala:45`).
  **Do this with A43** — they touch the same rules. Note the asymmetry it closes:
  A44's INPUT verbs grew twice (sixteen now, against the ten A44 listed) while
  its OUTPUT counterpart never moved, so a deliberately symmetric pair is out of
  step. Re-marked ACCEPTED from REQUIRED by Reid, 2026-08-14.

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
  rule.** Reid ruled 2026-08-09: *"There must be no non-sealed matches — it is
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

- **DONE 2026-08-14 (`75a791682`) — it should not; a head need only bear an OUTLET.**
  The preliminary question this entry demanded is answered: the two reactive-bbq
  repositories are **WIRED**, not unwired, so the rule was wrong rather than its
  wording. Nothing is lost — the fixture's real defect is still reported, and better,
  as "Inlet 'Unfed' is not connected" instead of blaming the sink. Original entry
  follows.

- ~~**Decide whether stream reachability should require a `Source`-SHAPED head.**~~
  Surfaced by the corpus A/B for `70b0f527a`, not theorised. With the graph
  widened, two reactive-bbq repositories now report `is a sink but has no
  upstream path from any source`. The message is LITERALLY true — tracing
  `TableOrderRepository` upstream gives `TableOrderEventSplit` (split) ←
  `TableOrder` (`event-sourced entity … as flow`) ← `RestaurantApp`
  (`application context … as router`) — there is no `Source`-shaped processor
  anywhere in the chain. Data enters that pipeline through an application
  context fed by users, not from a `source`.

  So `originates` (`StreamingValidation.scala`) asks "is this Source-shaped?"
  when the useful question may be "does this have no inbound edge in the
  graph?". Related hole in the arity mapping: `shapeForArity` sends
  (out ≥ 2, in = 0) to `Void` as degenerate, so a multi-outlet, no-inlet
  processor is neither a Source nor reported.

  **Reid ruled 2026-08-11: a head must be OUTLET-shaped, not `Source`-shaped.**
  Any outlet whose type matches the Connector's will do, and the processor may
  have MORE outlets besides — which is exactly what `Source`-shaped rules out
  and why reactive-bbq trips today. So `originates` becomes "has an outlet of
  the connector's type", not "is a Source".

  **CONFIRMED AND MADE EXPLICIT 2026-08-14:** *"a chain of outlet-connector-inlet
  MUST start with an outlet (Source, Merge, Flow, Split, Router), never a Sink
  (only has inlet(s))."* So the admissible head shapes are exactly the five that
  the arity table gives ≥1 outlet — and **`Void` is excluded too**, having
  neither port, which the enumeration does not say but the rule requires. The
  test is therefore "has an outlet of the connector's type", asked of the
  processor, not "is shaped `Source`".

  He also asked a question this item must answer before the change lands:
  *"sink with no upstream source probably means no connector connected?"* —
  i.e. whether the two reported repositories are genuinely UNWIRED (no
  connector reaching them at all), in which case the message is right and only
  its WORDING is wrong, or wired-but-not-from-a-`Source`, in which case the
  rule is wrong. **Determine which before writing code** — they need opposite
  fixes, and the corpus is the evidence.

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

- **BLOCKED ON riddl-models: two corpus tests are RED here and stay red until
  it lands its fix.** `RiddlModelsRoundTripTest` (16 of 189 models) and
  `Root2JsonCorpusTest` (173/189, needs ≥95%). **This is not a defect here and
  must not be "fixed" here.**
  Cause: `ccd278c00` taught the tell-addressing check to resolve `Id` aliases,
  which turned it ON for the spelling riddl-models actually uses, surfacing **49
  ambiguity Errors it had been hiding**. Verified by A/B — stash the fix and the
  corpus is 189/189 green — so the delta is exactly those 49.
  All 49 are corpus-side, in three classes, checked against riddl-models'
  sources rather than inferred from the messages: genuine two-id ambiguity
  (`CartsMerged {targetCartId, sourceCartId}`) needing `by`; actor fields
  legitimately of the same entity (`identityId` + `suspendedBy`) also needing
  `by`; and **wrong-entity aliases** — `nursing-workflow/types.riddl:18 type
  TaskId is Id(NursingContext.NurseShift)`, `radiology-workflow/types.riddl:27
  type ReportId is Id(ImagingExam)`, `member-enrollment/types.riddl:2 type
  MemberId is Id(Enrollment)`, `policy-lifecycle/types.riddl:8,14
  BeneficiaryId`/`RiderId is Id(LifePolicy)`.
  Task filed at `../riddl-models/task/2026-08-14-alias-fix-exposes-49-addressing-defects.md`
  with the full list and a corpus-wide sweep of 17 wrong-alias candidates.
  **Consequence for the next RC:** certification cannot be clean until this
  lands. `~/Code/ossuminc/bin/riddlc` is already staged at
  `2.0.0-rc.14-12-54d82288` so riddl-models can start.
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
