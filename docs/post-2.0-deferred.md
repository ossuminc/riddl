# Post-2.0 Deferred Work

This file holds work that is correctly reasoned and verified but
deliberately out of scope for the 2.0 release — BACKLOG.md holds
2.0 work only. Re-file each item into BACKLOG.md once 2.0 ships.

- **BLOCKED UNTIL 3.0 — drop the deprecated inline aggregation from
  `requires`/`returns`, then narrow the accessors to `Option[TypeRef]`.**
  **Reid, 2026-08-12: wait for 3.0.** Removing a deprecated form is a breaking
  change, and the compatibility policy in `CLAUDE.md` allows it only in the next
  MAJOR release — the inline form was deprecated during 2.x development, so 2.0
  is not where it goes. Do not start this against `release/2`; the detail below
  is kept because it was verified and would otherwise be re-derived in a year.
  Originally decided by Reid 2026-08-04 while moving the clauses into contents: `Option[TypeRef]` is the wanted END state,
  but it is a language change, not a type tidy-up, so it does not ride along.
  Today `Requires.what` / `Returns.what` are `TypeRef | Aggregation` and
  `Function.input` / `Saga.input` return `Option[TypeRef | Aggregation]` —
  **exactly the type the constructor fields had**, which is why the move cost
  consumers nothing.
  **Do NOT narrow the accessor while the node stays wide.** A saga written
  `requires { a: Integer }` would then read as having no input at all, and any
  check gated on `input.isEmpty` fires wrongly — the ungated-accessor failure
  mode this repo keeps rediscovering.
  **Verified cost of doing it properly** (checked 2026-08-04, not estimated):
  4 fixtures use the inline form — `language/input/everything_full.riddl:72,97`,
  `language/input/module/mixed-module.riddl:17`,
  `language/input/requires-returns-ref.riddl` — plus two tests that ASSERT the
  deprecation fires (`FunctionValidatorTest:106`, `SagaValidatorTest:56`), the
  `aggregation` alternative in `func_input`/`func_output` in the EBNF + a GBNF
  regen, the `ArgDto.fields` read path in JSON, and an external-corpus re-run.
  Sequence: deprecate loudly for a release, then remove.

## RBAC / authorization in the language (Issue #535)

Reid, filed 2026-08-04 as a DRAFT ("do not act on this"), moved here 2026-08-19
and **not to be raised again until 2.0.0 has shipped**.

Security is a cross-cutting design element that should be expressible in the
model. The sketch:

```riddl
role RWAccess is read, write with { briefly "A role that allows read-write access" }
role ROAccess is read with { briefly "A role that allows read-only access" }
context Foo is {
  accessible by RWAccess
  handler Writeables is {
    accessible by ROAccess
    ???
  }
}
```

`role` becomes a new definition, and definitions may declare who may access them.
At context level it covers every message in every handler in that context; on a
handler it covers the messages that handler handles. **Most-specific-specification
wins.**

**Status when moved: genuinely a draft.** "Why we cannot derive it ourselves",
"Semantics we are assuming" and "Acceptance criteria" were all empty. It needs a
design pass before it needs an implementation, and that pass is post-2.0 work by
the author's ruling.

