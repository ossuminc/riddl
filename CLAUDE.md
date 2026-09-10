# RIDDL Project Guide for Claude Code

Specific guidance for the RIDDL project; for org-wide patterns see
`../CLAUDE.md` (parent directory). RIDDL (Reactive Interface to Domain
Definition Language) is a specification language for distributed, reactive,
cloud-native systems using DDD principles — a **monorepo** of cross-platform
Scala modules.

**Documentation lives at [ossum.tech/riddl](https://ossum.tech/riddl/)** —
language reference, EBNF grammar, tutorials, riddlc tooling. The `doc/`
directory here is legacy Hugo content that redirects there; do not add new
documentation to this repo.

## Backward Compatibility Policy

RIDDL is heavily used by Ossum Inc. and external consumers. **Never make
incompatible changes** to public APIs:

1. **No removal of public API.** Retire with `@deprecated(msg, "version")`.
2. **No breaking signature changes** — no changed parameter or return types, no
   new required parameters. New parameters must have defaults.
3. **Deprecation is FOREVER from 3.0 onward — nothing is ever deleted**
   (Reid, 2026-08-31). Through 1.x the rule allowed removal in the next major,
   and 2.0.0 exercised it (`Grammar.loadGbnfGrammar*`) on the reasoning that an
   unshipped major IS the removal window (*"that's the deal with RCs, things
   could disappear"*). **That window is now CLOSED**: rename freely, deprecate
   freely, but the OLD SPELLING STAYS. Stricter than semver allows,
   deliberately — RIDDL models are authored artifacts that outlive the tool, and
   a modeller who wrote valid RIDDL should never have to rewrite it to move
   forward. A 3.0 that breaks a consumer's model or build is not one we ship.
4. **Additive changes only.** When in doubt, **add, don't change**.

**What this means for the LANGUAGE**, since the above reads as an API rule: a
retired keyword or spelling must still PARSE, still produce the same AST, and
emit a `Deprecation` message naming its replacement. Worked examples:
`Abstract`→`Anything`, `option persistent`→the connector intention, the shape
keywords→`as <shape>`, `state X is <record>`→`of`, `prompt`→`do`. Several are
CONSUMED by the parser into the new form, which is what makes prettify converge
and `autoFixable` honest. Follow those, not the GBNF removal.

## Definition of Done, and what bounds 2.0

**2.0 ships when the Computational Model is met** — not when the backlog hits
zero by attrition (Reid, 2026-08-15). "Does the CM require this?" is the test
for whether something belongs in 2.0 at all: no over-engineering, no featurism.

Distinguish two kinds of completeness, because only one is featurism:

- **Correctness completeness** — making a dispatch total so a construct the
  language ALREADY admits stops emitting broken output. Not a feature; leaving
  it half-done is a defect every generator inherits.
- **Feature completeness** — adding constructs or diagnostics because they
  would be nice. This is the thing to resist.

**A backlog item is not done until the CM records its effect.** Code landed,
tests green and the entry deleted is not completion: if the change alters what a
conforming generator must preserve, it is done only once
`../RIDDL-Computational-Model.md` says so. "Tests pass, committed" is an
incomplete report for any language change.

**The CM reconciles with the BRANCH, never with the backlog.** The CM records
**events** — what actually landed. Backlog items are commands and aspirations;
they have not happened, so they have nothing to say to the CM until they do.
Reconciliation runs CM against the branch (`git log`) and may *produce* backlog
entries as output. Writing an aspiration in early makes the document describe a
language that does not exist.

**There is no "defer to 2.1" pile.** Everything on BACKLOG.md is 2.0 work;
post-2.0 items get filed after 2.0 ships. Creating a 2.1 bucket to shorten the
list is the same dishonest zero as deleting an entry carrying real work.

**Where items come from:** many originate in **riddlg** (`../riddl-generator`),
which keeps discovering things RIDDL must disambiguate before code generation is
well-defined. Those are CM-relevant almost by construction — that is the CM's
whole purpose — so treat a riddlg-sourced item as in-scope for 2.0 unless there
is a specific reason not to.

## Critical Build Information

### Scala Version & Syntax

- **Scala 3.9.0** (not Scala 2!) — final since 2026-08-27, after riding
  3.9.0-RC1→RC6 through the 2.0 branch. Pinned via `V.scala` +
  `With.Scala3.configure(version = Some(V.scala))` on every CrossModule:
  sbt-ossuminc's `With.typical` otherwise pins its default 3.8.4 and is applied
  AFTER `scalaVersion :=`, so the plain setting is a no-op and the
  `With.Scala3.configure` override is the real lever.
  **A Scala bump is ~36 sites, not one**, because the full version is a
  build-output PATH SEGMENT: `project/Dependencies.scala` plus every hardcoded
  `scala-<version>` in `scala.yml`, `release.yml`, `coverage.yml`,
  `.sonarcloud.properties` and `Dockerfile`. A grep omitting `.github/` misses
  ten of them.
- **Build files are Scala 3 too** since the sbt 2 upgrade — `build.sbt` and
  `project/*.scala`; the old Scala 2.12 build-def rule is gone.
- **ALWAYS Scala 3 syntax**: `while i < end do … end while`; no `null` checks
  (use `Option(x)`); `do`/`then`/`end` control flow.

### sbt-ossuminc 3.0.3 (sbt 2.0.2+, projectMatrix)

sbt is pinned in `project/build.properties`; sbt 2 credentials live in
`~/.sbt/2/`.

- `CrossModule(dir, mod, V.scala)(JVM, JS, Native)` takes the Scala version and
  wraps sbt 2's built-in `projectMatrix`. Extract rows with
  `.jvm`/`.js`/`.native`; wire deps per-row (no cp-level `.dependsOn`).
- **Flat source tree** (no more `shared/jvm/js/native`):
  `<mod>/src/{main,test}/scala` (shared), plus `.../scalajvm`, `.../scalajs`,
  `.../scalanative`, and `.../scala-jvm-native` (JVM+Native shared, wired via
  `unmanagedSourceDirectories`).
- 3.0.3 auto-adds `scalajs-stubs % provided` to the JVM/Native rows of any
  module that also targets JS, so shared `@JSExport*` code compiles — no
  consumer dep needed.
- Cross-platform deps use plain `%%` (the `%%%` operator is gone).

```scala
.configure(With.typical, With.GithubPublishing,
  With.Scala3.configure(version = Some(V.scala)))   // plain scalaVersion := is a no-op
.jsConfigure(With.ScalaJS(header = "RIDDL: mod", hasMain = false,
  forProd = true, withCommonJSModule = true))
.nativeConfigure(With.Native(mode = "fast",   // debug|fast|full|size|release
  buildTarget = "static", gc = "none", lto = "none"))
.jvmConfigure(With.BuildInfo.withKeys("key" -> value))
```

### CRITICAL: Target-path layout (sbt 2 virtual FS)

Build outputs live under a **central** virtual-FS tree at the repo root (sbt
runs with `sbt.io.virtual=true`), NOT per-module `<mod>/target/…`:

```
target/out/<platform>/scala-<fullVersion>/<artifactName>/…
```

- `<platform>` ∈ `jvm`, `sjs1`, `native0.5` (NOT `js`/`native`).
- `<fullVersion>` is the **full** Scala version (`scala-3.9.0`), NOT a `-3`
  binary tag — so a Scala patch bump DOES move every hardcoded path.
- `<artifactName>` is the `moduleName` (`riddl-utils`, `riddl-lib`, `riddlc`, …).

Verified real paths: native riddlc `…/native0.5/scala-3.9.0/riddlc/riddlc`;
native lib `…/native0.5/scala-3.9.0/riddl-lib/libriddl-lib.a`; JS opt
`…/sjs1/scala-3.9.0/riddl-lib/riddl-lib-opt/main.js`; JVM stage
`…/jvm/scala-3.9.0/riddlc/universal/stage/bin/riddlc`; scoverage
`…/jvm/scala-3.9.0/<artifact>/scoverage-report/scoverage.xml`.

These are hardcoded in **scala.yml** (`RIDDLC_PATH`, artifact upload paths),
**coverage.yml** + **.sonarcloud.properties** (scoverage), **release.yml**
(native cp + JVM stage zip) and **Dockerfile** — update them on any
full-Scala-version bump. Search:
`grep -rn "target/out/.*scala-3\." .github/ Dockerfile .sonarcloud.properties`

**`target/out` must NOT be cached, and an earlier version of this list wrongly
said scala.yml caches it.** Restoring sbt 2 build outputs into a fresh checkout
leaves sbt believing the meta-build is already built, so
`project/Dependencies.scala` never contributes its symbols and `build.sbt`
collapses with dozens of `Not found: V` / `Not found: Dep` plus an `Append`
ambiguity on a line nobody edited — a cascade pointing everywhere except the
cause. **A cache written by a GREEN run is exactly as poisonous as a stale
one**: the rule that held was not "stale cache" but "every cold build passed,
every cache-restoring build failed", including on markdown-only commits.
Dropping `restore-keys` does NOT fix it — that only makes one run cold by
accident. `scala.yml:165` carries the ban and its reason; Coursier/ivy2
dependency caches are separate and fine.

## Module Structure

```
utils → language → passes → commands → riddlc
                     ↓
                  testkit
```

The `diagrams` and `hugo` modules moved to the `riddl-gen` repository.

**Adding a module**: create `<mod>/src/{main,test}/scala/…`, add a `CrossModule`
to `build.sbt`, add all three rows to root aggregation, wire deps per-row.
Platform dirs as listed under sbt-ossuminc above; **avoid platform-specific APIs
in shared code** — abstract over them with `PlatformContext`.

```scala
lazy val mymodule_cp = CrossModule("mymodule", "riddl-mymodule", V.scala)(JVM, JS, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp))
  .configure(With.typical, With.GithubPublishing)
  .jvmConfigure(With.coverage(50))
  .jsConfigure(With.ScalaJS("RIDDL: mymodule", withCommonJSModule = true))
  .nativeConfigure(With.Native(mode = "fast"))
lazy val mymodule = mymodule_cp.jvm   // .js, .native
```

### BAST Module (Binary AST)

Binary AST serialization for fast module imports — complete, ~6-10x faster than
reparsing source, output ~63-67% of source size on non-trivial inputs. Package
`com.ossuminc.riddl.language.bast` (`language/src/main/scala/…/bast/`),
cross-platform, written by `passes/…/BASTWriterPass.scala`. CLI: `riddlc bastify
<file.riddl>` and `riddlc unbastify` (both implemented; `UnbastifyCommand`, and
`RiddlModelsRoundTripTest` exercises it over the whole corpus). Format docs live
at ossum.tech/riddl, not in this repo. Key files: `package.scala` (constants and
`NODE_*`/`TYPE_*`/`STREAMLET_*` tags), `BASTWriter`, `BASTReader`, `BASTLoader`,
`BASTUtils`, `StringTable`, `PathTable`.

**HAZARD — disjoint tag sets**: `readNode()` handles only `NODE_*` tags;
`readTypeExpression()` only `TYPE_*`. Crossing them misaligns bytes and surfaces
as "Invalid string table index" during deserialization.

**HAZARD — one tag per WIRE SHAPE, not per family.** `Constant` and `Method`
were both written with `NODE_FIELD` because all three are "a name and a type" —
but a Constant appends its literal value and a Method its argument list, so the
reader (which read a Field) left those bytes in the stream and every byte after
such a node was misread. Fixed 2026-08-13 with `NODE_CONSTANT` (109) /
`NODE_METHOD` (110) and `FORMAT_REVISION` 14. The reader had ADMITTED it in a
comment (*"This is ambiguous … For now, assume Field"*), which is the part worth
learning from: **a known-ambiguous decode is a latent corruption, not a rough
edge. Two node kinds may share a tag only if they write byte-identical
payloads.**

**A BAST error names where the reader DERAILED, never what derailed it.** The
same single constant surfaced as `Invalid string table index` in a 13-node model
and as `Invalid invariant condition kind: 67` in a 9618-node one, sending both
riddl-models and this repo to bisect an innocent invariant. Bisect toward the
node BEFORE the reported position, and distrust the construct named.

## Testing Patterns

### Parser/EBNF Synchronization Requirement

**Any change to the fastparse parser MUST have a corresponding change to the
EBNF grammar** at `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf`,
which is the canonical specification of RIDDL syntax and is validated in CI by a
TatSu-based parser over all `**/input/**/*.riddl` files. Update the rule, run the
validator locally (`language/src/test/scalajvm/python`, `.venv/bin/python
ebnf_tatsu_validator.py`), and ensure both parsers accept the same inputs.

**There is NO GBNF any more.** The bundled 258-rule `riddl-grammar.gbnf`, its
generator (`ebnf_to_gbnf.py`), its validator and its overrides were deleted
2026-08-20 on Reid's ruling (*"We could do without the reflectivity tax, it's
high enough without it"*), and `Grammar.loadGbnfGrammar*` went with them —
legitimate only because 2.0.0 had not shipped. **A grammar change now touches
TWO artifacts, not three**; any instruction to "regenerate the GBNF" is stale.
The evidence was a measurement riddl-generator had already written down:
llama.cpp's grammar engine could not run the full RIDDL grammar at a usable
speed — **an 8-token constrained generation did not finish in seven minutes**
against seconds unconstrained — so it was dropped for PERFORMANCE, not quality,
and nothing consumed the bundled file. Constrained decoding survives via
JSON-schema-derived grammars llama.cpp builds itself, needing no file from this
repo. Coverage did not change: the EBNF stays authoritative and TatSu gates it.

**TatSu's `nameguard` refuses a bare letter token that touches a digit.** An
exponent marker written `("e" | "E")` reads fine as prose and fails under the
generated parser for `1e3` specifically — nameguard bounds any word-like quoted
literal to a word boundary, so `e` followed immediately by a digit looks like the
start of a longer identifier. `e+3`/`e-3` work, which is what makes it look like
a sign bug. Write the marker as an inline regex (`/[eE]/`), the idiom `mime_type`
and `markdown_line` already use.

**Adding a `.riddl` fixture is a GRAMMAR-SURFACE change, not just a test
change.** A fixture that is an include fragment or intentionally invalid must be
added to `INCLUDE_FRAGMENTS` in `ebnf_tatsu_validator.py`, or the CI
`ebnf-grammar-validation` job exits 1 — on a commit whose Scala suites are green
on all three platforms, because `tJVM`/`tJS`/`tNative` do not run the Python
validators at all. **Run them yourself** before calling grammar work verified; a
green test run is a claim about the tests you ran, and the gates outside the test
runner are exactly the ones it cannot speak for. Conversely, **a fixture in a
SKIPPED file is not coverage** — check the validator's own output for a `✓` on
the file.

### Reflection / Round-Trip Requirement

**RIDDL is fully reflective by design and necessity: anything that can be parsed
MUST also be emitted.** A change to the AST or parser is only half done until
PrettifyPass emits the new/changed construct AND a parse → prettify → re-parse
round-trip preserves it. "Parses and validates" is half the contract; **emit +
round-trip is the other half.** When you add or move a construct:

1. Confirm `PrettifyVisitor` / `RiddlFileEmitter` emit it. Traversal
   (`HierarchyPass`) and dispatch (`VisitingPass`, `Pass.scala`) are generic and
   type-based, so it often "just works" — but **prove it, don't assume it.**
2. **Add a round-trip test** — parse → `PrettifyPass(flatten=true)` → re-parse —
   asserting the construct survives at the SAME place (not dropped, not
   relocated). Templates:
   `passes/…/prettify/RepositoryDomainScopeRoundTripTest.scala`,
   `IdentifierQuotingRoundTripTest.scala`.
3. **Run the FULL suite on all platforms** (`tJVM tJS tNative`), not just the
   module you touched. A green partial suite proves nothing when no existing
   test exercises the new shape.

BAST is a second serialization surface and JSON a third: a new AST node
generally needs BASTWriter/BASTReader support and a `FORMAT_REVISION` bump.

### Working rhythm

Compile after every change (`sbt "project <module>" compile`) and fix Scala 3
syntax errors immediately, before moving on. Test inputs live in
`language/input/<category>/<file>.riddl`.

## Common Errors & Solutions

| Error | Cause → Fix |
|---|---|
| "This construct is not allowed under -new-syntax" | Scala 2 syntax → use `do`/`then`/`end` |
| "value kind is not a member of Token" | Token is an enum → `getClass.getSimpleName` |
| "value toList is not a member of Contents" | Opaque type → use `.toSeq` |
| "value Javascript is not a member of With" | sbt-ossuminc API change → `With.ScalaJS` |

**"No given instance of PlatformContext for default parameter"** — a Scala 3.8.x
limitation: a default value in a case class's FIRST parameter list cannot resolve
a `given` from a subsequent `using` clause in the generated companion `apply`.
Fix by removing the default: `case class Foo(x: Bar)(using PlatformContext)`,
never `x: Bar = Bar()`.

**"parameters with defaults must be at the end" (Scala.js)** —
`@JSExportTopLevel` on a case class with `(using PlatformContext)` in a second
parameter list sees the context as a non-default parameter after defaulted ones.
Fix by removing `@JSExportTopLevel` from internal data structures JS never
constructs. **Corollary used repeatedly below: a new field with a default must
be TRAILING**, which is why several AST fields are declared without defaults.

**`System.lineSeparator()` returns `\0` in Scala.js** — use
`PlatformContext.newline` instead, and never `System.lineSeparator()` in shared
code. The `FileBuilder` trait and its whole hierarchy take `(using
PlatformContext)` for exactly this.

## Git Workflow

- **sbt-dynver** derives versions from git tags
  (`MAJOR.MINOR.PATCH-commits-hash-YYYYMMDD-HHMM`); tag without a `v` prefix,
  which breaks it. **Always `sbt publishLocal` after tagging** so the new
  version is available locally.
- **`main` is both the working and the release branch** — commit directly to it
  and cut releases by tagging it; CI builds from the tag. There is no GitFlow
  and no permanent `development` branch (see `../CLAUDE.md`). Reach for a
  short-lived branch only when you want isolation, then merge and delete it.
- **The `development` branch is GONE** — deleted local and remote 2026-08-27,
  0 commits ahead of `main`; `old-development` was already gone. So was a stray
  `help` tag (a typo'd `git tag help` pointing at a 2019 commit) which had
  sorted to the top of `git tag --sort=-v:refname` and so LED the tag list
  whenever anyone worked out the latest release. Do not recreate any of them —
  a reference to one is stale text, not a branch you failed to fetch.
- **`.claude/skills/ship/SKILL.md` no longer prescribes GitFlow** (fixed
  2026-08-27; it had told every release to fast-forward `main` from
  `development` and merge back, both no-ops or contrary to policy from 1.30.0
  on). It now says: ship a FINAL release from `main`; when the work lives on a
  release branch, merge that branch into `main` and tag `main`, never the
  branch, then delete the branch. Release CANDIDATES remain the documented
  exception and may be tagged on the branch — see the `/rc` skill.
- PR merge with branch protection: `gh pr merge --admin --merge
  --delete-branch=false`.

## Quick Reference

```bash
sbt "project language" compile     # or test; "project bast", etc.
sbt cJVM cJS cNative               # all platforms compile
sbt tJVM tJS tNative               # all tests
sbt riddlc/stage                   # → target/out/jvm/scala-3.9.0/riddlc/universal/stage/bin/riddlc
sbt scalafmt
./scripts/pack-npm-modules.sh riddlLib
```

`riddlc help | version | info | parse <file> | validate <file>`; every command
can load its options from a HOCON config file (`riddlc from <conf> <cmd>`).


## Reference split out of this file

Two companion files under `docs/claude/` hold detail that is consulted during a
named task rather than needed unprompted. **Read the relevant one when you touch
its subject; nothing in them is less authoritative for having moved.**

- **`docs/claude/language-constructs.md`** — per-construct language reference:
  syntax, AST shape, BAST tags and `FORMAT_REVISION` numbers, JSON keys and
  design rationale for the standard module, handler clauses (A55/A57/
  quiescence), correlations (A70), processor instance identity, typed holes
  (A20), entity intentions and stream shapes, values and literals, and the
  messaging statements. The traps for each stayed under **AST / Language
  Internals** below.
- **`docs/claude/build-and-api.md`** — adding a new riddlc command, NPM
  packaging, import vs include, the `Contents`/Token/`At` basics, writing a
  Pass, the RiddlLib/RiddlAPI split, and the GitHub workflows.

If you ever need a fact from either file that you had no reason to look for, it
belongs back in this one.

## Subtle Patterns and Gotchas

Each subsection is a topic, not a serial number — add new entries to the right
group rather than appending to a list.

### BAST

- **VERSION is a single integer** (`VERSION: Int = 1`) and stays at 1 until the
  schema is finalized for external users.
- **FORMAT_REVISION** (`language/shared/.../bast/package.scala`) must be
  incremented whenever a BASTWriter change produces output an older BASTReader
  cannot read correctly: new statement subtypes, wire-format changes, reordered
  fields, new node tags.
- **Location comparisons use offsets**, not `line`/`col`.
- **`writeContents` writes a COUNT and trusts an unrelated caller elsewhere to
  write the ITEMS — a contract that has now failed four times.**
  `readContentsDeferred` then consumes N nodes that were never written and the
  stream desynchronizes. It bites any node holding children the generic
  traversal cannot reach: `BASTImport` and `InteractionContainer`
  (`sequence`/`parallel`/`optional`) are `Container` but **not `Branch`** — no
  `id`, so they cannot be `Definition`s, so `BASTWriterPass.traverse` fell
  through to the `wm: WithMetaData` arm, which calls `process()` (header +
  count) and never descends. `InvariantBlock` is worse: its statements sit in a
  FIELD of a node that is not even a `Container`, and `writeInvariant` emits the
  predicate INLINE, so the deferred items must land after `requires` rather than
  after their own count. **The tell is a node count going DOWN when a construct
  is ADDED.** Every fix so far taught the specific missing traversal path rather
  than making the mismatch structurally impossible; a fifth instance is the
  signal to change the contract itself (a writer returning "how many items I
  still owe", and a chokepoint refusing to finalize a node until it is
  satisfied). Adjacent, same sweep: `writeRelationship` wrote **no discriminator
  byte at all** while the shared-tag reader unconditionally reads one, so every
  relationship misread its own location as its dispatch byte — latent since
  `relationship` first became serializable.
- **BAST carries real positions; `positionsKnown` is how a consumer detects when
  it cannot.** `writeLocation` delta-encodes the REAL offset and `At` has always
  derived line/col lazily from `source.lineOf(offset)`, so the format was never
  the problem. The defect was the READER attaching a `BASTParserInput` whose line
  index is SYNTHETIC (line L starts at L×10000) and then feeding it real offsets,
  putting everything under offset 10000 on line 1 at col = offset. Pass real
  sources via `BASTReader.read`'s optional `sources` map. When absent, `At.line`
  returns **0** (`At.scala:43`) — unrepresentable as a 1-based position, and
  deliberately so: **a confident wrong answer is worse than an absent one**,
  because the old plausible line 1 was good enough for a Problems pane to point
  at and impossible to detect.
- **BASTImport in HierarchyPass** — `openBASTImport`/`closeBASTImport` hooks plus
  `traverseBASTImportContents(bi)`. All `PassVisitor` implementors must define
  these (even as no-ops); `BASTImport` extends `Container` but not `Branch`, so
  without the hooks it falls through and its contents are never visited.

### AST / Language Internals

**Per-construct detail — syntax, AST shape, BAST tags, `FORMAT_REVISION`
numbers, JSON keys and design rationale — lives in
`docs/claude/language-constructs.md`. Read it when you touch one of these
constructs.** What stays here is the half that has to fire unprompted: the rule
you could get wrong, the "do not restore this", the anti-pattern. If you need a
fact from that file that you had no reason to look for, it belongs back here.

- **The predefined `Riddl` standard module** — **NEVER inject it into a user's
  `Root.contents`.** The ONLY seam is `SymbolsPass.postProcess`, seeding
  `predefinedSymTab`/`predefinedParentage` — separate maps lookups fall back to.
  Separateness is load-bearing: several public APIs
  (`AnalysisResult.domains/streamlets/…`, `UseCaseWitnessPass`,
  `foreachOverloadedSymbol`) ENUMERATE `parentage`/`symTab`, and seeding the
  shared maps leaks the standard library into "all X in the model". All
  exemptions test REFERENCE IDENTITY via `PredefinedModule.isPredefined`, never a
  name. **A missing error-sink is a `Missing` warning, NOT a
  CompletenessWarning** — Completeness asserts STRUCTURAL incompleteness (unfed
  inlets, unreachable sinks) while "has not said where hard errors go" is the
  "has no author" family; emitting it as Completeness turned **thirteen unrelated
  suites red**. `GeneratorError` and `Envelope` are legitimately UNUSED inside
  the module — the design, not a defect — and `PredefinedTerminatorsTest` asserts
  exactly which, by name: widen that list when adding another, never loosen it.
  `language/input/predefined/riddl-standard-module.riddl` is a verbatim copy;
  `PredefinedModuleSourceTest` fails on drift.

- **`on other as x [: <envelope>]` (A57)** — **the ascription RESTATES the
  `option message_envelope` in scope; it never OVERRIDES it.** A per-clause
  override would mean reading one clause tells you nothing about its siblings —
  exactly what scope inheritance prevents. **Rendering lives in
  `Declaration.ascription`, NOT the clause's `format`**: `format` alone makes
  prettify silently DROP the binding on every round trip (shipped as a bug for
  exactly one commit). **`OnOtherClause` must NOT join `OnMessageLikeClause`** —
  that is what keeps it out of `UseCaseWitnessPass`'s index; a clause matching
  every type would witness every step.

- **On-clause message binding (A55)** — `binding` is declared WITHOUT a default
  on `OnMessageLikeClause` and both concrete nodes, because `@JSExportTopLevel`
  requires defaulted params to be TRAILING and `contents`/`metadata` are
  defaulted. Same rule forced A57's two fields.

- **Correlations in projectors (A70)** — **`yields` names a COMMAND**, never a
  record: a handler clause takes a `messageRef` (the four real messages only,
  A9b), so no `on` clause could name what a record form produced. **The timeout
  clause is MANDATORY and is grammar, not metadata** — §4.2 calls options
  *advisory*, and a bound that MUST fire a block is not; there is consequently
  **no timeout inheritance** from the Projector. **Keys are stored AS WRITTEN and
  never canonicalized** (§6.5 makes identity the full tuple, so sorting would
  silently equate two different declarations) — the exact OPPOSITE of
  `EntityIntention.canonical`. **The effect ban binds FOLDS only**; the timeout
  block exists to have an effect. The repository-accepts-it rule is a
  COMPLETENESS warning, not an Error. **`checkCorrelationEventSources` must NOT
  use `MessageFlowPass`** — depending on it would reorder the standard passes.

- **Processor instance identity — `Id(P)`, `self`, `initiate`, `terminate`.**
  - **`Id(P)` is RUNTIME instance identity, NOT the definition ULID** (CM:2523,
    model-time identity of a *definition*).
  - **`Id(P)` covers all six processor kinds and must NOT be narrowed** —
    a singleton's `Id` is how you SEND IT MESSAGES. `initiate`/`terminate` being
    ENTITY-ONLY is an EXPLICIT check (`reportNotInstantiable`), never a
    consequence of the type system.
  - **`self`'s type is a synthesized `Aggregation`, and that is load-bearing** —
    because it is an ordinary record, no resolution rule anywhere has to know
    `self` exists. `SelfValue.fieldNames` is a CLOSED set; the admission test is
    runtime-only. `enclosingProcessorOf` terminates at `Function` AND `Saga`, or
    `self` in a saga step silently types as the enclosing Context.
  - **Every effect ban is wired into `checkStatementScopes`, NOT
    `validateStatement`** — the latter never sees statements held in a FIELD
    (`when`/`match`/`foreach`), the trap two tasks of that plan fell into.
  - **Addressing matches by RESOLVED IDENTITY (`eq` through the refMap), never
    the path's last segment** — name matching turned a legal model into a false
    ambiguity Error. It **follows ALIAS CHAINS but never NESTING**: `type OrderId
    is Id(Order)` IS an address (the corpus house style; matching `UniqueId`
    alone made 72 of 86 reactive-bbq findings false), while an id inside a NESTED
    record stays flagged, because descending an aggregate is an unbounded search
    with no principled stopping point. **Both alias walks need an `eq` visited
    list** — `type A is B` / `type B is A` is a real `StackOverflowError`,
    surfacing as `[severe] Exception Thrown` with no line number; NOT a
    `Set`/`contains` guard, since structural `equals` would fuse two distinct
    identical declarations and truncate a legitimate chain. Fixing the alias case
    exposed **49 Errors the corpus had been hiding** in 16 of 189 models — the
    fourth reminder that a green corpus is evidence about the corpus.
  - **`resolveIdTarget` needs TWO lookups and the second is not optional** —
    `valueTypeExpr` SYNTHESIZES a `UniqueId` for `initiate` and `self.id` with no
    refMap entry, so a refMap-only lookup made every such `terminate` resolve to
    `None` and skip its checks in silence. Falls back to `symbols.lookup`. Found
    by instrumenting, not reading.
  - **An `initiate` whose id is never referenced is a plain Warning** — not an
    Error (a self-terminating worker legitimately has an unused id) and not gated
    behind `showCompletenessWarnings` (it is locally decidable). The work is the
    five-route escape analysis, not the message.

- **A new `Branch` node breaks three things silently** — all found building A70,
  none caught by the compiler:
  1. **`Containment.of`** (`AST.scala`) is an exhaustive match over `Branch` with
     no fallback arm → runtime `MatchError`, not a compile error.
  2. **`Pass.traverse`'s generic `case branch: Branch[?]` walks `contents`
     ONLY.** Statements held in a FIELD (`Correlation.timeoutStatements`,
     `SagaStep.do/undoStatements`) need their own case BEFORE that arm, or they
     are never resolved and never validated — the model validates clean while
     naming definitions that need not exist. `HierarchyPass` deliberately does
     NOT do this: its visitors emit field-held statements themselves, in the
     position the syntax requires.
  3. **`VisitingPass.openContainer`/`closeContainer` end in `case _: Definition
     => ()`**, so a new node falls through in silence.
  Also remember `PrettifyVisitor.keyword`, whose fallback is `"unknown"`.

- **Typed holes (A20) — `prompt("…") as T`.** **The ascription RESTATES the
  position's already-known type; it never OVERRIDES it** — a contradiction is an
  Error, not a coercion. **The comparison is deliberately SYNTACTIC, not
  resolved-type**: `constant G: Real = prompt(…) as Score` (`type Score is Real`)
  is still an Error, because a resolved comparison would swallow exactly the
  contradiction the rule exists to catch. It compares only the LAST path segment
  — a KNOWN, accepted limitation shared with `checkOnOtherBinding`. **The
  ascription's type reference RESOLVES like any other TypeExpression**; the arm
  used to say "no references" and do nothing, so `prompt("x") as Nonexistent`
  validated clean. **The untyped-seam warning is deliberately CONSERVATIVE** —
  only an unascribed `let x = prompt(…)` with no declared type, nowhere else:
  **"we did not wire this position" is not the same fact as "the language cannot
  type this position", and only the second deserves a diagnostic.** `Currency`
  cannot appear bare in an example — it needs a `country` argument and does not
  resolve to `Real` underneath.

- **Entity intentions** — **grammar, not options, on purpose**: CM §4.2 calls
  options advisory, and a hard Error keyed off advisory metadata is a category
  error. The parser stores them via `EntityIntention.canonical` because
  **`Definition.equals` compares this field** — write order must never make two
  identical entities compare unequal. **Four event-sourcing rules are Errors**
  because replay must reproduce the same state changes, and **R1/R2 read the
  `yields` DECLARATION on the command's type, never `yield` statements in a
  body**. Two migration traps: `yields` exists ONLY on the kind-first form, so
  type-first commands must be reshaped; and R3 forbids `set` in `on init` while
  an empty body is a parse error, so the idiom is `on init is { yield event
  Created }` plus an `on event Created` clause doing the mutation.

- **Unified processor model / `streamlet` keyword** — **shape and intention
  participate in `Definition.equals`, so keep their `loc` at `At.empty` on every
  surface** (parser/BAST/JSON). **The `streamlet`/`processor` alternation must be
  FACTORED** — one `.!` capture across both keywords, not two branches, because
  `Keywords.keyword` ends in a cut and whichever branch matched first would make
  the other unreachable. **`AST.Streamlet.format` and `RiddlFileEmitter.openDef`
  are the same decision written twice** and must move together — canary BOTH.
  **The AST hierarchy is untouched**: `Streamlet` is the concrete case class,
  `Processor` the port-bearing supertype; the two have been confused here before
  at real cost. Corpus counting trap: a LOOSE grep scores 455 `processor` uses in
  riddl-models against 242 real declarations — 195 of the extras are `error
  "Unexpected message for processor X"` prose. **Grep the declaration SHAPE
  (`^\s*processor <Id>`), never the bare word.**

- **Numeric literals** — **the text is stored AS WRITTEN**: `1.50`, `007`, `+3`
  and `2E+8` are not recoverable from a parsed `Long`/`BigDecimal`, so a parsed
  payload would make prettify diverge from source on first use (same reasoning as
  `UniqueId.kindKeyword` and correlation keys). **JSON stores it as a
  `ujson.Str`, never a `ujson.Num`** — `ujson.Num` is a Double and would silently
  turn `1.50` into `1.5`, which a JSON-identity fixed-point test CANNOT catch,
  because a consistently-mangled value is still a perfect fixed point; assert the
  text. **`Integer` is signed, `Whole` is `>= 0`, `Natural` is `>= 1`** (Reid,
  2026-08-14) — until then the three had NO definition anywhere, and **a check
  cannot enforce a rule the language never states.** **Literals are held STRICTER
  than references, deliberately**: `NumericType.isAssignmentCompatible` stays
  loose and `NumericLiteralConformanceTest` pins that, so a later "tidy-up"
  reddens instead of silently changing behaviour far beyond literals. **`Bool
  extends IntegerTypeExpression`**, so put an explicit `Bool` arm first or a
  Boolean constant is told it "requires a whole number". **Never call `asLong` in
  a match guard** — the parser accepts unbounded digit runs, so a 20-digit
  literal throws `NumberFormatException` *inside the guard* and surfaces as
  `[severe] Exception Thrown` with no line number.

- **`Constant`** — `ConstantValue` is a NARROWING of `Value` (`LiteralString |
  NumericLiteral | BooleanLiteral | PromptValue`), deliberately not the full
  union, which would admit `Call`, `Ask` and `Initiate` in a constant. **The
  quoted numeric/boolean form is CONSUMED by the parser**, not merely deprecated
  — that is what makes its `autoFixable = true` honest and the round trip
  converge. A deprecation claiming `autoFixable` while prettify re-emits the old
  spelling is a lie a migration tool will act on.

- **`PromptValue.ascriptionFormat` was a SECOND, narrower copy of
  `emitTypeExpression` — the canonical instance of "a dispatch written twice".**
  Until 2026-08-15 only the four validated positions (`constant`, `let`, `set`,
  `when`) routed through `RiddlFileEmitter.emitValue`, whose fallback for every
  OTHER `Value` shape was `add(other.format)` — so a `PromptValue` nested one
  level deeper (a `Constructor`/`Call`/`Initiate` argument, an
  `InvariantCondition`'s `with` argument, a `LogicalExpression`/`NotExpression`
  operand) fell back into `.format`, reached the narrower dispatch, and emitted
  source riddlc cannot parse (`as any of {…}`, `as Currency(USD)`, `as table of T
  of [3,3]`, `as reference to entity E`). **`emitValue` is now TOTAL** over every
  `Value` shape that can contain a nested `PromptValue`, via
  `emitConstructorArg(s)`, `emitConstructorOperand` and `emitLogicalOperand` —
  the last of which **duplicates the parenthesizing rule of
  `LogicalExpression.format`'s private `paren` helper and must be kept in step BY
  HAND**, since that helper is private to `AST.scala`. `put` and `return` had no
  case at all and fell to the generic `addLine(statement.format)` arm.
  `PrettifyVisitor.doInvariant` had the same defect in both forms;
  `emitInvariantBlock` now puts one statement per line, the layout every other
  statement block uses (Reid ruled it in: RIDDL statements are
  whitespace-separated EVERYWHERE — `pseudo_code_block` has no separator — so the
  single-line rendering was never a
  deliberate choice — it was the narrow, un-synced second copy of the block
  dispatch, `AST.InvariantBlock.format` vs. the emitter, behaving differently
  from the other five).
  **`AST.scala` is in `language` and `RiddlFileEmitter` in `passes`, so the copy
  cannot call the original — the two must be kept in step by hand, which is
  precisely why this pattern keeps recurring here.** `ascriptionFormat` remains,
  unchanged, for the one place the emitter cannot reach: `.format`-based
  error-message rendering. Pinned by `TypedHoleContainerAscriptionRoundTripTest`,
  whose six cases were each verified to fail before their fix via `git stash`.
  **What is NOT fixed**: `checkPromptAscription` (validation) is still wired at
  only those four positions, so an ascription CONTRADICTING its position's actual
  expected type is silently accepted at `put`, `return`, `require … with`, and a
  `Call`/`Constructor`/`Initiate`/`TerminateStatement` argument — a missing
  check, not broken output. See BACKLOG § 1.
- **Inlet/outlet direction — the one people invert, Reid included (2026-08-16).**
  **An OUTLET is an exit and an INLET is an entrance.** A processor PLACES a
  message on its outlet; the connector carries it; the message ARRIVES at the
  receiver's inlet. Source of truth: `Connector(from: OutletRef, to: InletRef)`
  (`AST.scala:5232`), plus the CM's "validated on arrival … per-inlet ordering
  preserved" for Inlet and "name WHICH outlet they place the message on" for
  Outlet.
  **The reliable mnemonic is the arity table, not the words**: a `sink` has
  inlets and NO outlets. A sink only consumes, so an inlet must be an entrance;
  everything else follows. The inverted rule — "inlets push into a connector" —
  reads plausibly and survives casual checking because `send … to <portlet>`
  accepts BOTH kinds, so a sentence about "sending to an inlet" is grammatical
  and still wrong about direction.
  Consequence that keeps coming up: an extra INLET raises the inlet count, so a
  1-in/1-out `flow` that also hosts an `error-sink` inlet derives as a `merge`
  (≥2 inlets, 1 outlet) — never a `split`, which is ≥2 OUTLETS.


- **`empty` / `none`** — the rule is **minimum cardinality ZERO**: legal for
  `T?`, `T*`, `T{0,n}`; an Error for `T+`, `T{1,n}` and a bare `T`. That one rule
  is why ONE literal covers both the absent optional and the empty collection.
  Two traps worth re-reading before adding any `Value` arm:
  1. **The four throw-terminated walks are INVISIBLE to `-Werror`**
     (`countValueFailPoints`, `stateReadsIn`, `initiatesIn`, `asksIn`) — the
     terminal `throw` that enforces totality is itself what makes the match
     exhaustive. `-Werror` found three sites; the fourth threw at RUN time and
     aborted `checkStatementScopes` before the new checks could run. **Grep for
     `has no arm for` and add an arm to each.**
  2. **An optional trailing TypeExpression SWALLOWS THE NEXT STATEMENT.** An
     aliased type is a bare path and RIDDL statements are whitespace-separated
     with no terminator, so `set x to empty` followed by `set y to …` parsed the
     second as the first's ascription. Guarded by refusing statement-leading
     keywords (`statementStart`) — COMPLETE rather than heuristic, because a type
     can never be named a reserved word. The EBNF carries the same guard, or the
     two parsers disagree and TatSu reddens.

- **`tell` addressing an INSTANCE** — **the instance is NEVER resolved and
  nothing needs it** (Reid, 2026-08-22: *"You CANNOT know the specific instance
  at validation time, but fortunately you don't need to."*). Every question asked
  of a tell target is answered by the processor KIND the `Id` names. **An earlier
  "it needs a new resolution-output map" analysis was WRONG** — it assumed
  resolving a value target required the general value-typing machinery; reuse of
  a general helper is not the same fact as a capability being unavailable, so
  check which one you have. **`checkTellAddressing` is SKIPPED for a value
  target, and that is the feature**: it exists to recover the address
  structurally when the tell does not say which instance. **NOT entity-only**
  (unlike `terminate`): only an entity can be *ended*, but any processor can be
  *addressed*. **`send` takes no value TARGET** — it takes a portlet.
  **Diagnostics must use the bare PATH, not `ProcessorRef.format`**, which
  prepends the keyword and silently rewrites every existing message from `target
  'E'` to `target 'entity E'`.

- **`on quiescence <window>`** — **look the value window up with `oqc +:
  parents`**: ResolutionPass prepends the node being processed, so a header
  reference is recorded under the CLAUSE, and a lookup keyed on the handler alone
  misses it silently (the same `c +: parentsAsSeq` the correlation timeout block
  needs). Found by a failing test, not by reading. **`Declaration.ascription`
  renders the window** — the `on other as x` lesson again: rendering only in
  `format` makes prettify DROP it. It is an EFFECT block, unlike `on activate`/
  `on passivate`; event-sourcing's R3/R4 are UNCHANGED, so in an event-sourced
  entity it changes state only through a yielded event — which is also what keeps
  replay from re-firing the timer.

- **`send … at <instant>`** — **`send` ONLY**: `tell`'s target may itself be a
  value, so `tell m to x at t` already parses as a lookup. **Deliberately
  UNCHANGED, do not "fix" any of them**: A23's effect set (a scheduled send is
  still a transmission), the discharge rules (**a `send` has not settled `yields`
  since rc.19, scheduled or not** — this feature's plan claimed the opposite and
  the test caught it), A6 reachability (the channel must exist now; only the
  delivery is later), outlet ownership, portlet typing. **There is NO cancellation
  construct** — the idiom is schedule to YOURSELF and decide at fire time, so a
  receiver must tolerate a stale scheduled message, and **that idiom's loop
  connector is legal** (it drew `stream-graph-cycle` for a few hours until the
  rule was re-ruled). **`aliasFreeTypeExpr` follows ALIASES only, never
  cardinality** — `TimeStamp?` is not an instant, `Duration?` is not a window —
  and carries the `eq` visited list, which `isDurationTypeExpr` shipped for a day
  without.

- **A message delivered where nothing can receive it — two CompletenessWarnings**
  (rc.21+). `checkTellDeliverability` is the SENDING end (a `tell` whose target
  declares no clause receiving that type); `checkInletsAreReceived` is the
  RECEIVING end (a processor declares `inlet I is type T` and handles `T`
  nowhere). Not redundant — one needs a delivery to exist, the other fires on the
  declaration alone.
  **The receiving-end question had to be RESTATED before it could be built**, and
  the restatement is the durable part: "an inlet no handler consumes" relates two
  things that are never directly related. Handlers do not consume, they CONTAIN
  `on` clauses, and an `on` clause names a MESSAGE TYPE, never an inlet. Nothing
  in the AST links the two; the relation is INDIRECT, through the type.
  **`on other` satisfies both** — it states a policy for anything unmatched, and
  is the idiom `Riddl.BottomlessPit` is built from. Both reuse ONE helper
  (`receivesMessageType`) rather than a second copy of `validateAsk`'s identical
  logic; `validateAsk` now calls it too.
  **Two interactions found by RUNNING it, not reading it:** a deliberate-discard
  sink is now exempt from *"contains only 'do' statements"* (otherwise the two
  checks form a demand no legal spelling satisfies — same trap as the adaptor
  advisory in `c075f1af0`); and `checkInletsAreReceived` is silent when a
  processor declares NO handlers at all, because *"should have a handler"*
  already reports that — adding the exclusion took fixture churn from 7 edits to
  zero, which is evidence the existing diagnostics covered those cases.
  **Corpus cost 6,379 + 906 across 190 models — 84% of all tells — and they are
  TRUE POSITIVES.** Verified by hand before reporting: the corpus idiom is *tell
  the event to the entity, handle it somewhere else*. Migration filed in
  riddl-models. Reid: *"Correct is correct."*

- **`resolvePath` had NO `ClassTag` and cast unchecked, for the whole life of the
  function.** `T` erases, so `pathIdToDefinition(...).map(_.asInstanceOf[T])`
  always "succeeded" and returned a definition of the WRONG kind typed as `T`.
  Nothing failed there; the `ClassCastException` fired at whichever caller first
  touched a `T`-specific member — and only for callers that touch one, so the
  same mistyped value crashed one model and passed silently through another. **A
  crash whose occurrence depends on which check ran first is this shape.**
  `ReferenceMap.definitionOf` does the same job correctly with a `ClassTag`; the
  two resolution paths disagreed about whether to check. Returning `None` loses
  no diagnostic — `ResolutionPass` reports a wrong-kind path first.

- **`forward` is the ONLY statement that discharges by passing on.** Legal ONLY
  in a clause handling a command that declares `yields` or a query that declares
  `replies` — **you cannot delegate an event or a result** (author's ruling):
  those record what happened and owe no answer. The operand's TYPE must match the
  handled message; its VALUES need not. NOT terminal: a `yield`/`reply` after it
  is an Error, a `send`/`tell` after it a style warning.
- **What DISCHARGES a `yields`/`replies` obligation NARROWED at rc.19, and this
  is the part that breaks models.** Only `yield`/`reply`, `error`/`require`, and
  `forward` settle a path. A `send`/`tell` no longer does — **neither of the
  handled message nor of a different one** — retiring the previous "emitting ANY
  message settles a path" allowance and the event-sourcing example defending it.
  Two corpus shapes need DIFFERENT fixes and a bulk edit must not conflate them:
  a handler that passes the message on becomes `forward` (mechanical), while one
  that declines by emitting a `*Rejected` event cannot forward anything and needs
  an explicit `error`/`require` — a semantic change.

- **`error` AND `terminate` are TERMINAL in their block; `require` is not.** A
  statement after either is unreachable and an Error. `error` REFUSES,
  `terminate` DESTROYS the instance — same rule, different reasons, and **the
  message must state the one that applies**. `require X` refuses only when X
  fails, so statements after it are ordinary. Per statement LIST, recursing into
  `when`/`match`/`foreach` bodies as their own lists. **`on term` needs no
  exemption**: it is a different list, and it runs BECAUSE of the termination
  rather than after it.
  **The `terminate` half was missing for a full release, and that is the
  lesson.** rc.19 shipped the `error` half and reordered 268 corpus statements
  for it, while a `set state` sitting after a `terminate` in reactive-bbq
  survived that pass and every validation since — because the check matched
  `ErrorStatement` alone. riddl-models found it BY EYE. **When a rule is about
  unreachability, ask what ELSE ends a block**; enumerating one terminator is how
  the next one stays invisible.
  **Do not "simplify" this by matching the two terminators together.** That was
  the reported suggestion and it is the smaller change; it also yields a TRUE
  diagnostic with a FALSE explanation, telling an author their `terminate`
  "refuses" and offering `require` as the conditional alternative, which is not a
  conditional `terminate` at all. `BlockEnder` carries each terminator's own
  reason and advice. Same trap as A23 borrowing A26's effect set: **a check
  inherited wholesale stops answering its own question.**

- **A23 ("refusals first") asks a DIFFERENT question from A26, and its effect set
  was borrowed from A26 for months.** A26 asks *is this pure?*; A23 asks *would
  refusing now leave a partial change?* Narrowed 2026-08-19 to LOCAL state
  transformation: **`set`, `morph`, `terminate` are effects; `send`, `tell`,
  `yield`, `put` and `become` are not.** Transmissions leave nothing partial
  HERE — any state they cause is elsewhere and later, a remote "maybe" that is
  acceptable for a locally immutable statement — and `become` is a BEHAVIOR
  transition, not a state one. **The narrowing is load-bearing**: without it,
  making `error` terminal left the corpus's "refuse AND publish a rejection
  event" idiom illegal in BOTH orders, i.e. inexpressible. When a check is
  borrowed wholesale from another, re-derive it from its own question.


- **`option snapshots` (Entity, event-sourced only)** — the option says WHETHER
  journal-derived snapshots are taken, never how; no policy enum and no interval,
  because whether snapshotting pays turns on update rate, read/write mix and
  physical layout, none of which is in the model. **Its ABSENCE is the default
  and is meaningful: take NO snapshots, replay the whole log** — right more often
  than it looks, since many entities see under a hundred events in their
  lifespan. An Error on a non-event-sourced entity. **The CM gained a
  must-preserve with it: state as of any past point must be reconstructible**, so
  a current-state row kept as an optimization is fine but one that is the ONLY
  reconstruction mechanism is not.
- **A clause that answers should handle a message that DECLARES what it answers
  with** — StyleWarning, not an Error (author: it *"doesn't rise to the level of
  an error"*). The converse is already an Error in all four combinations (declare
  and produce nothing; declare and produce the wrong type; command and query
  alike), so do not add a check for it.

- **AST.Set shadows scala.Set** — use selective imports or qualify as
  `scala.collection.immutable.Set`.
- **Schema match ordering** — Schema extends `Leaf` (Definition) but is also in
  the `NonDefinitionValues` union, so its case must appear BEFORE `case _:
  NonDefinitionValues`. Same trap for `Relationship` vs `case _: Definition`.
- **State is a Branch**, not a Leaf, of `Branch[StateContents]` where
  `StateContents = Handler | Comment`. `PassVisitor` uses
  `openState`/`closeState` (not `doState`). ResolutionPass prepends State to
  parents (as with all Branches), so refMap keys for State's type ref use State
  as parent, not Entity.
- **`do "..."` is an alias for `prompt "..."`** — both produce `PromptStatement`.


- **`not` and `!` are SYNONYMOUS everywhere**, as the inverse of a boolean
  expression (ruled 2026-08-14, shipped 2026-08-15). Both build the IDENTICAL
  `NotExpression` — there is no spelling flag anywhere, so two ASTs meaning the
  same thing can never compare unequal — and both work wherever a boolean
  expression does. **This OVERRODE a 2026-08-13 ruling** that `!` was a legacy
  spelling accepted ONLY as `when !<bare-identifier>` and "will not be extended";
  that reasoning is retired, not merely superseded — **do not restore it.** The
  parser guards the `!=` case with `"!" ~~ !"="` (fastparse negative lookahead,
  no regex — unavailable on Scala Native). **Prettify converges `!` to `not`** —
  same precedent as `A | B` → `one of { A or B }` — while a `!=` comparison is
  untouched, being a comparison operator rather than a negation.
- **walkStatements helper** — private in ValidationPass; walks into
  `WhenStatement`/`MatchStatement` nesting.

- **Accessors see through the provenance wrappers; `Finder` sees through
  everything.** The 35 `contents` accessors (`context.entities`,
  `domain.contexts`, `handler.clauses`, …) use `Contents.filterThroughWrappers`,
  which descends **`Include` AND `BASTImport`** — the same two `flatten()`
  removes. HOW a definition reached a container is riddl's bookkeeping; a client
  asking what is in a context wants the whole list. Three rules follow:
  1. **`Contents.filter` stays literal** ("my direct children"), and `includes`
     must keep using it, since the wrapper is matched BEFORE the type test.
     `vitals`/`processors` also stay literal — their callers (DiagramsPass,
     StatsPass) already reach included definitions another way and would double
     count. Reasons are recorded at each in `Contents.scala`.
     **`definitions` was the third of those and is transparent as of
     2026-08-06** (synapify's task), with `directDefinitions` added as the
     literal form. That change disproved the rule the old comment stated — "make
     it transparent AND delete the caller's manual walk". ResolutionPass's walk
     descends `Include` and deliberately NOT `BASTImport`, and
     `filterThroughWrappers` cannot express "includes but not imports", so
     **ResolutionPass keeps its walk and reads `directDefinitions`** (7 sites);
     making it transparent would have made imports resolve, breaking rule 2.
     Three validation checks moved with it: `checkContents` and
     `checkIncludeHygiene` stopped emitting two FALSE warnings (a container whose
     content all arrived by include was told it "should have content"), and
     `checkUniqueContent` STARTED reporting duplicate sibling names across an
     include boundary — a real ambiguity, approved as a deliberate tightening
     (Reid, 2026-08-06). It cost the corpus nothing: 189/189 riddl-models
     validate with zero errors. Pinned by `IncludeTransparentValidationTest`.
  2. **READING and RESOLVING answer differently for imports, on purpose.**
     `domain.types` reports a `.bast`-imported type, but a reference to it does
     NOT resolve until an explicit `flatten` — the symbol table is built by
     traversal, not by these accessors, and S61-2's contract that loading only
     fills wrappers is unchanged. Structure is likewise untouched:
     `contents.filter` still shows nothing spliced in, and `BASTLoader.getImports`
     still finds the wrapper. Pinned in `BASTImportLoadingTest` and
     `IncludeAndImportTest`.
  3. **`Finder.recursiveFindByType` and the accessors answer DIFFERENT
     QUESTIONS** — it walks EVERY `Container`, the accessor walks only the
     provenance wrappers. They diverge under a **Domain** (domains DO nest,
     `domain_content`, ebnf-grammar.ebnf:77) and for `Type` under a Context,
     since a recursive find also picks up types declared inside entities —
     riddl-generator relies on exactly that to emit state records. They do NOT
     diverge for `Entity` under a `Context`, because contexts cannot nest
     (`context_definition` :85 omits `context`, `entity_content` :96 omits
     `entity`, and `processor_definition_contents` has no `entity`). Pick by the
     question, not by reflex — an earlier version of this note warned that
     recursive find "returns nested contexts' entities", which the grammar
     forbids; riddl-generator caught it.
  Before 2026-08-03, `context.entities` was empty whenever the entity lived in an
  include — silently. That is how riddl-generator produced 582 files for
  reactive-bbq with no entity class among them while the model validated clean.
  It survived because riddl validates by TRAVERSING and every internal test took
  that path; the consumer path had no gate at all.
  `ConsumerReadsIncludedDefinitionsTest` is now that gate — **add to it whenever
  you add an accessor.**

- **A case class that transitively reaches a DOCUMENT has an O(document)
  hashCode, and only Scala.js notices.** `StringParserInput`'s first field is
  `data: String`, the entire text of a source file; `At` holds a
  `RiddlParserInput`, `Identifier` and `Definition` hold an `At`, and
  `ReferenceMap.Key` holds a `Definition` — so every refMap add and lookup hashed
  a whole source file, twice per `Definition.hashCode`. The JVM and Native
  memoise `String.hashCode` into the string object and never noticed; a JS string
  cannot carry that field. Measured on a 139KB source: 14ns (JVM), 1ns (Native),
  **181,187ns (Scala.js)**. Fixed by memoising on the parser input
  (`RiddlParserInput.cachedHashCode`) — one field per FILE, nothing per node —
  taking Scala.js `Definition.hashCode` from 384,016ns to **217ns**, at parity
  with the JVM. **The tell was the RATIOS, not the totals**: parse cost 3.2x on
  Scala.js while Resolution cost 97x, and ordinary overhead is uniform — when one
  number is 30x the others on the same runtime, the runtime is doing something
  different, not the algorithm. Get the cross-platform ratio BEFORE profiling.
  (Both the report and our first hypothesis blamed complexity; the favourite
  suspect, ClassTag dispatch, measured **5x faster** on Scala.js than the JVM.)

- **Definition hashCode/equals override** — `Definition` overrides both:
  `hashCode` cheap (id + loc + class); `equals` structural via `productEquals`,
  skipping `Contents` fields. Prevents O(subtree) hashing in any
  `HashMap[Definition, X]`. The opaque type `Contents[?]` erases to `ArrayBuffer`
  at runtime, so `case (_: Contents[?], …)` matches correctly.

### Diagnostic rule ids — every message names the RULE that produced it

**`RuleId`** (`language/.../RuleId.scala`) is a kebab-case, subject-prefixed
enum: 303 rules covering all 307 diagnostic sites. `Message.ruleId:
Option[RuleId]`, and `ruleId` is a **REQUIRED** parameter on the eight
`Accumulator.add*` helpers — a new diagnostic does not compile until it names its
rule. The six calls in `MessagesTest` pass `None` explicitly.

**It GENERALIZES `Messages.DeprecationCode`; it does not sit beside it.** That
object was already a threaded kebab-case id registry for deprecations, consumed
at `RiddlLib.scala:970` to build `SourceEdit`s. Its 12 codes are reproduced
EXACTLY — including `prompt-statement`, whose rule was renamed `DoStatement`
while its code deliberately was not, because renaming a rule is a source change
and renaming its code is an API break. **Do not introduce a second scheme** (an
early draft proposed `REF001`-style ids; dropped for exactly this).

**An id names a RULE, not a site.** Four rules are emitted from more than one
place on purpose — `ref-wrong-kind` from BOTH `ReferenceMap.definitionOf` and
`ResolutionPass.wrongType`, apt given those two paths once disagreed about
whether to check the kind at all.

**Non-reuse is enforced by CODE, in three parts** (all canary-tested by breaking
them): `values` is generated so codes are checked unique; `RuleId.retired` names
withdrawn codes and no live code may appear there; and a committed **append-only
ledger** (`language/src/test/resources/rule-ids.txt`) catches what the in-memory
checks cannot see — a rule DELETED without retiring its code, the one at risk of
being reused later. **`RuleId.grandfathered` is CLOSED**: the 12 legacy codes
predate the subject scheme and are exempt from it. A new rule that fits no
subject needs a SUBJECT added, never an exemption.

**Why the enum at all**: `DeprecationCode.all` was a hand-maintained `Seq` beside
the definitions, and TWICE a code was defined but never added to it —
`entity-option-to-intention` for months — so "exhaustive" migration reports
silently omitted a whole family. `all` and the mechanical-replacement map are
DERIVED now; there is no second list to forget.

**The id renders in the LOGGER, not in `Message.format`.** The logger already
supplies the kind prefix, so output reads `[error] [use-unused-definition]
file(...)`, rustc's shape. `format` is what `CheckMessagesTest` compares its 13
goldens against, so putting it there churned every one of them for a fact those
files do not exist to pin. **`--no-msg-ids`** (`CommonOptions.showMessageIds`,
default TRUE) restores the previous output exactly.

**`validate --json`** emits one object per diagnostic on stdout (rule, severity,
message, file, line, col, and context/suggestion when present); `[]` when clean,
never empty output. **`validate --fix` / `--fix-rule <id>`** applies the codemod
a rule carries (`RuleId.mechanicalFix`), through the SAME gate as `find
-replace` — `FindEditor.applyVerified`, lifted so there is one copy rather than
two. Only PURE SPAN replacements qualify: `type-first-aggregate` is a reordering
and `shape-keyword` inserts outside the reported span, so both are excluded
rather than approximated. See BACKLOG [1.16] for `quoted-constant-literal`,
genuinely mechanical but needing a COMPUTED replacement an `Option[String]`
cannot express.

**`FindEditor.fileOfSource`, never `Path.of(loc.source.origin)`.** `origin` is
the SHORT name error messages render, so treating it as a path works only when
the cwd happens to be the model's own directory — how `find -replace` originally
shipped, and a bug `validate --fix` nearly reintroduced the same day.

### A bare `println` is invisible to a test that redirects stdout

**`println` is `Console.println`, and `Console.out` is a THREAD-LOCAL initialised
at class load.** `System.setOut` therefore does not redirect it, and code
printing from inside a `Future` — on an executor thread — writes to the real
stdout regardless. In production the two name the same object and nothing is
wrong with the output; **under capture the test reads an empty string, which
presents as exactly the "command printed nothing" defect** the
`ValidateSummaryTest`/`ProductGoesToStdoutTest` family exists to detect. A false
positive from the instrument, not the code.

**Emit a command's product with `System.out.println`.** `ValidateCommand.emitJson`
and `DumpCommand.emit` both do. `StdStreamCapture` also wraps `Console.withOut`,
which closes the same-thread half but CANNOT help across threads — the
`System.out` form is what does.

### Multi-line `do` and `prompt` (rc.25+)

`do { "a" "b" "c" }` and `prompt({ "a" "b" })`, with the bare single-string form
unchanged. The braced shape is **`doc_block`'s**, already RIDDL's spelling for
prose, so no new syntax idiom was invented. **The bare form takes EXACTLY ONE
string**: `do "a" "b"` by juxtaposition parses unambiguously (nothing else begins
with a quote) but leaves nothing except the next keyword to mark where the
statement ends.

`DoStatement.what` and `PromptValue.prompt` are `Seq[LiteralString]`; **`.text`**
derives the `\n`-separated prose riddlg reads. Derived, not stored, so there is
no second field to disagree — and a single-line `do` is a Seq of one rather than
a special case.

**Additive at every layer, and that is load-bearing.** A one-line `do` prettifies
byte-identically to before and serializes as a bare JSON string rather than an
array, so none of the corpus's 190 models move for a feature they do not use.
Several lines get ONE PER LINE inside braces — the layout every other block uses;
squashing them onto one line would be the narrow second copy of a block dispatch
`InvariantBlock` was already caught being. **BAST `FORMAT_REVISION` 23**: both
now write a SEQUENCE where they wrote a bare string, so a revision-22 file's
string is read as a COUNT and everything after it derails. The JSON reader
accepts a string OR an array, so nothing already written stops loading.

### Parsing (fastparse)

- **`Keywords.keyword` ends in a CUT — `P(key ~~ &(isNotKeywordChar))./` — so
  once the keyword matches, the enclosing `|` CANNOT backtrack.** Whichever
  alternative comes first wins outright and the others are unreachable. That is
  how `attachment ULID is "…"` could not be parsed AT ALL: the general attachment
  rule was first, so `ulidAttachment` was never tried and the ULID form failed
  where a mime type was expected. **Reordering only breaks the other branch the
  same way — the shared prefix must be FACTORED**, matching the keyword once,
  ahead of the choice, and alternating the BODIES
  (`ulidAttachmentBody | namedAttachmentBody`). `bastImport` was already written
  this way, with a comment describing the identical hazard. The same cut
  collision is why an optional leading marker needs a non-cutting variant
  (`Keywords.maybeInitial`), and why `on event`/`on <msg>` must be ONE parser
  branching on the parsed ref via `flatMap` rather than two `on …` alternatives.
  **Symptom to recognise:** a documented piece of syntax that has never worked,
  with an error naming what the OTHER branch expected.
- **Test the alternation; do not read it.** fastparse aggregates its failure set
  at the FURTHEST position reached, which is not the same thing as "what is
  allowed here". `tell p` reported `Expected one of ("become" | "command" |
  "event" | "morph" | …)` — mixing statement keywords with message-kind keywords
  — which reads like `tell` is banned in that clause. It was not; the OPERAND was
  the problem. A three-line experiment settled in seconds what two people read in
  opposite directions.
- **A `rep(2)` that looks like a semantic guard usually is not.**
  `sagaDefinitions` read as "a saga needs two steps"; the real rule is in
  `ValidationPass`, with a proper Error and a suggestion. Relaxing the parser lost
  no rule and UPGRADED the diagnostic — a parse failure at the wrong token became
  a message that says what is wrong. Check for this shape before assuming a
  parser cardinality is load-bearing.

### Total Dispatch — no silent fall-through

**Reid's standing rule (2026-08-09): "There must be no non-sealed matches — it is
okay to fall through to generate an error or exception but not okay to not select
anything and then carry on as if nothing happened."**

A `case _ => ()` on a SEALED hierarchy is the failure mode: it compiles, and when
a new node type is added the code quietly does nothing for it. Every symptom then
appears far from the cause — an empty output, a dropped statement, a model that
validates clean and means something else.

- **Enumerate the cases by READING, because nothing checks it for you. `-Werror`
  is NOT a safety net here.** This file claimed it was until 2026-08-13; the
  claim is false as this repo is configured, and believing it is how the
  processor-instance-identity branch shipped seven missed dispatch or
  dispatch-input sites — every one caught by a human reading code or a code
  review, **none** by the compiler. Two independent reasons, the second being the
  important one:
  1. `language` and `commands` compile with `--no-warnings` alongside `-Werror`
     (`build.sbt:229`, `:417`), so in those two modules there is no warning left
     to escalate. (An earlier note named `passes` and `riddlLib` as well —
     wrong; check `build.sbt` before repeating it. `-Werror` really is live in
     those two.)
  2. Where `-Werror` IS live it still cannot help, because **a wildcard arm makes
     a match exhaustive** — so the terminal `throw` this section prescribes is
     itself what silences the compiler. Follow the rule and you are guaranteed
     never to be told the hierarchy grew. Most of the seven were in `passes`,
     where warnings are on.
  The real net is that `throw`, and it fires at RUN time on the first test that
  exercises the missing arm — so it protects you exactly as far as your tests
  reach, and not one node further. When you add a node type, grep the dispatches
  and read them; do not wait to be told.
- **When a branch genuinely cannot be reached, `throw`** rather than return unit.
  `Pass.processValue` does this; so do `BASTWriter`/`BASTReader`, which previously
  used a `println`-and-drop and a placeholder `PromptStatement` respectively —
  both of which produced corrupt output instead of a failure.
- **`case _ => ()` remains correct for "not interested in this node"** — a
  visitor handling three of forty types. The test is whether the arm means
  *"nothing to do here"* or *"I do not know what this is"*. Only the second is
  the bug.
- **Enumerate the domain of the FUNCTION, not of the nearest-looking type.**
  `stateReadsIn`/`asksIn`/`countValueFailPoints` walk what `statementValues`
  yields, which is WIDER than `Value`: `WhenStatement.condition` alone is
  `LiteralString | Identifier | ValueRef | BooleanExpression | PromptValue`, and
  `Identifier` appears in no other member. Auditing `Value` exhaustively
  therefore still misses it — which is exactly how `when !isValid`, a form that
  validated on rc.11, threw on rc.13. The throw did its job; the enumeration was
  against the wrong hierarchy.
- **A total walk is still defeated if its INPUT drops a field.** Auditing the
  match arms proves nothing about the fields each arm forgot to RETURN.
  `statementValues` was total over the statement kinds and nonetheless never
  yielded `RequireStatement.argument` (the `with <expr>` operand) or
  `MatchCase.guard` — both full `Value`s — so an `initiate` parked in `require X
  with initiate entity Order` was invisible to every walk built on it at once:
  state-reads, asks, the A12 fail-point census, and the instance-effect ban that
  was itself written correctly. Check the arms AND their payloads.
- **A dispatch written TWICE hides the incomplete copy behind the complete one.**
  `AST.WhenStatement.format` had four arms over a five-member `condition` union
  (no `PromptValue`), so `when prompt("…")` threw a `MatchError` — and it
  survived because `PrettifyVisitor` does NOT route through it:
  `RiddlFileEmitter.emitStatement` keeps its OWN copy of that dispatch, and that
  copy has the arm. So the reflectivity round trip, which is what normally proves
  a `format` total, could never reach the hole; prettifying the construct
  produced correct output on the released binary. **When you find two
  implementations of one dispatch, the tested one tells you nothing about the
  other — read both.** `Statement.format` and `RiddlFileEmitter.emitStatement`
  are that pair; keep them in step.
- **Fix the SHAPE of a dispatch/recursion defect, not the instance.** The
  alias-chain cycle guard was added to `fieldsWithOwner` in rc.14 and its sibling
  `aggregateFieldsOf` was left unguarded, so `type A is B` / `type B is A` still
  killed the stack — latent until a caller reached a cyclic alias. When fixing a
  defect of this class, grep for the shape.

**A field-drop defect has no natural blast radius, and `Finder` is where it
lives.** `Finder.recursiveFindByType` walked `contents` only, so **27 field-held
sites were unreachable** — `MatchStatement`'s cases and guards,
`Correlation.timeoutStatements`, `SagaStep`'s do/undo blocks,
`RequireStatement.argument`, `InvariantBlock`, `PromptValue.typeEx`, the
`Constructor`/`Call`/`Initiate` argument lists, the
`LogicalExpression`/`NotExpression` operands. Anything reading the AST through
`Finder` rather than a `Pass` silently returned SHORTER LISTS; nothing errored.
The consumers most exposed are the ones that ENUMERATE rather than traverse, i.e.
riddl-generator. Consolidated into `Finder.fieldChildren` (`Finder.scala:86`) —
one extension point instead of four scattered special cases — **which still ends
in `case _ => Seq.empty`, so arm 12 of `Value` will be invisible on the day it is
added.** The lesson is about detection, not the fix: it surfaced because ONE BAST
test looked for a `ComparisonExpression` inside a `when` condition and got
nothing back. **The instance you notice is the one your test happened to walk,
not the extent of the problem** — which separates this family from a dispatch
defect, where the compiler at least knows the arms exist.

Known-total today: `Pass.processValue`, `classifyHandlers` (all 17 `Statement`
kinds), `countValueFailPoints`, BASTWriter/BASTReader statement dispatch. The
remaining ~140 catch-alls are unaudited — see BACKLOG § 2.

**A new `Value` arm touches EIGHT sites, not five** (counted 2026-08-15 adding
`NumericLiteral`; the plan said five and `-Werror` found three more). Beyond
`ValidationPass`'s four walks (`countValueFailPoints`, `stateReadsIn`,
`initiatesIn`, `asksIn`) and `validateValue`, there are:
**`AST.NonDefinitionValues`** — a parallel union to `Value` that is easy to miss
entirely — **`ValidationPass.valueType`**, and **`JsonifierPass`** in `riddlLib`.
Widening **`Comparand`** is a SEPARATE family: `resolveComparand`,
`serializeComparand`, `buildComparand`, plus the BAST writer/reader pair. Grep
and read; do not trust a five-item list.

**A catch-all that "just works" is how a literal disappears.** Before its arm was
added, `JsonAstBuilder.buildComparand`'s pre-existing `case other =>
ValueRef(curAt, PathIdentifier.empty)` silently degraded a numeric comparand into
an empty reference — no error, no warning, a valid-looking wrong answer. A live
instance of the unaudited catch-alls above, not a hypothetical.

### Emptiness — `isEmpty` means NO CONTENTS, never "absent"

**Reid has been bitten by this repeatedly while developing RIDDL, and it can make
EVERYTHING fail if implemented wrong. Read this before touching `isEmpty` or
before "fixing" a spurious emptiness warning.**

- **The contract**: `RiddlValue.isEmpty` defaults to **`true`**, documented at
  `AST.scala:98` as *"non-containers are always empty"*. Emptiness asks whether a
  node HAS CONTENTS. It does **not** ask whether the author supplied it, and it
  does **not** mean "all optional fields are None".
- **Overrides belong on CONCRETE case classes** that genuinely have contents, and
  should fold in their parents' `isEmpty` result. Traits with no members of their
  own generally need nothing — auditing every subclass is the wrong sweep.
- **`Statement` deliberately inherits the `true` default.** Statements have no
  bodies, so they are ALWAYS empty, and it never matters: they are leaves that
  traversal never descends into.
- **Among the `Value` kinds, only `LiteralString` overrides it** (`:181`,
  `s.isEmpty`) — the one Value whose emptiness is a real question, because an
  empty string IS the author writing nothing. `Call`, `Ask`, `Constructor`,
  `ValueRef`, `GetValue` and `BooleanLiteral` are non-containers and correctly
  report empty ALWAYS.

**The gotcha this produces.** `checkNonEmptyValue` (`BasicValidation.scala:279`)
asks `value.nonEmpty`, so it is meaningful ONLY for a `LiteralString`. Eight of
its ten call sites in `ValidationPass` honour that — they pass a `LiteralString`
field (`PromptStatement.what`, `ErrorStatement.message`, `CodeStatement.language`,
`LiteralPattern.literal`, `PromptValue.prompt`) or guard with `case ls:
LiteralString =>`. Two sites passed an arbitrary `Value` unguarded and therefore
fired on correct code: `let`'s expression and `set`'s value, so `let q = call
function F(…)` and `set field S.flag to true` were both reported "must not be
empty". Fixed 2026-08-10 by guarding both on `LiteralString`; pinned by
`ValueEmptinessCheckTest`.

**The trap to avoid.** The tempting "fix" is to override `isEmpty` on
`Call`/`Constructor`/`ValueRef`/`BooleanLiteral` so they report non-empty. That
REDEFINES emptiness from *contentless* to *present*, a different question and the
one the whole traversal/flatten layer depends on. **When an emptiness check
misfires, the bug is almost always in the CALLER asking the wrong question, not
in the node's `isEmpty`.** Non-literal values get their real validation —
resolution and type-checking — in `checkStatementScopes`.

### Pass Framework & Standard Passes

- **OutlinePass / TreePass** — lightweight `HierarchyPass` subclasses in
  `passes/shared/.../passes/`. OutlinePass → flat `Seq[OutlineEntry]`; TreePass →
  recursive `Seq[TreeNode]`, exposed via `RiddlAPI.getOutline()`/`getTree()`.
  TreePass uses a `mutable.Stack[ListBuffer[TreeNode]]` for pure O(n) building
  (not a `HashMap[Definition, ListBuffer]`).
- **Analysis passes** — MessageFlowPass, EntityLifecyclePass,
  DependencyAnalysisPass (1.22.0), in `passes/shared/.../analysis/`; each extends
  `CollectingPass` and requires ResolutionPass. (AIHelperPass was removed in
  1.24.0 — see "Message suggestions" under Validation Specifics.)
- **MessageFlowPass** — `MessageFlowEdge.messageType` is `Option[Type]` (adaptor
  declarations produce `None`; typed handler edges produce `Some`).
  Direction-aware: `InboundAdaptor`("from") → producer=referent,
  consumer=source; `OutboundAdaptor`("to") → producer=source, consumer=referent.
  `MessageFlowOutput.edgesForDomain()`/`edgesForContext()` take a `SymbolsOutput`
  for parent-chain walking.
- **UsageResolution** uses `mutable.Set[Definition]` for `uses`/`usedBy` (was
  `Seq`). API boundary methods (`getUsers`, `getUses`) return `.toSeq`.
- **ParentStack is a class**, not a type alias — use `ParentStack.empty`. Same
  API (push, pop, toParents); it caches `toParents`.
- **ValidationMode enum** — `Full` or `Quick`. Quick skips `checkStreaming` and
  `classifyHandlers` in postProcess.
- **IncrementalValidator** caches messages per-Context using FNV-1a fingerprints;
  `validator.reset()` forces a full recheck.
- **RecognizedOptions registry** validates option names, argument counts and
  parent types; unrecognized → StyleWarning. **This registry is the ONLY thing
  validation consults.** The `KnownOptions.*` lists
  (`language/.../KnownOptions.scala`) have **no consumers anywhere** — they are
  advisory/reference data exported to JS via `@JSExportTopLevel`. Adding a name
  there does NOT clear a warning; adding it to `RecognizedOptions.registry` does.
  Keep both in sync anyway, since `KnownOptions` is public API.
- **Generator-metadata options** (1.30.0, 1.31.0) — riddl-gen and friends drive
  output from RIDDL metadata, with names prefixed for their target so they are
  self-describing: `protocol` (AsyncAPI), `event_catalog_version`,
  `sql_dialect`/`sql_table`, `backstage_owner`/`backstage_lifecycle`/
  `backstage_type`, `confluence_space`/`confluence_parent`. These parse fine
  unregistered but draw a spurious "not a recognized RIDDL option" StyleWarning.
  **Choosing `validParents`:** `Seq.empty` when the generator resolves the value
  by walking UP the parent chain, so it is legitimately settable at any level
  (the common case); a specific list (e.g. `Seq("Domain")` for `confluence_*`)
  when the generator reads it from exactly one kind of definition, so a misplaced
  option gets a "not typically used on X" nudge instead of passing silently.
  Registering one is ~3 edits: `KnownOption` constant, `KnownOptions.*` list
  membership, registry entry, plus a `CompletenessTest` case.
- **RiddlLib analysis API** — `getHandlerCompleteness()`, `getMessageFlow()`,
  `getEntityLifecycles()` on the shared trait and JS facade. The JS facade
  returns `""` for the untyped (None) MessageFlow edges.
- **Path-identifier usages tracked separately** (1.23.1).
  `ResolutionPass.resolvePathFromAnchor` calls `associatePathUsage(parents.head,
  intermediate)` for each anchor + non-terminal component, into the
  `usesInPath`/`usedInPathBy` maps on `UsageBase`. Existing `uses`/`usedBy`
  semantics are intentionally unchanged so `Usages.getUsers` and
  `AnalysisResult.getUsers` don't shift underneath callers. Filtered against
  `user eq use` and `parents.exists(_ eq anchor)` so internal self-references
  don't leak in. Public accessors: `Usages.isUsedInPath(d)`/`getPathUsers(d)`.
- **Path-only usage triggers a CompletenessWarning** (Types only). When a Type's
  `usedBy` is empty but `usedInPathBy` is non-empty, `UsageResolution.checkUnused`
  emits "only referenced in path identifiers" — the type is addressable but
  cannot carry data, because nothing declares a field or state of that type.

### Validation Specifics

- **`validateType` skips its type-expression walk for a TOP-LEVEL aggregate, so a
  whole family of checks fires only on nested inline ones.** The guard is `if
  !t.typEx.isInstanceOf[AggregateTypeExpression]` (`ValidationPass.scala:2752`),
  so `checkAggregation` and `checkAggregateUseCase` run for `f: command { … }`
  and NEVER for `command X is { … }` — that is, never for any aggregate a model
  actually writes. This is why a duplicate field name validated clean AND
  survived an idempotent prettify round trip until 2026-08-19: putting the new
  check with its obvious neighbours made it fire on NOTHING, and the tests stayed
  red in a way that looked like the check was broken. Their neighbours (field
  naming, identifier length, metadata) share the blind spot and **nobody has
  audited what else that guard silently excludes.** When a new aggregate check
  appears to do nothing, suspect the guard before the check.

- **Connector intentions (`persistent`, `at-least-once` | `at-most-once`)** —
  keywords written BEFORE `connector`, two independent groups, mutually exclusive
  within a group (an Error, not a parse failure, so both keywords can be named).
  **Absence of a delivery keyword means `at-least-once`** — CM §25.7 already said
  so, so nothing was invented and an absent keyword draws NO warning;
  `at-most-once` exists to make that section's "knowing downgrade, never a silent
  one" enforceable. **ORDERING is deliberately NOT an intention**: §25.7 makes
  `unordered` "permission, not mandate" with a best-effort obligation, which is
  the definition of advisory. The admission test for the enum is whether a
  generator may decline to honour the keyword.
  `option persistent` is deprecated and **CONSUMED** into the intention by the
  parser, which makes the round trip converge and migrated 430 corpus uses for
  free. **Ask `Connector.isPersistent`, never `hasOption("persistent")`** — it
  accepts both spellings, and three validation gates go through it.
  Two traps this hit: inserting the enum between `@JSExportTopLevel("Connector")`
  and its case class silently reattached the annotation (invisible to `cJVM`),
  and `StreamingValidation` had an `options.find(…).get` that was safe only while
  persistence could come from nowhere else.

- **The stream-shape arity table is TOTAL, and `sink`/`source` take ANY port
  count** (Reid, 2026-08-12). `Processor.shapeForArity` maps every non-negative
  `(outlets, inlets)`:

  | shape | outlets | inlets |
  |---|---|---|
  | `void` | 0 | 0 |
  | `sink` | 0 | **≥1** |
  | `source` | **≥1** | 0 |
  | `flow` | 1 | 1 |
  | `merge` | 1 | ≥2 |
  | `split` | ≥2 | 1 |
  | `router` | ≥2 | ≥2 |

  `sink`/`source` were pinned to exactly one port until 2026-08-12, leaving
  `(0, ≥2)` and `(≥2, 0)` unnamed; they fell to a catch-all returning `Void`, so
  `repository R as sink` with two inlets was rejected as "its arity is void".
  **The final arm now THROWS** — reachable only for a negative count — because
  returning a plausible shape is how the gap became a confident wrong diagnosis
  reported as fact.
  **Two places encode this and both must move together:** the table, and the
  parser's per-shape `minInlets`/`maxInlets`/`minOutlets`/`maxOutlets` in
  `StreamingParser` (`sink R` and `repository R as sink` must agree about what a
  sink is). Their prior agreement was not corroboration — it was one assumption
  written twice.

- **`external context Foo` is an INTENTION, not `option external` — test both.**
  `Context.intention: Option[Intention]` (Application/External/Gateway/Service)
  is set by the keyword form, which is what riddl-models uses almost exclusively;
  `hasOption("external")` is the OTHER spelling and does NOT see it. A check
  exempting external contexts must ask for both:
  `c.intention.contains(Intention.External) || c.hasOption("external")`. Testing
  only the option cost 1120 false warnings in one run — every event declared in
  an `external context`, i.e. exactly the systems a model deliberately does not
  implement, reported as emitted by nothing.
  **The correct idiom was already in the codebase** at
  `StreamingValidation.scala:66`; it just was not copied. Two sites still ask for
  the option ONLY — `ValidationPass.scala:248` (`checkCompletenessPostProcess`) and `:581`
  (`validateOnMessageClause`) — so an `external
  context` is NOT exempt from those two. Filed in BACKLOG; each needs its own
  corpus A/B, since widening an exemption changes which models escape a different
  check.

- **Statement scope: `set` and `get from state` need something that OWNS state**
  (Reid, 2026-08-12). `set` is legal only in an **Entity** (owns its `State`) or
  a **Projector** (owns the read-model record its folds build — A70 REQUIRES it).
  An Error in a Context (§3.5: state lives in contained
  entities/repositories/projectors, "never in the Context itself"), a Saga (§9.5:
  housekeeping with "no domain-specific value"), a Repository, an Adaptor and the
  streamlets. A **Function** is deliberately not reported here — A26 already
  rejects `set` at the keyword, and a second message would double-report.
  **A Repository is banned despite the corpus appearing to disagree.** 97 `set`s
  across reactive-bbq and two pattern templates were added to silence *"contains
  only prompt statements"* — evidence about that warning, not about what a
  repository does. The warning now **exempts repositories** (most of their
  on-clauses legitimately hold one `do` standing in for SQL) and says **`do`**,
  not `prompt` (`do` is canonical; `prompt` is the deprecated synonym, and
  `prompt(…)` with parens is a VALUE). The two halves must move together.
  `get from state` is legal only inside the entity that OWNS the state: outside
  any entity there is nothing to read (and in a saga step this is the rule the
  `ask` ban already states, which reading state directly would bypass), and
  inside a *different* entity it crosses §4.6's encapsulation rule. That second
  half is why the rule lives in **validation, not the parser** — it needs the
  resolved `State` and its owner. **`get from input` is untouched**:
  `GetValue.source` is `InputRef | StateRef`, and inputs are confined to
  application contexts indirectly, because A41 pins UI groups there. A dedicated
  message was considered and REJECTED (2026-08-12) — but know the tradeoff: what
  the author sees is the GENERIC *"Path 'Screen.NameField' was not resolved"*
  (verified, not assumed), **not** A41's message. It is correct and unhelpful;
  revisit if it confuses anyone, the reason to leave it being that `get from
  input` outside an application context is nearly always a missing group, which
  A41 does report well.
  Hooked in `validateStatement`, which every statement reaches WITH its parents —
  including saga-step statements, whose `parents.head` is the **Saga** (a SagaStep
  is a Leaf and is never pushed). Note `checkStatementScopes` is NOT that hook:
  it is wired only to on-clauses and function bodies.

- **A processor receives ONLY through its OWN inlet, and publishes ONLY through
  its OWN outlet** (Reid, 2026-08-18). *"Inlets are needed to receive, outlets to
  transmit/publish."* A message reaches a processor through THAT processor's
  inlet — not a sibling's, and not its container's. **`tell` is no exception**:
  it is the same operation as `send` unless a generator can lower it more
  efficiently while keeping RIDDL's semantics, so a `tell` target must have an
  inlet. An "inbox" is a LOWERING detail with no presence at the RIDDL design
  level — do not reason about one in validation.
  Consequences that are easy to get backwards:
  - **An entity cannot publish on its context's outlet.** Getting a message out
    of a context is entity outlet → connector → context inlet → handler → context
    outlet, so the FIRST step is the entity's own outlet.
  - **Intra-context, nothing needs ceremony.** Inside one context any
    processor/streamlet/connector may communicate with any other, and a connector
    may drive a contained entity's own inlet directly.
    **An ADAPTOR enjoys this too** (Reid, 2026-09-09, closing BACKLOG [3.8]):
    *"The adaptor is part of the context (its boundary) and therefore enjoys the
    same privilege as other processors in that context."* **Being the boundary
    does not make an adaptor a stranger to its own context** — A103 makes it
    special about what CROSSES the boundary and changes nothing about wiring
    inside one, so the 2026-09-03 statement rule
    (`adaptor-targets-context-only`) implies no matching CONNECTOR rule. The
    cross-context half was already settled by A103 (that shape
    draws `stream-boundary-outlet` AND `stream-boundary-inlet`, verified), so
    only the intra-context case was ever live, and it is legal. No code changed.
    Pinned by `AdaptorIsTheBoundaryTest`, **because nothing pinned it**:
    `SharedAdaptorTest`'s "allow wrapper adaptations" carries this exact shape but
    asserts only that the adaptor parses with the right id, so a boundary rule
    that started erroring on it would have left that test green.
  - **At the boundary, and only there, the CONTEXT is the port.** Crossing IN it
    is the sink; crossing OUT it is the source.
  **This corrected two completeness checks that had encoded the opposite.** 4h
  asked whether the parent CONTEXT had an outlet (never asking about the entity)
  and 4i whether anything in the context had an inlet; both are per-entity now,
  and 4i's context-level form is DELETED. Each is gated on the entity actually
  doing the thing — handles no message ⇒ needs no inlet, emits nothing ⇒ needs no
  outlet — and `???` is exempt.
  **Fold STATE handlers in**: `entity.handlers ++
  entity.states.flatMap(_.handlers)`, the idiom `validateAsk` and four
  neighbouring checks already use; an entity's clauses commonly live inside a
  `State`. (Adding the fold moved NOTHING in the corpus — correct-by-idiom, not
  evidenced by movement.)

- **THE ADAPTOR IS THE BOUNDARY for the pair it names (A103, Reid 2026-09-05/06;
  CM §§7.2, 7.7, 8.1).** An Adaptor declared in context A `to context B` or
  `from context B` is boundary surface of A for that ordered pair and direction.
  **This REVERSED the earlier "no adaptor exemption" ruling** (below, as history):
  the old rule compelled the foreign message type onto the context's own portlet
  and into its own handler, contradicting §7.6's isolation seam ("the ONLY
  sanctioned place another context's types are named"); one of the two had to go.
  Landed in two commits, permissive then adamant, on Reid's instruction.
  - **Ports are IMPLIED**: `Adaptor.arityShape` counts each side as at least one,
    so a port-less adaptor is a `flow`; declaring a port overrides that side.
    `validateProcessorShape` checks an adaptor even when port-less, so `as
    source`/`as merge` on one is an Error (the corpus's 31 one-outlet `as source`
    adaptors included). **No AST change**: `AdaptorContents` already admits ports.
  - **No grammar change for the endpoint** (Reid's choice): `from outlet
    Sales.ToBilling` already parsed; `ResolutionPass.resolveConnectorEnd` accepts
    an Adaptor where a portlet was expected. **Every streaming check resolves
    endpoints through ONE abstraction**, `StreamingValidation.ConnectorEnd`
    (`DeclaredEnd` | `ImpliedEnd`) via `connectorFrom`/`connectorTo` — seven
    sites used to call `resolvePath[Outlet]`/`[Inlet]` each. Do not add an
    eighth. Consequences: type agreement is skipped when an end is implied
    (nothing is synthesised); an implied port has cardinality one and is never
    reported unconnected; a cycle through an implied port is undetected (the edge
    carries no declared type); the unattached-port check collects each side
    independently — the old PAIR collection would have reported a declared far
    inlet as unconnected whenever the near end was implied. Check 1 ("no
    connections") excludes processors with no DECLARED ports, or ~1000 corpus
    adaptors would have warned.
  - **The boundary exemption is DIRECTIONAL, in both checks.**
    `checkBoundaryEncapsulation`: an OUTBOUND adaptor toward B may be the `from`
    end of a connector into B; an INBOUND adaptor from B may be the `to` end of
    one leaving B; the referent must be the far context of THIS connector.
    `reachesPastContextBoundary` (the ONE boundary test for statements) exempts a
    target that is an inbound adaptor whose referent is the SENDER's context.
    Wrong way round, or toward a third context, still errors.
  - **Typing is VALIDATED, never SYNTHESISED**
    (`adaptor-target-no-admitting-inlet`): a `tell`/`forward ... to context X`
    from inside an adaptor is an Error unless X declares an inlet whose type IS
    the message type or whose alternation CONTAINS it — `typeAdmits`, the one
    permissive type test, shared with the chain-tail rule; `areSameType` stays
    strict for connectors. By-name form only. Inbound adaptors address their OWN
    context and the same rule applies — which is how shopping-cart's adaptor
    telling an event to a context whose inlets are all commands was found.
  - **EXCLUSIVITY** (`stream-connector-bypasses-adaptor`): where A declares an
    outbound adaptor toward B, a connector from A's OWN outlet into B is an Error
    naming the adaptor (AR2); where A declares an inbound adaptor from B, a
    connector from B onto A's own inlet is an Error (AR6). An un-adaptored
    direction crosses as before. **This is what made the old two-hop shape
    illegal**, and why the permissive half had to land first.
  - **`send` obeys ownership** (`stmt-outlet-not-owned`): a `send`/`forward` may
    name only an outlet whose parent chain contains the sending processor. A6
    bound `tell` this way since 2026-09-02; `send` had no check, so an adaptor in
    OnlineOrdering published on FrontOfHouse's outlet with zero errors. `send ...
    to inlet X` is a delivery, judged by the boundary rules, not here.

- **An implied port HAS a type, and a connector with an implied end is
  type-checked (AR9, 2026-09-07).** riddlg's derivation, adopted: an implied
  OUTLET carries the distinct types its adaptor `tell`s/`forward`s to a context,
  resolved by `clauseOperandType` through the clause binding, a `let` in the
  clause, the constructor or the message ref; an implied INLET accepts what its
  adaptor HANDLES. **The SOURCE decides what a wire carries** — a destination's
  expectation is not evidence about what arrives (reactive-bbq's mirrored pairs
  proved it). Several distinct told types is `adaptor-implied-outlet-ambiguous`,
  reported once by `validateAdaptor`; never the first taken. `validateConnector`
  compares with permissive `typeAdmits` against a declared inlet and
  `adaptorAccepts` against an implied one; declared/declared keeps strict
  `areSameType`, deliberately.
  **`adaptorAccepts` is NOT `receivesMessageType`.** An `on other` whose body is
  only `error` is a refusal, and the corpus writes `on other { error "Unexpected
  message for adaptor X" }` in every adaptor — counting it as acceptance made
  every wire type-correct and both AR9 tests green for nothing. Delivery
  questions keep the looser helper.
  **AR5 accepts the far context's INBOUND adaptor as the admitting port.** As
  shipped it looked only at the far context's own inlets, while AR6 requires the
  crossing to land on that adaptor: the two rules contradicted each other on the
  exclusive shape, hidden because every corpus adaptor tell is `let`-bound and
  AR5 did not resolve `let`s. **Resolving an operand you previously ignored can
  expose a rule you already shipped** — check what the newly visible cases
  collide with before landing the resolution.

- **`adaptor-direction-advisory` counts a far-context reference ANYWHERE in the
  adaptor, and resolves the referent parent-independently.** It used to read only
  the `on`-clauses' handled types, so under A103 it fired on every correctly
  migrated OUTBOUND adaptor (own event handled, far command produced through a
  `let` and a `send`) and stayed silent on the unmigrated placeholders — it
  rewarded the wrong shape. Handled types, transmitted operands
  (`clauseOperandType`), `let` ascriptions and declared portlet types all count
  now.
  **The referent lookup was the same trap `hasAdaptorFor` records**:
  `resolvePath(referent, parents)` keys the refMap on the adaptor's PARENT, but
  `ResolutionPass` records an adaptor's `referent` under the adaptor itself, so a
  QUALIFIED `to context D.Far` never resolved and the advisory was silently
  skipped; only a bare `to context Far` ever ran it. Any check resolving
  `adaptor.referent` must use
  `resolution.refMap.definitionOf[Context](pathId, adaptor)`.
  **A test that passes because the check never ran is the vacuous kind**: the
  qualified-referent positives were green before the fix for exactly that reason,
  which is why the suite carries a bare-referent positive as well.

- **A cross-context connector must land on the CONTEXT'S OWN portlet — an Error**
  (Reid, 2026-08-18, choosing Error over CompletenessWarning).
  `StreamingValidation.checkBoundaryEncapsulation`. Reaching past the boundary
  onto a contained definition's portlet **contradicts** the bounded context
  rather than under-stating it: a context publishes its message set and keeps its
  representations private, so binding a peer to a contained entity's existence
  and current command/query set means that entity can no longer change without
  breaking a stranger. The rule engages ONLY across contexts. **Cost, ruled
  acceptable:** 250 inbound + 241 outbound violations across 184 of 198 corpus
  entry points.
  *History, do not restore:* this entry originally carried a **NO ADAPTOR
  EXEMPTION** ruling — "being the translator does not make it the boundary", an
  adaptor sitting BEHIND the context's own portlet — decided 2026-08-18 so that
  one rule with no exceptions kept the context's message set the single public
  surface. **A103 REVERSED it on 2026-09-06**; the adaptor exemption above is the
  live rule.

- **A `tell` target needs BOTH a declared inlet AND a connector into it** (Reid,
  2026-08-18). The two rules genuinely compound, and that is intended. A `tell`
  requires the target to have an inlet (above); `checkUnattachedOutlets`
  separately reports a declared inlet that no `connector` references as *"is not
  connected"*. So declaring the inlet to satisfy the first trips the second —
  asked explicitly, and ruled that the CONNECTOR SHOULD EXIST: `tell` is sugar
  for a send on the outlet connected to the target's inlet (CM §25.7 / A6), so
  the warning is correctly telling the author to model the channel rather than
  leave it implied. **Do not "fix" this by teaching `checkUnattachedOutlets` to
  count tells.** There is no corpus population today (ZERO "is not connected")
  because the corpus's tell-target entities declare no inlets at all; the
  interaction surfaces only as models comply.

- **An `ask` needs a modelled path BOTH WAYS** (Reid, 2026-09-09/10;
  `msg-ask-target-unreachable`, `msg-ask-reply-unreachable`). `ask` is `send`
  plus a declared correlation — *"there are no magic ways for processors to
  communicate … there's no way to communicate without wiring, even in the same
  process boundaries"* — and the answer is held to the same standard: *"the reply
  path must be wired in the model just like the query path."*
  **The distinction that settles the reply leg: the MECHANISM is the generator's,
  the PATH is the model's.** A reply actor, a future, a correlation id are
  lowering choices with no model-level representation; whether an answer can
  physically get back is not one. Reading Reid's earlier *"setting up a reply
  actor … is the generator's concern"* as covering the path is the natural
  mistake, and it is wrong.
  `checkAskReachability` is `checkTellReachability`'s question asked TWICE over
  the same `connectorAdjacency` graph, with the same exemptions (`???`,
  predefined, asking yourself, a side with no inlets). **Deliberately not a type
  check** — whether the far inlet admits the query is `checkInletsAreReceived`'s
  question.
  **Its ABSENCE had taught a false rule, which is why this is an Error and not a
  warning.** `ask` was validated only for the far end's BEHAVIOUR, so an `ask` in
  a completely unwired adaptor reported NOTHING while a `tell` in that same
  adaptor drew two Errors. riddl-models read the silence, wrote down *"wiring is
  simply irrelevant to it"*, and was about to apply that to 363 sites across 118
  models. **A validator silent where the language has a rule teaches the wrong
  rule** — "it validates" is the evidence modellers use. Corpus population at
  landing: **zero**, which is exactly why the checks went in BEFORE the 363 are
  authored rather than after.

- **A `tell` into an UNRELATED domain is a modelling Error, and the diagnostic
  must say RESTRUCTURE rather than "add a connector"** (Reid, 2026-09-08;
  `msg-tell-crosses-unrelated-domains`). Relatedness is a SHARED ANCESTOR domain
  — the same test `stream-crosses-domains` applies to a connector, deliberately,
  so the two rules cannot disagree about which pairs may be joined.
  **It resolved a THREE-sided vise, which is why two-sided reasoning kept missing
  it.** riddl-generator found an adaptor in `Shop` telling a processor in `Corp`
  (top-level siblings) with no legal spelling at all: declare the far inlet and
  A6 demanded a connector; add the connector and `stream-crosses-domains` refused
  it; drop the inlet and AR5 refused that. Root scope admits no connector, so
  there was no fourth placement. **A diagnostic whose remedy is impossible is
  worse than none.**
  **The two rules had been prescribing each other's refusal.**
  `stream-crosses-domains`' suggestion said *"model the communication with an
  adaptor and messaging rather than a direct stream connector"* — precisely the
  shape A6 then rejected. Both halves moved together; changing only one would
  have left the contradiction intact facing the other way. **When two rules can
  each refuse the other's remedy, fixing one is not a fix.**
  **Deliberately the SAME trigger, not a wider one**: emitted only where
  `msg-tell-target-unreachable` already fired, so this is a re-diagnosis with
  ZERO new error surface (corpus verified unmoved, 190/190). Residual, accepted:
  a target with NO inlet stays exempt, so such a model reports the inlet first
  and the domain problem only after one is added — two rounds, but every message
  followable. `UnrelatedDomainTellTest` pins the related case with the remedy
  actually applied, validating at 0 errors — a negative control proving the
  advice works, not merely that the error stops.

- **A stream chain ENDS where its message is CONSUMED, never at a `sink` SHAPE —
  and a chain may not loop** (Reid, 2026-09-04; CM §8.1). Check 2 used to ask
  whether a Source reaches a node whose `effectiveShape` is `Sink` (zero
  outlets). A6 made that unsatisfiable: a terminal event log that records to its
  repository must OWN the outlet it writes on, so by arity it is a `flow`, and
  every source above it drew `stream-source-reaches-no-sink` — 42 corpus findings
  no wiring could remove. **The mirror of the 2026-08-14 chain-HEAD ruling**
  (`75a791682`: a head bears an outlet with nothing feeding it; a Source SHAPE is
  not required), found the same way — a correctly modelled corpus reporting a
  rule, not a model, wrong.
  `ValidationPass.isStreamTail`: an inlet, every admitted type handled
  (`unreceivedMembers`, so alternations expand and `on other` counts), and no
  clause handling T that `send`s/`tell`s/`forward`s a message of THAT type
  (`propagatesOnward`). **Same-type is the whole point** (Reid chose it over "any
  send"): receiving an event and sending a `Persist` COMMAND, or `put`ting to an
  output, is a write, not a continuation. `forward` always disqualifies.
  **A handler-less processor is opaque and is a TAIL whatever its shape** ([5.7],
  2026-09-08). It was a tail only when it had no outlets, a ports-only flow being
  "assumed to pass through" — which put this rule in direct disagreement with
  `checkMessageLoops`, where the same node passes nothing through. **The unifying
  principle: an opaque processor lets NO rule assert what it does with a
  message** — the loop rule may not claim the message comes back, this rule may
  not claim it goes on, and the arity it happens to have is evidence for neither.
  It is also the anti-double-reporting rule: *"Flow 'X' should have a handler"*
  states the whole omission.
  **Corpus population was ZERO** — measured across all 189 entry points,
  calibrated on a known-positive first. **`sink-reach.check` did NOT move
  either**, though [5.7] predicted it would: that fixture declares no Source, so
  Check 2 never runs on it. A `???` body needs no exemption: it declares no
  inlet.
  **The predicate lives in `ValidationPass` as an abstract hook on
  `StreamingValidation`**, because the helpers it needs (`handlerClausesOf`,
  `alternationMembers`, `operandType`, `walkStatements`) are private there; do
  not grow a second copy in the trait.
  **`stream-graph-cycle` forbids an INFINITE MESSAGE LOOP, not a connector ring**
  (Reid, re-ruled 2026-09-07; `checkMessageLoops`, an abstract hook like
  `isStreamTail`). An `on X` clause transmits X, the message travels the
  portlet/connector network to an inlet admitting X on a processor whose own `on
  X` clause transmits X again, and so on back to the start — any length, one node
  or many. **The 2026-09-04 version reported any per-type ring of connectors,
  self-loops included, and was too general**: it condemned the `send … at`
  schedule-to-yourself idiom, whose emitting clause is `on command Book` and can
  never be re-entered by the event it sends. Folded-in rulings: X may be a UNION
  member (`typeAdmits`/`typeMembers` at every hop); a HANDLER-LESS processor
  passes nothing through; `tell`/`forward` ride the same channel as `send` and
  arrive at Q exactly when Q declares an inlet admitting X (an adaptor:
  `adaptorAccepts`); `on other`/`on init`/`on term` do not handle X. Reported
  once per loop at the first member's transmitting clause.
  **Fixture trap that cost two red runs**: a bare `outlet o` is AMBIGUOUS once
  two processors declare an `o` — single-segment paths search the WHOLE symbol
  table — so the resolver records nothing, the walk has no edge, and the loop is
  silently missed. Qualify the path (`outlet C.Loop.o`). And an on-clause names a
  message KIND: an alternation of events is `on event Y`, never `on type Y`.
  **The riddl-models report that prompted this claimed a SECOND cause — "the walk
  stops dead at a context inlet because the handler-to-outlet hop is invisible" —
  and it was false.** The graph is per PROCESSOR: a connector into a context's
  inlet makes the Context a node, and the walk continues through every connector
  leaving any of its outlets, ports never consulted. Proven with a probe before
  designing anything. **When a sender describes the mechanism of a bug in your
  code, verify the mechanism before the count.**

- **A queried repository with no index draws a CompletenessWarning — and the
  check deliberately does NOT name a field** (Reid, 2026-08-18, on riddlg's
  request). `checkQueriedWithoutIndex` fires when a repository has a schema,
  answers at least one query, and declares no `index on` at all. 26 corpus sites.
  **The ruling that produced it: an index belongs to the REPOSITORY, not to a
  field.** riddlg asked for an `indexed` option on `Field`; declined, because a
  database index is a persistence concern and putting it on an entity's field
  leaks a generator's lowering choice into the model. `Schema.indices` is the
  mechanism — 517 uses across 228 corpus schemas.
  **Do not try to make it name the field. Both routes were MEASURED and neither
  is derivable:** all **406** repository `on query` bodies in the corpus are
  `prompt(...)`/`do "..."` with **zero** comparisons (by design); and taking the
  query TYPE's fields as the operands — the better idea, since a query's
  parameters ARE its operands — maps to a stored record field **1 time by name
  and 19 by type out of 284 (6%)**. The correspondence has never been required of
  authors, so it is not in the models. Making it derivable needs a language
  change; **prose on the query type would move the ambiguity, not remove it.**
  **The no-repository case is already diagnosed**: an entity with no repository
  draws *"has entities but no repository to persist them"*.

- **`???` is a body that says "known to be incomplete" — validation must EXEMPT
  it** (Reid, 2026-08-11). Any definition whose body is `???` earns at most a
  **Missing** warning saying the body should be provided. Every other check —
  structural, completeness, wiring, cross-reference — is skipped, because the
  author has already said *don't expect much*. So a check must not reason from
  what a `???` body does NOT contain: `repository R is { ??? }` is not missing
  its handlers, it is unwritten, and a rule that fires on it will fire on nearly
  every stub in the corpus. Guard a new check on `nonEmpty` (as the streamlet
  shape check does) rather than reporting the stub.

- **A parse-time `error()` PREEMPTS validation — the pass chain never runs.** So
  whatever the parser says is the ONLY thing the author sees, and any more
  specific diagnostic ValidationPass would have produced is silently lost.
  Learned 2026-08-08 adding the `yields`/`replies` pairing: checking it in the
  parser looked equivalent to checking it in validation and is not — it killed
  three existing A19 messages because those inputs stopped reaching the pass that
  emits them.
  **Rule: put a check in the parser ONLY when validation cannot make it**, the
  test being whether the evidence survives into the AST. The keyword/use-case
  pairing qualifies: `usecase` is in the AST but which KEYWORD was written is
  not. Everything else belongs in ValidationPass. Two corollaries:
  - A parser `error()` is otherwise NON-FATAL and accumulating (see
    `defOfTypeKindType`), so it looks harmless in isolation. The damage is to the
    passes that never run, not to parsing.
  - Parse-time messages travel a DIFFERENT channel: `parseInputWithMessages` →
    `PassInput.parseMessages` → `PassesResult.additionalMessages`. They reach
    users under every `riddlc` command, but `parseAndValidate` in tests DISCARDS
    them — assert them with `TopLevelParser.parseInputWithMessages` (pattern:
    `RecognizedOptionSetTest:98`).

- **`ValueRef` resolves in the RESOLVER (A55), not in validation.**
  `ResolutionPass` queues every `ValueRef` and resolves it in `postProcess` (its
  anchors are reached through other references, and the pass visits definitions
  in source order). Only the ANCHOR differs from an ordinary reference: the
  on-clause `binding`, else a field of the handled message / entity state /
  function `requires` input (`valueScopeField`), else the ordinary `findAnchor`
  route. The rest is `resolvePathFromAnchor`'s walk. Validation reads
  `refMap.anyDefinitionOf(path, parents.head)`. **Do NOT reintroduce
  last-component name matching** — that was A54's
  `valueAllowedFields`/`constantOf`, and it let `garbage.nonsense.realField`
  validate.
  - **`let`-locals stay LEXICAL** — a `let` is not a Definition and is
    statement-ORDERED (visible only after its declaration, shadowed by inner
    blocks), which the symbol table cannot model. They are threaded by
    `checkStatementScopes`; a `let`'s type is DECLARED or INFERRED from its
    expression (`letType`). Because the resolver cannot see them, the ValueRef
    walk runs under `ResolutionPass.quietly` (suppresses
    `notResolved`/`wrongType`/`ambiguous`) and **validation owns the
    diagnostic**.
  - **`Reference.id` is a reference's optional LOCAL NAME**, the one `from di:
    context C` sets — NOT the referenced definition's id. No `MessageRef` ever
    carries one, which is why `findMatchingCandidate`'s on-clause arm was dead
    until A55 changed its guard to `omc.msg.nonEmpty`.

- **Message suggestions / `provideTips` (1.24.0)** — every `Message` carries a
  `suggestion: String`; any pass attaches one at the message-creation site (via
  the `addX`/`check` helpers' trailing `suggestion` param). The single chokepoint
  `Messages.Accumulator.add` STRIPS it unless `CommonOptions.provideTips` is set,
  and `Message.format` appends a `Suggestion:` line only when present — so
  default output is unchanged (no `.check` churn). `riddlc advise` == `validate`
  with `provideTips=true`; `--provide-tips` / HOCON `provide-tips` toggle it.
  This replaced `AIHelperPass`: the pass and its tests are deleted; the `Tip`
  message kind is retained but has no producer; `RiddlLib.analyzeForTips`/
  `analyzeSourceForTips` + the `advise` command are kept, re-implemented to run
  standard passes with `provideTips=true` (analyze* are `@deprecated`). Catalog
  of every message→suggestion pair: `MESSAGE_SUGGESTIONS.md` (repo root).
  Three entity completeness checks promoted from old AIHelper tips (no command
  types, no event types, unhandled command) are ADVISORY — gated behind
  `provideTips` because message types are often context-scoped. The
  context-with-entities-but-no-repository check is ALWAYS-ON, gated only by
  `showCompletenessWarnings`.

- **Streamlet shape check** — guard on `nonEmpty` before checking inlet/outlet
  counts (empty = placeholder).
- **Adaptor cross-context type resolution** — use the parent-independent
  `resolution.refMap.definitionOf[Type](pathId)`.
- **Schema parser** — `schemaKind` uses `"time-series"` (hyphenated).
  Consecutive schemas need `with { ... }` blocks.
- **CheckMessagesTest `.check` file format** — lines starting with a space are
  continuation lines; non-space lines begin new entries. Don't insert
  mid-continuation.
- **RiddlResult[T]** replaces `Either[Messages, T]` — a sealed ADT with
  `Success[T]`/`Failure`; use `result.toEither` for backward compat.

### Container / Flatten / FileBuilder / PrettifyPass

- **`Container.flatten()`** recursively removes Include / BASTImport wrappers in
  place. Use base `Pass`, not `DepthFirstPass` — mutating contents during
  traversal corrupts ArrayBuffer iteration.
- **FileBuilder requires PlatformContext** — `trait FileBuilder (using
  PlatformContext)`; all subclasses must propagate the `using` clause.
- **PrettifyPass multi-file mode** — `flatten=false` (default) preserves
  include/import structure; `-s true` collapses to a single file.
- **`PrettifyState.toDestination()`** strips leading/trailing `/` from `outDir`
  (a URL basis cannot start with `/`).
- **Include paths** — `openInclude` uses `url.path` (relative filename), not
  `url.toExternalForm` (absolute URL).
- **`RiddlFileEmitter.trimTrailingNewline()`** — used in `closeType` to join `}`
  with ` with {` on the same line.

### JS / npm / TypeScript

- **parseString returns an opaque Root in JS** — use `getDomains(root)` or
  `inspectRoot(root)` to access data; the TypeScript type is a branded `RootAST`.
- **`RiddlLib.ast2bast(root)`** returns `RiddlResult[Array[Byte]]` on the shared
  side / `RiddlResult<Int8Array>` in TS.
- **riddlLibJS tests** override `Test / scalaJSLinkerConfig` to `CommonJSModule`;
  production stays ESModule.
- **ESM shim hazard** — never put `import '`, `import "`, or `import(` in shared
  string literals; ESM shim plugins rewrite these patterns. Use string
  concatenation. `ESMSafetyTest` enforces it.
- **npm prerelease publishing** — sbt-dynver versions like `1.2.3-1-hash` are
  prerelease per npm semver; pass `--tag dev`. GitHub Packages npm auth needs
  `gh auth refresh -s write:packages`.
- **The opaque `*AST` handles in `index.d.ts` are DELIBERATE. Do not "fix" them
  by exporting the AST to TypeScript** (considered and DECLINED 2026-08-27,
  BACKLOG [2.9]). `parseString` hands JS a branded handle (`RootAST`,
  `EntityAST`, …) that can only be passed back in; structure is served through
  flattened projections (`inspectRoot`, `getOutline`, `getTree`). Three reasons,
  in order of weight:
  1. **JSON serializes STATE; the AST's value is largely BEHAVIOUR.** `AST.scala`
     carries ~540 `def`/`lazy val` members that do not serialize — 182 `format`,
     67 `kind`, the 34 `WithX` accessor traits, and derived answers like
     `effectiveShape`, `Connector.isPersistent`, `Statement.canFail`,
     `Function.input`/`output`. `JsonModel` has ZERO references to
     `refMap`/`symTab`/`usedBy`, so no resolution output crosses either.
  2. **JSON keeps `Include`/`BASTImport` as content entries**, so a consumer
     walking `contents` sees the WRAPPER rather than through it — the exact
     defect that had riddl-generator emit 582 files with no entity class, at exit
     0. A JSON-derived TS AST would invite every consumer to reimplement
     include-transparency and alias-resolution.
  3. **It would be a FIFTH reflective surface** to keep in lockstep with
     parse/prettify/BAST/JSON, and nothing would fail when it drifted.
  **The real consumers agree**: riddl-vscode touches a raw AST handle zero times
  (all facade — `parseToTokens`, `parseString`, `getTree`, `validateString`, …),
  and the consumer that truly walks the AST is Synapify, which is **Scala.js and
  has the real objects, methods included**. If this returns, the trigger is a TS
  consumer hitting a wall the facade cannot answer — add one accessor inside the
  conversion layer, never the AST.
- **NEVER `@JSExport` an overridden `toString`.** Interpolation compiles to JS
  `+`, so `s"…$loc…"` throws `TypeError: Cannot convert object to primitive
  value` and takes down the whole validation run on JS while the JVM passes. `At`
  and `URL` both carried it. JS callers get `toString` from the prototype anyway,
  so the export buys nothing. `ToPrimitiveCoercionTest` guards it and is
  **JS-only by necessity** — on the JVM every assertion in it passes regardless
  of the annotation, which is precisely why the bug survived. Grep before adding
  `@JSExport` anywhere near a `toString`.
- **Three JSON-surface traps, all the same shape — a second code path that
  quietly disagrees with the first:** (a) **upickle TAGS sealed hierarchies** —
  making the DTOs extend a `sealed trait` silently added `$type` to every object,
  and the round trip still agreed with ITSELF so the fixtures suite stayed green;
  `ContentDto` is a Scala 3 UNION for exactly this reason. (b) **Hand-written
  codecs drop new fields** — `writeTypeExpr` and `refJs` are hand-written, not
  derived, so `RecordDto.comments` and `RefDto.keyword` were added to the case
  class and went on being dropped. Anything in `JsonModel`'s manual codec section
  needs the field added in TWO places. (c) **The tag key is `$kind`, not `kind`**
  — `OnClauseDto` and `SchemaDto` carry a `kind` FIELD of their own and
  `ujson.Obj.from` keeps the last of a duplicate pair, so the tag silently
  overwrote the data.

### A corpus suite must assert it covered the WHOLE corpus

**A relative assertion cannot notice that its own population vanished.** Both
corpus suites compared one count to another — `identical mustBe reparsed`,
`reparsed mustBe parsed`, `parsed mustBe files.size` — which is equally satisfied
by 190 models and by 3, and `RiddlModelsRoundTripTest` simply generates one case
per model FOUND. A truncated corpus therefore produced fewer green cases and said
nothing. `Root2JsonCorpusTest`'s own docstring already recorded the same shape
biting once: every read failed, every failure was skipped, and its assertions
reduced to `0 mustBe 0` for months.

Both now carry an absolute floor (`MinimumModels`, 189 and 190) and **FAIL when
the corpus is present but partial**, while an ABSENT corpus still SKIPS — Reid's
[1.3] ruling, so a developer without the sibling checkout is not blocked. Raise a
floor when the corpus grows; never lower one to make a run pass. **Both floors
were canary-tested** by setting them to 9999 and confirming the right cases
redden: a check that has only ever passed is not evidence it works.

**CI could also serve a stale result for these suites, and that is closed
separately.** `sbt/setup-sbt` restores `$HOME/.cache/sbt` under a key of the form
`Linux-X64-sbt-runner-<sbtVersion>-<actionVersion>` — keyed on VERSIONS, not
content — and `v2/ac` maps task-input hashes to task RESULTS. The corpora are
cloned by a workflow step and are not build inputs, so their CONTENT is in no
key. `scala.yml` now deletes `v2/ac` after restore; the expensive caches
(Coursier, ivy2, launcher, JDK, `v2/cas`) are untouched. **Both halves were
needed because they are indistinguishable from outside:** a replayed result and a
truncated corpus both present as a fast green suite.

### Measuring riddlc output — three ways to get a FALSE ZERO

**All three were hit in one session (2026-08-19), each looked like a finding
rather than a broken instrument, and each was caught only by a CONTRADICTION.** A
zero from a measurement you have not calibrated is not evidence of absence —
calibrate on a case known to be positive before trusting a zero.

1. **`grep '^\[error\]'` matches nothing when output is ANSI-coloured.** riddlc
   colours by default, so the line starts with an escape sequence, not `[`. This
   produced the report "both statement orderings are accepted" when one of them
   was rejected — the opposite of the truth. **Pipe through `sed
   's/\x1b\[[0-9;]*m//g'` before counting anything.**
2. **`--show-style-warnings=true` SUPPRESSES style warnings.** The same probe
   gave 2 findings on default flags and 0 with the flag that names them. Default
   already shows them; passing the flag explicitly is worse than passing nothing.
3. **Every riddl-models `.conf` sets `show-style-warnings = false`**, so `riddlc
   from <model>.conf validate` reports ZERO style findings across all 190 models.
   A style-warning census must validate the `.riddl` DIRECTLY. This is why a
   452-site finding read as 0 corpus-wide.

Related, same family as the false-green traps below: **validate ENTRY POINTS, not
include fragments.** A fragment validated alone reports errors by construction,
which reads as corpus breakage. riddl-examples' `FooBarSameDomain` is a further
trap — it is a DELIBERATELY ambiguous fixture, so its duplicate-name errors are
the fixture working.

### Build / CI / Tooling

- **Three ways a test suite passes without running** (all found in #64, which had
  hidden 38 dead cases — including a completely non-parsing `import "f.bast"` —
  for months). A green suite is NOT proof the assertions ran; the check is to
  drop a `fail("canary")` into a case body and confirm the suite goes red.
  1. **TestData lambda on a plain spec.** `AbstractTestingBasis`
     (`utils/src/test/.../AbstractTestingBasis.scala`) is a PLAIN `AnyWordSpec
     with Matchers`, so its `in` takes a by-name `=> Any`. Writing `in { (td:
     TestData) => body }` there merely constructs a `Function1` and **never
     evaluates `body`** — deterministic Scala semantics, not sbt elision. That
     form is only meaningful on `AbstractTestingBasisWithTestData` (the
     `FixtureAnyWordSpec` base) and everything derived from it
     (`AbstractParsingTest` → `ParsingTest` → `AbstractValidatingTest` →
     `AbstractRunPassTest`). **Rule: if a case body takes `(td: TestData)`, the
     suite MUST extend a `…WithTestData` base.**
  2. **Abstract spec with no concrete subclass.** The runner never instantiates
     it, so its cases never appear in the log at all — zero mentions, not even as
     skipped. Either make the class concrete or declare a subclass in the
     platform aggregator (`JVMTests.scala`/`JSTests.scala`). Beware the silent
     trap: a class stays abstract because an inherited member is unimplemented
     (`PrettifyPassTest` declared `checkAFile(Path, File)` against a base wanting
     `checkAFile(Path, Path)`).
  3. **Constructor parameters on a concrete suite.** ScalaTest cannot instantiate
     `class FooTest(using PlatformContext)`, so it is never discovered. Concrete
     suites take NO parameters; import `com.ossuminc.riddl.utils.pc` instead.
- **Unawaited Future in a non-async spec** is a fourth variant: `inputFuture.map
  { … assertions … }` followed by `Await.result(inputFuture, …)` awaits the WRONG
  future — the assertions run detached and their failures are discarded. Await
  the MAPPED future. (**`BASTWriterSpec` does NOT have this shape** — this note
  claimed it did until 2026-08-14, wrongly; all five of its cases bind
  `assertionFuture = inputFuture.map { … }` and await THAT. The failure mode is
  real and worth watching for; it just has no instance in the repo today.)
- **`test`/`tJVM` resolve to `testQuick`** — which incrementally SKIPS suites it
  judges unaffected, even after a source change and even with
  `~/Library/Caches/sbt/v2/ac` cleared (a DIFFERENT cache from testQuick's own
  succeeded-tests tracking). Symptom: "No tests to run for language / Test /
  testQuick" and a false green. For a guaranteed full run after edits, use
  `<module>/testOnly *` (e.g. `language/testOnly * ; passes/testOnly *`), which
  ignores incremental state. Separate from, and additive to, the action-cache
  fixture blindspot.
- **`sbt -batch` runs only the FIRST command argument** — found 2026-08-03. `sbt
  -batch 'utils/testOnly *' 'language/testOnly *' …` with seven module arguments
  ran `utils` ONLY, printed "Suites: completed 18 / Tests: succeeded 146 / All
  tests passed", and **exited 0**. The other six never ran and nothing said so.
  The most deceptive member of the false-green family, because both the exit code
  and the word "passed" are honest about the 14% that executed. Put every command
  in ONE argument separated by `;` — `sbt -batch 'a/testOnly *; b/testOnly *; …'`
  — and then **count the `Suites: completed` lines against the number of modules
  you asked for.** (The `;` chain still aborts at the first failure, so a short
  count means either a red or a skip; either way, look.)
- **Corpus tests can resolve the WRONG `../riddl-models` — or none — under sbt
  2's `projectMatrix`, and the failure mode is a CANCELLED, green-looking
  suite.** `RiddlModelsRoundTripTest` and `Root2JsonCorpusTest` locate the corpus
  via `Path.of("../riddl-models")` resolved against the **process cwd at sbt
  launch** — NOT `Test/baseDirectory`, which under `projectMatrix` is
  `<root>/.sbt/matrix/<module>`, several directories deeper than the repo root
  the relative path was written for. Depending on where sbt was launched from,
  that path can land on a directory that doesn't exist, or a *different* one that
  does — either way the test finds nothing to iterate over. **A plain symlink at
  that path does not fix it and fails the SAME silent way**: BSD `find` and
  Java's `Files.walk` do not descend into a directory reached via a top-level
  symlink argument without `-L`/`FOLLOW_LINKS` (confirmed both ways). The symptom
  in both cases is "No .conf files found" followed by the suite reporting as
  **cancelled, not failed** — which reads as green in a summary scan exactly like
  the `testQuick`-skip and abstract-spec members of this family. **To tell:**
  don't trust "all tests passed" from a corpus-reading suite — check that it
  reports the expected model COUNT (e.g. "models=190"), not zero, and use a real
  directory copy (`cp -R`, not a symlink) at the path the test computes when
  reproducing a corpus run outside CI.
- **`@JSExport*` annotation placement** — an `@JSExportTopLevel(...)` binds to
  the very next definition. Inserting a new `enum`/`object`/class between the
  annotation and its case class silently reattaches it (breaks `cJS`, invisible
  to `cJVM`). Any AST edit near an exported type MUST be checked with `cJS` (and
  `cNative`), not `cJVM` alone.
- **Scala.js stale-incremental devirtualization** — when a class gains a `WithX`
  accessor trait (or any mixin changing which field a trait method resolves to),
  the JS linker can keep a *stale devirtualization* of that method to the OLD
  owner's field, producing a runtime `TypeError` while `cJS` succeeds. Neither a
  passing `cJS` nor deleting the `*-fastopt` dir clears it — only
  `<module>JS/clean` does. Symptom: a JS-only runtime failure no compile catches.
  Learned adding `WithContexts` etc. to `Module` (#61).
- **Parse-time messages now surface** — `warning()`/`deprecation()` emitted
  during a *successful* parse used to be dropped (`parseRule` returned the buffer
  only on fastparse failure). They now flow via
  `TopLevelParser.parseInputWithMessages` → `PassInput.parseMessages` →
  `PassesResult.additionalMessages`, so deprecations show under every `riddlc`
  command, not just `validate`. New parse-time warnings therefore appear in
  `.check` goldens.
- **Scala Native builds with `gc = "none"` — a bump allocator that NEVER
  reclaims.** It is sbt-ossuminc's `With.Native` default and `build.sbt` does not
  override it. Right for a short-lived binary; catastrophic for a test binary
  running the whole corpus in one process. **Measured 2026-08-19 by sampling the
  live `riddl-commands-test` process: 18.18 GB peak RSS with `none`, 1.11 GB with
  `immix`** — 16x, identical results. A GitHub runner has 15,989 MB, so the
  Native corpus rows needed more memory than the machine had; the host killed
  them for 18 consecutive runs, always with the build step still `in_progress`
  and NO log blob, which is why it stayed invisible. `immix` is now scoped to
  `Test` on the two corpus-reading rows (`nativeTestGC` in `build.sbt`); the
  SHIPPED riddlc still builds with `none`, deliberately — changing that is a
  separate decision. **A CI job that dies with no logs at all is a lost runner,
  not a timeout**: a real `timeout-minutes` kill is marked `cancelled` and KEEPS
  its logs.
- **release.yml** — triggered by `gh release create`. Builds native riddlc (macOS
  ARM64, Linux x86_64) + JVM universal, and sends `repository_dispatch` to
  homebrew-tap with SHA256s. Requires the `HOMEBREW_TAP_SECRET` repo secret.
- **sbt-dynver wants a clean working tree** — `git stash` modified files before
  `sbt publish` on a release tag.
- **External-repo tests** — download at construction time (not in `beforeAll`)
  for ScalaTest `AnyWordSpec`.
- **TatSu pin** — `TatSu>=5.12.0,<5.17.0`. 5.17.0 has a missing `rich`
  dependency that breaks import. **EBNF TatSu syntax** — `{rule}+`, not `rule+`,
  for positive closure; TatSu requires curly braces around the repeated element.
- **ScalaDoc + inline + opaque types** — keep `inline` off `Contents` extension
  methods (NPE in `ScalaSignatureProvider.methodSignature`). Filed:
  scala/scala3#25306.
- **Scala 3.8.x scaladoc parallel race** — multiple `doc` tasks running
  concurrently under `publish` crash in
  `dotty.tools.scaladoc.renderers.Resources.allResources`. Symptom: `(<module>Native
  / Compile / doc) java.lang.reflect.InvocationTargetException` partway through
  `sbt clean test publish`, leaving partial Maven artifacts on GitHub Packages.
  Workaround applied to `passesNative` and `riddlLibNative` in `build.sbt`:
  `.nativeSettings(Compile / doc / sources := Seq.empty)`. If a future Native
  module trips the same race, add the same one line.
- **`annotateErrorLine` tolerates EOF-boundary `At`** — when a parser failure
  points one past EOF (the typical "missing `}`" case), the failure's `endOffset`
  can exceed the line range computed by `lineRangeOf`. Downstream slicing already
  clamps via `Math.min`, so the function does NOT assert on the boundary. Don't
  reintroduce the `require(end >= index.endOffset, …)` check that lived there
  before 1.23.3 — it crashes the error reporter itself and surfaces the real
  parse error as `[severe] Exception Thrown` instead of a normal `[error]`.
- **sbt-riddl auto-downloads riddlc** — caches in `~/.cache/riddlc/<version>/`;
  three-tier resolution: explicit path > download > PATH. Use
  `--no-ansi-messages` and strip ANSI for version parsing. Pin `riddlcVersion` to
  a real release tag in scripted tests, not the dynver snapshot.
- **`ThirdPartyNotices.scala` is a hand-maintained CONSTANT and goes stale in
  SILENCE.** It is not generated and not read from a file, because only the JVM
  build has a filesystem — the Native binary has no resources at all and the same
  text must render under Scala.js. `ThirdPartyNoticesTest` pins the SHAPE (80
  columns, every license group, both links) but **cannot know a dependency was
  added**, so regenerate it whenever deps change: JVM truth is the staged
  `riddlc/universal/stage/lib` (what actually ships), JS/Native from
  `<mod>/Runtime/fullClasspath`, licenses from each artifact's POM in the
  Coursier cache (walk to the parent POM when the child declares none). **Do NOT
  take the copyright holder from `<developer>`** — that is the first committer,
  not the holder; for Apache projects read `META-INF/NOTICE` from the jar, which
  Apache-2.0 §4(d) requires be reproduced anyway. **riddl carries NO copyleft
  dependency** (all Apache-2.0/MIT/BSD-3-Clause) and the test asserts that
  ABSENCE — `must not include "logback" / "LGPL" / "ScalaTest"` — so a regression
  fails the build instead of quietly re-adding an obligation. The URL it prints
  is compiled into riddlc and cannot be silently redirected.
- **Run sbt as `sbt --server …` when you need to read its output.** The sbt 2 CLI
  is the `sbtn` native thin client talking to a DETACHED server, so piped stdout
  comes back **empty** and the build looks hung. `--server` runs in the
  foreground with attached stdout. Do not trust its exit code — grep the log.
- **sbt plugin visibility** — use `private[plugin] def` (not `private def`) so
  the compiler doesn't warn "private method never used" when sbt macros generate
  the usage. (The sbt-riddl plugin is now Scala 3 / sbt 2, but the pattern
  holds.)
