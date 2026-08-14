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

- **FLAKY CI GATE: `PerformanceBenchmarkTest` asserts a hard 100x cache speedup.**
  Reid, 2026-08-14 — do before rc.15. Found during the rc.14 certification.
  `language/src/test/scalajvm/.../PerformanceBenchmarkTest.scala:388-391`:
  ```scala
  assert(cachedFindTime < findTime / 100,
    s"Cache should provide 100x+ speedup (got ${findTime / cachedFindTime}x)")
  ```
  **It failed CI run #2278 with 78.4x** on a commit whose code is BYTE-IDENTICAL
  to the one that passed as #2277 (`git diff cbb7e53b4..9b9ffac36` over
  `*.scala *.sbt project/ .github/` is empty), and #2279 then passed again on the
  same code. It also passed in the local rc.14 certification. So the assertion is
  measuring shared-runner timing noise, not the cache.
  **Why it matters more than one red X:** this runs in `scala-build (JVM)`, which
  gates releases. A gate that fails ~1 run in 3 on machine noise trains everyone
  to disregard a red X — and the next real failure looks identical.
  **Fix direction, not yet decided:** assert a much weaker bound (the cache is
  clearly working at 78x, so the property under test is "caching helps", not
  "helps 100x"), or measure a monotonic property that does not depend on wall
  clock, or move the whole benchmark out of the release gate and into something
  advisory. Whichever is chosen, a timing threshold tuned to one machine does not
  belong in a gate. Note the sibling assertions in the same file
  (`types1.size == typeCount`) are deterministic and should stay.

- **Close the JVM/Native test gap: 729 cases run on JVM that never run on
  Native.** Reid, 2026-08-14, from the rc.14 certification. *"Testing on the JVM
  does not guarantee correctness on Native, and I can't believe there are ~800
  test cases that genuinely cannot run there."*

  **Measured, not estimated** — rc.14 certification from clean under a throwaway
  `--sbt-cache`, module order taken from the `tJVM`/`tNative` aliases
  (`build.sbt:538`, `:549`):

  | module | JVM | Native | gap |
  |---|---|---|---|
  | language | 668 | 343 | **−325** |
  | commands | 245 | 47 | **−198** |
  | passes | 1196 | 1040 | **−156** |
  | utils | 146 | 108 | −38 |
  | riddlLib | 122 | 111 | −11 |
  | testkit | 2 | 1 | −1 |
  | riddlc | 21 | 21 | 0 |
  | **total** | **2400** | **1671** | **−729** |

  **Three modules are 679 of the 729.** Start there, in this order:

  1. **`commands` (−198) is the alarming one, and probably the cheapest win.**
     245 JVM against 47 Native, and the module has NO `src/test/scala-jvm-native`
     directory at all — 14 shared test files and 7 under `scalajvm`. The JVM
     count matches the riddl-models corpus gate exactly, which means **our single
     largest regression net almost certainly runs JVM-only.** Confirm that first;
     if the corpus round trip can run on Native, that one change is worth ~200.
  2. **`language` (−325)** — 33 shared test files, 19 `scala-jvm-native`, 22
     `scalajvm`. **169 of this gap is already filed** as the abstract-suite item
     below (`TypeParserTest` and 12 siblings are abstract with concrete runners
     only in `JVMTests`/`JSTests`, so they move JVM and JS but not Native). That
     part is a pure wiring fix — add Native runners — and needs no rewriting. The
     remaining ~156 is the 22 `scalajvm` files.
  3. **`passes` (−156)** — 26 shared, 139 `scala-jvm-native`, 29 `scalajvm`. This
     module already does the right thing at scale, so the residue is likely
     genuinely JVM-bound; audit it last.

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


- **A message ref must be able to name its VALUE, not just its type — DESIGNED
  2026-08-14, scheduled for 2026-08-15. NEXT UP.**
  **Design: `docs/superpowers/specs/2026-08-14-message-value-source-design.md`.**
  Read it before planning — it supersedes the summary below, which is kept only
  so this entry stands alone.

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

- **GAP: 13 shared `language` parser suites — 169 test cases — have NEVER run
  on Native.** Found 2026-08-13 by chasing a ONE-test shortfall in the
  instance-identity certification (predicted Native +68, got +67).
  They live in `language/src/test/scala/.../parsing/`, which reads like "all
  three platforms", but each is **abstract** and its concrete runners are
  declared only in `scalajvm/.../JVMTests.scala` and
  `scalajs/.../JSTests.scala`. There is no `NativeTests.scala`. Verified by
  diffing the suite names in the JS and Native `language` rows of the
  certification log — they overlap almost nowhere.
  Counts (`in {` per suite): `StatementsTest` 52, `TypeParserTest` 37,
  `HandlerTest` 19, `ParsingTestTest` 19, `CommonParserTest` 9,
  `ProjectorTest` 8, `StreamingParserTest` 7, `ApplicationParsingTest` 6,
  `ModuleTest` 4, `MetaDataTest` 3, `RepositoryTest` 2, `TokenParserTest` 2,
  `NebulaTest` 1.
  **Fix is probably one file** — a `scalanative/.../NativeTests.scala` mirroring
  the other two. Do it as its own change with its own certification, NOT
  alongside a feature: it will raise the Native floor by ~169 in one step, and
  if any parser behaves differently on Native (`String` handling and
  `fastparse` are the plausible risks) the failures must be attributable to
  this change alone.
  The trap is already documented in `.claude/skills/rc/SKILL.md` as the
  "reverse trap"; what was missing was anyone measuring it. A floor is a total,
  and a total cannot say what is absent from it.

- **UNDECIDED: are `initiate`/`terminate` legal inside a SAGA STEP?** Raised
  three times during the instance-identity plan (2026-08-13) and never ruled,
  so it is filed rather than lost. **They are legal today by DEFAULT, not by
  decision:** `checkInstanceEffectScope`
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

- **NEW CHECK (Reid ruled it, 2026-08-13; build it as its own task):** a
  `let x = initiate …` whose id is **never subsequently referenced** draws a
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

- **NAME-MATCHING SURVIVOR: `isIdForEntity`** (`ValidationPass.scala:2343`,
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

- **Decide whether stream reachability should require a `Source`-SHAPED head.**
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
