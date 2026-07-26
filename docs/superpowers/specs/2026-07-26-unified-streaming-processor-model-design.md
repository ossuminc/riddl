# Unified Streaming Processor Model — Design Spec

**Roadmap items:** A32 (optional shape ascription), A31 (port cardinality),
A37 ("as \<intention\>" on contexts), A6 (semantics of `tell`).
**Branch:** `release/2`. **Date:** 2026-07-26.

A6 is included because its frontend footprint is small: `tell`/`send` stay as
surface syntax, the lowering (to a function call, in-memory handoff, or
persistent stream) is a **generator-backend** concern, and the only language
change is a validator warning when a `tell` target is unreachable via any
modeled connector (§7.5).

---

## 1. Motivation & mental model

RIDDL's Computational Mental Model treats **every processor as a streamlet**:
the only way messages flow between processors is streaming — outlets connected
to inlets by connectors. Two consequences drive this spec:

1. **Shape belongs to the `as` clause, uniformly.** Today the streamlet family
   (`source`/`sink`/`flow`/`merge`/`split`/`router`) encodes shape in the
   *leading keyword*, while every other processor has no shape at all. That is
   inconsistent. We unify: a generic **`processor`** keyword plus an optional
   **`as <shape>`** ascription, available on *every* processor kind. Shape is
   otherwise **derived from arity** (the count of declared inlets/outlets), so
   the ascription is a redundant-when-correct check and a documentation aid.

2. **All processors bear ports.** Inlets/outlets move up to the `Processor`
   base so `context`, `entity`, `projector`, `repository`, `adaptor`, and the
   generic `processor` can all declare them. (Today only `Streamlet` parses
   ports.)

**Distribution policy is behavioral, never structural.** A fan-out processor
(≥2 outlets) decides *which* outlets a message goes to via its handler `send`
statements. "Broadcast" (copy to all), round-robin, random, or user-defined are
handler logic — not shapes and not metadata. This is why the fan-out shape
names collapse to a single shape (see §3).

---

## 2. Surface grammar

```
[<intention>] context   <id> [as <shape>] is { … }   // intention: context-only prefix
              entity     <id> [as <shape>] is { … }
              projector  <id> [as <shape>] is { … }
              repository <id> [as <shape>] is { … }
              adaptor    <id> [as <shape>] is { … }
              processor  <id> [as <shape>] is { … }   // generic streaming processor

// DEPRECATED aliases (warn; desugar to `processor <id> as <shape>`; removed in 3.0):
source|sink|flow|merge|split|router <id> is { … }
```

