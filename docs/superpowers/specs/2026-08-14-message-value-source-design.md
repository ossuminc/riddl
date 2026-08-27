# Where a message ref's VALUE comes from

**Status:** designed 2026-08-14, not implemented. Decisions by Reid; measurements
taken, not estimated. Origin: `task/done/2026-08-14-where-does-a-message-refs-value-come-from.md`.

## 1. The problem

`send event Foo to outlet Bar` names a message **type**. It says nothing about
where the **value** comes from, so a generator has nothing to lower and must
invent a payload or emit a hole. riddlg measured this on reactive-bbq: **659 of
1088 `AI FILL` markers (60.6%)** are bare message refs, and **98.2% of every hole**
counting the `morph` record analogue. Each becomes a `null` in generated Java —
worse than a missing one, because it runs.

## 2. The decisive discovery: A56 already built half of this

**This is a WIDENING of A56, not a new feature.** Anyone planning it from the
task file alone would design a feature that partly exists.

Already shipped:

| surface | state |
|---|---|
| `EBNF:298` | `deliverable_message_value = message_value \| path_identifier` |
| `EBNF:302-303` | `send` and `tell` accept it; `yield`/`reply` do NOT |
| `AST:3496` | `SendStatement.msg: MessageRef \| Constructor \| ValueRef` |
| `AST:3570` | `TellStatement.msg: MessageRef \| Constructor \| ValueRef` |
| `ValidationPass:1163,1192` | both dispatch the `ValueRef` arm |
| helpers | `operandType` (`:752`), `operandMessageKind` (`:763`), `operandMessageName` (`:931`) all already handle `ValueRef` |

So `on p: command Ping is { tell p to entity F }` **works today**.

What A56 did NOT do, and this design does:

- **The source is narrow.** `checkBoundMessageOperand` (`ValidationPass:920`)
  asks `refMap.definitionOf[Type](vr.path)` — the key the resolver uses for an
  on-clause **binding**. A state field or a `let`-local does not resolve that
  way, and the failure is a hard **Error**, not a fallthrough. Its message says
  so outright: *"does not name a message bound by an enclosing 'on' clause."*
- **`yield` and `reply` are excluded**, deliberately: their operand is compared
  against the clause's declared `yields`/`replies`.
- **`morph … with` is excluded** — `MorphStatement.value: RecordRef | Constructor`
  (`AST:3520`). This is riddlg's other 37.6%.
- **Nothing warns** about the bare ref form.

## 3. Decisions

### D1 — Shape: a bare `ValueRef`, no new keyword

`message_value = constructor | message_ref | value_ref`. Unambiguous because a
type always carries its kind keyword, so a bare identifier can only be a value.
The parse ordering A56 established stands: `message_ref` is keyword-led and is
tried first; a bare path is reached only after `command`/`event`/`query`/`result`
fails.

```riddl
on placed: event OrderPlaced is { send placed to outlet Downstream }
send orderRecord.lastEvent to outlet Bar
tell shipCmd to entity Order
```

**`from` was rejected.** It already means the SENDER in epic interactions
(`send command Foo from user U to context C`), and one word with two meanings in
sibling constructs is a cost authors pay repeatedly. riddlg's own question
anticipated this — whether existing spellings make a `from` clause redundant —
and they do: A55's binding, `let`, and `ValueRef` path-walking already spell
every source.

### D2 — Sources: ANY resolvable `ValueRef`

State-record field, on-clause binding, `let`-local, function result, `ask`
result. One rule, not an enumeration to keep in sync. The value's type must BE
the message type; that check is what makes this worth more than a comment.

### D3 — The bare form becomes an Error, but warns first

End state: naming a type with no value is an Error. It ships as a
**CompletenessWarning** first, because of the measurement in §5.

## 4. The four changes

