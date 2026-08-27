# Numeric literals in `Value` — design

**Date:** 2026-08-14
**Branch:** `release/2`
**Status:** Approved by Reid, 2026-08-14. Ready for an implementation plan.
**Backlog item:** § 2, "Numeric literals in `Value` — APPROVED, integers AND
decimals."

RIDDL has no way to write a number. Every numeric value is either quoted
(`constant PointsPerDollar is Natural = "10"`) or unwritable
(`initiate entity Order(1)`, `record R(1)`, `count > 5`). This adds one literal
node and widens the three places a number belongs.

## 1. Syntax

```ebnf
numeric_literal = [ "+" | "-" ] digit {digit} [ "." digit {digit} ]
                  [ ("e"|"E") [ "+" | "-" ] digit {digit} ] ;
```

| legal | illegal |
|---|---|
| `1  -1  +3  007` | `1_000` (no digit separators) |
| `1.5  -0.25  1.50` | `0xFF` (no radix prefixes) |
| `1e3  1.5e-3  2E+8` | `.5`  `1.`  `1,5` |

**No lexical ambiguity.** An identifier must begin with a letter
(`CommonParser.simpleIdentifier`, `:281`), so nothing beginning with a digit or
a sign can be one, and a path identifier cannot be confused with a decimal
point. Digit separators and radix prefixes were considered and declined (Reid,
2026-08-14): no motivating case needs them, and every form is carried forever by
four reflection surfaces. Both are pure additions later if wanted.

## 2. The AST node

```scala
@JSExportTopLevel("NumericLiteral")
case class NumericLiteral(loc: At, text: String) extends Value:
  override def kind: String = "Numeric Literal"
  def format: String = text
  def isInteger: Boolean = !text.exists(c => c == '.' || c == 'e' || c == 'E')
  def asLong: Long = text.toLong
  def asBigDecimal: BigDecimal = BigDecimal(text)
end NumericLiteral
```

**The literal is stored AS WRITTEN, and that is the whole point.** `1.50`,
`007` and `+3` are not recoverable from a parsed number, so a `Long`/`BigDecimal`
payload would make prettify diverge from the source on its first use. Storing
text makes the round trip byte-exact by construction, needs one BAST tag and one
JSON kind rather than two, and avoids `BigDecimal` on Native and JS entirely.
Same reasoning already recorded for `UniqueId.kindKeyword` ("stored as written,
so prettify is byte-exact without a mapping table") and for correlation keys.

`isEmpty` is deliberately NOT overridden: it inherits the `true` default, which
is correct — a literal is a non-container, and emptiness asks whether a node has
contents, never whether the author supplied it. See CLAUDE.md § Emptiness.

`loc` is not defaulted, matching the sibling value nodes — `@JSExportTopLevel`
forbids a non-trailing default.

## 3. Unions widened

- **`Value`** (`AST.scala:2981`) — gains `NumericLiteral`. This alone unblocks
  `initiate entity Order(1)`, `record R(1)`, `let x = 5`, `set … to 5` and
  constructor arguments generally.
- **`Comparand`** (`AST.scala:3255`) — gains `NumericLiteral`, so `count > 5`
  parses. **This reverses a deliberate A28 decision** and the reversal is
  Reid's, taken 2026-08-14 with the original reasoning in front of him.

### The A28 reversal, recorded so it is not "fixed" back

`Comparand` was narrowed to refs on purpose. The AST says so at `:3262` —
*"Comparison operands, by contrast, are narrowed to `Comparand` (ref-only) so
magic-constant comparisons cannot be constructed at all"* — and the parser
repeats it at `StatementParser.scala:529-533`.

The rule has had no uptake to measure. **The entire riddl-models corpus contains
exactly one constant** (`constant PointsPerDollar is Natural = "10"`), so
"name the number instead" is advice essentially nobody has taken — plausibly
because the only way to name one was to put it in a string.

The intent survives as advice: a `NumericLiteral` in a comparand position draws
a **StyleWarning** suggesting a named constant. Its population starts at
**zero**, because `count > 5` is a parse error today and no existing model can
contain one. The warning only ever fires on newly written comparisons.

## 4. `Constant` — value and separator

Today `Constant.value` is a `LiteralString` (`AST.scala:2956`) and the parser
demands one (`TypeParser.scala:828`), so every constant's value is quoted.

```scala
type ConstantValue = LiteralString | NumericLiteral | BooleanLiteral | PromptValue
```

Defined as a narrowing of `Value`, exactly as `Comparand` is. Deliberately NOT
the full `Value` union, which would admit `Call`, `Ask` and `Initiate` in a
constant.

| form | meaning |
|---|---|
| `constant Max: Integer = 5` | numeric literal |
| `constant Enabled: Boolean = true` | boolean literal, no longer quoted |
| `constant Name: String = "Fred"` | string literal, unchanged |
| `constant Gravity: Real = prompt("the gravitational constant")` | typed hole |

**The `prompt` form is a typed hole** (Reid, 2026-08-14): the type is declared
and checkable, the computation is prose filled in by AI at generation time. It
needs no `as T` ascription because the constant already declares its type — a
precedent that lands ahead of, and informs, the A20 work. It is **exempt from
the conformance checks in § 5**: there is no value to check.

**Separator: NO parser change — the `is` production already accepts both.**
Reid, 2026-08-14: *"we can think of it as a solo field which also uses `:`."*
`:` is already RIDDL's ascription mark (`let x: T`, `on foo: command Foo`,
`p1: String`), so a constant reads as a field with a value.

