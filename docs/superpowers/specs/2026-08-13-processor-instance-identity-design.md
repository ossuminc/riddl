# Processor Instance Identity — design

**Date:** 2026-08-13 · **Branch:** `release/2` · **Status:** approved design, not yet planned
**Rulings by:** Reid Spencer, in session, 2026-08-13. Where a ruling overrides or
extends an earlier one, this document says so and cites it.

## Problem

`tell command Ship to entity Sales.Order` names a **type**, not an **instance**. An
entity is not a singleton — there are many `Order`s — so a generator receiving this
statement cannot know which aggregate the message is for. riddl-generator reported
this (`task/2026-08-13-tell-to-an-entity-cannot-name-which-instance.md`) and degrades
to an `AI FILL` marker rather than inventing a rule, which is correct: any convention
it picked ("use the field named `id`") would be riddlg inventing semantics, which is
what the Computational Model exists to prevent.

The gap is wider than addressing. RIDDL has **no way to denote an instance at all**:
not to address one, not to read one's identity, and — as the design work revealed —
no way to bring one into being such that an identity exists to denote.

## What this design does NOT cover

- **Clusterability** (`clustered`, and `self.isClustered`). Deferred to its own spec.
  Note this is *not* "multiplicity": Reid ruled 2026-08-13 that **entity is the only
  multiply-instantiated processor**; contexts, projectors, streamlets, repositories
  and adaptors are all singletons, which may be clustered for resilience. Clustering
  is irrelevant to addressability, because clustered instances are interchangeable.
- **Saga execution identity.** A Saga extends `VitalDefinition`, not `Processor`, and
  the CM calls a saga step "a phase of a saga execution instance" rather than an
  instance. Durable-workflow identity is a separate question. `self` in a saga step is
  an Error.
- **Constructor/destructor arguments beyond `on init`/`on term`.** Those two clauses
  are the constructor and destructor; this design gives them parameters and a way to
  be invoked, and stops there.

---

## 1. Identity

### 1.1 `Id(P)` widens from Entity to Processor

Today `TypeValidation.scala:264` reads `case UniqueId(_, pid) => checkPathRef[Entity](pid, parents)`.
It becomes `checkPathRef[Processor[?]]`, so `Id(entity Order)`, `Id(repository Inventory)`,
`Id(context Pricing)`, `Id(projector Ledger)`, `Id(streamlet Feed)` and
`Id(adaptor Bridge)` all typecheck. Sagas are excluded (not Processors).

**The keyword form is canonical and generalizes.** The grammar's
`unique_id_type = "Id" "(" ["entity"] path_identifier ")"` widens its optional keyword
to any processor kind. The bare form `Id(Order)` remains accepted.

**Reid's ruling (2026-08-13), overriding an earlier proposal in this session to
deprecate the keyword:** keyword-name disambiguation is a RIDDL-wide idiom, and
`Order` alone could name a context, a message or an entity. The keyword is better
documentation of the model, and deprecating it would send the wrong signal about the
idiom generally.

This buys a check worth having: **the keyword must match the resolved kind.**
`Id(entity Inventory)` naming a repository is an Error, not a lie a reader believes.

### 1.2 The identity value

**Axiom.** A value of `Id(P)` is a **globally unique** identifier.

It remains string-representable, so `UniqueId.isAssignmentCompatible` is unchanged
(it accepts `String_` and `Pattern` today) and nothing here is a breaking change. The
generation scheme stays the generator's choice — ULID, UUID, content hash — exactly as
the CM already says at line 1635. Reid, 2026-08-13: *"both UUID and ULIDs have string
representations so there is no conflict."* Write the axiom as "globally unique, e.g. a
ULID" so no generator reads it as mandating an encoding.

**Do not conflate this with definition ULIDs.** CM line 2523 gives **every definition**
a ULID for tool round-tripping (`ULIDAttachment`). That is *model-time* identity of a
*definition*. `Id(P)` is *runtime* identity of an *instance*. A generator reading
"ULID" in both places must not merge them. The spec text must say this outright.

### 1.3 `self`

`self` is a well-known value denoting the **currently executing processor instance**.

