# A20 Typed Holes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an author write `prompt("compute the discount") as Currency`, so the seam between RIDDL's deterministic tier and its AI tier carries a checkable type.

**Architecture:** One optional field on the existing `PromptValue` — no new node, no union widening. `prompt("…")` and `prompt("…") as T` differ by an `Option`, not by wire shape. Validation follows A57: the ascription RESTATES the type the position already supplies and never overrides it; a contradiction is an Error.

**Tech Stack:** Scala 3.9.0-RC4, sbt 2 (sbt-ossuminc 3.0.3), fastparse, uPickle, ScalaTest.

**Design doc:** `docs/superpowers/specs/2026-08-14-a20-typed-holes-design.md`

## Global Constraints

- **Scala 3 syntax only.**
- **Every parser change needs a matching EBNF change**, then a GBNF regeneration. Validators are in `language/src/test/scalajvm/python`; run them with `.venv/bin/python` — Homebrew's python3 is externally managed and fails.
- **`FORMAT_REVISION` RIDES 18. Do NOT bump to 19.** 18 was spent by numeric literals (`6cfeceb2f`) and has **not shipped** — the latest tag is `2.0.0-rc.14`. Folding in avoids a needless `.bast` regeneration across riddl-models, exactly as BACKLOG § 2 prescribes. Extend the revision-18 comment to name this change. **Check `git tag` first**; if 18 has shipped by then, bump to 19 and say so in the commit.
- **Do NOT run `scalafmt` / `scalafmtCheckAll`** — deferred to one pass at 2.0 release.
- **`<module>/testOnly *`, never bare `test`** — `test` resolves to `testQuick`, which silently skips suites.
- **ONE sbt invocation with `;` separators.** Multiple quoted arguments run only the FIRST and exit 0. **Count the `Suites: completed` lines.**
- **Three suites are RED for pre-existing reasons:** `Root2JsonCorpusTest` (59/190), `RiddlModelsRoundTripTest`, riddlc local-corpus. A/B with `git stash` before attributing anything.
- Commit messages end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- **NEVER `pkill -f sbt`.**

## Test-harness traps — every one has bitten this repo, several twice

- A fixture using `function F is { body { … } }` does **not** parse; `body { }` is not a function-content wrapper. Use an entity/handler or `on init` shape.
- A **parse-error** assertion cannot use `parseAndValidate` — its `Left` branch calls `fail` directly, so the assertion is unreachable and the case can only pass. Use `TopLevelParser.parseInput` and match on `Left`.
- A **parse-time message** assertion needs `TopLevelParser.parseInputWithMessages`; `parseAndValidate` discards them.
- Suites in the shared `src/test/scala` tree are **abstract** with `(using PlatformContext)` and need concrete subclasses in `JVMNativeTests.scala` AND `JSTests.scala`. Without them the suite never runs and says nothing.
- JSON suites are plain `AnyWordSpec` — cases take **no** `(td: TestData)` parameter; one there builds a `Function1` and never runs the body.
- Validation tests asserting a StyleWarning/Completeness message must set the corresponding `CommonOptions` flag.

---

## A DECISION FOR REID BEFORE TASK 3

**The untyped-seam warning's accuracy depends on how many positions we wire, and we do not wire them all.**

The design says a `prompt("…")` in a position where *nothing* supplies a type draws a CompletenessWarning. But "supplies a type" is not a property of the language — it is a property of **which call sites we thread an expected type through**. Established while building numeric literals (Task 5): only `validateConstant` (via `c.typeEx`) and `checkValueType` (`let`/`set`) carry one today. **Constructor arguments and comparison operands do not**, and wiring them was explicitly ruled out of scope there as the "general mechanism" the design also declines to build.

So a naive implementation warns on `record R(prompt("the total"))` — where the field type IS known to the language, just not to the check. That is a false positive on a form the design's own § 1 table lists as *silent*.

**Recommendation: make the warning CONSERVATIVE — fire only where we positively know no type exists**, which today means an unascribed `let x = prompt("…")` with no declared type. Everywhere else stays silent until its position is wired. A warning that fires on correct code teaches authors to ignore it, and this codebase has paid that price twice (1120 false "external context" warnings; 854 hidden `populates` warnings). Under-warning is recoverable; over-warning is what gets a check disabled.

