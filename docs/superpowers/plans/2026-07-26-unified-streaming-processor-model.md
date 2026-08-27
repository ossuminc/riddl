# Unified Streaming Processor Model — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify all RIDDL processors under a `processor` keyword with an optional
arity-checked `as <shape>` ascription, give every processor kind inlets/outlets,
add a context `<intention>` prefix, and enforce the associated A31/A37/A6 rules —
across parser, resolution, validation, and all reflection surfaces.

**Architecture:** Layered, bottom-up. AST first (ports on the `Processor` base,
optional ascribed shape, context intention), then parser, resolution,
validation, and finally the three reflection surfaces (prettify/BAST/JSON) plus
EBNF/GBNF. Each task is independently testable and ends in a commit.

**Tech Stack:** Scala 3.9.0-RC4, sbt 2.0.2 + sbt-ossuminc 3.0.3, fastparse,
projectMatrix cross-build (JVM/JS/Native), upickle (JSON), TatSu (EBNF validator).

**Spec:** `docs/superpowers/specs/2026-07-26-unified-streaming-processor-model-design.md`

## Global Constraints

- **Scala 3 syntax only** (`do`/`then`/`end`, no `null` — use `Option`).
- **Backward-compat policy:** additive/deprecate, never silently break public
  API. Retiring syntax → `@deprecated` + a `Deprecation` message, removal
  targeted at 3.0.
- **Reflection is mandatory:** anything parsed MUST emit and round-trip. A new/
  changed AST node touches Prettify, BAST, and JSON, plus EBNF+GBNF.
- **BAST:** bump `FORMAT_REVISION` in `language/.../bast/package.scala` on any
  wire-format change.
- **EBNF↔parser sync:** every parser change needs the matching
  `ebnf-grammar.ebnf` change; regenerate GBNF via `ebnf_to_gbnf.py`; run the
  TatSu + GBNF validators.
- **Tri-platform:** run `tJVM ; tJS ; tNative`; reflection tests live in
  `scala-jvm-native` (run on JVM+Native) with concrete runners in `scalajvm`/
  `scalajs`.
- **Coverage maintained** at existing module thresholds (language 65, passes,
  utils 70, riddlLib).
- **Action-cache blindspot:** after fixture edits, clear
  `~/Library/Caches/sbt/v2/ac` before a certifying run.
- **Shape vocabulary (canonical ← synonyms):** void; source; sink;
  flow←cascade; merge←fanin; split←broadcast,fanout; router. Arity table:
  void 0/0, source 1out/0in, sink 0out/1in, flow 1/1, merge 1out/≥2in,
  split ≥2out/1in, router ≥2out/≥2in.
- **Intention set:** `application | external | gateway | service` (context-only,
  optional prefix).
- Long sbt runs go in the background; use `unset GITHUB_TOKEN` for test runs.

---

## File map

| File | Responsibility | Tasks |
|---|---|---|
| `language/.../AST.scala` | ports on `Processor`, ascribed shape, `Context.intention`, shape synonyms | 1–3 |
| `language/.../parsing/Keywords.scala` | `processor`, synonym keywords, intention keywords | 4–7 |
| `language/.../parsing/StreamingParser.scala` | generic `processor` rule, ports in shared body, deprecated aliases | 4–6 |
| `language/.../parsing/ProcessorParser.scala` | `as <shape>` clause helper reused by all processor headers | 4 |
| `language/.../parsing/ContextParser.scala` | intention prefix | 7 |
| `passes/.../resolve/ResolutionPass.scala` | portlets on all processor kinds | 8 |
| `passes/.../validate/StreamingValidation.scala` | A31 cardinality, shape-ascription check, A6 reachability | 9,10,13 |
| `passes/.../validate/DefinitionValidation.scala` | A37 intention rules, option deprecations | 11,12 |
| `passes/.../prettify/*` | emit processor/intention/as-shape/ports | 14 |
| `language/.../bast/BASTWriter.scala`,`BASTReader.scala`,`package.scala` | ports+shape+intention wire format | 15 |
| `riddlLib/.../json/{JsonModel,JsonifierPass,JsonAstBuilder}.scala` | JSON DTOs | 16 |
| `language/.../resources/riddl/grammar/{ebnf-grammar.ebnf,riddl-grammar.gbnf}` | grammar | 17 |

