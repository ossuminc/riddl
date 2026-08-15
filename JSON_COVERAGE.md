# JSON Input Method — Language Coverage Ledger

This ledger tracks how much of the RIDDL language the JSON input
method (`RiddlLib.parseJson`, `JsonModel` + `JsonAstBuilder`) can
express. The goal is **eventual total coverage** of every construct
RIDDL currently supports, delivered incrementally. Each AST node is
listed with a status:

- ✅ **done** — supported as of the named phase
- 🔜 **phase-N** — planned for phase N
- 🚫 **deferred** — intentionally out of scope (with reason)

When RIDDL gains a new construct, add a row here. The
`JsonCoverageGuardTest` (riddlLib JVM tests) enforces this: it scans
`AST.scala` and fails if a definition / type-expression / statement /
interaction case class has no ledger entry. (Metadata nodes use category
names and are tracked manually in the Metadata section.)

> **What ✅ means, and what enforces it.** A ✅ claims the construct survives
> `root -> JSON -> root` EXACTLY, order included. That is enforced by the
> prettify-agreement check in `Root2JsonFixturesTest`, which renders both trees
> back to RIDDL source and requires them identical across every fixture in the
> repository. `JsonCoverageGuardTest` does NOT enforce it — it only checks that
> each AST case-class name has a row here, so it cannot detect an overclaiming
> ✅, which is exactly how Projector/Repository ports were marked supported
> while the builder ignored them.

Source LOCATIONS are carried: each `contents` entry may hold `$at: [offset,
endOffset]`, read against the basis the document declares (`origin` for a
RIDDL-sourced model, `document` for one authored as JSON). See `JSON_INPUT.md`.

**Schema reference:** `JSON_INPUT.md`. **Roadmap:** NOTEBOOK.md
("JSON input method — phased roadmap").

References (`TypeRef`, `CommandRef`, `EntityRef`, …) are not listed
separately: each is produced by the construct that contains it, so it
is covered in that construct's phase. The builder emits references as
`PathIdentifier`s and leaves resolution to the standard passes.

---

## Definitions