**Alternative if Reid prefers coverage:** wire constructor arguments too (the field type is available where `checkConstructorArgs` already resolves it), then warn everywhere else. Larger, and it starts down the road to the general mechanism.

**Task 3 must not be dispatched until this is answered.** Tasks 1, 2, 4 and 5 are unaffected.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `language/.../AST.scala` | `PromptValue.typeEx`, `format` | 1 |
| `language/.../parsing/StatementParser.scala` | `promptValue` gains the ascription | 1 |
| `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf` | `prompt_value` rule | 1 |
| `passes/.../validate/ValidationPass.scala` | restate/contradict Error, seam warning | 3 |
| `language/.../bast/{package,BASTWriter,BASTReader}.scala` | optional type on the wire | 4 |
| `riddlLib/.../json/{JsonModel,JsonAstBuilder,JsonifierPass}.scala` | optional type in JSON | 4 |
| `language/input/typed-holes.riddl` | corpus fixture for CI grammar validation | 5 |

---

### Task 1: The ascription — AST, parser, grammar

**Files:**
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/AST.scala` (`PromptValue`, `:3218`)
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/parsing/StatementParser.scala` (`promptValue`, `:446`)
- Modify: `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf` (`prompt_value`, `:321`)
- Test: `language/src/test/scala/.../parsing/TypedHoleTest.scala` (abstract) + registration in both aggregators

**Interfaces:**
- Produces: `PromptValue(loc: At, prompt: LiteralString, typeEx: Option[TypeExpression] = None)`.

- [ ] **Step 1: Write the failing test**

An abstract suite (`abstract class TypedHoleTest(using PlatformContext) extends AbstractParsingTest`) asserting:
- `prompt("x")` parses with `typeEx == None` (unchanged behaviour).
- `prompt("x") as Real` parses with `typeEx` a predefined `Real`.
- `prompt("x") as OrderId` parses with `typeEx` an aliased type reference.
- The ascription survives in each position: a `let`, a constructor argument, a `set`, and a `when` condition.
- `prompt` with no parens (the deprecated STATEMENT form, `do "…"`) is unaffected.

Register concrete subclasses in `JVMNativeTests.scala` and `JSTests.scala`, then **confirm the log names the suite** and canary it with a temporary `fail("canary")`.

- [ ] **Step 2: Run to verify it fails**

```bash
sbt -batch "language/testOnly *TypedHoleTest"
```

- [ ] **Step 3: Widen the AST node**

```scala
  @JSExportTopLevel("PromptValue")
  case class PromptValue(
    loc: At,
    prompt: LiteralString,
    typeEx: Option[TypeExpression] = None
  ) extends RiddlValue:
    override def kind: String = "Prompt Value"
    def format: String =
      s"prompt(${prompt.format})" + typeEx.map(t => s" as ${t.format}").getOrElse("")
  end PromptValue
```

The `= None` default is legal here because it is **trailing** — `@JSExportTopLevel` forbids only a non-trailing default, and `PromptValue` has no `contents`/`metadata` after it. (This differs from A55/A57, whose new fields had to go undefaulted for exactly that reason.) The default also keeps every existing `PromptValue(loc, str)` construction source-compatible.

- [ ] **Step 4: Widen the parser**

```scala
  private[parsing] def promptValue[u: P]: P[PromptValue] = {
    P(
      Index ~ Keywords.prompt ~ Punctuation.roundOpen ~/ literalString ~
        Punctuation.roundClose ~ (Keywords.keyword("as") ~/ typeExpression).? ~/ Index
    )./.map { case (start, str, typeEx, end) => PromptValue(at(start, end), str, typeEx) }
  }
```

Use `Keywords.keyword("as")`, **not** `Readability.readable("as")` — `readable` now carries a word boundary too (fixed 2026-08-15), but `as` here is a real keyword introducing a type, not a readability word that may be omitted.

**No ambiguity**, verified: every `as` in the grammar follows an identifier, a keyword, or an import string — never a value expression. Sites: `selective_bast_import` (`:54`), `on_other_clause` (`:251`), `as_shape` (`:424`), `byAs` (`:35`).

