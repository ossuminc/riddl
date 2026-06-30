# JSON Input Method — Language Coverage Ledger

This ledger tracks how much of the RIDDL language the JSON input
method (`RiddlLib.parseJson`, `JsonModel` + `JsonAstBuilder`) can
express. The goal is **eventual total coverage** of every construct
RIDDL currently supports, delivered incrementally. Each AST node is
listed with a status:

- ✅ **done** — supported as of the named phase
- 🔜 **phase-N** — planned for phase N
- 🚫 **deferred** — intentionally out of scope (with reason)

When RIDDL gains a new construct, add a row here. Phase 9 adds an
automated guard test that fails if a concrete AST node has no entry.

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
| Context | ✅ Phase 1 | types, commands/events/queries/results, entities, handlers |
| Entity | ✅ Phase 1 | state, handlers, invariants, types |
| Type | ✅ Phase 1 | named type with a type expression |
| Field | ✅ Phase 1 | inside records/messages |
| State | ✅ Phase 1 | record reference only (RIDDL holds no fields in a state) |
| Handler | ✅ Phase 1 | on-clauses |
| OnMessageClause | ✅ Phase 1 | command/event/query/result refs |
| OnInitializationClause | ✅ Phase 1 | `kind: "init"` |
| OnOtherClause | ✅ Phase 1 | `kind: "other"` |
| OnTerminationClause | ✅ Phase 1 | `kind: "term"` |
| Invariant | ✅ Phase 1 | string condition |
| Author | ✅ Phase 1 | at domain level |
| Enumerator | ✅ Phase 2 | names + explicit `value` |
| Constant | ✅ Phase 2 | in context/entity |
| User | ✅ Phase 2 | at domain level |
| Term | 🔜 Phase 9 | glossary entry (it is metadata) |
| Method | ✅ Phase 3 | aggregate method with args |
| Function | ✅ Phase 3 | input/output aggregation + statement body + nested |
| Adaptor | ✅ Phase 4 | direction + ContextRef |
| Projector | ✅ Phase 4 | RepositoryRef |
| Repository | ✅ Phase 4 | Schema |
| Schema | ✅ Phase 4 | Map-based data/links/indices |
| Streamlet | ✅ Phase 4 | shape ↔ inlet/outlet cardinality |
| Inlet | ✅ Phase 4 | |
| Outlet | ✅ Phase 4 | |
| Connector | ✅ Phase 4 | OutletRef → InletRef |
| Relationship | ✅ Phase 4 | ProcessorRef + cardinality |
| Saga | ✅ Phase 5 | input/output + steps |
| SagaStep | ✅ Phase 5 | do/undo statements |
| Module | 🔜 Phase 6 | nested modules/domains |
| Epic | 🔜 Phase 7 | user story + use cases + shownBy |
| UseCase | 🔜 Phase 7 | user story + interactions |
| VagueInteraction | 🔜 Phase 7 | |
| SendMessageInteraction | 🔜 Phase 7 | |
| ArbitraryInteraction | 🔜 Phase 7 | |
| SelfInteraction | 🔜 Phase 7 | |
| FocusOnGroupInteraction | 🔜 Phase 7 | |
| DirectUserToURLInteraction | 🔜 Phase 7 | |
| ShowOutputInteraction | 🔜 Phase 7 | |
| SelectInputInteraction | 🔜 Phase 7 | |
| TakeInputInteraction | 🔜 Phase 7 | |
| ParallelInteractions | 🔜 Phase 7 | |
| SequentialInteractions | 🔜 Phase 7 | |
| OptionalInteractions | 🔜 Phase 7 | |
| Group | 🔜 Phase 8 | |
| Input | 🔜 Phase 8 | |
| Output | 🔜 Phase 8 | |
| ContainedGroup | 🔜 Phase 8 | |
| Nebula | 🚫 deferred | a parse target, not a self-contained JSON document; Root is the JSON top level |
| Include | 🚫 deferred | file-reference mechanism; JSON is self-contained and the builder is no-I/O / Native-safe |
| BASTImport | 🚫 deferred | same reason as Include |

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
| UniqueId | ✅ Phase 1 | `kind: "Id"`, entity path required |
| Pattern | ✅ Phase 1 | ≥1 regex required |
| Enumeration | ✅ Phase 1 | `kind: "Enum"`, ≥1 value required |
| Alternation | ✅ Phase 1 | `of`: declared type names |
| AggregateUseCaseTypeExpression | ✅ Phase 1 | record + command/event/query/result |
| Aggregation | ✅ Phase 1 | exposed via `Record` (mapped to a RecordCase aggregate) |
| AliasedTypeExpression | ✅ Phase 1 | `kind: "Alias"` |
| Optional / ZeroOrMore / OneOrMore | ✅ Phase 1 | `cardinality` wrapper |
| SpecificRange | ✅ Phase 2 | `cardinality: "range"` with min/max |
| UserId | ✅ Phase 2 | |
| Abstract | ✅ Phase 2 | |
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
| LetStatement | ✅ Phase 3 | |
| CodeStatement | ✅ Phase 3 | |
| RequireStatement | ✅ Phase 3 | |
| SetStatement | ✅ Phase 3 | FieldRef/StateRef |
| SendStatement | ✅ Phase 3 | MessageRef + PortletRef |
| MorphStatement | ✅ Phase 3 | |
| BecomeStatement | ✅ Phase 3 | |
| TellStatement | ✅ Phase 3 | |
| ReplyStatement | ✅ Phase 3 | |
| WhenStatement | ✅ Phase 3 | nested statements |
| MatchStatement / MatchCase | ✅ Phase 3 | nested statements |

## Metadata

| Construct | Status | Notes |
|---|---|---|
| BriefDescription | ✅ Phase 1 | `brief` on most constructs |
| Description (block) | 🔜 Phase 9 | |
| Term (as metadata) | 🔜 Phase 9 | |
| OptionValue (options) | 🔜 Phase 9 | |
| AuthorRef (byAuthor) | 🔜 Phase 9 | |
| FileAttachment / StringAttachment / ULIDAttachment | 🔜 Phase 9 | |
| Comment | 🔜 Phase 9 | |