---

## Phase 1 — AST foundation

### Task 1: Lift inlets/outlets to the `Processor` base

**Files:**
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/AST.scala`
  (`OccursInProcessor` union ~750; `Processor` trait 1024–1032; `WithInlets`/
  `WithOutlets` 546–558; `Streamlet` 3383–3391; `OccursInStreamlet` 794)
- Test: `language/src/test/scala-jvm-native/com/ossuminc/riddl/language/ASTTest.scala`

**Interfaces:**
- Produces: every `Processor` subtype now has `.inlets: Seq[Inlet]` and
  `.outlets: Seq[Outlet]`; `Inlet`/`Outlet` are legal contents of any processor.

- [ ] **Step 1: Write the failing test** — an entity with an inlet/outlet compiles and exposes them.

```scala
"Processor base exposes ports on every kind" in { _ =>
  val inlet = Inlet(At.empty, Identifier(At.empty, "in"),
    TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("Cmd"))))
  val outlet = Outlet(At.empty, Identifier(At.empty, "out"),
    TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("Evt"))))
  val entity = Entity(At.empty, Identifier(At.empty, "E"),
    Contents(inlet, outlet))
  entity.inlets must be(Seq(inlet))
  entity.outlets must be(Seq(outlet))
}
```

- [ ] **Step 2: Run to verify it fails** — `Inlet`/`Outlet` not accepted in `EntityContents`.

Run: `unset GITHUB_TOKEN; sbt "language/testOnly *ASTTest -- -z \"ports on every kind\""`
Expected: FAIL to compile (`Inlet` not a member of `OccursInEntity`).

- [ ] **Step 3: Implement**
  - Add `Inlet | Outlet` to the `OccursInProcessor` union so all six processor
    content-unions inherit them; remove the now-redundant `Inlet | Outlet` from
    `OccursInStreamlet`.
  - Add `with WithInlets[CT] with WithOutlets[CT]` to the `Processor` trait
    (1024–1032); remove them from `Streamlet` (now inherited).

- [ ] **Step 4: Run to verify it passes** — same command → PASS. Then compile all: `sbt "cJVM"`.

- [ ] **Step 5: Commit**

```bash
git add language/src/main/scala/com/ossuminc/riddl/language/AST.scala language/src/test/scala-jvm-native/com/ossuminc/riddl/language/ASTTest.scala
git commit -m "Lift inlets/outlets to the Processor base (all processors are port-bearing)"
```

### Task 2: Shape synonyms + optional ascribed shape + arity-derived effective shape

**Files:**
- Modify: `AST.scala` (`StreamletShape` 3315–3366; add companion `fromKeyword`;
  `Streamlet.shape` 3386 → `ascribedShape: Option[StreamletShape]`; add
  `effectiveShape`/`ascribedShape` to the `Processor` trait; relax the
  `Streamlet` arity `require` at 3395–3431 to only fire when an ascription is
  present)
- Test: `ASTTest.scala`

**Interfaces:**
- Produces: `StreamletShape.fromKeyword(String, At): Option[StreamletShape]`
  (maps `cascade→Flow`, `fanin→Merge`, `broadcast|fanout→Split`, plus canonical);
  `Processor.ascribedShape: Option[StreamletShape]`;
  `Processor.effectiveShape: StreamletShape` (ascribed, else derived from
  `outlets.size`/`inlets.size` via the arity table).

- [ ] **Step 1: Write the failing test**

```scala
"effectiveShape derives from arity; synonyms canonicalize" in { _ =>
  StreamletShape.fromKeyword("cascade", At.empty).map(_.keyword) must be(Some("flow"))
  StreamletShape.fromKeyword("fanout", At.empty).map(_.keyword) must be(Some("split"))
  StreamletShape.fromKeyword("bogus", At.empty) must be(None)
  val p = Streamlet(At.empty, Identifier(At.empty, "P"), None,
    Contents(Inlet(At.empty, Identifier(At.empty,"i"),
      TypeRef(At.empty,"type",PathIdentifier(At.empty,Seq("T")))),
      Outlet(At.empty, Identifier(At.empty,"o"),
      TypeRef(At.empty,"type",PathIdentifier(At.empty,Seq("T"))))))
  p.effectiveShape.keyword must be("flow") // 1 in + 1 out
}
```

- [ ] **Step 2: Run to verify it fails** — `fromKeyword` undefined; `Streamlet` still takes a required `shape`.

Run: `sbt "language/testOnly *ASTTest -- -z \"effectiveShape derives\""` → FAIL.

- [ ] **Step 3: Implement**
  - `object StreamletShape { def fromKeyword(kw: String, loc: At): Option[StreamletShape] = kw match { case "source" => Some(Source(loc)); case "sink" => Some(Sink(loc)); case "flow" | "cascade" => Some(Flow(loc)); case "merge" | "fanin" => Some(Merge(loc)); case "split" | "broadcast" | "fanout" => Some(Split(loc)); case "router" => Some(Router(loc)); case "void" => Some(Void(loc)); case _ => None } }`
  - `Processor` trait: `def ascribedShape: Option[StreamletShape]`; concrete
    `def effectiveShape: StreamletShape = ascribedShape.getOrElse(deriveFromArity)`
    where `deriveFromArity` maps `(outlets.size, inlets.size)` to a shape
    (≥2 → the multi cases; else source/sink/flow/void).
  - `Streamlet`: replace `shape: StreamletShape` with
    `ascribedShape: Option[StreamletShape]`; move the arity `require` so it only
    checks when `ascribedShape.isDefined` (mismatch is validated in Task 10, but
    keep a defensive `require` for the ascribed case).
  - Every other processor case class (`Context`, `Entity`, `Projector`,
    `Repository`, `Adaptor`) gains `ascribedShape: Option[StreamletShape] = None`.

- [ ] **Step 4: Run to verify it passes** → PASS; then `sbt "cJVM"`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add optional ascribed shape + arity-derived effectiveShape with synonym parsing"
```