**C1. Widen the source.** Replace `checkBoundMessageOperand`'s
`refMap.definitionOf[Type](vr.path)` probe with the A55/lifecycle-parameter
resolution path (`valueRefTypeExpr` / `typeExprOfPath`), then require the
resulting `TypeExpression` to be — or to alias to — an
`AggregateUseCaseTypeExpression` whose use case matches the statement's required
kind. Keep the Error for a genuinely unresolvable name; broaden its message,
which currently names on-clause binding as the only legal source and would
become a lie.

**C2. Extend the arm to `yield` and `reply`.** Their stated reason for exclusion
does not survive the widening: the operand is compared against the declared
`yields`/`replies`, and that comparison is by resolved TYPE, which a `ValueRef`
supplies exactly as a `MessageRef` does. Add `ValueRef` to
`YieldStatement.msg` (`AST:3591`) and `ReplyStatement.msg` (`:3627`), and switch
their EBNF rules to `deliverable_message_value`.

**C3. Extend `morph`.** `MorphStatement.value` becomes
`RecordRef | Constructor | ValueRef`. This closes riddlg's 409 `take the <field>
from record <R>` holes and is the same shape as the message case, one level down.

**C4. Warn on the bare `MessageRef` operand** in all four statements plus
`morph` — a CompletenessWarning saying the operand names a type, not a value,
suggesting the constructor form or a value name.

### Reflectivity and wire format

C2 and C3 change what three statements can hold, so all four surfaces move:
parser, prettify, BAST, JSON. **BAST needs `FORMAT_REVISION` 16 → 17** — note it
was just moved to 16 by the interaction-block fix (`78a025362`), so this is a
second bump and `language/input/import/NotImplemented.bast` must be regenerated
again, from its own directory.

## 5. The measurement that forces the sequencing

Counted 2026-08-14 over riddl-models — do not re-derive:

| statement | bare refs |
|---|---|
| `tell` | 7,541 |
| `send` | 6,445 |
| `reply` | 406 |
| `yield` | 349 |
| **total** | **14,730** |
| constructor form | **0** |

An Error therefore does not migrate the corpus — it invalidates every
message-sending statement in all 189 models at once, while CI requires 189/189
validating clean. Sequence: ship C1–C4 with the warning, drop a migration task on
riddl-models, flip to Error when the corpus is clean. **riddlg loses nothing by
this** — a warning marks all 14,730 sites for their gap audit exactly as an Error
would; only the model breaking is deferred.

## 6. Open questions — need a ruling before implementation

**Q1. Does a field-less message need a value at all?** `event Started is { }` has
no data, so the type fully determines the value and there is nothing to source.
Warning on it would be noise of exactly the kind the `???` ruling exists to
prevent. Proposal: **exempt a message whose resolved type has no fields**, and
count how many of the 14,730 that removes before committing to the number.

**Q2. Is `reply` in scope for the warning?** `reply` answers a query with its
declared `result`, so the type is already pinned by the clause. The value still
is not — but the 406 sites may deserve different treatment from the 13,986
`tell`/`send` ones.

**Q3. Does the widened source admit `self`?** `self` is a synthesized Aggregation
carrying `id`/`version`, not a message, so `send self` must fail — but it should
fail with a good message, not "does not name a message." Cheap to get right if
designed in; confusing if discovered later.

## 7. Out of scope

- **Migrating riddl-models.** A separate task drop, and the prerequisite for the
  Error flip.
- **Whether a generator may infer a value.** RIDDL specifies meaning; lowering
  stays riddlg's.

## 8. The lesson riddlg attached, which is about us

riddl-models uses the constructor form **zero** times corpus-wide; riddlg's own
fixtures use it **30** times across 6 spec files. Each body of tests was green
about a path the other never exercised.

This is the second sighting in two days of the same blindness — the
processor-instance-identity branch shipped a Critical past eight task-scoped
reviews because every test body was `do "start"` and the one fixture exercising
the feature passed by name coincidence. **CI grammar validation against
riddl-models proves those models parse. It never proves the corpus reaches the
language's expressive range**, and a rule that tightens gets its counter-examples
edited away.