| field | type | why it is here |
|---|---|---|
| `self.id` | `Id(<enclosing Processor>)` | created at runtime; a generator cannot know it statically |
| `self.version` | `String` | the composed version coordinate (A47), `Version.component` walked up the parent chain |

**The admission principle** (Reid chose `id` + `version`; the principle is what decides
future fields): `self` carries what **cannot be known statically**. `self.isClustered`
is excluded here because it forward-references vocabulary the clusterability spec has
not defined yet, and it belongs with that work.

**Typing.** `self`'s type is synthesized per enclosing processor — `self.id` is
`Id(Order)` in an Order handler and `Id(Shipping)` in a Shipping one — so the type is
**not user-nameable**. `let me = self` infers it; `let me: T = self` has no `T` to
write. `self` is **not assignable into a message field**, which needs a writable type;
pass `self.id`. `let me = self` then `me.id` resolves through the existing `let` +
`ValueRef` path-walk machinery (A55/A17) with no new resolution rules.

**Legality.** `self` is legal wherever there is an enclosing `Processor` — Adaptor,
Context, Entity, Projector, Repository, Streamlet — including inside a function
declared within one. It is an Error at domain/root level and in a saga step, each
with a message naming the reason.

**`self.id` works in singletons, including adaptors.** Reid, 2026-08-13, recanting an
earlier statement in the same session that adaptors "cannot be addressed separately
from the contexts involved": *"Any of the processors can be told directly, even the
singletons. This means self.id has to work in the singletons."* For a singleton,
`self.id` is used for provenance — stamping which instance acted — rather than for
addressing, since a singleton is reached by path.

---

## 2. Lifecycle: `initiate` and `terminate`

### 2.1 `on init` and `on term` gain parameters

Both reuse the existing `method_argument` shape (`identifier ":" field_type_expression`),
so nothing new is invented for the parameter syntax.

```riddl
entity Order is {
  on init(custId: Id(entity Customer), total: Currency) is {
    yield event Created
  }
  on term(oid: Id(entity Order), reason: String) is {
    do "archive the order"
  }
}
```

`on term`'s **leading parameter is required** and must be `Id(<enclosing processor>)`:
it is invoked from outside, so the caller must say which instance. `on init` has no
such parameter — there is no instance yet, and the identity is minted by initiating.

### 2.2 The two constructs

```riddl
let oid = initiate entity Order(custId, total)   // VALUE expression -> Id(entity Order)
let wid = initiate entity Widget                 // no parens when on init takes none
terminate entity Order(oid, "cancelled")         // STATEMENT, no value
```

**One keyword, `initiate`, with optional parentheses** (Reid, 2026-08-13). Parens are
present exactly when there are arguments to pass. An earlier message in the session
wrote `create` for the no-argument case; that was a slip for the same word, confirmed.

**`initiate` has NO standalone-statement form** (Reid's ruling, final review,
2026-08-13, amending this section — an earlier revision of this document wrote
`initiate entity Order` on a line by itself). It does not parse, and it must not: the
returned `Id` would be lost and the created processor could never be referenced again,
**not even to terminate it — a memory leak by construction**. `let x = initiate …` is
the only way to write it, including in the no-argument case. The parser was already
correct; only this document was wrong.

**`terminate`'s parentheses are MANDATORY** (Reid's ruling, final review, 2026-08-13).
The symmetry with `initiate` was dead syntax: `on term`'s leading `Id(...)` parameter is
required (§2.1), so a no-argument `terminate` can never satisfy the arity check and is
unreachable in any valid model. `terminate P()` — an explicitly empty list — still
parses; "at least one argument" is a validation rule, not a grammar one.

`initiate` **evaluates to `Id(P)`** — system-minted, opaque, globally unique. A
*business* key (an order number) is therefore an `on init` argument stored in state,
distinct from the instance identity. This keeps `Id(P)` free of domain meaning.

Both are type-checked against the declared clause: arity, order, and each argument's
assignment-compatibility. `initiate entity Order` where `on init` declares parameters
is an Error, and so is the reverse.

### 2.3 Why this does not contradict activation-on-first-message

CM line 999 rules that an entity instance is *"activate on first message (recover
state), process, passivate when idle, re-activate on demand"*, and line 1580 calls
`on init` *"where state comes into existence"*.