```scala
// CommonParser.scala:38
def is[u: P]: P[Unit] = Keywords.keywords(StringIn("is", "are", ":", "=")).?
```

`constant Gravity : Real = 5` therefore parses today, as do `are`, `=`, and
omitting the separator entirely. **All spellings are legal and none warns.**
The only change is that prettify emits `: ` instead of `is ` — one line at
`RiddlFileEmitter.scala:253`.

**No spelling field, and no new reflectivity deviation.** An earlier draft of
this design treated "written `is`, prettified `:`" as a deviation from
byte-exact recovery needing precedent. That was wrong: the `is` production has
always discarded which of five spellings the author wrote, everywhere in the
language, so a constant is no different from a domain or a type in this respect.
The round trip converges on pass two, as it already does for every other `is`
in every model. Nothing special to test beyond the ordinary round trip.

**Latent defect to fix alongside.** `Constant.format` (`AST.scala:2962`) emits
`const `, but the keyword is `constant` (`Keywords.scala:584`), so that text
does not re-parse. It is invisible because `PrettifyVisitor` routes through
`emitConstant`, which is correct — the same two-copies-of-one-dispatch trap as
`WhenStatement.format` vs `RiddlFileEmitter.emitStatement`, where the exercised
copy concealed the broken one. Fix both the keyword and the separator in
`format` so the two copies agree.

**Deprecating the quoted number.** `constant Max is Natural = "10"` continues to
parse and draws a deprecation warning pointing at the unquoted form. Scoped
precisely: the warning fires only when the constant's declared type is a
`NumericType` (or `Boolean` for `"true"`/`"false"`) AND the string's content
parses as a literal of that type. A `String`-typed constant is never warned, and
a numeric-typed constant whose string is not a number is left to § 5.2. Corpus
cost is one line.