### Task 3: `Context.intention`

**Files:**
- Modify: `AST.scala` (`Context` 3206–3222; add `Intention` sealed set near
  `StreamletShape`)
- Test: `ASTTest.scala`

**Interfaces:**
- Produces: `enum Intention { case Application, External, Gateway, Service }`
  (with a `keyword` and `fromKeyword`); `Context.intention: Option[Intention]`.

- [ ] **Step 1: Write the failing test**

```scala
"Context carries an optional intention" in { _ =>
  val c = Context(At.empty, Identifier(At.empty, "C"), Contents.empty(),
    intention = Some(Intention.Application))
  c.intention must be(Some(Intention.Application))
  Intention.fromKeyword("gateway") must be(Some(Intention.Gateway))
}
```

- [ ] **Step 2: Run to verify it fails** — `Intention` undefined; `Context` has no `intention`.

- [ ] **Step 3: Implement** — add the `Intention` enum with `keyword`/
  `fromKeyword`; add `intention: Option[Intention] = None` to `Context`.

- [ ] **Step 4: Run to verify it passes** → PASS; `sbt "cJVM"`.

- [ ] **Step 5: Commit**

```bash
git commit -am "Add optional intention to Context (application/external/gateway/service)"
```

---

## Phase 2 — Parser

### Task 4: `processor` keyword + `as <shape>` clause helper