**Reid's ruling (2026-08-13):** *"This doesn't contradict CM's line 999 because the
construction is not completed until the `on init` clause finishes so there is only one
way to bring an instance into being, all that was missing was a way to invoke it, now
`initiate` handles that."*

The codebase already supports this partition, which is why it fits without strain:

- `OnInitializationClause` — *"happens once ever at creation"*, available on any processor
- `OnActivationClause` — *"each time an entity is activated (rehydrated into memory)"*,
  entity-only, body restricted to side-effect-free statements (`HandlerParser.scala:59`)

So `initiate` invokes the once-ever clause; a `tell` to an existing id rehydrates
through `on activate`.

**Consequence, and a CM amendment.** Because `on init` is once-ever and now explicitly
invoked, **"activate" must mean rehydrate-an-existing-instance, never create one**, and
telling a never-initiated id is an Error. The CM's §4 "activate on first message", read
alone, implies create-on-demand and must be amended so it does not.

**riddlc cannot check this.** Whether an id was ever initiated is a runtime fact. The
rule is a semantic ruling for generators, not a validation, and the spec says so plainly
so nobody expects a diagnostic that cannot exist.

### 2.4 Consequences that follow from existing rules

**Both join the can-fail census** (CM line 2273 enumerates it: send, tell, call, yield,
put, get). An initiate can fail (the instance may already exist); a terminate can race a
passivation. Mechanically: `initiate` needs an arm in `countValueFailPoints`, `terminate`
one in the statement dispatch.

**Both are effects, so three bans apply:**

| context | why |
|---|---|
| projector correlation folds | A70 requires fold purity so re-runs are safe |
| `on activate` / `on passivate` | bodies already restricted to side-effect-free statements |
| function bodies | confirmed by Reid, 2026-08-13 — a function computes, it does not create |

---

## 3. Addressing

### 3.1 The rule

**The target instance is the message's field whose type is `Id(<target processor>)`**,
found structurally. Nothing is written at the send site in the ordinary case:

```riddl
tell command Ship(orderId = oid, from = self.id) to entity Order
```

`orderId: Id(entity Order)` addresses it. `from: Id(entity Warehouse)` is reply-to and
is **not** a candidate — the two are distinguished **by type**, which is the property
that makes the scheme work without annotation.

*(The target is deliberately unqualified: a `to entity Sales.Order` from outside
`Sales` would be a cross-context tell and fall under §4.1. Examples in this document
stay same-context so they do not illustrate one rule while violating another.)*

**Why structural rather than named at the send site or declared on the type:** one
message may be told to two *different* processor types, and each target then needs its
own address. Structural derivation gives each one for free; a per-type declaration
would need a per-target form, which collapses back into structural derivation anyway.

### 3.2 Disambiguation

Grammar: `tell_statement = "tell" deliverable_message_value "to" processor_ref ["by" identifier]`

```riddl
tell command Transfer(fromAcct = a, toAcct = b) to entity Account by toAcct
```

- Omitting `by` when two fields qualify is an **Error** naming both candidates.
- `by` naming an absent field, or one not typed `Id(target)`, is an **Error**.

### 3.3 When no field qualifies

| target | behaviour |
|---|---|
| `entity` | **CompletenessWarning**, gated with the other completeness warnings |
| everything else | **silent** — reached by path; a singleton has nothing to distinguish |

"Silent" here means *about addressing only*. A tell may still be rejected by the
isolation-seam rule in §4.1, which is independent and applies to every processor kind.

**Severity rationale, from measurement.** riddl-models holds **7,556** `tell`
statements (5,155 to an entity, 2,382 to a repository, 4 to a context) against
**7** fields typed `Id(...)` in the entire corpus, across 8,339 message declarations.
So structural derivation finds an address for approximately **zero** of the 5,155
entity tells today. An Error would redden essentially the whole corpus and is not
mechanically migratable — each site needs a human decision about which field identifies
the aggregate, plus a new field on ~8,332 message types. A warning names the gap,
keeps the corpus at 189/189, gives riddlg a real diagnostic instead of a guess, and
leaves tightening to a later major once adoption is real.