| Construct | Status | Notes |
|---|---|---|
| Root | ✅ Phase 1 | JSON top level (`{ "domains": [...] }`) |
| Domain | ✅ Phase 1 | contexts, types, authors; nested subdomains via recursion |
| Context | ✅ Phase 1 | types, commands/events/queries/results, entities, handlers; opt. `intention`/`shape`/inlets/outlets (2.0) |
| Entity | ✅ Phase 1 | state, handlers, invariants, types; opt. `intentions`/`shape`/inlets/outlets (2.0) |
| Type | ✅ Phase 1 | named type with a type expression |
| Field | ✅ Phase 1 | inside records/messages |
| State | ✅ Phase 1 | record reference only (RIDDL holds no fields in a state) |
| Correlation | ✅ 2.0 (A70) | ordered `keys`, `yieldsRecord`, mandatory `timeout` + `timeoutStatements` |
| Handler | ✅ Phase 1 | on-clauses |
| OnMessageClause | ✅ Phase 1 | command/query/result/record refs |
| OnEventClause | ✅ 2.0 | `kind: "event"`, event ref |
| OnInitializationClause | ✅ Phase 1 / Task 3 | `kind: "init"`; `parameters` (`MethodArgDto[]`, same shape as `MethodDto.args`) |
| OnActivationClause | ✅ 2.0 | `kind: "activate"` |
| OnPassivationClause | ✅ 2.0 | `kind: "passivate"` |
| OnOtherClause | ✅ Phase 1 / A57 | `kind: "other"`; A57 binding + `envelope` (the optional explicit envelope type) |
| OnTerminationClause | ✅ Phase 1 / Task 3 | `kind: "term"`; `parameters` (first must be `Id(...)` of the enclosing processor — validation, not JSON) |
| Invariant | ✅ Phase 1 / A28 s2 / 2026-08-04 | string `condition`, structured `expression`, or a `block` (statements + predicate) — exactly one. Plus `requires` + `requiresKind` (`state`/`type`), which decide WHERE the invariant applies, so dropping them describes a different model. |
| Author | ✅ Phase 1 | at domain level |
| Version | ✅ A53 / A47 | `version` on root/module/domain + all six processors; `name` + `numeric` flag |
| Copyright | ✅ A47 | `copyright` on root/module/domain + all six processors; `name` + verbatim `text` |
| Enumerator | ✅ Phase 2 | names + explicit `value` |
| Constant | ✅ Phase 2 / numeric-literals (2026-08-15) | in context/entity; `value` is a `ValueDto` (`ConstantValue = LiteralString \| NumericLiteral \| BooleanLiteral \| PromptValue`), not a bare string, so a constant can hold any of the four |
| User | ✅ Phase 2 | at domain level |
| Term | ✅ Phase 9 | glossary entry (metadata; see Metadata section) |
| Method | ✅ Phase 3 | aggregate method with args |
| Function | ✅ Phase 3 | `requires`/`returns` as ordered `Requires`/`Returns` contents + body + nested |
| Adaptor | ✅ Phase 4 | direction + ContextRef; opt. `shape`/inlets/outlets (2.0) |
| Projector | ✅ Phase 4 | RepositoryRef; opt. `shape`/inlets/outlets (2.0) |
| Repository | ✅ Phase 4 | Schema; opt. `shape`/inlets/outlets (2.0) |
| Schema | ✅ Phase 4 | Map-based data/links/indices |
| Streamlet | ✅ Phase 4 | optional author-ascribed `shape` (None = derived from arity); inlets/outlets |
| Inlet | ✅ Phase 4 | |
| Outlet | ✅ Phase 4 | |
| Connector | ✅ Phase 4 | OutletRef → InletRef |
| Relationship | ✅ Phase 4 | ProcessorRef + cardinality |
| Saga | ✅ Phase 5 | `requires`/`returns` as ordered `Requires`/`Returns` contents + steps |
| SagaStep | ✅ Phase 5 | do/undo statements |
| Requires | ✅ A9 / rev 4 | `$kind: "requires"` content entry holding an `ArgDto` (type ref, or deprecated inline agg). Also written to the deprecated `input` field so older readers keep working; the content entry is what carries its POSITION among the comments around it. |
| Returns | ✅ A9 / rev 4 | `$kind: "returns"`; mirrors `Requires`, deprecated field is `output`. |
| Module | ✅ S61-1 | FLAT bag: authors, domains, types + message groups, constants, invariants, users, contexts, entities, adaptors, functions, projectors, repositories, streamlets, sagas, epics, connectors, relationships, nested modules, metadata |
| Epic | ✅ Phase 7 | user story + use cases + shownBy |
| UseCase | ✅ Phase 7 | user story + interactions |
| VagueInteraction | ✅ Phase 7 | |
| SendMessageInteraction | ✅ Phase 7 | |
| ArbitraryInteraction | ✅ Phase 7 | |
| SelfInteraction | ✅ Phase 7 | |
| FocusOnGroupInteraction | ✅ Phase 7 | |
| DirectUserToURLInteraction | ✅ Phase 7 | |
| ShowOutputInteraction | ✅ Phase 7 | |
| SelectInputInteraction | ✅ Phase 7 | |
| TakeInputInteraction | ✅ Phase 7 | |
| RefusalInteraction | ✅ A38 | `refusal` kind: from (RefDto), user, reason |
| ParallelInteractions | ✅ Phase 7 | |
| SequentialInteractions | ✅ Phase 7 | |
| OptionalInteractions | ✅ Phase 7 | |
| Group | ✅ Phase 8 | |
| Input | ✅ Phase 8 | |
| Output | ✅ Phase 8 | |
| ContainedGroup | ✅ Phase 8 | |
| Nebula | 🚫 deferred | not a child of ANY container (never in a Root), so it is not a fidelity gap — it is a separate parse target and would need its own top-level document shape |
| Include | ✅ 2.0 | `$kind: "include"` with `origin` + its already-loaded `contents` NESTED, so read-back needs no I/O and stays Native-safe |
| BASTImport | ✅ 2.0 | `$kind: "import"` with path/importKind/selector/alias + nested `contents` |

## Type expressions