**Files:**
- Modify: `language/.../parsing/Keywords.scala` (add `processor`, `as` reuse,
  synonym keyword recognition), `language/.../parsing/StreamingParser.scala`
  (new `processor` rule), `language/.../parsing/ProcessorParser.scala` (shared
  `asShape` helper)
- Test: `language/src/test/scala-jvm-native/com/ossuminc/riddl/language/parsing/StreamingParserTest.scala` (create if absent) or existing streamlet parse test.

**Interfaces:**
- Produces: `def asShape[u:P]: P[Option[StreamletShape]]` parsing
  `(as ~ shapeWord).?` where `shapeWord` is `StringIn(<all canonical+synonyms>)`
  mapped via `StreamletShape.fromKeyword`; `def processor[u:P]: P[Streamlet]`.

- [ ] **Step 1: Write the failing test** — parse a generic processor with an ascription and synonym.

```scala
"parses `processor P as fanout` to a Split-ascribed Streamlet" in { (td: TestData) =>
  val input = RiddlParserInput(
    """domain D is { context C is {
      |  processor P as fanout is { inlet i is command Cmd
      |    outlet a is command Cmd outlet b is command Cmd }
      |  command Cmd is { f: Integer }
      |} }""".stripMargin, td)
  TopLevelParser.parseInput(input) match
    case Left(m) => fail(m.justErrors.format)
    case Right(root) =>
      val p = AST.getContexts(AST.getTopLevelDomains(root).head).head
        .contents.filter[Streamlet].head
      p.ascribedShape.map(_.keyword) must be(Some("split"))
}
```

- [ ] **Step 2: Run to verify it fails** — `processor` keyword unknown.

Run: `sbt "language/testOnly *StreamingParserTest -- -z fanout"` → FAIL.

