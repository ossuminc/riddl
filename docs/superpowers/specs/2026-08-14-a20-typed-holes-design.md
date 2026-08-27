# A20 — typed holes, `prompt("…") as T` — design

**Date:** 2026-08-14
**Branch:** `release/2`
**Status:** Approved by Reid, 2026-08-14. Ready for an implementation plan.
**Backlog item:** § 2, "A20 typed holes — APPROVED, spelled `prompt("…") as T`."

A20 asks for a vague-but-typed expression: the type is known and checkable, the
computation is prose an AI fills in at generation time. It types the seam
between the deterministic and AI tiers, which is untyped today.

The untyped relative already shipped — `prompt_value = "prompt" "("
literal_string ")"` (`ebnf-grammar.ebnf:321`), `PromptValue(loc, prompt)`
(`AST.scala:3218`). This adds an optional type to it.

Reid chose this spelling over `prompt T ("…")` and over the document's un-RIDDL
`Value[T]("prose")`: it reuses the shipped `prompt` and ascribes a type after
it, so nothing new enters the lexer.

## 1. Scope — smaller than the backlog entry implies

**In most positions the type is already available without `as T`.** Established
while designing numeric literals, and worth stating before deciding how hard to
push the ascription:

| position | the type comes from |
|---|---|
| `constant Gravity is Real = prompt("…")` | the constant's declared type |
| `let x: Currency = prompt("…")` | the `let` ascription |
| `record R(prompt("…"))` | the target field's type |
| `set S.total to prompt("…")` | the field's type |
| `when prompt("…")` | implicitly `Boolean` |

So `as T` earns its keep specifically where **nothing** declares a type —
chiefly `let x = prompt("…")` and bare operand positions. It is optional
everywhere and never required.

## 2. Syntax

```ebnf
prompt_value = "prompt" "(" literal_string ")" [ "as" type_expression ] ;
```

`type_expression`, not `type_ref`, so both `as Real` (predefined) and
`as OrderId` (named) work — the same thing `constant` accepts.

**No parse ambiguity, verified.** Every `as` in the grammar follows an
identifier, a keyword, or an import string — never a value expression:

| site | preceded by |
|---|---|
| `selective_bast_import` (`:54`) | a literal string in an import |
| `on_other_clause` (`:251`) | the keyword `other` |
| `as_shape` (`:424`) | a processor identifier |
| `byAs` (`:35`, 8 uses) | `briefly`, `described`, `link`, `contains`, `of`, `label`, a mime type |

## 3. The AST

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

One node, not two. `prompt("…")` and `prompt("…") as T` differ by an optional
field, not by wire shape, so a separate `TypedHole` node would double the
dispatch arms, BAST tags and JSON kinds for nothing. Same shape as A55's
`binding: Option[Identifier]` and A57's `envelopeType: Option[TypeRef]`.

The default `= None` is safe here because it is TRAILING — `@JSExportTopLevel`
forbids only a non-trailing default, and `PromptValue` has no `contents` or
`metadata` field to sit after it. This differs from A55/A57, where the new
optional fields had to be declared without defaults precisely because
`contents`/`metadata` are defaulted and must trail. The default also keeps every
existing `PromptValue(loc, str)` construction source-compatible.

## 4. Validation

### 4.1 Ascription restates, never overrides

Follows A57 exactly. Where the position already supplies a type:

| written | result |
|---|---|
| `let x: Currency = prompt("d") as Currency` | silent — restates |
| `let x: Currency = prompt("d") as Real` | **Error** — contradicts |
| `when prompt("is it valid") as Boolean` | silent — restates |
| `when prompt("is it valid") as Currency` | **Error** — contradicts |

Agreement earns no complaint: writing the type out is a legitimate readability
choice, letting the hole read standalone. A contradiction is a
self-contradiction, and those are Errors.

An overriding ascription was considered and rejected on A57's reasoning —
reading one site would tell you nothing about what it means.

### 4.2 The untyped seam

A `prompt("…")` in a position where **nothing** supplies a type draws a
**CompletenessWarning** suggesting `as T`. This is the untypedness A20 was filed
about; a warning names it without breaking any existing model, and `as T`
remains optional.

Gated behind `showCompletenessWarnings` like its siblings.

### 4.3 Conformance at the use site

Once a hole has a type — ascribed or contextual — it is checked like any other
value. `let x: String = prompt("…") as Currency` is an Error through the
existing assignment-compatibility path, not through new machinery.