| Construct | Status | Notes |
|---|---|---|
| String_ | ✅ Phase 1 | `min`/`max` default to 0/255 |
| Bool | ✅ Phase 1 | `kind: "Boolean"` |
| Integer | ✅ Phase 1 | |
| Whole | ✅ Phase 1 | |
| Natural | ✅ Phase 1 | |
| Number | ✅ Phase 1 | |
| Real | ✅ Phase 1 | |
| Decimal | ✅ Phase 1 | `whole`/`fractional` default to 12/2 |
| Currency | ✅ Phase 1 | `country` defaults to USD |
| RangeType | ✅ Phase 1 | `min`/`max` default to 0/100 |
| UUID | ✅ Phase 1 | |
| Date | ✅ Phase 1 | |
| TimeStamp | ✅ Phase 1 | |
| UniqueId | ✅ Phase 1 | `kind: "Id"`, entity path required; `keyword`? carries the as-written processor-kind keyword (2026-08-13, task 1 of processor-instance-identity) |
| Pattern | ✅ Phase 1 | ≥1 regex required |
| Enumeration | ✅ Phase 1 | `kind: "Enum"`, ≥1 value required |
| Alternation | ✅ Phase 1 | `of`: declared type names |
| AggregateUseCaseTypeExpression | ✅ Phase 1 | `kind: "Record"`; use case in `aggregate` |
| Aggregation | ✅ Phase 1 | `kind: "Record"` with `aggregate: "aggregation"` |
| AliasedTypeExpression | ✅ Phase 1 | `kind: "Alias"` |
| Optional / ZeroOrMore / OneOrMore | ✅ Phase 1 | `cardinality` wrapper |
| SpecificRange | ✅ Phase 2 | `cardinality: "range"` with min/max |
| UserId | ✅ Phase 2 | |
| Anything | ✅ Phase 2 | serialized as `kind: "Anything"`; `"Abstract"` still accepted on input (deprecated) |
| Location | ✅ Phase 2 | |
| URI | ✅ Phase 2 | optional scheme |
| Blob | ✅ Phase 2 | blob kind (default Text) |
| Nothing | ✅ Phase 2 | |
| Time / DateTime / Duration | ✅ Phase 2 | |
| ZonedDate / ZonedDateTime | ✅ Phase 2 | optional zone |
| Current/Length/Luminosity/Mass/Mole/Temperature | ✅ Phase 2 | SI base units |
| Sequence / Set / Graph / Replica | ✅ Phase 2 | `of` element type |
| Mapping | ✅ Phase 2 | from/to |
| Table | ✅ Phase 2 | `of` + dimensions |
| EntityReferenceTypeExpression | ✅ Phase 2 | entity path |

## Statements (handler / function bodies)

| Construct | Status | Notes |
|---|---|---|
| PromptStatement | ✅ Phase 1 | `do`/prompt text |
| ErrorStatement | ✅ Phase 3 | |
| LetStatement | ✅ Phase 3 / A54 | expression widened to ValueDto |
| CodeStatement | ✅ Phase 3 | |
| RequireStatement | ✅ Phase 3 / A28 s2 | condition widened: string, `invariant` name, or structured `expression` (ValueDto) |
| SetStatement | ✅ Phase 3 / A54 | FieldRef/StateRef; value widened to ValueDto |
| SendStatement | ✅ Phase 3 / A54 / A56 | msg = MessageRef, Constructor, or ValueRef (kind `"bound"`); + PortletRef |
| MorphStatement | ✅ Phase 3 / A54 / message-value T2 | value = RecordRef, Constructor, or ValueRef (kind `"bound"`, the same reserved spelling the message operands use) |
| BecomeStatement | ✅ Phase 3 | |
| TellStatement | ✅ Phase 3 / A54 / A56 / A70 task 6 | msg = MessageRef, Constructor, or ValueRef (kind `"bound"`); optional `"by": "<field-name>"` disambiguates which `Id(target)`-typed field is the address (task 6 of processor-instance-identity, 2026-08-13) |
| YieldStatement | ✅ Phase 3 / A54 / message-value T2 | msg = MessageRef, Constructor, or ValueRef (kind `"bound"`); `"kind": "yield"` |
| ReplyStatement | ✅ 2.0 / message-value T2 | msg = MessageRef, Constructor, or ValueRef (kind `"bound"`); `"kind": "reply"`. Its own node and DTO since `reply` stopped being a deprecated synonym for `yield` — a command yields an event, a query replies a result |
| WhenStatement | ✅ Phase 3 / A28 s2 | nested statements; condition widened: string, identifier, or structured `expression` (ValueDto) |
| MatchStatement / MatchCase | ✅ Phase 3 / A29 | subject = ValueDto (valueRef/get/literal); each case: structured `pattern` (type/comparison/literal MatchPatternDto) + optional `guard` (ValueDto) + nested statements |
| MatchPattern (Type/Comparison/Literal) | ✅ A29 | `{ "kind": "type", "path", "keyword"? }` / `{ "kind": "comparison", "op", "comparand": <value> }` / `{ "kind": "literal", "text" }` |
| ForeachStatement | ✅ A25 | field-ref or local collection; nested body statements |
| PutStatement | ✅ A45 | value + OutputRef; value via ValueDto |
| ReturnStatement | ✅ A57 | value via ValueDto |
| TerminateStatement | ✅ 2.0 | `{ "kind": "terminate", "processor": "<path>", "processorKind": "<kind>", "args": [<arg>] }` — end an instance by invoking its `on term` (task 5 of processor-instance-identity, 2026-08-13). Same shape as `Initiate` below, but a statement rather than a value |