- [ ] **Step 3: Implement**
  - `Keywords.processor` (mirror `Keywords.flow` at line 145).
  - `ProcessorParser.asShape` = `P((Keywords.as ~ StringIn("void","source","sink","flow","cascade","merge","fanin","split","broadcast","fanout","router").!).? ).map(_.flatMap(kw => StreamletShape.fromKeyword(kw, ...)))`.
  - `StreamingParser.processor` = `P(Index ~ Keywords.processor ~/ identifier ~ asShape ~ is ~ open ~ streamletBody(...) ~ close ~ withMetaData ~ Index)` building `Streamlet(loc, id, ascribedShape, contents, meta)`.
  - Add `processor` to the streamlet dispatcher and the context-body menu.

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "Parse generic `processor` with optional `as <shape>` (synonyms canonicalized)"
```

### Task 5: Inlets/outlets in every processor body

**Files:**
- Modify: `StreamingParser.scala` (`streamletDefinition` 64–82 → a shared
  `portlet` rule), and each processor-body rule (`ContextParser.contextBody`,
  `EntityParser`, `ProjectorParser`, `RepositoryParser`, `AdaptorParser`) to
  admit `inlet`/`outlet`.
- Test: a parse test placing an `inlet`/`outlet` inside a `context`, `entity`,
  `projector`, `repository`, `adaptor`.

**Interfaces:**
- Consumes: `Inlet`/`Outlet` legal in every `OccursInProcessor` (Task 1).
- Produces: `def portlet[u:P]: P[Inlet | Outlet]` reused by all processor bodies.

- [ ] **Step 1: Write the failing test**

```scala
"parses inlets/outlets inside a context, entity, projector, repository, adaptor" in { (td: TestData) =>
  val input = RiddlParserInput(
    """domain D is {
      |  external context X is { inlet xi is command Cmd outlet xo is event Evt }
      |  context C is {
      |    entity E is { inlet ei is command Cmd }
      |    projector P is { outlet po is event Evt }
      |    command Cmd is { a: Integer }  event Evt is { b: Integer }
      |  }
      |}""".stripMargin, td)
  TopLevelParser.parseInput(input).isRight must be(true)
}
```

- [ ] **Step 2: Run to verify it fails** — `inlet`/`outlet` rejected outside a streamlet.

- [ ] **Step 3: Implement** — extract the inlet/outlet rules into a shared
  `portlet` parser; add it to each processor body's content alternation.

- [ ] **Step 4: Run to verify it passes** → PASS; `sbt "cJVM"`.

- [ ] **Step 5: Commit**

```bash
git commit -am "Allow inlet/outlet declarations in every processor body"
```

### Task 6: Deprecated shape-keyword aliases

**Files:**
- Modify: `StreamingParser.scala` (keep `source|sink|flow|merge|split|router`
  rules, but map them to `Streamlet` with the corresponding `ascribedShape` and
  emit a `Deprecation` message), reusing the A9-era deprecation mechanism
  (`Messages.addDeprecation`; see `Messages.scala`).
- Test: parse `source S is {…}` → a Source-ascribed `Streamlet` **and** one
  `Deprecation` message.

**Interfaces:**
- Consumes: `Messages.addDeprecation` / the `Deprecation` KindOfMessage.
- Produces: the six shape keywords desugar to `processor … as <shape>`.

- [ ] **Step 1: Write the failing test**

```scala
"`source S is {…}` still parses but is deprecated" in { (td: TestData) =>
  val input = RiddlParserInput(
    """domain D is { context C is {
      |  source S is { outlet o is event Evt }  event Evt is { a: Integer }
      |} }""".stripMargin, td)
  val (root, messages) = TopLevelParser.parseInputWithMessages(input) // helper that returns both
  root.isRight must be(true)
  messages.count(_.kind == Messages.Deprecation) must be >= 1
}
```

*(If no combined helper exists, assert the deprecation via a full parse+validate
run that surfaces messages — mirror the A9 deprecation-logging regression test.)*

- [ ] **Step 2: Run to verify it fails** — no deprecation emitted today.

- [ ] **Step 3: Implement** — in each alias rule, build the `Streamlet` with the
  ascribed shape and record a `Deprecation` ("`source` is deprecated; use
  `processor <id> as source`") at the keyword location.

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "Keep streamlet shape keywords as deprecated aliases for `processor … as <shape>`"
```

### Task 7: Context intention prefix

**Files:**
- Modify: `Keywords.scala` (intention words), `ContextParser.scala` (`context`
  rule 48–55 → optional leading intention), the domain/context body dispatch so a
  definition may begin with an intention word followed by `context`.
- Test: parse `application context …`, `gateway context … as merge`, and a bare
  `context …`.

**Interfaces:**
- Consumes: `Intention.fromKeyword` (Task 3).
- Produces: `context` rule yields `Context(..., intention = Option[Intention])`.

- [ ] **Step 1: Write the failing test**

```scala
"parses an intention prefix on a context" in { (td: TestData) =>
  val input = RiddlParserInput(
    """domain D is {
      |  application context Orders is { }
      |  gateway context Edge as merge is { inlet a is command C inlet b is command C outlet o is command C command C is {x:Integer} }
      |  context Plain is { }
      |}""".stripMargin, td)
  TopLevelParser.parseInput(input) match
    case Left(m) => fail(m.justErrors.format)
    case Right(root) =>
      val ctxs = AST.getContexts(AST.getTopLevelDomains(root).head)
      ctxs.find(_.id.value == "Orders").flatMap(_.intention) must be(Some(Intention.Application))
      ctxs.find(_.id.value == "Plain").flatMap(_.intention) must be(None)
}
```

- [ ] **Step 2: Run to verify it fails** — intention prefix unparsed.