**The separator never warns, in any of its spellings** (Reid, 2026-08-14: *"Both
are legal, and should not warn"*). The quoted-value form above is the only new
warning in this section.

**This is a breaking change to a public field's type.** The backward
compatibility policy permits that only in a major release; `release/2` is 2.0.0,
so this is a deliberate use of that window rather than a side effect.

## 5. Validation

### 5.1 The integer types, defined at last

`Natural` and `Whole` are both `IntegerTypeExpression` (`AST.scala:2521-2525`)
with **no doc comment, no language-reference entry, and no Computational Model
definition**. Nothing in the repository says what they mean. Ruled by Reid,
2026-08-14:

| type | range |
|---|---|
| `Integer` | signed — `… -2 -1 0 1 2 …` |
| `Whole` | non-negative — `0 1 2 …` (counts) |
| `Natural` | positive — `1 2 …` (ordinals) |

**Recording this is part of the work, not a footnote.** A check cannot enforce a
rule the language never states, and an error message asserting `Natural ≥ 1`
against an undocumented type is enforcing folklore. Scaladoc on both case
classes, the language reference, and the Computational Model's type-system
section all get it.

Note the grammar's lexical `natural = /[0-9]+/` (`ebnf-grammar.ebnf:31`) admits
`0` and is unaffected — it is the rule for version components, not the type.

### 5.2 Conformance

A literal infers a `TypeExpression`: `Integer(loc)` when `isInteger`,
`Real(loc)` otherwise. Existing machinery then applies — and it is looser than
it looks: `NumericType.isAssignmentCompatible` (`AST.scala:1912`) returns true
for *any* two numeric types, so `Natural` already accepts a `Real` today.

Three Errors are added on top, because a literal's value is statically known
where a reference's is not:

1. A real-form literal assigned to an `IntegerTypeExpression`
   (`constant N is Natural = 1.5`).
2. A negative literal assigned to `Whole` or `Natural`.
3. A zero literal assigned to `Natural`.

**This makes literals stricter than references, deliberately.**
`let x: Natural = someRealField` remains legal and unchanged. The asymmetry is
the justification: the compiler can see a literal's value and cannot see a
field's.

A numeric literal in a non-numeric position (`let name: String = 5`) is already
an error through the existing check sites, since `Integer.isAssignmentCompatible`
is false for `String_`.

### 5.3 Style

A `NumericLiteral` used as a comparand draws a StyleWarning suggesting a named
constant (§ 3).

## 6. Reflection surfaces

RIDDL is fully reflective: parse ⇒ prettify ⇒ BAST ⇒ JSON. A new value node
touches all four.

| surface | change |
|---|---|
| Parser | `numericLiteral` rule; added to `value`, `comparand`, `constant` |
| EBNF | `numeric_literal` rule; `constant` and `comparand` rules updated |
| GBNF | regenerated via `ebnf_to_gbnf.py`, re-validated |
| Prettify | `format` is the stored text; `Constant` emits `:` |
| BAST | new value tag **10** (8 = `Initiate`, 9 = `SelfValue`); `Constant` payload |
| JSON | new kind; `Constant` value encoding |

**This item spends `FORMAT_REVISION` 17 → 18**, and its commit message must say
so. BACKLOG § 2 reserves one bump for this item, A20 and A38 together —
whichever lands first decides it, and this lands first. A20 and A38 then ride
18. Precedent for the wording: the message-value plan's "the 16 → 17 bump is
SPENT — no later task may move it."

`language/input/import/NotImplemented.bast` must be regenerated at the bump,
from its own directory, or `IncludeAndImportTest` reddens. See BACKLOG § 0.

## 7. Dispatch sites

A new `Value` arm falls through silently in several places; CLAUDE.md § Total
Dispatch is explicit that `-Werror` will not catch it here. Sites to read, not
assume:

- `Pass.processValue` and `ResolutionPass` value dispatch (`:501`)
- `ValidationPass` value walks at `:5175`, `:5281`, `:5317`, `:5365`, `:6412` —
  each currently lists `_: LiteralString | _: PromptValue | _: ValueRef |
  _: BooleanLiteral`
- `statementValues` — check the arms AND what each returns; a total walk is
  still defeated if its input drops a field
- `AST.WhenStatement.format` **and** `RiddlFileEmitter.emitStatement` — two
  copies of one dispatch; the tested one says nothing about the other
- `PrettifyVisitor.keyword`, whose fallback is the string `"unknown"`

## 8. Testing

- Parser: every legal and illegal form in § 1, as a table.
- Round trip: parse → prettify → re-parse for each form, asserting `1.50`,
  `007`, `+3` and `2E+8` survive byte-exact.
- Constant convergence: `is` → `:` normalization is stable on pass two.
- BAST: round-trip each form; regenerate the in-repo fixture.
- JSON: round-trip each form.
- Validation: one case per Error in § 5.2, plus a negative control that
  `let x: Natural = someRealField` still passes.
- Comparand StyleWarning: fires on `count > 5`, absent on `count > MaxCount`.
- Corpus: `RiddlModelsRoundTripTest` and the TatSu/GBNF validators. Add a
  fixture exercising the new forms so CI grammar validation actually covers
  them.

**The corpus is red on this branch for three pre-existing reasons** (NOTEBOOK
§ HANDOFF). Take a baseline before attributing anything to this work.

## 9. Out of scope

- Arithmetic. `let x = a + b` is not proposed; this adds literals, not
  expressions.
- Digit separators, radix prefixes — declined in § 1, additive later.
- Numeric literals in type expressions (`String(1,10)`, `range(-5,5)`), which
  already have their own `integer`/`naturalNumber` rules and are untouched.
