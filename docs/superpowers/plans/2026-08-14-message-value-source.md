# Plan: a message ref may name its VALUE

**Design:** `docs/superpowers/specs/2026-08-14-message-value-source-design.md` — read
it first; this plan does not restate the decisions.

**This is a WIDENING of A56, not a new feature.** `SendStatement.msg` (`AST:3496`)
and `TellStatement.msg` (`:3570`) are already `MessageRef | Constructor | ValueRef`,
the EBNF already has `deliverable_message_value` (`:298`) on `send`/`tell`, and
`operandType`/`operandMessageKind`/`operandMessageName` already handle the arm.
`on p: command Ping is { tell p to entity F }` works today.

## Global constraints

- **Scala 3 only.** New case-class parameters defaulted AND trailing, except where
  an existing trailing default forces otherwise (the A55/A57 precedent).
- **`@JSExportTopLevel` binds to the NEXT definition** — never insert an
  `enum`/`object` between an annotation and its case class. Check AST edits with
  `cJS` and `cNative`, not `cJVM` alone.
- **No silent fall-through.** A `case _ => ()` on a sealed hierarchy is forbidden
  where it means "I do not know what this is"; `throw` instead. **Enumerate the
  domain of the FUNCTION, not the nearest-looking type** — `statementValues` is
  wider than `Value`, and a total walk is still defeated if its INPUT drops a
  field (that is how `require X with initiate` evaded two bans).
- **`-Werror` is NOT a safety net.** A wildcard arm makes a match syntactically
  exhaustive, so the terminal `throw` this repo prescribes is itself what silences
  the compiler. Audit dispatch sites BY READING.
- **`FORMAT_REVISION` 16 → 17, bumped ONCE, in Task 3.** Regenerate
  `language/input/import/NotImplemented.bast` **from its own directory**; done
  right it is 93 bytes differing at byte 12 only.
- **Every parser change needs a matching EBNF change**, TatSu re-validated with
  `language/src/test/scalajvm/python/.venv/bin/python`, and GBNF regenerated.
- **Reflectivity:** parse → prettify → re-parse, BAST round trip, JSON coverage.
- Do NOT run `scalafmt` or report `scalafmtCheck`.
- Never run `pkill -f sbt`; do not run `sbt "cJVM; cJS; cNative"` chained.

## Task 1 — widen the SOURCE on `send`/`tell`

`checkBoundMessageOperand` (`ValidationPass.scala:920`) probes
`refMap.definitionOf[Type](vr.path)` — the key the resolver uses for an on-clause
**binding** — and errors otherwise. Replace that probe with the A55 /
lifecycle-parameter resolution path (`valueRefTypeExpr` / `typeExprOfPath`), then
require the resulting `TypeExpression` to be, or to alias to, an
`AggregateUseCaseTypeExpression` whose use case matches the statement's kind.

Its message currently names an on-clause binding as the ONLY legal source and
becomes a lie once widened — rewrite it to name the real set (state field,
binding, `let`-local, function result, `ask` result).

**`self` must fail with its own message** (design Q3), not the generic "does not
name a message": `self` is a synthesized aggregation carrying `id`/`version`, not
a message value.

Tests: one per source kind, each proven load-bearing by reverting the widening.

## Task 2 — extend the arm to `yield`, `reply`, and `morph`

- `YieldStatement.msg` (`AST:3591`) and `ReplyStatement.msg` (`:3627`) gain
  `ValueRef`; their EBNF rules move to `deliverable_message_value`.
- `MorphStatement.value` (`AST:3520`) becomes `RecordRef | Constructor | ValueRef`
  — this is riddlg's other 37.6% of holes.

**The stated reason `yield`/`reply` were excluded does not survive the widening**
(EBNF:296): their operand is compared against the clause's declared
`yields`/`replies`, and that comparison is by RESOLVED TYPE, which a `ValueRef`
supplies exactly as a `MessageRef` does. Keep that comparison working — it is a
real check, not an obstacle.

Watch the positional-match sweep: widening a `msg` field means every `case
SendStatement(...)`-style pattern and every dispatch over the operand union must
gain the arm. Audit by reading; the compiler will not tell you.

## Task 3 — reflectivity: prettify, BAST (revision 17), JSON

Three statements can now hold a shape they could not. All four surfaces move.
Round-trip tests for each of `yield` / `reply` / `morph` with a `ValueRef`
operand, on all three platforms, in `scala-jvm-native` where they can run there.

## Task 4 — the bare-form warning, with the field-less exemption

A bare `MessageRef` operand on `send`/`tell`/`yield`/`reply`, and a bare
`RecordRef` on `morph`, draws a **CompletenessWarning**: it names a type, not a
value.

**EXEMPT a message whose resolved type has no fields** (design Q1, decided
2026-08-14). `event Started is { }` has no data, so the type fully determines the
value and there is nothing to source; warning on it is the noise the `???` ruling
exists to prevent.

**COUNT what the exemption removes** from the 14,730 corpus bare refs and report
it. That number has been quoted to riddlg and must be corrected if it moves.

**This is a WARNING now and an ERROR later** — do not ship the Error. The corpus
holds 14,730 bare refs and ZERO constructor uses, so an Error invalidates every
message-sending statement in all 189 models while CI requires 189/189 clean.

## Task 5 — the unused-`initiate`-id warning

Reid, 2026-08-14: *"no further task is needed, just build it."* A
`let x = initiate …` whose id is **never subsequently referenced** draws a plain
**Warning** — on by default, NOT gated behind `showCompletenessWarnings`, since
unlike a missing address this is locally decidable from the clause body.

**The real work is the escape-route analysis, and it must be CONSERVATIVE.** An id
is used if it is `set` into state, passed to a `tell`/`send`/`reply`, passed to
`terminate`, yielded in an event, put to a repository, returned, or read in any
value position. Missing a route means a false warning on correct code. When in
doubt, treat it as used.

**A self-terminating worker legitimately has an unused id** — that is precisely
why Reid graded this a Warning rather than an Error, and the test suite must
contain that case as a legal counter-example.

## Task 6 — pin the saga-step ruling

Reid, 2026-08-14: `initiate`/`terminate` ARE legal in a saga step, because a saga
may need new entities created. That is the behaviour today, but **by accident** —
`checkInstanceEffectScope`'s predicates are structurally false for a `SagaStep`
(a `Leaf`, never pushed onto the parent stack). Add a test asserting both validate
clean there, citing the ruling, so a future tightening cannot silently remove it.

## Task 7 — certify and close

Full tri-platform certification from clean under a throwaway `--sbt-cache`.
Current floors: **JVM 2400 / JS 750 / Native 1671** — predict the per-row delta
BEFORE reading it and reconcile; a mismatch is a skipping bug. Raise the floors in
`.claude/skills/rc/SKILL.md`. Run all four validators. Corpus A/B with counts by
severity — expect the bare-form warning to move it substantially and understand
the number.

Notify riddlg (their task is closed in `task/done/`; append a note), and file the
riddl-models migration toward the eventual Error.