## Values (A54)

Value expressions serialized inline within `let`/`set`/`put`/`return`, the
message operands of `send`/`tell`/`yield`/`morph`, and constructor args via
`ValueDto` (`readValue`/`writeValue`).

| Construct | Status | Notes |
|---|---|---|
| LiteralString (value) | ✅ A54 | `{ "value": "literal", "text": ... }` |
| NumericLiteral | ✅ numeric-literals (2026-08-15) | `{ "value": "numeric", "text": ... }` — `text` is the literal AS WRITTEN (`5`, `007`, `1.50`, `2E+8`), always a JSON string, never `ujson.Num`: a Double would turn `1.50` into `1.5` and drop the precision of a large integer, exactly the loss the AST node stores text to avoid. Also serves as a `Comparand` (A28, widened) |
| PromptValue | ✅ A54 | `{ "value": "prompt", "prompt": ... }` — AI-computed value |
| Constructor / ConstructorArg | ✅ A54 | refKind command/event/query/result/record; positional + named args |
| Call | ✅ A24 | `{ "value": "call", "function": "<path>", "args": [<arg>] }` — call a pure function to get a result |
| Ask | ✅ 2.0 | `{ "value": "ask", "query": "<path>", "processor": "<path>", "processorKind": "<kind>" }` — a query correlated with the processor asked. No answer type is carried: it is the query's declared `replies result X`, so storing it would be a second place for the same fact to drift |
| ValueRef | ✅ A54 | `{ "value": "valueRef", "path": ... }` |
| GetValue | ✅ A54 | `{ "value": "get", "source": "input"\|"state", "ref": ... }` |
| BooleanLiteral | ✅ A28 | `{ "value": "boolLiteral", "bool": true\|false }` |
| ComparisonExpression | ✅ A28 | `{ "value": "comparison", "op": "=="\|..., "left": <value>, "right": <value> }` |
| LogicalExpression | ✅ A28 | `{ "value": "logical", "op": "and"\|"or", "left": <value>, "right": <value> }` |
| NotExpression | ✅ A28 | `{ "value": "not", "expr": <value> }` |
| SelfValue | ✅ 2.0 | `{ "value": "self", "field"?: "id"\|"version" }` — the running processor instance. The type is a SYNTHESIZED Aggregation (see `AST.SelfValue`), so nothing here names a path |
| Initiate | ✅ 2.0 | `{ "value": "initiate", "processor": "<path>", "processorKind": "<kind>", "args": [<arg>] }` — bring an instance into being and yield its identity (task 4 of processor-instance-identity, 2026-08-13). No answer type is carried: it is always the synthesized `Id(<processor>)`, so storing it would be a second place for the same fact to drift |

## Metadata

Rich metadata (below) is carried by `metadata` on **every** definition. It used
to ride on only seven of them — domain, context, entity, type, and since A42
group, input and output — so a `described as` on a saga, a `term` on an author
or an `option` on a connector was parsed and then dropped. `brief` remains a
shorthand everywhere.

| Construct | Status | Notes |
|---|---|---|
| BriefDescription | ✅ Phase 1 | `brief` on most constructs |
| Description (block) | ✅ Phase 9 | `description` lines |
| Term (as metadata) | ✅ Phase 9 | `terms` |
| OptionValue (options) | ✅ Phase 9 | `options` |
| AuthorRef (byAuthor) | ✅ Phase 9 | `byAuthors` |
| FigmaRef | ✅ A42 | `figmaRefs` (`{fileKey, nodeId}`) |
| FileAttachment / StringAttachment | ✅ Phase 9 | `attachments` (ULIDAttachment is builder-internal) |
| Comment (in metadata) | ✅ Phase 9 | `comments` on the metadata object |
| Comment (in contents) | ✅ 2.0 | `comments` on the container: `{text, inline?}`. Grouped with the container's other children, so position relative to neighbouring definitions is not preserved — the schema groups every child by kind, so that ordering is already gone for definitions too. |
| Comment (in a statement list) | ✅ 2.0 | `{"kind": "comment", "text": ..., "inline"?: true}` **in place** in the statement array, since `AST.Statements` is `Statement \| Comment`. This carries comments inside `when`/`foreach` bodies and saga steps. A statement list is the SOLE carrier for the comments in it — an on-clause does not also list them under `comments`, or every one would be duplicated on rebuild. |
| URL description (`described at`) | ✅ 2.0 | `urlDescription` |