- [ ] **Step 3: Implement** — `context` rule = `P(Index ~ intentionPrefix.? ~ Keywords.context ~/ identifier ~ asShape ~ is ~ …)`, where `intentionPrefix = StringIn("application","external","gateway","service").! ` consumed only when followed by `context` (use `&(… ~ Keywords.context)` lookahead so a stray identifier named e.g. "service" is not swallowed). Ensure the domain/context body's definition dispatcher tries the intention-prefixed context alternative.

- [ ] **Step 4: Run to verify it passes** → PASS; run full `sbt "language/test"`.

- [ ] **Step 5: Commit**

```bash
git commit -am "Parse optional intention prefix on contexts (application/external/gateway/service)"
```

---

## Phase 3 — Resolution

### Task 8: Resolve portlets on all processor kinds

**Files:**
- Modify: `passes/.../resolve/ResolutionPass.scala` (connector endpoint
  resolution ~130; ensure `Inlet`/`Outlet` resolve when hosted by any processor,
  not only `Streamlet`).
- Test: `passes/.../resolve/*` — a connector wired between an outlet on a
  `context`/`entity` and an inlet on another processor resolves cleanly.

**Interfaces:**
- Consumes: ports on all processors (Task 1,5).

- [ ] **Step 1: Write the failing test** — parse+resolve a model with an
  outlet on an entity connected to an inlet on a projector; assert no resolution
  errors and both endpoints resolve.
- [ ] **Step 2: Run to verify it fails** (if resolution assumed streamlet-only).
- [ ] **Step 3: Implement** — generalize portlet lookup to any `Processor`.
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit** — `git commit -am "Resolve inlets/outlets on any processor kind"`.

---

## Phase 4 — Validation

### Task 9: A31 — exactly one connector per inlet and per outlet

**Files:**
- Modify: `passes/.../validate/StreamingValidation.scala` (add a per-portlet
  connector-count check alongside `checkUnattachedOutlets` 251–284).
- Test: `passes/.../validate/*` — two connectors into one inlet → error; one → ok.

**Interfaces:**
- Produces: an `Error` "inlet/outlet '<id>' has N connectors; exactly one is
  allowed (model fan-in/out with multiple ports)".

- [ ] **Step 1: Write the failing test** — a model with two connectors targeting
  the same inlet; assert exactly one error mentioning the inlet.
- [ ] **Step 2: Run to verify it fails** (no such check today).
- [ ] **Step 3: Implement** — build a multimap portlet → connectors across the
  context; emit an error for any portlet count > 1. (Zero stays the existing
  completeness warning.)
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit** — `"Validate exactly one connector per inlet/outlet (A31)"`.

### Task 10: Shape-ascription arity check + omitted-shape nudge

**Files:**
- Modify: `StreamingValidation.scala` (check `ascribedShape` vs arity;
  StyleWarning when absent and ports present).
- Test: `as flow` with 2 inlets → error; no `as` with ports → StyleWarning;
  `as flow` with 1/1 → clean; portless stub → no warning.

**Interfaces:**
- Produces: `Error` on ascription/arity mismatch; suppressible `StyleWarning`
  "consider ascribing the shape with `as <shape>`" when omitted on a ported
  processor.

- [ ] **Step 1: Write the failing tests** (three cases above, each asserting the
  exact message kind).
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** — for each `Processor`: if `ascribedShape` defined
  and its canonical shape ≠ arity-derived shape → error; if undefined and
  `(inlets.nonEmpty || outlets.nonEmpty)` → StyleWarning (gate behind the same
  show-missing-warnings flag used elsewhere).
- [ ] **Step 4: Run to verify they pass.**
- [ ] **Step 5: Commit** — `"Validate `as <shape>` against arity; nudge when omitted (A32)"`.

### Task 11: A37 intention rules