- [ ] **Step 5: Update the EBNF and regenerate GBNF**

```ebnf
prompt_value = "prompt" "(" literal_string ")" [ "as" type_expression ] ;
```

```bash
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py && .venv/bin/python ebnf_to_gbnf.py && .venv/bin/python gbnf_validator.py
```

- [ ] **Step 6: Run tests and commit**

```bash
sbt -batch "; language/testOnly * ; passes/testOnly *"
```

Two `Suites: completed` lines. Note `passes` compiles under live `-Werror` and may surface dispatch sites that destructure `PromptValue` positionally — the new field breaks those. `language` and `commands` compile `--no-warnings`, so **grep for `PromptValue(` and read the results**; the compiler will not tell you everywhere.

---

### Task 2: Prettify round trip

**Files:**
- Test: `passes/src/test/scala-jvm-native/.../prettify/TypedHoleRoundTripTest.scala`

Prettify emits values via `.format`, so Task 1's `format` is the whole implementation. This task proves it.

- [ ] **Step 1: Write the failing test**

Model on `passes/src/test/scala-jvm-native/.../prettify/NumericLiteralRoundTripTest.scala` (added 2026-08-15; it has the exact `PrettifyPass` creator-chain shape). Assert for both `prompt("x")` and `prompt("x") as Currency`:
1. parse → prettify → the emitted source contains the construct verbatim,
2. re-parse → `typeEx` is unchanged (`None` stays `None`, `Some(T)` stays the same `T`).

**Assert the ascription's presence AND absence.** A `format` that always appends ` as …` would round-trip the typed form perfectly while corrupting every untyped one, and a test covering only the typed case would pass.

- [ ] **Step 2-4: Run to fail, fix any divergence at the source (`format` or the parser capture, never the emitter), commit.**

---

### Task 3: Validation — restates, contradicts, and the seam warning

**BLOCKED until Reid answers the decision above.**

**Files:**
- Modify: `passes/src/main/scala/com/ossuminc/riddl/passes/validate/ValidationPass.scala`
- Test: `passes/src/test/scala-jvm-native/.../validate/TypedHoleValidationTest.scala`

**Interfaces:**
- Consumes: `PromptValue.typeEx` (Task 1).
- Produces: `checkPromptAscription(pv: PromptValue, expected: Option[TypeExpression]): Unit`.

- [ ] **Step 1: Write the failing test**

| written | expected |
|---|---|
| `let x: Currency = prompt("d") as Currency` | silent — restates |
| `let x: Currency = prompt("d") as Real` | **Error** — contradicts |
| `constant G: Real = prompt("gravity") as Real` | silent — restates |
| `constant G: Real = prompt("gravity") as Currency` | **Error** — contradicts |
| `constant G: Real = prompt("gravity")` | silent — type from the constant |
| `let x = prompt("d")` | CompletenessWarning (per the ruling) |
| `let x = prompt("d") as Currency` | silent |

Use the `diagnostics` helper shape from `NumericLiteralConformanceTest` — it **fails loudly when a fixture does not parse**, which matters because half these cases assert the ABSENCE of a message, and an unparsed fixture satisfies that for free.

- [ ] **Step 2: Run to verify it fails.**

- [ ] **Step 3: Implement**

Wire at the sites that already carry an expected type: `validateConstant` (`c.typeEx`) and `checkValueType` (`let`/`set`). The `when`-condition position implies `Boolean`.

**Hook via `checkStatementScopes`, NOT `validateStatement`** — the latter never sees statements held in a FIELD (`when`/`match`/`foreach`), and a `when` condition is exactly that. Two tasks of the instance-identity plan fell into this.

**Do NOT build a general `expectedTypeOf`.** No such function exists; creating one is explicitly out of scope and is the design's own recommendation.

- [ ] **Step 4-5: Run tests, commit.**

---

### Task 4: BAST and JSON