## 5. The central implementation cost — no single "expected type"

**There is no `expectedType(position)` function today, and this is the main
driver of the work.** `valueType` (`ValidationPass.scala:6054`) answers the type
*of* a value; the inverse — what type this position *wants* — is computed
locally at each site, as `checkLifecycleInvocation` (`:5775`) does for
constructor arguments.

So § 4.1 and § 4.2 cannot be wired in one place. Each position in § 1 must
supply its expected type:

1. `let` with a declared type — `letType` already has it (`:6198`).
2. `constant` — `Constant.typeEx`, directly.
3. Constructor argument — the target aggregate's field type, resolved where
   `checkConstructorArgs` already resolves it.
4. `set` target — the resolved field's type.
5. `when` condition — implicitly `Boolean`.
6. Everything else — no expected type, so § 4.2 fires.

**Two options, and the plan should pick deliberately:** thread expected type
through the existing per-site checks (smaller, five local changes, no new
concept), or introduce a real `expectedTypeOf(value, parents)` helper (larger,
but the next feature needing it inherits it). Recommend the former for this
work and the latter only if a third feature asks — YAGNI, and a half-built
general mechanism is worse than five honest local ones.

**Where to hook is a known trap.** `validateStatement` never sees statements
held in a FIELD (`when`/`match`/`foreach`), which is where a `when` condition
lives. Use `checkStatementScopes`, per CLAUDE.md § Total Dispatch — two tasks of
the instance-identity plan fell into exactly this.

## 6. Reflection surfaces

| surface | change |
|---|---|
| Parser | `promptValue` (`StatementParser.scala:446`) gains `("as" ~ typeExpression).?` |
| EBNF | `prompt_value` rule (`:321`) |
| GBNF | regenerated via `ebnf_to_gbnf.py`, re-validated |
| Prettify | `format` appends ` as T` when present |
| BAST | `PromptValue` payload gains an optional type expression |
| JSON | new optional field on the prompt-value kind |

**Rides `FORMAT_REVISION` 18**, which the numeric-literals work spends (17 → 18).
If A20 lands FIRST for any reason, it spends the bump instead and says so in its
commit — but the two must not bump twice. See BACKLOG § 2.

**Watch the disjoint tag sets.** The optional type expression is written with
`TYPE_*` tags inside a node otherwise built from `NODE_*` tags; crossing them
misaligns the stream and surfaces far from the cause. See CLAUDE.md § BAST.

## 7. Dispatch sites

`PromptValue` is an existing `Value` arm, so no union widens and the § 7 sweep
from the numeric-literals design does not repeat here. But `format` changes
shape, so:

- **`AST.WhenStatement.format` and `RiddlFileEmitter.emitStatement` are two
  copies of one dispatch.** The `PromptValue` arm was missing from the first
  until 2026-08-14 and nobody noticed, because prettify routes through the
  second. Any change to how a prompt value renders must be made in both, and
  both must be exercised.
- Anywhere `PromptValue` is destructured positionally rather than by name will
  break on the new field — grep before assuming the default covers it.

## 8. Testing

- Parser: `prompt("x")`, `prompt("x") as Real`, `prompt("x") as OrderId`, and
  the ascription in each position of § 1.
- Round trip: parse → prettify → re-parse, both with and without `as T`,
  asserting the ascription survives at the same place.
- BAST and JSON round trips for both forms.
- Validation: one case per row of the § 4.1 table, plus a § 4.2 case and a
  negative control that a contextually-typed hole stays silent.
- **A `when prompt("…") as T` case specifically**, since that path goes through
  the duplicated dispatch of § 7 and is the one a round-trip test would
  otherwise miss.
- Corpus: `RiddlModelsRoundTripTest` plus the TatSu/GBNF validators, with a
  fixture exercising the ascription so CI grammar validation covers it.

Take a corpus baseline first — the corpus is red on this branch for three
pre-existing reasons (NOTEBOOK § HANDOFF).

## 9. Out of scope

- Making `as T` mandatory. Considered; it would break existing models using a
  bare `prompt("…")` in an untyped position and needs a corpus survey first.
- A general `expectedTypeOf` helper — see § 5.
- Typed holes anywhere `PromptValue` is not already legal. This adds a type to
  an existing value; it does not widen where prompts may appear.