**Files:**
- Modify: `passes/.../validate/DefinitionValidation.scala` (context validation).
- Test cases:
  - `service` context whose effectiveShape ≠ flow → error.
  - `gateway` context whose effectiveShape ≠ merge → error.
  - non-`application` context containing a `group`/`input`/`output` → error.
  - `application` context containing UI → clean.
  - `external` context with a connector lacking the `persistent` option → error.
  - `external` context communicating without an intervening adaptor → advisory
    StyleWarning.

**Interfaces:**
- Consumes: `Context.intention`, `Processor.effectiveShape`, connector options.

- [ ] **Step 1: Write the failing tests** (one per rule, asserting message kind + substring).
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** — a `validateIntention(context)` helper:
  - `Service` ⇒ require `effectiveShape.keyword == "flow"` else error.
  - `Gateway` ⇒ require `"merge"` else error.
  - group/input/output present and `intention != Some(Application)` ⇒ error.
  - `External` ⇒ for each connector with an endpoint on this context's ports,
    require the `persistent` option else error; and if a non-adaptor processor
    connects directly to it, StyleWarning recommending an adaptor.
- [ ] **Step 4: Run to verify they pass.**
- [ ] **Step 5: Commit** — `"Enforce context intention rules (A37)"`.

### Task 12: Option deprecations (gateway/service/external/wrapper)

**Files:**
- Modify: `DefinitionValidation.scala` (`RecognizedOptions.registry` 49–138 +
  `validateRecognizedOption`) — register the four as **deprecated** context
  options so they emit a `Deprecation` (not an "unrecognized option"
  StyleWarning). `wrapper` has no replacement message; the other three point to
  the intention prefix.
- Test: `option gateway` on a context → one `Deprecation` message.

- [ ] **Step 1: Write the failing test.**
- [ ] **Step 2: Run to verify it fails** (today: StyleWarning, not Deprecation).
- [ ] **Step 3: Implement** — add a deprecated-option path in the registry/
  validator emitting `Deprecation` with the migration hint.
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit** — `"Deprecate option gateway/service/external/wrapper in favor of intention"`.

### Task 13: A6 — `tell` target reachability

**Files:**
- Modify: `StreamingValidation.scala` (new check).
- Test: a `tell C to E` where no connector reaches `E`'s inlet → warning; with a
  connector path → clean.

**Interfaces:**
- Consumes: connectors, portlets, `TellStatement`.

- [ ] **Step 1: Write the failing test** — model with a `tell` to an unreachable
  target; assert a warning mentioning unreachability.
- [ ] **Step 2: Run to verify it fails.**
- [ ] **Step 3: Implement** — for each `TellStatement`, check there exists a
  connector whose `to` inlet belongs to the tell's target processor; else
  `Warning`. (Reachability = direct connector for now; document that transitive
  paths are a later refinement.)
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit** — `"Warn when a `tell` target is unreachable via connectors (A6)"`.

---

## Phase 5 — Reflection

### Task 14: Prettify

**Files:**
- Modify: `passes/.../prettify/PrettifyVisitor.scala` (+ `RiddlFileEmitter`) —
  emit intention prefix, `processor` keyword, `as <shape>`, and inlets/outlets on
  every processor kind; normalize deprecated aliases to `processor … as <shape>`.
- Test: `passes/.../prettify/*RoundTripTest.scala` — parse → PrettifyPass(flatten)
  → re-parse preserves intention, ascribed shape, and ports.

- [ ] **Step 1: Write the failing round-trip test** (a model exercising an
  intention-prefixed context, a `processor … as split`, and ports on an entity).
- [ ] **Step 2: Run to verify it fails.**
- [ ] **Step 3: Implement** the emitters.
- [ ] **Step 4: Run to verify it passes** on `tJVM`, `tJS`, `tNative`.
- [ ] **Step 5: Commit** — `"Prettify: emit processor/intention/as-shape/ports; normalize aliases"`.

### Task 15: BAST

**Files:**
- Modify: `language/.../bast/BASTWriter.scala`, `BASTReader.scala`,
  `bast/package.scala` (bump `FORMAT_REVISION`). Serialize ascribed shape +
  intention + ports for every processor.