The mechanism is **uniform across processor kinds** — an `Id(projector Foo)` field is
used as an address if present — only the *diagnostic* is entity-only, because entity is
the only multiply-instantiated processor.

### 3.4 `send` is untouched

`send` crosses the streaming boundary to a **port**, not to a processor instance.
Addressing does not apply to it.

---

## 4. The isolation seam and channel durability

These are **two independent rules**. Conflating them is the mistake this section exists
to prevent (Reid, 2026-08-13: *"the type convenience of common domain message types says
nothing about the durability of the channel"*).

### 4.1 Adaptor requirement — about type vocabulary

A `tell` whose target resolves into a **different context** is an **Error**, *unless*
the message type is defined in a **domain common to both**. Across **domains**, an
adaptor is always required.

**"Common" means an ancestor of both**, since domains nest: the message type must be
declared in a domain that encloses the sending context *and* the target context. A type
declared in a sibling domain does not qualify — it is foreign vocabulary to at least one
side, which is the condition the seam exists to catch.

Reid, 2026-08-13: *"defining types in domain is one way to ensure that there is a lingua
franca across contexts in a common domain."*

**This completes an already-accepted rule rather than opening a new front.**
`RIDDL-Tools-To-Do-List.md:45`, item **A4, ACCEPTED**: *"Reject references to a foreign
context's message types that occur outside adaptor scope… The adaptor is the only
sanctioned place where another context's message types may be named."* CM line 1163
repeats it. A4 as written covers foreign **message types**; this extends the same seam
to foreign **processor targets**, which is the same violation seen from the other side.

### 4.2 Durability — about transport

**A cross-context `tell` is always a persistent channel**, regardless of where the
message type is defined. The common-domain exemption in §4.1 waives the *adaptor*, not
the *durability*.

**Axiom for the CM:** only processors within one context are guaranteed to share a
memory space — context singletons may sit in different data centres. That is precisely
what licenses a generator to optimize the **same-context** case into something cheaper
than a queue send, and forbids it cross-context.

This **preserves the 2026-08-11 tell ruling** rather than overturning it: *"every
non-projection tell rides a connector, ENQUEUE ALWAYS, and a same-context tell may use
a cheap in-process queue."* riddlg has already implemented and shipped that
cross-context durable path, and it remains the correct lowering for every cross-context
tell that §4.1 permits.

### 4.3 Corpus impact is UNMEASURED, and must be measured by riddlc

A heuristic comparing each tell's named context against its enclosing one suggests
**5,301 cross-context tells (64% of all tells)** — but that is an **upper bound with a
known-unsound method**, and the §4.1 exemption's size could not be measured at all:
**603 of 996 corpus files are include fragments** with no top-level construct, so
file-local analysis cannot tell whether a message type sits in a domain or a context.
Two attempts to measure it produced numbers that were discarded as artifacts.

**Therefore:** build the check with a **counting mode**, run it over riddl-models with
riddlc's real resolution, and get the true migration number **before** the Error flips
on in a release. The severity is settled; discovering on release day that it reddens an
unknown fraction of 189 models is the avoidable part.

---

## 5. Surfaces

### 5.1 Grammar and AST

| surface | change |
|---|---|
| `unique_id_type` | optional keyword widens from `"entity"` to any processor kind |
| `on_init_clause` | gains an optional parameter list |
| `on_term_clause` | gains a **required** one, leading param typed `Id(this)` |
| `tell_statement` | gains optional `"by" identifier` |
| *new value* | `initiate` — evaluates to `Id(P)` |
| *new statement* | `terminate` |
| *new value* | `self` — the token |
| *new resolution* | `self.id` and `self.version` — field access on it |

**`self` and `self.id` are two pieces of work, not one.** Parsing the `self` token is
trivial; what is not is giving `self.id` its type. There is no declared record to look
`id` up in, so the resolver must synthesize `self`'s shape from the **enclosing
Processor** and type `id` as `Id(<that processor>)` — contextually, per occurrence. The
same machinery types `self.version` as `String`, and is what makes `let me = self` then
`me.id` work through the ordinary `ValueRef` path walk (A55/A17). A plan that treats
`self` as one lexical addition will under-estimate this.

Existing nodes gain fields: `UniqueId` the kind keyword, `TellStatement` a
`by: Option[Identifier]`, both `On*Clause`s their parameters.

**Two house rules apply to every one of those fields:**

- `@JSExportTopLevel` requires defaulted parameters to be **trailing**, so new fields
  are declared **before** `contents`/`metadata` and **without** defaults.
- `Definition.equals` is structural, so the kind keyword's `loc` stays `At.empty` on
  every surface (parser, BAST, JSON) or write-form makes two identical definitions
  compare unequal.

### 5.2 The three silent breakages a new node causes

All three are documented in `CLAUDE.md` and none is a compile error:

1. **`Containment.of`** is an exhaustive match over `Branch` with no fallback →
   runtime `MatchError`.
2. **`Pass.traverse`'s generic `case branch: Branch[?]` walks `contents` ONLY.**
   `on init`'s parameters are held in a **field**, so without their own traverse case
   they are never resolved and never validated — the model validates clean while naming
   types that need not exist. Same shape as `Correlation.timeoutStatements` and
   `SagaStep.do/undoStatements`.
3. **`VisitingPass.openContainer`/`closeContainer`** end in `case _: Definition => ()`,
   so a new node falls through in silence. Also check `PrettifyVisitor.keyword`, whose
   fallback is the string `"unknown"`.

### 5.3 Reflectivity tail

Parse-only is half a feature. Each construct needs:

- **PrettifyPass** emission plus a parse → prettify → re-parse round-trip test
- **BASTWriter/BASTReader** with **distinct tags per wire shape** (see the
  `NODE_CONSTANT`/`NODE_METHOD` incident of 2026-08-13 — two node kinds may share a tag
  only if they write byte-identical payloads) and `FORMAT_REVISION` → **15**
- **`JsonifierPass`** plus the `JSON_COVERAGE.md` ledger
- **EBNF** updated and TatSu-validated; **GBNF** regenerated and validated
- `language/input/import/NotImplemented.bast` regenerated **from its own directory**
  (see `BACKLOG.md` § 0 for the recipe and the 93-byte check)

### 5.4 Validation checks

All in `ValidationPass`:

- the `Id(kind Name)` keyword matches the resolved processor kind
- `initiate`/`terminate` arguments type-checked against the declared clause
- `on term`'s leading parameter is `Id(<enclosing processor>)`
- `self` legality: enclosing Processor required; Error at domain/root and in saga steps
- `self.<field>` naming anything other than `id` or `version` is an Error listing the
  two — the set is closed, and a fall-through would silently accept `self.anything`
- address derivation, `by` disambiguation, entity-only CompletenessWarning
- effect bans: correlation folds, `on activate`/`on passivate`, function bodies
- cross-context `tell` seam rule (§4.1), with a counting mode (§4.3)
- can-fail arms for `initiate` and `terminate`

---

## 6. Testing

- **Round-trip test per construct** — parse → `PrettifyPass(flatten=true)` → re-parse,
  asserting the construct survives at the same place.
- **Each validation family gets a positive case**, not only a rejection. A ban with no
  legal counter-example is indistinguishable from a ban applied too widely — the lesson
  from A70, where "legal in the timeout block" was the case that mattered.
- **Certification from clean under a throwaway `--sbt-cache`**, all three platforms, with
  `Suites: completed` counted against 7 JVM / 5 JS / 7 Native and the per-row delta
  reconciled before the run, not after. Current floors: JVM 2267 / JS 712 / Native 1552.
- **Corpus A/B over riddl-models**, whose expected delta is exactly two things: new
  entity-addressing CompletenessWarnings, and whatever the seam check's counting mode
  reports. Any other new message means a rule is too broad.

## 7. Open items filed elsewhere

- **`self` field survey** — peruse the CM and every A item for other usually-available
  processor information that should become `self` fields, classified by whether it is
  statically knowable (generator inlines it) or genuinely runtime-only. → `BACKLOG.md`
- **Clusterability spec** — `clustered`, `self.isClustered`. → `BACKLOG.md`
- **Cross-context seam measurement** — counting mode, then the Error flip. → `BACKLOG.md`
- **CM amendments** — activate-means-rehydrate (§2.3); the memory-space axiom (§4.2);
  the `Id(P)`-vs-definition-ULID distinction (§1.2).
