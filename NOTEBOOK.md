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

**Still open:** `Root2JsonCorpusTest` is red by standing policy (2 of 189
external models fail to re-parse: `reactive-bbq.riddl`, `fund-accounting.riddl`).
`Include`/`BASTImport`/`Nebula`/`ULIDAttachment` remain unrepresented and are
still excluded from the census by `NotRepresented` — the last reflectivity hole,
and the next task.

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

0. **`state X is <recordRef>` — deprecate the `is` spelling** (raised
   2026-07-28, spec confirmed same day). `EntityParser.state`
   (`EntityParser.scala:29`) reads
   `identifier ~/ (of | is) ~ recordRef ~/ stateBody.?`, so the record
   reference may be introduced by EITHER `of` or `is`. Every other
   definition uses `is` to introduce a BODY, and `stateBody` already
   does (`is ~ open ~ … ~ close`), so the `is` alternative here means
   one keyword doing two jobs in one production.

   **The 2.0 grammar is `of` only**, before the record reference:

   ```
   state <id> of <recordRef> [ is { <stateContents> } ]
   ```

   `stateBody` is UNCHANGED — it already supplies its own `is`, so
   nothing about the body moves.

   **`is` in the `of` position must still parse**, raising a
   `deprecation(...)`; dropping it outright would invalidate a large
   part of the test suite and the whole external corpus. It goes away in
   a later major. Note `deprecation` messages now surface under every
   `riddlc` command (see CLAUDE.md "Parse-time messages now surface"),
   so `.check` goldens will move. Touches: parser, EBNF grammar (+
   regenerate GBNF), and a deprecation test. Prettify already emits
   `of`, so no emitter change — confirm with a round trip.

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