- Test: `passes/.../BASTRoundTripTest.scala` / `BASTSerializationTest.scala`.

- [ ] **Step 1: Write the failing BAST round-trip test** for the new fields.
- [ ] **Step 2: Run to verify it fails.**
- [ ] **Step 3: Implement** writer/reader symmetry; bump `FORMAT_REVISION`.
- [ ] **Step 4: Run to verify it passes** tri-platform.
- [ ] **Step 5: Commit** — `"BAST: serialize processor shape/intention/ports; bump FORMAT_REVISION"`.

### Task 16: JSON

**Files:**
- Modify: `riddlLib/.../json/JsonModel.scala` (processor DTOs gain
  inlets/outlets + optional shape; context DTO gains intention),
  `JsonifierPass.scala`, `JsonAstBuilder.scala`; update `JSON_COVERAGE.md`.
- Test: `riddlLib/.../JsonInputTest.scala` / `JsonRoundTripTest.scala`.

- [ ] **Step 1: Write the failing JSON round-trip test** for intention + shape + ports.
- [ ] **Step 2: Run to verify it fails.**
- [ ] **Step 3: Implement** DTO + jsonify + build symmetry.
- [ ] **Step 4: Run to verify it passes** (`riddlLib/test`).
- [ ] **Step 5: Commit** — `"JSON: processor shape/ports + context intention DTOs"`.

---

## Phase 6 — Grammar & certification

### Task 17: EBNF + GBNF

**Files:**
- Modify: `language/.../resources/riddl/grammar/ebnf-grammar.ebnf` (processor
  rule with optional `as <shape>` + synonyms; ports in processor bodies;
  context intention prefix; deprecated aliases noted), regenerate
  `riddl-grammar.gbnf` via `ebnf_to_gbnf.py`.
- Test: TatSu validator (`language/src/test/scalajvm/python/ebnf_tatsu_validator.py`)
  + GBNF validator over the input corpus.

- [ ] **Step 1:** Update the EBNF rules.
- [ ] **Step 2:** Regenerate GBNF; run both validators. Expected: pass on all
  `**/input/**/*.riddl` fixtures (add a fixture exercising the new syntax).
- [ ] **Step 3: Commit** — `"Grammar: processor/as-shape/intention/ports in EBNF+GBNF"`.

### Task 18: From-clean tri-platform certification + fixtures

**Files:**
- Modify: internal `.riddl` fixtures/`.check` goldens touched by the new
  validation (e.g. omitted-shape StyleWarnings on existing streaming fixtures).
- Test: whole suite.

- [ ] **Step 1:** Migrate/adjust internal fixtures and regenerate any affected
  `.check` goldens (CheckMessagesTest regen trick).
- [ ] **Step 2:** `rm -rf ~/Library/Caches/sbt/v2/ac`; run
  `unset GITHUB_TOKEN; sbt "; tJVM ; tJS ; tNative"`. Expected: zero internal
  failures (external-corpus tests excepted, tracked separately).
- [ ] **Step 3:** `sbt scalafmtCheckAll`; fix any drift.
- [ ] **Step 4:** Build native `riddlc` (`sbt riddlcNative/nativeLink`) and copy
  to `~/Code/ossuminc/bin/riddlc`; smoke-test the new syntax.
- [ ] **Step 5: Commit** any fixture/goldens changes —
  `"Migrate internal fixtures + certify unified processor model tri-platform"`.

---

## Follow-ups (out of this plan)

- **Corpus migration** of `../riddl-models` + `../riddl-examples` to the new
  forms (or riding the deprecated aliases) — a cross-repo task alongside the
  pending A9b-ext migration.
- **A6 lowering** (tell/send → function call / in-memory / stream) in the
  generator backend.
- **Transitive** tell reachability (Task 13 does direct-connector only).
