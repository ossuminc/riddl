# `!` / `not` Synonymy (A28) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `!` legal in every position `not` is, as the inverse of a boolean expression — so `!(a and b)`, `require !x` and `let y = !x` parse, and both spellings build the same node.

**Architecture:** `!` becomes an alternative inside `not_expression`, and the `when_condition` special case goes away. `WhenStatement.negated` is deleted: the negation becomes an ordinary `NotExpression` like every other. Prettify emits `not` for both spellings.

**Tech Stack:** Scala 3.9.0-RC4, sbt 2, fastparse, uPickle, ScalaTest.

**Ruling:** Reid, 2026-08-14 — *"`not` and `!` should be synonymous everywhere as the inverse of a boolean expression."* This **OVERRIDES** the 2026-08-13 ruling that `!` was accepted only as `when !<bare-identifier>` and "will not be extended". Do not restore that reasoning; it was weighed and overruled.

## Global Constraints

- Scala 3 syntax only.
- Every parser change needs a matching EBNF change, then a GBNF regeneration. Validators live in `language/src/test/scalajvm/python`; run them with `.venv/bin/python` (Homebrew python3 is externally managed and fails).
- Do NOT run `scalafmt` / `scalafmtCheckAll` — deferred to the 2.0 release.
- `<module>/testOnly *`, never bare `test`. ONE sbt invocation with `;` separators. **Count the `Suites: completed` lines**; the chain aborts at the first red module.
- **`FORMAT_REVISION` RIDES 18** — spent by numeric literals, and `git tag` shows the latest release is `2.0.0-rc.14`, so 18 has not shipped. Extend the revision-18 comment; do NOT bump to 19. Re-check `git tag` before assuming.
- Four suites are RED for pre-existing reasons: `Root2JsonCorpusTest` (59/190), `RiddlModelsRoundTripTest`, riddlc local-corpus, `ReportedIssuesTest` "should 406". A/B with `git stash` before attributing anything.
- Commit messages end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- NEVER `pkill -f sbt`.

## Corpus measurements (taken 2026-08-15, not estimated)

| form | uses in 190 models |
|---|---:|
| bare `!` negation | **0** |
| `not` negation | **597** |
| `!=` comparison | **0** |

**Nobody writes `!`.** Converging it to `not` therefore costs the corpus nothing, and deleting `WhenStatement.negated` breaks no model. The `!=` guard is still a hard correctness requirement — the operator exists in the language (`StringIn("==", "!=", …)`) and future models will use it.

## Decisions already made

- **Prettify emits `not`; `!` is a one-way alias that converges.** The precedent is `A | B`, which parses but prettifies to `one of { A or B }` because "RIDDL is meant to stay readable by people who are not computer scientists, so PrettifyPass emits the words". The corpus agrees empirically: 597 `not` against 0 `!`. **No `spelling` flag on the node** — that would make two ASTs meaning the identical thing compare unequal, the trap `EntityIntention.canonical` exists to avoid.
- **Round-trip tests assert CONVERGENCE, not byte-exactness**, for input written with `!`. Same shape as `is` → `:` on constants, and as `option persistent` being consumed into an intention.

---

## Task 1: Grammar and parser