- **Ports** in any processor body: `inlet <id> is <typeRef>` /
  `outlet <id> is <typeRef>` (same grammar as today's streamlet ports).
- **`as <shape>`** is optional on every kind (see §3, §7.2).
- **Intention prefix** ∈ `{application, external, gateway, service}`, optional,
  **`context` only**.

Examples:
```
application context Orders is { … }              // UI-bearing context
gateway     context Edge   as merge is { … }     // load balancer (fan-in)
service     context Billing as flow is { … }
external    context Stripe is { … }              // a separate system
context Plain is { … }                           // no intention, no shape
processor Ingest as source is { outlet out is Event }
processor Fanout as split  is { inlet in is Cmd  outlet a is Cmd outlet b is Cmd }
```

---

## 3. Shape vocabulary & arity

`StreamletShape` keeps its **seven canonical cases** (no new cases). Parsing of
`as <shape>` accepts synonyms that map onto them:

| Canonical | Synonyms          | Arity (outlets / inlets) |
|-----------|-------------------|--------------------------|
| void      | —                 | 0 / 0                    |
| source    | —                 | 1 / 0                    |
| sink      | —                 | 0 / 1                    |
| flow      | **cascade**       | 1 / 1                    |
| merge     | **fanin**         | 1 / ≥2                   |
| split     | **broadcast, fanout** | ≥2 / 1               |
| router    | —                 | ≥2 / ≥2                  |

Because the synonyms collapse the fan-out family, **arity uniquely determines
the canonical shape.** `as <shape>` therefore never adds semantic information —
it documents intent and lets the validator catch arity mistakes.

---

## 4. AST changes (`language/.../AST.scala`)

- Move `WithInlets` / `WithOutlets` up to the **`Processor`** base trait so all
  processor subtypes carry ports. (Streamlet already mixes them in; remove the
  now-redundant per-Streamlet mixin.)
- Add an **optional ascribed shape** to `Processor` (e.g.
  `ascribedShape: Option[StreamletShape]`). Provide an `effectiveShape` derived
  from arity when the ascription is absent.
- `Context` gains an optional **`intention`** (a small sealed enum:
  `Application | External | Gateway | Service`).
- Keep the generic streaming-processor case class (today `Streamlet`) as the
  target of the `processor` keyword; the deprecated shape keywords desugar to it
  with the corresponding ascribed shape. (Internal name may stay `Streamlet`;
  no functional rename required.)
- `StreamletShape` synonym parsing lives in the parser, not new AST cases.

Reflection note: adding ports to all processors and an intention to contexts are
new serialization surfaces — see §8.

---

## 5. Parser changes (`language/.../parsing/`)

- **`processor`** keyword + generic processor rule (`ProcessorParser` /
  `StreamingParser`).
- **`as <shape>`** clause between `<id>` and `is`, on every processor header;
  shape word resolved through the synonym table to a canonical `StreamletShape`.
- **Ports in every processor body** — lift inlet/outlet parsing out of the
  streamlet-only body into the shared processor body.
- **Intention prefix** before `context` — an optional contextual keyword
  (`application|external|gateway|service`) consumed only when immediately
  followed by `context`. None of the four are current keywords/identifiers.
- **Deprecated aliases** — keep `source|sink|flow|merge|split|router <id> is`
  parsing, mapping to the generic processor with the ascribed shape, and emit a
  `Deprecation` message (see the A9-era deprecation-message machinery).
- **EBNF + GBNF** updated in lockstep (§8).

---

## 6. Resolution (`passes/.../resolve/ResolutionPass.scala`)

- Portlets (inlets/outlets) resolvable on all processor kinds, not just
  streamlets. Connector endpoint resolution (`OutletRef`/`InletRef`) already
  exists; ensure it works when the endpoint lives on a non-streamlet processor.

---

## 7. Validation (`passes/.../validate/`)

### 7.1 Port cardinality (A31) — universal
Each **inlet** and each **outlet** is the endpoint of **exactly one connector**.
Fan-in / fan-out is modeled by declaring **multiple ports** (which makes the
processor a merge / split by arity), never by attaching multiple connectors to a
single port. This is unconditional — the unified model makes arity always
knowable, so the original "only when no shape is declared" caveat is dropped.
(The existing zero-connector completeness warning is retained and complementary.)

### 7.2 Shape ascription check
- If `as <shape>` **is present**: the ascribed canonical shape must match the
  arity-derived shape → **error** on mismatch (e.g. `as flow` with two inlets).
- If `as <shape>` **is absent**: **no arity check** — the declared inlets/outlets
  simply *are* the arity. Emit a **suppressible `StyleWarning`** recommending an
  ascription **when the processor has ≥1 port** (portless / `???` stubs stay
  quiet). "Enforce wisdom" via advisory nudge, not mandate.

### 7.3 Context intention rules (A37)
The constraint checks the **effective shape** (ascribed or arity-derived).

| Intention   | Shape constraint     | Contents / connector rule |
|-------------|----------------------|---------------------------|
| application | any                  | **Only** intent that may contain `group`/`input`/`output` (UI). |
| service     | must be **flow**     | No UI definitions. |
| gateway     | must be **merge**    | No UI definitions. (Load balancer / fan-in.) |
| external    | any                  | No content restrictions. **Every connector with an endpoint on an external context's port must carry the `persistent` option** → error otherwise. **Advisory** (suppressible) `StyleWarning`: interpose an `adaptor` between an external context and anything communicating with it, so the external (usually legacy) system is plug-in-replaceable by re-implementing the adaptor's API as a new context. |

Non-application contexts containing `group`/`input`/`output` → **error**.

### 7.4 Option deprecations
`option gateway`, `option service`, `option external` → **deprecated** in favor
of the intention prefix (warn, keep working through 2.x). `option wrapper` →
**deprecated with no replacement** (wrapping an external context to give it a
new API is an adaptor's job). Register the four in `RecognizedOptions` if needed
so they warn cleanly rather than as "unrecognized option".

### 7.5 `tell`/`send` reachability (A6)
`tell <msg> to <target>` is surface sugar for a `send` on the outlet connected
to the target's inlet, keyed by the target's address. The validator emits a
**warning when the target is not reachable via any modeled connector** (no
connector path from an outlet in scope to the target's inlet). The lowering
itself — to a direct function call, an in-memory handoff, or a persistent stream
— is a **generator-backend** concern and is documented, not enforced here.
Direct non-streaming delivery to an entity is therefore a backend optimization,
not a language-level construct: at the model level everything flows through
connectors, which is exactly what the reachability check guards.

---

## 8. Reflection surfaces (round-trip requirement)

RIDDL is fully reflective: every new/changed construct must emit and round-trip.

- **Prettify** (`PrettifyVisitor`/`RiddlFileEmitter`): emit the `processor`
  keyword, the intention prefix, `as <shape>`, and inlets/outlets on **every**
  processor kind. Deprecated aliases are **not** re-emitted — prettify
  normalizes to `processor … as <shape>`.
- **BAST** (`BASTWriter`/`BASTReader`): serialize ports + ascribed shape on all
  processors and the context intention; **bump `FORMAT_REVISION`**.
- **JSON** (`JsonModel`/`JsonifierPass`/`JsonAstBuilder`): extend processor DTOs
  with inlets/outlets + optional shape, and the context DTO with intention;
  update `JSON_COVERAGE.md`.
- **EBNF** (`ebnf-grammar.ebnf`) + regenerated **GBNF** (`ebnf_to_gbnf.py`); run
  the TatSu and GBNF validators. Add a corpus fixture exercising the new syntax.

Round-trip proof required on all three surfaces (parse → prettify → re-parse;
BAST write/read; JSON write/read), tri-platform.

---

## 9. Out of scope (deferred)

- **A6's lowering** — the actual translation of `tell`/`send` to a function
  call, in-memory handoff, or persistent stream is a **generator-backend**
  concern. The frontend adds only the reachability warning (§7.5).
- **Distribution policy** as a modeled attribute — it is handler behavior.
- **wrapper** intention — deprecated, not replaced.

---

## 10. Backward compatibility & migration

- Shape keywords (`source`…`router`) remain as **deprecated aliases** (warn;
  remove in 3.0). Prettify normalizes them away.
- `option gateway/service/external/wrapper` remain functional but **deprecated**.
- **Corpus migration**: `../riddl-models` and `../riddl-examples` will need the
  new forms (or will ride the deprecated aliases until 3.0). Track as a follow-up
  task alongside the pending A9b-ext migration; refresh the native
  `~/Code/ossuminc/bin/riddlc` when green so the external correction can proceed
  concurrently.

---

## 11. Testing strategy

Per project rigor:
- Unit/parse tests for: `processor` + `as <shape>` (all synonyms); ports on each
  processor kind; intention prefix; deprecated-alias desugaring + deprecation
  message; arity-mismatch error; omitted-shape StyleWarning; A31 one-connector
  cardinality (pass + fail); intention rules (service/gateway shape, application
  UI gate, external persistent-connector, external adaptor advisory); option
  deprecations; `tell` target reachable vs unreachable via connectors (A6).
- Round-trip tests (prettify/BAST/JSON) for the new forms.
- **Coverage maintained** (module thresholds).
- **Ends with a full "from clean" run on all platforms** — clear the sbt
  action-cache, then `tJVM ; tJS ; tNative` with zero internal failures
  (external-corpus tests excepted, tracked separately).

---

## 12. Implementation phases (dependency order)

1. **AST** — ports + ascribed shape on `Processor` base; `Context.intention`;
   `effectiveShape`.
2. **Parser** — `processor` keyword; `as <shape>` + synonyms; ports in shared
   processor body; intention prefix; deprecated aliases.
3. **Resolution** — portlets on all processor kinds.
4. **Validation** — A31 cardinality; shape-ascription check + omitted-shape
   nudge; A37 intention rules; option deprecations; A6 `tell` reachability.
5. **Reflection** — prettify, BAST (`FORMAT_REVISION` bump), JSON, EBNF/GBNF.
6. **Tests** — full regression + coverage; corpus-migration follow-up.
7. **Certify** — from-clean tri-platform; refresh native `riddlc`.