**Files:**
- Modify: `language/.../bast/BASTWriter.scala` (`writeValue`'s `PromptValue` arm, tag 4)
- Modify: `language/.../bast/BASTReader.scala` (`readValue` case 4)
- Modify: `language/.../bast/package.scala` (extend the revision-18 comment)
- Modify: `riddlLib/.../json/{JsonModel,JsonAstBuilder,JsonifierPass}.scala`
- Test: `passes/src/test/scalajvm/.../TypedHoleBASTRoundTripTest.scala`, `riddlLib/src/test/scala/.../TypedHoleJsonRoundTripTest.scala`

- [ ] **Step 1: Write the failing tests**

BAST: round-trip both forms; assert `typeEx` survives as the right `TypeExpression`, not merely as equivalent text. **Include a definition AFTER the prompt and assert it decodes intact** — a BAST error names where the reader DERAILED, never what derailed it, so a mis-sized payload surfaces on a later innocent node. That case is the one that proves the codec.

JSON: round-trip both forms plus the identity fixed point. **The fixed point alone is insufficient** — a consistently-dropped field is still a perfect fixed point. Assert `typeEx` explicitly.

- [ ] **Step 2: Run to verify they fail.**

- [ ] **Step 3: Implement**

Writer, in the existing tag-4 arm — **append**, so an untyped prompt's bytes are unchanged apart from the new flag:

```scala
      case pv: PromptValue =>
        writer.writeU8(4)
        writeLocation(pv.loc)
        writeLiteralString(pv.prompt)
        writeOption(pv.typeEx)(writeTypeExpression)
```

Reader mirrors it exactly:

```scala
      case 4 => // PromptValue
        val loc = readLocation()
        val what = readLiteralString()
        PromptValue(loc, what, readOption(readTypeExpression()))
```

**Trace the writer and reader as a PAIR, in order.** A writer emitting location-then-string against a reader taking string-then-location misaligns the stream, and the failure appears far away.

**Watch the disjoint tag sets:** the type expression is written with `TYPE_*` tags inside a node otherwise built from `NODE_*` tags. Crossing them causes byte misalignment surfacing as "Invalid string table index".

JSON: add an optional `type` to the prompt DTO. Note the DTO's kind string is **`"numeric"`-style short form** — check what the prompt DTO actually uses rather than assuming, since the plan text and code have diverged on this before.

- [ ] **Step 4: Extend the revision-18 comment**, naming this change alongside numeric literals. Do NOT bump to 19 (see Global Constraints).

- [ ] **Step 5: Regenerate `language/input/import/NotImplemented.bast`** only if the revision byte changes — from its own directory, verifying with `cmp -l` that only byte 12 differs.

- [ ] **Step 6: Run `; language/testOnly * ; passes/testOnly * ; riddlLib/testOnly *`, commit.**

---

### Task 5: Corpus fixture, platforms, documentation

- [ ] **Step 1:** Add `language/input/typed-holes.riddl` exercising both forms, an ascription in each position, and both a predefined and an aliased type. **Without a fixture under `input/`, the CI grammar validators never see the new syntax** — the numeric-literals fixture immediately exposed a real parser/EBNF divergence (`1e3` failing under TatSu) that no internal test could have found.

- [ ] **Step 2:** Run the three grammar validators. The TatSu baseline is **106/129**; one fixture should make it **107/130**. **If the denominator moves but the numerator does not, the fixture does not parse under the EBNF** — fix the grammar, not the fixture.

- [ ] **Step 3:** Full suite across all five modules in one invocation, counting five `Suites: completed` lines; then `sbt -batch "; cJS ; cNative"`. The JS/Native compile matters specifically because `@JSExportTopLevel` binds to the very next definition and an edit near an exported type can silently reattach an annotation — invisible to `cJVM`.

- [ ] **Step 4:** Corpus A/B with `git stash` against the three known reds.

- [ ] **Step 5:** Drop a task file in `../ossum.tech/task/` documenting the typed-hole syntax for the language reference. **Do not edit that repo** — one instance per project. Add the A20 syntax to the existing BACKLOG § 0 item for the Computational Model.

- [ ] **Step 6: Commit.**

---

## Post-Plan

- Remove the A20 item from `BACKLOG.md` § 2.
- Update the revision-18 note to record that A20 rode it, leaving A38 as the last claimant.
- NOTEBOOK: what it taught. CLAUDE.md: that a typed hole restates and never overrides, and that a constant with a `prompt` value needs no ascription because the constant declares the type.
