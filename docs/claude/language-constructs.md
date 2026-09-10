# RIDDL Language Constructs — detailed reference

Per-construct detail split out of `CLAUDE.md` on 2026-09-10, when that file
exceeded the 150k character limit at which it stops being loaded in full.

**What lives here vs. what stayed in `CLAUDE.md`.** This file holds the
*reference* half of each construct: its syntax, AST shape, field declarations,
BAST tags and `FORMAT_REVISION` numbers, JSON keys, and the reasoning behind its
design. `CLAUDE.md` keeps the *trap* half — the rule you could get wrong, the
"do not restore this", the anti-pattern — as a compact bullet under **AST /
Language Internals**, each pointing here.

**Read this when you touch one of these constructs.** The split was made on the
principle that reference material is consulted during a named task (you know you
are editing `terminate`), while a trap must fire unprompted and therefore cannot
be moved out of the always-loaded file. If you find yourself needing a fact from
here that you had no reason to look for, that fact belongs back in `CLAUDE.md`.

Everything here is as authoritative as it was in `CLAUDE.md`; nothing was
softened in the move. The Computational Model
(`../../../RIDDL-Computational-Model.md`) remains the authority for any lowering
decision.

---

## Standard module, envelopes and error sinks

- **The predefined `Riddl` standard module** (`language/.../
  PredefinedModule.scala`) is readable RIDDL in a string constant, parsed ONCE
  and cached. It holds `type Drain is Anything` plus the terminators
  `BottomlessPit` (sink, inlet `hole`) and `ForeverEmpty` (source, outlet
  `void`), directly in the module (no domain/context — `ModuleContents` is
  `NebulaContents`).
  **NEVER inject it into a user's `Root.contents`.** The ONLY seam is
  `SymbolsPass.postProcess`, seeding `predefinedSymTab`/`predefinedParentage` —
  separate maps on `SymbolsOutput` that lookups fall back to. Separateness is
  load-bearing: several public APIs (`AnalysisResult.domains/streamlets/…`,
  `UseCaseWitnessPass`, `foreachOverloadedSymbol`) ENUMERATE
  `parentage`/`symTab`, and seeding the shared maps leaks the standard library
  into "all X in the model". A colliding user definition wins structurally (its
  table is consulted first) — no ambiguity, no message.
  **`Envelope`** (rc.10+) carries a message's metadata, selected by `option
  message_envelope("Riddl.Envelope")`. Fields are the **CloudEvents v1.0 context
  attributes** with ONE forced deviation: CloudEvents `id` is spelled
  **`messageId`**, because RIDDL requires identifiers of >= 3 chars and the
  standard module must validate clean. Deliberately **no `data` field**: in RIDDL
  the payload IS the message, already modelled and typed, so Envelope is the
  metadata AROUND a message, not a wrapper containing one. The option is
  **scope-inherited** (`Seq.empty` validParents — resolved by walking UP the
  parent chain), so declaring it on a context covers every entity in it. Opt-in
  by design: RIDDL specifies meaning, not representation, so how the attributes
  ride (CloudEvents JSON, Kafka headers, gRPC metadata, or nothing in-process)
  stays the generator's choice.
  **`GeneratorError`** (`origin`, `kind`, `detail`, `occurredAt`) is the shape
  every generator sends to the inlet marked `option error-sink` — an exhausted
  saga undo, a dead-lettered adaptor message, a projector's poison event. **The
  name states the SOURCE** (it was `HardError` until 2026-08-01, when
  `Operations` was also withdrawn): the standard library owes a generator the
  SHAPE of a notification and a way to NAME its destination, nothing more, so
  there is deliberately **no predefined receiver**. An error-sink inlet must
  accept it — directly or via an alternation including it — else it is an Error,
  because a generator has nothing it can send there. **A missing error-sink is a
  `Missing` warning, NOT a CompletenessWarning**: `isIgnorable` is `severity <
  CompletenessWarning`, so Completeness asserts STRUCTURAL incompleteness
  (unfed inlets, unreachable sinks) while "has not said where hard errors go" is
  the "has no author" family. Emitting it as Completeness turned **thirteen
  unrelated suites red**.
  Both records are legitimately **unused inside the module** — the design, not a
  defect — so `PredefinedTerminatorsTest` asserts exactly which are unused, by
  name; widen that list when adding another, never loosen it.
  All exemptions (A31 cardinality, unattached/isolated/reachability, handler
  completeness) test REFERENCE IDENTITY via `PredefinedModule.isPredefined`,
  never a name. A port typed `Anything` is connector-compatible with every type.
  `language/input/predefined/riddl-standard-module.riddl` is a verbatim copy so
  the CI grammar validators cover it; `PredefinedModuleSourceTest` fails on
  drift.

## Handler clauses — binding, residual messages, quiescence

- **On-clause message binding (A55)** — `on foo: command Foo { … }` optionally
  binds a local name to the handled message. The `:` is ordinary TYPE ASCRIPTION
  (same rule as `let x: T = …`), so the parser reuses `HandlerParser.maybeName`.
  `binding: Option[Identifier]` sits on `OnMessageLikeClause` and BOTH concrete
  nodes, immediately after `from` and **without a default** —
  `@JSExportTopLevel` requires defaulted params TRAILING and `contents`/`metadata`
  are defaulted. `id`/`format` stay derived from `msg`. Bare `foo` denotes the
  whole message; `foo.field` is an ordinary path walk. See "Validation Specifics"
  for how it resolves.

- **`on other as x [: <envelope>]` (A57)** — binds the residual message's
  ENVELOPE, not a message: the clause names none. `OnOtherClause` gains
  `binding: Option[Identifier]` and `envelopeType: Option[TypeRef]`, both before
  `contents` and WITHOUT defaults (`@JSExportTopLevel` needs defaulted params
  trailing — same rule as A55). `x`'s type is the ascription when written, else
  whatever `option message_envelope` names in scope
  (`ResolutionPass.envelopePathFor`).
  **The ascription RESTATES the option, never overrides it.** Three Errors in
  `checkOnOtherBinding`: binding with no envelope in scope, ascription with no
  envelope in scope, ascription contradicting the option. A per-clause override
  would mean reading one clause tells you nothing about its siblings — exactly
  what scope inheritance prevents.
  **The type is BARE after the colon** — no keyword. `message` would be untrue
  and `type` is correct only because it is vacuous; the colon already says a type
  follows.
  **`OnOtherClause` must NOT join `OnMessageLikeClause`** — that is what keeps it
  out of `UseCaseWitnessPass`'s index; a clause matching every type would witness
  every step.
  **Rendering lives in `Declaration.ascription`, NOT the clause's `format`.** The
  prettifier reads the former via `openDef`; `format` alone makes prettify
  silently DROP the binding on every round trip. Shipped as a bug for exactly one
  commit; `OnOtherEnvelopeRoundTripTest` pins it.

- **`on quiescence <window>` — the clause that fires when NOTHING arrived**
  (2026-09-07, Reid's rulings on riddl-models' temporal-semantics task).
  `OnQuiescenceClause(loc, window: LiteralString | ValueRef, contents, metadata)
  extends OnClause`. ONE clause kind, instance-scoped: the clock restarts on
  every handled message, and inside a `State`'s handler it is armed only while
  that state is active. Legal on ANY handler-bearing processor; an Error inside a
  Correlation (`handler-quiescence-in-correlation`), which bounds itself with
  `times out after`; at most one per handler
  (`handler-quiescence-duplicate`).
  **The window is a literal duration string OR a bare path to a Duration-typed
  value** — constant, state field, message field. The literal goes through the
  SAME `checkPreciseDuration` the correlation timeout uses (vague and
  non-positive are Errors). A `let` cannot be named from a clause header because
  lets are clause-local statements; a stated limit, not a gap.
  **Look the value window up with `oqc +: parents`.** ResolutionPass prepends the
  node being processed before resolving, so the header reference is recorded
  under the CLAUSE, and a lookup keyed on the handler alone misses it silently —
  the same `c +: parentsAsSeq` the correlation's timeout block needs. Found by a
  failing test, not by reading.
  **It is an EFFECT block**: `yield`/`tell`/`send`/`terminate`/`morph`/`initiate`
  are legal, unlike `on activate`/`on passivate`. Event-sourcing's R3/R4 are
  UNCHANGED, so in an event-sourced entity the clause changes state only through
  a yielded event — which is also what keeps replay from re-firing the timer: the
  timed-out fact is in the journal. Rehydration re-arms from the last handled
  message's timestamp; the mechanism (durable timer, scheduler, poll) is the
  generator's.
  `Keyword.quiescence` is in `anyKeyword`/`allKeywords` but NOT
  `definitionKeywords`, so `quiescence` stays a legal identifier.
  **`Declaration.ascription` renders the window** — the `on other as x` lesson:
  rendering only in `format` makes prettify DROP it. BAST: `NODE_ON_CLAUSE`
  discriminator byte **7**, window as a tagged value before the contents count,
  reader's fabricating fallback replaced by a throw, **`FORMAT_REVISION` 24**.
  JSON: `OnClauseDto.kind = "quiescence"` + `window` (in `knownKeys`).
  `Finder.fieldChildren` yields the window.

## Projector correlations (A70)

- **Correlations in projectors (A70)** — `correlation <id> by <k>[, <k>…] yields
  command <C> is { <handler> } times out after "<duration>" { <statements> }
  [with { … }]`. A keyed accumulation of several events into one command the
  Repository handles. **Semantics live in `../RIDDL-Computational-Model.md` §6.2
  and §6.5–§6.8 and are NOT restated in the code** — that document is the
  authority for any lowering decision.
  **`yields` names a COMMAND** (Reid, 2026-08-12; it was `yields record <T>` for
  one day). A projector's only output is a change to a repository, and a
  repository is changed by handling a command. The record form could never work:
  a handler clause takes a `messageRef`, which is the four real messages only
  (A9b), so **no `on` clause could name what the correlation produced** — which
  is why the first design had to INFER acceptance from a command that *held* the
  record. Naming the command deletes the inference. Enforced in two places on
  purpose: the wrong KEYWORD dies in the grammar (`commandRef` in
  `ProjectorParser`), while `yields command Foo` naming a non-command is an Error
  from `ValidationPass` — the only place with the resolved referent, and a
  parse-time `error()` there would preempt the whole pass chain.
  **The timeout clause is MANDATORY and is grammar, not metadata.** Designed as
  an optional `else` plus `option timeout(…)`, it left one question unanswerable
  — what an unbounded correlation means — and needed three warnings to paper over
  it. Reid made it mandatory, deleting all three states instead of diagnosing
  them: §4.2 calls options *advisory*, and a bound that MUST fire a block is not.
  Consequences: **no timeout inheritance from the Projector** (`RecognizedOptions`
  is untouched by this feature), the duration is a `LiteralString` still
  duration-VALIDATED via `DefinitionValidation.checkPreciseDuration` (so `times
  out after "banana"` is an Error), and an empty block is a parse error — `do
  "nothing"` is the discard idiom.
  **Keys are stored AS WRITTEN and never canonicalized**: `Definition.equals` is
  structural and §6.5 makes identity the full tuple, so sorting them would
  silently equate two different declarations. The exact OPPOSITE of
  `EntityIntention.canonical`, which sorts so write order cannot make two
  identical entities compare unequal. Prettify, BAST and JSON all preserve order,
  each with a test.
  **The effect ban binds FOLDS only.** Fold purity is what makes re-runs safe
  (§6.5); the timeout block exists to have an effect (§6.7). `CorrelationTest`
  pins both sides — without the "legal in the timeout block" case, a ban wrongly
  applied to the whole correlation would still look green.
  Two pre-existing projector checks (needs its own record type; exactly one
  handler) assumed folds live in one top-level handler and are SKIPPED when
  correlations are present.
  **The repository-accepts-it rule is a COMPLETENESS warning, not an Error**
  (Reid, overriding A70 as written): a repository lacking the `on command` clause
  is under-specified, not self-contradictory. `???` is exempt. The test is plain
  identity on the resolved `Type` (`eq`, not by name — two contexts may each
  declare a `RecordFulfillment`).
  **The unemitted-event warning does NOT use `MessageFlowPass`** — depending on
  it would reorder the standard passes. `checkCorrelationEventSources` sweeps the
  root once in `postProcess`, GATED on a correlation existing. An `Outlet` typed
  with the event counts as emitting it, so a `???` source declaring the port is
  not reported; adaptor translations deliberately do not count.

## Processor instance identity — `Id(P)`, `self`, `initiate`, `terminate`

- **Processor instance identity (2.0)** — `Id(P)`, `self`, `initiate`,
  `terminate`, and structural `tell` addressing. Five constructs, one gap:
  **RIDDL could describe processors but not INSTANCES of them.**
  - **`Id(P)` names any Processor**, not just an Entity (Adaptor, Context,
    Entity, Projector, Repository, Streamlet). `Id(entity Order)` is CANONICAL,
    bare `Id(Order)` the shorthand — `UniqueId.kindKeyword` stores the keyword
    *as written* (a `String`, not an enum, so prettify is byte-exact without a
    mapping table), and `TypeValidation` makes it **tell the truth**: a keyword
    contradicting the resolved referent's kind is an Error, because a wrong
    keyword is worse than no keyword — a reader believes it. A bare `Order` could
    be a context, a message or an entity, which is why the keyword was kept.
  - **`Id(P)` is RUNTIME identity, NOT the definition ULID** (CM:2523, which is
    model-time identity of a *definition*): two `Order` instances share one ULID
    and never an `Id(Order)`. `isAssignmentCompatible` is deliberately UNCHANGED
    (still `String_`/`Pattern`-compatible) — the value is opaque and
    system-generated, so a BUSINESS key belongs in `on init`'s parameters and
    lives in state.
  - **`self`'s type is a synthesized `Aggregation`, and that is load-bearing.**
    Because it is an ordinary record, `let me = self` then `me.id` resolves
    through the SAME `ValueRef` path walk every other value uses — so no
    resolution rule anywhere has to know `self` exists; a bespoke node would have
    needed special-casing at each site. Consequence: the type is not
    user-nameable (`self.id` is `Id(Order)` in an Order handler), so `let me: T =
    self` has no `T` to write and `self` is not assignable into a message field —
    pass `self.id`. `SelfValue.fieldNames` is a CLOSED set (`id`, `version`);
    adding one is a language change, and the admission test is **runtime-only** —
    anything a generator can know statically it should inline, which is why
    `version` is in and `isClustered` is not.
    `enclosingProcessorOf` terminates at `Function` AND `Saga` — a Saga sits
    inside a Context routinely, so without the second terminator `self` in a saga
    step silently typed as the enclosing Context's identity.
  - **`initiate` supplies the invocation `on init` always lacked** — it does NOT
    add a second way for an instance to exist. Construction still completes only
    when `on init` finishes; CM:999's "activate on first message" is rehydration,
    not creation. Without it no `Id(P)` could come into being and the whole
    addressing story would be inert.
    **`initiate` is a VALUE (it yields the new `Id(P)`); `terminate` is a
    STATEMENT (termination produces nothing).** That asymmetry is why their bans
    live in validation, not the parser: `value` carries no `StatementsSet` to
    gate on, so parser-gating one and validating the other would split one rule
    across two layers. `on init`/`on term` gained parameter lists; arity and
    argument types are checked in `ValidationPass` (`checkInitiate`/`checkTerminate`),
    never the parser (a
    parse-time `error()` preempts the pass chain). Both fold an Entity's STATE
    handlers in when looking for the clause, as `validateAsk` does — `on init`
    commonly lives inside a `State`.
    **An `initiate` whose id is never referenced draws a plain Warning — NOT an
    Error, and NOT gated behind `showCompletenessWarnings`.** Recorded so it is
    not re-litigated: a self-terminating worker legitimately has an unused id and
    an Error would make that pattern unwritable; an unstated fate is
    under-specification (warns) rather than self-contradiction (errors); and it
    is ungated because it is locally decidable from the clause body alone. **The
    work is the escape-route analysis, not the message**: an id escapes by being
    `set` into state, passed as a `tell` argument, passed to `terminate`, yielded
    in an event, or `put` to a repository, and the sweep must be conservative
    enough that no legal model is rejected. `UnusedInitiateIdTest` pins all five
    plus the nested-`when` case.
    **`terminate <target> [with (args)]` names an INSTANCE; `target` is a VALUE
    typed `Id(entity E)`** (Reid, 2026-08-15). `TerminateStatement.target: Value`
    REPLACED `processor: ProcessorRef` — the old form said which KIND of thing
    ended, never which one, so `terminate` was the one rc.14 construct riddlg
    could not lower at all (it emitted an `AI FILL` marker rather than guess,
    correctly: `terminate` DESTROYS). The entity is DERIVED from the target's
    type, so ref and id can never contradict and no truth-check is needed —
    contrast `UniqueId.kindKeyword`, which needs exactly one. Arguments sit
    behind **`with (…)`**, not bare parens: `terminate order.id("x")` reads as a
    call on `id`, and `with` is the established idiom (`morph … with`, `require …
    with`). Empty list ⇒ no `with` clause; `terminate t with ()` prettifies away.
    **`on term`'s parameters are pure PAYLOAD.** A leading `Id(...)` parameter
    was an addressing convention detectable only BY POSITION — riddlg asked
    whether address and payload were distinguishable in the AST and the honest
    answer was no. Separate fields now; `self` is live for the whole clause body,
    so a clause needing the instance it is ending reads `self.id`. The asymmetry
    with `initiate` is the design: `initiate` names a TYPE and yields an id;
    `terminate` consumes an id and yields nothing.
    **Both are ENTITY-ONLY, and that is an EXPLICIT check, never a consequence of
    the type system.** `Id(P)` KEEPS its widening to all six processor kinds,
    because **a singleton's `Id` is how you SEND IT MESSAGES** (Reid,
    2026-08-15) — it denotes the singular DEPLOYMENT, and addressing it means
    "select the right shard/partition and forward". So `Id(context C)` is a good
    value that simply is not a legal thing to end, and only
    `reportNotInstantiable` says so. **Do not "simplify" this by narrowing
    `Id`.** Two Errors in `checkTerminate`: target's type is not a `UniqueId`;
    target is an `Id` of a non-Entity. It stays SILENT when the type is
    undeterminable (a bare `let n = 5`, an unascribed `prompt(…)`) — reporting
    there is reasoning from absence, the conservative rule A20 also follows. Note
    `valueTypeExpr` does NOT surface a `let`'s declared PREDEFINED type (`let n:
    Integer = 5` yields `None`), which is why the "not an Id" test uses bare
    `self`.
    **`resolveIdTarget` needs TWO lookups and the second is not optional.** The
    refMap holds only paths that were WRITTEN, but `valueTypeExpr` SYNTHESIZES a
    `UniqueId` for `initiate` and `self.id` carrying a fully-qualified `pathOf(p)`
    with no refMap entry — so a refMap-only lookup made every `terminate` whose
    target came from `initiate` or `self` resolve to `None` and skip its checks in
    silence. Falls back to `symbols.lookup`. Found by instrumenting, not reading.
  - **Addressing is STRUCTURAL: the address is the message's field typed
    `Id(target)`**, found without annotation; `by <field>` only DISAMBIGUATES
    when more than one qualifies. Candidates match by **resolved identity** (`eq`
    through the refMap), never by the path's last segment — two entities named
    `Order` in different contexts must not collide, and the name-matching version
    turned a legal model into a false ambiguity Error. The field's `UniqueId`
    must be looked up with its OWNING `Type` as the refMap key's parent (`Pass`
    pushes a `Type` for its own children), which is why `fieldsWithOwner` carries
    the owner along.
    Zero candidates is a **CompletenessWarning and only for an Entity target**:
    an entity is the only multiply-instantiated processor, and the corpus holds
    7,556 `tell`s against **7** `Id(...)`-typed fields, so an Error would have
    condemned essentially every model that exists. Ambiguity IS an Error — a
    contradiction, not an omission.
    **The candidate test follows ALIAS CHAINS but never NESTING** (Reid,
    2026-08-14). A field typed `OrderId` where `type OrderId is Id(Order)` IS an
    address — riddl-models' documented house style, and until `ccd278c00`
    `isAddressFieldFor` matched `UniqueId` alone, recognising only the rare inline
    spelling (72 of 86 findings in reactive-bbq were false; it aborted their
    `checkAll`). But `result R is { thing: ThingBase }`, where the NESTED record
    carries the id, stays flagged: descending into an aggregate's fields is an
    unbounded search with no principled stopping point, so **the id must be a
    field of the record actually named.** Renaming is followed; containment is
    not.
    **Both alias walks carry a visited list, and the reason is a real crash:**
    `type A is B` / `type B is A` sent `fieldsWithOwner` into infinite recursion
    in rc.14 (`StackOverflowError` against the released binary), surfacing as
    `[severe] Exception Thrown` with no line number. Use reference identity
    (`eq`), NOT a `Set`/`contains` guard — `Definition` overrides `equals`
    structurally, so a set would fuse two distinct identical alias declarations
    and truncate a legitimate chain.
    **Fixing the alias case cost the corpus 49 Errors it had been hiding**, in 16
    of 189 models — the fourth reminder that a green corpus is evidence about the
    corpus. All 49 were corpus defects: genuine two-id ambiguity needing `by`,
    actor fields legitimately of the same entity also needing `by`, and
    **wrong-entity aliases** (`type TaskId is Id(NurseShift)`) that no `tell` had
    ever exposed.
  - **`initiate`/`terminate` are effects** — banned in a function body (pure,
    A26), in `on activate`/`on passivate`, and in a correlation fold (A70/§6.5).
    The fold ban lives in exactly ONE place (`validateCorrelation`), not
    duplicated into `checkInstanceEffectScope`, so a fold offender is never
    double-reported. Every ban is wired into `checkStatementScopes`, **not**
    `validateStatement` — the latter never sees statements held in a FIELD
    (`when`/`match`/`foreach`), the trap two tasks of this plan fell into.
  - **BAST**: value tags 8 = `Initiate`, 9 = `SelfValue`; statement sub-kind 20 =
    `terminate`, at `FORMAT_REVISION` **15**. Sub-kind 20's PAYLOAD then changed
    at revision **18** — it now begins with a `writeValue` where it began with a
    `writeProcessorRef`. Not interchangeable, so an older reader MISALIGNS rather
    than failing cleanly; that is the whole reason the revision gate exists.
  - **JSON**: `TerminateStmtDto`'s `processor`/`processorKind` pair became a
    single `target` at the same time. `JsonModel`'s readers reject no unknown
    keys (BACKLOG § 1), so a producer still emitting the old pair has them
    SILENTLY DROPPED and gets a null `target` — recorded on the DTO, because a
    stale example in a machine-facing document is a data-loss bug.

## Typed holes (A20)

- **Typed holes (A20)** — `prompt("...") as <type>` ascribes a type to an
  AI-computed value: the type is known and checkable at compile time, the
  computation is prose an AI fills in at generation time. The seam between
  RIDDL's deterministic tier and its AI tier. `PromptValue` gains `typeEx:
  Option[TypeExpression] = None` — one node, not two, because the forms differ by
  an `Option` and not by wire shape; the default is legal ONLY because it is
  trailing. Unascribed `prompt(...)` is unchanged. Legal in every position an
  ordinary `Value` can occupy — `let`, `constant`, a constructor argument, `set`,
  and a `when` condition (which must resolve to `Boolean`).
  **The ascription's type reference RESOLVES, like any other TypeExpression.**
  `ResolutionPass.resolveValue`'s `PromptValue` arm used to say "no references"
  and do nothing, so `prompt("x") as Nonexistent` validated clean while naming a
  type that need not exist. It now calls the same `resolveTypeExpression` every
  other position uses, which recurses `Cardinality` wrappers for free and records
  the resolved Type in `usedBy`, so a Type named ONLY by an ascription is not
  wrongly flagged unused.
  **The ascription RESTATES the position's already-known type; it never OVERRIDES
  it.** `let x: Real = prompt("...") as String` is an Error (contradiction), not
  a coercion — checked by the same `checkValueType` a `set` already used.
  Agreement is silent, since writing the type out lets the hole read standalone.
  **The comparison is deliberately SYNTACTIC, not resolved-type**, mirroring
  A57: `constant G: Real = prompt("...") as Score` (`type Score is Real`) is
  still an Error, because RIDDL treats a declared alias as a distinct name, not a
  transparent synonym — a resolved comparison would swallow exactly the
  contradiction this rule exists to catch. `typeAscriptionName` RECURSES through
  the four `Cardinality` wrappers (discarding them rather than folding them into
  the name) and compares only the LAST path segment on both sides — both fixed
  2026-08-15 after review found false positives on `as OrderId?` and on a
  qualified restatement (`Common.OrderId`), and a false negative where two
  differently-aliased `Optional`s compared equal by `kind` alone. Last-segment
  comparison is a KNOWN, accepted limitation shared with `checkOnOtherBinding`:
  two differently-scoped types sharing a simple name compare equal here.
  **A `constant` with a `prompt` value needs no ascription at all** — its own
  type declaration supplies it, so `constant G: Real = prompt("...")` is the
  idiomatic form and `as Real` is legal but redundant. Where nothing else states
  a type (a bare `let x = prompt(...)`, a bare constructor argument) the
  ascription is the ONLY source — doing real work, but still describing what is
  already true about the hole, never coercing it.
  **The untyped-seam warning is deliberately CONSERVATIVE** (Reid, 2026-08-15):
  it fires only on an unascribed `let x = prompt("…")` with no declared type, and
  **nowhere else**. `when` is wired to `Boolean`; constructor arguments, `set`,
  `put`, `return` and `require … with` stay SILENT (BACKLOG § 1 as a decision to
  revisit, not a ruling). The evidence was a count: all 288 `prompt(` uses in
  riddl-models already carry a type (273 written unprompted; the other 15 are
  `when` conditions), so the warning's whole value is for future code and its
  whole risk is firing on correct code. **"We did not wire this position" is not
  the same fact as "the language cannot type this position", and only the second
  deserves a diagnostic.**
  **`Currency` cannot appear bare in an example** — a predefined type requiring a
  `country` argument (`Currency(USD)`), so `prompt("...") as Currency` does not
  compile, and it does NOT resolve to `Real` underneath; it is its own distinct
  `PredefinedType`. Several early A20 examples used it and are wrong.
  **BAST/JSON**: rides `FORMAT_REVISION` 18 (the bump numeric literals already
  spent), not a new bump.

## Entities, processors and stream shapes

- **Entity intentions (rc.10)** — six keywords written BEFORE `entity`, in three
  INDEPENDENT groups, mutually exclusive within a group: role (`aggregate`),
  consistency (`consistent` | `available`), persistence (`event-sourced` |
  `persistent` | `transient`). `Entity.intentions: Seq[EntityIntention]`; enum +
  companion at `AST.scala:4144`.
  **They are grammar, not options, on purpose.** They were `with { option
  event-sourced }` until 2.0, but CM §4.2 calls options advisory ("honored if
  possible"), and a hard Error keyed off advisory metadata is a category error —
  see `checkEventSourcing`. The old `option` spellings still parse, deprecated.
  `persistent` replaces the uninformative `value`.
  Two from one group is an **Error, not a parse failure**, so the message can
  name both. `event-sourced` sits in persistence because it IMPLIES persistent.
  Any order parses; the parser stores them via `EntityIntention.canonical`
  because **`Definition.equals` compares this field** — write order must never
  make two identical entities compare unequal. Prettify emits `canonicalOrder`.
  **Four event-sourcing rules are Errors** (`ValidationPass.scala:1865`), because
  replay must reproduce the same state changes: R1 every handled command declares
  `yields`; R2 every yielded event has an `on event` clause; R3/R4 no
  `set`/`morph` outside handling one of the entity's OWN events. R1/R2 read the
  `yields` DECLARATION on the command's type, never `yield` statements in a body.
  Two migration traps: `yields` exists ONLY on the kind-first form (`command X
  yields event Y is {…}`), so type-first commands must be reshaped; and R3
  forbids `set` in `on init` while an empty body is a parse error, so the idiom is
  `on init is { yield event Created }` plus an `on event Created` clause doing the
  mutation.

- **Unified processor model** — every `Processor`
  (Context/Entity/Projector/Repository/Adaptor + the generic keyword) is
  port-bearing: `Inlet`/`Outlet` are in `OccursInProcessor`, and
  `WithInlets`/`WithOutlets` are mixed into the `Processor` base. Each carries
  `ascribedShape: Option[StreamletShape]` (None ⇒ derived from arity via
  `arityShape`/`effectiveShape`). Surface: `[<intention>] context <id> [as
  <shape>] is {…}`, `streamlet <id> [as <shape>] is {…}`. Old streamlet shape
  keywords are deprecated aliases; `StreamletShape.fromKeyword` canonicalizes
  synonyms (cascade→Flow, fanin→Merge, broadcast/fanout→Split). `Context` has
  `intention: Option[Intention]` (Application/External/Gateway/Service).
  Shape/intention participate in `Definition.equals`, so keep their `loc` at
  `At.empty` on every surface (parser/BAST/JSON).

- **`streamlet` is the generic keyword; `processor` is its deprecated original**
  (2026-08-31, [5.1]). Every other kind of processor names a THING — `entity`,
  `repository`, `projector`, `adaptor`, `context` — so `processor` named the
  ABSTRACTION and did not match the AST node it has always built. Both spellings
  run through ONE parser, build the identical `Streamlet`, emit
  `stream-processor-keyword` for the old one, and prettify to `streamlet`.
  **The alternation is FACTORED — one `.!` capture across both keywords, not two
  branches.** `Keywords.keyword` ends in a cut, so whichever branch matched first
  would win outright and make the other unreachable. Same hazard `bastImport` and
  `ulidAttachment` document.
  **`AST.Streamlet.format` and `RiddlFileEmitter.openDef` are the same decision
  written twice** and had to move together. Canary BOTH when changing either.
  **The AST hierarchy is untouched**: `Streamlet` is the concrete case class,
  `Processor` the port-bearing supertype. Renaming the keyword renames neither,
  and the two have been confused here before at real cost.
  **The shape-keyword deprecation's message moved with it** — it now says
  `streamlet X as flow`. Pointing an author from one deprecated keyword at
  another is worse than saying nothing.
  Corpus: **242 declarations in riddl-models, 28 in riddl-examples**, all
  mechanically fixable (`validate --fix --fix-rule stream-processor-keyword`). A
  LOOSE grep scores 455; the 213 extras are prose inside string literals — 195 of
  them `error "Unexpected message for processor X"` — and must not be edited.
  Second time in two days a keyword-that-is-also-an-English-word inflated a
  corpus count; **grep the declaration SHAPE (`^\s*processor <Id>`), never the
  bare word.**

## Values and literals

- **Numeric literals** — `NumericLiteral(loc, text)` in the `Value` and
  `Comparand` unions, accepting `[+-]? digits [. digits] [(e|E) [+-] digits]`. No
  digit separators, no radix prefixes.
  **The text is stored AS WRITTEN and that is the whole design.** `1.50`, `007`,
  `+3` and `2E+8` are not recoverable from a parsed `Long`/`BigDecimal`, so a
  parsed payload would make prettify diverge from source on first use. Same
  reasoning as `UniqueId.kindKeyword` and correlation keys. It also keeps
  `BigDecimal` off the Native and JS paths, and needs one BAST tag (value **10**,
  comparand **3**) rather than two. **JSON stores it as a `ujson.Str`, never a
  `ujson.Num`** — `ujson.Num` is a Double and would silently turn `1.50` into
  `1.5`. A JSON-identity fixed-point test cannot catch that, because a
  consistently-mangled value is still a perfect fixed point; assert the text.
  **`count > 5` now parses, REVERSING A28's deliberate narrowing.** `Comparand`
  was ref-only on purpose, "so magic-constant comparisons cannot be constructed
  at all" — reversed 2026-08-14 on the evidence that the whole 189-model corpus
  contained exactly ONE constant, so the rule had no uptake to protect (plausibly
  because naming a number meant quoting it). The intent survives as a
  StyleWarning whose population started at zero. `count > true` is still a parse
  error: booleans are atoms, not comparands.
  **`Integer` is signed (any whole number), `Whole` is `>= 0` (counting),
  `Natural` is `>= 1` (ordinal, excludes zero)** — Reid, 2026-08-14. Until then
  the three had NO definition anywhere: no scaladoc, no language reference, no CM
  entry, so the check had nothing to enforce. Documented at
  `AST.scala:2518-2530`; **a check cannot enforce a rule the language never
  states.**
  **Literals are held STRICTER than references, deliberately.**
  `NumericType.isAssignmentCompatible` (`:1912`) lets ANY numeric accept any
  other and STAYS that way — `let x: Nat = someRealField` is unchanged. Only a
  literal, whose value the compiler can see, is range-checked
  (`checkNumericLiteralConformance`, and only on a `Constant`; a `ValueRef` is
  untouched). `NumericLiteralConformanceTest` pins the loose side so a later
  "tidy-up" reddens instead of silently changing behaviour far beyond literals.
  The fractional-value check is reported BEFORE the range checks — a range
  message for `1.5` would be true but useless next to "has a fractional part".
  **`Bool extends IntegerTypeExpression extends NumericType`**, so any check
  matching `IntegerTypeExpression` also catches Boolean-typed values — put an
  explicit `Bool` arm first, or a Boolean constant is told it "requires a whole
  number".
  **Never call `asLong` in a match guard.** It is `text.toLong` and the parser
  accepts unbounded digit runs, so a 20-digit literal throws
  `NumberFormatException` *inside the guard* and surfaces as `[severe] Exception
  Thrown` with no line number. Use `asBigDecimal` or test the text.

- **`Constant` holds four kinds, and prettify emits `:`**. `ConstantValue =
  LiteralString | NumericLiteral | BooleanLiteral | PromptValue` — a narrowing of
  `Value`, defined the way `Comparand` is. Deliberately NOT the full union, which
  would admit `Call`, `Ask` and `Initiate` in a constant. The `PromptValue` arm
  is a **typed hole**: the constant declares the type and the computation is
  prose, so it needs no `as T` (A20 above).
  **There was never any parser work for the separator.** `CommonParser.is` (`:38`)
  is `StringIn("is","are",":","=").?` and has always accepted the colon, and
  omission. All spellings are legal, none warns, and prettify emits `: `.
  **The quoted numeric/boolean form is CONSUMED by the parser**, not merely
  deprecated — that is what makes its `autoFixable = true` honest and the round
  trip converge, exactly as `ConnectorOptionToIntention` does. A deprecation
  claiming `autoFixable` while prettify re-emits the old spelling is a lie a
  migration tool will act on.

- **`empty` — the minimum-cardinality inhabitant of a type (rc.23+).**
  `EmptyValue(loc, typeEx: Option[TypeExpression])`. **`none` is a SYNONYM
  producing the identical node** — no flag records the spelling, the same choice
  `not`/`!` made, and prettify converges `none` to `empty`.
  **The rule is minimum cardinality ZERO**: legal for `T?`, `T*`, `T{0,n}`; an
  Error for `T+`, `T{1,n}` and a bare `T`. That one rule is why ONE literal
  covers both the absent optional and the empty collection — same inhabitant,
  different upper bounds — and it makes `admitsEmpty` total over the four
  `Cardinality` wrappers instead of special-casing two.
  **The ascribed form is load-bearing, not sugar.** A bare `empty` takes its type
  from the position, and only `let`/`constant`/`set` wire an expected type — NOT
  a constructor argument, which is the position this was requested from. **And
  the expected-type machinery resolves only NAMED types**, so a field typed
  INLINE (`note: String(1,20)+`) cannot be checked at all against a bare `empty`.
  Pre-existing, shared with A20.
  **Two traps, both worth re-reading before adding a `Value` arm:**
  1. **The four throw-terminated walks are INVISIBLE to `-Werror`**
     (`countValueFailPoints`, `stateReadsIn`, `initiatesIn`, `asksIn`) — the
     terminal `throw` that enforces totality is itself what makes the match
     exhaustive, exactly as Total Dispatch warns. `-Werror` found three sites;
     the fourth threw at RUN time and aborted `checkStatementScopes` before the
     new checks could run. **Grep for `has no arm for` and add an arm to each.**
  2. **An optional trailing TypeExpression SWALLOWS THE NEXT STATEMENT.** An
     aliased type is a bare path and RIDDL statements are whitespace-separated
     with no terminator, so `set x to empty` followed by `set y to …` parsed the
     second statement as the first's ascription. Guarded by refusing
     statement-leading keywords (`statementStart`), which is COMPLETE rather than
     heuristic because a type can never be named a reserved word. The EBNF
     carries the same guard — without it the two parsers disagree and TatSu
     reddens.
  BAST tag **12** at `FORMAT_REVISION` **21**; JSON `{"value":"empty"}` with an
  optional `type`.

- **`not` and `!` are SYNONYMOUS everywhere, as the inverse of a boolean
  expression** (ruled 2026-08-14, shipped 2026-08-15). `!` is legal in every
  position `not` is, and both build the IDENTICAL `NotExpression` node — there is
  no spelling flag anywhere, so two ASTs meaning the same thing can never compare
  unequal. `not` is prefix and recurses (`not not a` / `!!a`), and both work
  wherever a boolean expression does: `when`, `require`, `let`, parenthesised,
  and applied before a comparison. (This OVERRODE a 2026-08-13 ruling that `!`
  was a legacy spelling accepted ONLY as `when !<bare-identifier>` and "will not
  be extended" — that reasoning is retired, not merely superseded; do not restore
  it.)
  **The grammar rule is `("not" | "!") not_expression`**, replacing the old
  `when_condition`-only special case entirely (EBNF `not_expression`); the parser
  guards the `!=` case with `"!" ~~ !"="` (fastparse negative lookahead, no regex
  — unavailable on Scala Native).
  **Prettify converges `!` to `not`** — the same precedent as `A | B`
  prettifying to `one of { A or B }` — pinned by `BangNotRoundTripTest`; a `!=`
  comparison is untouched, being a comparison operator rather than a negation.
  BAST and JSON both carry the change at `FORMAT_REVISION` 18
  (`WhenStatement.negated` deleted entirely — there was never a second node kind
  to reconcile). Corpus fixture `language/input/bang-not-synonymy.riddl` exercises
  every position plus the `!=` guard, and moved the TatSu baseline from 108/131
  to 109/132. Corpus A/B showed **zero movement**: riddl-models + riddl-examples
  have no `!` uses, 597 `not` uses, and no `!=` uses either.

## Messaging statements

- **`tell` addresses an INSTANCE as well as a named processor (rc.21+).**
  `TellStatement.target` is `ProcessorRef | Value`: keyword-led means a static
  processor, a bare path or `self.id` means a value typed `Id(...)` naming WHICH
  INSTANCE. Told apart by the leading keyword, exactly as `forward` is; `Value`
  excludes `ProcessorRef`, so the union is disjoint.
  **The instance is NEVER resolved and nothing needs it** (Reid, 2026-08-22:
  *"You CANNOT know the specific instance at validation time, but fortunately you
  don't need to."*). Every question asked of a tell target is answered by the
  processor KIND the `Id` names. `TellTarget.processorOf` is the one place that
  answers it: `self` by a LEXICAL parent walk with no lookup, a reference by the
  one refMap lookup the static case already makes.
  **This is why an earlier "it needs a new resolution-output map" analysis was
  WRONG** — it assumed resolving a value target required `ValidationPass`'s
  general value-typing machinery. Reuse of a general helper is not the same fact
  as a capability being unavailable; check which one you have.
  **`checkTellAddressing` is SKIPPED for a value target, and that is the
  feature.** It exists to recover the address structurally from a message field
  typed `Id(target)` when the tell does not say which instance; a value target
  says it outright, so demanding the field would ask for something the statement
  made unnecessary.
  **NOT entity-only** (unlike `terminate`): only an entity can be *ended*, but
  any processor can be *addressed*. **`send` takes no value TARGET** — it takes a
  PORTLET, so `Id(entity E)` cannot apply there.
  **Diagnostics must use the bare PATH, not `ProcessorRef.format`**, which
  prepends the keyword and silently rewrites every existing message from `target
  'E'` to `target 'entity E'`.
  BAST gains a target-shape discriminator at **`FORMAT_REVISION` 20**; JSON adds
  `targetValue` beside the `to`/`processor` pair (register new keys in
  `knownKeys` or the vocabulary guard reddens).

- **`send <msg> to <portlet> at <instant>` — a delivery scheduled for a time**
  (2026-09-07). `SendStatement.at: Option[Value] = None`, TRAILING and defaulted
  (`@JSExportTopLevel`). `at` states an INSTANT, never a mechanism — timer,
  scheduler, delay queue or poll stays the generator's, exactly as CM §3.8 says.
  **`send` ONLY**: `tell`'s target may itself be a value, so `tell m to x at t`
  already parses as a lookup. **The instant must type as TimeStamp, DateTime or
  ZonedDateTime**, through aliases (`stmt-send-at-not-instant`); a `Date` has no
  time of day and a `Duration` is a span. Undeterminable is silent.
  **A past instant is delivered immediately. There is NO cancellation
  construct** — the idiom is schedule to YOURSELF and decide at fire time, so a
  receiver of a scheduled message must tolerate it being stale. **That idiom's
  loop connector is legal**: it drew `stream-graph-cycle` for a few hours on
  2026-09-07, until Reid re-ruled that rule to forbid only an `on X` clause whose
  X can travel back to it — `on command Book` cannot be re-entered by the event
  it schedules.
  **Deliberately UNCHANGED, do not "fix" any of them**: A23's effect set (a
  scheduled send is still a transmission), the discharge rules (**a `send` has
  not settled `yields` since rc.19, scheduled or not** — the plan for this
  feature claimed the opposite and the test caught it), A6 reachability (the
  channel must exist now; only the delivery is later), outlet ownership, portlet
  typing.
  Parser: the Readability `at` — optional, non-cutting — then `value`; a bare
  path reaches `value` through `booleanExpr`'s atom, and `system.now` through
  `systemValue`. `Finder.fieldChildren` AND `statementValues` yield the instant,
  so the four value walks see into it; pinned by a correlation-fold test whose
  only visible offender is an `initiate` hidden in the instant (a function body
  will not do: `send` does not parse there). Emitter routes it through
  `emitValue`, so `prompt("…") as TimeStamp` round-trips. BAST:
  `writeOption(at)(writeValue)` appended to sub-kind 5, riding revision 24. JSON:
  optional `"at"` key (in `knownKeys`); a plain send serializes byte-identically
  to before.
  **`aliasFreeTypeExpr` follows ALIASES only, never cardinality** — `TimeStamp?`
  is not an instant and `Duration?` is not a window — and carries the `eq` visited
  list; `isDurationTypeExpr` had shipped for a day without one (`type A is B` /
  `type B is A` is a real crash this repo has had).
  Fixture trap: `ZonedDateTime` takes a bare zone in parens —
  `ZonedDateTime(UTC)`; the quoted form and the bare `ZonedDateTime` both fail to
  parse.

- **`forward` — delegation, and the ONLY statement that discharges by passing
  on** (rc.19+). `forward <operand> to <portlet|processor>` says the declared
  `yields`/`replies` is produced by whatever handles the message downstream.
  Legal ONLY in a clause handling a command that declares `yields` or a query
  that declares `replies` — **you cannot delegate an event or a result**
  (author's ruling): those record what happened and owe no answer. The operand's
  TYPE must match the handled message; its VALUES need not, so a handler may
  adjust a field and still be forwarding the same message. NOT terminal: a
  `yield`/`reply` after it is an Error (the response was delegated), a
  `send`/`tell` after it a style warning. Both transmission shapes, told apart by
  the keyword leading the reference. BAST sub-kind 21 with ONE discriminator byte
  before the ref; **`FORMAT_REVISION` 19**.

- **`option snapshots` (Entity, event-sourced only) — and reconstructability is a
  CM MUST-PRESERVE.** The option says WHETHER journal-derived snapshots are
  taken, never how; no policy enum and no interval, because whether snapshotting
  pays turns on update rate, read/write mix and physical layout, none of which is
  in the model. **Its ABSENCE is the default and is meaningful: take NO
  snapshots, replay the whole log** — right more often than it looks, since many
  entities see under a hundred events in their lifespan. An Error on a
  non-event-sourced entity. The CM gained a must-preserve with it: **state as of
  any past point must be reconstructible**, so a current-state row kept as an
  optimization is fine but one that is the ONLY reconstruction mechanism is not.