**Files:**
- Modify: `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf` (`when_condition` `:275`, `not_expression` `:378`)
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/parsing/StatementParser.scala`
- Test: `language/src/test/scala/.../parsing/BangNotSynonymyTest.scala` (abstract) + registration in `JVMNativeTests.scala` AND `JSTests.scala`

- [ ] **Step 1: Write the failing test**

Abstract suite (`abstract class BangNotSynonymyTest(using PlatformContext) extends AbstractParsingTest`), registered in both aggregators. Assert that for each pair, **both spellings parse to the identical AST**:

| `not` form | `!` form |
|---|---|
| `when not isValid then … end` | `when !isValid then … end` |
| `require not x` | `require !x` |
| `let y = not x` | `let y = !x` |
| `when not (a and b) then … end` | `when !(a and b) then … end` |
| `when not not a then … end` | `when !!a then … end` |
| `when not a > b then … end` | `when !a > b then … end` |

Compare the parsed nodes for equality, not just "both parsed" — the point is one node for both.

**And the guard case, which is the one that will bite:** `when a != b then … end` must still parse as a comparison, NOT as `!` applied to `= b`. Add it in both a bare and a parenthesised position.

- [ ] **Step 2: Run to verify it fails**

```bash
sbt -batch "language/testOnly *BangNotSynonymyTest"
```

Confirm the log names `JVMNativeBangNotSynonymyTest`. **If no suite by that name appears the registration is missing and a green result means nothing.**

- [ ] **Step 3: Change the grammar**

```ebnf
not_expression = ("not" | "!") not_expression | comparison ;
when_condition = prompt_value | literal_string | boolean_expression | value_ref ;
```

Note what the second line does: the `"!" identifier` alternative is **deleted**, not left beside the general rule. Leaving both would keep a special case that the general rule already subsumes, which is how the two-ASTs problem started.

- [ ] **Step 4: Change the parser — and mind the `!=` guard**

Add `!` as an alternative to the `not` keyword in the `not`-level rule. **The guard is mandatory:**

```scala
// `!` must NOT match the `!` of `!=`. fastparse's `!` prefix is negative lookahead;
// regex lookahead is unavailable on Scala Native, so do NOT reach for one.
private def notOperator[u: P]: P[Unit] = P(Keywords.keyword("not") | ("!" ~~ !"="))
```

Use `~~` (no-whitespace) between `!` and the lookahead, or `! =` would slip through. `not` keeps its keyword word boundary so `notify` stays an identifier.

Then delete the `!`-handling from the `when`-condition parser.

- [ ] **Step 5: Run tests, regenerate grammars, commit**

```bash
sbt -batch "; language/testOnly * ; passes/testOnly *"
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py && .venv/bin/python ebnf_to_gbnf.py && .venv/bin/python gbnf_validator.py
```

---

## Task 2: Delete `WhenStatement.negated`

**Files:**
- Modify: `language/.../AST.scala` (`WhenStatement` `:3772`, its `format` `:3778-3779`)
- Modify: `passes/.../prettify/RiddlFileEmitter.scala` (`emitStatement`'s `WhenStatement` case)
- Modify: wherever `negated` is read — **grep for it and read every result**

- [ ] **Step 1: Grep first, and do not trust the compiler**

```bash
grep -rn "negated" --include='*.scala' language/src passes/src riddlLib/src
```

`passes` compiles under live `-Werror`; `language` and `commands` compile `--no-warnings` and will NOT tell you. Removing a public case-class field is a breaking change, permitted here because 2.0 is a major release — take it deliberately, and say so in the commit.

- [ ] **Step 2: Remove the field and its two `format` branches**

`WhenStatement.format`'s `Identifier` and `ValueRef` arms currently render `if negated then s"!${…}" else …`. Both collapse to the plain form; the negation is now a `NotExpression` wrapping the condition.

**`AST.WhenStatement.format` and `RiddlFileEmitter.emitStatement` are two copies of one dispatch** — the pair that already shipped a `MatchError` once because the tested copy concealed the broken one. Change both, and check they agree.

- [ ] **Step 3: Run the full suite, commit**

Existing tests asserting `when !isValid` produces a `negated` flag will fail. **That is this task working.** Update them to assert a `NotExpression`, and list every one you changed.

---

## Task 3: Prettify convergence

**Files:**
- Test: `passes/src/test/scala-jvm-native/.../prettify/BangNotRoundTripTest.scala`

- [ ] **Step 1: Write the failing test**

Model on `NumericLiteralRoundTripTest.scala` for the `PrettifyPass` creator-chain shape. For each position in Task 1's table:

1. Source written with `not` round-trips **byte-exact**.
2. Source written with `!` prettifies to the `not` form, and a **second** pass is stable (convergence, not oscillation).
3. `a != b` survives untouched — it is a comparison, not a negation.

Assert the emitted text, not merely that it re-parses: `!x` re-parses perfectly well after being mangled, which is exactly what this guards.

- [ ] **Step 2-4:** run to fail, fix at the source (`format`/`emitStatement`, never the test), commit.

---

## Task 4: BAST and JSON

**Files:**
- Modify: `language/.../bast/{BASTWriter,BASTReader,package}.scala`
- Modify: `riddlLib/.../json/{JsonModel,JsonAstBuilder,JsonifierPass}.scala`
- Test: BAST and JSON round-trip suites for both spellings

- [ ] **Step 1: Write the failing tests**

`WhenStatement` no longer carries `negated`, so its wire payload loses a field. Assert:
- Both spellings decode to the identical `NotExpression`.
- **A definition AFTER the `when` decodes intact** — a BAST error names where the reader DERAILED, never what derailed it, so a mis-sized payload surfaces on a later innocent node. This is the case that proves the codec.
- JSON: assert the field is gone AND that both spellings produce identical JSON. **A fixed-point test alone is insufficient** — a consistently-dropped field is still a perfect fixed point.

- [ ] **Step 2: Implement**

Writer and reader must mirror **exactly, field by field, in order** — trace them as a pair, not separately.

- [ ] **Step 3: Extend the revision-18 comment**, naming this change and what an older reader would misread. **Do not bump to 19.**

- [ ] **Step 4:** Regenerate `language/input/import/NotImplemented.bast` only if the revision byte changes — from its own directory, verifying with `cmp -l` that only byte 12 differs.

---

## Task 5: Corpus fixture, platforms, docs

- [ ] **Step 1:** Add `language/input/bang-not-synonymy.riddl` exercising `!` in every position from Task 1's table, plus a `!=` comparison. **Without a fixture under `input/`, the CI grammar validators never see the new syntax** — the numeric-literals fixture immediately exposed a real divergence (`1e3` failing under TatSu) that no internal test could have found.

- [ ] **Step 2:** Run the three validators and **check both numbers moved**. The TatSu baseline is **108/131**; one fixture should give **109/132**. A moved denominator with a static numerator means the fixture does not parse under the EBNF — fix the grammar, not the fixture. **Re-measure the baseline rather than trusting this number**; it has gone stale twice this week.

- [ ] **Step 3:** All five modules in ONE invocation, counting five `Suites: completed` lines; then `sbt -batch "; cJS ; cNative"`. JS/Native matter because `@JSExportTopLevel` binds to the very next definition and an AST edit can silently reattach an annotation, invisible to `cJVM`.

- [ ] **Step 4:** Corpus A/B with `git stash` against the four known reds. **Expect no change** — the corpus has zero `!` uses.

- [ ] **Step 5:** Drop a task file in `../ossum.tech/task/` for the language reference: `!` and `not` are synonymous everywhere, `not` is what prettify emits. Do NOT edit that repo.

- [ ] **Step 6:** Update `CLAUDE.md` — the § on `not`/`!` currently says "**The branch does NOT comply yet, and the work is in BACKLOG § 1**". Replace with what is then true. Remove the item from `BACKLOG.md` § 1.

---

## Post-Plan

**This item is not done when the code lands.** Per the definition of done, check whether `../RIDDL-Computational-Model.md` needs to record the synonymy — a generator lowering a boolean expression needs to know both spellings mean one node. If it does, that update is part of completing this item, not a follow-up.
